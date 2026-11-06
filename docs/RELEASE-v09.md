# SpektraLab 0.9 test release

Feature acceptance for 0.9.0-dev.2 / code 13; publication version 0.9.0-alpha.1 / code 14

Core JVM: 76 tests, 0 failures, 0 errors. Includes 5 new RecipeDocument tests:
complete recipe round trip, opt-in composition, strict version/header/material/field
validation, bounded malformed/range-invalid input, engine-revision warning identity,
and distinct compatible six-combination generation with unchanged geometry/adjustments.

Android emulator-5558: Android 35 x86_64, 1440x3120, density 560, font scale 1.0.
26 distinct affected instrumentation tests passed. After the last compact-grid-only
layout change, the 2 TestBenchStoryWorkflowTest methods were rebuilt and passed again.
Earlier initial 5 feature tests also passed; repetitions are not added to the count.

New feature coverage:
- RecipeStoryOutputTest (3): bounded streaming JSON input; no private source fields;
  read cancellation; photo aspect ratio and card pixels; valid 1280x1800 JPEG with
  no EXIF APP1 segment.
- TestBenchStoryWorkflowTest (2): actual app menu entry; 4/6 real native combinations;
  all six have distinct pixel hashes; bounded retained image bytes; custom print
  material; pause/rebuild preserving combination selection; missing-source failure
  and actual Retry; exact pixel parity with an independently rendered selection;
  saving a preset; applying one undoable recipe; preserving the editor's original
  Save/Discard baseline through feature navigation; source bytes unchanged; rejecting
  application to a different photo identity. Story title edits, real native story
  snapshot, output dimensions, file-picker cancellation/foreground rebuild, unchanged
  photo recipe, unavailable-provider error handling and retaining an output error
  while a foreground preview regenerates.

Existing feature coverage:
- ProjectBackupTest (13): integrity, identities, recipes/presets, marks/layouts,
  optional-original matching, cancellation, rollback and stale-store protection.
- ExportPreflightTest (2), ProjectStorageTest (1), PendingEditsTest (1),
  PreviewExperienceTest (1), PhotoWorkflowTest (2), FrameFinishPipelineTest (1).

Manual real-UI/file acceptance on the same isolated emulator:
- Actual story card saved through Android CreateDocument to Downloads.
- Host independently parsed the saved JPEG: 1280x1800, no EXIF APP1 metadata.
- Explicit Share card opened Android's share chooser and its real image thumbnail.
  No receiving app/person was selected and nothing was sent.
- Recipe JSON saved through the document picker; actual file is 1202 bytes, marked
  SpektraLab-recipe v1 with only format/version/engineRevision/name/settings fields.
- Presets > Import recipe accepted that actual file after confirmation, adding one
  new preset while all photo-project and queue records remained exactly unchanged.
- A version-99 file was rejected with a clear message. A repeat on a settled baseline
  preserved the entire index byte-for-byte. The initial baseline had been taken before
  startup preview saving finished; its only difference was an existing photo revision
  increment, not an imported recipe/project change.
- Final screenshots were actually viewed: all six compact comparisons visible at the
  target emulator size, selected outline/material identity, full story/parameter card,
  import confirmation, successful preset page and unsupported-version message.

Corrections made during review:
- Photo actions opens fully expanded so new entries are easy to reach; it remains
  scrollable for smaller windows.
- Six-mode thumbnail wells are more compact; four-mode retains larger wells.
- Story title offers keyboard Done; action areas retain their existing safe insets.
- Output errors/cancel receipts survive automatic foreground preview regeneration.
- Editor Save/Discard entry recipe now survives navigation away to these pages.

Performance/ownership:
- One serial native engine per feature, closed when rendering completes.
- Comparison input long edge 448 px; six retained bitmaps capped at 2 MiB each,
  <=12 MiB total. The test measured retained bitmap bytes, not total process/GPU PSS.
- Story input long edge 1024 px; fixed 1280x1800 software card. Existing render
  admission checks, original-size export and grain/halation algorithms remain intact.
- Backgrounding discards feature images; immutable recipes/identities reject stale
  async results. Presets use the existing project index and are included in backups.

Final builds:
- Core, Android lint and arm64 debug build passed after the last source change.
- Lint: 0 errors, 18 warnings (7 dependency updates, 2 newer-version suggestions,
  1 Gradle version, 1 ChromeOS ABI, 4 UseKtx suggestions, 3 usable-space suggestions).
- APK arm64-v8a only, 3 native libraries; 16 KiB ELF/ZIP alignment passed.
  Required engine constants/shaders/ICC/licenses present; APK signature verified.
- Signing certificate SHA-256 matches previous public/local builds:
  fe11f93bab228aad5a5e640f18e211f9c4cd52bdffec14d2ac1ce7f6d61fde7e
- Feature acceptance was local. Publication changes only version metadata and documentation; runtime source hashes are checked before packaging. No physical-phone operations were performed.

Not established by this validation:
- Physical S25 Ultra, Samsung One UI, arbitrary GPUs or physical photo colour parity.
- New-feature large-font/landscape/small-window matrix, full RAW/HDR stress or massive
  source libraries. Existing rendering tests cover the unchanged engine paths.
- Unsaved comparison arrangements surviving process death (save a preset instead).
- Exhaustive provider/disk-full, power loss or force-stop partial-output cleanup.
- Delivery to a third-party recipient app; only chooser launch/readable thumbnail was
  exercised. JSON engine-revision warnings do not promise identical future rendering.

The final publication rebuild runs core tests, lint, ARM64 APK and Android test-package compilation. Exact-commit CI, signature/alignment and anonymous asset download verification are recorded in the Release assets. CI debug signing is independent; the distributed APK uses the existing local signing identity.

Export memory evidence: a 2000x1500 synthetic framed PNG retained exactly the same output SHA-256 before and after the row-band change. Frame scratch decreased from 48,336,792 to 4,180,344 bytes; GPU allocation and the main full-resolution readback were unchanged. This is not a claim that arbitrary 50MP/200MP images now fit.

Backups omit negative rolls, generated exports and queued jobs; existing destination queue entries remain intact. Metadata-only restore requires the identical originals already available in the app. Normal rollback is tested, but arbitrary provider failures, disk-full, power loss and forced process death are not exhaustively covered. Negative conversion remains synthetic-input-validated 8-bit JPEG/PNG creative processing, without real-film calibration. Its known MediaStore forced-kill pending-row window remains.
