// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
// Additive adapter around the pinned SpektraLab C ABI. Its original source,
// notices and authorship remain in third_party/spektralab.
#include "pipeline/engine.cpp"
#include "android_engine.hpp"

bool android_float_render(spk_session* session, std::vector<float>& output,
                          uint32_t& width, uint32_t& height, std::string& error,
                          bool* negative_cache_hit) {
    if (!session) { error = "Missing render session"; return false; }
    std::lock_guard<std::mutex> guard(session->lock);
    auto* gpu = session->engine->gpu;
    session->progress = Progress{};
    session->progress.progress_id = "android-float";
    gpu->begin_frame();
    struct EndFrame { gpu::Gpu* gpu; ~EndFrame() { gpu->end_frame(); } } end{gpu};
    const Tier* tier = find_tier("full");
    const auto existing = session->tiers.find(tier->name);
    if (negative_cache_hit) *negative_cache_hit = existing != session->tiers.end() && existing->second.has_negative;
    Image source,negative,rgb;
    bool ok = tier_image(session,*tier,source,error);
    if (ok) session->pipeline->set_source_shape(source.w,source.h,session->source.w,session->source.h);
    if (ok) ok = negative_for(session,*tier,&session->progress,negative,error);
    if (ok) {
        const auto& state = session->tiers[tier->name];
        ok = session->pipeline->set_overscan_frame(state.overscan_frame_w,state.overscan_frame_h,error);
    }
    if (ok) ok = run_print_for(session,negative,rgb,&session->progress,error);
    if (!ok) return false;
    const uint64_t count = uint64_t(rgb.w)*rgb.h;
    if (!count || count > 400'000'000 || rgb.c != 3) { error = "Invalid float output dimensions"; return false; }
    output.resize(size_t(count)*3);
    if (!gpu->read(rgb.buf.get(),0,output.data(),output.size()*sizeof(float),error)) return false;
    width = rgb.w; height = rgb.h;
    session->progress.done = true;
    return true;
}

bool android_overscan_gate(spk_session* session, std::array<float, 8>& gate,
                           std::string& error) {
    gate = {};
    if (!session) { error = "Missing render session"; return false; }
    std::lock_guard<std::mutex> guard(session->lock);
    try {
        spk::Json layout;
        if (!spk::Json::parse(session->pipeline->overscan_geometry_json(), layout, error))
            return false;
        if (!layout.is_object() || !layout.has("valid") || !layout.at("valid").as_bool(false)) {
            error = "The film frame geometry is unavailable";
            return false;
        }
        const auto& gates = layout.at("gates");
        if (!gates.is_array() || gates.items().empty()) {
            error = "The film frame has no image gate";
            return false;
        }
        const auto& points = gates.items().front();
        if (!points.is_array() || points.items().size() != gate.size()) {
            error = "The film frame gate is invalid";
            return false;
        }
        for (size_t i = 0; i < gate.size(); ++i) {
            const auto& value = points.items()[i];
            if (!value.is_number() || !std::isfinite(value.as_double())) {
                error = "The film frame gate contains an invalid coordinate";
                return false;
            }
            gate[i] = float(value.as_double());
        }
        return true;
    } catch (const std::exception& failure) {
        error = std::string("Film frame geometry failed: ") + failure.what();
        return false;
    }
}
