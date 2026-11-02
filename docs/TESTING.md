# Testing

## Version 0.3.0-alpha.1 validation: 2026-11-02

- Eighteen core tests cover recipe compatibility/geometry, optional composition transfers, preset/queue serialization, format names and input bounds.
- Nine Android instrumentation tests run the real Vulkan engine on API 35 SwiftShader, with no skipped cases.
- A generated 3000 × 64 RGB16 PNG exports at Original width 3000 in JPEG, PNG, TIFF16 and Ultra HDR, beyond the old 2560 px limit.
- TIFF checks read actual BitsPerSample, ICC and unsigned samples: more than 256 distinct levels and values that are not an 8-bit-to-16-bit expansion.
- Ultra HDR checks reopen the JPEG, find a gain map with spatial variation and highlight ratios above 1, and verify gain-map preservation after encoding.
- All four formats publish complete MediaStore rows with their actual MIME types and unchanged encoded bytes.
- A generated uncompressed Bayer DNG, with no JPEG thumbnail, is recognized as RAW, developed at 128 × 96 and rendered into RGB16 TIFF from its full sensor pixels.
- Project tests restore multiple photos, presets, copied settings and a RUNNING queue from disk, retain target composition, and reject stale writes or resurrection after deletion.
- The batch workflow imports two photographs, copies settings, saves a preset, cancels/resumes and verifies exactly one published PNG per job.
- Existing material/frame, adjustment/composition, privacy, storage and Activity-recreation workflows remain covered.
- An unsupported monochrome HDR export keeps its error visible after the automatic preview refresh.

This is emulator validation, not a measurement of S25 Ultra RAW compatibility, Adreno memory limits or HDR panel/gallery appearance. No desktop color-parity claim is made. Original is subject to explicit memory/GPU limits; 50 MP/200 MP full-size processing is not guaranteed. The camera gain map is not retained as a separate scene-linear HDR source path. Standard PNG/JPEG remain 8-bit; RAW is input only. Keep the app open for processing and resume unfinished batch jobs after reopening it.

## Version 0.2.0-alpha.1 validation: 2026-11-02

- Fifteen core tests passed, including legacy recipe decoding, input bounds, crop corner containment at ±15°, physical gate ratios and transform serialization.
- Four Android instrumentation tests passed on the API 35 SwiftShader emulator.
- Native tests verified reversible scan-tone grading from the retained original scan, neutral reset, true desaturation, physical exposure/grain changes, swapped output dimensions after rotation and straightened/flipped film gates.
- The Compose workflow moved the exposure slider, undid the gesture, rotated, applied a free crop, selected paper/frame, and exported a decodable PNG.
- Version code increases to 2 and the existing application ID/signing key are retained, allowing an in-place upgrade from the first test APK.

Galaxy S25 Ultra hardware validation is still pending. Export input remains bounded to 2560 px and encoded sRGB; RAW, HDR and full-resolution output are not added in this release.

## Version 0.1.0 validation: 2026-11-02

- Nine core unit tests passed.
- The real Vulkan engine smoke test passed on an Android 15 / API 35 x86_64 AOSP emulator using SwiftShader.
- Both Android storage and Compose workflow tests passed, including JPEG/PNG decoding, private-import recovery after deleting the original URI, frame selection, undo/redo and PNG export.
- Android lint completed with zero errors. The remaining warnings recommend newer dependency versions, ChromeOS ABI support and a Kotlin convenience extension.
- The arm64 debug APK compiled successfully. Its signature, ZIP alignment, all three native libraries' 16 KB ELF load alignment, required notices and engine assets were verified.
- Native libraries were checked for embedded local Windows account paths.
- The APK manifest contains only AndroidX's app-private receiver permission; it requests no Internet, media-library, storage or location access.
- Synthetic 135, filed 135, 120 and XPan render files were visually inspected.
- Manual emulator review imported a 4068 × 6102 JPEG through the actual system picker, rendered a portrait 135 strip and saved a complete 2472 × 2672 JPEG at the export budget. The personal review photograph is not included in this repository or public release assets.

These historical version-0.1 results establish an initial working emulator port, not Galaxy S25 Ultra driver/performance validation. That APK deliberately retained the 2560 px export-input budget.

## Automated checks

Core unit tests cover compatible film/paper selection, history boundaries, frame/crop geometry, invalid input rejection, 200 MP downsampling policy, native color/edge parameters, black-and-white calibration and safe export filenames.

Android instrumentation runs the bundled upstream Vulkan engine on synthetic gradients. It renders the exposed films, compatible papers and each physical frame; checks opaque, varied output and stock-dependent pixels; and writes synthetic frame images for inspection.

The Compose workflow test imports a MediaStore photograph through the same import boundary as the picker, changes paper and frame, exercises undo/redo, exports PNG, decodes the saved file, checks requested permissions and recreates the Activity.

Run these on a connected Vulkan-capable device:

    ./gradlew :core:test :app:lintDebug :engine:connectedDebugAndroidTest :app:connectedDebugAndroidTest

The system picker's own UI is provided by Android. The automated workflow supplies a granted MediaStore URI; manually verify the picker and Samsung gallery integration on the target phone.

## Galaxy S25 Ultra checklist

The developer does not yet have a connected S25 Ultra. Compilation or an emulator run does not establish Adreno driver compatibility, phone render latency, heat or peak memory.

- Install the arm64 APK and launch without a network connection.
- Import a standard Samsung JPEG and HEIC; check orientation and colors.
- Import a large 50 MP / 200 MP photograph and verify bounded processing.
- Switch every film and compatible paper, including color → Tri-X → color.
- Inspect 135 perforations and edge lettering, 120 6×6 and XPan.
- Drag the photograph inside a frame; undo and redo the framing.
- Hold original and release; check that edited preview returns.
- Save JPEG, PNG, RGB16 TIFF and Ultra HDR. Check MIME/size, film edges and HDR rendering in a compatible viewer; TIFF may require a separate viewer.
- Try an S25 Ultra Expert RAW DNG. Record camera mode/compression and error details if unsupported; never accept a thumbnail as a RAW-development success.
- Check 2560/4096/Original with a standard photo; oversized Original jobs should explain the memory/GPU limit without silently shrinking.
- Create projects and presets, copy settings with/without composition, select multiple photographs and batch export. Force-stop during a batch, reopen and Resume; published jobs must not duplicate.
- Cancel an export; verify that no incomplete gallery item remains.
- Rotate, leave/reopen and relaunch the app; check last-edit recovery.
- Compare phone output against desktop output using the same input, recipe and size before making claims of visual parity.

Device checks remain required even when all automated tests pass.
