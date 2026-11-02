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
import androidx.compose.ui.graphics.drawscope.Stroke
import io.github.proleeeeeeeeee.spektralab.core.FilmFrame

/**
 * Original vector illustrations for physical frame choices.
 */
@Composable
internal fun FrameArt(frame: FilmFrame, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
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
    }
}
