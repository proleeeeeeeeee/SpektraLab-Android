// SPDX-License-Identifier: GPL-3.0-only
package io.github.proleeeeeeeeee.spektralab

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Color
import android.provider.MediaStore
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.proleeeeeeeeee.spektralab.core.*
import io.github.proleeeeeeeeee.spektralab.data.*
import io.github.proleeeeeeeeee.spektralab.editor.*
import io.github.proleeeeeeeeee.spektralab.engine.*
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TestBenchStoryWorkflowTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun settled(model: EditorViewModel)=compose.waitUntil(120_000) {
        val s=model.state.value;!s.importing && !s.rendering && s.preview!=null && s.previewRecipe==s.recipe
    }
    private fun shot(name: String) {
        compose.waitForIdle()
        val image=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()!!
        val folder=File(compose.activity.filesDir,"bench-story-evidence").apply { mkdirs() }
        File(folder,"$name.png").outputStream().use {image.compress(Bitmap.CompressFormat.PNG,100,it)};image.recycle()
    }
    private fun fixture(block: (EditorViewModel)->Unit) {
        val model=ViewModelProvider(compose.activity)[EditorViewModel::class.java]
        compose.waitUntil(120_000) {model.state.value.ready || model.state.value.error!=null};assertNull(model.state.value.error)
        val previous=model.state.value.library.activeId;val name="Bench story test ${System.nanoTime()}"
        compose.runOnIdle {model.createProject(name)};compose.waitUntil(60_000) {model.state.value.library.active.name==name}
        val project=model.state.value.library.activeId;val resolver=compose.activity.contentResolver
        val uri=resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME,"Bench-story-synthetic.png");put(MediaStore.Images.Media.MIME_TYPE,"image/png");put(MediaStore.Images.Media.IS_PENDING,1)
        })!!
        val bitmap=Bitmap.createBitmap(480,320,Bitmap.Config.ARGB_8888)
        for(y in 0 until bitmap.height)for(x in 0 until bitmap.width)bitmap.setPixel(x,y,Color.rgb(25+x*200/480,30+y*185/320,35+(x+y)*155/800))
        resolver.openOutputStream(uri)!!.use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        resolver.update(uri,ContentValues().apply {put(MediaStore.Images.Media.IS_PENDING,0)},null,null)
        try {
            compose.runOnIdle {model.importPhoto(uri)};settled(model)
            block(model)
        } finally {
            compose.runOnIdle {
                ViewModelProvider(compose.activity)[TestBenchViewModel::class.java].pause()
                ViewModelProvider(compose.activity)[StoryCardViewModel::class.java].pause()
                model.prepareLightTable();model.selectProject(project)
            }
            compose.waitUntil(60_000) {model.state.value.library.activeId==project}
            compose.runOnIdle {model.deleteProject()};compose.waitUntil(60_000) {model.state.value.library.projects.none {it.id==project}}
            compose.runOnIdle {model.selectProject(previous)}
            resolver.delete(uri,null,null)
        }
    }
    private fun hash(image: Bitmap): String {
        val pixels=IntArray(image.width*image.height);image.getPixels(pixels,0,image.width,0,0,image.width,image.height)
        val buffer=java.nio.ByteBuffer.allocate(pixels.size*4);buffer.asIntBuffer().put(pixels)
        return MessageDigest.getInstance("SHA-256").digest(buffer.array()).joinToString("") {"%02x".format(it)}+"/"+image.width+"x"+image.height
    }
    @Test fun sixRealCombinationsCustomPrintAndApplyAreUndoableAndNavigationKeepsDiscardBaseline()=fixture { editor->
        val initial=editor.state.value.recipe;val photo=editor.state.value.photo!!
        val before=PhotoStore(compose.activity).source(photo).readBytes()
        compose.onNodeWithTag("editor-more").performClick();compose.onNodeWithTag("editor-test-bench").performScrollTo().performClick()
        val bench=ViewModelProvider(compose.activity)[TestBenchViewModel::class.java]
        compose.waitUntil(120_000) {!bench.state.value.busy && bench.state.value.images.size>=4}
        compose.onNodeWithTag("bench-count-6").performClick()
        compose.waitUntil(120_000) {!bench.state.value.busy && bench.state.value.images.size==6}
        assertTrue(bench.state.value.failed.isEmpty());assertNull(bench.state.value.error)
        assertEquals(6,(0..5).map {hash(bench.state.value.image(it)!!)}.toSet().size)
        assertTrue(bench.state.value.images.values.sumOf {it.bitmap.allocationByteCount.toLong()}<=12L*1024*1024)
        compose.onNodeWithTag("bench-choice-2").performClick()
        compose.onNodeWithTag("bench-customize").performClick();compose.onNodeWithTag("bench-paper-menu").performClick()
        compose.onNodeWithTag("bench-paper-kodak_2383").performClick();compose.onNodeWithTag("bench-render-combination").performClick()
        compose.waitUntil(120_000) {!bench.state.value.busy && bench.state.value.image(2)!=null}
        assertEquals("kodak_2383",bench.state.value.selectedRecipe!!.paperId)
        shot("test-bench-six")
        val chosen=bench.state.value.selectedRecipe!!
        compose.runOnIdle {bench.pause();bench.open(photo)}
        compose.waitUntil(120_000) {!bench.state.value.busy && bench.state.value.image(2)!=null}
        assertEquals(chosen,bench.state.value.selectedRecipe)
        val sourceFile=PhotoStore(compose.activity).source(photo);val held=File(sourceFile.parentFile,sourceFile.name+".test-held")
        compose.runOnIdle {bench.pause()};assertTrue(sourceFile.renameTo(held))
        try {
            compose.runOnIdle {bench.retry()};compose.waitUntil(60_000) {!bench.state.value.busy && bench.state.value.error!=null}
            compose.onNodeWithTag("bench-error").assertIsDisplayed()
        } finally {assertTrue(held.renameTo(sourceFile))}
        compose.onNodeWithTag("bench-retry").performClick()
        compose.waitUntil(120_000) {!bench.state.value.busy && bench.state.value.image(2)!=null}
        assertNull(bench.state.value.error)
        val reference=runBlocking {
            val engine=FilmEngine(compose.activity)
            try {engine.initialize();val file=PhotoStore(compose.activity).source(photo);engine.renderSource(file,photo.id,PhotoSource(compose.activity).inspect(file),chosen,448)} finally {engine.close()}
        }
        assertEquals(hash(reference),hash(bench.state.value.image(2)!!));reference.recycle()
        val savedName="Bench saved test "+System.nanoTime()
        compose.onNodeWithTag("bench-save").performClick();compose.onNode(hasSetTextAction()).performTextInput(savedName)
        compose.onNodeWithText("Save",useUnmergedTree=true).performClick()
        compose.waitUntil(60_000) {editor.state.value.library.presets.any {it.name==savedName}}
        val preset=editor.state.value.library.presets.first {it.name==savedName}
        assertEquals(chosen,preset.settings.recipe)
        compose.onNodeWithText("Got it").performClick()
        compose.runOnIdle {editor.deletePreset(preset.id)}
        compose.waitUntil(60_000) {editor.state.value.library.presets.none {it.id==preset.id}}
        compose.onNodeWithTag("bench-apply").performClick();settled(editor)
        assertEquals(chosen,editor.state.value.recipe)
        compose.onNodeWithTag("editor-undo").performClick();settled(editor);assertEquals(initial,editor.state.value.recipe)
        compose.runOnIdle {editor.adjust(Adjustment.CONTRAST,25f);editor.finishInteraction()};settled(editor)
        compose.onNodeWithTag("editor-more").performClick();compose.onNodeWithTag("editor-test-bench").performScrollTo().performClick()
        compose.onNodeWithTag("bench-back").performClick();compose.onNodeWithTag("editor-back").performClick()
        compose.onNodeWithTag("editor-discard").performClick()
        compose.waitUntil(60_000) {editor.state.value.recipe==initial && !editor.state.value.savingEdits}
        assertArrayEquals(before,PhotoStore(compose.activity).source(photo).readBytes())
        assertFalse(editor.applyBenchRecipe("a-different-photo",chosen))
    }
    @Test fun storyRendersActualSnapshotAndFilePickerCancellationRecoversWithoutChangingPhoto()=fixture { editor->
        val initial=editor.state.value.recipe;val photo=editor.state.value.photo!!
        compose.onNodeWithTag("editor-more").performClick();compose.onNodeWithTag("editor-story-card").performScrollTo().performClick()
        val story=ViewModelProvider(compose.activity)[StoryCardViewModel::class.java]
        compose.waitUntil(120_000) {!story.state.value.rendering && story.state.value.image!=null}
        compose.onNodeWithTag("story-title").performTextReplacement("Daylight study")
        compose.onNodeWithTag("story-title").performImeAction()
        compose.waitUntil(120_000) {!story.state.value.rendering && story.state.value.document?.name=="Daylight study" && story.state.value.image!=null}
        assertEquals(initial,story.state.value.document!!.settings.recipe)
        assertEquals(1280,story.state.value.image!!.width);assertEquals(1800,story.state.value.image!!.height)
        shot("recipe-story")
        val dir=File(compose.activity.filesDir,"bench-story-evidence")
        File(dir,"actual-story.jpg").outputStream().use {story.state.value.image!!.compress(Bitmap.CompressFormat.JPEG,95,it)}
        File(dir,"actual-recipe.json").writeBytes(RecipeDocument.encode(story.state.value.document!!))
        compose.onNodeWithTag("story-export-recipe").performScrollTo().performClick()
        compose.waitUntil(20_000) {compose.activity.lifecycle.currentState==androidx.lifecycle.Lifecycle.State.CREATED}
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("input keyevent KEYCODE_BACK").close()
        compose.waitUntil(120_000) {!story.state.value.rendering && story.state.value.image!=null}
        compose.onNodeWithTag("story-save-card").assertIsEnabled()
        val document=story.state.value.document!!
        compose.runOnIdle {story.save(android.net.Uri.parse("content://spektralab.invalid.test/document/empty"),photo,document)}
        compose.waitUntil(120_000) {!story.state.value.saving && story.state.value.fileError!=null}
        compose.runOnIdle {story.pause();story.show(photo,document)}
        compose.waitUntil(120_000) {!story.state.value.rendering && story.state.value.image!=null}
        assertNotNull("A foreground preview must not erase an output failure warning",story.state.value.fileError)
        compose.onNodeWithTag("story-file-error").assertIsDisplayed()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithTag("story-back").performClick();settled(editor)
        assertEquals(initial,editor.state.value.recipe);assertEquals(photo.id,editor.state.value.photo!!.id)
    }
}
