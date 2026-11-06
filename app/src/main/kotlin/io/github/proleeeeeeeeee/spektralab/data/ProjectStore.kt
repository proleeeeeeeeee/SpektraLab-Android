// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab.data

import android.content.Context
import android.graphics.Bitmap
import io.github.proleeeeeeeeee.spektralab.engine.PhotoSource
import android.net.Uri
import android.provider.OpenableColumns
import android.util.AtomicFile
import io.github.proleeeeeeeeee.spektralab.core.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class StoredPhoto(
    val id: String, val displayName: String, val recipe: EditRecipe = EditRecipe(),
    val width: Int = 0, val height: Int = 0, val raw: Boolean = false,
    val revision: Long = 0,
)
@Serializable
data class PhotoProject(val id: String, val name: String, val photos: List<StoredPhoto> = emptyList(), val selectedId: String? = null)
@Serializable
data class ProjectLibrary(
    val projects: List<PhotoProject> = listOf(PhotoProject("default", "My photographs")),
    val activeId: String = "default",
    val presets: List<SavedPreset> = emptyList(),
    val queue: ExportQueue = ExportQueue(),
) {
    val active get() = projects.first { it.id == activeId }
    fun photo(id: String) = projects.asSequence().flatMap { it.photos.asSequence() }.firstOrNull { it.id == id }
}

/** One bounded atomic index owns projects, presets and the resumable export queue. */
class PhotoStore(context: Context) {
    private val application = context.applicationContext
    private val imports = File(application.filesDir, "imports").apply { mkdirs() }
    private val index = AtomicFile(File(application.filesDir, "projects.json"))
    private val legacy = AtomicFile(File(application.filesDir, "last-edit.json"))
    private val lock = storageLock
    private val codec = PhotoSource(application)
    private val json = Json { ignoreUnknownKeys = true }

    private fun read(): ProjectLibrary {
        val restored = if (index.baseFile.exists() || File(index.baseFile.path+".bak").exists()) {
            check(index.baseFile.length() <= 4_194_304) { "Project index exceeds its storage limit" }
            json.decodeFromString<ProjectLibrary>(index.readFully().toString(Charsets.UTF_8))
        } else {
            val old = runCatching {
                require(legacy.baseFile.length() <= 16384)
                json.decodeFromString<StoredPhoto>(legacy.readFully().toString(Charsets.UTF_8))
            }.getOrNull()?.takeIf { fileFor(it.id).isFile }
            if (old == null) ProjectLibrary() else ProjectLibrary(projects = listOf(
                PhotoProject("default","My photographs",listOf(old),old.id)))
        }
        require(restored.projects.size in 1..100 && restored.presets.size <= 200)
        require(restored.projects.any { it.id == restored.activeId })
        require(restored.projects.sumOf { it.photos.size } <= 2000 && restored.queue.jobs.size <= 2000)
        restored.projects.forEach { project ->
            require(project.name.isNotBlank() && project.name.length <= 80)
            project.photos.forEach { require(it.displayName.length <= 160); fileFor(it.id) }
        }
        return restored
    }
    private fun write(value: ProjectLibrary) {
        val bytes = json.encodeToString(value).toByteArray()
        require(bytes.size <= 4_194_304) { "Project storage limit reached" }
        val stream = index.startWrite()
        try { stream.write(bytes); index.finishWrite(stream) }
        catch (failure: Throwable) { index.failWrite(stream); throw failure }
    }
    suspend fun library(): ProjectLibrary = withContext(Dispatchers.IO) { lock.withLock { read() } }
    suspend fun restore(): StoredPhoto? = library().active.let { p -> p.photos.firstOrNull { it.id == p.selectedId } }

    suspend fun import(uri: Uri): Pair<StoredPhoto, Bitmap> = withContext(Dispatchers.IO) {
        require(uri.scheme == "content") { "Choose a photograph using the system picker" }
        require(library().projects.sumOf { it.photos.size } < 2000) { "Project photo limit reached" }
        val id = UUID.randomUUID().toString()
        val partial = File(imports, id+".part")
        val destination = fileFor(id)
        try {
            application.contentResolver.openInputStream(uri)?.use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(65536); var total = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= RenderPolicy.MAX_IMPORT_BYTES) { "Photograph exceeds the import size limit" }
                        output.write(buffer,0,count)
                    }
                    require(total > 0) { "The selected photograph is empty" }
                }
            } ?: error("The selected photograph cannot be opened")
            check(partial.renameTo(destination)) { "Could not store the photograph" }
            val info = codec.inspect(destination)
            val photo = StoredPhoto(id,displayName(uri),width = info.width,height = info.height,raw = info.raw)
            val bitmap = decode(photo,RenderPolicy.PREVIEW_LONG_EDGE)
            lock.withLock {
                val state = read()
                val project = state.active.copy(photos = state.active.photos+photo,selectedId = photo.id)
                write(state.copy(projects = state.projects.map { if (it.id == project.id) project else it }))
            }
            saveThumbnail(photo,bitmap)
            photo to bitmap
        } catch (failure: Throwable) { partial.delete(); destination.delete(); throw failure }
    }
    suspend fun save(photo: StoredPhoto) = withContext(Dispatchers.IO) {
        lock.withLock {
            val state = read()
            // Only indexed photos may be updated: late renders cannot resurrect deletions.
            val existing = state.photo(photo.id) ?: return@withLock
            if (photo.revision < existing.revision) return@withLock
            write(state.copy(projects = state.projects.map { project ->
                project.copy(photos = project.photos.map { if (it.id == photo.id) photo else it })
            }))
        }
    }
    suspend fun select(id: String) = mutate { state ->
        require(state.active.photos.any { it.id == id })
        state.copy(projects = state.projects.map { if (it.id == state.activeId) it.copy(selectedId = id) else it })
    }
    suspend fun createProject(name: String) = mutate { state ->
        require(state.projects.size < 100)
        val project = PhotoProject(UUID.randomUUID().toString(),cleanName(name))
        state.copy(projects = state.projects+project,activeId = project.id)
    }
    suspend fun selectProject(id: String) = mutate { state ->
        require(state.projects.any { it.id == id }); state.copy(activeId = id)
    }
    suspend fun renameProject(name: String) = mutate { state ->
        state.copy(projects = state.projects.map { if (it.id == state.activeId) it.copy(name = cleanName(name)) else it })
    }
    suspend fun deleteProject() = withContext(Dispatchers.IO) {
        lock.withLock {
            val state = read()
            val owned = state.active.photos
            val remaining = state.projects.filterNot { it.id == state.activeId }.ifEmpty {
                listOf(PhotoProject(UUID.randomUUID().toString(),"My photographs"))
            }
            write(state.copy(projects = remaining,activeId = remaining.first().id))
            owned.forEach { fileFor(it.id).delete(); thumbnail(it.id).delete() }
        }
    }
    suspend fun deletePhotos(ids: Set<String>) = withContext(Dispatchers.IO) {
        lock.withLock {
            val state = read()
            val owned = state.active.photos.filter { it.id in ids }
            val remaining = state.active.photos.filterNot { it.id in ids }
            val project = state.active.copy(photos = remaining,selectedId = state.active.selectedId?.takeUnless { it in ids } ?: remaining.firstOrNull()?.id)
            write(state.copy(projects = state.projects.map { if (it.id == project.id) project else it }))
            owned.forEach { fileFor(it.id).delete(); thumbnail(it.id).delete() }
        }
    }
    suspend fun savePreset(name: String, settings: RecipeTransfer) = mutate { state ->
        require(state.presets.size < 200)
        state.copy(presets = state.presets+SavedPreset(UUID.randomUUID().toString(),cleanName(name),settings))
    }
    suspend fun deletePreset(id: String) = mutate { it.copy(presets = it.presets.filterNot { p -> p.id == id }) }
    suspend fun applySettings(ids: Set<String>, transfer: RecipeTransfer) = withContext(Dispatchers.IO) {
        lock.withLock {
            val state = read()
            write(state.copy(projects = state.projects.map { project -> project.copy(photos = project.photos.map { photo ->
                if (photo.id in ids) photo.copy(recipe = transfer.applyTo(photo.recipe),revision = photo.revision+1) else photo
            }) }))
            ids.forEach { thumbnail(it).delete() }
        }
    }
    suspend fun saveQueue(queue: ExportQueue) = mutate { it.copy(queue = queue) }
    private suspend fun mutate(block: (ProjectLibrary) -> ProjectLibrary) = withContext(Dispatchers.IO) {
        lock.withLock { val next = block(read()); write(next); next }
    }
    suspend fun decode(photo: StoredPhoto, longEdge: Int): Bitmap = withContext(Dispatchers.IO) {
        codec.preview(fileFor(photo.id),longEdge)
    }
    suspend fun saveThumbnail(photo: StoredPhoto, bitmap: Bitmap) = withContext(Dispatchers.IO) {
        lock.withLock {
        val current = read().photo(photo.id) ?: return@withLock
        if (photo.revision < current.revision) return@withLock
        val size = RenderPolicy.targetSize(bitmap.width,bitmap.height,256)
        val small = Bitmap.createScaledBitmap(bitmap,size.first,size.second,true)
        val target = AtomicFile(thumbnail(photo.id))
        val stream = target.startWrite()
        try { check(small.compress(Bitmap.CompressFormat.JPEG,85,stream)); target.finishWrite(stream) }
        catch (failure: Throwable) { target.failWrite(stream); throw failure }
        finally { if (small !== bitmap) small.recycle() }
        }
    }
    fun thumbnail(id: String) = File(imports,checked(id)+".thumb")
    fun source(photo: StoredPhoto) = fileFor(photo.id)
    private fun fileFor(id: String) = File(imports,checked(id)+".photo")
    private fun checked(id: String): String {
        require(id.matches(Regex("[a-f0-9-]{36}"))) { "Invalid photo identity" }; return id
    }
    private fun cleanName(name: String) = name.trim().also {
        require(it.isNotBlank() && it.length <= 80 && it.none(Char::isISOControl)) { "Use a name of 1–80 characters" }
    }
    private fun displayName(uri: Uri): String = runCatching {
        application.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)
            ?.use { if (it.moveToFirst()) it.getString(0).take(160) else null }
    }.getOrNull()?.replace(Regex("[\\p{Cntrl}]"),"")?.ifBlank { null } ?: "Photograph"

    internal suspend fun <T> backupSnapshot(block: suspend (ProjectLibrary)->T): T = withContext(Dispatchers.IO) {
        lock.withLock { block(read()) }
    }

    /** Fresh identities resolve conflicts; one atomic index write makes the copies visible. */
    internal suspend fun restoreBackup(backup: ValidatedBackup): ProjectLibrary = withContext(Dispatchers.IO) {
        lock.withLock {
            val current=read();val incoming=backup.library
            require(current.projects.size+incoming.projects.size<=100 && current.presets.size+incoming.presets.size<=200) { "Restoring would exceed the project or preset limit." }
            require(current.projects.sumOf { it.photos.size }+incoming.projects.sumOf { it.photos.size }<=2000) { "Restoring would exceed the photo limit." }
            val photoIds=incoming.projects.flatMap { it.photos }.associate { it.id to UUID.randomUUID().toString() }
            val projectIds=incoming.projects.associate { it.id to UUID.randomUUID().toString() }
            val copied=incoming.projects.map { project -> project.copy(id=projectIds.getValue(project.id),
                name=(project.name.take(69)+" (restored)"),
                photos=project.photos.map { it.copy(id=photoIds.getValue(it.id)) },selectedId=project.selectedId?.let(photoIds::getValue)) }
            val presets=incoming.presets.map { preset -> preset.copy(id=UUID.randomUUID().toString(),
                name=if(current.presets.any { it.name==preset.name })preset.name.take(69)+" (restored)" else preset.name) }
            val tables=LightTableStore(application)
            val ownedFiles=mutableListOf<File>();val ownedTables=mutableListOf<String>()
            try {
                for((oldId,newId) in photoIds) {
                    currentCoroutineContext().ensureActive()
                    val source=backup.sources.getValue(oldId);val destination=fileFor(newId)
                    require(!destination.exists()) { "A restored identity conflicts with an existing photo." }
                    check(source.renameTo(destination)) { "Could not move a validated original into project storage." }
                    ownedFiles+=destination
                }
                for(project in incoming.projects) {
                    currentCoroutineContext().ensureActive()
                    val id=projectIds.getValue(project.id);val layout=backup.layouts.getValue(project.id)
                    val restored=layout.copy(
                        placements=layout.placements.map { it.copy(photoId=photoIds.getValue(it.photoId)) },
                        filteredPlacements=layout.filteredPlacements.map { it.copy(photoId=photoIds.getValue(it.photoId)) },
                        selectedId=layout.selectedId?.let(photoIds::getValue),
                        reviews=layout.reviews.mapKeys { photoIds.getValue(it.key) },pickedIds=layout.pickedIds.map(photoIds::getValue).toSet())
                    check(tables.save(id,restored,1)) { "Could not save the restored light-table layout." }
                    ownedTables+=id
                }
                currentCoroutineContext().ensureActive()
                val merged=current.copy(projects=current.projects+copied,presets=current.presets+presets,
                    activeId=projectIds.getValue(incoming.activeId))
                write(merged)
                merged
            } catch(failure: Throwable) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    ownedFiles.forEach { it.delete() }
                    for(id in ownedTables)tables.deleteRestored(id)
                }
                throw failure
            }
        }
    }
    companion object { private val storageLock=Mutex() }
}
