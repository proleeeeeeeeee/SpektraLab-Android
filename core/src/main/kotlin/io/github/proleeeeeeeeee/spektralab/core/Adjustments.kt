// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab.core

import kotlinx.serialization.Serializable

@Serializable
data class Adjustments(
    val exposure: Double = 0.0,
    val autoExposure: Boolean = true,
    val temperature: Double = 0.0,
    val tint: Double = 0.0,
    val contrast: Double = 0.0,
    val saturation: Double = 0.0,
    val highlights: Double = 0.0,
    val shadows: Double = 0.0,
    val grain: Double = 1.0,
    val halation: Double = 1.0,
) {
    init {
        require(exposure.isFinite() && exposure in -3.0..3.0)
        require(contrast.isFinite() && contrast in -75.0..75.0)
        require(listOf(temperature, tint, saturation, highlights, shadows).all { it.isFinite() && it in -100.0..100.0 })
        require(grain.isFinite() && grain in 0.0..2.0)
        require(halation.isFinite() && halation in 0.0..4.0)
    }
    /** Native scan-tone order; exposure and physical effects travel separately. */
    fun scanTone() = floatArrayOf(temperature.toFloat(), tint.toFloat(), contrast.toFloat(),
        saturation.toFloat(), highlights.toFloat(), shadows.toFloat())

    fun withoutScanTone() = copy(temperature = 0.0, tint = 0.0, contrast = 0.0,
        saturation = 0.0, highlights = 0.0, shadows = 0.0)
}

/** Physical processing stays in the engine; scan-only edits share this base. */
fun EditRecipe.scanBase() = copy(adjustments = adjustments.withoutScanTone())

enum class Adjustment(val label: String, val minimum: Float, val maximum: Float, val initial: Float = 0f) {
    EXPOSURE("Exposure", -3f, 3f), TEMPERATURE("Temperature", -100f, 100f),
    TINT("Tint", -100f, 100f), CONTRAST("Contrast", -75f, 75f),
    SATURATION("Saturation", -100f, 100f), HIGHLIGHTS("Highlights", -100f, 100f),
    SHADOWS("Shadows", -100f, 100f), GRAIN("Grain", 0f, 2f, 1f), HALATION("Halation", 0f, 4f, 1f);

    fun read(a: Adjustments): Float = when (this) {
        EXPOSURE -> a.exposure; TEMPERATURE -> a.temperature; TINT -> a.tint
        CONTRAST -> a.contrast; SATURATION -> a.saturation; HIGHLIGHTS -> a.highlights
        SHADOWS -> a.shadows; GRAIN -> a.grain; HALATION -> a.halation
    }.toFloat()
    fun write(a: Adjustments, value: Float): Adjustments {
        require(value.isFinite())
        val v = value.coerceIn(minimum, maximum).toDouble()
        return when (this) {
            EXPOSURE -> a.copy(exposure = v); TEMPERATURE -> a.copy(temperature = v); TINT -> a.copy(tint = v)
            CONTRAST -> a.copy(contrast = v); SATURATION -> a.copy(saturation = v); HIGHLIGHTS -> a.copy(highlights = v)
            SHADOWS -> a.copy(shadows = v); GRAIN -> a.copy(grain = v); HALATION -> a.copy(halation = v)
        }
    }
}
