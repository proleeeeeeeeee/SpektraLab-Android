// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.proleeeeeeeeee.spektralab.data.*
import io.github.proleeeeeeeeee.spektralab.editor.*
import java.io.File
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ContactSheetInterruptionTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun realUiCancelAndBackgroundReportCancellationAndRetryPublishedPdf() {
        val context=compose.activity;val resolver=context.contentResolver
        val model=ViewModelProvider(context)[EditorViewModel::class.java]
        val sheet=ViewModelProvider(context)[ContactSheetViewModel::class.java]
        val light=ViewModelProvider(context)[LightTableViewModel::class.java]
        val journal=ContactSheetJournal(context);val publisher=ContactSheetPublisher(context)
        val imports=mutableListOf<Uri>();val outputs=mutableSetOf<Uri>();val name="Cancel regression ${System.nanoTime()}"
        val evidence=File(context.filesDir,"stage2-interruption-final").apply { mkdirs() }
        val message="Contact-sheet export was cancelled. Retry starts the saved selection again."
        fun capture(label: String) {
            compose.waitForIdle();val instrumentation=InstrumentationRegistry.getInstrumentation()
            instrumentation.uiAutomation.waitForIdle(200,5_000);android.os.SystemClock.sleep(250)
            val bitmap=instrumentation.uiAutomation.takeScreenshot() ?: error("No screenshot")
            File(evidence,"$label.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
        }
        fun start() {
            compose.onNodeWithTag("review-export").performClick();compose.onNodeWithTag("review-contact-sheet").performClick()
            compose.onNodeWithTag("sheet-save").assertIsDisplayed().performClick()
        }
        fun assertCancelled(ids: List<String>) {
            compose.waitUntil(120_000) { !sheet.state.value.busy && sheet.state.value.error!=null }
            assertEquals(message,sheet.state.value.error);assertTrue(sheet.state.value.outputs.isEmpty())
            assertEquals(ids,journal.load()!!.snapshot.entries.map { it.photoId })
            assertTrue(journal.loadUris().isEmpty());assertTrue(File(context.cacheDir,"contact-sheets").listFiles().orEmpty().isEmpty())
        }
        fun retry(label: String) {
            compose.onNodeWithTag("sheet-retry").performClick()
            compose.waitUntil(180_000) { !sheet.state.value.busy && (sheet.state.value.outputs.isNotEmpty() || sheet.state.value.error!=null) }
            assertNull(sheet.state.value.error);assertEquals(1,sheet.state.value.outputs.size)
            val uri=sheet.state.value.outputs.single();outputs+=uri
            resolver.query(uri,arrayOf(MediaStore.MediaColumns.IS_PENDING,MediaStore.MediaColumns.SIZE),null,null,null)!!.use { assertTrue(it.moveToFirst());assertEquals(0,it.getInt(0));assertTrue(it.getLong(1)>0) }
            resolver.openFileDescriptor(uri,"r")!!.use { descriptor -> PdfRenderer(descriptor).use { assertEquals(1,it.pageCount) } }
            resolver.openInputStream(uri)!!.use { input -> File(evidence,"$label.pdf").outputStream().use { input.copyTo(it) } }
            capture("$label-saved")
            compose.onNodeWithTag("sheet-dismiss").performClick()
            compose.waitUntil(120_000) { !light.state.value.loading && light.state.value.images.size==2 }
        }
        compose.waitUntil(120_000) { model.state.value.ready && sheet.state.value.ready };assertNull(sheet.state.value.error)
        compose.runOnIdle { model.createProject(name) };compose.waitUntil(60_000) { model.state.value.library.active.name==name }
        try {
            for (index in 0..1) {
                val uri=resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,ContentValues().apply { put(MediaStore.Images.Media.DISPLAY_NAME,"interrupt-real-$index.png");put(MediaStore.Images.Media.MIME_TYPE,"image/png");put(MediaStore.Images.Media.IS_PENDING,1) })!!;imports+=uri
                resolver.openOutputStream(uri)!!.use { out -> File("/data/local/tmp/stage1-photos/$index.png").inputStream().use { it.copyTo(out) } }
                resolver.update(uri,ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING,0) },null,null)
            }
            compose.runOnIdle { model.importPhotos(imports) }
            compose.waitUntil(180_000) { !model.state.value.importing && model.state.value.library.active.photos.size==2 }
            val photos=model.state.value.library.active.photos;val ids=photos.map { it.id };val store=PhotoStore(context)
            fun hash(file: File)=MessageDigest.getInstance("SHA-256").digest(file.readBytes()).toList()
            val originals=photos.associate { it.id to hash(store.source(it)) }
            compose.onNodeWithTag("workspace-view-table").performClick()
            compose.waitUntil(120_000) { light.state.value.layoutReady && !light.state.value.loading && light.state.value.images.size==2 }
            start();compose.onNodeWithTag("sheet-cancel").performClick();assertCancelled(ids)
            compose.onNodeWithTag("sheet-error-message").assertTextContains("Cancel", substring = true).assertIsDisplayed();capture("explicit-cancel-correct-message");retry("explicit-cancel-retry")
            start();compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED);assertCancelled(ids)
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED);compose.activityRule.scenario.recreate()
            compose.onNodeWithTag("sheet-error-message").assertTextContains("Cancel", substring = true).assertIsDisplayed();capture("background-cancel-correct-message");retry("background-cancel-retry")
            for (photo in photos) assertEquals(originals[photo.id],hash(store.source(photo)))
            assertNull(journal.load());assertTrue(journal.loadUris().isEmpty())
            File(evidence,"report.json").writeText(org.json.JSONObject().put("realPhotos",2).put("explicitCancelMessage",true).put("backgroundCancelMessage",true).put("activityRestore",true).put("successfulRetries",2).put("originalsUnchanged",true).put("stagingEmpty",true).toString(2))
        } finally {
            compose.runOnIdle { sheet.cancel() };compose.waitUntil(120_000) { !sheet.state.value.busy }
            outputs+=sheet.state.value.outputs;outputs.forEach { publisher.removeOwned(it) };imports.forEach { resolver.delete(it,null,null) }
            compose.runOnIdle { sheet.discard();model.prepareLightTable();model.deleteProject() }
            compose.waitUntil(60_000) { model.state.value.library.active.name!=name }
        }
    }
}
