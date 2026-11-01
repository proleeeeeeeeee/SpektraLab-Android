// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab.engine

import android.content.Context
import java.io.File

/**
 * Only signed APK assets are installed. Profiles cannot be supplied by an intent,
 * photo filename or network response. The marker is written after the complete copy.
 */
internal object EngineResources {
    private const val REVISION = "a46627d250545a394ca21522f8a28b0fb89e4c42"
    fun install(context: Context): File {
        val target = File(context.filesDir, "engine-" + REVISION)
        val marker = File(target, ".complete")
        if (marker.isFile) return target
        target.mkdirs()
        copyAsset(context, "engine", target)
        check(File(target, "spektrafilm_constants.bin").isFile) { "Engine constants are missing" }
        check(File(target, "vulkan/spk_take_rgb.spv").isFile) { "Engine shaders are missing" }
        marker.writeText(REVISION, Charsets.UTF_8)
        return target
    }
    private fun copyAsset(context: Context, asset: String, target: File) {
        val entries = context.assets.list(asset).orEmpty()
        if (entries.isNotEmpty()) {
            check(target.isDirectory || target.mkdirs())
            entries.forEach { entry ->
                require('/' !in entry && '\\' !in entry && entry != "..")
                copyAsset(context, asset + "/" + entry, File(target, entry))
            }
        } else {
            target.parentFile?.mkdirs()
            context.assets.open(asset).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }
}
