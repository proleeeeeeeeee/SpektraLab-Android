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

/** Neutral darkroom chrome. The photograph and material art carry the colour. */
internal val CanvasColor = Color(0xFF0B0D0E)
internal val PanelColor = Color(0xFF141718)
internal val ChromeColor = Color(0xFF181C1D)
internal val RaisedColor = Color(0xFF202425)
internal val PreviewWellColor = Color(0xFF080A0B)
internal val PaperWhite = Color(0xFFF4F3EF)
internal val MutedColor = Color(0xFF959B9B)
internal val AccentColor = Color(0xFFD6B477)
internal val HairlineColor = Color(0xFF303536)

@Composable
fun SpektraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = AccentColor, onPrimary = CanvasColor,
            primaryContainer = Color(0xFF3D3322), onPrimaryContainer = PaperWhite,
            secondary = AccentColor, onSecondary = CanvasColor,
            secondaryContainer = RaisedColor, onSecondaryContainer = PaperWhite,
            background = CanvasColor, onBackground = PaperWhite,
            surface = PanelColor, onSurface = PaperWhite,
            surfaceContainerLowest = CanvasColor, surfaceContainerLow = CanvasColor,
            surfaceContainer = PanelColor, surfaceContainerHigh = ChromeColor,
            surfaceContainerHighest = RaisedColor,
            surfaceVariant = ChromeColor, onSurfaceVariant = MutedColor,
            outline = HairlineColor, error = Color(0xFFFFB4AB),
        ),
        typography = Typography(
            headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Light, fontSize = 30.sp, lineHeight = 38.sp),
            titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 21.sp, lineHeight = 28.sp),
            bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 23.sp),
            bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp),
            labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 14.sp),
        ),
        content = content,
    )
}
