# Changelog

## 0.5.0-alpha.1

- Expand the catalog to 11 films and nine print materials using existing valid upstream profiles, with provenance, descriptions, comparison and verified artwork or explicit named cards.
- Add persistent Light Table stars, keep/reject flags, color labels, picks, filters and exact-ID batch-export scopes.
- Export paginated JPG/PDF contact sheets from frozen real-photo, recipe and review snapshots, with bounded rendering, publication rollback and retry.
- Add fitted black-and-white paper grades 00–5, default-grade migration and preview/sample/export agreement, independently of scan contrast.
- Add independent real-negative JPEG/PNG import, original-pixel film-base sampling, creative density inversion, exposure/white balance and immutable same-roll serial exports. Preserve original bytes and recover controlled orphan imports.
- Retain the earlier Film/Paper option-art, stale-preview and Light Table fixes. Version code is 7 with the preceding signing identity.

Negative conversion is limited to 8-bit sRGB, 1024 px previews and 2560 px exports. Validation uses known synthetic negatives, not real-film calibration. S25 Ultra hardware, full 64-capture stress and actual process-kill testing remain unverified. The MediaStore-insert-before-pending-URI-journal forced-kill window can still leave an untracked pending row.

## 0.4.1-alpha.1

- Fix the lower Film option pictures by reusing the four existing transparent packaging cutouts, explicitly mapped to each film.
- Replace the identical generic Paper icon with the current photo developed for each compatible paper, with bounded samples and an explicit retry action.
- Guard sample identity across photos/parameters, avoid regeneration on paper-only selection, and release samples/engine on backgrounding or leaving the rail.
- Add resource, actual option-image, touch/scroll, failure/retry and rapid photo/parameter-switch regressions.
- Retain the Light Table and prior stale main-preview fix. Version code increases to 5 with the same application ID and signing identity.

S25 Ultra hardware validation remains pending. Packaging-cutout provenance and separate rights are recorded in LICENSES/Selector-covers-NOTICE.md; no private photo is bundled.

## 0.4.0-alpha.1

- Add a native Light Table for each project, with real edited photographs, backlight on/off and brightness.
- Drag film independently from the loupe, rotate selected film, and arrange up to eight photographs per page.
- Sample the actual transformed photograph through a movable 2x-4x loupe, with a bounded higher-resolution detail render.
- Restore project layouts, lamp and loupe settings across backgrounding and reopening; double-tap a photograph to edit and return to its arrangement.
- Clear stale previews immediately when Film/Paper or another recipe setting changes; label the original while a preview develops or is unavailable.
- Add geometry, persistence, real-pixel, material-selection and Android interaction regressions.

The arm64 test APK retains the previous signing identity and increases version code to 4. S25 Ultra touch, display, driver, camera-file and performance checks remain pending; emulator validation is not hardware validation.

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
