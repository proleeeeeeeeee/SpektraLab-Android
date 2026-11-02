// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.provider.MediaStore
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.proleeeeeeeeee.spektralab.core.*
import io.github.proleeeeeeeeee.spektralab.editor.EditorViewModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BatchWorkflowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun multiImportCopyPresetAndCancelledBatchResumeProduceExactlyOneFilePerJob() {
        val resolver = compose.activity.contentResolver
        val sources = (0..1).map { n ->
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME,"batch-fixture-$n.png");put(MediaStore.Images.Media.MIME_TYPE,"image/png")
                put(MediaStore.Images.Media.IS_PENDING,1)
            })!!
            val bitmap = Bitmap.createBitmap(320+n*40,240,Bitmap.Config.ARGB_8888)
            for (y in 0 until bitmap.height) for (x in 0 until bitmap.width)
                bitmap.setPixel(x,y,Color.rgb(x*255/bitmap.width,y*255/bitmap.height,80+n*50))
            resolver.openOutputStream(uri)!!.use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
            resolver.update(uri,ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING,0) },null,null);uri
        }
        val model = ViewModelProvider(compose.activity)[EditorViewModel::class.java]
        var outputs = emptyList<String>()
        try {
            compose.waitUntil(120_000) { model.state.value.ready || model.state.value.error != null }
            assertNull(model.state.value.error)
            compose.runOnIdle { model.createProject("Batch workflow test") }
            compose.waitUntil(30_000) { model.state.value.library.active.name == "Batch workflow test" }
            compose.runOnIdle { model.importPhotos(sources) }
            compose.waitUntil(120_000) { model.state.value.library.active.photos.size == 2 && !model.state.value.importing }
            assertNull(model.state.value.error)
            compose.runOnIdle {
                model.adjust(Adjustment.TEMPERATURE,15f);model.finishInteraction()
                model.copySettings(false);model.dismissNotice()
                model.savePreset("Batch preset",false)
            }
            compose.waitUntil(30_000) { model.state.value.library.presets.any { it.name == "Batch preset" } }
            compose.runOnIdle { model.dismissNotice();model.selectAll();model.pasteSettings() }
            compose.waitUntil(30_000) { model.state.value.library.active.photos.all { it.recipe.adjustments.temperature == 15.0 } }
            compose.runOnIdle { model.dismissNotice();model.startBatch(ExportSettings(ExportFormat.PNG,ExportSize.ORIGINAL),false) }
            compose.waitUntil(30_000) { model.state.value.library.queue.jobs.size == 2 }
            compose.runOnIdle { model.cancelExport() }
            compose.waitUntil(120_000) { !model.state.value.exporting }
            compose.runOnIdle { model.resumeBatch() }
            compose.waitUntil(180_000) { !model.state.value.exporting && model.state.value.library.queue.completed == 2 || model.state.value.library.queue.failed > 0 }
            val queue = model.state.value.library.queue
            assertEquals(queue.jobs.mapNotNull { it.error }.joinToString(),0,queue.failed)
            assertEquals(2,queue.completed)
            outputs = queue.jobs.mapNotNull { it.outputUri }
            assertEquals(2,outputs.toSet().size)
            outputs.forEach { output ->
                val decoded = resolver.openInputStream(android.net.Uri.parse(output))!!.use(BitmapFactory::decodeStream)
                assertNotNull(decoded);assertEquals(240,decoded.height);decoded.recycle()
            }
            compose.activityRule.scenario.recreate()
        } finally {
            sources.forEach { resolver.delete(it,null,null) }
            outputs.forEach { resolver.delete(android.net.Uri.parse(it),null,null) }
        }
    }
}
