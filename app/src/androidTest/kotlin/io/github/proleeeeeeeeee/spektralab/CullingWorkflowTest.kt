// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab

import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.proleeeeeeeeee.spektralab.core.*
import io.github.proleeeeeeeeee.spektralab.data.*
import io.github.proleeeeeeeeee.spektralab.editor.*
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CullingWorkflowTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun screenshot(name: String) {
        val directory=File(compose.activity.filesDir,"stage1-evidence").apply { mkdirs() }
        compose.waitForIdle()
        // Dialog exit animations run on platform RenderThread after Compose becomes idle.
        InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(200,5_000)
        android.os.SystemClock.sleep(250)
        val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            ?: error("Display screenshot unavailable")
        try { File(directory,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) } }
        finally { bitmap.recycle() }
    }
    @Test fun ratingsFiltersRestoreLayoutAndExportOnlyExplicitPhotoIds() {
        val context=compose.activity;val resolver=context.contentResolver
        val model=ViewModelProvider(context)[EditorViewModel::class.java]
        val light=ViewModelProvider(context)[LightTableViewModel::class.java]
        compose.waitUntil(120_000) { model.state.value.ready || model.state.value.error!=null };assertNull(model.state.value.error)
        val name="Culling workflow ${System.nanoTime()}"
        val uris=mutableListOf<android.net.Uri>();val outputs=mutableSetOf<String>()
        var ownedIds=emptySet<String>()
        try {
            compose.runOnIdle { model.createProject(name);model.clearQueue() }
            compose.waitUntil(60_000) { model.state.value.library.active.name==name && model.state.value.library.queue.jobs.isEmpty() }
            val directory=InstrumentationRegistry.getArguments().getString("cullingFixtureDirectory")?.let(::File)
            val fixtures=directory?.listFiles()?.filter { it.extension.lowercase() in setOf("jpg","jpeg","png") }?.sortedBy { it.name }.orEmpty()
            for (index in 0 until 10) {
                val uri=resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME,"stage-review-$index.png");put(MediaStore.Images.Media.MIME_TYPE,"image/png");put(MediaStore.Images.Media.IS_PENDING,1)
                })!!;uris+=uri
                resolver.openOutputStream(uri)!!.use { output ->
                    if (fixtures.isNotEmpty()) fixtures[index%fixtures.size].inputStream().use { it.copyTo(output) }
                    else {
                        val bitmap=Bitmap.createBitmap(320,240,Bitmap.Config.ARGB_8888)
                        for (y in 0 until 240) for (x in 0 until 320) bitmap.setPixel(x,y,android.graphics.Color.rgb(25+x*190/320,25+y*190/240,50+index*14))
                        bitmap.compress(Bitmap.CompressFormat.PNG,100,output);bitmap.recycle()
                    }
                }
                resolver.update(uri,ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING,0) },null,null)
            }
            compose.runOnIdle { model.importPhotos(uris) }
            compose.waitUntil(180_000) { !model.state.value.importing && model.state.value.library.active.photos.size==10 }
            val project=model.state.value.library.active;val ids=project.photos.map { it.id };ownedIds=ids.toSet()
            compose.runOnIdle { model.showLibrary() }
            compose.onNodeWithTag("workspace-view-table").performClick()
            fun waitPage(count: Int) {
                compose.waitUntil(180_000) { light.state.value.layoutReady && !light.state.value.loading && (light.state.value.images.size==count || light.state.value.failed.isNotEmpty() || light.state.value.error!=null) }
                assertNull(light.state.value.error);assertTrue(light.state.value.failed.isEmpty());assertEquals(count,light.state.value.images.size)
                assertTrue(light.state.value.images.size<=8)
                InstrumentationRegistry.getInstrumentation().sendStatus(2,android.os.Bundle().apply {
                    putString("stream","Culling checkpoint: page images=$count, matches=${light.state.value.filteredPhotos.size}, page=${light.state.value.layout.page}\n")
                })
            }
            fun waitSaved() { compose.waitUntil(60_000) { light.state.value.savedRevision>=light.state.value.revision } }
            waitPage(8)
            if (light.state.value.layout.loupeEnabled) compose.onNodeWithTag("table-loupe").performClick()
            val board=compose.onNodeWithTag("light-table-board")
            fun point(id: String): Offset { val pose=light.state.value.layout.displayPlacements.first { it.photoId==id };val size=board.fetchSemanticsNode().boundsInRoot.size;return Offset((pose.x*size.width).toFloat(),(pose.y*size.height).toFloat()) }
            val before=point(ids[0]);val size=board.fetchSemanticsNode().boundsInRoot.size
            board.performTouchInput { swipe(before,before+Offset(size.width*.06f,size.height*.05f),500) }
            assertEquals(ids[0],light.state.value.layout.selectedId)
            compose.onNodeWithTag("review-mark").performClick()
            compose.onNodeWithTag("review-rating-5").performClick()
            compose.onNodeWithTag("review-flag-KEEP").performScrollTo().performClick()
            compose.onNodeWithTag("review-color-RED").performScrollTo().performClick()
            compose.onNodeWithTag("review-picked").performScrollTo().performClick()
            screenshot("review-marking-controls")
            compose.onNodeWithTag("review-done").performClick()
            assertEquals(PhotoReview(5,ReviewFlag.KEEP,ReviewColor.RED),light.state.value.layout.reviews[ids[0]])
            compose.onNodeWithTag("table-next").performClick();waitPage(2)
            compose.onNodeWithTag("review-mark").performClick()
            compose.onNodeWithTag("review-rating-4").performClick()
            compose.onNodeWithTag("review-flag-KEEP").performScrollTo().performClick()
            compose.onNodeWithTag("review-color-BLUE").performScrollTo().performClick()
            compose.onNodeWithTag("review-picked").performScrollTo().performClick()
            compose.onNodeWithTag("review-done").performClick()
            val rejectedPoint=point(ids[9]);board.performTouchInput { click(rejectedPoint) };compose.waitUntil(5_000) { light.state.value.layout.selectedId==ids[9] }
            compose.onNodeWithTag("review-mark").performClick()
            compose.onNodeWithTag("review-rating-1").performClick()
            compose.onNodeWithTag("review-flag-REJECT").performScrollTo().performClick()
            compose.onNodeWithTag("review-done").performClick()
            // Consecutive field events must merge against latest review state, not a stale UI snapshot.
            compose.runOnIdle { light.rate(ids[2],5);light.flag(ids[2],ReviewFlag.KEEP);light.color(ids[2],ReviewColor.GREEN) }
            assertEquals(PhotoReview(5,ReviewFlag.KEEP,ReviewColor.GREEN),light.state.value.layout.reviews[ids[2]])
            assertEquals(setOf(ids[0],ids[8]),light.state.value.layout.pickedIds)
            val original=light.state.value.layout.placements;val originalPage=light.state.value.layout.page
            waitSaved();assertEquals(light.state.value.layout,runBlocking { LightTableStore(context).load(project.id).layout })
            screenshot("review-page-two")
            // The same project has two views. Grid review changes must not move the board,
            // and temporary multi-selection must never become a persistent picked flag.
            val beforeGrid = light.state.value.layout
            compose.onNodeWithTag("workspace-view-grid").performClick()
            compose.waitUntil(60_000) { light.state.value.layoutReady && !light.state.value.loading && light.state.value.images.isEmpty() }
            assertNull(light.state.value.detail)
            compose.onNodeWithTag("workspace-grid").performScrollToNode(hasTestTag("photo-review-${ids[0]}"))
            compose.onNodeWithTag("photo-review-${ids[0]}").assertTextContains("5★", substring = true)
            compose.onNodeWithTag("workspace-multiselect").performClick()
            compose.onNodeWithTag("workspace-grid").performScrollToNode(hasTestTag("photo-select-${ids[1]}"))
            compose.onNodeWithTag("photo-select-${ids[1]}").performClick()
            assertEquals(setOf(ids[1]), model.state.value.selection)
            assertEquals(beforeGrid.pickedIds, light.state.value.layout.pickedIds)
            compose.onNodeWithTag("workspace-multiselect").performClick()
            assertTrue(model.state.value.selection.isEmpty())
            compose.onNodeWithTag("workspace-grid").performScrollToNode(hasTestTag("photo-mark-${ids[0]}"))
            compose.onNodeWithTag("photo-mark-${ids[0]}").performClick()
            compose.onNodeWithTag("review-rating-4").performScrollTo().performClick()
            assertEquals(4, light.state.value.layout.reviews[ids[0]]!!.rating)
            assertEquals(beforeGrid.selectedId, light.state.value.layout.selectedId)
            compose.onNodeWithTag("review-rating-5").performScrollTo().performClick()
            compose.onNodeWithTag("review-done").performClick()
            compose.onNodeWithTag("workspace-view-table").performClick(); waitPage(2)
            assertEquals(beforeGrid, light.state.value.layout)
            screenshot("review-shared-grid-marks")
            compose.onNodeWithTag("review-filter").performClick()
            compose.onNodeWithTag("filter-minimum-rating").performSemanticsAction(SemanticsActions.SetProgress) { it(5f) }
            compose.onNodeWithTag("filter-flag-REJECT").performScrollTo().performClick()
            compose.onNodeWithTag("filter-apply").performClick();waitPage(0)
            compose.onNodeWithText("没有符合筛选的照片").assertIsDisplayed();assertNull(light.state.value.layout.selectedId)
            compose.runOnIdle { val previous=model.state.value.selection;assertFalse(model.prepareBatchSelection(emptySet()));assertEquals(previous,model.state.value.selection);model.dismissNotice() }
            screenshot("review-empty-filter")
            compose.onNodeWithText("清除筛选").performClick();waitPage(2)
            assertEquals(originalPage,light.state.value.layout.page);assertEquals(original,light.state.value.layout.placements)
            compose.onNodeWithTag("review-filter").performClick()
            compose.onNodeWithTag("filter-minimum-rating").performSemanticsAction(SemanticsActions.SetProgress) { it(4f) }
            compose.onNodeWithTag("filter-flag-KEEP").performScrollTo().performClick()
            compose.onNodeWithTag("filter-apply").performClick();waitPage(3)
            assertEquals(listOf(ids[0],ids[2],ids[8]),light.state.value.filteredPhotos.map { it.id })
            compose.onNodeWithTag("review-filter").performClick()
            compose.onNodeWithTag("filter-color-RED").performScrollTo().performClick()
            compose.onNodeWithTag("filter-apply").performClick();waitPage(1)
            assertEquals(listOf(ids[0]),light.state.value.filteredPhotos.map { it.id })
            screenshot("review-red-filter")
            compose.onNodeWithTag("review-filter").performClick()
            compose.onNodeWithTag("filter-color-any").performScrollTo().performClick()
            compose.onNodeWithTag("filter-picked-only").performScrollTo().performClick()
            compose.onNodeWithTag("filter-apply").performClick();waitPage(2)
            assertEquals(listOf(ids[0],ids[8]),light.state.value.filteredPhotos.map { it.id })
            compose.onNodeWithTag("review-filter").performClick()
            compose.onNodeWithTag("filter-picked-only").performScrollTo().performClick()
            compose.onNodeWithTag("filter-apply").performClick();waitPage(3)
            val selectedPoint=point(ids[8]);board.performTouchInput { click(selectedPoint) };compose.waitUntil(5_000) { light.state.value.layout.selectedId==ids[8] };assertEquals(ids[8],light.state.value.layout.selectedId)
            val filteredPoint=point(ids[8]);val filteredSize=board.fetchSemanticsNode().boundsInRoot.size
            board.performTouchInput { swipe(filteredPoint,filteredPoint+Offset(filteredSize.width*.05f,filteredSize.height*.04f),500) }
            assertEquals(original,light.state.value.layout.placements)
            compose.onNodeWithTag("review-mark").performClick()
            compose.onNodeWithTag("review-rating-3").performClick()
            compose.onNodeWithTag("review-color-PURPLE").performScrollTo().performClick()
            assertEquals(ReviewColor.RED,light.state.value.layout.reviews[ids[0]]!!.color)
            assertEquals(ReviewColor.PURPLE,light.state.value.layout.reviews[ids[8]]!!.color)
            compose.onNodeWithTag("review-rating-4").performScrollTo().performClick()
            compose.onNodeWithTag("review-color-BLUE").performScrollTo().performClick()
            compose.onNodeWithTag("review-done").performClick();waitPage(3)
            waitSaved();val remembered=light.state.value.layout
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED);assertTrue(light.state.value.images.isEmpty());assertNull(light.state.value.detail)
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED);waitPage(3);assertEquals(remembered,light.state.value.layout)
            compose.activityRule.scenario.recreate();waitPage(3);assertEquals(remembered,light.state.value.layout)
            assertEquals(remembered,runBlocking { LightTableStore(compose.activity).load(project.id).layout })
            screenshot("review-filter-restored")
            compose.onNodeWithTag("review-export").performClick();compose.onNodeWithTag("review-export-filtered").performClick()
            compose.onNodeWithTag("export-summary").assertIsDisplayed().assertTextContains("3", substring = true)
            assertEquals(setOf(ids[0],ids[2],ids[8]),model.state.value.selection);assertTrue(light.state.value.images.isEmpty())
            // Dismiss the sheet via its dedicated back action rather than exporting the filtered set.
            compose.onNodeWithTag("export-close").performClick();waitPage(3)
            compose.onNodeWithTag("review-export").performClick();compose.onNodeWithTag("review-export-picked").performClick()
            compose.onNodeWithTag("export-summary").assertIsDisplayed().assertTextContains("2", substring = true)
            assertEquals(setOf(ids[0],ids[8]),model.state.value.selection);assertTrue(light.state.value.images.isEmpty())
            compose.onNodeWithText("PNG").performClick()
            compose.onNodeWithTag("photo-export-save").performClick()
            compose.waitUntil(240_000) { !model.state.value.exporting && (model.state.value.library.queue.completed==2 || model.state.value.library.queue.failed>0) }
            val queue=model.state.value.library.queue
            assertEquals(queue.jobs.mapNotNull { it.error }.joinToString(),0,queue.failed)
            assertEquals(2,queue.completed);assertEquals(setOf(ids[0],ids[8]),queue.jobs.map { it.photoId }.toSet())
            outputs+=queue.jobs.mapNotNull { it.outputUri };assertEquals(2,outputs.size)
            waitPage(3);assertEquals(remembered.reviews,light.state.value.layout.reviews)
            screenshot("review-export-complete")
            if (model.state.value.notice!=null) compose.onNodeWithText("知道了").performClick()
            compose.waitUntil(5_000) { model.state.value.notice==null }
            screenshot("review-after-picked-export")
            compose.onNodeWithTag("review-filter").performClick();compose.onNodeWithTag("filter-clear").performClick();waitPage(2)
            assertEquals(originalPage,light.state.value.layout.page);assertEquals(original,light.state.value.layout.placements)
            compose.onNodeWithTag("table-previous").performClick();waitPage(8)
            val marks=light.state.value.layout.reviews;val poses=light.state.value.layout.placements.associateBy { it.photoId }
            val editPoint=point(ids[0]);board.performTouchInput { doubleClick(editPoint) }
            compose.waitUntil(120_000) { model.state.value.photo?.id==ids[0] && model.state.value.showEditor }
            compose.runOnIdle { model.selectFilm("kodak_portra_160") }
            compose.waitUntil(120_000) { !model.state.value.rendering && model.state.value.preview!=null }
            compose.onNodeWithTag("editor-back").performClick();waitPage(8)
            assertEquals(marks,light.state.value.layout.reviews);assertEquals(poses,light.state.value.layout.placements.associateBy { it.photoId })
            waitSaved();assertEquals(marks,runBlocking { LightTableStore(compose.activity).load(project.id).layout.reviews })
            screenshot("review-after-editor")
            val report=org.json.JSONObject().put("photos",10).put("markedPhotos",4).put("filteredIds",org.json.JSONArray(listOf(ids[0],ids[2],ids[8])))
                .put("exportedIds",org.json.JSONArray(queue.jobs.map { it.photoId })).put("exportedOutputs",2).put("emptyScopeRejected",true)
                .put("filterHitAndDrag",true).put("baseLayoutPreserved",true).put("backgroundRestore",true).put("activityRestore",true)
                .put("sharedGridMarks",true).put("temporarySelectionIndependent",true).put("pinnedReviewTarget",true).put("lateEditorSavePreservesMarks",true).put("batchSuspendsBoard",true).put("realPhotoFixtures",fixtures.isNotEmpty())
            File(compose.activity.filesDir,"stage1-evidence/culling-report.json").writeText(report.toString(2))
        } catch (failure: Throwable) {
            runCatching { screenshot("review-failure") }
            throw failure
        } finally {
            compose.runOnIdle { if (model.state.value.exporting) model.cancelExport() }
            compose.waitUntil(120_000) { !model.state.value.exporting }
            outputs+=model.state.value.library.queue.jobs.filter { it.photoId in ownedIds }.mapNotNull { it.outputUri }
            outputs.forEach { resolver.delete(android.net.Uri.parse(it),null,null) };uris.forEach { resolver.delete(it,null,null) }
            compose.runOnIdle { model.prepareLightTable();model.deleteProject();model.clearQueue() }
            compose.waitUntil(60_000) { model.state.value.library.active.name!=name }
        }
    }
}
