// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab.core

@kotlinx.serialization.Serializable
enum class ExportFormat(val extension: String, val mimeType: String) {
    JPEG("jpg", "image/jpeg"),
    PNG("png", "image/png"),
    TIFF16("tif", "image/tiff"),
    HDR_JPEG("jpg", "image/jpeg"),
}
fun exportName(input: String, format: ExportFormat): String {
    val stem = input.substringBeforeLast('.', input)
        .replace(Regex("[\\p{Cntrl}/\\\\:*?\"<>|]"), "_")
        .trim().trim('.').take(80).ifBlank { "Photograph" }
    return stem + "_SpektraLab." + format.extension
}
