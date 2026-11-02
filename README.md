# SpektraLab for Android

An unofficial, offline Android port of [SpektraLab](https://github.com/JamesQiu2005/SpektraLab), maintained by [proleeeeeeeeee](https://github.com/proleeeeeeeeee).

Download the test APK and matching complete source from [Releases](https://github.com/proleeeeeeeeee/SpektraLab-Android/releases).

Import a photograph, choose film and paper, add a physical film edge, and save a new photograph. Processing uses the actual upstream C++ spectral engine through Vulkan on the device. A running computer, account and network connection are not required.

The device-test build targets the Galaxy S25 Ultra (arm64, Android 11 or newer with a compatible Vulkan driver). Ultra HDR export requires Android 14 or newer. This is an early port, not an official SpektraLab release. See [testing](docs/TESTING.md) for measured results and remaining device checks.

## Current workflow

- System multi-photo picker and document picker for RAW files; access is limited to selected files.
- Four films: Kodak Gold 200, Portra 400, CineStill 800T and Tri-X 400.
- Compatible color papers and Ilford Multigrade IV RC for black and white.
- No frame, 135 perforations, filed 135 edge, 120 6×6 and XPan.
- Direct selection in a horizontal Film / Paper / Frame strip.
- Adjust exposure, automatic metering, temperature, tint, contrast, saturation, highlights and shadows.
- Set physical grain and halation strength.
- Free crop with draggable corners, aspect presets, 90° rotation, horizontal/vertical flips and ±15° straightening.
- Drag to reframe, undo / redo, and hold to compare the original.
- Named projects with a persistent multi-photo grid, selection and private edit recovery.
- Custom presets and copied settings; composition transfers only when requested.
- Resumable serial batch export, per-photo error reporting and retry.
- JPEG, PNG, true 16-bit RGB TIFF with an embedded sRGB profile, and calibrated Ultra HDR JPEG export.
- 2560 px, 4096 px or Original resolution. Gallery originals are never overwritten.

Preview inputs have a 1024 px long edge. Original export uses the complete decoded source before crop and film edge; smaller sizes explicitly downsample at import-to-engine time. A conservative memory check and the GPU driver may reject a large job. **Original never silently shrinks the source.** This is not a claim that a phone can process every 50 MP or 200 MP photograph at full size.

RAW development uses bundled LibRaw 0.22.2, full sensor pixels and as-shot white balance, then feeds linear 16-bit ProPhoto RGB into the spectral engine. It does not use an embedded JPEG. Some compressed DNG/Expert RAW variants and proprietary encodings are unsupported; S25 Ultra camera files still need hardware testing. RAW is an input format: processed sensor RAW export is not offered.

TIFF quantizes the float scan directly to unsigned RGB16, without passing through an 8-bit Bitmap. JPEG/PNG remain opaque 8-bit sRGB. Ultra HDR combines a new SDR base with a gain map calculated from the same edited print and the paper's calibrated EDR response; unsupported papers fail explicitly. The editor displays the SDR preview. The original camera gain map is not preserved as a separate scene-linear HDR input path. Export does not copy original EXIF or GPS.

Batch work runs while the app is open. If Android stops it, completed outputs stay saved and unfinished jobs can be resumed. This build does not provide a background foreground-service worker, desktop color-space parity, layered masks or lossless RAW output.

## Build

See [BUILDING.md](docs/BUILDING.md) for the pinned toolchain, commands and source packaging. Clone with submodules:

    git clone --recurse-submodules https://github.com/proleeeeeeeeee/SpektraLab-Android.git
    cd SpektraLab-Android
    ./gradlew :core:test :app:assembleDebug :app:lintDebug

Install the official Android SDK/NDK and a host glslangValidator before building. Local debug APKs use the Android debug signing key; keep that key private. CI artifacts are independently signed test builds.

## Structure

| Module | Responsibility |
| --- | --- |
| core | Immutable recipes, presets, export snapshots, compatibility and crop geometry |
| engine | Serial Vulkan ownership, RAW/image decoding, float output, TIFF and HDR encoding |
| app | Compose interface, atomic project storage, resumable queue and MediaStore publishing |
| third_party/spektralab | Unmodified upstream source pinned as a Git submodule |
| third_party/libraw | Unmodified LibRaw 0.22.2 source, using its LGPL-2.1 license option |

See [ARCHITECTURE.md](docs/ARCHITECTURE.md) and [PRIVACY.md](docs/PRIVACY.md).

## Credits and licenses

Android application and integration: Copyright (C) 2026 proleeeeeeeeee, GPL-3.0-only.

This project builds on **SpektraLab by JamesQiu2005** and **spektrafilm by Andrea Volpato**. The pinned upstream code is GPLv3. The original spektrafilm film/paper profiles, constants and direct derivatives retain **CC BY-SA 4.0** and Andrea Volpato's attribution. Product profiles, fonts and bundled libraries retain their own notices. See [NOTICE.md](NOTICE.md), [LICENSE](LICENSE), and [LICENSES](LICENSES).

No upstream authorship is claimed by the Android maintainer. The project is independent and is not endorsed by the named manufacturers; product names identify the simulated materials. The app's small stock illustrations are original vector drawings, not manufacturer packaging photographs.

Your finished photographs are yours; the profile license does not impose copyleft on them.
