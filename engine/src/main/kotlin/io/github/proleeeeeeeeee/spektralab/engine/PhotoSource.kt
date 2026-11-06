// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab.engine

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SourceInfo(val width: Int,val height: Int,val raw: Boolean)

/** RAW previews are developed from the sensor pixels, never an embedded JPEG. */
class PhotoSource(context: Context) {
    private val application = context.applicationContext
    suspend fun inspect(file: File): SourceInfo = withContext(Dispatchers.IO) {
        val info = NativeBridge.sourceInfo(privateSource(application,file))
        SourceInfo(info[0],info[1],info[2] != 0)
    }
    suspend fun preview(file: File,edge: Int): Bitmap = withContext(Dispatchers.IO) {
        NativeBridge.sourcePreview(privateSource(application,file),edge,memoryBudget(application))
    }
}

internal fun privateSource(context: Context,file: File): String {
    val root = File(context.filesDir,"imports").canonicalFile
    val actual = file.canonicalFile
    require(actual.parentFile == root && actual.name.matches(Regex("[a-f0-9-]{36}\\.photo")) && actual.isFile) {
        "The source must be an imported private photograph"
    }
    return actual.path
}

internal fun memoryBudget(context: Context): Long {
    val info = ActivityManager.MemoryInfo()
    context.getSystemService(ActivityManager::class.java).getMemoryInfo(info)
    // Leave headroom for Android, the UI and the GPU driver. This is a job
    // admission budget, not permission to resize an Original export.
    return minOf((info.availMem*.65).toLong(),(info.totalMem*.50).toLong()).coerceAtLeast(0L)
}

/** The same conservative budget used by native export admission. */
fun availableRenderMemory(context: Context): Long = memoryBudget(context)
