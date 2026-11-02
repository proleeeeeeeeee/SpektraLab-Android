// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.provider.MediaStore
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.proleeeeeeeeee.spektralab.core.*
import io.github.proleeeeeeeeee.spektralab.data.*
import io.github.proleeeeeeeeee.spektralab.editor.*
import io.github.proleeeeeeeeee.spektralab.engine.*
import io.github.proleeeeeeeeee.spektralab.ui.CanvasColor
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import kotlin.math.*

class MaterialDetailsWorkflowTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private fun capture(name: String) {
        compose.waitForIdle();instrumentation.uiAutomation.waitForIdle(200,5_000);android.os.SystemClock.sleep(250)
        val image=instrumentation.uiAutomation.takeScreenshot() ?: error("Screenshot unavailable")
        val folder=File(compose.activity.filesDir,"stage1-evidence").apply { mkdirs() }
        File(folder,"details-$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) };image.recycle()
    }
    private fun waitImages() {
        compose.waitForIdle()
        compose.waitUntil(120_000) { compose.onAllNodesWithTag("comparison-current").fetchSemanticsNodes().size==1 && compose.onAllNodesWithTag("comparison-alternative").fetchSemanticsNodes().size==1 }
    }
    private fun choose(id: String) {
        compose.onNodeWithTag("comparison-choice").performScrollTo().performClick()
        compose.onNodeWithTag("comparison-option-$id").performScrollTo().performClick();waitImages()
    }
    private fun match(tag: String,expected: Bitmap): Double {
        val node=compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
        compose.waitForIdle();android.os.SystemClock.sleep(250)
        val actual=node.captureToImage().asAndroidBitmap()
        val reference=Bitmap.createBitmap(actual.width,actual.height,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(reference);canvas.drawColor(CanvasColor.toArgb())
        val scale=min(actual.width.toFloat()/expected.width,actual.height.toFloat()/expected.height)
        val w=(expected.width*scale).roundToInt();val h=(expected.height*scale).roundToInt()
        val x=((actual.width-w)/2f).roundToInt();val y=((actual.height-h)/2f).roundToInt()
        canvas.drawBitmap(expected,null,RectF(x.toFloat(),y.toFloat(),(x+w).toFloat(),(y+h).toFloat()),Paint(Paint.FILTER_BITMAP_FLAG))
        val a=IntArray(actual.width*actual.height);val b=IntArray(a.size)
        actual.getPixels(a,0,actual.width,0,0,actual.width,actual.height);reference.getPixels(b,0,actual.width,0,0,actual.width,actual.height)
        var error=0L
        for (i in a.indices) for (shift in listOf(0,8,16)) error+=abs(((a[i] shr shift) and 255)-((b[i] shr shift) and 255))
        val mean=error.toDouble()/a.size/3;reference.recycle()
        assertTrue("$tag must sample the actual source/recipe; mean RGB error=$mean",mean<2.5)
        return mean
    }
    @Test fun realPhotographComparisonApplyRetryAndMonochromeCompatibility() {
        val context=compose.activity;val model=ViewModelProvider(context)[EditorViewModel::class.java]
        compose.waitUntil(120_000) { model.state.value.ready || model.state.value.error!=null };assertNull(model.state.value.error)
        val name="Material comparison ${System.nanoTime()}"
        compose.runOnIdle { model.createProject(name) };compose.waitUntil(60_000) { model.state.value.library.active.name==name }
        val resolver=context.contentResolver
        val uri=resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,ContentValues().apply { put(MediaStore.Images.Media.DISPLAY_NAME,"comparison-real.png");put(MediaStore.Images.Media.MIME_TYPE,"image/png");put(MediaStore.Images.Media.IS_PENDING,1) })!!
        var file: File?=null;var backup: ByteArray?=null
        val matches=org.json.JSONArray()
        try {
            val fixture=File(InstrumentationRegistry.getArguments().getString("selectorFixture") ?: "/data/local/tmp/stage1-photos/0.png")
            assertTrue("Real photograph fixture required",fixture.isFile)
            resolver.openOutputStream(uri)!!.use { output -> fixture.inputStream().use { it.copyTo(output) } };resolver.update(uri,ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING,0) },null,null)
            compose.runOnIdle { model.importPhoto(uri) }
            compose.waitUntil(120_000) { model.state.value.photo?.displayName=="comparison-real.png" && !model.state.value.importing && !model.state.value.rendering && model.state.value.preview!=null }
            compose.runOnIdle { model.adjust(Adjustment.GRAIN,0f);model.adjust(Adjustment.HALATION,0f);model.finishInteraction() }
            compose.waitUntil(120_000) { !model.state.value.rendering }
            val photo=model.state.value.photo!!;file=runBlocking { PhotoStore(context).source(photo) };backup=file!!.readBytes()
            fun verify(alternative: EditRecipe,label: String) {
                waitImages();val current=model.state.value.recipe
                val images=runBlocking { val engine=FilmEngine(context)
                    try { engine.initialize();val info=PhotoSource(context).inspect(file!!)
                        listOf(engine.renderSource(file!!,photo.id,info,current,512),engine.renderSource(file!!,photo.id,info,alternative,512))
                    } finally { engine.close() } }
                try {
                    matches.put(org.json.JSONObject().put("case",label).put("currentMeanRgbError",match("comparison-current",images[0])).put("alternativeMeanRgbError",match("comparison-alternative",images[1])))
                    assertFalse("Distinct materials should not display the same pixels",images[0].sameAs(images[1]))
                } finally { images.forEach { it.recycle() } }
                capture(label)
            }
            compose.onNodeWithText("Material details / compare").performClick();waitImages()
            compose.onNodeWithText("Material details").assertIsDisplayed()
            choose("kodak_portra_800");verify(model.state.value.recipe.withFilm("kodak_portra_800"),"film-portra800")
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED);waitImages()
            verify(model.state.value.recipe.withFilm("kodak_portra_800"),"background-restored")
            compose.onNodeWithText("Close details").performScrollTo().performClick()
            file!!.writeBytes(byteArrayOf(0,1,2,3))
            compose.onNodeWithText("Material details / compare").performClick()
            compose.waitUntil(120_000) { compose.onAllNodesWithText("Retry comparison").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Apply alternative").assertIsNotEnabled();capture("failure")
            file!!.writeBytes(backup!!)
            compose.onNodeWithText("Retry comparison").performScrollTo().performClick();waitImages()
            choose("kodak_portra_800");verify(model.state.value.recipe.withFilm("kodak_portra_800"),"retry-restored")
            compose.onNodeWithText("Apply alternative").performScrollTo().performClick()
            compose.waitUntil(120_000) { model.state.value.recipe.filmId=="kodak_portra_800" && !model.state.value.rendering && model.state.value.preview!=null }
            compose.onNodeWithText("Paper").performClick();compose.onNodeWithText("Material details / compare").performClick();waitImages()
            choose("kodak_2393");verify(model.state.value.recipe.copy(paperId="kodak_2393"),"print-2393")
            compose.onNodeWithText("Apply alternative").performScrollTo().performClick()
            compose.waitUntil(120_000) { model.state.value.recipe.paperId=="kodak_2393" && !model.state.value.rendering }
            compose.onNodeWithText("Material details / compare").performClick()
            compose.onNodeWithText("Cinema print film").performScrollTo().assertIsDisplayed();capture("cinema-identity")
            compose.onNodeWithText("Close details").performScrollTo().performClick()
            compose.runOnIdle { model.selectFilm("ilford_hp5_plus_400");model.selectTab(MaterialTab.PAPER) }
            compose.waitUntil(120_000) { !model.state.value.rendering }
            compose.onNodeWithText("Material details / compare").performClick()
            compose.onNodeWithText("This film currently has one compatible print material.").performScrollTo().assertIsDisplayed()
            compose.onAllNodesWithTag("comparison-current").assertCountEquals(0)
            compose.onNodeWithText("Black-and-white photographic paper").performScrollTo().assertIsDisplayed();capture("monochrome-compatible")
            compose.onNodeWithText("Close details").performScrollTo().performClick()
            File(context.filesDir,"stage1-evidence/material-details-report.json").writeText(org.json.JSONObject().put("realPhoto",true).put("pixelMatches",matches).put("sourceFailureRetry",true).put("filmAndPrintApply",true).put("lifecycleRestore",true).put("monochromeSingleCompatible",true).toString(2))
        } catch (failure: Throwable) { runCatching { capture("test-failure") };throw failure }
        finally {
            if (file!=null && backup!=null && file!!.exists()) file!!.writeBytes(backup!!)
            resolver.delete(uri,null,null)
            compose.runOnIdle { model.prepareLightTable();model.deleteProject() }
            compose.waitUntil(60_000) { model.state.value.library.active.name!=name }
        }
    }
}
