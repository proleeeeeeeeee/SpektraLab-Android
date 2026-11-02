// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
#include "source_codec.hpp"
#include <libraw/libraw.h>
#include <android/imagedecoder.h>
#include <android/data_space.h>
#include <fcntl.h>
#include <unistd.h>
#include <algorithm>
#include <cmath>
#include <cstring>
#include <memory>
#include <mutex>
#include <stdexcept>

namespace {
constexpr uint64_t max_pixels=240'000'000;
std::mutex raw_mutex;
void check_dimensions(uint32_t w,uint32_t h) {
    if (!w || !h || w>32768 || h>32768 || uint64_t(w)*h>max_pixels)
        throw std::invalid_argument("Source dimensions exceed the supported image limit");
}
void check_cancel(std::atomic<bool>* cancelled) {
    if (cancelled && cancelled->load()) throw std::runtime_error("Processing cancelled");
}
std::pair<uint32_t,uint32_t> target_size(uint32_t w,uint32_t h,int edge) {
    check_dimensions(w,h);
    if (edge<0 || edge>32768) throw std::invalid_argument("Invalid decode size");
    const double scale=edge==0?1.0:std::min(1.0,double(edge)/std::max(w,h));
    return {std::max(1u,uint32_t(std::lround(w*scale))),std::max(1u,uint32_t(std::lround(h*scale)))};
}
struct ImageDecoder {
    int fd=-1;
    AImageDecoder* value=nullptr;
    explicit ImageDecoder(const std::string& path) {
        fd=open(path.c_str(),O_RDONLY|O_CLOEXEC);
        if (fd<0) throw std::runtime_error("Could not open the private photograph");
        const int result=AImageDecoder_createFromFd(fd,&value);
        if (result!=0) { close(fd);fd=-1;throw std::runtime_error("This image encoding is not supported by Android"); }
    }
    ~ImageDecoder() { if (value) AImageDecoder_delete(value); if (fd>=0) close(fd); }
    SourceInfo info() {
        auto* header=AImageDecoder_getHeaderInfo(value);
        const std::string mime=AImageDecoderHeaderInfo_getMimeType(header);
        if (mime.find("dng")!=std::string::npos || mime.find("raw")!=std::string::npos)
            throw std::runtime_error("This RAW encoding is not supported by bundled LibRaw. Try a standard uncompressed DNG or a JPEG/HEIC copy.");
        SourceInfo result{uint32_t(AImageDecoderHeaderInfo_getWidth(header)),
                          uint32_t(AImageDecoderHeaderInfo_getHeight(header)),false};
        check_dimensions(result.width,result.height);return result;
    }
};
float half_to_float(uint16_t bits) {
    _Float16 value;
    std::memcpy(&value,&bits,sizeof bits);
    return float(value);
}
int raw_progress(void* data,enum LibRaw_progress,int,int) {
    return data && static_cast<std::atomic<bool>*>(data)->load()?1:0;
}
bool open_raw(LibRaw& raw,const std::string& path) {
    raw.imgdata.rawparams.max_raw_memory_mb=2048;
    return raw.open_file(path.c_str())==LIBRAW_SUCCESS && raw.imgdata.idata.raw_count>0;
}
SourceInfo raw_info(const LibRaw& raw) {
    uint32_t w=raw.imgdata.sizes.width,h=raw.imgdata.sizes.height;
    if (raw.imgdata.sizes.flip&4) std::swap(w,h);
    check_dimensions(w,h);return {w,h,true};
}
void raw_ok(int code,const char* stage) {
    if (code!=LIBRAW_SUCCESS) throw std::runtime_error(std::string(stage)+": "+LibRaw::strerror(code));
}
float encode_srgb(float value) {
    return value<=.0031308f?12.92f*value:1.055f*std::pow(std::max(0.f,value),1.f/2.4f)-.055f;
}
}

SourceInfo inspect_source(const std::string& path) {
    {
        std::lock_guard<std::mutex> lock(raw_mutex);
        LibRaw raw;
        if (open_raw(raw,path)) return raw_info(raw);
    }
    return ImageDecoder(path).info();
}

SourcePixels decode_source(const std::string& path,int edge,uint64_t budget,
                           std::atomic<bool>* cancelled,bool display_srgb) {
    check_cancel(cancelled);
    SourcePixels output;
    std::unique_lock<std::mutex> raw_lock(raw_mutex);
    LibRaw raw;
    if (open_raw(raw,path)) {
        output.original=raw_info(raw);
        if (uint64_t(output.original.width)*output.original.height*40+128*1024*1024>budget)
            throw std::runtime_error("Not enough available memory to decode this RAW photograph");
        raw.imgdata.rawparams.max_raw_memory_mb=unsigned(std::clamp<uint64_t>(budget/(1024*1024),128,4096));
        raw.set_progress_handler(raw_progress,cancelled);
        // Compatibility policy from SpektraLab's raw_decoder.cpp: full-size
        // AHD, as-shot WB, linear 16-bit ProPhoto, no automatic brightening.
        auto& p=raw.imgdata.params;
        p.user_qual=3;p.half_size=0;p.four_color_rgb=0;
        p.output_color=display_srgb?1:4;p.output_bps=16;
        p.gamm[0]=p.gamm[1]=1.0;p.no_auto_bright=1;p.adjust_maximum_thr=0;
        p.use_camera_wb=1;p.use_auto_wb=0;p.bright=1;p.highlight=0;p.user_flip=-1;
        raw_ok(raw.unpack(),"RAW unpack");
        check_cancel(cancelled);
        raw_ok(raw.dcraw_process(),"RAW development");
        int error=0;
        auto image=std::unique_ptr<libraw_processed_image_t,decltype(&LibRaw::dcraw_clear_mem)>(
            raw.dcraw_make_mem_image(&error),&LibRaw::dcraw_clear_mem);
        raw_ok(error,"RAW pixels");
        if (!image || image->type!=LIBRAW_IMAGE_BITMAP || image->bits!=16 || image->colors!=3)
            throw std::runtime_error("RAW decoder did not produce full RGB16 pixels");
        check_dimensions(image->width,image->height);
        output.original={image->width,image->height,true};
        const auto [w,h]=target_size(image->width,image->height,edge);
        output.width=w;output.height=h;output.linear_prophoto=!display_srgb;
        output.rgb.resize(size_t(w)*h*3);
        const auto* pixels=reinterpret_cast<const uint16_t*>(image->data);
        for (uint32_t y=0;y<h;++y) {
            check_cancel(cancelled);
            const uint32_t y0=uint32_t(uint64_t(y)*image->height/h);
            const uint32_t y1=uint32_t(uint64_t(y+1)*image->height/h);
            for (uint32_t x=0;x<w;++x) {
                const uint32_t x0=uint32_t(uint64_t(x)*image->width/w);
                const uint32_t x1=uint32_t(uint64_t(x+1)*image->width/w);
                // Average in linear light instead of selecting one sensor
                // sample. Original mode has exactly one source pixel per bin.
                uint64_t sums[3]{};
                for (uint32_t sy=y0;sy<y1;++sy) for (uint32_t sx=x0;sx<x1;++sx)
                    for (int c=0;c<3;++c) sums[c]+=pixels[(size_t(sy)*image->width+sx)*3+c];
                const double denominator=double(x1-x0)*(y1-y0)*65535.0;
                for (int c=0;c<3;++c) {
                    const float value=float(sums[c]/denominator);
                    output.rgb[(size_t(y)*w+x)*3+c]=display_srgb?encode_srgb(value):value;
                }
            }
        }
        return output;
    }
    raw_lock.unlock();
    ImageDecoder decoder(path);
    output.original=decoder.info();
    const auto [w,h]=target_size(output.original.width,output.original.height,edge);
    if (uint64_t(w)*h*24+64*1024*1024>budget)
        throw std::runtime_error("Not enough available memory for this decode size");
    if (AImageDecoder_setAndroidBitmapFormat(decoder.value,ANDROID_BITMAP_FORMAT_RGBA_F16)!=0 ||
        AImageDecoder_setDataSpace(decoder.value,ADATASPACE_SRGB)!=0 ||
        AImageDecoder_setTargetSize(decoder.value,int(w),int(h))!=0)
        throw std::runtime_error("Android could not configure a high-precision image decode");
    const size_t stride=AImageDecoder_getMinimumStride(decoder.value);
    std::vector<uint16_t> pixels((stride*h+1)/2);
    if (AImageDecoder_decodeImage(decoder.value,pixels.data(),stride,pixels.size()*2)!=0)
        throw std::runtime_error("Android could not decode the complete photograph");
    output.width=w;output.height=h;output.rgb.resize(size_t(w)*h*3);
    for (uint32_t y=0;y<h;++y) {
        check_cancel(cancelled);
        const auto* row=reinterpret_cast<const uint16_t*>(reinterpret_cast<const uint8_t*>(pixels.data())+stride*y);
        for (uint32_t x=0;x<w;++x) {
            const float alpha=std::clamp(half_to_float(row[x*4+3]),0.f,1.f);
            for (int c=0;c<3;++c) {
                const float value=half_to_float(row[x*4+c])+1.f-alpha;
                output.rgb[(size_t(y)*w+x)*3+c]=std::isfinite(value)?value:0.f;
            }
        }
    }
    return output;
}
