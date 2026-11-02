// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Gainmap
import android.os.Build
import androidx.annotation.RequiresApi
import java.io.File
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Owns only generated cache files. Publishing copies a completed file to MediaStore. */
class PreparedExport internal constructor(
    val file: File,val width: Int,val height: Int,private val owned: List<File>,
) : AutoCloseable {
    override fun close() { owned.forEach { it.delete() } }
}

@RequiresApi(34)
internal suspend fun encodeHdr(base: File,gain: File,output: File,metadata: FloatArray,quality: Int) {
    require(metadata.size == 4 && metadata.all { it.isFinite() } && metadata[2] > 0 && metadata[3] > metadata[2])
    val pixels = metadata[0].toLong()*metadata[1].toLong()
    val runtime = Runtime.getRuntime()
    val available = runtime.maxMemory()-(runtime.totalMemory()-runtime.freeMemory())
    require(pixels*6+32L*1024*1024 < available*.8) {
        "HDR encoding exceeds the available Bitmap memory. Choose 4096 or 2560 px; Original is never silently resized."
    }
    val bitmap = BitmapFactory.decodeFile(base.path) ?: error("Could not decode the HDR base")
    val gainBitmap = BitmapFactory.decodeFile(gain.path) ?: run { bitmap.recycle();error("Could not decode the HDR gain map") }
    try {
        currentCoroutineContext().ensureActive()
        check(Build.VERSION.SDK_INT >= 34)
        val map = Gainmap(gainBitmap).apply {
            setRatioMin(metadata[2],metadata[2],metadata[2])
            setRatioMax(metadata[3],metadata[3],metadata[3])
            setGamma(1f,1f,1f)
            setEpsilonSdr(1e-4f,1e-4f,1e-4f)
            setEpsilonHdr(1e-4f,1e-4f,1e-4f)
            setDisplayRatioForFullHdr(maxOf(1.01f,metadata[3]))
            setMinDisplayRatioForHdrTransition(1f)
        }
        bitmap.setGainmap(map)
        output.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG,quality,it)) { "Ultra HDR encoding failed" } }
        currentCoroutineContext().ensureActive()
        // Verify the encoded file, not just the in-memory Gainmap object.
        val verified = BitmapFactory.decodeFile(output.path,BitmapFactory.Options().apply { inSampleSize = 4 })
            ?: error("The HDR photograph could not be verified")
        try { check(verified.hasGainmap()) { "Android did not preserve the HDR gain map" } }
        finally { verified.recycle() }
    } finally { bitmap.recycle();gainBitmap.recycle() }
}
