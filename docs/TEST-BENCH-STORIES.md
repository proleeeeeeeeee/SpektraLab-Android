# Test bench and recipe stories

## Interface plan

Keep the current neutral darkroom palette: Canvas #0B0D0E, Panel #141718,
Raised #202425, text #F4F3EF, secondary text #959B9B and selection #D6B477.
Reuse the existing sans-serif type scale and 48 dp controls. The photo comparison
is the main visual structure: an adaptive grid of actual photographs, one selected
outline and a persistent action area. Material names identify the results; no
decorative film packaging is substituted for a render. The story card keeps the
photograph largest, followed by film/print identity, parameters and a small version
note. Controls and action menus remain outside the exported image.

## Test bench

Open a photograph, choose **Photo actions > Test bench**. Switch between four and
six simultaneous results. The first combination is the current recipe; other
starting combinations use compatible, independently configured catalog materials.
**Edit combination** changes its film, compatible print and black-and-white grade.
All results retain the same source, adjustments, crop, framing and orientation.

Comparisons are real `FilmEngine.renderSource` results at a 448 px input long edge.
They are labelled previews, not original-resolution proofs. Native grain/halation
are unchanged; final export still uses the explicitly chosen output dimensions.
One engine renders serially and closes after finishing. At most six display images,
each bounded to 2 MiB, are retained. Leaving/backgrounding drops those images and
cancels pending work; foregrounding rebuilds the same in-memory combinations.
Photo/recipe generations reject obsolete results. Changes to one combination reuse
other matching images. Failed work has an explicit retry action.

Selecting a result does not edit the photo. **Apply selected recipe** applies one
undoable edit to that exact photograph. **Save preset** stores the selected look
without applying it. Crop remains photo-specific when that preset is reused.
Unsaved trial arrangements are temporary; save a preset before closing the process.
Editor navigation preserves its original Save/Discard baseline across these pages.

## Recipe story and file exchange

**Photo actions > Recipe story** renders the current recipe into a 1280 x 1800 SDR
JPEG card. The photo is fit without distortion and includes its optional independent
Frame. The card lists film, print, supported grade, exposure/auto exposure, white
balance controls, contrast, saturation, highlights/shadows, grain and halation.
The image is newly encoded, so original EXIF/GPS metadata, filename, project name and
private source path are not copied. The photograph itself is intentionally visible.

**Save story card** and **Export recipe file** open Android's CreateDocument picker.
The user chooses the destination. A saved-file dialog offers **Share card/file**;
only that explicit action opens Android's share chooser. The app does not choose a
recipient or upload the file. Import recipe files under **Presets**, review their
names/materials, opt into crop/orientation if wanted, then save as a new preset.
Existing presets are not overwritten, even when names match. Export is also
available on each saved preset.

Recipe JSON is versioned, strict, limited to 64 KiB and contains the complete
`RecipeTransfer` and pinned upstream engine revision. It contains no image or photo
identity. Invalid UTF-8, missing format/version headers, future versions, unknown
fields/materials/enums, incompatible pairs and out-of-range numeric controls fail
before saving a preset. A different engine revision with known controls is readable
but carries an appearance warning. Files cannot supply executable code or profiles.
Composition transfer defaults off on import, regardless of the sender's preference.

Creation is foreground work. Cancellation/failure removes the new partial output;
if a document provider cannot remove it, the user is told to delete it before retrying.
Rotation retains file operations through ViewModels. There is no durable background
export or cross-device colour-calibration promise. Existing project backups include
newly saved/imported presets through the same unchanged library schema.
