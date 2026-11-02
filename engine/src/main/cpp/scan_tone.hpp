// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
#pragma once
#include <algorithm>
#include <array>
#include <cmath>
#include <stdexcept>

// Scan-tone subset adapted from SpektraLab's desktop/src/shared/adjustments.ts
// (itself a port of Adjustments.swift). Original project attribution: NOTICE.md.
// Android grades encoded sRGB at the same boundary for preview and export.
struct ScanTone {
    std::array<float, 6> values{};
    bool neutral = true;
    explicit ScanTone(const std::array<float, 6>& input) : values(input) {
        for (size_t i = 0; i < values.size(); ++i) {
            const float limit = i == 2 ? 75.f : 100.f;
            if (!std::isfinite(values[i]) || std::abs(values[i]) > limit)
                throw std::invalid_argument("Invalid scan adjustment");
            neutral = neutral && values[i] == 0.f;
        }
    }
    std::array<float, 3> apply(float r, float g, float b) const {
        r *= 1.f + .0018f * values[0];
        g *= 1.f - .0012f * values[1];
        b *= 1.f - .0018f * values[0];
        constexpr float mid = .4614f;
        const float contrast = 1.f + .012f * values[2];
        r = (r - mid) * contrast + mid;
        g = (g - mid) * contrast + mid;
        b = (b - mid) * contrast + mid;
        const auto luma = [](float x, float y, float z) { return .2126f*x + .7152f*y + .0722f*z; };
        const float l = luma(r,g,b);
        const float position = l <= mid ? .5f*l/mid : .5f + .5f*(l-mid)/(1.f-mid);
        const float shadow = values[5] * .0025f * (1.f-position) * (1.f-position);
        r += shadow * (1.f-r); g += shadow * (1.f-g); b += shadow * (1.f-b);
        const float highlight = values[4] * .0025f * position * position;
        if (highlight > 0) {
            r += highlight * (1.f-r); g += highlight * (1.f-g); b += highlight * (1.f-b);
        } else {
            r += highlight*r; g += highlight*g; b += highlight*b;
        }
        const float grey = luma(r,g,b), saturation = 1.f + .01f * values[3];
        return {std::clamp(grey+(r-grey)*saturation,0.f,1.f),
                std::clamp(grey+(g-grey)*saturation,0.f,1.f),
                std::clamp(grey+(b-grey)*saturation,0.f,1.f)};
    }
};
