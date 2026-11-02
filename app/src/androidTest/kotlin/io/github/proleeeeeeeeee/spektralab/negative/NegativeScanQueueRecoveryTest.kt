// SPDX-License-Identifier: GPL-3.0-only
package io.github.proleeeeeeeeee.spektralab.negative

import android.app.Application
import android.content.ContentUris
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import io.github.proleeeeeeeeee.spektralab.ui.SpektraTheme
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.proleeeeeeeeee.spektralab.core.negative.*
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NegativeScanQueueRecoveryTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private data class OutputRow(val uri: Uri, val name: String, val pending: Int)
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun pixel(index: Int, x: Int, y: Int) = if (x < 8 || x >= 72 || y < 8 || y >= 32) Color.rgb(220, 155, 100)
        else if (index == 0) Color.rgb(125, 65, 40) else Color.rgb(90, 100, 45)
    private fun fixture(prefix: String, index: Int): Uri {
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$prefix-$index.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png"); put(MediaStore.Images.Media.IS_PENDING, 1)
        })!!
        val bitmap = Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888)
        try {
            val pixels = IntArray(80 * 40) { pixel(index, it % 80, it / 80) }
            bitmap.setPixels(pixels, 0, 80, 0, 0, 80, 40)
            context.contentResolver.openOutputStream(uri)!!.use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { bitmap.recycle() }
        context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        return uri
    }
    private fun outputs(prefix: String): List<OutputRow> {
        val result = mutableListOf<OutputRow>()
        context.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.IS_PENDING),
            "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?", arrayOf("$prefix%positive%"), null)?.use { rows ->
            while (rows.moveToNext()) result += OutputRow(ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, rows.getLong(0)), rows.getString(1), rows.getInt(2))
        }
        return result
    }
    private fun screenshot(directory: File, name: String) {
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            File(directory, "$name.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        }
    }

    @Test fun failedCaptureRetriesThroughUiWithoutRepeatingTheCompletedSnapshot() = runBlocking<Unit> {
        val token = UUID.randomUUID().toString()
        val prefix = "negative-queue-$token"
        val directory = "negative-queue-test-$token"
        val evidence = File(context.filesDir, "negative-queue-recovery-evidence").apply { mkdirs() }
        val model = NegativeScanViewModel(context.applicationContext as Application, directory)
        val store = NegativeScanStore(context, directory)
        val fixtures = mutableListOf<Uri>()
        var damagedFile: File? = null; var damagedBytes: ByteArray? = null
        val report = JSONObject().put("fixture", "Two synthetic opaque negative captures; no real-film validation").put("status", "RUNNING")
        try {
            repeat(2) { fixtures += fixture(prefix, it) }
            val originalBytes = fixtures.map { uri -> context.contentResolver.openInputStream(uri)!!.use { it.readBytes() } }
            val originalHashes = originalBytes.map(::sha)
            compose.setContent { SpektraTheme { NegativeScanScreen(onBack = {}, model = model) } }
            compose.waitUntil(10_000) { model.state.value.ready }
            compose.runOnIdle { model.import(fixtures) }
            compose.waitUntil(20_000) { model.state.value.roll.photos.size == 2 && !model.state.value.importing && model.state.value.negative != null }
            val photos = model.state.value.roll.photos
            compose.onNodeWithTag("negative-continue").performScrollTo().performClick()
            // The selected second image's clear border maps to original pixel (3,20).
            compose.runOnIdle { model.sample(11.25, 150.0, 300.0, 300.0) }
            compose.waitUntil(20_000) { !model.state.value.sampling && model.state.value.positive != null }
            val sampled = model.state.value.roll.settings
            assertNotNull(sampled.base)
            val snapshot = sampled.copy(exposure = .4, temperature = .2, tint = -.1)
            compose.runOnIdle { model.settings(snapshot); model.commit() }
            damagedFile = store.source(photos[0]); damagedBytes = damagedFile!!.readBytes()
            damagedFile!!.writeBytes(byteArrayOf(0, 1, 2, 3))
            compose.runOnIdle { model.export(all = true, format = NegativeFormat.PNG) }
            compose.waitUntil(30_000) {
                val state = model.state.value
                !state.exporting && state.roll.jobs.size == 2 && state.roll.jobs.count { it.status == NegativeJobStatus.FAILED } == 1 && state.roll.jobs.count { it.status == NegativeJobStatus.COMPLETE } == 1
            }
            val firstJobs = model.state.value.roll.jobs
            val failed = firstJobs.single { it.status == NegativeJobStatus.FAILED }
            val complete = firstJobs.single { it.status == NegativeJobStatus.COMPLETE }
            assertEquals(photos[0].id, failed.photo.id); assertEquals(photos[1].id, complete.photo.id)
            assertTrue(firstJobs.all { it.settings == snapshot && it.format == NegativeFormat.PNG })
            val completedUri = complete.outputUri!!
            val completedHash = sha(context.contentResolver.openInputStream(Uri.parse(completedUri))!!.use { it.readBytes() })
            val firstRows = outputs(prefix)
            assertEquals(1, firstRows.size); assertTrue(firstRows.all { it.pending == 0 })
            compose.onNodeWithText("Retry failed jobs").performScrollTo()
            screenshot(evidence, "01-one-failed-one-complete")

            // A stable lifecycle boundary: the failed job waits for explicit retry, so no
            // timing-sensitive attempt is made to interrupt these very small encodes.
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            assertNull(model.state.value.negative); assertNull(model.state.value.positive)
            assertEquals(firstJobs, model.state.value.roll.jobs)
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            compose.waitUntil(20_000) { model.state.value.previewId == photos[1].id && model.state.value.positive != null }
            assertEquals(firstJobs, model.state.value.roll.jobs)
            assertEquals(completedUri, model.state.value.roll.jobs.single { it.id == complete.id }.outputUri)

            damagedFile!!.writeBytes(damagedBytes!!)
            assertEquals(originalHashes[0], sha(damagedFile!!.readBytes()))
            // Deliberately change the current roll. Retry must retain its original snapshot.
            compose.runOnIdle { model.settings(snapshot.copy(exposure = -.75, temperature = -.3, tint = .3)); model.commit() }
            compose.onNodeWithText("Retry failed jobs").performScrollTo().performClick()
            compose.waitUntil(30_000) { !model.state.value.exporting && model.state.value.roll.jobs.size == 2 && model.state.value.roll.jobs.all { it.status == NegativeJobStatus.COMPLETE } }
            val finalJobs = model.state.value.roll.jobs
            assertEquals(firstJobs.map { it.id }, finalJobs.map { it.id })
            assertTrue(finalJobs.all { it.settings == snapshot })
            assertEquals(completedUri, finalJobs.single { it.id == complete.id }.outputUri)
            assertEquals(completedHash, sha(context.contentResolver.openInputStream(Uri.parse(completedUri))!!.use { it.readBytes() }))
            val finalRows = outputs(prefix)
            assertEquals("One published output per job, including any auto-renamed duplicate", 2, finalRows.size)
            assertEquals(2, finalRows.map { it.uri }.distinct().size)
            assertTrue("No pending MediaStore output may remain", finalRows.all { it.pending == 0 })
            assertEquals(finalJobs, store.load().jobs)
            val checks = JSONArray()
            for (job in finalJobs) {
                val index = photos.indexOfFirst { it.id == job.photo.id }
                val decoded = context.contentResolver.openInputStream(Uri.parse(job.outputUri!!))!!.use(BitmapFactory::decodeStream)
                try {
                    assertEquals(NegativeInversion.pixel(pixel(index, 30, 20), snapshot), decoded.getPixel(30, 20))
                    assertEquals(originalHashes[index], sha(store.source(job.photo).readBytes()))
                    assertEquals(originalHashes[index], sha(context.contentResolver.openInputStream(fixtures[index])!!.use { it.readBytes() }))
                    checks.put(JSONObject().put("jobId", job.id).put("photoId", job.photo.id).put("outputUri", job.outputUri)
                        .put("snapshot", Json.encodeToString(job.settings)).put("sourceSha256", originalHashes[index]))
                } finally { decoded.recycle() }
            }
            compose.onNodeWithText("Export queue: 2 saved, 0 pending, 0 failed").performScrollTo()
            screenshot(evidence, "02-retried-without-duplicate")
            report.put("status", "PASS").put("initialCompletedOutputs", firstRows.size).put("finalCompletedOutputs", finalRows.size)
                .put("pendingRows", finalRows.count { it.pending != 0 }).put("checks", checks)
                .put("lifecycle", "Activity CREATED then RESUMED while failed snapshot awaited explicit retry; displayed bitmaps released and restored")
                .put("notCovered", "No forced process kill or timing-dependent mid-encode pause; real negatives unavailable")
        } catch (failure: Throwable) {
            report.put("status", "FAIL").put("failure", failure.toString())
            runCatching { screenshot(evidence, "failure") }
            throw failure
        } finally {
            if (damagedFile != null && damagedBytes != null) damagedFile!!.writeBytes(damagedBytes!!)
            File(evidence, "report.json").writeText(report.toString(2))
            compose.runOnIdle { model.pause() }
            outputs(prefix).map { it.uri }.distinct().forEach { context.contentResolver.delete(it, null, null) }
            fixtures.forEach { context.contentResolver.delete(it, null, null) }
        }
    }
}
