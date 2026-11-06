// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
#include <jni.h>
#include <android/bitmap.h>
#include <spektrafilm/spk_engine.h>
#include <atomic>
#include <cstdint>
#include <cstring>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <string>
#include <unordered_map>
#include <vector>
#include "scan_tone.hpp"
#include "source_codec.hpp"
#include "output_codec.hpp"
#include "android_engine.hpp"
#include "core/json.hpp"
#include "parameter_delta.hpp"
#include <algorithm>

namespace {
constexpr uint64_t kMaxInputPixels = 7'000'000;
constexpr uint64_t kMaxOutputPixels = 16'000'000;

struct EngineContext {
    spk_engine* engine = nullptr;
    spk_session* session = nullptr;
    std::string source_key;
    std::string rendered_parameters;
    std::string applied_parameters;
    uint64_t source_opens=0, negative_builds=0, print_renders=0, tone_scans=0;
    std::string resources;
    std::vector<float> float_scan;
    uint32_t float_width=0,float_height=0;
    PhotoRegion photo_region;
    spk_result cached_result{};
    std::atomic<bool> cancelled{false};
    std::mutex render_mutex;
    std::mutex session_mutex;
    ~EngineContext() {
        spk_result_free(&cached_result);
        if (session) spk_session_release(session);
        if (engine) spk_engine_destroy(engine);
    }
};

// Java receives opaque IDs rather than pointers. A stale or fabricated handle
// cannot become a native dereference; in-flight calls retain their own ownership.
std::mutex registry_mutex;
std::unordered_map<jlong, std::shared_ptr<EngineContext>> registry;
std::atomic<jlong> next_handle{1};

void throw_error(JNIEnv* env, const std::string& message) {
    if (!env->ExceptionCheck()) {
        jclass type = env->FindClass("java/lang/IllegalStateException");
        if (type) env->ThrowNew(type, message.c_str());
    }
}
std::string utf8(JNIEnv* env, jstring value, size_t limit) {
    if (!value) throw std::invalid_argument("Missing native argument");
    if (env->GetStringUTFLength(value) > static_cast<jsize>(limit))
        throw std::invalid_argument("Native argument is too long");
    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (!chars) throw std::runtime_error("Could not read native argument");
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}
std::shared_ptr<EngineContext> context_for(jlong handle) {
    std::lock_guard<std::mutex> lock(registry_mutex);
    auto found = registry.find(handle);
    if (found == registry.end()) throw std::invalid_argument("Engine handle is closed");
    return found->second;
}
std::string engine_error(spk_engine* engine, const char* operation) {
    const char* detail = spk_last_error(engine);
    return std::string(operation) + ": " + (detail && *detail ? detail : "native engine error");
}

void update_parameters(EngineContext& c,const std::string& params) {
    const auto delta=parameter_delta(c.applied_parameters,params);
    if (delta!="{}" && spk_set_params(c.session,delta.c_str(),nullptr)!=SPK_OK) {
        // A failed rebuild may have partially changed native state. Force a
        // fresh source session before the next request instead of reusing it.
        c.source_key.clear();c.applied_parameters.clear();c.rendered_parameters.clear();
        throw std::runtime_error(engine_error(c.engine,"Updating render settings failed"));
    }
    c.applied_parameters=params;
}

class BitmapPixels {
public:
    BitmapPixels(JNIEnv* env, jobject bitmap) : env_(env), bitmap_(bitmap) {
        if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS ||
            info.format != ANDROID_BITMAP_FORMAT_RGBA_8888 ||
            info.width == 0 || info.height == 0 ||
            info.width > 8192 || info.height > 8192 ||
            info.stride < uint64_t(info.width) * 4) {
            throw std::invalid_argument("Expected a bounded software RGBA bitmap");
        }
        if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS)
            throw std::runtime_error("Could not lock bitmap pixels");
    }
    ~BitmapPixels() { AndroidBitmap_unlockPixels(env_, bitmap_); }
    AndroidBitmapInfo info{};
    void* pixels = nullptr;
    BitmapPixels(const BitmapPixels&) = delete;
    BitmapPixels& operator=(const BitmapPixels&) = delete;
private:
    JNIEnv* env_;
    jobject bitmap_;
};

jlong create(JNIEnv* env, jobject, jstring resources) {
    try {
        auto context = std::make_shared<EngineContext>();
        const auto directory = utf8(env, resources, 4096);
        context->resources = directory;
        context->engine = spk_engine_create(directory.c_str(), nullptr);
        if (!context->engine) throw std::runtime_error(engine_error(nullptr, "Vulkan initialization failed"));
        const jlong handle = next_handle.fetch_add(1);
        std::lock_guard<std::mutex> lock(registry_mutex);
        registry.emplace(handle, context);
        return handle;
    } catch (const std::exception& error) {
        throw_error(env, error.what());
        return 0;
    } catch (...) {
        throw_error(env, "Unexpected engine initialization failure");
        return 0;
    }
}

jobject render(JNIEnv* env, jobject, jlong handle, jobject input, jstring source, jstring parameters, jfloatArray scan_tone) {
    try {
        auto context = context_for(handle);
        std::lock_guard<std::mutex> render_lock(context->render_mutex);
        context->cancelled.store(false);
        if (!scan_tone || env->GetArrayLength(scan_tone) != 6)
            throw std::invalid_argument("Expected six scan adjustment values");
        std::array<float, 6> tone_values{};
        env->GetFloatArrayRegion(scan_tone, 0, 6, tone_values.data());
        if (env->ExceptionCheck()) return nullptr;
        const ScanTone tone(tone_values);
        const auto source_key = utf8(env, source, 256);
        const auto params = utf8(env, parameters, 65536);
        if (source_key.empty()) throw std::invalid_argument("Missing source identity");
        if (context->source_key != source_key || !context->session || parameters_omit_fields(context->applied_parameters,params)) {
            spk_result_free(&context->cached_result);
            std::vector<float>().swap(context->float_scan);
            context->rendered_parameters.clear();
            std::vector<float> rgb;
            AndroidBitmapInfo info{};
            {
                BitmapPixels bitmap(env, input);
                info = bitmap.info;
                const uint64_t pixels = uint64_t(info.width) * info.height;
                if (pixels > kMaxInputPixels) throw std::invalid_argument("Input exceeds the render memory budget");
                rgb.resize(static_cast<size_t>(pixels) * 3);
                const auto* bytes = static_cast<const uint8_t*>(bitmap.pixels);
                const auto alpha_type = info.flags & ANDROID_BITMAP_FLAGS_ALPHA_MASK;
                for (uint32_t y = 0; y < info.height; ++y) {
                    for (uint32_t x = 0; x < info.width; ++x) {
                        const auto* pixel = bytes + size_t(y) * info.stride + size_t(x) * 4;
                        const float alpha = pixel[3] / 255.f;
                        for (unsigned channel = 0; channel < 3; ++channel) {
                            // Physical print output is opaque. Composite transparent
                            // input on white while respecting Android premultiplication.
                            const float value = pixel[channel] / 255.f;
                            rgb[(size_t(y) * info.width + x) * 3 + channel] =
                                (alpha_type == ANDROID_BITMAP_FLAGS_ALPHA_PREMUL ? value : value * alpha) + 1.f - alpha;
                        }
                    }
                }
            }
            const spk_image image{rgb.data(), info.width, info.height, 3};
            std::lock_guard<std::mutex> session_lock(context->session_mutex);
            if (context->session) { spk_session_release(context->session); context->session = nullptr; }
            context->source_key.clear();
            context->session = spk_open(context->engine, &image, params.c_str(), nullptr);
            if (!context->session) throw std::runtime_error(engine_error(context->engine, "Opening photograph failed"));
            context->source_key = source_key;
            context->applied_parameters=params;
        } else if (context->rendered_parameters != params) {
            update_parameters(*context,params);
        }
        // Retain the ungraded 16-bit scan. Moving a scan-tone slider does not
        // re-develop the negative or grade an already graded 8-bit bitmap.
        if (context->rendered_parameters != params || !context->cached_result.rgba16) {
            spk_result_free(&context->cached_result);
            context->rendered_parameters.clear();
            if (spk_render(context->session, "full", &context->cached_result) != SPK_OK)
                throw std::runtime_error(engine_error(context->engine, "Local film processing failed"));
            context->rendered_parameters = params;
        }
        const auto& result = context->cached_result;
        const uint64_t count = uint64_t(result.width) * result.height;
        if (!result.rgba16 || count == 0 || count > kMaxOutputPixels ||
            result.row_stride_px < result.width) throw std::runtime_error("Invalid engine image dimensions");
        jclass bitmap_class = env->FindClass("android/graphics/Bitmap");
        jclass config_class = env->FindClass("android/graphics/Bitmap$Config");
        if (!bitmap_class || !config_class) throw std::runtime_error("Bitmap classes are unavailable");
        auto config_field = env->GetStaticFieldID(config_class, "ARGB_8888", "Landroid/graphics/Bitmap$Config;");
        auto make = env->GetStaticMethodID(bitmap_class, "createBitmap", "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");
        if (!config_field || !make) throw std::runtime_error("Bitmap allocation method is unavailable");
        jobject config = env->GetStaticObjectField(config_class, config_field);
        jobject output = env->CallStaticObjectMethod(bitmap_class, make, jint(result.width), jint(result.height), config);
        if (!output || env->ExceptionCheck()) throw std::runtime_error("Could not allocate the processed photograph");
        {
            BitmapPixels bitmap(env, output);
            auto* bytes = static_cast<uint8_t*>(bitmap.pixels);
            for (uint32_t y = 0; y < result.height; ++y) {
                if (context->cancelled.load()) throw std::runtime_error("Processing cancelled");
                const auto* row = result.rgba16 + size_t(y) * result.row_stride_px * 4;
                for (uint32_t x = 0; x < result.width; ++x) {
                    auto* pixel = bytes + size_t(y) * bitmap.info.stride + size_t(x) * 4;
                    if (tone.neutral) {
                        for (unsigned channel = 0; channel < 3; ++channel)
                            pixel[channel] = uint8_t((uint32_t(row[x * 4 + channel]) + 128) / 257);
                    } else {
                        const auto graded = tone.apply(row[x*4]/65535.f, row[x*4+1]/65535.f, row[x*4+2]/65535.f);
                        for (unsigned channel = 0; channel < 3; ++channel)
                            pixel[channel] = uint8_t(std::lround(graded[channel] * 255.f));
                    }
                    pixel[3] = 255;
                }
            }
        }
        return output;
    } catch (const std::exception& error) {
        throw_error(env, error.what());
        return nullptr;
    } catch (...) {
        throw_error(env, "Unexpected native rendering failure");
        return nullptr;
    }
}

// The app passes only its private imported files and generated export paths.
// This boundary never receives gallery URIs or filenames from document providers.
ScanTone read_tone(JNIEnv* env,jfloatArray values) {
    if (!values || env->GetArrayLength(values)!=6) throw std::invalid_argument("Expected six scan adjustments");
    std::array<float,6> tone{};env->GetFloatArrayRegion(values,0,6,tone.data());
    if (env->ExceptionCheck()) throw std::runtime_error("Could not read scan adjustments");
    return ScanTone(tone);
}
void release_source(EngineContext& c) {
    std::lock_guard<std::mutex> lock(c.session_mutex);
    if (c.session) { spk_session_release(c.session);c.session=nullptr; }
    spk_result_free(&c.cached_result);c.source_key.clear();c.rendered_parameters.clear();c.applied_parameters.clear();
    std::vector<float>().swap(c.float_scan);
    c.photo_region={};
}
std::string source_parameters(const std::string& params,bool raw,bool hdr) {
    spk::Json json;std::string error;
    if (!spk::Json::parse(params,json,error) || !json.is_object()) throw std::invalid_argument("Invalid render settings");
    json.set("input_color_space",spk::Json(std::string(raw?"ProPhoto RGB":"sRGB")));
    json.set("input_cctf_decoding",spk::Json(!raw));
    json.set("extended_dynamic_range",spk::Json(hdr));
    return json.dump();
}
void open_source(EngineContext& c,const std::string& path,const std::string& key,
                 const std::string& params,int edge,uint64_t budget) {
    // Leaving a fitted B&W paper pack removes its explicit illuminant/filter
    // overrides. The ABI has no delete operation, so restore profile defaults
    // with a fresh session rather than carrying those settings into color film.
    if (c.session && c.source_key==key && !parameters_omit_fields(c.applied_parameters,params)) return;
    release_source(c);
    const auto info=inspect_source(path);
    const double scale=edge==0?1.0:std::min(1.0,double(edge)/std::max(info.width,info.height));
    const uint64_t pixels=uint64_t(std::ceil(info.width*scale))*uint64_t(std::ceil(info.height*scale));
    // Source, cached negative, pipeline arenas, staging and float readback all
    // coexist. Admission rejects the requested job; it never reduces its size.
    if (pixels*160+128*1024*1024>budget)
        throw std::runtime_error("This resolution exceeds the available render memory. Choose 4096 or 2560 px; Original is never silently resized.");
    auto decoded=decode_source(path,edge,budget,&c.cancelled);
    const auto actual=source_parameters(params,decoded.linear_prophoto,false);
    const spk_image image{decoded.rgb.data(),decoded.width,decoded.height,3};
    std::lock_guard<std::mutex> lock(c.session_mutex);
    c.session=spk_open(c.engine,&image,actual.c_str(),nullptr);
    if (!c.session) throw std::runtime_error(engine_error(c.engine,"Opening source pixels failed"));
    c.source_key=key;c.applied_parameters=actual;c.source_opens++;
}
void float_render(EngineContext& c,const std::string& params,std::vector<float>& pixels,uint32_t& w,uint32_t& h) {
    update_parameters(c,params);
    std::string error;
    bool negative_hit=false;
    if (!android_float_render(c.session,pixels,w,h,error,&negative_hit)) throw std::runtime_error("High-precision render failed: "+error);
    if (c.cancelled.load()) throw std::runtime_error("Processing cancelled");
    c.print_renders++;if (!negative_hit) c.negative_builds++;
}
PhotoRegion finish_frame_after_adjustments(EngineContext& c,const std::string& params,
                                           std::vector<float>& photo,uint32_t w,uint32_t h) {
    spk::Json current;std::string error;
    if (!spk::Json::parse(params,current,error) || !current.is_object())
        throw std::invalid_argument("Invalid frame render settings");
    if (!current.has("overscan_active") || !current.at("overscan_active").as_bool(false)) return {};
    PhotoRegion region;region.active=true;
    if (!android_overscan_gate(c.session,region.gate,error))
        throw std::runtime_error("Film frame layout failed: "+error);

    // The photograph keeps the user's physical exposure, grain and halation.
    // Render the surrounding film at stable defaults and replace only pixels
    // outside the gate. Scan-tone controls are also limited to the gate later.
    auto frame=current;
    frame.set("auto_exposure",spk::Json(true));
    frame.set("exposure_compensation_ev",spk::Json(0.0));
    frame.set("grain_active",spk::Json(true));
    frame.set("grain_amount",spk::Json(1.0));
    frame.set("halation_active",spk::Json(true));
    frame.set("halation_amount",spk::Json(1.0));
    const auto frame_params=frame.dump();
    if (frame_params!=params) {
        std::vector<float> stable;uint32_t fw=0,fh=0;
        float_render(c,frame_params,stable,fw,fh);
        if (fw!=w||fh!=h||stable.size()!=photo.size())
            throw std::runtime_error("Frame and photograph geometry differ");
        for (uint32_t y=0;y<h;++y) for(uint32_t x=0;x<w;++x) {
            if (region.contains(x,y,w,h)) continue;
            const size_t i=(size_t(y)*w+x)*3;
            photo[i]=stable[i];photo[i+1]=stable[i+1];photo[i+2]=stable[i+2];
        }
        update_parameters(c,params);
    }
    return region;
}
jobject bitmap_rgba(JNIEnv* env,uint32_t w,uint32_t h,const std::vector<uint8_t>& pixels) {
    jclass bitmap=env->FindClass("android/graphics/Bitmap");
    jclass config=env->FindClass("android/graphics/Bitmap$Config");
    const auto field=env->GetStaticFieldID(config,"ARGB_8888","Landroid/graphics/Bitmap$Config;");
    const auto method=env->GetStaticMethodID(bitmap,"createBitmap","(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");
    jobject output=env->CallStaticObjectMethod(bitmap,method,jint(w),jint(h),env->GetStaticObjectField(config,field));
    if (!output || env->ExceptionCheck()) throw std::runtime_error("Could not allocate the photograph preview");
    BitmapPixels target(env,output);
    for (uint32_t y=0;y<h;++y) std::memcpy(static_cast<uint8_t*>(target.pixels)+size_t(y)*target.info.stride,
                                         pixels.data()+size_t(y)*w*4,size_t(w)*4);
    return output;
}
jintArray source_info(JNIEnv* env,jobject,jstring file) {
    try {
        const auto info=inspect_source(utf8(env,file,4096));
        const jint values[]={jint(info.width),jint(info.height),info.raw?1:0};
        auto result=env->NewIntArray(3);if (result) env->SetIntArrayRegion(result,0,3,values);return result;
    } catch (const std::exception& e) { throw_error(env,e.what());return nullptr; }
}
jobject source_preview(JNIEnv* env,jobject,jstring file,jint edge,jlong budget) {
    try {
        if (edge<64 || edge>2560 || budget<0) throw std::invalid_argument("Invalid preview budget");
        auto source=decode_source(utf8(env,file,4096),edge,uint64_t(budget),nullptr,true);
        std::atomic<bool> cancelled{false};const ScanTone tone({});
        return bitmap_rgba(env,source.width,source.height,scan_rgba(source.rgb,tone,cancelled));
    } catch (const std::exception& e) { throw_error(env,e.what());return nullptr; }
}
jobject render_file(JNIEnv* env,jobject,jlong handle,jstring file,jstring source,jstring parameters,
                    jfloatArray scan_tone,jint edge,jlong budget,jboolean raw) {
    try {
        if (edge<64 || edge>2560 || budget<0) throw std::invalid_argument("Invalid preview size");
        auto c=context_for(handle);std::lock_guard<std::mutex> lock(c->render_mutex);c->cancelled.store(false);
        const auto path=utf8(env,file,4096),key="file:"+utf8(env,source,256)+":"+std::to_string(edge);
        const auto params=source_parameters(utf8(env,parameters,65536),raw,false);
        const auto tone=read_tone(env,scan_tone);
        open_source(*c,path,key,params,edge,uint64_t(budget));
        if (c->float_scan.empty() || c->rendered_parameters!=params) {
            // Cancellation can occur after readback. Never let partially replaced
            // pixels keep the previous recipe's cache identity.
            c->rendered_parameters.clear();
            float_render(*c,params,c->float_scan,c->float_width,c->float_height);
            c->photo_region=finish_frame_after_adjustments(*c,params,c->float_scan,c->float_width,c->float_height);
            c->rendered_parameters=params;
        }
        c->tone_scans++;
        return bitmap_rgba(env,c->float_width,c->float_height,
            scan_rgba(c->float_scan,tone,c->cancelled,c->float_width,c->float_height,c->photo_region));
    } catch (const std::exception& e) { throw_error(env,e.what());return nullptr; }
}
jfloatArray export_file(JNIEnv* env,jobject,jlong handle,jstring file,jstring parameters,jfloatArray scan_tone,
                        jint edge,jlong budget,jboolean raw,jstring destination,jstring gain_path,jint format,jint quality) {
    std::shared_ptr<EngineContext> c;
    try {
        if (edge<0 || edge>32768 || budget<0 || format<0 || format>3 || quality<60 || quality>100)
            throw std::invalid_argument("Invalid export request");
        c=context_for(handle);std::lock_guard<std::mutex> lock(c->render_mutex);c->cancelled.store(false);
        const auto path=utf8(env,file,4096),output=utf8(env,destination,4096);
        const auto params=source_parameters(utf8(env,parameters,65536),raw,false);
        const auto tone=read_tone(env,scan_tone);
        // Exports release the preview and full session on completion. Large
        // batch jobs therefore do not retain the previous photograph's planes.
        release_source(*c);
        struct Cleanup { EngineContext& c;~Cleanup(){release_source(c);} } cleanup{*c};
        open_source(*c,path,"export",params,edge,uint64_t(budget));
        std::vector<float> sdr;uint32_t w=0,h=0;float_render(*c,params,sdr,w,h);
        const auto photo=finish_frame_after_adjustments(*c,params,sdr,w,h);
        float low=1.f,high=1.f;
        if (format==2) write_tiff16(output,c->resources+"/io/sRGB.icc",w,h,sdr,tone,c->cancelled,photo);
        else {
            auto rgba=scan_rgba(sdr,tone,c->cancelled,w,h,photo);
            std::vector<float>().swap(sdr);
            if (format==3) {
                std::vector<float> hdr;uint32_t hw=0,hh=0;
                const auto hdr_params=source_parameters(params,raw,true);
                float_render(*c,hdr_params,hdr,hw,hh);
                const auto hdr_photo=finish_frame_after_adjustments(*c,hdr_params,hdr,hw,hh);
                if (hw!=w || hh!=h) throw std::runtime_error("HDR and SDR render geometry differs");
                auto gain=make_gain_map(w,h,rgba,hdr,tone,c->cancelled,hdr_photo);low=gain.minimum;high=gain.maximum;
                encode_image(utf8(env,gain_path,4096),gain.width,gain.height,gain.rgba,true,100,c->cancelled);
            }
            encode_image(output,w,h,rgba,format==1 || format==3,quality,c->cancelled);
        }
        const jfloat values[]={jfloat(w),jfloat(h),low,high};
        auto result=env->NewFloatArray(4);if (result) env->SetFloatArrayRegion(result,0,4,values);return result;
    } catch (const std::exception& e) { throw_error(env,e.what());return nullptr; }
}

jlongArray statistics(JNIEnv* env,jobject,jlong handle) {
    try {
        auto c=context_for(handle);std::lock_guard<std::mutex> lock(c->render_mutex);
        const jlong values[]={jlong(c->source_opens),jlong(c->negative_builds),jlong(c->print_renders),jlong(c->tone_scans)};
        auto result=env->NewLongArray(4);if (result) env->SetLongArrayRegion(result,0,4,values);return result;
    } catch (const std::exception& error) { throw_error(env,error.what());return nullptr; }
}
jstring capabilities(JNIEnv* env, jobject, jlong handle) {
    try {
        auto context = context_for(handle);
        std::lock_guard<std::mutex> lock(context->render_mutex);
        return env->NewStringUTF(spk_capabilities(context->engine));
    } catch (const std::exception& error) { throw_error(env, error.what()); return nullptr; }
}
void cancel(JNIEnv* env, jobject, jlong handle) {
    try {
        auto context = context_for(handle);
        context->cancelled.store(true);
        // Opening a source can take time. The UI cancellation signal must never
        // wait for that work; its generation check still discards a late result.
        std::unique_lock<std::mutex> lock(context->session_mutex, std::try_to_lock);
        if (!lock.owns_lock()) return;
        if (context->session) spk_cancel(context->session, nullptr);
    } catch (const std::exception& error) { throw_error(env, error.what()); }
}
void destroy(JNIEnv*, jobject, jlong handle) {
    std::shared_ptr<EngineContext> retained;
    {
        std::lock_guard<std::mutex> lock(registry_mutex);
        auto found = registry.find(handle);
        if (found == registry.end()) return;
        retained = std::move(found->second);
        registry.erase(found);
    }
}
const JNINativeMethod methods[] = {
    {const_cast<char*>("create"), const_cast<char*>("(Ljava/lang/String;)J"), reinterpret_cast<void*>(create)},
    {const_cast<char*>("render"), const_cast<char*>("(JLandroid/graphics/Bitmap;Ljava/lang/String;Ljava/lang/String;[F)Landroid/graphics/Bitmap;"), reinterpret_cast<void*>(render)},
    {const_cast<char*>("capabilities"), const_cast<char*>("(J)Ljava/lang/String;"), reinterpret_cast<void*>(capabilities)},
    {const_cast<char*>("statistics"),const_cast<char*>("(J)[J"),reinterpret_cast<void*>(statistics)},
    {const_cast<char*>("sourceInfo"),const_cast<char*>("(Ljava/lang/String;)[I"),reinterpret_cast<void*>(source_info)},
    {const_cast<char*>("sourcePreview"),const_cast<char*>("(Ljava/lang/String;IJ)Landroid/graphics/Bitmap;"),reinterpret_cast<void*>(source_preview)},
    {const_cast<char*>("renderFile"),const_cast<char*>("(JLjava/lang/String;Ljava/lang/String;Ljava/lang/String;[FIJZ)Landroid/graphics/Bitmap;"),reinterpret_cast<void*>(render_file)},
    {const_cast<char*>("exportFile"),const_cast<char*>("(JLjava/lang/String;Ljava/lang/String;[FIJZLjava/lang/String;Ljava/lang/String;II)[F"),reinterpret_cast<void*>(export_file)},
    {const_cast<char*>("cancel"), const_cast<char*>("(J)V"), reinterpret_cast<void*>(cancel)},
    {const_cast<char*>("destroy"), const_cast<char*>("(J)V"), reinterpret_cast<void*>(destroy)},
};
} // namespace

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) {
    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) return JNI_ERR;
    jclass bridge = env->FindClass("io/github/proleeeeeeeeee/spektralab/engine/NativeBridge");
    if (!bridge || env->RegisterNatives(bridge, methods, sizeof(methods) / sizeof(methods[0])) != JNI_OK) return JNI_ERR;
    return JNI_VERSION_1_6;
}
