// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab.engine

import android.graphics.Bitmap
import androidx.annotation.Keep

/** The only JNI surface. File paths are validated against app-private storage. */
@Keep
internal object NativeBridge {
    init { System.loadLibrary("spektralab_android") }
    external fun create(resources: String): Long
    external fun render(handle: Long, input: Bitmap, sourceKey: String, parameters: String, scanTone: FloatArray): Bitmap
    external fun sourceInfo(file: String): IntArray
    external fun sourcePreview(file: String, edge: Int, budget: Long): Bitmap
    external fun renderFile(handle: Long, file: String, sourceKey: String, parameters: String,
                            scanTone: FloatArray, edge: Int, budget: Long, raw: Boolean): Bitmap
    external fun exportFile(handle: Long, file: String, parameters: String, scanTone: FloatArray,
                            edge: Int, budget: Long, raw: Boolean, destination: String, gainPath: String,
                            format: Int, quality: Int): FloatArray
    external fun capabilities(handle: Long): String
    external fun cancel(handle: Long)
    external fun destroy(handle: Long)
}
