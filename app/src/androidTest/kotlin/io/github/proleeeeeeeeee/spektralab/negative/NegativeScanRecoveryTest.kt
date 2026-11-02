// SPDX-License-Identifier: GPL-3.0-only
package io.github.proleeeeeeeeee.spektralab.negative

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.proleeeeeeeeee.spektralab.core.negative.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NegativeScanRecoveryTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun id() = UUID.randomUUID().toString()
    private fun fixture(valid: Boolean = true): Uri {
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "synthetic-recovery-${System.nanoTime()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png"); put(MediaStore.Images.Media.IS_PENDING, 1)
        })!!
        context.contentResolver.openOutputStream(uri)!!.use { stream ->
            if (valid) {
                val bitmap = Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(220, 155, 100)) }
                try { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) } finally { bitmap.recycle() }
            } else stream.write(byteArrayOf(1, 2, 3, 4))
        }
        context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        return uri
    }

    @Test fun restartRemovesOnlyControlledOrphansAndPreservesIndexedQueueAndUnknownFiles() = runBlocking {
        val directory = "negative-recovery-${id()}"; val root = File(context.filesDir, directory)
        val store = NegativeScanStore(context, directory); val originals = File(root, "originals")
        val first = NegativePhoto(id(), "indexed.png", 80, 40)
        val queued = NegativePhoto(id(), "queued.png", 80, 40)
        val settings = NegativeSettings(FilmBase(.7, .4, .2))
        val roll = NegativeRoll(revision = 1, photos = listOf(first, queued), selectedId = first.id, settings = settings,
            jobs = listOf(NegativeJob(id(), queued, settings, NegativeFormat.PNG)))
        val indexedFile = File(originals, first.id + ".scan").apply { writeBytes(byteArrayOf(7, 8, 9)) }
        val queuedFile = File(originals, queued.id + ".scan").apply { writeBytes(byteArrayOf(10, 11)) }
        store.save(roll)
        val orphanTemp = File(originals, id() + ".tmp").apply { writeBytes(byteArrayOf(1, 2)) }
        val orphanCopy = File(originals, id() + ".scan").apply { writeBytes(byteArrayOf(3, 4)) }
        val unknown = File(originals, "unrecognized.scan").apply { writeText("keep") }
        val unknownExtension = File(originals, id() + ".txt").apply { writeText("keep") }
        val nested = File(originals, id() + ".scan").apply { mkdirs() }
        val nestedFile = File(nested, id() + ".tmp").apply { writeText("keep") }
        val outside = File(root, id() + ".scan").apply { writeText("outside originals") }
        try {
            assertEquals(roll, NegativeScanStore(context, directory).load())
            assertFalse(orphanTemp.exists()); assertFalse(orphanCopy.exists())
            assertArrayEquals(byteArrayOf(7, 8, 9), indexedFile.readBytes()); assertArrayEquals(byteArrayOf(10, 11), queuedFile.readBytes())
            assertTrue(unknown.isFile); assertTrue(unknownExtension.isFile); assertTrue(nestedFile.isFile); assertTrue(outside.isFile)
            store.discardUnindexed(queued)
            assertTrue("A queue source cannot be discarded", queuedFile.isFile)
        } finally { root.deleteRecursively() }
    }

    @Test fun concurrentLoadsCannotDeleteTheCopiedSourceBeforeItsIndexCommit() = runBlocking {
        val directory = "negative-recovery-${id()}"; val root = File(context.filesDir, directory)
        val store = NegativeScanStore(context, directory); val uri = fixture()
        try {
            val photo = coroutineScope {
                val copying = async(Dispatchers.IO) { store.import(uri) }
                val loads = (1..8).map { async(Dispatchers.IO) { NegativeScanStore(context, directory).load() } }
                val copied = copying.await(); loads.awaitAll(); copied
            }
            val bytes = store.source(photo).readBytes()
            // Explicitly exercise the copy-return/index-save window in a second store instance.
            NegativeScanStore(context, directory).load()
            assertArrayEquals(bytes, store.source(photo).readBytes())
            val roll = NegativeRoll(revision = 1, photos = listOf(photo), selectedId = photo.id)
            coroutineScope {
                val writing = async(Dispatchers.IO) { store.save(roll) }
                val reading = async(Dispatchers.IO) { NegativeScanStore(context, directory).load() }
                writing.await(); reading.await()
            }
            assertEquals(roll, store.load()); assertArrayEquals(bytes, store.source(photo).readBytes())
            store.discardUnindexed(photo)
            assertTrue(store.source(photo).isFile)
        } finally { context.contentResolver.delete(uri, null, null); root.deleteRecursively() }
    }

    @Test fun failedAndAbandonedImportsCleanTheirOwnFilesWithoutReplacingExistingOriginals() = runBlocking {
        val directory = "negative-recovery-${id()}"; val root = File(context.filesDir, directory)
        val store = NegativeScanStore(context, directory); val valid = fixture(); val invalid = fixture(false)
        val abandonedId = id(); val invalidId = id()
        try {
            val copied = store.import(valid, abandonedId)
            store.discardUnindexed(copied)
            assertFalse(File(root, "originals/$abandonedId.scan").exists())
            var rejected = false
            try { store.import(invalid, invalidId) } catch (_: Exception) { rejected = true }
            assertTrue(rejected)
            assertFalse(File(root, "originals/$invalidId.tmp").exists()); assertFalse(File(root, "originals/$invalidId.scan").exists())
            val retained = store.import(valid)
            store.save(NegativeRoll(revision = 1, photos = listOf(retained)))
            val bytes = store.source(retained).readBytes()
            rejected = false
            try { store.import(valid, retained.id) } catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected); assertArrayEquals(bytes, store.source(retained).readBytes())
        } finally { context.contentResolver.delete(valid, null, null); context.contentResolver.delete(invalid, null, null); root.deleteRecursively() }
    }
}
