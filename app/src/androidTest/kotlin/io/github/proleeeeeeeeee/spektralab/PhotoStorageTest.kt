// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.proleeeeeeeeee.spektralab.core.ExportFormat
import io.github.proleeeeeeeeee.spektralab.core.FilmFrame
import io.github.proleeeeeeeeee.spektralab.data.PhotoExporter
import io.github.proleeeeeeeeee.spektralab.data.PhotoStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhotoStorageTest {
    @Test
    fun bothFormatsPublishCompleteDecodableImagesAndRejectFileUris() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val exporter = PhotoExporter(context)
        val store = PhotoStore(context)
        val photo = Bitmap.createBitmap(80, 60, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        for (format in ExportFormat.entries) {
            val uri = exporter.save(photo, "../../unsafe\n.jpg", format, 95)
            try {
                context.contentResolver.query(uri, arrayOf(MediaStore.Images.Media.IS_PENDING,
                    MediaStore.Images.Media.MIME_TYPE, MediaStore.Images.Media.DISPLAY_NAME), null, null, null)!!.use {
                    assertTrue(it.moveToFirst())
                    assertEquals(0, it.getInt(0))
                    assertEquals(format.mimeType, it.getString(1))
                    assertFalse(it.getString(2).contains('/'))
                }
                val decoded = context.contentResolver.openInputStream(uri)!!.use(BitmapFactory::decodeStream)
                assertEquals(80, decoded.width)
                assertEquals(60, decoded.height)
                if (format == ExportFormat.JPEG) {
                    val (imported, original) = store.import(uri)
                    assertEquals(80, original.width)
                    val edited = imported.copy(recipe = imported.recipe.withFrame(FilmFrame.XPAN))
                    store.save(edited)
                    assertEquals(edited, PhotoStore(context).restore())
                    // Subsequent decoding must not rely on the original content URI.
                    context.contentResolver.delete(uri, null, null)
                    assertEquals(60, store.decode(edited, 1024).height)
                }
            } finally { runCatching { context.contentResolver.delete(uri, null, null) } }
        }
        var rejected = false
        try { PhotoStore(context).import(Uri.parse("file:///sdcard/arbitrary.jpg")) }
        catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
    }
}
