// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab.engine

import android.graphics.Bitmap
import androidx.annotation.Keep

/** The only JNI surface. No Activity, URI or caller-controlled path crosses it. */
@Keep
internal object NativeBridge {
    init { System.loadLibrary("spektralab_android") }
    external fun create(resources: String): Long
    external fun render(handle: Long, input: Bitmap, sourceKey: String, parameters: String): Bitmap
    external fun capabilities(handle: Long): String
    external fun cancel(handle: Long)
    external fun destroy(handle: Long)
}
