# Architecture

## Boundaries

The Android layer does not reproduce or approximate the spectral material model. It feeds encoded sRGB float RGB, or developed linear ProPhoto RGB for RAW, into the upstream C ABI. An additive translation unit includes the pinned engine implementation and reads its completed float scan before RGBA16 materialization/clamping. Display rounds that scan to opaque ARGB_8888; TIFF quantizes it once to RGB16. No color matrix or decorative overlay stands in for the engine.

The native engine, shaders and profiles are pinned together at upstream revision a46627d250545a394ca21522f8a28b0fb89e4c42. The upstream submodule is unmodified. Native builds retain strict floating-point settings, including disabled fast math and contraction.

## State and work

The ViewModel serializes UI events on Main and stores small immutable recipes. Fifty recipe steps bound undo history independently of bitmap size. Film changes select a compatible paper; unsupported material IDs never cross the native boundary.

One executor owns the Vulkan engine and its cached session. Preview requests are debounced and use collectLatest plus generation checks to reject stale results. Cancellation signals the native session; queued work and destruction stay ordered. The JNI registry exposes opaque IDs, retains in-flight ownership, and validates dimensions, strides, types and output limits.

The system photo/document picker grants selected content URIs. A bounded stream copies each to a UUID-named private file. LibRaw develops supported RAWs at full size with as-shot WB, no automatic brightening and linear RGB16 ProPhoto. The NDK ImageDecoder decodes ordinary images into client-owned RGBA_F16 sRGB memory at the requested size. Header dimensions, byte counts and memory admission are checked before large work. Untrusted display names are never used as filesystem paths.

An AtomicFile index owns named projects, photo metadata/recipes, presets and export-queue snapshots. A Mutex serializes changes. Revision numbers reject stale preview writes, and an absent indexed photo cannot be resurrected by a late render. Version 0.1/0.2 last-edit storage migrates without deleting the private original. Files are deleted only after the updated index is committed. Limits bound index bytes, photo/project/preset/job counts and import size. There is no gallery scan, remote resource loading, analytics, account, server or WebView. The APK supplies all native resources.

## Adjustments and composition

Exposure compensation, auto exposure, grain and halation use the upstream physical engine parameters. Temperature/tint, contrast, saturation and highlight/shadow controls form a scan-tone stage adapted from the upstream desktop reference. Android applies this subset to encoded sRGB with the same native function for preview and export, before output quantization. HDR permits values above SDR white through this stage. It does not claim parity with the desktop's ProPhoto working-space grade.

The native adapter retains one ungraded scan. Scan-tone-only edits reuse it, avoiding a spectral re-render and avoiding cumulative grading of an 8-bit preview. A changed source or physical recipe invalidates it. Cancellation is checked during scan conversion; all cached texture ownership stays on the serial engine dispatcher.

Slider gestures preview draft recipes and commit one history entry on release. Crop-sheet changes remain local until Apply crop; Cancel discards them. Serialized defaults preserve edits made by version 0.1.

Crop rectangles refer to the original source. Aspect presets fit inside that rectangle; an active film frame owns the physical gate ratio. Straightening shrinks the effective crop until every rotated corner lies inside the source. The engine then turns and flips the crop, before producing the physical scan. Geometry parameters are shared by preview and export.

## Export

Export snapshots the recipe and size, pauses preview and renders the private source at that exact requested size. Original uses the full decoded source. A conservative admission budget accounts for source, negative, staging, render arenas and readback; GPU limits can also reject a job. Nothing silently changes the chosen resolution. Full sessions are released after each job; there is no unsupported independent-tile approximation of spatial effects.

TIFF is baseline little-endian uncompressed RGB16 with the pinned sRGB ICC profile and row-streamed quantization from float. JPEG/PNG compress native pixel memory through Android's NDK API, avoiding an extra full-size Java Bitmap. Ultra HDR renders SDR and the paper's calibrated EDR response with the same geometry/negative, computes a quarter-size RGB gain map from the edited linear-light ratios, then uses Android 14 Gainmap JPEG encoding. Unsupported paper calibrations fail explicitly. The camera's original gain map is not retained as a scene-linear input path, and the editor remains an SDR preview.

A completed private cache file is copied into a pending MediaStore row. Publication exposes it only after the copy completes; failure or cancellation removes the pending entry. Batch jobs persist immutable recipe/format/size snapshots and run serially. A pending URI is recorded before copying, enabling recovery to distinguish already-published output from unfinished work and avoid duplicate retries. Failed photos do not prevent subsequent jobs. App termination pauses work; resume is explicit, not a background-service promise. Gallery originals are never opened for writing.

## Extending

- Add materials through the explicit catalog and matching compatible profiles; add cross-boundary tests.
- Add render controls to EditRecipe and the single engineParameters boundary.
- Keep business rules in core and platform I/O out of it.
- Keep UI selection separate from render ownership.
- Update the upstream pin, resource installation revision and notices together.
- Do not modify profile arrays to compensate for a UI, color-space or GPU bug.

Comments explain ownership, invariants and unusual decisions. Straightforward UI construction is left readable without line-by-line narration.
