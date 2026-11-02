// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
#pragma once
#include <atomic>
#include <cstdint>
#include <string>
#include <vector>

struct SourceInfo { uint32_t width=0,height=0; bool raw=false; };
struct SourcePixels {
    SourceInfo original;
    uint32_t width=0,height=0;
    bool linear_prophoto=false;
    std::vector<float> rgb;
};
SourceInfo inspect_source(const std::string& path);
SourcePixels decode_source(const std::string& path, int long_edge, uint64_t budget,
                           std::atomic<bool>* cancelled = nullptr, bool display_srgb = false);
