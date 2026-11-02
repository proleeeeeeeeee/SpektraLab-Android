// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
#pragma once
#include <spektrafilm/spk_engine.h>
#include <cstdint>
#include <string>
#include <vector>

bool android_float_render(spk_session* session, std::vector<float>& rgb,
                          uint32_t& width, uint32_t& height, std::string& error,
                          bool* negative_cache_hit = nullptr);
