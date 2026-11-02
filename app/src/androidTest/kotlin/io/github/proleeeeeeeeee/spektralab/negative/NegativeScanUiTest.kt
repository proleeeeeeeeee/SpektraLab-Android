// SPDX-License-Identifier: GPL-3.0-only
package io.github.proleeeeeeeeee.spektralab.negative

import android.app.Application
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Color
import android.provider.MediaStore
import io.github.proleeeeeeeeee.spektralab.ui.SpektraTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.proleeeeeeeeee.spektralab.core.ExportFormat
import io.github.proleeeeeeeeee.spektralab.core.negative.*
import io.github.proleeeeeeeeee.spektralab.data.PhotoExporter
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NegativeScanUiTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun emptyImportCalibrationDisclosureAndBackAreReachable() {
        val directory = "negative-test-${UUID.randomUUID()}"
        val model = NegativeScanViewModel(context.applicationContext as Application, directory)
        var returned = false
        try {
            compose.setContent { SpektraTheme { NegativeScanScreen(onBack = { returned = true }, model = model) } }
            compose.waitUntil(10_000) { model.state.value.ready }
            compose.onNodeWithTag("negative-empty").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("negative-import").assertIsEnabled()
            compose.onNodeWithTag("negative-calibration").performClick()
            compose.onNodeWithText("尚不提供校准重建").assertIsDisplayed()
            compose.onNodeWithText("知道了").performClick()
            compose.onNodeWithText("返回").performScrollTo().performClick()
            assertTrue(returned)
        } finally { compose.runOnIdle { model.pause() }; File(context.filesDir, directory).deleteRecursively() }
    }

    @Test fun syntheticCapturesStayAssociatedAcrossSamplingSelectionAndPause() = runBlocking {
        val directory = "negative-test-${UUID.randomUUID()}"
        val model = NegativeScanViewModel(context.applicationContext as Application, directory)
        val uris = mutableListOf<android.net.Uri>()
        try {
            for (color in listOf(Color.rgb(220, 155, 100), Color.rgb(130, 90, 60))) {
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "synthetic-negative-${System.nanoTime()}.png")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png"); put(MediaStore.Images.Media.IS_PENDING, 1)
                })!!
                uris += uri
                val bitmap = Bitmap.createBitmap(100, 50, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
                try { context.contentResolver.openOutputStream(uri)!!.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } } finally { bitmap.recycle() }
                context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            }
            compose.setContent { SpektraTheme { NegativeScanScreen(onBack = {}, model = model) } }
            compose.waitUntil(10_000) { model.state.value.ready }
            compose.runOnIdle { model.import(uris) }
            compose.waitUntil(20_000) { !model.state.value.importing && model.state.value.roll.photos.size == 2 && model.state.value.negative != null }
            val first = model.state.value.roll.photos.first(); val second = model.state.value.roll.photos.last()
            assertEquals(second.id, model.state.value.previewId)
            compose.runOnIdle { model.select(first.id) }
            assertNull(model.state.value.positive)
            compose.waitUntil(10_000) { model.state.value.previewId == first.id && model.state.value.negative != null }
            compose.runOnIdle { model.sample(150.0, 150.0, 300.0, 300.0) }
            compose.waitUntil(10_000) { !model.state.value.sampling && model.state.value.positive != null }
            assertEquals(Color.BLACK, model.state.value.positive!!.getPixel(50, 25))
            compose.runOnIdle { model.select(second.id) }
            compose.waitUntil(10_000) { model.state.value.previewId == second.id && model.state.value.positive != null }
            assertNotEquals(Color.BLACK, model.state.value.positive!!.getPixel(50, 25))
            compose.runOnIdle { model.pause() }
            assertNull(model.state.value.negative); assertNull(model.state.value.positive)
            compose.runOnIdle { model.activate() }
            compose.waitUntil(10_000) { model.state.value.previewId == second.id && model.state.value.positive != null }
            assertEquals(second.id, NegativeScanStore(context, directory).load().selectedId)
        } finally {
            compose.runOnIdle { model.pause() }
            uris.forEach { context.contentResolver.delete(it, null, null) }
        }
    }

    @Test fun crashRecoveryRecognizesPublishedPendingUriAndPausesUnfinishedSnapshot() = runBlocking {
        val directory = "negative-test-${UUID.randomUUID()}"; val store = NegativeScanStore(context, directory)
        val photo = NegativePhoto(UUID.randomUUID().toString(), "synthetic.png", 100, 50)
        val settings = NegativeSettings(FilmBase(.7, .4, .2), exposure = 1.0)
        val bitmap = Bitmap.createBitmap(10, 5, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }
        val saved = try { PhotoExporter(context).save(bitmap, "synthetic-recovery", ExportFormat.PNG, 95) } finally { bitmap.recycle() }
        val published = NegativeJob(UUID.randomUUID().toString(), photo, settings, NegativeFormat.PNG, NegativeJobStatus.RUNNING, saved.toString())
        val interrupted = NegativeJob(UUID.randomUUID().toString(), photo.copy(turns = 3), settings.copy(exposure = -1.0), NegativeFormat.JPEG, NegativeJobStatus.RUNNING)
        store.save(NegativeRoll(revision = 1, photos = listOf(photo), jobs = listOf(published, interrupted), settings = settings))
        val model = NegativeScanViewModel(context.applicationContext as Application, directory)
        try {
            compose.setContent { SpektraTheme { NegativeScanScreen(onBack = {}, model = model) } }
            compose.waitUntil(10_000) { model.state.value.ready }
            val jobs = model.state.value.roll.jobs
            assertEquals(NegativeJobStatus.COMPLETE, jobs[0].status); assertEquals(saved.toString(), jobs[0].outputUri)
            assertEquals(NegativeJobStatus.WAITING, jobs[1].status); assertEquals(-1.0, jobs[1].settings.exposure, 0.0); assertEquals(3, jobs[1].photo.turns)
            assertFalse(model.state.value.exporting)
            assertEquals(jobs, store.load().jobs)
        } finally { compose.runOnIdle { model.pause() }; context.contentResolver.delete(saved, null, null) }
    }
}
