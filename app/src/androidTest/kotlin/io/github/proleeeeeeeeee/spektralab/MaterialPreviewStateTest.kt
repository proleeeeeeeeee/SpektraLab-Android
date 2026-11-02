// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Color
import android.provider.MediaStore
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.proleeeeeeeeee.spektralab.core.*
import io.github.proleeeeeeeeee.spektralab.data.PhotoStore
import io.github.proleeeeeeeeee.spektralab.editor.EditorViewModel
import io.github.proleeeeeeeeee.spektralab.engine.FilmEngine
import io.github.proleeeeeeeeee.spektralab.engine.PhotoSource
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MaterialPreviewStateTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun switchingInvalidatesOldPreviewImmediatelyAndFailureCannotKeepItsPixels() {
        val context = compose.activity
        val resolver = context.contentResolver
        val name = "material-state-${System.nanoTime()}.png"
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME,name)
            put(MediaStore.Images.Media.MIME_TYPE,"image/png");put(MediaStore.Images.Media.IS_PENDING,1)
        })!!
        var privateFile: File? = null
        var backup: ByteArray? = null
        try {
            val input = Bitmap.createBitmap(320,240,Bitmap.Config.ARGB_8888)
            for (y in 0 until 240) for (x in 0 until 320)
                input.setPixel(x,y,Color.rgb(20+x*210/320,20+y*210/240,80+(x+y)*90/560))
            resolver.openOutputStream(uri)!!.use { input.compress(Bitmap.CompressFormat.PNG,100,it) };input.recycle()
            resolver.update(uri,ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING,0) },null,null)
            val model = ViewModelProvider(context)[EditorViewModel::class.java]
            compose.waitUntil(120_000) { model.state.value.ready || model.state.value.error != null }
            assertNull(model.state.value.error)
            compose.runOnIdle { model.importPhoto(uri) }
            compose.waitUntil(120_000) {
                val state = model.state.value
                state.photo?.displayName == name && !state.importing && !state.rendering && state.preview != null
            }
            compose.runOnIdle {
                model.selectPaper("kodak_supra_endura")
                assertNull("A new material must not display the previous recipe's pixels",model.state.value.preview)
                assertTrue("Developing must start before the debounce",model.state.value.rendering)
            }
            compose.waitUntil(120_000) { !model.state.value.rendering && model.state.value.preview != null }
            val stored = model.state.value.photo!!
            privateFile = runBlocking { PhotoStore(context).source(stored) }
            backup = privateFile!!.readBytes()
            privateFile!!.writeBytes(byteArrayOf(0,1,2,3))
            compose.runOnIdle { model.selectPaper("kodak_2383") }
            compose.waitUntil(120_000) { !model.state.value.rendering && model.state.value.error != null }
            assertNull("Failed material rendering must not retain the previous preview",model.state.value.preview)
            compose.runOnIdle { model.dismissError() }
            compose.onNodeWithText("Original · preview unavailable").assertIsDisplayed()
            compose.onNodeWithContentDescription("Original photograph").assertIsDisplayed()
            privateFile!!.writeBytes(backup!!)
            compose.runOnIdle {
                model.retryPreview()
                model.adjust(Adjustment.GRAIN,0f);model.adjust(Adjustment.HALATION,0f);model.finishInteraction()
                repeat(4) {
                    model.selectFilm("kodak_portra_400");model.selectPaper("kodak_portra_endura")
                    model.selectFilm("cinestill_800t");model.selectPaper("kodak_supra_endura")
                }
                model.selectFilm("kodak_gold_200");model.selectPaper("kodak_2383")
            }
            compose.waitUntil(120_000) { !model.state.value.rendering && model.state.value.preview != null }
            val state = model.state.value
            assertNull(state.error)
            assertEquals("kodak_gold_200",state.recipe.filmId);assertEquals("kodak_2383",state.recipe.paperId)
            val expected = runBlocking {
                val engine = FilmEngine(context)
                try {
                    engine.initialize()
                    engine.renderSource(privateFile!!,stored.id,PhotoSource(context).inspect(privateFile!!),state.recipe)
                } finally { engine.close() }
            }
            val actualPixels = IntArray(state.preview!!.width*state.preview!!.height)
            state.preview!!.getPixels(actualPixels,0,state.preview!!.width,0,0,state.preview!!.width,state.preview!!.height)
            val expectedPixels = IntArray(expected.width*expected.height)
            expected.getPixels(expectedPixels,0,expected.width,0,0,expected.width,expected.height);expected.recycle()
            assertArrayEquals("Rapid switches must finish with the selected material's pixels",expectedPixels,actualPixels)
        } finally {
            if (privateFile != null && backup != null) privateFile!!.writeBytes(backup!!)
            resolver.delete(uri,null,null)
        }
    }
}
