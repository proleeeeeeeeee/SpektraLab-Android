// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Color
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.proleeeeeeeeee.spektralab.core.*
import io.github.proleeeeeeeeee.spektralab.data.PhotoStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProjectStorageTest {
    @Test fun projectsPresetsTransfersAndQueueSurviveRecreationAndLateWritesCannotResurrectDeletedPhotos() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver
        val source = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME,"project-fixture.png");put(MediaStore.Images.Media.MIME_TYPE,"image/png")
            put(MediaStore.Images.Media.IS_PENDING,1)
        })!!
        val store = PhotoStore(context)
        val previous = store.library().activeId
        val project = store.createProject("Storage test").activeId
        var presetId: String? = null
        try {
            val bitmap = Bitmap.createBitmap(100,80,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.CYAN) }
            resolver.openOutputStream(source)!!.use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
            resolver.update(source,ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING,0) },null,null)
            val (first,a) = store.import(source);a.recycle()
            val (second,b) = store.import(source);b.recycle()
            val composed = second.copy(recipe = second.recipe.copy(composition = Composition(quarterTurns = 1)))
            store.save(composed)
            val look = RecipeTransfer(first.recipe.copy(adjustments = Adjustments(exposure = .5)))
            store.applySettings(setOf(second.id),look)
            val saved = store.library().photo(second.id)!!
            assertEquals(.5,saved.recipe.adjustments.exposure,0.0)
            assertEquals(1,saved.recipe.composition.quarterTurns)
            store.save(composed) // An old preview must not overwrite the copied look.
            assertEquals(saved,store.library().photo(second.id))
            store.savePreset("My print",look)
            presetId = store.library().presets.last().id
            val queue = ExportQueue(listOf(ExportJob("test-job",first.id,first.recipe,
                ExportSettings(ExportFormat.TIFF16,ExportSize.ORIGINAL),ExportJobStatus.RUNNING,"content://media/external/images/media/1")))
            store.saveQueue(queue)
            val restored = PhotoStore(context).library()
            assertEquals(project,restored.activeId);assertEquals(2,restored.active.photos.size)
            assertEquals(queue,restored.queue);assertEquals(look,restored.presets.last().settings)
            store.deletePhotos(setOf(first.id));store.save(first)
            assertNull(PhotoStore(context).library().photo(first.id))
            assertFalse(store.source(first).exists())
            assertEquals(100,store.decode(saved,1024).width)
        } finally {
            store.saveQueue(ExportQueue());presetId?.let { store.deletePreset(it) }
            store.selectProject(project);store.deleteProject()
            if (store.library().projects.any { it.id == previous }) store.selectProject(previous)
            resolver.delete(source,null,null)
        }
    }
}
