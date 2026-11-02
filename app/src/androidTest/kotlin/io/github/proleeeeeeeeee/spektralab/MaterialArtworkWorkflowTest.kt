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
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.proleeeeeeeeee.spektralab.core.*
import io.github.proleeeeeeeeee.spektralab.data.*
import io.github.proleeeeeeeeee.spektralab.editor.*
import io.github.proleeeeeeeeee.spektralab.engine.*
import io.github.proleeeeeeeeee.spektralab.ui.*
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import kotlin.math.*

class MaterialArtworkWorkflowTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun screenshot(name: String) {
        val directory=File(compose.activity.filesDir,"selector-art-evidence").apply { mkdirs() }
        File(directory,"$name.png").outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it) }
    }
    private fun assertImage(node: SemanticsNodeInteraction,expected: Bitmap,crop: Boolean,label: String) {
        compose.mainClock.advanceTimeBy(600)
        // Platform ripple feedback also runs on RenderThread, outside Compose's test clock.
        android.os.SystemClock.sleep(400)
        compose.waitForIdle()
        val actual=node.captureToImage().asAndroidBitmap()
        val reference=Bitmap.createBitmap(actual.width,actual.height,Bitmap.Config.ARGB_8888)
        val pixels=IntArray(actual.width*actual.height)
        actual.getPixels(pixels,0,actual.width,0,0,actual.width,actual.height)
        val canvas=Canvas(reference)
        canvas.drawColor(pixels.first())
        val scale=if (crop) max(actual.width.toFloat()/expected.width,actual.height.toFloat()/expected.height)
            else min(actual.width.toFloat()/expected.width,actual.height.toFloat()/expected.height)
        // BitmapPainter rounds destination size and centered alignment to integer pixels.
        val w=(expected.width*scale).roundToInt();val h=(expected.height*scale).roundToInt()
        val left=((actual.width-w)/2f).roundToInt();val top=((actual.height-h)/2f).roundToInt()
        canvas.drawBitmap(expected,null,RectF(left.toFloat(),top.toFloat(),(left+w).toFloat(),(top+h).toFloat()),Paint(Paint.FILTER_BITMAP_FLAG))
        val comparison=IntArray(pixels.size);reference.getPixels(comparison,0,actual.width,0,0,actual.width,actual.height)
        var error=0L;var count=0
        for (i in pixels.indices) {
            // The selected-state check is a sibling over the image, not part of its artwork.
            if (!crop && i%actual.width>actual.width*.75 && i/actual.width>actual.height*.60) continue
            for (shift in listOf(0,8,16)) error+=abs(((pixels[i] shr shift) and 255)-((comparison[i] shr shift) and 255))
            count++
        }
        val evidence=File(compose.activity.filesDir,"selector-art-evidence").apply { mkdirs() }
        for ((name,image) in listOf("actual" to actual,"reference" to reference))
            File(evidence,"$label-$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
        File(evidence,"pixel-matches.jsonl").appendText(org.json.JSONObject().put("id",label).put("meanRgbError",error.toDouble()/count/3).put("pixels",count).toString()+"\n")
        screenshot("latest-option")
        assertTrue("$label: Actual option image must match its own cover/sample, mean error ${error.toDouble()/count/3}",error.toDouble()/count/3<2.5)
        reference.recycle()
    }
    @Test fun actualOptionImagesMatchEachStockAcrossScrollingSwitchingAndSampleFailure() {
        val context=compose.activity;val resolver=context.contentResolver
        val model=ViewModelProvider(context)[EditorViewModel::class.java]
        val samples=ViewModelProvider(context)[PaperSampleViewModel::class.java]
        compose.waitUntil(120_000) { model.state.value.ready || model.state.value.error!=null };assertNull(model.state.value.error)
        val name="Selector artwork ${System.nanoTime()}"
        compose.runOnIdle { model.createProject(name) }
        compose.waitUntil(60_000) { model.state.value.library.active.name==name }
        val uri=resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME,"selector-fixture.png");put(MediaStore.Images.Media.MIME_TYPE,"image/png");put(MediaStore.Images.Media.IS_PENDING,1)
        })!!
        var file: File?=null;var backup: ByteArray?=null;var secondUri: android.net.Uri?=null
        try {
            val fixture=InstrumentationRegistry.getArguments().getString("selectorFixture")?.let(::File)
            resolver.openOutputStream(uri)!!.use { output ->
                if (fixture?.isFile==true) fixture.inputStream().use { it.copyTo(output) }
                else {
                    val image=Bitmap.createBitmap(480,640,Bitmap.Config.ARGB_8888)
                    for (y in 0 until 640) for (x in 0 until 480) image.setPixel(x,y,android.graphics.Color.rgb(20+x*215/480,20+y*215/640,60+(x+y)*100/1120))
                    image.compress(Bitmap.CompressFormat.PNG,100,output);image.recycle()
                }
            }
            resolver.update(uri,ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING,0) },null,null)
            compose.runOnIdle { model.importPhoto(uri) }
            compose.waitUntil(120_000) { model.state.value.photo?.displayName=="selector-fixture.png" && !model.state.value.importing && !model.state.value.rendering }
            compose.runOnIdle { model.adjust(Adjustment.GRAIN,0f);model.adjust(Adjustment.HALATION,0f);model.finishInteraction() }
            val rail=compose.onNodeWithTag("material-options")
            repeat(2) { round ->
                for (film in if (round==0) Materials.films else Materials.films.reversed()) {
                    rail.performScrollToNode(hasText(film.name))
                    compose.onNodeWithText(film.name).performClick()
                    assertEquals(film.id,model.state.value.recipe.filmId)
                    // Recreate only the artwork rail so native pressed/focus feedback cannot tint its pixels.
                    // The actual touch selection above is asserted separately and remains selected.
                    compose.onNodeWithText("Frame").performClick();compose.onNodeWithText("Film").performClick()
                    rail.performScrollToNode(hasText(film.name))
                    compose.waitUntil(30_000) { compose.onAllNodesWithTag("film-cover-${film.id}",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty() }
                    val cover=requireNotNull(decodeFilmCover(context,filmCoverResource(film.id)))
                    assertImage(compose.onNodeWithTag("film-cover-${film.id}",useUnmergedTree=true),cover,false,film.id);cover.recycle()
                    if (round==0) screenshot("film-${film.id}")
                }
            }
            fun waitSamples(count: Int) {
                compose.waitUntil(120_000) { val key=samples.state.value.key
                    key?.photoId==model.state.value.photo?.id && key?.recipe==model.state.value.recipe.copy(paperId=Materials.compatiblePapers(model.state.value.recipe.filmId).first().id) &&
                    !samples.state.value.loading && (samples.state.value.images.size==count || samples.state.value.failed.isNotEmpty()) }
                assertTrue(samples.state.value.failed.isEmpty());assertEquals(count,samples.state.value.images.size)
                assertTrue(samples.state.value.images.values.sumOf { it.allocationByteCount }<=3*192*192*4)
            }
            compose.onNodeWithText("Paper").performClick();waitSamples(3)
            val stored=model.state.value.photo!!
            file=runBlocking { PhotoStore(context).source(stored) };backup=file!!.readBytes()
            val expected=runBlocking {
                val worker=FilmEngine(context)
                try {
                    worker.initialize();val info=PhotoSource(context).inspect(file!!)
                    Materials.compatiblePapers(model.state.value.recipe.filmId).associate { paper ->
                        paper.id to worker.renderSource(file!!,stored.id,info,model.state.value.recipe.copy(paperId=paper.id),192)
                    }
                } finally { worker.close() }
            }
            val retained=samples.state.value.images
            repeat(2) { for (paper in Materials.compatiblePapers(model.state.value.recipe.filmId)) {
                rail.performScrollToNode(hasText(paper.name))
                assertImage(compose.onNodeWithTag("paper-sample-${paper.id}",useUnmergedTree=true),expected.getValue(paper.id),true,paper.id)
                compose.onNodeWithText(paper.name).performClick();assertEquals(paper.id,model.state.value.recipe.paperId)
                assertSame(retained.getValue(paper.id),samples.state.value.images.getValue(paper.id))
                if (it==0) screenshot("paper-${paper.id}")
            } }
            expected.values.forEach { it.recycle() }
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED);assertTrue(samples.state.value.images.isEmpty())
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED);waitSamples(3)
            compose.waitUntil(120_000) { !model.state.value.rendering }
            file!!.writeBytes(byteArrayOf(0,1,2,3))
            compose.onNodeWithText("Film").performClick();compose.onNodeWithText("Paper").performClick()
            compose.waitUntil(120_000) { !samples.state.value.loading && samples.state.value.failed.size==3 }
            assertTrue(samples.state.value.images.isEmpty())
            compose.onAllNodesWithText("Sample unavailable").assertCountEquals(3);screenshot("paper-unavailable")
            file!!.writeBytes(backup!!)
            compose.onNodeWithText("Retry samples").performClick();waitSamples(3)
            compose.onNodeWithText("Film").performClick();rail.performScrollToNode(hasText("Tri-X 400"));compose.onNodeWithText("Tri-X 400").performClick()
            compose.onNodeWithText("Paper").performClick();waitSamples(1)
            assertEquals(setOf("ilford_multigrade_iv_rc"),samples.state.value.images.keys)
            assertImage(compose.onNodeWithTag("paper-sample-ilford_multigrade_iv_rc",useUnmergedTree=true),samples.state.value.images.values.single(),true,"ilford_multigrade_iv_rc")
            screenshot("paper-ilford_multigrade_iv_rc")
            compose.onNodeWithText("Film").performClick();rail.performScrollToNode(hasText("Gold 200"));compose.onNodeWithText("Gold 200").performClick()
            compose.onNodeWithText("Paper").performClick();waitSamples(3)
            assertFalse("No stale monochrome sample may remain",samples.state.value.images.containsKey("ilford_multigrade_iv_rc"))
            compose.waitUntil(120_000) { !model.state.value.rendering }
            val threadsBefore=Thread.getAllStackTraces().keys.count { it.name=="FilmEngine" && it.isAlive }
            secondUri=resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME,"selector-second.png");put(MediaStore.Images.Media.MIME_TYPE,"image/png");put(MediaStore.Images.Media.IS_PENDING,1)
            })!!
            val secondImage=Bitmap.createBitmap(400,300,Bitmap.Config.ARGB_8888)
            for (y in 0 until 300) for (x in 0 until 400) secondImage.setPixel(x,y,android.graphics.Color.rgb(210-x*170/400,40+y*160/300,220-x*130/400))
            resolver.openOutputStream(secondUri!!)!!.use { secondImage.compress(Bitmap.CompressFormat.PNG,100,it) };secondImage.recycle()
            resolver.update(secondUri!!,ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING,0) },null,null)
            compose.runOnIdle { model.importPhoto(secondUri!!) }
            compose.waitUntil(120_000) { model.state.value.photo?.displayName=="selector-second.png" && !model.state.value.importing && !model.state.value.rendering }
            compose.runOnIdle { model.selectTab(MaterialTab.PAPER) };waitSamples(3)
            val second=model.state.value.photo!!;val secondSamples=samples.state.value.images
            assertEquals(second.id,samples.state.value.key!!.photoId)
            screenshot("paper-second-photograph")
            repeat(6) { index ->
                compose.runOnIdle { model.openPhoto(if (index%2==0) stored.id else second.id) }
            }
            compose.runOnIdle { model.openPhoto(stored.id) }
            compose.waitUntil(120_000) { model.state.value.photo?.id==stored.id && !model.state.value.rendering }
            compose.runOnIdle {
                repeat(12) { index ->
                    model.adjust(Adjustment.CONTRAST,(.8+index*.03).toFloat())
                    model.selectPaper(if (index%2==0) "kodak_portra_endura" else "kodak_supra_endura")
                }
                model.adjust(Adjustment.CONTRAST,1.12f);model.finishInteraction();model.selectPaper("kodak_2383")
            }
            waitSamples(3)
            assertEquals(stored.id,samples.state.value.key!!.photoId)
            assertEquals(1.12, samples.state.value.key!!.recipe.adjustments.contrast, .001)
            for (id in secondSamples.keys) assertNotSame(secondSamples[id],samples.state.value.images[id])
            val stable=samples.state.value.images
            val currentPhoto=model.state.value.photo!!
            val expectedFinal=runBlocking {
                val worker=FilmEngine(context)
                try { worker.initialize();val info=PhotoSource(context).inspect(file!!)
                    worker.renderSource(file!!,currentPhoto.id,info,model.state.value.recipe,192)
                } finally { worker.close() }
            }
            rail.performScrollToNode(hasText("Vision 2383"))
            assertImage(compose.onNodeWithTag("paper-sample-kodak_2383",useUnmergedTree=true),expectedFinal,true,"rapid-final-kodak_2383")
            expectedFinal.recycle();screenshot("paper-rapid-switch-final")
            android.os.SystemClock.sleep(800)
            assertFalse(samples.state.value.loading)
            for (id in stable.keys) assertSame("Idle samples must not regenerate continuously",stable[id],samples.state.value.images[id])
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            assertTrue(samples.state.value.images.isEmpty())
            compose.waitUntil(15_000) { Thread.getAllStackTraces().keys.count { it.name=="FilmEngine" && it.isAlive }<=threadsBefore }
            val threadsAfter=Thread.getAllStackTraces().keys.count { it.name=="FilmEngine" && it.isAlive }
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED);waitSamples(3)
            val report=org.json.JSONObject().put("filmCovers",4).put("paperKinds",4).put("rapidPhotoOpens",7)
                .put("rapidParameterChanges",13).put("repeatedPaperSelection",6).put("backgroundOwnedSampleBitmaps",0)
                .put("sampleBitmapBytes",samples.state.value.images.values.sumOf { it.allocationByteCount })
                .put("engineThreadsBefore",threadsBefore).put("engineThreadsAfterBackground",threadsAfter)
                .put("sourceFailureAndRetry",true).put("idleRegeneration",false)
            File(context.filesDir,"selector-art-evidence/report.json").writeText(report.toString(2))

        } finally {
            if (file!=null && backup!=null && file!!.exists()) file!!.writeBytes(backup!!)
            resolver.delete(uri,null,null);secondUri?.let { resolver.delete(it,null,null) }
            compose.runOnIdle { model.prepareLightTable();model.deleteProject() }
            compose.waitUntil(60_000) { model.state.value.library.active.name!=name }
        }
    }
}
