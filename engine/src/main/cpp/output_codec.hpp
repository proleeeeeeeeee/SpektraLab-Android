// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
#pragma once
#include "scan_tone.hpp"
#include <array>
#include <atomic>
#include <cstdint>
#include <string>
#include <vector>

struct PhotoRegion {
    bool active = false;
    std::array<float, 8> gate{};
    bool contains(uint32_t x, uint32_t y, uint32_t width, uint32_t height) const;
};

void encode_image(const std::string& path,uint32_t w,uint32_t h,
                  const std::vector<uint8_t>& rgba,bool png,int quality,std::atomic<bool>& cancelled);
std::vector<uint8_t> scan_rgba(const std::vector<float>& rgb,const ScanTone& tone,std::atomic<bool>& cancelled,
                               uint32_t width = 0,uint32_t height = 0,const PhotoRegion& photo = {});
void write_tiff16(const std::string& path,const std::string& icc,uint32_t w,uint32_t h,
                  const std::vector<float>& rgb,const ScanTone& tone,std::atomic<bool>& cancelled,
                  const PhotoRegion& photo = {});
// RGB gain map values encode natural-log HDR/SDR ratios, including the same
// scan adjustments on both renderings. These limits match Android Gainmap.
struct GainMapPixels { uint32_t width,height; float minimum,maximum; std::vector<uint8_t> rgba; };
GainMapPixels make_gain_map(uint32_t w,uint32_t h,const std::vector<uint8_t>& sdr,
                            const std::vector<float>& hdr,const ScanTone& tone,std::atomic<bool>& cancelled,
                            const PhotoRegion& photo = {});
