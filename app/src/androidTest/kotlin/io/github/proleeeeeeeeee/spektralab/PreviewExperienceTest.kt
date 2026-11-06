// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.provider.MediaStore
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.proleeeeeeeeee.spektralab.core.*
import io.github.proleeeeeeeeee.spektralab.data.PhotoStore
import io.github.proleeeeeeeeee.spektralab.editor.*
import io.github.proleeeeeeeeee.spektralab.engine.*
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PreviewExperienceTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()

    @Test fun cachedSwitchesGpuGesturesAndNavigationKeepExactFinalPixels() {
        val context=compose.activity
        val model=ViewModelProvider(context)[EditorViewModel::class.java]
        compose.waitUntil(120_000) { model.state.value.ready || model.state.value.error!=null }
        assertNull(model.state.value.error)
        val previousProject=model.state.value.library.active.id
        val name="Preview acceptance ${System.nanoTime()}"
        compose.runOnIdle { model.createProject(name) }
        compose.waitUntil(60_000) { model.state.value.library.active.name==name }
        val resolver=context.contentResolver
        val uri=resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME,"Preview-study.png")
            put(MediaStore.Images.Media.MIME_TYPE,"image/png");put(MediaStore.Images.Media.IS_PENDING,1)
        })!!
        val source=Bitmap.createBitmap(360,540,Bitmap.Config.ARGB_8888)
        for (y in 0 until 540) for (x in 0 until 360) source.setPixel(x,y,Color.rgb(25+x*200/360,25+y*200/540,70+(x+y)*90/900))
        resolver.openOutputStream(uri)!!.use { source.compress(Bitmap.CompressFormat.PNG,100,it) };source.recycle()
        resolver.update(uri,ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING,0) },null,null)
        fun settled()=compose.waitUntil(120_000) { val s=model.state.value;!s.rendering && !s.importing && s.preview!=null }
        fun capture(label: String) {
            compose.waitForIdle()
            val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
            automation.waitForIdle(200,5_000);android.os.SystemClock.sleep(150)
            val image=automation.takeScreenshot()!!
            val dir=File(context.filesDir,"v07-evidence").apply { mkdirs() }
            File(dir,"$label.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) };image.recycle()
        }
        try {
            compose.runOnIdle { model.importPhoto(uri) };settled()
            compose.runOnIdle { model.adjust(Adjustment.GRAIN,0f);model.adjust(Adjustment.HALATION,0f);model.finishInteraction() };settled()
            val initialRecipe=model.state.value.recipe
            val initialPixels=pixels(model.state.value.preview!!)
            compose.runOnIdle { model.selectPaper("kodak_2383") };settled()
            compose.runOnIdle { model.selectPaper(initialRecipe.paperId) };settled()
            assertTrue("A recently viewed complete recipe must hit the bounded cache",model.state.value.previewCacheHit)
            assertArrayEquals(initialPixels,pixels(model.state.value.preview!!))
            val cachedMillis=model.state.value.previewMilliseconds
            if (Build.VERSION.SDK_INT>=33) {
                assertTrue("The API 33 shader must compile on the test device",ScanPreviewShader.supported)
                val beforeDrag=pixels(compose.onNodeWithTag("editor-preview-image").captureToImage().asAndroidBitmap())
                compose.runOnIdle {
                    val exact=model.state.value.preview
                    repeat(30) { model.adjust(Adjustment.CONTRAST,it.toFloat()) }
                    assertSame("Dragging must retain the completed exact bitmap",exact,model.state.value.preview)
                    assertNotNull("A scan-only gesture must use a neutral GPU preview",model.state.value.fastAdjustments)
                }
                compose.onNodeWithTag("preview-status").assertTextEquals("Fast preview")
                val duringDrag=pixels(compose.onNodeWithTag("editor-preview-image").captureToImage().asAndroidBitmap())
                assertFalse("GPU drag must change visible pixels before native correction",beforeDrag.contentEquals(duringDrag))
                capture("fast-preview")
                compose.runOnIdle { model.finishInteraction() };settled()
                assertNull(model.state.value.fastAdjustments)
                val correctedDisplay=pixels(compose.onNodeWithTag("editor-preview-image").captureToImage().asAndroidBitmap())
                assertEquals(duringDrag.size,correctedDisplay.size)
                var difference=0L
                for (i in duringDrag.indices) for (shift in listOf(0,8,16))
                    difference+=kotlin.math.abs(((duringDrag[i] ushr shift) and 255)-((correctedDisplay[i] ushr shift) and 255))
                val meanError=difference.toDouble()/(duringDrag.size*3)
                assertTrue("The GPU contrast draft must agree with native correction on this unclipped fixture: $meanError",meanError<3.0)
                val state=model.state.value;val file=PhotoStore(context).source(state.photo!!)
                val reference=runBlocking {
                    val engine=FilmEngine(context)
                    try { engine.initialize();engine.renderSource(file,"exact-reference",PhotoSource(context).inspect(file),state.recipe) }
                    finally { engine.close() }
                }
                assertArrayEquals("Gesture release must match native float processing",pixels(reference),pixels(state.preview!!))
            }
            capture("editor")
            compose.onNodeWithTag("editor-tool-frame").performClick()
            compose.onNodeWithTag("material-choice-STRIP_135").performScrollTo().performClick();settled()
            capture("frame")
            compose.onNodeWithTag("editor-back").performClick()
            compose.onNodeWithTag("editor-back").performClick()
            compose.onNodeWithTag("editor-save-close").performClick()
            compose.onNodeWithTag("nav-presets").performClick()
            compose.onNodeWithTag("presets-screen").assertIsDisplayed()
            compose.onAllNodesWithTag("nav-materials").assertCountEquals(0)
            compose.onNodeWithTag("nav-negatives").performClick()
            compose.onNodeWithText("Negative scan",substring=true).assertExists()
            compose.onNodeWithTag("nav-photos").performClick()
            capture("library")
            File(context.filesDir,"v07-evidence/preview-metrics.json").writeText(
                org.json.JSONObject().put("device",Build.MODEL).put("cachedRecipeMilliseconds",cachedMillis)
                    .put("nativeFinalPixelsMatch",true).put("cacheBudgetBytes",24*1024*1024).toString(2))
        } finally {
            resolver.delete(uri,null,null)
            compose.runOnIdle { model.prepareLightTable();model.deleteProject() }
            compose.waitUntil(60_000) { model.state.value.library.active.name!=name }
            compose.runOnIdle { model.selectProject(previousProject) }
        }
    }
    private fun pixels(bitmap: Bitmap)=IntArray(bitmap.width*bitmap.height).also {
        bitmap.getPixels(it,0,bitmap.width,0,0,bitmap.width,bitmap.height)
    }
}
