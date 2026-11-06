// SPDX-License-Identifier: GPL-3.0-only
package io.github.proleeeeeeeeee.spektralab

import android.content.Context
import android.content.ContextWrapper
import io.github.proleeeeeeeeee.spektralab.core.*
import io.github.proleeeeeeeeee.spektralab.data.*
import androidx.test.platform.app.InstrumentationRegistry
import java.io.*
import java.util.UUID
import java.util.zip.*
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ProjectBackupTest {
    private class Fixture: AutoCloseable {
        val root=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"backup-test-"+UUID.randomUUID()).apply { mkdirs() }
        val context=object: ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
            override fun getApplicationContext(): Context=this
            override fun getFilesDir()=File(root,"files").apply { mkdirs() }
            override fun getCacheDir()=File(root,"cache").apply { mkdirs() }
        }
        val photoId=UUID.randomUUID().toString()
        val projectId=UUID.randomUUID().toString()
        val preset=SavedPreset(UUID.randomUUID().toString(),"My look",RecipeTransfer(EditRecipe().withFilm("kodak_portra_400"),true))
        val photo=StoredPhoto(photoId,"Original test.png",recipe=EditRecipe().copy(adjustments=Adjustments(exposure=.7)),width=32,height=24,revision=5)
        val library=ProjectLibrary(projects=listOf(PhotoProject(projectId,"Test project",listOf(photo),photoId)),activeId=projectId,presets=listOf(preset),
            queue=ExportQueue(listOf(ExportJob(UUID.randomUUID().toString(),photoId,photo.recipe,ExportSettings()))))
        val source=File(File(context.filesDir,"imports").apply { mkdirs() },"$photoId.photo")
        val bytes=ByteArray(300_000) { (it%251).toByte() }
        val store=PhotoStore(context)
        val backup=ProjectBackup(context)
        val layout=LightTableLayout.fresh().reconcile(listOf(photoId)).copy(
            placements=listOf(FilmPlacement(photoId,.32,.41,17.0)),
            reviews=mapOf(photoId to PhotoReview(rating=4,flag=ReviewFlag.KEEP,color=ReviewColor.BLUE)),pickedIds=setOf(photoId))
        suspend fun initialize() {
            source.writeBytes(bytes)
            File(context.filesDir,"projects.json").writeText(Json.encodeToString(library))
            LightTableStore(context).save(projectId,layout,7)
        }
        suspend fun archive(originals: Boolean=true): ByteArray=ByteArrayOutputStream().also { backup.write(it,originals) }.toByteArray()
        fun indexBytes()=File(context.filesDir,"projects.json").readBytes()
        override fun close() { root.deleteRecursively() }
    }
    private suspend fun rejected(f: Fixture,bytes: ByteArray) {
        val before=f.indexBytes()
        try { f.backup.read(ByteArrayInputStream(bytes)).close();fail("Invalid backup was accepted") }
        catch(expected: Exception) { /* Validation must fail before live storage changes. */ }
        assertArrayEquals(before,f.indexBytes())
        val staging=File(f.context.cacheDir,"project-backups")
        assertTrue(staging.listFiles().orEmpty().isEmpty())
    }
    private fun rewrite(bytes: ByteArray,change: (String,ByteArray)->ByteArray,extraPath: String?=null): ByteArray {
        val result=ByteArrayOutputStream()
        ZipOutputStream(result).use { out ->
            ZipInputStream(ByteArrayInputStream(bytes)).use { source ->
                while(true) {
                    val entry=source.nextEntry ?: break
                    out.putNextEntry(ZipEntry(entry.name));out.write(change(entry.name,source.readBytes()));out.closeEntry()
                }
            }
            if(extraPath!=null){out.putNextEntry(ZipEntry(extraPath));out.write(byteArrayOf(1));out.closeEntry()}
        }
        return result.toByteArray()
    }
    @Test fun fullRoundTripPreservesRecipesPresetsReviewsLayoutsBytesAndExistingQueue()=runBlocking {
        Fixture().use { f ->
            f.initialize()
            val original=f.store.library()
            f.backup.read(ByteArrayInputStream(f.archive())).use { validated -> f.store.restoreBackup(validated) }
            val restored=f.store.library()
            assertEquals(2,restored.projects.size);assertEquals(original.projects.single(),restored.projects.first())
            assertEquals(original.queue,restored.queue)
            val project=restored.active;val photo=project.photos.single()
            assertNotEquals(f.photoId,photo.id);assertNotEquals(f.projectId,project.id)
            assertEquals(f.photo.recipe,photo.recipe);assertEquals(photo.id,project.selectedId)
            assertArrayEquals(f.bytes,f.store.source(photo).readBytes())
            assertEquals(2,restored.presets.size);assertEquals(f.preset.settings,restored.presets.last().settings)
            assertNotEquals(f.preset.id,restored.presets.last().id)
            val layout=LightTableStore(f.context).load(project.id).layout
            assertEquals(f.layout.reviews.getValue(f.photoId),layout.reviews.getValue(photo.id))
            assertEquals(setOf(photo.id),layout.pickedIds)
            assertEquals(FilmPlacement(photo.id,.32,.41,17.0),layout.placements.single())
        }
    }
    @Test fun metadataOnlyReusesOnlyMatchingOriginalBytes()=runBlocking {
        Fixture().use { f ->
            f.initialize();val bytes=f.archive(false)
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while(true){val entry=zip.nextEntry ?: break;assertFalse(entry.name.startsWith("originals/"))}
            }
            f.backup.read(ByteArrayInputStream(bytes)).use { f.store.restoreBackup(it) }
            assertArrayEquals(f.bytes,f.store.source(f.store.library().active.photos.single()).readBytes())
        }
    }
    @Test fun missingMetadataOnlySourceFailsWithoutChangingProjects()=runBlocking {
        Fixture().use { f -> f.initialize();val bytes=f.archive(false);f.source.delete();rejected(f,bytes) }
    }
    @Test fun changedOriginalFailsItsChecksumBeforeRestore()=runBlocking {
        Fixture().use { f -> f.initialize();rejected(f,rewrite(f.archive(),{name,b->if(name.startsWith("originals/"))b.clone().also {it[0]=(it[0]+1).toByte()} else b})) }
    }
    @Test fun changedMetadataFailsItsChecksumBeforeRestore()=runBlocking {
        Fixture().use { f -> f.initialize();rejected(f,rewrite(f.archive(),{name,b->if(name=="library.json")b+32.toByte() else b})) }
    }
    @Test fun unsafeArchivePathsAreRejectedBeforeExtraction()=runBlocking {
        Fixture().use { f -> f.initialize();rejected(f,rewrite(f.archive(),{_,b->b},"../escape")) }
    }
    @Test fun futureBackupVersionsAreRejected()=runBlocking {
        Fixture().use { f -> f.initialize();rejected(f,rewrite(f.archive(),{name,b->if(name=="manifest.json")b.toString(Charsets.UTF_8).replace("\"version\":1","\"version\":99").toByteArray() else b})) }
    }
    @Test fun cancellingValidationRemovesStagingAndKeepsIndex()=runBlocking {
        Fixture().use { f ->
            f.initialize();val bytes=f.archive();val before=f.indexBytes()
            try { f.backup.read(ByteArrayInputStream(bytes)) {throw CancellationException("Test cancellation")};fail("Should cancel") }
            catch(expected: CancellationException) {}
            assertArrayEquals(before,f.indexBytes())
            assertTrue(File(f.context.cacheDir,"project-backups").listFiles().orEmpty().isEmpty())
        }
    }
    @Test fun failedIndexCommitRollsBackNewSourcesAndLayouts()=runBlocking {
        Fixture().use { f ->
            f.initialize();val before=f.indexBytes();val bytes=f.archive()
            val blocker=File(f.context.filesDir,"projects.json.new").apply { mkdir();File(this,"blocked").writeText("Simulated unwritable staging path") }
            try {
                f.backup.read(ByteArrayInputStream(bytes)).use { validated ->
                    try { f.store.restoreBackup(validated);fail("Commit should fail") } catch(expected: Exception) {}
                }
                assertArrayEquals(before,f.indexBytes())
                assertEquals(setOf(f.photoId+".photo"),File(f.context.filesDir,"imports").listFiles().orEmpty().map {it.name}.toSet())
                assertEquals(setOf(f.projectId+".json"),File(f.context.filesDir,"light-tables").listFiles().orEmpty().map {it.name}.toSet())
            } finally { blocker.deleteRecursively() }
        }
    }
    @Test fun repeatedRestoresUseNewIdsWithoutOverwriting()=runBlocking {
        Fixture().use { f ->
            f.initialize();val bytes=f.archive()
            repeat(3){f.backup.read(ByteArrayInputStream(bytes)).use { f.store.restoreBackup(it) }}
            val current=f.store.library()
            assertEquals(4,current.projects.size)
            assertEquals(4,current.projects.map {it.id}.toSet().size)
            assertEquals(4,current.projects.flatMap {it.photos}.map {it.id}.toSet().size)
            assertArrayEquals(f.bytes,f.source.readBytes())
        }
    }
    @Test fun truncatedArchiveIsRejectedWithoutChangingProjects()=runBlocking {
        Fixture().use { f -> f.initialize();val archive=f.archive();rejected(f,archive.copyOf(archive.size/2)) }
    }
    @Test fun cancellingWriteKeepsSourcesAndIndexAndAllowsRetry()=runBlocking {
        Fixture().use { f ->
            f.initialize();val before=f.indexBytes()
            try { f.backup.write(ByteArrayOutputStream(),true) {throw CancellationException("Cancel save")};fail("Should cancel") }
            catch(expected: CancellationException) {}
            assertArrayEquals(before,f.indexBytes());assertArrayEquals(f.bytes,f.source.readBytes())
            f.backup.read(ByteArrayInputStream(f.archive())).use { assertEquals(1,it.library.projects.size) }
        }
    }
    @Test fun staleStoreQueueWriteCannotLoseRestoredProjectsOrPresets()=runBlocking {
        Fixture().use { f ->
            f.initialize();val stale=PhotoStore(f.context);stale.library()
            f.backup.read(ByteArrayInputStream(f.archive())).use { f.store.restoreBackup(it) }
            stale.saveQueue(ExportQueue())
            assertEquals(2,f.store.library().projects.size);assertEquals(2,f.store.library().presets.size)
        }
    }
}
