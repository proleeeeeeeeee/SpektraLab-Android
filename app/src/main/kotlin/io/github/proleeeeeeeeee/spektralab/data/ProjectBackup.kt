// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab.data

import android.content.Context
import io.github.proleeeeeeeeee.spektralab.core.*
import java.io.*
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable internal data class BackupFile(val bytes: Long,val sha256: String)
@Serializable internal data class BackupManifest(
    val format: String="SpektraLab-project-backup",val version: Int=1,
    val includesOriginals: Boolean,val files: Map<String,BackupFile>,
    val originals: Map<String,BackupFile>,
)
internal data class BackupSummary(val projects: Int,val photos: Int,val presets: Int)
internal class ValidatedBackup(
    val library: ProjectLibrary,val layouts: Map<String,LightTableLayout>,val sources: Map<String,File>,
    private val staging: File,
): AutoCloseable {
    override fun close() { staging.deleteRecursively() }
}

/** Streaming offline archive. Nothing enters live project storage before full validation. */
internal class ProjectBackup(context: Context) {
    private val app=context.applicationContext
    private val store=PhotoStore(app)
    private val tables=LightTableStore(app)
    private val json=Json { ignoreUnknownKeys=false;encodeDefaults=true }
    private val uuid=Regex("[a-f0-9-]{36}")
    private val hashPattern=Regex("[a-f0-9]{64}")
    private fun projectId(id: String)=id=="default" || uuid.matches(id)
    private fun pathAllowed(name: String)= name=="library.json" ||
        Regex("layouts/(default|[a-f0-9-]{36})\\.json").matches(name) ||
        Regex("originals/[a-f0-9-]{36}\\.photo").matches(name)

    suspend fun write(output: OutputStream,includeOriginals: Boolean,progress: (String)->Unit={}): BackupSummary =
        store.backupSnapshot { library ->
            validateLibrary(library.copy(queue=ExportQueue()))
            val metadata=linkedMapOf<String,ByteArray>()
            // Export jobs contain device-specific output URIs and are deliberately not replayed.
            metadata["library.json"]=json.encodeToString(library.copy(queue=ExportQueue())).toByteArray()
            for(project in library.projects) {
                val layout=tables.load(project.id).layout.reconcile(project.photos.map { it.id })
                metadata["layouts/"+project.id+".json"]=json.encodeToString(layout).toByteArray()
                require(metadata.values.sumOf { it.size.toLong() }<=16L*1024*1024) { "Project metadata exceeds the backup memory limit." }
            }
            val sourceRecords=linkedMapOf<String,BackupFile>()
            val sources=library.projects.flatMap { it.photos }
            var total=0L
            for((i,photo) in sources.withIndex()) {
                currentCoroutineContext().ensureActive()
                progress("Checking original "+(i+1)+" / "+sources.size)
                val file=store.source(photo)
                require(file.isFile) { "An original is missing. Restore the source before backing up this project." }
                val record=file.inputStream().use { fingerprint(it,null,MAX_FILE) }
                require(record.bytes>0) { "An original is empty." }
                sourceRecords[photo.id]=record
                total+=record.bytes
                require(total<=MAX_ARCHIVE) { "Backup exceeds the 16 GB original-data limit." }
            }
            val records=metadata.mapValues { (_,bytes)-> BackupFile(bytes.size.toLong(),sha(bytes)) }.toMutableMap()
            require(metadata.values.all { it.size.toLong()<=MAX_METADATA }) { "A project metadata entry exceeds the backup limit." }
            if(includeOriginals)sourceRecords.forEach { (id,record)->records["originals/$id.photo"]=record }
            val manifest=BackupManifest(includesOriginals=includeOriginals,files=records,originals=sourceRecords)
            val bounded=object: FilterOutputStream(output) {
                private var written=0L
                private fun reserve(bytes: Int) {
                    require(written+bytes<=MAX_ARCHIVE) { "Backup ZIP exceeds the 16 GB archive limit." }
                    written+=bytes
                }
                override fun write(value: Int) { reserve(1);out.write(value) }
                override fun write(bytes: ByteArray,offset: Int,length: Int) {
                    reserve(length);out.write(bytes,offset,length)
                }
            }
            ZipOutputStream(BufferedOutputStream(bounded)).use { zip ->
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write(json.encodeToString(manifest).toByteArray());zip.closeEntry()
                for((name,bytes) in metadata) {
                    currentCoroutineContext().ensureActive()
                    zip.putNextEntry(ZipEntry(name));zip.write(bytes);zip.closeEntry()
                }
                if(includeOriginals)for((i,photo) in sources.withIndex()) {
                    progress("Saving original "+(i+1)+" / "+sources.size)
                    zip.putNextEntry(ZipEntry("originals/"+photo.id+".photo"))
                    val actual=store.source(photo).inputStream().use { fingerprint(it,zip,MAX_FILE) }
                    require(actual==sourceRecords.getValue(photo.id)) { "An original changed while creating the backup. Try again." }
                    zip.closeEntry()
                }
            }
            BackupSummary(library.projects.size,sources.size,library.presets.size)
        }

    suspend fun read(input: InputStream,progress: (String)->Unit={}): ValidatedBackup = withContext(Dispatchers.IO) {
        val stage=File(File(app.cacheDir,"project-backups").apply { mkdirs() },UUID.randomUUID().toString()).apply { mkdirs() }
        try {
            val archive=File(stage,"archive.zip")
            archive.outputStream().use { output -> fingerprint(input,output,minOf(MAX_ARCHIVE,stage.usableSpace-RESERVE)) }
            ZipFile(archive).use { zip ->
                val entries=zip.entries().asSequence().toList()
                require(entries.size<=2205 && entries.map { it.name }.distinct().size==entries.size) { "Backup has duplicate or too many entries." }
                require(entries.all { !it.isDirectory && (it.name=="manifest.json" || pathAllowed(it.name)) }) { "Backup contains an invalid path." }
                val manifestEntry=zip.getEntry("manifest.json") ?: error("This is not a SpektraLab project backup.")
                val manifestBytes=zip.getInputStream(manifestEntry).use { boundedBytes(it,MAX_METADATA) }
                val manifest=json.decodeFromString<BackupManifest>(manifestBytes.toString(Charsets.UTF_8))
                require(manifest.format=="SpektraLab-project-backup" && manifest.version==1) { "This backup version is not supported." }
                require(manifest.files.keys.all(::pathAllowed)) { "Invalid backup file list." }
                require(entries.map { it.name }.toSet()==manifest.files.keys+"manifest.json") { "Backup file list does not match the archive." }
                require(manifest.files.values.all { it.bytes in 0..MAX_FILE && hashPattern.matches(it.sha256) }) { "Invalid backup checksums." }
                require(manifest.originals.keys.all(uuid::matches) && manifest.originals.values.all { it.bytes in 1..MAX_FILE && hashPattern.matches(it.sha256) }) { "Invalid original identities." }
                val total=manifest.files.values.fold(0L) { n,f -> Math.addExact(n,f.bytes) }
                require(manifest.files.filterKeys { !it.startsWith("originals/") }.values.sumOf { it.bytes }<=16L*1024*1024) { "Backup metadata exceeds its memory limit." }
                require(total<=MAX_ARCHIVE && total<=stage.usableSpace-RESERVE) { "Not enough free storage to validate this backup." }
                val metadata=mutableMapOf<String,ByteArray>()
                val sources=mutableMapOf<String,File>()
                for((i,item) in manifest.files.entries.withIndex()) {
                    currentCoroutineContext().ensureActive()
                    progress("Validating file "+(i+1)+" / "+manifest.files.size)
                    val (name,expected)=item
                    val entry=zip.getEntry(name) ?: error("Backup entry is missing.")
                    require(entry.size==expected.bytes) { "Backup entry size does not match." }
                    if(name.startsWith("originals/")) {
                        val id=name.removePrefix("originals/").removeSuffix(".photo")
                        val target=File(stage,"$id.photo")
                        val actual=zip.getInputStream(entry).use { source->target.outputStream().use { fingerprint(source,it,expected.bytes) } }
                        require(actual==expected && expected==manifest.originals[id]) { "Original checksum mismatch." }
                        sources[id]=target
                    } else {
                        val bytes=zip.getInputStream(entry).use { boundedBytes(it,MAX_METADATA) }
                        require(BackupFile(bytes.size.toLong(),sha(bytes))==expected) { "Project checksum mismatch." }
                        metadata[name]=bytes
                    }
                }
                val library=json.decodeFromString<ProjectLibrary>(metadata.getValue("library.json").toString(Charsets.UTF_8))
                validateLibrary(library)
                val ids=library.projects.flatMap { it.photos }.map { it.id }.toSet()
                require(manifest.originals.keys==ids) { "Original list does not match the projects." }
                require(manifest.files.keys.filter { it.startsWith("layouts/") }.toSet()==library.projects.map { "layouts/"+it.id+".json" }.toSet()) { "Project layout list does not match." }
                val layouts=library.projects.associate { project ->
                    val layout=json.decodeFromString<LightTableLayout>(metadata.getValue("layouts/"+project.id+".json").toString(Charsets.UTF_8))
                    val photoIds=project.photos.map { it.id }.toSet()
                    require(layout.placements.all { it.photoId in photoIds } && layout.filteredPlacements.all { it.photoId in photoIds } &&
                        layout.reviews.keys.all { it in photoIds } && layout.pickedIds.all { it in photoIds } &&
                        (layout.selectedId==null || layout.selectedId in photoIds)) { "A layout refers to an unknown photograph." }
                    project.id to layout
                }
                if(manifest.includesOriginals)require(sources.keys==ids) { "Backup is missing originals." }
                else {
                    require(sources.isEmpty()) { "Unexpected originals in a metadata-only backup." }
                    require(manifest.originals.values.sumOf { it.bytes }<=minOf(MAX_ARCHIVE,stage.usableSpace-RESERVE)) { "Not enough free storage to restore source copies." }
                    store.backupSnapshot { current ->
                        val candidates=current.projects.flatMap { it.photos }.map(store::source).filter { it.isFile }
                        val fingerprints=mutableMapOf<File,BackupFile>()
                        for((id,expected) in manifest.originals) {
                            currentCoroutineContext().ensureActive()
                            progress("Matching original "+(sources.size+1)+" / "+ids.size)
                            var match: File?=null
                            for(file in candidates) {
                                currentCoroutineContext().ensureActive()
                                if(file.length()!=expected.bytes)continue
                                val record=fingerprints[file] ?: file.inputStream().use { fingerprint(it,null,MAX_FILE) }.also { fingerprints[file]=it }
                                if(record==expected){match=file;break}
                            }
                            requireNotNull(match) { "This backup excludes originals. Matching source files are missing on this device. Use a backup that includes originals." }
                            val target=File(stage,"$id.photo")
                            val actual=match.inputStream().use { source->target.outputStream().use { fingerprint(source,it,MAX_FILE) } }
                            require(actual==expected) { "A source changed during validation." }
                            sources[id]=target
                        }
                    }
                }
                require(metadata.keys==setOf("library.json")+library.projects.map { "layouts/"+it.id+".json" }) { "Unexpected backup metadata." }
                ValidatedBackup(library,layouts,sources,stage)
            }
        } catch(failure: Throwable) { stage.deleteRecursively();throw failure }
    }

    private fun validateLibrary(value: ProjectLibrary) {
        require(value.projects.size in 1..100 && value.presets.size<=200) { "Backup exceeds project limits." }
        require(value.projects.map { it.id }.distinct().size==value.projects.size && value.projects.any { it.id==value.activeId })
        require(value.presets.map { it.id }.distinct().size==value.presets.size && value.presets.all { uuid.matches(it.id) })
        val photos=value.projects.flatMap { it.photos }
        require(photos.size<=2000 && photos.map { it.id }.distinct().size==photos.size)
        for(project in value.projects) {
            require(projectId(project.id) && project.name.isNotBlank() && project.name.length<=80 && project.name.none(Char::isISOControl))
            require(project.selectedId==null || project.photos.any { it.id==project.selectedId })
            for(photo in project.photos)require(uuid.matches(photo.id) && photo.displayName.length<=160 &&
                photo.displayName.none(Char::isISOControl) && photo.width in 1..32768 && photo.height in 1..32768 && photo.revision>=0)
        }
        require(value.queue.jobs.isEmpty()) { "Backups cannot replay device-specific export jobs." }
    }
    private suspend fun fingerprint(input: InputStream,output: OutputStream?,limit: Long): BackupFile {
        require(limit>=0) { "Not enough free storage for this backup." }
        val hash=MessageDigest.getInstance("SHA-256");val buffer=ByteArray(65536);var total=0L
        while(true) {
            currentCoroutineContext().ensureActive()
            val n=input.read(buffer);if(n<0)break
            total+=n;require(total<=limit) { "Backup file exceeds the allowed size." }
            hash.update(buffer,0,n);output?.write(buffer,0,n)
        }
        return BackupFile(total,hash.digest().joinToString("") {"%02x".format(it)})
    }
    private suspend fun boundedBytes(input: InputStream,limit: Long): ByteArray =
        ByteArrayOutputStream().also { fingerprint(input,it,limit) }.toByteArray()
    private fun sha(bytes: ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {"%02x".format(it)}
    companion object {
        const val MAX_FILE=512L*1024*1024
        const val MAX_ARCHIVE=16L*1024*1024*1024
        const val MAX_METADATA=4L*1024*1024
        const val RESERVE=64L*1024*1024
    }
}
