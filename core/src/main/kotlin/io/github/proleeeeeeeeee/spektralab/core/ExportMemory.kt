// SPDX-License-Identifier: GPL-3.0-only
package io.github.proleeeeeeeeee.spektralab.core

import kotlin.math.ceil

data class ExportSourceSize(val width: Int,val height: Int,val raw: Boolean=false)
data class ExportMemoryEstimate(val decodedWidth: Int,val decodedHeight: Int,val requiredBytes: Long)

/** Mirrors native input admission. Frames/driver allocations can still require more. */
fun estimateExportMemory(source: ExportSourceSize,settings: ExportSettings): ExportMemoryEstimate {
    require(source.width in 1..RenderPolicy.MAX_SOURCE_SIDE && source.height in 1..RenderPolicy.MAX_SOURCE_SIDE)
    val originalPixels=source.width.toLong()*source.height
    require(originalPixels<=RenderPolicy.MAX_SOURCE_PIXELS)
    val scale=if(settings.size.longEdge==0)1.0 else minOf(1.0,settings.size.longEdge.toDouble()/maxOf(source.width,source.height))
    val w=ceil(source.width*scale).toInt();val h=ceil(source.height*scale).toInt()
    val reserve=128L*1024*1024
    val render=w.toLong()*h*(if(settings.format==ExportFormat.HDR_JPEG)176 else 160)+reserve
    val decode=if(source.raw)originalPixels*40+reserve else w.toLong()*h*24+64L*1024*1024
    return ExportMemoryEstimate(w,h,maxOf(render,decode))
}
