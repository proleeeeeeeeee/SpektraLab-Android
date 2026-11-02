// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab.editor

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi

/** The native float scan remains authoritative when a gesture finishes or exports. */
internal object ScanPreviewShader {
    val supported: Boolean by lazy {
        Build.VERSION.SDK_INT >= 33 && runCatching { create(); true }.getOrDefault(false)
    }
    @RequiresApi(33)
    fun create() = RuntimeShader(SOURCE)

    // Same encoded-sRGB equations and order as engine/scan_tone.hpp. During a
    // gesture the input is an 8-bit neutral preview, so clipped values and final
    // quantization are corrected by the native float render on release.
    private const val SOURCE = """
        uniform shader content;
        uniform float temperature;
        uniform float tint;
        uniform float contrast;
        uniform float saturation;
        uniform float highlights;
        uniform float shadows;
        half4 main(float2 position) {
            half4 pixel = content.eval(position);
            if (pixel.a <= 0.0) return pixel;
            float3 c = float3(pixel.rgb) / float(pixel.a);
            c *= float3(1.0 + .0018 * temperature, 1.0 - .0012 * tint, 1.0 - .0018 * temperature);
            float mid = .4614;
            c = (c - mid) * (1.0 + .012 * contrast) + mid;
            float l = dot(c, float3(.2126,.7152,.0722));
            float p = clamp(l <= mid ? .5*l/mid : .5+.5*(l-mid)/(1.0-mid),0.0,1.0);
            c += shadows * .0025 * (1.0-p) * (1.0-p) * (1.0-c);
            float h = highlights * .0025 * p * p;
            c += h > 0.0 ? h*(1.0-c) : h*c;
            float grey = dot(c,float3(.2126,.7152,.0722));
            c = clamp(grey+(c-grey)*(1.0+.01*saturation),0.0,1.0);
            return half4(half3(c * float(pixel.a)),pixel.a);
        }
    """
}
