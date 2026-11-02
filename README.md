# SpektraLab for Android

An unofficial, offline Android port of [SpektraLab](https://github.com/JamesQiu2005/SpektraLab), maintained by [proleeeeeeeeee](https://github.com/proleeeeeeeeee).

Download the test APK and matching complete source from [Releases](https://github.com/proleeeeeeeeee/SpektraLab-Android/releases).

Import a photograph, choose film and paper, add a physical film edge, and save a new photograph. Processing uses the actual upstream C++ spectral engine through Vulkan on the device. A running computer, account and network connection are not required.

The first device-test build targets the Galaxy S25 Ultra (arm64, Android 10 or newer with a compatible Vulkan driver). This is an early port, not an official SpektraLab release. See [testing](docs/TESTING.md) for measured results and remaining device checks.

## Current workflow

- System photo picker; the app receives only the photograph you select.
- Four films: Kodak Gold 200, Portra 400, CineStill 800T and Tri-X 400.
- Compatible color papers and Ilford Multigrade IV RC for black and white.
- No frame, 135 perforations, filed 135 edge, 120 6×6 and XPan.
- Direct selection in a horizontal Film / Paper / Frame strip.
- Adjust exposure, automatic metering, temperature, tint, contrast, saturation, highlights and shadows.
- Set physical grain and halation strength.
- Free crop with draggable corners, aspect presets, 90° rotation, horizontal/vertical flips and ±15° straightening.
- Drag to reframe, undo / redo, and hold to compare the original.
- JPEG or PNG export into Pictures / SpektraLab.
- Private last-edit recovery. Gallery originals are never overwritten.

Preview inputs have a 1024 px long edge. Export inputs have a **2560 px long edge**, before cropping and the added film edge. This deliberate first-build memory budget also applies to large Samsung photographs. Export is opaque 8-bit sRGB; original EXIF and location are not copied. RAW, HDR output, batch work, full-resolution export and desktop editing controls are outside this first build.

## Build

See [BUILDING.md](docs/BUILDING.md) for the pinned toolchain, commands and source packaging. Clone with submodules:

    git clone --recurse-submodules https://github.com/proleeeeeeeeee/SpektraLab-Android.git
    cd SpektraLab-Android
    ./gradlew :core:test :app:assembleDebug :app:lintDebug

Install the official Android SDK/NDK and a host glslangValidator before building. Local debug APKs use the Android debug signing key; keep that key private. CI artifacts are independently signed test builds.

## Structure

| Module | Responsibility |
| --- | --- |
| core | Immutable recipes, compatibility, crop geometry, bounded history and native parameter schema |
| engine | Serial engine ownership, trusted resource installation and checked JNI bitmap bridge |
| app | Compose interface, system picker, bounded private import and atomic MediaStore export |
| third_party/spektralab | Unmodified upstream source pinned as a Git submodule |

See [ARCHITECTURE.md](docs/ARCHITECTURE.md) and [PRIVACY.md](docs/PRIVACY.md).

## Credits and licenses

Android application and integration: Copyright (C) 2026 proleeeeeeeeee, GPL-3.0-only.

This project builds on **SpektraLab by JamesQiu2005** and **spektrafilm by Andrea Volpato**. The pinned upstream code is GPLv3. The original spektrafilm film/paper profiles, constants and direct derivatives retain **CC BY-SA 4.0** and Andrea Volpato's attribution. Product profiles, fonts and bundled libraries retain their own notices. See [NOTICE.md](NOTICE.md), [LICENSE](LICENSE), and [LICENSES](LICENSES).

No upstream authorship is claimed by the Android maintainer. The project is independent and is not endorsed by the named manufacturers; product names identify the simulated materials. The app's small stock illustrations are original vector drawings, not manufacturer packaging photographs.

Your finished photographs are yours; the profile license does not impose copyleft on them.
