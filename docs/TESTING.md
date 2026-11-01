# Testing

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
- Save JPEG and PNG, open them in Samsung Gallery and check the film edge.
- Cancel an export; verify that no incomplete gallery item remains.
- Rotate, leave/reopen and relaunch the app; check last-edit recovery.
- Compare phone output against desktop output using the same input, recipe and size before making claims of visual parity.

This build does not claim full-resolution processing, HDR preservation, RAW decoding or color parity with a calibrated desktop display.
