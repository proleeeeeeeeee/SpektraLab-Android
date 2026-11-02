// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Small neutral glyphs; the enclosing button owns its accessible label. */
@Composable
internal fun UiIcon(name: String, color: Color = PaperWhite) {
    Canvas(Modifier.size(20.dp)) {
        val u = size.width / 24f
        fun line(x: Float,y: Float,x2: Float,y2: Float) = drawLine(color,Offset(x*u,y*u),Offset(x2*u,y2*u),1.5f*u)
        when (name) {
            "back" -> { line(15f,5f,8f,12f);line(8f,12f,15f,19f) }
            "down" -> { line(6f,9f,12f,15f);line(12f,15f,18f,9f) }
            "more" -> for (x in listOf(5f,12f,19f)) drawCircle(color,1.3f*u,Offset(x*u,12f*u))
            "Film" -> { drawRect(color,Offset(4f*u,5f*u),Size(16f*u,14f*u),style=Stroke(1.5f*u));line(4f,9f,20f,9f);line(10f,5f,10f,19f) }
            "Print" -> { drawRect(color,Offset(4f*u,4f*u),Size(14f*u,14f*u),style=Stroke(1.5f*u));line(8f,21f,21f,21f);line(21f,8f,21f,21f) }
            "Frame" -> { line(4f,9f,4f,4f);line(4f,4f,9f,4f);line(15f,4f,20f,4f);line(20f,4f,20f,9f);line(20f,15f,20f,20f);line(20f,20f,15f,20f);line(9f,20f,4f,20f);line(4f,20f,4f,15f) }
            "Crop" -> { line(7f,3f,7f,17f);line(7f,17f,21f,17f);line(3f,7f,17f,7f);line(17f,7f,17f,21f) }
            else -> for ((i,x) in listOf(8f,16f,10f).withIndex()) { val y=5f+i*7f;line(3f,y,21f,y);drawCircle(PanelColor,3f*u,Offset(x*u,y*u));drawCircle(color,2.3f*u,Offset(x*u,y*u),style=Stroke(1.5f*u)) }
        }
    }
}
