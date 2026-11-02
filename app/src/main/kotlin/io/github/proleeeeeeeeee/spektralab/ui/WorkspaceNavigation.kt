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
internal enum class EditorPane(val title: String) { FILM("Film"), PAPER("Print"), ADJUST("Adjust"), COMPOSE("Compose") }

@Composable internal fun WorkspaceNavigation(page: WorkspacePage, enabled: Boolean, onSelect: (WorkspacePage) -> Unit) {
    NavigationBar(containerColor = PanelColor, windowInsets = WindowInsets(0, 0, 0, 0), modifier = Modifier.testTag("workspace-navigation")) {
        listOf(Triple(WorkspacePage.PHOTOS, "Photos", "photos"), Triple(WorkspacePage.MATERIALS, "Materials", "materials"), Triple(WorkspacePage.NEGATIVE, "Tools", "tools")).forEach { (target, label, tag) ->
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
    Adjustment.EXPOSURE -> "Exposure"; Adjustment.TEMPERATURE -> "Temperature"; Adjustment.TINT -> "Tint"; Adjustment.CONTRAST -> "Contrast"
    Adjustment.SATURATION -> "Saturation"; Adjustment.HIGHLIGHTS -> "Highlights"; Adjustment.SHADOWS -> "Shadows"; Adjustment.GRAIN -> "Grain"; Adjustment.HALATION -> "Halation"
}
internal fun FilmFrame.uiLabel(): String = when (this) {
    FilmFrame.NONE -> "No frame"; FilmFrame.STRIP_135 -> "135 sprockets"; FilmFrame.FILED_135 -> "135 film edge"; FilmFrame.SQUARE_120 -> "120 square"; FilmFrame.XPAN -> "XPan"
}
internal fun uiMessage(message: String): String = when {
    message.contains("cancelled", true) -> "Export cancelled. You can export again with the saved selection."
    message.contains("Processing cancelled", true) -> "Processing cancelled."
    else -> message
}
