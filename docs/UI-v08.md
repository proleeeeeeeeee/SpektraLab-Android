# v0.8 darkroom interface

The English interface uses a neutral dark canvas, restrained warm selection accents, a 4:5 photo-library grid, and larger Film/Print choices. The grid crops display thumbnails only; imported originals and editing geometry are unchanged. Film covers retain their explicit material IDs. Print samples still render the current photograph through the native engine.

Crop, Film, Print and Adjust open transactional panels. Apply commits the tool's result; Cancel restores its starting recipe. Leaving an edited photograph offers Save and close, Discard or Keep editing. Undo/redo remain bounded recipe operations. Finish separates the final frame controls from editing and exposes one primary Export photo action. Landscape uses a side panel; large text can scroll the options while keeping export visible.

The native adapter identifies the photograph gate from the upstream frame geometry. It retains the edited photograph and replaces surrounding film pixels with a stable frame render. The native scan-tone stage applies only within the photo gate. Preview and PNG/JPEG, TIFF and HDR export use the same gate logic. A synthetic regression verifies unchanged frame-edge pixels and changed photograph pixels in native preview and PNG export; it does not establish every film/frame/format combination.

## Verification

- 65 core tests passed; the app JVM unit-test task has no source tests.
- Eight focused Android 35 emulator regressions passed: photo workflow (2), preview experience (1), light-table workflow (2), window workflow (1), material browser (1), and Frame preview/export pixels (1).
- The window workflow also passed at 3120x1440/560dpi and 1080x2340/480dpi with font scale 1.6. It checks visible photo area, reachable controls, material details, Finish/export, light-table dragging and editor return.
- Real emulator screenshots were inspected for Photos, editor, Film, Finish, Light table, small/large-text Finish and landscape Finish. Review screenshots use a self-created synthetic scene; the window fixture is also self-created.
- Core tests, Android lint and ARM64 build passed before versioning. Version metadata is rebuilt for publication; the precise publication commit must also pass CI.

## Limits

The redesigned UI has not been installed on a physical S25 Ultra. An Android emulator at the same dimensions is not Samsung One UI validation. Earlier native smoke tests and manual UI checks ran on an S25 Ultra before the visual redesign, using synthetic input only; its test project and exports were removed and the project index was restored byte-for-byte.

A framed 4000x3000 synthetic image was rejected by the render-memory guard for Original and 4096px PNG export on that phone. Original was not silently resized. This UI release does not remove that limit, and full-resolution RAW/HDR, every output format and 64-photo stress paths were not newly validated.

The negative tool retains its 8-bit JPEG/PNG creative-processing limits. No measured real-negative/camera/backlight calibration or new negative colour validation is claimed. The previously documented forced-process-death window between MediaStore insertion and pending-URI persistence remains a limitation.
