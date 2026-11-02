# Changelog

## 0.3.0-alpha.1

- Add persistent named projects with multiple private originals and a thumbnail grid.
- Add selection, custom presets, copy/paste settings and optional composition transfer.
- Add serial batch exports with durable recipe snapshots, cancellation, resume and per-photo retry.
- Migrate the previous last edit without deleting its private source.
- Add full-sensor RAW development through pinned LibRaw 0.22.2 and linear RGB16 ProPhoto input.
- Add explicit 2560 px, 4096 px and Original export sizes, with memory admission and no silent Original downsampling.
- Add real RGB16 TIFF output directly from the float scan, with an embedded sRGB profile.
- Add Android 14+ Ultra HDR JPEG with a gain map recomputed from edited SDR/calibrated EDR print output.
- Keep standard JPEG/PNG encoding in native memory and release full sessions between batch jobs.
- Raise the minimum to Android 11 for the native image decoder/compressor.

S25 Ultra hardware, camera RAW variants and HDR gallery presentation still require device testing. Original resolution can be rejected by memory or GPU limits. Camera gain-map preservation and background-service processing are not implemented.

## 0.2.0-alpha.1

- Add Adjust and Compose beside the existing material choices.
- Add physical exposure compensation, auto exposure, grain and halation strength.
- Add relative scan temperature/tint, contrast, saturation, highlight and shadow controls.
- Retain the ungraded 16-bit scan for reversible, inexpensive scan-tone updates.
- Add free crop with draggable corners, crop movement and accessible size sliders.
- Add landscape/portrait aspect presets, quarter turns, horizontal/vertical flips and ±15° straightening.
- Fit straightened crops inside the source and preserve an active film frame's physical gate ratio.
- Commit a slider gesture as one undo step, with per-control and group reset.
- Preserve neutral defaults when reading edits from the first APK.

Processing remains offline. The first-build export budget and sRGB output limits remain in place. This release does not claim device validation on a Galaxy S25 Ultra.

## 0.1.0-alpha.1

Initial offline Android port: system-picker import, film/paper/frame selection, local upstream Vulkan rendering and JPEG/PNG export.
