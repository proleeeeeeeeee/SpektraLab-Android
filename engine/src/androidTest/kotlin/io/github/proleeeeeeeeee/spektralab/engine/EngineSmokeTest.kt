// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab.engine

import android.graphics.Bitmap
import android.graphics.Color
import android.content.ContentValues
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.proleeeeeeeeee.spektralab.core.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EngineSmokeTest {
    @Test
    fun bundledVulkanEngineRendersStocksAndPhysicalFrames() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val input = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888)
        for (y in 0 until input.height) for (x in 0 until input.width) {
            input.setPixel(x, y, Color.rgb(x * 255 / 320, y * 255 / 240, (x + y) * 255 / 560))
        }
        val engine = FilmEngine(context)
        try {
            assertTrue(engine.initialize().isNotBlank())
            var referenceHash = 0
            val stockHashes = mutableSetOf<Int>()
            for (film in Materials.films) {
                val recipe = EditRecipe().withFilm(film.id)
                val output = engine.render(input, "test-gradient", recipe)
                assertImage(output)
                val hash = hash(output)
                stockHashes += hash
                if (film == Materials.films.first()) referenceHash = hash
                for (paper in Materials.compatiblePapers(film.id)) {
                    assertImage(engine.render(input, "test-gradient", recipe.copy(paperId = paper.id)))
                }
            }
            assertTrue("Film selection must change actual rendered pixels", stockHashes.size >= 3)
            for (frame in FilmFrame.entries.filter { it != FilmFrame.NONE }) {
                val output = engine.render(input, "test-gradient", EditRecipe(frame = frame))
                assertImage(output)
                assertNotEquals(referenceHash, hash(output))
                // Inspect the whole scan: XPan's 65:24 gate sits on 35 mm film,
                // while a square 120 gate also includes unequal physical rebates.
                val aspect = output.width.toDouble() / output.height
                if (frame == FilmFrame.XPAN) assertTrue("XPan scan aspect: " + aspect, aspect in 1.7..2.1)
                if (frame == FilmFrame.SQUARE_120) assertTrue("120 scan aspect: " + aspect, aspect in .85..1.15)
                // Synthetic evidence remains available after the test APK is removed.
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, frame.name + ".png")
                        put(MediaStore.Downloads.MIME_TYPE, "image/png")
                        put(MediaStore.Downloads.RELATIVE_PATH, "Download/SpektraLab-tests")
                    })!!
                context.contentResolver.openOutputStream(uri)!!.use {
                    assertTrue(output.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
            }
            // A different source key must replace the native cached session.
            val white = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
            assertNotEquals(referenceHash, hash(engine.render(white, "test-white", EditRecipe())))
        } finally { engine.close() }
    }

    private fun assertImage(bitmap: Bitmap) {
        assertTrue(bitmap.width > 0 && bitmap.height > 0)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        assertTrue("Output must have tonal variation", pixels.toSet().size > 16)
        assertTrue("Print output must be opaque", pixels.all { Color.alpha(it) == 255 })
    }
    private fun hash(bitmap: Bitmap): Int {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.contentHashCode()
    }
}
