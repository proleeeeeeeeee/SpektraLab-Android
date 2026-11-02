// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.proleeeeeeeeee.spektralab.core.*

internal enum class WorkspacePage { PHOTOS, EDITOR, MATERIALS, NEGATIVE, SETTINGS }
internal enum class WorkspaceOverlay { NONE, PHOTO_EXPORT, BATCH_EXPORT, PRESETS }
internal enum class EditorPane(val title: String) { FILM("胶片"), PAPER("印相"), ADJUST("调整"), COMPOSE("构图") }

@Composable internal fun WorkspaceNavigation(page: WorkspacePage, enabled: Boolean, onSelect: (WorkspacePage) -> Unit) {
    NavigationBar(containerColor = PanelColor, windowInsets = WindowInsets(0, 0, 0, 0), modifier = Modifier.testTag("workspace-navigation")) {
        listOf(Triple(WorkspacePage.PHOTOS, "照片", "photos"), Triple(WorkspacePage.MATERIALS, "材料", "materials"), Triple(WorkspacePage.NEGATIVE, "工具", "tools")).forEach { (target, label, tag) ->
            NavigationBarItem(selected = page == target, onClick = { onSelect(target) }, enabled = enabled,
                icon = { NavigationGlyph(target, if (page == target) AccentColor else MutedColor) }, label = { Text(label) },
                modifier = Modifier.heightIn(min = 56.dp).testTag("nav-$tag"),
                colors = NavigationBarItemDefaults.colors(indicatorColor = CanvasColor, selectedTextColor = AccentColor, unselectedTextColor = MutedColor))
        }
    }
}

@Composable private fun NavigationGlyph(page: WorkspacePage, color: Color) {
    Canvas(Modifier.size(23.dp)) {
        val unit = size.width / 24f
        val stroke = Stroke(1.4f * unit)
        when (page) {
            WorkspacePage.PHOTOS -> for (x in listOf(3f, 13f)) for (y in listOf(3f, 13f)) drawRect(color, Offset(x * unit, y * unit), Size(7f * unit, 7f * unit), style = stroke)
            WorkspacePage.MATERIALS -> {
                drawRect(color, Offset(3f * unit, 3f * unit), Size(13f * unit, 15f * unit), style = stroke)
                drawLine(color, Offset(19f * unit, 7f * unit), Offset(19f * unit, 21f * unit), 1.4f * unit)
                drawLine(color, Offset(7f * unit, 21f * unit), Offset(19f * unit, 21f * unit), 1.4f * unit)
            }
            else -> for ((index, x) in listOf(8f, 16f, 11f).withIndex()) {
                val y = (5f + index * 7f) * unit
                drawLine(color, Offset(3f * unit, y), Offset(21f * unit, y), 1.4f * unit)
                drawCircle(PanelColor, 3f * unit, Offset(x * unit, y))
                drawCircle(color, 2.6f * unit, Offset(x * unit, y), style = stroke)
            }
        }
    }
}

internal fun Adjustment.uiLabel(): String = when (this) {
    Adjustment.EXPOSURE -> "曝光"; Adjustment.TEMPERATURE -> "色温"; Adjustment.TINT -> "色调"; Adjustment.CONTRAST -> "反差"
    Adjustment.SATURATION -> "饱和度"; Adjustment.HIGHLIGHTS -> "高光"; Adjustment.SHADOWS -> "阴影"; Adjustment.GRAIN -> "颗粒"; Adjustment.HALATION -> "光晕"
}
internal fun FilmFrame.uiLabel(): String = when (this) {
    FilmFrame.NONE -> "无片框"; FilmFrame.STRIP_135 -> "135 齿孔"; FilmFrame.FILED_135 -> "135 片边"; FilmFrame.SQUARE_120 -> "120 方形"; FilmFrame.XPAN -> "XPan"
}
internal fun uiMessage(message: String): String = when {
    message.contains("cancelled", true) -> "本次导出已取消。可以使用保留的选择重新导出。"
    message.contains("Processing cancelled", true) -> "处理已取消。"
    else -> message
}
