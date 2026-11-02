# Architecture

## Boundaries

The Android layer does not reproduce or approximate the spectral material model. It feeds encoded sRGB float RGB, or developed linear ProPhoto RGB for RAW, into the upstream C ABI. An additive translation unit includes the pinned engine implementation and reads its completed float scan before RGBA16 materialization/clamping. Display rounds that scan to opaque ARGB_8888; TIFF quantizes it once to RGB16. No color matrix or decorative overlay stands in for the engine.

The native engine, shaders and profiles are pinned together at upstream revision a46627d250545a394ca21522f8a28b0fb89e4c42. The upstream submodule is unmodified. Native builds retain strict floating-point settings, including disabled fast math and contraction.

## State and work

The ViewModel serializes UI events on Main and stores small immutable recipes. Fifty recipe steps bound undo history independently of bitmap size. Film changes select a compatible paper; unsupported material IDs never cross the native boundary.

One executor owns the Vulkan engine and its cached session. A conflated channel processes the latest requested preview without a fixed delay for discrete selections. Heavy gestures use a 512 px draft and a short coalescing interval; release requests an exact 1024 px preview. Generation checks reject obsolete results. Cancellation signals the native session; queued work and destruction stay ordered. The JNI registry exposes opaque IDs, retains in-flight ownership, and validates dimensions, strides, types and output limits.

Native sessions track applied parameters independently of completed rendered pixels. Only changed fields reach `spk_set_params`: repeating an unchanged film field would invalidate the negative even for a paper-only edit. Failed updates force a fresh source session, and a cancelled readback cannot retain the previous cache identity.

A main-thread LRU retains at most four exact/neutral bitmap pairs and 24 MiB of bitmap allocation. Keys include private source identity, file size/modification time, complete recipe and preview size. It is process-local, so engine/resource changes cannot reuse old entries across app upgrades. Low-priority warm-up considers at most two adjacent films after 700 ms idle, never displacing a full set of user-viewed entries. Backgrounding cancels work and releases the cache. Bitmaps still owned by Compose are not manually recycled.

The last completed preview keeps its own recipe while a new one develops. The UI labels it `Updating…` or `Previous preview`; a failure clears it and exposes the error. Exact cache hits use the same recipe identity. Save requests coalesce independently of rendering and retain revision conflict checks.

The system photo/document picker grants selected content URIs. A bounded stream copies each to a UUID-named private file. LibRaw develops supported RAWs at full size with as-shot WB, no automatic brightening and linear RGB16 ProPhoto. The NDK ImageDecoder decodes ordinary images into client-owned RGBA_F16 sRGB memory at the requested size. Header dimensions, byte counts and memory admission are checked before large work. Untrusted display names are never used as filesystem paths.

An AtomicFile index owns named projects, photo metadata/recipes, presets and export-queue snapshots. A Mutex serializes changes. Revision numbers reject stale preview writes, and an absent indexed photo cannot be resurrected by a late render. Version 0.1/0.2 last-edit storage migrates without deleting the private original. Files are deleted only after the updated index is committed. Limits bound index bytes, photo/project/preset/job counts and import size. There is no gallery scan, remote resource loading, analytics, account, server or WebView. The APK supplies all native resources.

## Adjustments and composition

Exposure compensation, auto exposure, grain and halation use the upstream physical engine parameters. Temperature/tint, contrast, saturation and highlight/shadow controls form a scan-tone stage adapted from the upstream desktop reference. Android applies this subset to encoded sRGB with the same native function for preview and export, before output quantization. HDR permits values above SDR white through this stage. It does not claim parity with the desktop's ProPhoto working-space grade.

The native adapter retains one ungraded scan. Scan-tone-only edits reuse it, avoiding a spectral re-render and avoiding cumulative grading of an 8-bit preview. A changed source or physical recipe invalidates it. Cancellation is checked during scan conversion; all cached texture ownership stays on the serial engine dispatcher.

On Android 13 and newer, six scan-tone sliders use an AGSL drag preview of the retained neutral bitmap with the native scan-tone equations. The `Fast preview` label is explicit: its 8-bit input cannot recover values already clipped at the display boundary. Release replaces it with the authoritative native float result. A shader capability check falls back to native drafts on unsupported devices. Exposure, grain, halation and physical film changes always run through the spectral engine. Export never uses the shader or display cache.

Slider gestures preview draft recipes and commit one history entry on release. Crop-sheet changes remain local until Apply crop; Cancel discards them. Serialized defaults preserve edits made by version 0.1.

Crop rectangles refer to the original source. Aspect presets fit inside that rectangle; an active film frame owns the physical gate ratio. Straightening shrinks the effective crop until every rotated corner lies inside the source. The engine then turns and flips the crop, before producing the physical scan. Geometry parameters are shared by preview and export.

## Light Table

Each project has a separate AtomicFile layout: normalized film positions, z-order, rotation, lamp brightness, page and loupe settings. Layout writes have a mutex/revision guard and never write the photo index or export queue. Controls wait for the initial disk load; background cancellation cannot replace a saved layout with defaults.

The native Compose Canvas draws the project's actual edited photographs through FilmEngine.renderSource. At most eight 512-pixel board renders and one 1536-pixel loupe detail are retained. A serial engine, cancellation and generation checks reject stale page/project results. Leaving the screen or backgrounding drops these feature-owned bitmaps and closes its engine; returning reloads the current page.

Drawing, film hit testing and loupe sampling share the same translation/rotation geometry. The lens clips and magnifies that transformed scene around its own board coordinate, substituting the higher-resolution render for the photo under its center. The board image remains a correctly sampled fallback while detail develops. Lens/handle drags take priority within its hit region; film drags elsewhere select and bring the film forward. Double-tap opens the existing editor and returns to the preserved table layout.

The table's lamp affects its display only. Recipes, physical profiles, preview/export rendering and output pixels are not changed by lamp settings. A changed recipe clears the editor's old preview synchronously, before debounce; the original fallback is labelled so a new selection cannot masquerade as the prior render.

## Material option artwork

Film cover IDs map explicitly to four bundled transparent cutouts from the existing design, decoded off Main at no more than 512 pixels. Unknown, corrupt or missing resources show an unavailable label; another stock's cover is never substituted. Asynchronous cover state retains its stock/resource identity to reject stale decoding.

Paper option samples use the current private source, full current recipe and each compatible paper through the existing renderSource path at 192 pixels. One short-lived serial engine develops at most three samples and then closes. A generation guard rejects old photo/recipe work; normalizing only the selected paper out of the request key avoids regenerating the same set after a paper-only selection. The UI independently checks this key before displaying samples. Leaving/backgrounding cancels work, closes the engine and clears sample bitmaps. Failures clear stale pictures and expose Retry samples. This display feature does not change physical profiles or export algorithms.

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
