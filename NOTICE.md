# Notices and source attribution

## Android port

Copyright (C) 2026 proleeeeeeeeee.

The Kotlin Android interface, integration code, build scripts, documentation and original vector stock illustrations are GPL-3.0-only. The full license is in LICENSE.

## SpektraLab

The Android scan-tone subset in engine/src/main/cpp/scan_tone.hpp adapts the white-balance, contrast, saturation and highlight/shadow math in upstream desktop/src/shared/adjustments.ts (itself ported from Adjustments.swift). This adaptation uses encoded sRGB, omits the remaining desktop grade controls and retains GPLv3 and the upstream attribution.

This is an independent port of [SpektraLab by JamesQiu2005](https://github.com/JamesQiu2005/SpektraLab), pinned at a46627d250545a394ca21522f8a28b0fb89e4c42 in third_party/spektralab. Original authorship, copyright and source history are preserved there. The upstream engine source is not modified by this port.

The upstream product-profile provenance is preserved verbatim in LICENSES/Product-profiles-NOTICE.md and included in the APK. These separately authored product data must not be misattributed to spektrafilm.

## spektrafilm

The spectral simulation and original profiles originate from [spektrafilm by Andrea Volpato](https://github.com/andreavolpato/spektrafilm).

Original spektrafilm profiles, baked constants, LUTs and direct derivatives retain CC BY-SA 4.0. See the unchanged LICENSES/SPEKTRAFILM_LICENSE.txt for full terms and attribution. This port makes no changes to those profiles or LUTs. Finished photographs remain the user's work.

## Other bundled components

LibRaw 0.22.2 is pinned at b93f6e45c194f5df9b02a43b1af9a54b4f41f33f in third_party/libraw. Copyright belongs to LibRaw LLC and its credited contributors. This port uses the LGPL-2.1 option of LibRaw's dual license. The unmodified COPYRIGHT and LICENSE.LGPL are bundled in the APK; complete corresponding source and build scripts, including both pinned submodules, accompany every APK release. No optional proprietary RAW SDK is linked.

The native engine incorporates volk (MIT), stb (MIT or public domain), Liberation Sans (SIL Open Font License 1.1) and DejaVu Sans Mono (the bundled DejaVu/Bitstream notices). Their full notices are preserved in LICENSES and in APK assets. Font data used for physical edge text is pinned with upstream; no external fonts are parsed.

The application uses AndroidX/Jetpack Compose, Kotlin, kotlinx.coroutines and kotlinx.serialization, with their respective upstream licenses. Gradle wrapper files retain their Apache-2.0 notices. Third-party authors are credited, not added as repository collaborators.

Manufacturer and product names are trademarks of their owners, used to identify simulated materials. The UI illustrations are original drawings, not photographs of actual product packaging.
