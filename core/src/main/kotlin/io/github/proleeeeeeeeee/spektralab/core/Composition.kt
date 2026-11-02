// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab.core

import kotlinx.serialization.Serializable
import kotlin.math.*

@Serializable
data class CropRect(val x: Double = 0.0, val y: Double = 0.0, val width: Double = 1.0, val height: Double = 1.0) {
    init {
        require(listOf(x, y, width, height).all { it.isFinite() })
        require(x >= 0 && y >= 0 && width in .05..1.0 && height in .05..1.0)
        require(x + width <= 1.00000001 && y + height <= 1.00000001)
    }
}

@Serializable
enum class CropAspect(val label: String, val ratio: Double?) {
    FREE("Free", null), ORIGINAL("Original", null), SQUARE("1:1", 1.0),
    R3_2("3:2", 1.5), R2_3("2:3", 2.0 / 3), R4_3("4:3", 4.0 / 3), R3_4("3:4", 3.0 / 4),
    R16_9("16:9", 16.0 / 9),
    R9_16("9:16", 9.0 / 16);
}

@Serializable
data class Composition(
    val crop: CropRect = CropRect(),
    val aspect: CropAspect = CropAspect.FREE,
    val straighten: Double = 0.0,
    val quarterTurns: Int = 0,
    val flipH: Boolean = false,
    val flipV: Boolean = false,
) {
    init {
        require(straighten.isFinite() && straighten in -15.0..15.0)
        require(quarterTurns in 0..3)
    }
    fun rotate() = copy(quarterTurns = (quarterTurns + 1) % 4)
}

/**
 * Crop coordinates always refer to the unrotated source. The native engine then
 * straightens, turns and flips it. Shrinking the crop keeps all rotated corners
 * inside the source, so straightening never manufactures black corner triangles.
 */
fun composedCrop(recipe: EditRecipe, width: Int, height: Int): NormalizedCrop {
    require(width > 0 && height > 0)
    val c = recipe.composition
    val area = c.crop
    val sourceAspect = width.toDouble() / height
    val ratio = recipe.frame.gateAspect(sourceAspect)
        ?: if (c.aspect == CropAspect.ORIGINAL) sourceAspect else c.aspect.ratio
    var cw = area.width
    var ch = area.height
    if (ratio != null) {
        if (cw * sourceAspect / ch > ratio) cw = ch * ratio / sourceAspect
        else ch = cw * sourceAspect / ratio
    }
    val cx = area.x + (area.width - cw) * recipe.framingX + cw / 2
    val cy = area.y + (area.height - ch) * recipe.framingY + ch / 2
    val a = Math.toRadians(c.straighten)
    val ex = (cw * width * abs(cos(a)) + ch * height * abs(sin(a))) / 2
    val ey = (cw * width * abs(sin(a)) + ch * height * abs(cos(a))) / 2
    val scale = minOf(1.0, min(cx, 1 - cx) * width / ex, min(cy, 1 - cy) * height / ey)
    cw *= scale
    ch *= scale
    return NormalizedCrop(cx - cw / 2, cy - ch / 2, cw, ch)
}
