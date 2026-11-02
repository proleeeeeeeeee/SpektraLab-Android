# Changelog

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
