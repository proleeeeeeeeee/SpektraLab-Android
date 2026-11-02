// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Debug
import android.provider.MediaStore
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.proleeeeeeeeee.spektralab.core.*
import io.github.proleeeeeeeeee.spektralab.data.*
import io.github.proleeeeeeeeee.spektralab.editor.*
import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LightTableWorkflowTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun screenshot(name: String) {
        val directory=File(compose.activity.filesDir,"light-table-evidence").apply { mkdirs() }
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            File(directory,name+".png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        }
    }
    @Test fun emptyTableHasImportAndBackWithoutInventingPhotographs() {
        val model=ViewModelProvider(compose.activity)[EditorViewModel::class.java]
        compose.waitUntil(120_000) { model.state.value.ready || model.state.value.error!=null }
        assertNull(model.state.value.error)
        val name="Empty light table ${System.nanoTime()}"
        compose.runOnIdle { model.createProject(name);model.showLibrary() }
        compose.waitUntil(120_000) { model.state.value.library.active.name==name }
        compose.onNodeWithTag("workspace-view-table").performClick()
        compose.onNodeWithText("No photos on the light table").assertIsDisplayed()
        compose.onNodeWithTag("workspace-import").assertIsEnabled()
        screenshot("empty")
        compose.onNodeWithTag("workspace-view-grid").performClick()
        compose.onNodeWithTag("workspace-view-table").assertIsDisplayed()
        compose.runOnIdle { model.deleteProject() }
        compose.waitUntil(120_000) { model.state.value.library.active.name!=name }
    }
    @Test fun realPhotosTouchLampLoupePagesBackgroundAndEditorRoundTrip() {
        val context=compose.activity;val resolver=context.contentResolver
        val model=ViewModelProvider(context)[EditorViewModel::class.java]
        val light=ViewModelProvider(context)[LightTableViewModel::class.java]
        compose.waitUntil(120_000) { model.state.value.ready || model.state.value.error!=null }
        assertNull(model.state.value.error)
        val name="Touch light table ${System.nanoTime()}"
        val uris=mutableListOf<android.net.Uri>()
        var privateFile: File?=null;var backup: ByteArray?=null
        try {
            compose.runOnIdle { model.createProject(name);model.showLibrary() }
            compose.waitUntil(120_000) { model.state.value.library.active.name==name }
            val directory=InstrumentationRegistry.getArguments().getString("lightTableFixtureDirectory")?.let(::File)
            val fixtures=directory?.listFiles()?.filter { it.isFile && it.extension.lowercase() in setOf("jpg","jpeg","png") }?.sortedBy { it.name }.orEmpty()
            for (index in 0 until 9) {
                val uri=resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME,"light-table-$index-${System.nanoTime()}.png")
                    put(MediaStore.Images.Media.MIME_TYPE,"image/png");put(MediaStore.Images.Media.IS_PENDING,1)
                })!!
                uris+=uri
                resolver.openOutputStream(uri)!!.use { output ->
                    if (fixtures.isNotEmpty()) fixtures[index%fixtures.size].inputStream().use { it.copyTo(output) }
                    else {
                        val w=if (index==0) 1800 else 320;val h=if (index==0) 1200 else 240
                        val pixels=IntArray(w*h) { i -> val x=i%w;val y=i/w
                            Color.rgb(25+x*190/w,25+y*190/h,45+(index*17+x*70/w)%170) }
                        val bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
                        bitmap.setPixels(pixels,0,w,0,0,w,h);bitmap.compress(Bitmap.CompressFormat.PNG,100,output);bitmap.recycle()
                    }
                }
                resolver.update(uri,ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING,0) },null,null)
            }
            compose.runOnIdle { model.importPhotos(uris) }
            compose.waitUntil(180_000) { !model.state.value.importing && model.state.value.library.active.photos.size==9 }
            val project=model.state.value.library.active
            compose.runOnIdle { model.showLibrary() }
            compose.onNodeWithTag("workspace-view-table").performClick()
            fun waitPage(count: Int) {
                compose.waitUntil(180_000) { val s=light.state.value
                    s.project?.id==project.id && !s.loading && (s.images.size==count || s.failed.isNotEmpty() || s.error!=null) }
                assertNull(light.state.value.error);assertTrue(light.state.value.failed.isEmpty());assertEquals(count,light.state.value.images.size)
                assertTrue(light.state.value.images.size<=LIGHT_TABLE_PAGE_SIZE)
            }
            waitPage(8)
            val board=compose.onNodeWithTag("light-table-board")
            fun bounds()=board.fetchSemanticsNode().boundsInRoot.size
            val id=project.photos.first().id
            val before=light.state.value.layout.placements.first { it.photoId==id }
            val size=bounds()
            board.performTouchInput { swipe(Offset((before.x*size.width).toFloat(),(before.y*size.height).toFloat()),
                Offset((before.x*size.width+size.width*.07).toFloat(),(before.y*size.height+size.height*.04).toFloat()),500) }
            val dragged=light.state.value.layout.placements.first { it.photoId==id }
            assertTrue("Film drag must update its normalized position",dragged.x>before.x+.03)
            assertEquals(id,light.state.value.layout.placements.last().photoId)
            compose.onNodeWithContentDescription("Rotate current film").performClick()
            assertEquals(dragged.rotated(15.0).rotation,light.state.value.layout.placements.first { it.photoId==id }.rotation,0.0)
            val controls = compose.onNodeWithTag("table-controls")
            fun openControls() { controls.performClick() }
            fun closeControls() { compose.onNodeWithTag("table-controls-done").performClick() }
            openControls()
            compose.onNodeWithContentDescription("Light-table backlight brightness").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(.31f) }
            assertEquals(.31,light.state.value.layout.brightness,.005)
            compose.onNodeWithTag("table-light-switch").performScrollTo().assertIsOn().performClick()
            assertFalse(light.state.value.layout.lightOn)
            closeControls(); screenshot("lamp-off")
            compose.onNodeWithTag("table-light").performClick()
            openControls()
            compose.onNodeWithContentDescription("Light-table backlight brightness").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(.9f) }
            closeControls()
            val posesBeforeLens=light.state.value.layout.placements
            val lensBefore=light.state.value.layout
            val target=posesBeforeLens.first { it.photoId==id }
            board.performTouchInput { swipe(Offset((lensBefore.loupeX*size.width).toFloat(),(lensBefore.loupeY*size.height).toFloat()),
                Offset((target.x*size.width).toFloat(),(target.y*size.height).toFloat()),500) }
            assertEquals("Dragging the lens must not drag underlying film",posesBeforeLens,light.state.value.layout.placements)
            assertTrue(kotlin.math.abs(light.state.value.layout.loupeX-lensBefore.loupeX)>.1)
            compose.waitUntil(120_000) { light.state.value.detailId==id && light.state.value.detail!=null }
            assertTrue("Loupe must use a higher-resolution real render",maxOf(light.state.value.detail!!.width,light.state.value.detail!!.height)>
                maxOf(light.state.value.images.getValue(id).width,light.state.value.images.getValue(id).height)*2)
            val imageBytes=light.state.value.images.values.sumOf { it.allocationByteCount.toLong() }
            val detailBytes=light.state.value.detail!!.allocationByteCount
            assertTrue(imageBytes<=8L*512*512*4);assertTrue(detailBytes<=1536*1536*4)
            screenshot("lamp-on-loupe")
            val remembered=light.state.value.layout
            compose.waitUntil(60_000) { light.state.value.savedRevision>=light.state.value.revision }
            assertEquals(remembered,runBlocking { LightTableStore(context).load(project.id).layout })
            val pssBefore=Debug.getPss()
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            assertTrue("Background must release board bitmap ownership",light.state.value.images.isEmpty())
            assertNull(light.state.value.detail)
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            waitPage(8);assertEquals(remembered,light.state.value.layout)
            compose.activityRule.scenario.recreate()
            compose.onNodeWithTag("workspace-view-table").assertIsDisplayed()
            waitPage(8);assertEquals(remembered,light.state.value.layout)
            repeat(2) {
                compose.onNodeWithTag("table-next").performClick();waitPage(1)
                assertEquals(project.photos.last().id,light.state.value.images.keys.single())
                compose.onNodeWithTag("table-previous").performClick();waitPage(8)
            }
            compose.onNodeWithTag("table-next").performClick();waitPage(1)
            privateFile=PhotoStore(context).source(project.photos.last());backup=privateFile!!.readBytes()
            privateFile!!.writeBytes(byteArrayOf(0,1,2,3))
            compose.runOnIdle { light.retry() }
            compose.waitUntil(120_000) { !light.state.value.loading && light.state.value.failed.isNotEmpty() }
            assertTrue(light.state.value.images.isEmpty())
            privateFile!!.writeBytes(backup!!)
            openControls(); compose.onNodeWithText("Retry previews").performScrollTo().performClick(); waitPage(1); closeControls()
            privateFile=null;backup=null
            compose.onNodeWithTag("table-previous").performClick();waitPage(8)
            if (light.state.value.layout.loupeEnabled) compose.onNodeWithTag("table-loupe").performClick()
            val pose=light.state.value.layout.placements.first { it.photoId==id };val currentSize=bounds()
            val retained=light.state.value.layout.placements
            board.performTouchInput { doubleClick(Offset((pose.x*currentSize.width).toFloat(),(pose.y*currentSize.height).toFloat())) }
            compose.waitUntil(120_000) { model.state.value.showEditor && model.state.value.photo?.id==id }
            compose.onNodeWithTag("editor-back").assertIsDisplayed()
            assertTrue(light.state.value.images.isEmpty())
            compose.runOnIdle { model.selectFilm("kodak_portra_400") }
            compose.waitUntil(120_000) { !model.state.value.rendering && model.state.value.preview!=null }
            assertEquals("kodak_portra_400",model.state.value.recipe.filmId)
            compose.onNodeWithTag("editor-back").performClick();waitPage(8)
            assertEquals(retained,light.state.value.layout.placements)
            screenshot("returned-from-editor")
            val report=JSONObject().put("photos",9).put("pageSize",8).put("boardBitmapBytes",imageBytes)
                .put("loupeBitmapBytes",detailBytes).put("backgroundOwnedBitmapCount",0).put("processPssKbBeforeBackground",pssBefore)
                .put("actualPhotoFixtures",fixtures.isNotEmpty()).put("touchDragRotationLampLoupe",true)
                .put("backgroundRestore",true).put("activityRecreation",true).put("repeatedPages",true)
                .put("sourceFailureRecovery",true).put("editorRoundTrip",true)
            val evidence=File(context.filesDir,"light-table-evidence").apply { mkdirs() }
            File(evidence,"workflow.json").writeText(report.toString(2))
            android.util.Log.i("LightTableEvidence",report.toString())
            compose.onNodeWithTag("workspace-view-grid").performClick()
            compose.onNodeWithTag("workspace-view-table").assertIsDisplayed()
        } finally {
            if (privateFile!=null && backup!=null) privateFile!!.writeBytes(backup!!)
            uris.forEach { resolver.delete(it,null,null) }
            compose.runOnIdle { if (model.state.value.library.active.name==name) { model.prepareLightTable();model.deleteProject() } }
            compose.waitUntil(120_000) { model.state.value.library.active.name!=name }
        }
    }
}
