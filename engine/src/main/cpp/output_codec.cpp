// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
#include "output_codec.hpp"
#include <android/bitmap.h>
#include <android/data_space.h>
#include <fcntl.h>
#include <unistd.h>
#include <cerrno>
#include <cstring>
#include <fstream>
#include <limits>
#include <stdexcept>

namespace {
void check(std::atomic<bool>& cancelled) {
    if (cancelled.load()) throw std::runtime_error("Processing cancelled");
}
class OutputFile {
public:
    int fd;
    std::string path;
    bool complete=false;
    explicit OutputFile(const std::string& value):path(value) {
        fd=open(path.c_str(),O_WRONLY|O_CREAT|O_EXCL|O_CLOEXEC,0600);
        if (fd<0) throw std::runtime_error("Could not create a private export file");
    }
    ~OutputFile() { if (fd>=0) close(fd); if (!complete) unlink(path.c_str()); }
    bool bytes(const void* data,size_t size) {
        const auto* at=static_cast<const uint8_t*>(data);
        while (size) {
            const ssize_t n=write(fd,at,size);
            if (n<0 && errno==EINTR) continue;
            if (n<=0) return false;
            size-=size_t(n);at+=n;
        }
        return true;
    }
    void finish() {
        if (fsync(fd)!=0) throw std::runtime_error("Could not finish writing the photograph");
        complete=true;
    }
};
void put16(std::vector<uint8_t>& b,uint16_t value) { b.push_back(value&255);b.push_back(value>>8); }
void put32(std::vector<uint8_t>& b,uint32_t value) { put16(b,value&65535);put16(b,value>>16); }
float linear(float v) { return v<=.04045f?v/12.92f:std::pow((v+.055f)/1.055f,2.4f); }
}

bool PhotoRegion::contains(uint32_t x,uint32_t y,uint32_t width,uint32_t height) const {
    if (!active || !width || !height) return true;
    const float px=(float(x)+.5f)/float(width),py=(float(y)+.5f)/float(height);
    bool positive=false,negative=false;
    for (int i=0;i<4;++i) {
        const int j=(i+1)%4;
        const float ax=gate[i*2],ay=gate[i*2+1],bx=gate[j*2],by=gate[j*2+1];
        const float cross=(bx-ax)*(py-ay)-(by-ay)*(px-ax);
        positive|=cross>1e-6f;negative|=cross< -1e-6f;
        if (positive&&negative) return false;
    }
    return true;
}

std::vector<uint8_t> scan_rgba(const std::vector<float>& rgb,const ScanTone& tone,std::atomic<bool>& cancelled,
                               uint32_t width,uint32_t height,const PhotoRegion& photo) {
    if (photo.active && (!width || !height || rgb.size()!=uint64_t(width)*height*3))
        throw std::invalid_argument("Invalid photo gate dimensions");
    std::vector<uint8_t> rgba(rgb.size()/3*4);
    for (size_t i=0;i<rgb.size()/3;++i) {
        if (i%65536==0) check(cancelled);
        const bool grade=!photo.active||photo.contains(uint32_t(i%width),uint32_t(i/width),width,height);
        const auto color=grade?tone.apply(rgb[i*3],rgb[i*3+1],rgb[i*3+2]):
            std::array<float,3>{std::clamp(rgb[i*3],0.f,1.f),std::clamp(rgb[i*3+1],0.f,1.f),std::clamp(rgb[i*3+2],0.f,1.f)};
        for (int c=0;c<3;++c) rgba[i*4+c]=uint8_t(std::lround(color[c]*255.f));
        rgba[i*4+3]=255;
    }
    return rgba;
}
void encode_image(const std::string& path,uint32_t w,uint32_t h,
                  const std::vector<uint8_t>& rgba,bool png,int quality,std::atomic<bool>& cancelled) {
    if (!w || !h || rgba.size()!=uint64_t(w)*h*4 || quality<60 || quality>100)
        throw std::invalid_argument("Invalid output image");
    // The NDK compressor writes native memory directly. Original-sized JPEG
    // and PNG exports do not require an additional full-sized Java Bitmap.
    AndroidBitmapInfo info{};info.width=w;info.height=h;info.stride=w*4;
    info.format=ANDROID_BITMAP_FORMAT_RGBA_8888;info.flags=ANDROID_BITMAP_FLAGS_ALPHA_OPAQUE;
    OutputFile file(path);
    struct Writer { OutputFile* file;std::atomic<bool>* cancelled; } writer{&file,&cancelled};
    const auto callback=[](void* value,const void* data,size_t size) -> bool {
        auto* context=static_cast<Writer*>(value);
        return !context->cancelled->load() && context->file->bytes(data,size);
    };
    const int result=AndroidBitmap_compress(&info,ADATASPACE_SRGB,rgba.data(),png?1:0,quality,&writer,callback);
    check(cancelled);
    if (result!=ANDROID_BITMAP_RESULT_SUCCESS) throw std::runtime_error("Android image encoding failed");
    file.finish();
}

void write_tiff16(const std::string& path,const std::string& profile,uint32_t w,uint32_t h,
                  const std::vector<float>& rgb,const ScanTone& tone,std::atomic<bool>& cancelled,
                  const PhotoRegion& photo) {
    if (!w || !h || rgb.size()!=uint64_t(w)*h*3) throw std::invalid_argument("Invalid TIFF dimensions");
    std::ifstream input(profile,std::ios::binary|std::ios::ate);
    if (!input || input.tellg()<128 || input.tellg()>1024*1024) throw std::runtime_error("The sRGB output profile is unavailable");
    std::vector<uint8_t> icc(size_t(input.tellg()));input.seekg(0);input.read(reinterpret_cast<char*>(icc.data()),icc.size());
    if (!input || std::memcmp(icc.data()+36,"acsp",4)!=0) throw std::runtime_error("Invalid sRGB output profile");
    // Baseline little-endian RGB TIFF: one uncompressed strip, unsigned 16-bit
    // samples and an embedded ICC profile. Quantize the float scan once, here.
    constexpr uint16_t entries=12;
    constexpr uint32_t bits_offset=8+2+entries*12+4;
    const uint32_t icc_offset=bits_offset+6;
    const uint32_t data_offset=(icc_offset+uint32_t(icc.size())+3)&~3u;
    const uint64_t byte_count=uint64_t(w)*h*6;
    if (byte_count+data_offset>std::numeric_limits<uint32_t>::max()) throw std::runtime_error("This image requires BigTIFF, which is not supported");
    std::vector<uint8_t> header{'I','I'};put16(header,42);put32(header,8);put16(header,entries);
    const auto tag=[&](uint16_t id,uint16_t type,uint32_t count,uint32_t value) {
        put16(header,id);put16(header,type);put32(header,count);put32(header,value);
    };
    tag(256,4,1,w);tag(257,4,1,h);tag(258,3,3,bits_offset);tag(259,3,1,1);
    tag(262,3,1,2);tag(273,4,1,data_offset);tag(274,3,1,1);tag(277,3,1,3);
    tag(278,4,1,h);tag(279,4,1,uint32_t(byte_count));tag(284,3,1,1);tag(34675,7,uint32_t(icc.size()),icc_offset);
    put32(header,0);for (int c=0;c<3;++c) put16(header,16);
    header.insert(header.end(),icc.begin(),icc.end());header.resize(data_offset,0);
    OutputFile file(path);
    if (!file.bytes(header.data(),header.size())) throw std::runtime_error("TIFF header write failed");
    std::vector<uint8_t> row;row.reserve(size_t(w)*6);
    for (uint32_t y=0;y<h;++y) {
        check(cancelled);row.clear();
        for (uint32_t x=0;x<w;++x) {
            const size_t i=(size_t(y)*w+x)*3;
            const auto color=photo.active&&!photo.contains(x,y,w,h)?
                std::array<float,3>{std::clamp(rgb[i],0.f,1.f),std::clamp(rgb[i+1],0.f,1.f),std::clamp(rgb[i+2],0.f,1.f)}:
                tone.apply(rgb[i],rgb[i+1],rgb[i+2]);
            for (float v:color) put16(row,uint16_t(std::lround(v*65535.f)));
        }
        if (!file.bytes(row.data(),row.size())) throw std::runtime_error("TIFF pixel write failed");
    }
    file.finish();
}

GainMapPixels make_gain_map(uint32_t w,uint32_t h,const std::vector<uint8_t>& sdr,
                            const std::vector<float>& hdr,const ScanTone& tone,std::atomic<bool>& cancelled,
                            const PhotoRegion& photo) {
    if (sdr.size()!=uint64_t(w)*h*4 || hdr.size()!=uint64_t(w)*h*3)
        throw std::invalid_argument("HDR and SDR dimensions differ");
    GainMapPixels result{(w+3)/4,(h+3)/4,1.f,1.f,{}};
    std::vector<float> logs(size_t(result.width)*result.height*3);
    constexpr float epsilon=1e-4f;
    for (uint32_t gy=0;gy<result.height;++gy) {
        check(cancelled);
        for (uint32_t gx=0;gx<result.width;++gx) {
            std::array<float,3> sum{};int samples=0;
            for (uint32_t y=gy*4;y<std::min(h,gy*4+4);++y) for (uint32_t x=gx*4;x<std::min(w,gx*4+4);++x) {
                const size_t i=size_t(y)*w+x;
                const auto color=photo.active&&!photo.contains(x,y,w,h)?
                    std::array<float,3>{std::clamp(hdr[i*3],0.f,8.f),std::clamp(hdr[i*3+1],0.f,8.f),std::clamp(hdr[i*3+2],0.f,8.f)}:
                    tone.apply(hdr[i*3],hdr[i*3+1],hdr[i*3+2],true);
                for (int c=0;c<3;++c) {
                    const float ratio=std::clamp((linear(color[c])+epsilon)/(linear(sdr[i*4+c]/255.f)+epsilon),1.f/16.f,16.f);
                    sum[c]+=std::log(ratio);
                }
                ++samples;
            }
            for (int c=0;c<3;++c) {
                const float value=sum[c]/samples;
                logs[(size_t(gy)*result.width+gx)*3+c]=value;
                result.minimum=std::min(result.minimum,std::exp(value));
                result.maximum=std::max(result.maximum,std::exp(value));
            }
        }
    }
    // Uniform dark photographs may contain no pixels above SDR white. A gain
    // map remains valid, with a small nonzero encoding span for the metadata.
    result.minimum=std::min(result.minimum,.999f);result.maximum=std::max(result.maximum,1.001f);
    const float low=std::log(result.minimum),span=std::log(result.maximum)-low;
    result.rgba.resize(size_t(result.width)*result.height*4);
    for (size_t i=0;i<logs.size()/3;++i) {
        for (int c=0;c<3;++c) result.rgba[i*4+c]=uint8_t(std::lround(std::clamp((logs[i*3+c]-low)/span,0.f,1.f)*255.f));
        result.rgba[i*4+3]=255;
    }
    return result;
}
