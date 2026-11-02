// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Color
import android.provider.MediaStore
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import io.github.proleeeeeeeeee.spektralab.core.Adjustment
import io.github.proleeeeeeeeee.spektralab.data.PhotoStore
import io.github.proleeeeeeeeee.spektralab.editor.EditorViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PendingEditsTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun switchingPhotosCannotConflateTheirPendingSaves() {
        val context=compose.activity;val model=ViewModelProvider(context)[EditorViewModel::class.java]
        compose.waitUntil(120_000) { model.state.value.ready || model.state.value.error!=null };assertNull(model.state.value.error)
        val previous=model.state.value.library.active.id;val name="Pending edits ${System.nanoTime()}"
        compose.runOnIdle { model.createProject(name) };compose.waitUntil(60_000) { model.state.value.library.active.name==name }
        val resolver=context.contentResolver
        val uris=(0..1).map { index ->
            val uri=resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME,"pending-$index.png");put(MediaStore.Images.Media.MIME_TYPE,"image/png")
                put(MediaStore.Images.Media.IS_PENDING,1)
            })!!
            val image=Bitmap.createBitmap(128,96,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(80+index*20,110,140)) }
            resolver.openOutputStream(uri)!!.use { image.compress(Bitmap.CompressFormat.PNG,100,it) };image.recycle()
            resolver.update(uri,ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING,0) },null,null);uri
        }
        try {
            compose.runOnIdle { model.importPhotos(uris) }
            compose.waitUntil(120_000) { !model.state.value.importing && model.state.value.library.active.photos.size==2 }
            val ids=model.state.value.library.active.photos.map { it.id }
            compose.runOnIdle { model.openPhoto(ids[0]) }
            compose.waitUntil(120_000) { model.state.value.photo?.id==ids[0] && model.state.value.original!=null }
            compose.runOnIdle { model.adjust(Adjustment.CONTRAST,31f);model.openPhoto(ids[1]) }
            compose.waitUntil(120_000) { model.state.value.photo?.id==ids[1] && model.state.value.original!=null }
            compose.runOnIdle { model.adjust(Adjustment.SATURATION,-20f);model.finishInteraction() }
            compose.waitUntil(60_000) { !model.state.value.savingEdits }
            // A new store verifies the committed disk index, not a previously
            // loaded per-instance snapshot. Do not race AtomicFile's writer.
            val saved=runBlocking { PhotoStore(context).library() }
            assertEquals(31.0,saved.photo(ids[0])!!.recipe.adjustments.contrast,0.0)
            assertEquals(-20.0,saved.photo(ids[1])!!.recipe.adjustments.saturation,0.0)
        } finally {
            uris.forEach { resolver.delete(it,null,null) }
            compose.runOnIdle { model.prepareLightTable();model.deleteProject() }
            compose.waitUntil(60_000) { model.state.value.library.active.name!=name }
            compose.runOnIdle { model.selectProject(previous) }
        }
    }
}
