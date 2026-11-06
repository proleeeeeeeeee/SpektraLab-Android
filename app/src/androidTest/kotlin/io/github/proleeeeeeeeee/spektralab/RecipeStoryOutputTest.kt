// SPDX-License-Identifier: GPL-3.0-only
package io.github.proleeeeeeeeee.spektralab

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import io.github.proleeeeeeeeee.spektralab.core.*
import io.github.proleeeeeeeeee.spektralab.data.*
import java.io.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class RecipeStoryOutputTest {
    @Test fun recipeStreamsAreBoundedAndDoNotContainSourceIdentity()=runBlocking {
        val document=RecipeDocument(name="Portable look",settings=RecipeTransfer(EditRecipe()))
        val bytes=RecipeDocument.encode(document)
        assertEquals(document,RecipeExchange.readDocument(ByteArrayInputStream(bytes)))
        val text=bytes.decodeToString()
        for(privateField in listOf("sourceUri","photoId","displayName","latitude","longitude"))assertFalse(text.contains(privateField))
        val oversized=object: InputStream() {
            var reads=0
            override fun read(): Int { reads++;return 'x'.code }
        }
        try { RecipeExchange.readDocument(oversized);fail("Oversized recipe accepted") } catch(expected: IllegalArgumentException) {}
        assertTrue("Reading must stop at a bounded chunk",oversized.reads<=RecipeDocument.MAX_BYTES+4096)
    }
    @Test fun cancelledRecipeReadDoesNotContinueConsumingInput()=runBlocking {
        val job=Job().apply { cancel() };var reads=0
        val input=object: InputStream() { override fun read(): Int { reads++;return 0 } }
        try { withContext(job) { RecipeExchange.readDocument(input) };fail("Should cancel") } catch(expected: CancellationException) {}
        assertEquals(0,reads)
    }
    @Test fun cardKeepsPhotoAspectAndWritesValidExifFreeJpeg() {
        val photo=Bitmap.createBitmap(300,200,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(80,140,210)) }
        val document=RecipeDocument(name="A portable recipe",settings=RecipeTransfer(EditRecipe()))
        val card=StoryCardRenderer.render(photo,document)
        try {
            assertEquals(1280,card.width);assertEquals(1800,card.height)
            assertEquals(photo.getPixel(150,100),card.getPixel(640,694))
            assertEquals(Color.rgb(11,13,14),card.getPixel(640,185)) // Letterbox, never stretch the photograph.
            val output=ByteArrayOutputStream();assertTrue(card.compress(Bitmap.CompressFormat.JPEG,95,output))
            val bytes=output.toByteArray();assertFalse(bytes.toString(Charsets.ISO_8859_1).contains("Exif\u0000\u0000"))
            val decoded=BitmapFactory.decodeByteArray(bytes,0,bytes.size)!!
            assertEquals(card.width,decoded.width);assertEquals(card.height,decoded.height);decoded.recycle()
        } finally { card.recycle();photo.recycle() }
    }
}
