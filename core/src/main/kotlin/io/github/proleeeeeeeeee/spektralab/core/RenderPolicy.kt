// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab.core

import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object RenderPolicy {
    const val PREVIEW_LONG_EDGE = 1024
    // Bound UI/comparison decoding. File exports use ExportSize and native
    // memory admission, including a distinct full-source Original mode.
    const val EXPORT_LONG_EDGE = 2560
    const val MAX_IMPORT_BYTES = 512L * 1024 * 1024
    const val MAX_SOURCE_PIXELS = 240_000_000L
    const val MAX_SOURCE_SIDE = 32768
    const val MAX_DECODED_PIXELS = 7_000_000L
    fun targetSize(width: Int, height: Int, longEdge: Int): Pair<Int, Int> {
        require(width > 0 && height > 0 && longEdge in 64..EXPORT_LONG_EDGE)
        require(width <= MAX_SOURCE_SIDE && height <= MAX_SOURCE_SIDE)
        require(width.toLong() * height <= MAX_SOURCE_PIXELS) { "Source image is too large" }
        val scale = min(1.0, longEdge.toDouble() / maxOf(width, height))
        return maxOf(1, (width * scale).roundToInt()) to maxOf(1, (height * scale).roundToInt())
    }
}

data class NormalizedCrop(val x: Double, val y: Double, val width: Double, val height: Double)

fun cropFor(recipe: EditRecipe, width: Int, height: Int): NormalizedCrop {
    return composedCrop(recipe, width, height)
}

/**
 * Keep the upstream flat C ABI schema at one boundary. Android supplies encoded
 * sRGB; the engine decodes it and returns encoded sRGB without a display shortcut.
 */
fun engineParameters(recipe: EditRecipe, width: Int, height: Int): String {
    val crop = cropFor(recipe, width, height)
    return Json.encodeToString(buildJsonObject {
        put("film_stock", recipe.filmId)
        put("print_stock", recipe.paperId)
        put("input_color_space", "sRGB")
        put("input_cctf_decoding", true)
        put("output_color_space", "sRGB")
        put("output_cctf_encoding", true)
        put("auto_exposure", recipe.adjustments.autoExposure)
        put("exposure_compensation_ev", recipe.adjustments.exposure)
        put("grain_active", recipe.adjustments.grain > 0)
        put("grain_amount", recipe.adjustments.grain)
        put("halation_active", recipe.adjustments.halation > 0)
        put("halation_amount", recipe.adjustments.halation)
        put("auto_exposure_method", "balanced")
        put("preview_long_edge", RenderPolicy.PREVIEW_LONG_EDGE)
        if (PaperGrade.supports(recipe)) {
            // Explicit fitted BW packs bypass the color neutral-filter database.
            // All rendering surfaces share this physical-print boundary.
            val grade = recipe.paperGrade
            put("enlarger_illuminant", PaperGrade.ENLARGER_ILLUMINANT)
            put("c_filter_neutral", 0)
            put("m_filter_neutral", grade.mFilterNeutral)
            put("y_filter_neutral", grade.yFilterNeutral)
            put("print_exposure", grade.printExposure)
        }
        put("geometry_crop_x", crop.x)
        put("geometry_crop_y", crop.y)
        put("geometry_crop_w", crop.width)
        put("geometry_crop_h", crop.height)
        put("geometry_rotation_deg", recipe.composition.straighten)
        put("geometry_quarter_turns", recipe.composition.quarterTurns)
        put("geometry_flip_h", recipe.composition.flipH)
        put("geometry_flip_v", recipe.composition.flipV)
        put("overscan_active", recipe.frame != FilmFrame.NONE)
        put("overscan_format", recipe.frame.engineFormat ?: "135")
        put("overscan_mode", if (recipe.frame.filed) "filed" else "strip")
        put("film_format_mm", when (recipe.frame) {
            FilmFrame.SQUARE_120 -> 56
            FilmFrame.XPAN -> 65
            else -> 36
        })
        put("overscan_edge_text", if (recipe.filmId=="kodak_gold_200" && recipe.frame==FilmFrame.SQUARE_120)
            "KODAK 200" else Materials.film(recipe.filmId).edgeText)
        put("overscan_camera_seed", 42)
        put("overscan_frame_seed", 36)
        put("overscan_leaks", 0)
        put("contrast_mask_active", false)
    })
}
