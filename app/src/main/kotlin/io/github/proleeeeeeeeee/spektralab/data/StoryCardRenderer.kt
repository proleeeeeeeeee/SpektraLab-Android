// SPDX-License-Identifier: GPL-3.0-only
package io.github.proleeeeeeeeee.spektralab.data

import android.graphics.*
import android.text.TextPaint
import android.text.TextUtils
import io.github.proleeeeeeeeee.spektralab.core.*
import java.util.Locale
import kotlin.math.min

/** Bounded, EXIF-free sharing image. The supplied photo is an exact native render. */
object StoryCardRenderer {
    const val WIDTH=1280
    const val HEIGHT=1800
    fun render(photo: Bitmap,document: RecipeDocument): Bitmap {
        require(photo.width>0 && photo.height>0 && photo.allocationByteCount<=16*1024*1024)
        val image=Bitmap.createBitmap(WIDTH,HEIGHT,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(image);canvas.drawColor(Color.rgb(11,13,14))
        val paint=TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.rgb(244,243,239) }
        fun line(text: String,y: Float,size: Float,color: Int=Color.rgb(244,243,239)) {
            paint.color=color;paint.textSize=size
            canvas.drawText(TextUtils.ellipsize(text,paint,1152f,TextUtils.TruncateAt.END).toString(),64f,y,paint)
        }
        val gold=Color.rgb(214,180,119);val muted=Color.rgb(160,167,167)
        line("SpektraLab / Recipe story",75f,28f,gold)
        line(document.name,140f,44f)
        val region=RectF(64f,184f,1216f,1204f)
        val ratio=min(region.width()/photo.width,region.height()/photo.height)
        val w=photo.width*ratio;val h=photo.height*ratio
        val x=region.centerX()-w/2;val y=region.centerY()-h/2
        canvas.drawBitmap(photo,null,RectF(x,y,x+w,y+h),Paint(Paint.FILTER_BITMAP_FLAG))
        val r=document.settings.recipe;val a=r.adjustments
        line(Materials.film(r.filmId).brand+" "+Materials.film(r.filmId).name,1290f,40f)
        line(Materials.paper(r.paperId).brand+" "+Materials.paper(r.paperId).name+
            if(PaperGrade.supports(r))" / Grade "+r.paperGrade.label else "",1340f,32f,gold)
        fun n(value: Double)=String.format(Locale.ROOT,"%.1f",value)
        val rows=listOf(
            "Exposure "+n(a.exposure)+" EV / Auto exposure "+if(a.autoExposure)"on" else "off",
            "Temperature "+n(a.temperature)+" / Tint "+n(a.tint),
            "Contrast "+n(a.contrast)+" / Saturation "+n(a.saturation),
            "Highlights "+n(a.highlights)+" / Shadows "+n(a.shadows),
            "Grain "+n(a.grain)+" / Halation "+n(a.halation),
            "Frame: "+r.frame.label+" / Crop and orientation: "+if(document.settings.includeComposition)"included in recipe" else "photo-specific",
        )
        rows.forEachIndexed { index,text->line(text,1410f+index*43f,27f,muted) }
        line("Creative simulation / SDR share card",1722f,24f,muted)
        line("Recipe v1 / Engine "+document.engineRevision.take(8),1760f,23f,muted)
        return image
    }
}
