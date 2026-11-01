// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import io.github.proleeeeeeeeee.spektralab.core.FilmFrame

/**
 * Original vector illustrations, drawn on the surface without a white image tile.
 * These are navigation cues, not reproductions of manufacturers' packaging.
 */
@Composable
internal fun StockArt(id: String, modifier: Modifier, paper: Boolean = false, frame: FilmFrame? = null) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        if (frame != null) {
            val wide = frame == FilmFrame.XPAN
            val square = frame == FilmFrame.SQUARE_120
            val left = w * if (square) .26f else .13f
            val top = h * if (wide) .30f else .17f
            val width = w - left * 2
            val height = h - top * 2
            val edge = if (frame == FilmFrame.NONE) MutedColor else Color(0xFF8E7152)
            drawRoundRect(edge, Offset(left, top), Size(width, height), CornerRadius(3f), style = Stroke(2f))
            if (frame != FilmFrame.NONE) {
                drawRect(edge.copy(alpha = .15f), Offset(left, top), Size(width, height))
                drawRect(CanvasColor, Offset(left + width * .13f, top + height * .15f),
                    Size(width * .74f, height * .70f))
                if (!square) repeat(7) { index ->
                    val x = left + width * (.04f + index * .135f)
                    for (y in listOf(top + height * .025f, top + height * .905f))
                        drawRoundRect(PaperWhite.copy(alpha = .65f), Offset(x, y),
                            Size(width * .055f, height * .065f), CornerRadius(1f))
                }
            }
            return@Canvas
        }
        if (paper) {
            drawRect(Color(0xFFB0ABA0), Offset(w * .18f, h * .14f), Size(w * .64f, h * .74f))
            drawRect(PaperWhite, Offset(w * .14f, h * .09f), Size(w * .64f, h * .74f))
            drawRect(Color(0xFF63746B), Offset(w * .20f, h * .15f), Size(w * .52f, h * .48f))
            drawPath(Path().apply {
                moveTo(w * .20f, h * .63f); lineTo(w * .42f, h * .29f)
                lineTo(w * .53f, h * .43f); lineTo(w * .72f, h * .25f)
                lineTo(w * .72f, h * .63f); close()
            }, Color(0xFF344A43))
            return@Canvas
        }
        val color = when (id) {
            "kodak_gold_200" -> Color(0xFFE1B83F)
            "kodak_portra_400" -> Color(0xFFB997C8)
            "cinestill_800t" -> Color(0xFFCE5446)
            else -> Color(0xFFB9B9AF)
        }
        drawPath(Path().apply {
            moveTo(w * .12f, h * .30f); lineTo(w * .75f, h * .18f)
            lineTo(w * .88f, h * .31f); lineTo(w * .25f, h * .45f); close()
        }, color.copy(alpha = .80f))
        drawPath(Path().apply {
            moveTo(w * .12f, h * .30f); lineTo(w * .75f, h * .43f)
            lineTo(w * .75f, h * .82f); lineTo(w * .12f, h * .68f); close()
        }, color)
        drawPath(Path().apply {
            moveTo(w * .75f, h * .43f); lineTo(w * .88f, h * .31f)
            lineTo(w * .88f, h * .69f); lineTo(w * .75f, h * .82f); close()
        }, color.copy(alpha = .55f))
        drawPath(Path().apply {
            moveTo(w * .12f, h * .52f); lineTo(w * .75f, h * .65f)
            lineTo(w * .75f, h * .76f); lineTo(w * .12f, h * .63f); close()
        }, Color(0xFF303236))
        drawLine(Color(0xFF303236), Offset(w * .20f, h * .41f), Offset(w * .48f, h * .47f), 4f)
    }
}
