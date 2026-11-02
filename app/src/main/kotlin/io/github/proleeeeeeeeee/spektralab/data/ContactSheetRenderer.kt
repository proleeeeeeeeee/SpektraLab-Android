// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.graphics.createBitmap
import io.github.proleeeeeeeeee.spektralab.core.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import kotlin.math.*

data class ContactSheetOutput(val file: File,val mimeType: String,val pageNumbers: List<Int>)

/** Software pages, serial cells, at most 24 retained PDF image resources per part. No originals are changed.
 * [render] transfers ownership of a newly rendered, bounded bitmap to this writer; it must not return a UI/cache bitmap.
 */
class ContactSheetRenderer {
    suspend fun write(snapshot: ContactSheetSnapshot,format: ContactSheetFormat,directory: File,
        render: suspend (ContactSheetEntry,Int)->Bitmap,
        onProgress: suspend (Int,Int)->Unit={ _,_ -> }): List<ContactSheetOutput> = withContext(Dispatchers.Default) {
        check(directory.isDirectory || directory.mkdirs()) { "Contact-sheet staging directory unavailable" }
        val layout=ContactSheetLayout(snapshot.settings);val pages=layout.pages(snapshot.entries.size)
        val prefix="contact-"+UUID.randomUUID().toString();val staged=mutableListOf<File>();val result=mutableListOf<ContactSheetOutput>()
        var completed=0
        suspend fun draw(canvas: Canvas,page: ContactSheetPage) {
            canvas.drawColor(Color.WHITE)
            val paint=TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.rgb(30,32,32) }
            fun line(text: String,x: Double,baseline: Double,width: Double,size: Float) {
                paint.textSize=size
                canvas.drawText(TextUtils.ellipsize(text,paint,width.toFloat(),TextUtils.TruncateAt.END).toString(),x.toFloat(),baseline.toFloat(),paint)
            }
            line(snapshot.title,layout.margin,layout.margin+15,layout.width-layout.margin*2,15f)
            line("Contact sheet / ${snapshot.entries.size} photographs",layout.margin,layout.margin+30,layout.width-layout.margin*2,9f)
            line("${page.number} / ${pages.size}",layout.margin,layout.height-layout.margin,layout.width-layout.margin*2,9f)
            for (cell in page.cells) {
                currentCoroutineContext().ensureActive()
                val entry=snapshot.entries[cell.entryIndex]
                val edge=ceil(max(cell.image.width,cell.image.height)*layout.rasterScale).toInt().coerceIn(128,768)
                val bitmap=render(entry,edge)
                try {
                    require(bitmap.width>0 && bitmap.height>0 && max(bitmap.width,bitmap.height)<=768) { "Contact-sheet cell exceeded its bitmap bound" }
                    val ratio=min(cell.image.width/bitmap.width,cell.image.height/bitmap.height)
                    val w=bitmap.width*ratio;val h=bitmap.height*ratio
                    val x=cell.image.x+(cell.image.width-w)/2;val y=cell.image.y+(cell.image.height-h)/2
                    canvas.drawBitmap(bitmap,null,RectF(x.toFloat(),y.toFloat(),(x+w).toFloat(),(y+h).toFloat()),Paint(Paint.FILTER_BITMAP_FLAG))
                } finally { bitmap.recycle() }
                val caption=cell.caption
                line("${entry.number.toString().padStart(3,'0')}  ${entry.displayName}",caption.x,caption.y+10,caption.width,9f)
                val grade=if (PaperGrade.supports(entry.recipe)) "G${entry.recipe.paperGrade.label} " else ""
                line(Materials.film(entry.recipe.filmId).name+" / "+grade+Materials.paper(entry.recipe.paperId).name,caption.x,caption.y+22,caption.width,8f)
                if (snapshot.settings.includeRatings) {
                    val mark=entry.review
                    line("${mark.rating}/5  ${mark.flag.name.lowercase()}  ${mark.color.name.lowercase()}",caption.x,caption.y+34,caption.width,8f)
                }
                completed++;onProgress(completed,snapshot.entries.size)
            }
        }
        suspend fun finish(partial: File,target: File) {
            currentCoroutineContext().ensureActive()
            check(partial.length()>0 && partial.renameTo(target)) { "Contact-sheet file could not be completed" }
            staged+=target
        }
        try {
            if (format==ContactSheetFormat.JPEG) for (page in pages) {
                val target=File(directory,"$prefix-${page.number.toString().padStart(3,'0')}.jpg")
                val partial=File(directory,target.name+".part");staged+=partial
                val bitmap=createBitmap(layout.rasterWidth,layout.rasterHeight,Bitmap.Config.ARGB_8888)
                try {
                    val canvas=Canvas(bitmap);canvas.scale(layout.rasterScale.toFloat(),layout.rasterScale.toFloat());draw(canvas,page)
                    withContext(Dispatchers.IO) { partial.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG,95,it)) } }
                } finally { bitmap.recycle() }
                finish(partial,target);result+=ContactSheetOutput(target,"image/jpeg",listOf(page.number))
            } else for ((part,group) in pages.chunked(snapshot.settings.pdfPagesPerPart).withIndex()) {
                val target=File(directory,"$prefix-${(part+1).toString().padStart(3,'0')}.pdf")
                val partial=File(directory,target.name+".part");staged+=partial
                val document=PdfDocument()
                try {
                    for (page in group) {
                        currentCoroutineContext().ensureActive()
                        val pdfPage=document.startPage(PdfDocument.PageInfo.Builder(layout.width.toInt(),layout.height.toInt(),page.number).create())
                        try { draw(pdfPage.canvas,page) } finally { document.finishPage(pdfPage) }
                    }
                    withContext(Dispatchers.IO) { partial.outputStream().use(document::writeTo) }
                } finally { document.close() }
                finish(partial,target);result+=ContactSheetOutput(target,"application/pdf",group.map { it.number })
            }
            result.toList()
        } catch (failure: Throwable) { staged.forEach { it.delete() };throw failure }
    }
}
