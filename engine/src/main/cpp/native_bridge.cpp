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

namespace {
constexpr uint64_t kMaxInputPixels = 7'000'000;
constexpr uint64_t kMaxOutputPixels = 16'000'000;

struct EngineContext {
    spk_engine* engine = nullptr;
    spk_session* session = nullptr;
    std::string source_key;
    std::string rendered_parameters;
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
        if (context->source_key != source_key || !context->session) {
            spk_result_free(&context->cached_result);
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
        } else if (context->rendered_parameters != params) {
            if (spk_set_params(context->session, params.c_str(), nullptr) != SPK_OK)
                throw std::runtime_error(engine_error(context->engine, "Updating film settings failed"));
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
