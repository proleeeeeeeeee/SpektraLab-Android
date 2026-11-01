# Architecture

## Boundaries

The Android layer does not reproduce or approximate the spectral material model. It feeds encoded sRGB float RGB into the upstream C ABI and receives encoded sRGB RGBA16. The JNI adapter rounds that result into an opaque Android ARGB_8888 bitmap for display and export. No color matrix or decorative overlay stands in for the engine.

The native engine, shaders and profiles are pinned together at upstream revision a46627d250545a394ca21522f8a28b0fb89e4c42. The upstream submodule is unmodified. Native builds retain strict floating-point settings, including disabled fast math and contraction.

## State and work

The ViewModel serializes UI events on Main and stores small immutable recipes. Fifty recipe steps bound undo history independently of bitmap size. Film changes select a compatible paper; unsupported material IDs never cross the native boundary.

One executor owns the Vulkan engine and its cached session. Preview requests are debounced and use collectLatest plus generation checks to reject stale results. Cancellation signals the native session; queued work and destruction stay ordered. The JNI registry exposes opaque IDs, retains in-flight ownership, and validates dimensions, strides, types and output limits.

The system picker grants access to one content URI. A bounded stream copies it to a UUID-named private file. ImageDecoder checks the header and sets a target size before allocation, handles image orientation and converts to sRGB. Untrusted display names are never used as filesystem paths.

Only the current private import is retained. A small AtomicFile stores its recipe. There is no gallery scan, remote resource loading, analytics, account, server or WebView. The signed APK supplies all native resources.

## Export

Export snapshots the recipe, pauses preview, decodes the private source at the export budget, renders a separate native source identity and encodes through Android. A MediaStore row remains pending until compression completes. Failure or cancellation deletes the incomplete row. The original gallery item is never opened for writing.

The first build deliberately processes at a 2560 px input long edge. Full-resolution work should be introduced only with a measured memory budget and a supported tiled upstream path; do not silently remove the limit.

## Extending

- Add materials through the explicit catalog and matching compatible profiles; add cross-boundary tests.
- Add render controls to EditRecipe and the single engineParameters boundary.
- Keep business rules in core and platform I/O out of it.
- Keep UI selection separate from render ownership.
- Update the upstream pin, resource installation revision and notices together.
- Do not modify profile arrays to compensate for a UI, color-space or GPU bug.

Comments explain ownership, invariants and unusual decisions. Straightforward UI construction is left readable without line-by-line narration.
