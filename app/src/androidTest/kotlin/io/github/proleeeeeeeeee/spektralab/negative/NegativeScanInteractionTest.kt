// SPDX-License-Identifier: GPL-3.0-only
package io.github.proleeeeeeeeee.spektralab.negative

import android.app.Application
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import io.github.proleeeeeeeeee.spektralab.ui.SpektraTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
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
import kotlin.math.*

/** Actual pointer/slider/chip/checkbox/button interactions with known synthetic negatives. */
@RunWith(AndroidJUnit4::class)
class NegativeScanInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val basePixel = Color.rgb(220, 155, 100)
    private val knownBase = NegativeInversion.sample(intArrayOf(basePixel))
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun sourcePixel(index: Int, x: Int, y: Int): Int {
        if (x < 16 || x >= 184 || y < 16 || y >= 84) return basePixel
        val positive = if (index == 0) {
            if (x < 100) doubleArrayOf(.50, .15, .03) else doubleArrayOf(.06, .45, .12)
        } else {
            if (x < 100) doubleArrayOf(.12, .08, .60) else doubleArrayOf(.40, .07, .25)
        }
        if (y >= 50) positive.reverse()
        val base = doubleArrayOf(knownBase.red, knownBase.green, knownBase.blue)
        val channels = IntArray(3) { c -> (NegativeInversion.encoded(base[c] / (1 + positive[c] * 7)) * 255).roundToInt() }
        return Color.rgb(channels[0], channels[1], channels[2])
    }
    private fun fixture(index: Int): Uri {
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "synthetic-negative-interaction-$index-${System.nanoTime()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png"); put(MediaStore.Images.Media.IS_PENDING, 1)
        })!!
        val bitmap = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        try {
            val pixels = IntArray(200 * 100) { sourcePixel(index, it % 200, it / 200) }
            bitmap.setPixels(pixels, 0, 200, 0, 0, 200, 100)
            context.contentResolver.openOutputStream(uri)!!.use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { bitmap.recycle() }
        context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        return uri
    }
    private fun screenshot(directory: File, name: String) {
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            File(directory, "$name.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        }
    }
    private fun dragSlider(description: String, endFraction: Float) {
        val node = compose.onNodeWithContentDescription(description).performScrollTo()
        val bounds = node.fetchSemanticsNode().boundsInRoot.size
        node.performTouchInput {
            swipe(Offset(bounds.width * .5f, bounds.height * .5f), Offset(bounds.width * endFraction, bounds.height * .5f), 350)
        }
    }
    private fun awaitPreview(model: NegativeScanViewModel, id: String) {
        compose.waitUntil(20_000) { val state = model.state.value; state.previewId == id && !state.loading && state.positive != null }
        assertNull(model.state.value.error)
    }

    @Test fun touchSampleGradeRotateSwitchAndExportWholeRollAsPng() = runBlocking<Unit> {
        val directoryName = "negative-interaction-${UUID.randomUUID()}"
        val evidence = File(context.filesDir, "negative-interaction-evidence").apply { mkdirs() }
        val report = JSONObject().put("fixture", "Known synthetic orange-base multicolor negatives; no real-film validation")
            .put("status", "RUNNING").put("inputOutput", "8-bit sRGB PNG; 200x100 capture pixels")
        val model = NegativeScanViewModel(context.applicationContext as Application, directoryName)
        val store = NegativeScanStore(context, directoryName)
        val fixtures = mutableListOf<Uri>(); val outputs = mutableListOf<Uri>()
        try {
            repeat(2) { fixtures += fixture(it) }
            val originalHashes = fixtures.map { uri -> sha(context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }) }
            compose.setContent { SpektraTheme { NegativeScanScreen(onBack = {}, model = model) } }
            compose.waitUntil(10_000) { model.state.value.ready }
            // Import fixtures directly; every subsequent operation uses the actual screen controls.
            compose.runOnIdle { model.import(fixtures) }
            compose.waitUntil(20_000) { !model.state.value.importing && model.state.value.roll.photos.size == 2 && model.state.value.negative != null }
            assertNull(model.state.value.error)
            val photos = model.state.value.roll.photos
            compose.onNodeWithTag("negative-import").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("negative-photo-${photos[0].id}").performScrollTo().assertIsDisplayed().performClick()
            compose.waitUntil(20_000) { model.state.value.previewId == photos[0].id && model.state.value.negative != null }
            // First reveal the vertical parent; performScrollTo on the chip itself only scrolls its horizontal row.
            compose.onNodeWithText("Black-and-white negative").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("negative-sample-mode").performScrollTo().assertIsDisplayed().performClick()
            val viewport = compose.onNodeWithTag("negative-viewport").performScrollTo()
            val size = viewport.fetchSemanticsNode().boundsInRoot.size
            val scale = min(size.width / 200f, size.height / 100f)
            viewport.performTouchInput { click(Offset((size.width - 200 * scale) / 2 + 8 * scale, size.height / 2)) }
            compose.waitUntil(20_000) { !model.state.value.sampling && model.state.value.positive != null }
            val sampled = model.state.value.roll.settings.base!!
            assertEquals(knownBase.red, sampled.red, 1e-5); assertEquals(knownBase.green, sampled.green, 1e-5); assertEquals(knownBase.blue, sampled.blue, 1e-5)
            report.put("sample", Json.encodeToString(sampled)).put("sampleMethod", "Pointer tap at actual viewport fit position of original pixel (8,50)")
            // First reveal the vertical parent; performScrollTo on the chip itself only scrolls its horizontal row.
            compose.onNodeWithText("Black-and-white negative").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("negative-sample-mode").performScrollTo().assertIsDisplayed().performClick()
            compose.onNodeWithTag("negative-sample-mode").assertIsNotSelected()
            val before = model.state.value.positive!!.getPixel(60, 40)
            compose.onNodeWithTag("negative-viewport").performScrollTo()
            compose.onNodeWithContentDescription("Creative positive preview").assertIsDisplayed()
            screenshot(evidence, "01-sampled-positive")

            dragSlider("Positive exposure (EV)", .60f)
            dragSlider("Warm / cool balance (EV)", .62f)
            dragSlider("Green / magenta balance (EV)", .40f)
            awaitPreview(model, photos[0].id)
            val graded = model.state.value.roll.settings
            assertTrue("Exposure slider must change the roll", graded.exposure > .1)
            assertTrue("Temperature slider must change the roll", graded.temperature > .1)
            assertTrue("Tint slider must change the roll", graded.tint < -.1)
            assertNotEquals("UI grade must change actual preview pixels", before, model.state.value.positive!!.getPixel(60, 40))
            compose.onNodeWithText("Black-and-white negative").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Rotate 90°").performScrollTo().assertIsDisplayed().performClick()
            awaitPreview(model, photos[0].id)
            assertEquals(1, model.state.value.roll.photos[0].turns)
            assertEquals(100, model.state.value.positive!!.width); assertEquals(200, model.state.value.positive!!.height)
            compose.onNodeWithTag("negative-viewport").performScrollTo()
            compose.onNodeWithTag("negative-sample-mode").assertIsNotSelected()
            compose.onNodeWithContentDescription("Creative positive preview").assertIsDisplayed()
            screenshot(evidence, "02-adjusted-rotated-positive")

            compose.onNodeWithTag("negative-import").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("negative-photo-${photos[1].id}").performScrollTo().assertIsDisplayed().performClick()
            awaitPreview(model, photos[1].id)
            assertEquals(graded, model.state.value.roll.settings)
            assertEquals(200, model.state.value.positive!!.width)
            compose.onNodeWithTag("negative-viewport").performScrollTo()
            screenshot(evidence, "03-second-capture-shared-grade")
            compose.onNodeWithText("PNG", substring = false).performScrollTo().performClick()
            compose.onNodeWithText("Export all 2 captures with current roll settings").performScrollTo()
            compose.onAllNodes(isToggleable()).assertCountEquals(2)[1].performScrollTo().performClick()
            compose.onNodeWithText("Save roll positives").performScrollTo().performClick()
            compose.waitUntil(30_000) { !model.state.value.exporting && model.state.value.roll.jobs.size == 2 && model.state.value.roll.jobs.all { it.status == NegativeJobStatus.COMPLETE } }
            assertNull(model.state.value.error)
            val jobs = model.state.value.roll.jobs
            assertEquals(photos.map { it.id }.toSet(), jobs.map { it.photo.id }.toSet())
            assertTrue(jobs.all { it.format == NegativeFormat.PNG && it.settings == graded })
            val checks = JSONArray()
            for (job in jobs) {
                val index = photos.indexOfFirst { it.id == job.photo.id }
                val uri = Uri.parse(job.outputUri!!); outputs += uri
                val decoded = context.contentResolver.openInputStream(uri)!!.use(BitmapFactory::decodeStream)
                try {
                    val expectedWidth = if (job.photo.turns % 2 == 0) 200 else 100
                    val expectedHeight = if (job.photo.turns % 2 == 0) 100 else 200
                    assertEquals(expectedWidth, decoded.width); assertEquals(expectedHeight, decoded.height)
                    val x = 60; val y = 40
                    val outputPoint = if (job.photo.turns == 1) 99 - y to x else x to y
                    val expected = NegativeInversion.pixel(sourcePixel(index, x, y), job.settings)
                    val actual = decoded.getPixel(outputPoint.first, outputPoint.second)
                    assertEquals("PNG readback must match this capture's frozen grade and rotation", expected, actual)
                    val preservedHash = sha(store.source(job.photo).readBytes())
                    assertEquals("Private original bytes must remain unchanged", originalHashes[index], preservedHash)
                    assertEquals("Selected gallery input bytes must remain unchanged", originalHashes[index], sha(context.contentResolver.openInputStream(fixtures[index])!!.use { it.readBytes() }))
                    checks.put(JSONObject().put("photoId", job.photo.id).put("turns", job.photo.turns).put("outputSize", "${decoded.width}x${decoded.height}")
                        .put("expectedPixel", expected).put("actualPixel", actual).put("originalSha256", preservedHash).put("format", job.format.name))
                } finally { decoded.recycle() }
            }
            assertEquals(jobs, store.load().jobs)
            compose.onNodeWithText("Export queue: 2 saved · 0 pending · 0 failed").performScrollTo()
            screenshot(evidence, "04-roll-png-export-complete")
            report.put("settings", Json.encodeToString(graded)).put("readback", checks).put("status", "PASS")
                .put("coverage", "Pointer film-base sample; touch exposure/temperature/tint sliders; rotate; switch captures; PNG chip; all-roll checkbox; export button; output pixel/dimension and input SHA-256 checks")
                .put("notCovered", "Real negatives; background/cancel/failure tests belong to separate tests; no measured peak-memory assertion")
        } catch (failure: Throwable) {
            report.put("status", "FAIL").put("failure", failure.toString())
                .put("state", "selected=${model.state.value.roll.selectedId}, preview=${model.state.value.previewId}, loading=${model.state.value.loading}, error=${model.state.value.error}")
            runCatching { screenshot(evidence, "failure") }
            throw failure
        } finally {
            File(evidence, "report.json").writeText(report.toString(2))
            compose.runOnIdle { model.pause() }
            outputs.forEach { context.contentResolver.delete(it, null, null) }
            fixtures.forEach { context.contentResolver.delete(it, null, null) }
        }
    }
}
