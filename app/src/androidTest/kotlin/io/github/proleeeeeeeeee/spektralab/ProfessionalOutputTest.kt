// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab

import android.graphics.BitmapFactory
import android.os.Build
import android.provider.MediaStore
import io.github.proleeeeeeeeee.spektralab.data.PhotoExporter
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.proleeeeeeeeee.spektralab.core.*
import io.github.proleeeeeeeeee.spektralab.engine.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfessionalOutputTest {
    @Test fun originalResolutionAndTiffRetainMoreThanEightBitsAndHdrContainsANewGainMap() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = File(File(context.filesDir,"imports").apply { mkdirs() },UUID.randomUUID().toString()+".photo")
        TestImages.png16(source,3000,64)
        val engine = FilmEngine(context)
        try {
            engine.initialize()
            val info = PhotoSource(context).inspect(source)
            assertEquals(SourceInfo(3000,64,false),info)
            val recipe = EditRecipe(adjustments = Adjustments(grain = 0.0,halation = 0.0))
            for (format in listOf(ExportFormat.JPEG,ExportFormat.PNG,ExportFormat.TIFF16,ExportFormat.HDR_JPEG)) {
                if (format == ExportFormat.HDR_JPEG && Build.VERSION.SDK_INT < 34) continue
                val prepared = engine.prepareExport(source,info,recipe,ExportSettings(format,ExportSize.ORIGINAL))
                try {
                    assertEquals(3000,prepared.width);assertEquals(64,prepared.height)
                    if (format == ExportFormat.TIFF16) verifyTiff(prepared.file)
                    else {
                        val decoded = BitmapFactory.decodeFile(prepared.file.path)
                        assertNotNull(decoded);assertEquals(3000,decoded.width);assertEquals(64,decoded.height)
                        if (format == ExportFormat.HDR_JPEG && Build.VERSION.SDK_INT >= 34) {
                            assertTrue(decoded.hasGainmap())
                            val gain = decoded.gainmap!!
                            assertTrue("Calibrated HDR must contain highlight headroom",gain.ratioMax.any { it > 1.01f })
                            val pixels = IntArray(gain.gainmapContents.width*gain.gainmapContents.height)
                            gain.gainmapContents.getPixels(pixels,0,gain.gainmapContents.width,0,0,gain.gainmapContents.width,gain.gainmapContents.height)
                            assertTrue("Gain map must contain spatial information",pixels.toSet().size > 4)
                        }
                        decoded.recycle()
                    }
                    val published = PhotoExporter(context).publish(prepared.file,"precision-test",format)
                    try {
                        context.contentResolver.query(published,arrayOf(MediaStore.Images.Media.IS_PENDING,
                            MediaStore.Images.Media.MIME_TYPE),null,null,null)!!.use {
                            assertTrue(it.moveToFirst());assertEquals(0,it.getInt(0));assertEquals(format.mimeType,it.getString(1))
                        }
                        assertArrayEquals(prepared.file.readBytes(),context.contentResolver.openInputStream(published)!!.use { it.readBytes() })
                    } finally { context.contentResolver.delete(published,null,null) }
                } finally { prepared.close();assertFalse(prepared.file.exists()) }
            }
        } finally { engine.close();source.delete() }
    }
    @Test fun rawDevelopsFullSensorPixelsWithoutAnEmbeddedJpeg() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = File(File(context.filesDir,"imports").apply { mkdirs() },UUID.randomUUID().toString()+".photo")
        TestImages.dng(source)
        val engine = FilmEngine(context)
        try {
            val codec = PhotoSource(context)
            val info = codec.inspect(source)
            assertTrue("Fixture must be classified as RAW",info.raw)
            assertEquals(128,info.width);assertEquals(96,info.height)
            val preview = codec.preview(source,1024)
            assertEquals(128,preview.width);assertEquals(96,preview.height)
            val colors = IntArray(128*96);preview.getPixels(colors,0,128,0,0,128,96)
            assertTrue(colors.toSet().size > 256);preview.recycle()
            engine.initialize()
            val result = engine.prepareExport(source,info,EditRecipe(),ExportSettings(ExportFormat.TIFF16,ExportSize.ORIGINAL))
            try { assertEquals(128,result.width);assertEquals(96,result.height);assertTrue(result.file.length() > 128*96*6) }
            finally { result.close() }
        } finally { engine.close();source.delete() }
    }
    private fun verifyTiff(file: File) {
        val bytes = file.readBytes()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(42,buffer.getShort(2).toInt())
        val count = buffer.getShort(8).toInt()
        val tags = (0 until count).associate { i ->
            val offset = 10+i*12
            buffer.getShort(offset).toInt().and(65535) to buffer.getInt(offset+8)
        }
        val bits = tags.getValue(258)
        assertEquals(16,buffer.getShort(bits).toInt());assertEquals(16,buffer.getShort(bits+2).toInt())
        val start = tags.getValue(273)
        val samples = (start until bytes.size step 2).map { buffer.getShort(it).toInt().and(65535) }
        assertTrue("TIFF must retain more than 256 levels",samples.toSet().size > 256)
        assertTrue("TIFF must not be an 8-bit bitmap promoted to 16-bit",samples.any { it % 257 != 0 })
        val icc = tags.getValue(34675)
        assertEquals("acsp",bytes.copyOfRange(icc+36,icc+40).toString(Charsets.US_ASCII))
    }
}
