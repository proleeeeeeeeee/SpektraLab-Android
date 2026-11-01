// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.OpenableColumns
import android.util.AtomicFile
import io.github.proleeeeeeeeee.spektralab.core.EditRecipe
import io.github.proleeeeeeeeee.spektralab.core.RenderPolicy
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
data class StoredPhoto(val id: String, val displayName: String, val recipe: EditRecipe = EditRecipe())

class PhotoStore(context: Context) {
    private val application = context.applicationContext
    private val imports = File(application.filesDir, "imports").apply { mkdirs() }
    private val session = AtomicFile(File(application.filesDir, "last-edit.json"))
    private val lock = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private var activeId: String? = null

    /** Only picker-granted content URIs are accepted; external filenames never form a path. */
    suspend fun import(uri: Uri): Pair<StoredPhoto, Bitmap> = withContext(Dispatchers.IO) {
        require(uri.scheme == "content") { "Choose a photograph using the system photo picker" }
        val id = UUID.randomUUID().toString()
        val partial = File(imports, id + ".part")
        val destination = fileFor(id)
        try {
            application.contentResolver.openInputStream(uri)?.use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(65536)
                    var total = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        require(total <= RenderPolicy.MAX_IMPORT_BYTES) { "Photograph exceeds the 128 MB import limit" }
                        output.write(buffer, 0, read)
                    }
                    require(total > 0) { "The selected photograph is empty" }
                }
            } ?: error("The selected photograph cannot be opened")
            check(partial.renameTo(destination)) { "Could not store the selected photograph" }
            val photo = StoredPhoto(id, displayName(uri))
            val bitmap = decode(photo, RenderPolicy.PREVIEW_LONG_EDGE)
            save(photo, replaceSource = true)
            // Only obsolete, app-owned copies are removed. Gallery originals are untouched.
            imports.listFiles()?.filter { it.name != destination.name && it.isFile }?.forEach { it.delete() }
            photo to bitmap
        } catch (failure: Throwable) {
            partial.delete()
            destination.delete()
            throw failure
        }
    }
    suspend fun restore(): StoredPhoto? = withContext(Dispatchers.IO) {
        lock.withLock {
            if (!session.baseFile.exists() && !File(session.baseFile.path + ".bak").exists())
                return@withLock null
            runCatching {
                val bytes = session.readFully()
                require(bytes.size <= 16384)
                json.decodeFromString<StoredPhoto>(bytes.toString(Charsets.UTF_8))
                    .also {
                        require(fileFor(it.id).isFile && it.displayName.length <= 160)
                        activeId = it.id
                    }
            }.getOrNull()
        }
    }
    suspend fun save(photo: StoredPhoto, replaceSource: Boolean = false) = withContext(Dispatchers.IO) {
        lock.withLock {
            // A late save from a cancelled preview cannot replace a newer import.
            if (!replaceSource && activeId != null && activeId != photo.id) return@withLock
            val stream = session.startWrite()
            try {
                stream.write(json.encodeToString(photo).toByteArray(Charsets.UTF_8))
                session.finishWrite(stream)
                activeId = photo.id
            } catch (failure: Throwable) { session.failWrite(stream); throw failure }
        }
    }
    suspend fun decode(photo: StoredPhoto, longEdge: Int): Bitmap = withContext(Dispatchers.IO) {
        val source = ImageDecoder.createSource(fileFor(photo.id))
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val size = RenderPolicy.targetSize(info.size.width, info.size.height, longEdge)
            decoder.setTargetSize(size.first, size.second)
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
            decoder.isMutableRequired = false
        }
        if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap
        else bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: error("Photograph could not be converted to sRGB")
    }
    private fun fileFor(id: String): File {
        require(id.matches(Regex("[a-f0-9-]{36}"))) { "Invalid stored photograph identity" }
        return File(imports, id + ".photo")
    }
    private fun displayName(uri: Uri): String = runCatching {
        application.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0).take(160) else null }
    }.getOrNull()?.replace(Regex("[\\p{Cntrl}]"), "")?.ifBlank { null } ?: "Photograph"
}
