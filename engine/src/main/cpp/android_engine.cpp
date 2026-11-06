// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
// Additive adapter around the pinned SpektraLab C ABI. Its original source,
// notices and authorship remain in third_party/spektralab.
#include "pipeline/engine.cpp"
#include "android_engine.hpp"
#include "output_codec.hpp"

bool android_float_render(spk_session* session, std::vector<float>& output,
                          uint32_t& width, uint32_t& height, std::string& error,
                          bool* negative_cache_hit, AndroidRenderMetrics* metrics,
                          const std::array<float,8>* frame_gate, std::atomic<bool>* cancelled) {
    if (!session) { error = "Missing render session"; return false; }
    std::lock_guard<std::mutex> guard(session->lock);
    auto* gpu = session->engine->gpu;
    const auto observation = metrics ? gpu->begin_memory_observation() : 0;
    struct Observe {
        gpu::Gpu* gpu; uint64_t id; AndroidRenderMetrics* metrics;
        ~Observe() {
            if (!id || !metrics) return;
            const auto measured=gpu->end_memory_observation(id);
            metrics->gpu_live_peak=std::max<uint64_t>(metrics->gpu_live_peak,measured.live_peak);
            metrics->gpu_resident_peak=std::max<uint64_t>(metrics->gpu_resident_peak,measured.resident_peak);
        }
    } observe{gpu,observation,metrics};
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
    if (frame_gate) {
        if (width!=rgb.w || height!=rgb.h || output.size()!=count*3) {
            error="Frame and photograph geometry differ";return false;
        }
        // Read the stable frame in bounded row bands. Spatial film processing
        // above remains whole-frame; this only changes host readback ownership.
        const size_t row_bytes=size_t(rgb.w)*3*sizeof(float);
        const uint32_t rows=std::max<uint32_t>(1,uint32_t((4*1024*1024)/row_bytes));
        std::vector<float> band(size_t(rgb.w)*3*std::min(rows,rgb.h));
        if(metrics)metrics->frame_scratch_bytes=std::max<uint64_t>(metrics->frame_scratch_bytes,band.capacity()*sizeof(float));
        const PhotoRegion region{true,*frame_gate};
        for(uint32_t y=0;y<rgb.h;y+=rows) {
            if(cancelled && cancelled->load()){error="Processing cancelled";return false;}
            const uint32_t height=std::min(rows,rgb.h-y);
            if(!gpu->read(rgb.buf.get(),size_t(y)*row_bytes,band.data(),size_t(height)*row_bytes,error))return false;
            for(uint32_t by=0;by<height;++by)for(uint32_t x=0;x<rgb.w;++x) {
                if(region.contains(x,y+by,rgb.w,rgb.h))continue;
                const size_t destination=(size_t(y+by)*rgb.w+x)*3,source=(size_t(by)*rgb.w+x)*3;
                std::copy_n(band.data()+source,3,output.data()+destination);
            }
        }
    } else {
        output.resize(size_t(count)*3);
        if (metrics) metrics->readback_bytes=std::max<uint64_t>(metrics->readback_bytes,output.capacity()*sizeof(float));
        if (!gpu->read(rgb.buf.get(),0,output.data(),output.size()*sizeof(float),error)) return false;
    }
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
