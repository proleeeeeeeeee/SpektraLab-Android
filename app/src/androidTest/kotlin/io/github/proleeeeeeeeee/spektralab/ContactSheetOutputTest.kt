// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import io.github.proleeeeeeeeee.spektralab.core.*
import io.github.proleeeeeeeeee.spektralab.data.ContactSheetRenderer
import java.io.File
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class ContactSheetOutputTest {
    private fun snapshot()=ContactSheetSnapshot("接触印样 / Test roll",(0 until 13).map { ContactSheetEntry("id-$it",it*2+1,"照片 $it.jpg",if(it==0) EditRecipe().withFilm(PaperGrade.FITTED_FILM_ID).copy(paperGrade=PaperGrade.GRADE_5) else EditRecipe(),PhotoReview(it%6)) })
    private fun cell(index: Int)=Bitmap.createBitmap(80,120,Bitmap.Config.ARGB_8888).apply { eraseColor(if (index%2==0) Color.RED else Color.BLUE) }
    @Test fun jpegAndPdfContainCorrectPagesAndActualCellPixels() { runBlocking {
        val root=File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir,"stage2-evidence").apply { mkdirs() }
        val snapshot=snapshot();val writer=ContactSheetRenderer();val visited=mutableListOf<String>()
        val jpeg=writer.write(snapshot,ContactSheetFormat.JPEG,root,{ entry,edge -> assertTrue(edge<=768);visited+=entry.photoId;cell(entry.number/2) })
        assertEquals(snapshot.entries.map { it.photoId },visited);assertEquals(2,jpeg.size)
        val image=BitmapFactory.decodeFile(jpeg[0].file.path);assertEquals(2400,maxOf(image.width,image.height))
        val layout=ContactSheetLayout(snapshot.settings);val first=layout.pages(13)[0].cells[0].image
        val pixel=image.getPixel(((first.x+first.width/2)*layout.rasterScale).toInt(),((first.y+first.height/2)*layout.rasterScale).toInt())
        assertTrue(Color.red(pixel)>245 && Color.blue(pixel)<10);image.recycle()
        val pdf=writer.write(snapshot,ContactSheetFormat.PDF,root,{ entry,_ -> cell(entry.number/2) })
        assertEquals(1,pdf.size);assertEquals(listOf(1,2),pdf[0].pageNumbers)
        ParcelFileDescriptor.open(pdf[0].file,ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor -> PdfRenderer(descriptor).use { document ->
            assertEquals(2,document.pageCount)
            document.openPage(0).use { page ->
                assertEquals(595,page.width);assertEquals(842,page.height)
                val bitmap=Bitmap.createBitmap(page.width,page.height,Bitmap.Config.ARGB_8888)
                page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                val color=bitmap.getPixel((first.x+first.width/2).toInt(),(first.y+first.height/2).toInt())
                assertTrue(Color.red(color)>245 && Color.blue(color)<10)
                File(root,"pdf-raster-check.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
            }
        } }
        File(root,"report.json").writeText(org.json.JSONObject().put("jpegPages",jpeg.size).put("pdfParts",pdf.size).put("pdfPages",2).put("cellPixelsVerified",true).put("sourceOrderVerified",true).toString(2))
    } }
    @Test fun largePdfSplitsAtBoundAndKeepsGlobalPageNumbers() { runBlocking {
        val root=File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir,"stage2-pdf-split-evidence").apply { mkdirs() }
        val snapshot=ContactSheetSnapshot("Split test",(0 until 25).map { ContactSheetEntry("id-$it",it+1,"Photo-$it.jpg",EditRecipe()) },ContactSheetSettings(SheetOrientation.LANDSCAPE))
        val files=ContactSheetRenderer().write(snapshot,ContactSheetFormat.PDF,root,{ entry,_ -> cell(entry.number) })
        assertEquals(2,files.size);assertEquals(listOf(listOf(1,2),listOf(3)),files.map { it.pageNumbers })
        for ((index,file) in files.withIndex()) ParcelFileDescriptor.open(file.file,ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor -> PdfRenderer(descriptor).use { document ->
            assertEquals(if (index==0) 2 else 1,document.pageCount)
            document.openPage(0).use { page -> assertEquals(842,page.width);assertEquals(595,page.height) }
        } }
    } }
    @Test fun failureAndCancellationRemoveEveryStagedPage() { runBlocking {
        val root=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"sheet-failure-${System.nanoTime()}").apply { mkdirs() }
        val writer=ContactSheetRenderer();val snapshot=snapshot()
        for (cancel in listOf(false,true)) {
            try {
                writer.write(snapshot,ContactSheetFormat.JPEG,root,{ entry,_ ->
                    if (entry.number==25) { if (cancel) throw CancellationException("Test cancellation") else error("Test missing source") }
                    cell(entry.number/2)
                })
                fail("Expected failure")
            } catch (failure: Throwable) { if (cancel) assertTrue(failure is CancellationException) else assertEquals("Test missing source",failure.message) }
            assertTrue("No partial or completed pages may remain after a failed group",root.listFiles().orEmpty().isEmpty())
        }
        root.delete()
    } }
}
