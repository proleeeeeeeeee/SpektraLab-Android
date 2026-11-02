// SPDX-License-Identifier: GPL-3.0-only
package io.github.proleeeeeeeeee.spektralab.negative

import android.app.Application
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.proleeeeeeeeee.spektralab.core.negative.*
import io.github.proleeeeeeeeee.spektralab.ui.SpektraTheme
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.min

/** Workflow regression: an existing base or old success must never auto-advance a new sample. */
@RunWith(AndroidJUnit4::class)
class NegativeScanWizardTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val basePixel = Color.rgb(220, 155, 100)
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun fixture(): Uri {
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "synthetic-wizard-${System.nanoTime()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png"); put(MediaStore.Images.Media.IS_PENDING, 1)
        })!!
        val bitmap = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888).apply {
            eraseColor(basePixel)
            for (y in 20 until 80) for (x in 40 until 160) setPixel(x, y, Color.BLACK)
        }
        try { context.contentResolver.openOutputStream(uri)!!.use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
        finally { bitmap.recycle() }
        context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        return uri
    }
    private fun tapPixel(x: Int, y: Int) {
        val viewport = compose.onNodeWithTag("negative-viewport").performScrollTo()
        val size = viewport.fetchSemanticsNode().boundsInRoot.size
        val scale = min(size.width / 200f, size.height / 100f)
        viewport.performTouchInput { click(Offset((size.width - 200 * scale) / 2 + (x + .5f) * scale, (size.height - 100 * scale) / 2 + (y + .5f) * scale)) }
    }
    private fun screenshot(directory: File, name: String) {
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap -> File(directory, "$name.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
    }
    private fun assertSameBase(expected: FilmBase, actual: FilmBase?) {
        assertNotNull("The previous film base must remain available", actual)
        val current = actual!!
        // Permit double roundoff only; 1e-12 is far below an 8-bit input's channel step.
        val epsilon = 1e-12
        assertEquals("Film-base red must be preserved", expected.red, current.red, epsilon)
        assertEquals("Film-base green must be preserved", expected.green, current.green, epsilon)
        assertEquals("Film-base blue must be preserved", expected.blue, current.blue, epsilon)
    }

    @Test fun oldBaseAndOldSuccessCannotSkipResamplingAndFailureStaysBesideTheImage() = runBlocking<Unit> {
        val directory = "negative-wizard-${UUID.randomUUID()}"
        val store = NegativeScanStore(context, directory); val uri = fixture()
        val evidence = File(context.filesDir, "negative-wizard-evidence").apply { mkdirs() }
        val report = JSONObject().put("fixture", "Synthetic orange border with dark image patch; no real-film validation")
        val model = NegativeScanViewModel(context.applicationContext as Application, directory)
        try {
            val photo = store.import(uri)
            val oldBase = NegativeInversion.sample(intArrayOf(basePixel))
            val initial = NegativeRoll(revision = 1, photos = listOf(photo), selectedId = photo.id, settings = NegativeSettings(oldBase))
            store.save(initial)
            val originalHash = sha(store.source(photo).readBytes())
            compose.setContent { SpektraTheme { NegativeScanScreen(onBack = {}, model = model) } }
            compose.waitUntil(10_000) { model.state.value.ready && model.state.value.positive != null }
            compose.onNodeWithTag("negative-step-import").assertExists()
            compose.onNodeWithTag("negative-step-preview").assertDoesNotExist()
            compose.onNodeWithTag("negative-photo-entry").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("negative-import").performScrollTo()
            screenshot(evidence, "01-entry-explanation")
            compose.onNodeWithTag("negative-continue").performScrollTo().performClick()
            compose.onNodeWithTag("negative-step-preview").assertExists()
            compose.onNodeWithTag("negative-sample-mode").performScrollTo().performClick()
            val request = model.state.value.sampleRequestId
            assertTrue(request > 0)
            compose.waitForIdle()
            compose.onNodeWithTag("negative-step-sample").assertExists()
            compose.onNodeWithTag("negative-step-preview").assertDoesNotExist()
            assertSameBase(oldBase, model.state.value.roll.settings.base)
            tapPixel(100, 50)
            compose.waitUntil(10_000) { !model.state.value.sampling && model.state.value.sampleError != null }
            compose.onNodeWithTag("negative-step-sample").assertExists()
            compose.onNodeWithTag("negative-sample-error").performScrollTo().assertIsDisplayed()
            assertNull(model.state.value.sampleSuccess)
            assertSameBase(oldBase, model.state.value.roll.settings.base)
            screenshot(evidence, "02-sample-failure-stays")
            compose.waitUntil(10_000) { model.state.value.negative != null }
            tapPixel(8, 50)
            compose.waitUntil(10_000) { model.state.value.sampleSuccess?.requestId == request && model.state.value.positive != null }
            compose.onNodeWithTag("negative-step-preview").assertExists()
            compose.onNodeWithTag("negative-step-sample").assertDoesNotExist()
            val success = model.state.value.sampleSuccess!!
            assertEquals(photo.id, success.photoId)
            compose.onNodeWithTag("negative-viewport").performScrollTo()
            screenshot(evidence, "03-new-success-auto-preview")
            compose.onNodeWithTag("negative-sample-mode").performScrollTo().performClick()
            val secondRequest = model.state.value.sampleRequestId
            assertTrue(secondRequest > success.requestId)
            compose.waitForIdle()
            compose.onNodeWithTag("negative-step-sample").assertExists()
            assertEquals("An earlier success event cannot advance a new request", success, model.state.value.sampleSuccess)
            compose.onNodeWithTag("negative-sample-cancel").performScrollTo().performClick()
            compose.onNodeWithTag("negative-step-preview").assertExists()
            assertEquals(success, model.state.value.sampleSuccess)
            assertSameBase(oldBase, model.state.value.roll.settings.base)
            compose.runOnIdle { model.sample(150.0, 150.0, 300.0, 300.0, expectedRequestId = secondRequest) }
            assertNull("A cancelled pointer request must be rejected before reading pixels", model.state.value.sampleError)
            assertFalse(model.state.value.sampling)
            assertEquals(success, model.state.value.sampleSuccess)
            assertEquals(originalHash, sha(store.source(photo).readBytes()))
            assertEquals(originalHash, sha(context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }))
            report.put("status", "PASS").put("firstRequest", request).put("nextRequest", secondRequest).put("successSequence", success.sequence)
                .put("originalSha256", originalHash).put("checks", "Explicit continue; failed sample stays in step 2; only matching fresh success enters step 3; re-sample ignores old success; cancel keeps old base")
        } catch (failure: Throwable) {
            report.put("status", "FAIL").put("failure", failure.toString()); runCatching { screenshot(evidence, "failure") }; throw failure
        } finally { File(evidence, "report.json").writeText(report.toString(2)); compose.runOnIdle { model.pause() }; context.contentResolver.delete(uri, null, null) }
    }

    @Test fun unsampledRollOffersSamplingInsteadOfDisabledSaveAndCancelReturnsToExplanation() = runBlocking<Unit> {
        val directory = "negative-wizard-${UUID.randomUUID()}"; val store = NegativeScanStore(context, directory); val uri = fixture()
        val model = NegativeScanViewModel(context.applicationContext as Application, directory)
        try {
            val photo = store.import(uri)
            store.save(NegativeRoll(revision = 1, photos = listOf(photo), selectedId = photo.id))
            compose.setContent { SpektraTheme { NegativeScanScreen(onBack = {}, model = model) } }
            compose.waitUntil(10_000) { model.state.value.ready }
            compose.onNodeWithTag("negative-export").assertDoesNotExist()
            compose.onNodeWithText("开始片基取样").performScrollTo().assertIsEnabled().performClick()
            compose.onNodeWithTag("negative-step-sample").assertExists()
            compose.onNodeWithTag("negative-export").assertDoesNotExist()
            compose.onNodeWithTag("negative-sample-cancel").performScrollTo().performClick()
            compose.onNodeWithTag("negative-step-import").assertExists()
            assertNull(model.state.value.roll.settings.base); assertNull(model.state.value.sampleSuccess)
        } finally { compose.runOnIdle { model.pause() }; context.contentResolver.delete(uri, null, null) }
    }

    @Test fun restoredSampleStepRearmsAgainstANewViewModelAndFreshSuccessSequence() = runBlocking<Unit> {
        val directory = "negative-wizard-${UUID.randomUUID()}"; val store = NegativeScanStore(context, directory); val uri = fixture()
        val application = context.applicationContext as Application
        val original = NegativeScanViewModel(application, directory)
        val replacement = NegativeScanViewModel(application, directory)
        var displayedModel by mutableStateOf(original)
        try {
            val photo = store.import(uri)
            store.save(NegativeRoll(revision = 1, photos = listOf(photo), selectedId = photo.id, settings = NegativeSettings(NegativeInversion.sample(intArrayOf(basePixel)))))
            val restoration = StateRestorationTester(compose)
            restoration.setContent { SpektraTheme { NegativeScanScreen(onBack = {}, model = displayedModel) } }
            compose.waitUntil(10_000) { original.state.value.ready && original.state.value.positive != null }
            compose.onNodeWithTag("negative-continue").performScrollTo().performClick()
            compose.onNodeWithTag("negative-sample-mode").performScrollTo().performClick()
            tapPixel(8, 50)
            compose.waitUntil(10_000) { original.state.value.sampleSuccess != null && original.state.value.positive != null }
            compose.onNodeWithTag("negative-step-preview").assertExists()
            val originalSuccess = original.state.value.sampleSuccess!!
            compose.onNodeWithTag("negative-sample-mode").performScrollTo().performClick()
            assertTrue(original.state.value.sampleRequestId > originalSuccess.requestId)
            compose.onNodeWithTag("negative-step-sample").assertExists()
            compose.runOnIdle { original.pause(); displayedModel = replacement }
            restoration.emulateSavedInstanceStateRestore()
            compose.waitUntil(10_000) { replacement.state.value.ready && replacement.state.value.negative != null && replacement.state.value.sampleRequestId > 0 }
            compose.onNodeWithTag("negative-step-sample").assertExists()
            assertNull(replacement.state.value.sampleSuccess)
            tapPixel(8, 50)
            compose.waitUntil(10_000) { replacement.state.value.sampleSuccess != null && replacement.state.value.positive != null }
            compose.onNodeWithTag("negative-step-preview").assertExists()
            assertEquals(1L, replacement.state.value.sampleSuccess!!.sequence)
        } finally { compose.runOnIdle { original.pause(); replacement.pause() }; context.contentResolver.delete(uri, null, null) }
    }
}
