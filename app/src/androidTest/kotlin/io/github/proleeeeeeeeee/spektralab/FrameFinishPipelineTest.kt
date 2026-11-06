// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.proleeeeeeeeee.spektralab.core.Adjustments
import io.github.proleeeeeeeeee.spektralab.core.EditRecipe
import io.github.proleeeeeeeeee.spektralab.core.ExportFormat
import io.github.proleeeeeeeeee.spektralab.core.ExportSettings
import io.github.proleeeeeeeeee.spektralab.core.ExportSize
import io.github.proleeeeeeeeee.spektralab.core.FilmFrame
import io.github.proleeeeeeeeee.spektralab.engine.FilmEngine
import io.github.proleeeeeeeeee.spektralab.engine.PhotoSource
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FrameFinishPipelineTest {
    @Test fun photoAdjustmentsChangeTheGateButNotTheLateFrameInPreviewOrExport() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = fixture(context)
        val engine = FilmEngine(context)
        try {
            engine.initialize()
            val info = PhotoSource(context).inspect(source)
            val base = EditRecipe(frame = FilmFrame.STRIP_135,
                adjustments = Adjustments(autoExposure = false, grain = 0.0, halation = 0.0))
            val brighter = base.copy(adjustments = base.adjustments.copy(exposure = 2.5,
                temperature = 45.0, contrast = 30.0))

            val previewA = engine.renderSource(source,"late-frame",info,base)
            val previewB = engine.renderSource(source,"late-frame",info,brighter)
            assertStableFrame(previewA,previewB,"preview")
            assertPhotoChanged(previewA,previewB,"preview")

            val settings = ExportSettings(ExportFormat.PNG,ExportSize.COMPACT,100)
            val exportA = engine.prepareExport(source,info,base,settings)
            val exportB = engine.prepareExport(source,info,brighter,settings)
            try {
                val bitmapA = requireNotNull(BitmapFactory.decodeFile(exportA.file.path))
                val bitmapB = requireNotNull(BitmapFactory.decodeFile(exportB.file.path))
                assertStableFrame(bitmapA,bitmapB,"export")
                assertPhotoChanged(bitmapA,bitmapB,"export")
                bitmapA.recycle();bitmapB.recycle()
            } finally { exportA.close();exportB.close() }
            previewA.recycle();previewB.recycle()
        } finally { engine.close();source.delete() }
    }

    private fun fixture(context: Context): File {
        val file = File(File(context.filesDir,"imports").apply { mkdirs() },"${UUID.randomUUID()}.photo")
        val bitmap = Bitmap.createBitmap(480,720,Bitmap.Config.ARGB_8888)
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            bitmap.setPixel(x,y,Color.rgb(20+x*210/bitmap.width,15+y*220/bitmap.height,
                35+(x+y)*170/(bitmap.width+bitmap.height)))
        }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        bitmap.recycle()
        return file
    }

    private fun assertStableFrame(a: Bitmap,b: Bitmap,label: String) {
        assertEquals(a.width,b.width);assertEquals(a.height,b.height)
        var compared=0
        val edge=(a.width*.10).toInt().coerceAtLeast(1)
        for (y in 0 until a.height step 3) for (x in 0 until edge step 2) {
            assertEquals("$label left frame changed at $x,$y",a.getPixel(x,y),b.getPixel(x,y));compared++
            val right=a.width-1-x
            assertEquals("$label right frame changed at $right,$y",a.getPixel(right,y),b.getPixel(right,y));compared++
        }
        assertTrue(compared>1_000)
    }

    private fun assertPhotoChanged(a: Bitmap,b: Bitmap,label: String) {
        var changed=0;var total=0
        val x0=(a.width*.25).toInt();val x1=(a.width*.75).toInt()
        val y0=(a.height*.20).toInt();val y1=(a.height*.80).toInt()
        for (y in y0 until y1 step 3) for (x in x0 until x1 step 3) {
            if (a.getPixel(x,y)!=b.getPixel(x,y)) changed++;total++
        }
        assertTrue("$label photograph did not respond to adjustments: $changed / $total",changed>total/3)
    }
}
