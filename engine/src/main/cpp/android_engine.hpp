// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
#pragma once
#include <spektrafilm/spk_engine.h>
#include <array>
#include <atomic>
#include <cstdint>
#include <string>
#include <vector>

struct AndroidRenderMetrics {
    uint64_t readback_bytes=0, frame_scratch_bytes=0;
    uint64_t gpu_live_peak=0, gpu_resident_peak=0;
};

bool android_float_render(spk_session* session, std::vector<float>& rgb,
                          uint32_t& width, uint32_t& height, std::string& error,
                          bool* negative_cache_hit = nullptr, AndroidRenderMetrics* metrics = nullptr,
                          const std::array<float,8>* frame_gate = nullptr, std::atomic<bool>* cancelled = nullptr);
bool android_overscan_gate(spk_session* session, std::array<float, 8>& gate,
                           std::string& error);
