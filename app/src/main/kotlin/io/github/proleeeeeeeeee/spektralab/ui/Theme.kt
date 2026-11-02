// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

internal val CanvasColor = Color(0xFF151718)
internal val PanelColor = Color(0xFF222526)
internal val PaperWhite = Color(0xFFF1EFEA)
internal val MutedColor = Color(0xFFA8ADAD)
internal val AccentColor = Color(0xFFCAB58D)

@Composable
fun SpektraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = AccentColor, onPrimary = CanvasColor,
            primaryContainer = Color(0xFF403A30), onPrimaryContainer = PaperWhite,
            secondary = AccentColor, onSecondary = CanvasColor,
            secondaryContainer = Color(0xFF3A3E3D), onSecondaryContainer = PaperWhite,
            background = CanvasColor, onBackground = PaperWhite,
            surface = PanelColor, onSurface = PaperWhite,
            surfaceContainerLowest = CanvasColor, surfaceContainerLow = CanvasColor,
            surfaceContainer = PanelColor, surfaceContainerHigh = Color(0xFF303435),
            surfaceContainerHighest = Color(0xFF3A3E3D),
            surfaceVariant = Color(0xFF303435), onSurfaceVariant = MutedColor,
            outline = Color(0xFF424748), error = Color(0xFFFFB4AB),
        ),
        typography = Typography(
            headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 28.sp, lineHeight = 36.sp),
            titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 21.sp, lineHeight = 28.sp),
            bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 23.sp),
            bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp),
            labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 14.sp),
        ),
        content = content,
    )
}
