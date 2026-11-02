// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab.ui

import android.graphics.RenderEffect
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import io.github.proleeeeeeeeee.spektralab.core.Adjustments
import io.github.proleeeeeeeeee.spektralab.editor.ScanPreviewShader

@Composable
internal fun Modifier.fastScanPreview(adjustments: Adjustments?): Modifier {
    if (Build.VERSION.SDK_INT < 33 || !ScanPreviewShader.supported) return this
    val shader = remember { ScanPreviewShader.create() }
    return graphicsLayer {
        if (adjustments == null) renderEffect = null
        else {
            shader.setFloatUniform("temperature",adjustments.temperature.toFloat())
            shader.setFloatUniform("tint",adjustments.tint.toFloat())
            shader.setFloatUniform("contrast",adjustments.contrast.toFloat())
            shader.setFloatUniform("saturation",adjustments.saturation.toFloat())
            shader.setFloatUniform("highlights",adjustments.highlights.toFloat())
            shader.setFloatUniform("shadows",adjustments.shadows.toFloat())
            // RenderEffect captures the uniform values. Retain the compiled
            // shader, but create its effect after every uniform update.
            renderEffect = RenderEffect.createRuntimeShaderEffect(shader,"content").asComposeRenderEffect()
        }
    }
}
