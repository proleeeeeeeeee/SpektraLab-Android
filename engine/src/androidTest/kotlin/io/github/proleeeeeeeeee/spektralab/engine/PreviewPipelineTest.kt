// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab.engine

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import io.github.proleeeeeeeeee.spektralab.core.*
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PreviewPipelineTest {
    @Test fun paperChangesReuseNegativeAndMatchAFreshSession() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val imports=File(context.filesDir,"imports").apply { mkdirs() }
        val file=File(imports,java.util.UUID.randomUUID().toString()+".photo")
        val input=Bitmap.createBitmap(128,96,Bitmap.Config.ARGB_8888)
        for (y in 0 until 96) for (x in 0 until 128) input.setPixel(x,y,Color.rgb(30+x,40+y,70+(x+y)/3))
        file.outputStream().use { assertTrue(input.compress(Bitmap.CompressFormat.PNG,100,it)) };input.recycle()
        val engine=FilmEngine(context);val fresh=FilmEngine(context)
        try {
            engine.initialize();fresh.initialize()
            val info=PhotoSource(context).inspect(file)
            val base=EditRecipe(adjustments=Adjustments(grain=0.0,halation=0.0))
            engine.renderSource(file,"pipeline-test",info,base,256)
            val initial=engine.statistics()
            val paper=base.copy(paperId="kodak_2383")
            val updated=engine.renderSource(file,"pipeline-test",info,paper,256)
            val afterPaper=engine.statistics()
            assertEquals("Paper-only changes must retain the decoded source",initial[0],afterPaper[0])
            assertEquals("Paper-only changes must retain the physical negative",initial[1],afterPaper[1])
            assertEquals(initial[2]+1,afterPaper[2])
            val expected=fresh.renderSource(file,"fresh-test",info,paper,256)
            assertArrayEquals("Incremental printing must match a fresh session",pixels(expected),pixels(updated))
            engine.renderSource(file,"pipeline-test",info,paper.copy(adjustments=paper.adjustments.copy(contrast=20.0)),256)
            val afterTone=engine.statistics()
            assertEquals("Scan tone must not invoke the physical print pipeline",afterPaper[2],afterTone[2])
            engine.renderSource(file,"pipeline-test",info,paper.withFilm("kodak_portra_400"),256)
            assertTrue("Changing film must rebuild the negative",engine.statistics()[1]>afterTone[1])
        } finally { engine.close();fresh.close();file.delete() }
    }
    private fun pixels(bitmap: Bitmap)=IntArray(bitmap.width*bitmap.height).also {
        bitmap.getPixels(it,0,bitmap.width,0,0,bitmap.width,bitmap.height)
    }
}
