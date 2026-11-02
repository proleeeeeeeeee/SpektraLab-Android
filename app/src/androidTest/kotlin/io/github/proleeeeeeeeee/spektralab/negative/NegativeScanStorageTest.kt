// SPDX-License-Identifier: GPL-3.0-only
package io.github.proleeeeeeeeee.spektralab.negative

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.proleeeeeeeeee.spektralab.core.ExportFormat
import io.github.proleeeeeeeeee.spektralab.core.negative.*
import io.github.proleeeeeeeeee.spektralab.data.PhotoExporter
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic fixtures only: these checks make no claim of validation with real film captures. */
@RunWith(AndroidJUnit4::class)
class NegativeScanStorageTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun fixture(bitmap: Bitmap): Uri {
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "synthetic-negative-${System.nanoTime()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        })!!
        resolver.openOutputStream(uri)!!.use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        return uri
    }
    private fun testStore() = NegativeScanStore(context, "negative-test-${UUID.randomUUID()}")

    @Test fun preservesOriginalBytesAndRestoresPhotoJobAssociation() = runBlocking {
        val image = Bitmap.createBitmap(80, 60, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(220, 155, 100)) }
        val uri = fixture(image); image.recycle()
        var sourceDeleted = false
        try {
            val original = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
            val store = testStore(); val photo = store.import(uri)
            assertArrayEquals(original, store.source(photo).readBytes())
            val base = NegativeRaster.sample(store.source(photo), photo, NegativePoint(10.0, 12.0))
            val settings = NegativeSettings(base, exposure = 1.25)
            val job = NegativeJob(UUID.randomUUID().toString(), photo.copy(turns = 1), settings, NegativeFormat.PNG, NegativeJobStatus.RUNNING, "content://pending/123")
            val roll = NegativeRoll(revision = 4, photos = listOf(photo), selectedId = photo.id, settings = settings, jobs = listOf(job))
            store.save(roll)
            assertEquals(roll, store.load())
            store.save(roll.copy(revision = 3, settings = settings.copy(exposure = -1.0)))
            assertEquals("An older asynchronous save cannot overwrite the roll", roll, store.load())
            assertEquals(1, context.contentResolver.delete(uri, null, null))
            sourceDeleted = true
            assertTrue(store.source(photo).isFile)
        } finally { if (!sourceDeleted) context.contentResolver.delete(uri, null, null) }
    }

    @Test fun quarterTurnFitTapSamplesExactUnscaledPatch() = runBlocking {
        val image = Bitmap.createBitmap(100, 50, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(40, 40, 40))
            for (y in 8..12) for (x in 23..27) setPixel(x, y, Color.rgb(220, 155, 100))
        }
        val uri = fixture(image); image.recycle()
        try {
            val store = testStore(); val photo = store.import(uri).copy(turns = 1)
            val point = NegativeGeometry.sourcePoint(195.0, 75.0, 300.0, 300.0, photo.width, photo.height, photo.turns)!!
            val base = NegativeRaster.sample(store.source(photo), photo, point)
            val expected = NegativeInversion.sample(intArrayOf(Color.rgb(220, 155, 100)))
            assertEquals(expected.red, base.red, 1e-5); assertEquals(expected.green, base.green, 1e-5); assertEquals(expected.blue, base.blue, 1e-5)
            val source = NegativeRaster.decode(store.source(photo), 1024)
            val output = NegativeRaster.invert(source, NegativeSettings(base), 1)
            try { assertEquals(50, output.width); assertEquals(100, output.height); assertEquals(Color.BLACK, output.getPixel(39, 25)) }
            finally { output.recycle(); source.recycle() }
        } finally { context.contentResolver.delete(uri, null, null) }
    }

    @Test fun jpegExifOrientationDefinesTheOrientedSamplingCoordinates() {
        val image = Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(220, 155, 100)) }
        val file = File(context.cacheDir, "negative-oriented-${System.nanoTime()}.jpg")
        try {
            val bytes = java.io.ByteArrayOutputStream().use { stream -> image.compress(Bitmap.CompressFormat.JPEG, 100, stream); stream.toByteArray() }
            // Big-endian TIFF IFD with Orientation=6 (90 degrees clockwise).
            val exif = byteArrayOf(0x45, 0x78, 0x69, 0x66, 0, 0, 0x4d, 0x4d, 0, 0x2a, 0, 0, 0, 8,
                0, 1, 1, 0x12, 0, 3, 0, 0, 0, 1, 0, 6, 0, 0, 0, 0, 0, 0)
            file.outputStream().use { out -> out.write(bytes, 0, 2); out.write(byteArrayOf(0xff.toByte(), 0xe1.toByte(), 0, (exif.size + 2).toByte())); out.write(exif); out.write(bytes, 2, bytes.size - 2) }
            assertEquals(40 to 80, NegativeRaster.inspect(file))
            val photo = NegativePhoto(UUID.randomUUID().toString(), "oriented.jpg", 40, 80)
            val base = NegativeRaster.sample(file, photo, NegativePoint(20.0, 40.0))
            assertTrue(base.red > base.green && base.green > base.blue)
            val decoded = NegativeRaster.decode(file, 1024)
            try { assertEquals(40, decoded.width); assertEquals(80, decoded.height) } finally { decoded.recycle() }
        } finally { image.recycle(); file.delete() }
    }

    @Test fun failedAndCancelledPublishRemovePendingRows() = runBlocking {
        val bitmap = Bitmap.createBitmap(12, 10, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }
        val exporter = PhotoExporter(context)
        try {
            for (cancel in listOf(false, true)) {
                var pending: Uri? = null
                try {
                    exporter.save(bitmap, "negative-failure", ExportFormat.PNG, 95) { uri ->
                        pending = uri
                        if (cancel) throw CancellationException("synthetic cancellation") else error("synthetic write failure")
                    }
                    fail("The injected failure must propagate")
                } catch (_: CancellationException) { assertTrue(cancel) }
                catch (_: IllegalStateException) { assertFalse(cancel) }
                assertNotNull(pending)
                // Query the owned collection: a removed item URI itself can revoke access on API 35.
                context.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Images.Media._ID), "${MediaStore.Images.Media._ID} = ?",
                    arrayOf(android.content.ContentUris.parseId(pending!!).toString()), null)!!.use {
                    assertFalse("Failed/cancelled publication must leave no MediaStore row", it.moveToFirst())
                }
                assertFalse(exporter.isPublished(pending!!))
            }
        } finally { bitmap.recycle() }
    }

    @Test fun serialMultiCaptureExportIsBoundedAndBothFormatsAreDecodable() = runBlocking {
        val image = Bitmap.createBitmap(3200, 160, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(120, 80, 50)) }
        val uri = fixture(image); image.recycle()
        val exporter = PhotoExporter(context)
        try {
            val store = testStore(); val photo = store.import(uri)
            val base = NegativeInversion.sample(intArrayOf(Color.rgb(220, 155, 100)))
            val snapshots = (1..16).map { NegativeJob(UUID.randomUUID().toString(), photo.copy(turns = it % 4), NegativeSettings(base, exposure = it / 16.0), if (it % 2 == 0) NegativeFormat.PNG else NegativeFormat.JPEG) }
            for (job in snapshots) {
                val source = NegativeRaster.decode(store.source(job.photo), NEGATIVE_EXPORT_EDGE)
                assertTrue(maxOf(source.width, source.height) <= NEGATIVE_EXPORT_EDGE)
                val output = try { NegativeRaster.invert(source, job.settings, job.photo.turns) } finally { source.recycle() }
                val saved = try { exporter.save(output, "synthetic-positive", if (job.format == NegativeFormat.PNG) ExportFormat.PNG else ExportFormat.JPEG, 95) } finally { output.recycle() }
                try {
                    val decoded = context.contentResolver.openInputStream(saved)!!.use(BitmapFactory::decodeStream)
                    try { assertTrue(maxOf(decoded.width, decoded.height) <= NEGATIVE_EXPORT_EDGE); assertTrue(exporter.isPublished(saved)) } finally { decoded.recycle() }
                } finally { context.contentResolver.delete(saved, null, null) }
            }
        } finally { context.contentResolver.delete(uri, null, null) }
    }

    @Test fun rejectsFileUrisAndSixteenBitPngHeader() = runBlocking {
        var rejected = false
        try { testStore().import(Uri.parse("file:///sdcard/not-selected.png")) } catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
        val file = File(context.cacheDir, "negative-sixteen-${System.nanoTime()}.png")
        try {
            file.writeBytes(ByteArray(25).apply { this[0] = 0x89.toByte(); this[1] = 0x50; this[2] = 0x4e; this[3] = 0x47; this[24] = 16 })
            rejected = false
            try { NegativeRaster.inspect(file) } catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected)
        } finally { file.delete() }
    }
}
