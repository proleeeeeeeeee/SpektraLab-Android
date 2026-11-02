# Building

## Toolchain

| Tool | Version |
| --- | --- |
| JDK | 21, Java/Kotlin bytecode 17 |
| Gradle wrapper | 8.13, official distribution SHA-256 in wrapper properties |
| Android Gradle Plugin | 8.13.2 |
| Kotlin / Compose plugin | 2.2.21 |
| Compose BOM | 2025.10.01 |
| Compile / target SDK | 36 |
| Minimum Android | API 30; Ultra HDR output API 34 |
| Android NDK | 28.2.13676358 |
| CMake | 3.22.1 |
| glslangValidator | Host compiler supporting Vulkan 1.0 SPIR-V |

Accept the official Google SDK agreement yourself before installing SDK packages. Set ANDROID_HOME or provide an ignored local.properties with sdk.dir. Install platform-tools, platforms;android-36, build-tools;35.0.0, ndk;28.2.13676358 and cmake;3.22.1 with sdkmanager.

On Ubuntu, glslang-tools supplies glslangValidator. On Windows, use an official Khronos glslang host binary. Set GLSLANG_VALIDATOR to its absolute path, or pass -Pglslang=/path/to/glslangValidator. GLSL shaders are compiled during the build; no runtime compiler download occurs.

## Commands

    git submodule update --init --recursive
    ./gradlew :core:test :app:lintDebug :app:assembleDebug

The phone APK is app/build/outputs/apk/debug/app-debug.apk. The default ABI is arm64-v8a. For an x86_64 emulator use:

    ./gradlew -PandroidAbi=x86_64 :engine:connectedDebugAndroidTest :app:connectedDebugAndroidTest

After packaging, verify native alignment and attribution, then verify ZIP alignment and signing with the Android build tools:

    python scripts/check_apk.py app/build/outputs/apk/debug/app-debug.apk
    zipalign -c -P 16 4 app/build/outputs/apk/debug/app-debug.apk
    apksigner verify --verbose app/build/outputs/apk/debug/app-debug.apk

To produce one APK with both ABIs, use -PandroidAbi=arm64-v8a,x86_64. The native bridge and NDK libraries use 16 KB-compatible load alignment.

Instrumentation requires a Vulkan-capable device or emulator. Unit tests and lint do not establish GPU/device compatibility. Tests generate their own synthetic photographs, exercise real native rendering, and keep no personal images in the source tree.

## Signing and distribution

The first APK is a debuggable device-test build, not a store release. Never commit a keystore, signing password, local.properties or access token. A future production release must have an independently managed release key and a measured device test matrix.

Distribute the exact matching source, build scripts, notices and pinned upstream source alongside an APK. GitHub's automatic source archive omits submodule contents: use the provided source-bundle script instead.

    python scripts/source_bundle.py --output /path/to/SpektraLab-Android-source.zip

The script archives tracked files from the root commit and every pinned submodule, including SpektraLab and LibRaw, excluding build caches and private keys. It must be run after committing changes. The APK and complete source bundle in a release must correspond to the same commit. LibRaw is compiled locally without optional proprietary SDKs or runtime downloads.

Official references: [Android SDK terms](https://developer.android.com/studio/terms), [16 KB page support](https://developer.android.com/guide/practices/page-sizes), [emulator acceleration and Vulkan](https://developer.android.com/studio/run/emulator-acceleration).
