// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab

import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.compose.ui.test.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.proleeeeeeeeee.spektralab.editor.*
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class WindowLayoutWorkflowTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun realEditorAndBoardRetainUsablePhotoAreaAndControls() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val variant=InstrumentationRegistry.getArguments().getString("insetVariant") ?: "default"
        val context=compose.activity;val model=ViewModelProvider(context)[EditorViewModel::class.java]
        compose.waitUntil(120_000) { model.state.value.ready || model.state.value.error!=null };assertNull(model.state.value.error)
        val name="Window check ${System.nanoTime()}"
        compose.runOnIdle { model.createProject(name) };compose.waitUntil(60_000) { model.state.value.library.active.name==name }
        val resolver=context.contentResolver
        val uri=resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,ContentValues().apply { put(MediaStore.Images.Media.DISPLAY_NAME,"window-real.png");put(MediaStore.Images.Media.MIME_TYPE,"image/png");put(MediaStore.Images.Media.IS_PENDING,1) })!!
        val evidence=File(context.filesDir,"stage1-evidence").apply { mkdirs() }
        fun capture(label: String) {
            compose.waitForIdle();instrumentation.uiAutomation.waitForIdle(200,5_000);android.os.SystemClock.sleep(250)
            val bitmap=instrumentation.uiAutomation.takeScreenshot() ?: error("Screenshot unavailable")
            File(evidence,"window-$variant-$label.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
        }
        fun clickReachable(node: SemanticsNodeInteraction) {
            try { node.assertIsDisplayed() } catch (_: AssertionError) { node.performScrollTo() }
            node.assertIsDisplayed().performClick()
        }
        try {
            resolver.openOutputStream(uri)!!.use { out -> File("/data/local/tmp/stage1-photos/0.png").inputStream().use { it.copyTo(out) } }
            resolver.update(uri,ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING,0) },null,null)
            compose.runOnIdle { model.importPhoto(uri) }
            compose.waitUntil(120_000) { model.state.value.photo?.displayName=="window-real.png" && !model.state.value.importing && !model.state.value.rendering && model.state.value.preview!=null }
            capture("editor")
            val image=compose.onNodeWithContentDescription("Processed photograph").fetchSemanticsNode().boundsInRoot
            val density=context.resources.displayMetrics.density
            assertTrue("Editor photo area collapsed: $image",image.width>=120*density && image.height>=100*density)
            clickReachable(compose.onNodeWithText("Material details / compare"))
            compose.onNodeWithText("Close details").performScrollTo().assertIsDisplayed().performClick()
            clickReachable(compose.onNodeWithContentDescription("Back to photographs"))
            clickReachable(compose.onNodeWithText("Light table"))
            val light=ViewModelProvider(compose.activity)[LightTableViewModel::class.java]
            compose.waitUntil(120_000) { light.state.value.layoutReady && !light.state.value.loading && light.state.value.images.size==1 }
            capture("board")
            val board=compose.onNodeWithTag("light-table-board").fetchSemanticsNode().boundsInRoot
            assertTrue("Light table area collapsed: $board",board.width>=120*density && board.height>=120*density)
            if (light.state.value.layout.loupeEnabled) clickReachable(compose.onNodeWithText("Loupe"))
            val initial=light.state.value.layout.displayPlacements.first()
            val center=Offset((initial.x*board.width).toFloat(),(initial.y*board.height).toFloat())
            compose.onNodeWithTag("light-table-board").performTouchInput { swipe(center,center+Offset(board.width*.08f,board.height*.10f),500) }
            compose.waitUntil(5_000) { light.state.value.layout.displayPlacements.first { it.photoId==initial.photoId }.x>initial.x+.02 }
            val moved=light.state.value.layout.displayPlacements.first { it.photoId==initial.photoId }
            clickReachable(compose.onNodeWithText("Rate / mark"))
            compose.onNodeWithText("Done marking").performScrollTo().assertIsDisplayed().performClick()
            clickReachable(compose.onNodeWithTag("review-filter"))
            compose.onNodeWithText("Apply filter").performScrollTo().assertIsDisplayed().performClick()
            clickReachable(compose.onNodeWithContentDescription("Back to photographs"))
            clickReachable(compose.onNodeWithText("Light table"))
            compose.waitUntil(120_000) { light.state.value.layoutReady && !light.state.value.loading && light.state.value.images.size==1 }
            assertEquals(moved,light.state.value.layout.displayPlacements.first { it.photoId==moved.photoId })
            val local=Offset((moved.x*board.width).toFloat(),(moved.y*board.height).toFloat())
            compose.onNodeWithTag("light-table-board").performTouchInput { doubleClick(local) }
            compose.waitUntil(120_000) { model.state.value.showEditor && !model.state.value.rendering }
            clickReachable(compose.onNodeWithContentDescription("Back to light table"))
            compose.waitUntil(120_000) { light.state.value.layoutReady && !light.state.value.loading && light.state.value.images.size==1 }
            assertEquals(moved,light.state.value.layout.displayPlacements.first { it.photoId==moved.photoId })
            capture("touch-return")
            File(evidence,"window-$variant.json").writeText(org.json.JSONObject().put("editorWidthDp",image.width/density).put("editorHeightDp",image.height/density).put("boardWidthDp",board.width/density).put("boardHeightDp",board.height/density).put("controlsReachable",true).put("dragAndEditorReturn",true).toString(2))
        } catch (failure: Throwable) { runCatching { capture("failure") };throw failure }
        finally {
            resolver.delete(uri,null,null)
            compose.runOnIdle { model.prepareLightTable();model.deleteProject() };compose.waitUntil(60_000) { model.state.value.library.active.name!=name }
        }
    }
}
