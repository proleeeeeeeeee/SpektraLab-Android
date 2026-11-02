# Version 0.5: five-feature acceptance

## Material library

The catalog contains 11 films and nine print materials, including compatible color papers and motion-picture print films. Existing valid upstream data and documented derived identities are retained; no profile arrays were edited to manufacture differences. Six stocks have verified packaging artwork; other stocks use their own clearly named cards. Descriptions and material comparisons are available in the editor.

All 60 compatible native render/export combinations passed. The later integrated run retained those results rather than repeating the unaffected matrix. Actual selector pictures, sample identity, failure/retry, scrolling and the original main-preview regression passed again.

## Light Table culling

Stars, keep/reject flags, colors, picks and filters persist in the separate per-project Light Table index. Old asynchronous photograph saves cannot overwrite marks. Filter display positions preserve the original board arrangement, paging and photograph numbers. Batch export receives an explicit frozen ID set; empty culling selections cannot mean export-all.

Real-photo UI acceptance covers marking, filtered hit testing, exact-ID export, empty scopes, layout/paging, editor return, persistence and background recovery. Five window/navigation configurations and two landscape interaction checks passed.

## Contact sheets

JPG/PDF exports use immutable photo/recipe/review snapshots, original numbers and optional ratings. Rendering is serial and bounded; PDFs split after 24 cell resources per part. Handled cancellation/failure rolls back staged and published partial-group outputs, and interrupted recorded requests can be retried.

Real-photo evidence used three fixtures repeated across 13 entries: 55 cell comparisons and 11 visually reviewed output pages, including portrait PDF, landscape JPG, filtered selection and retries. Final output/publication/cancel/background regressions passed. A separately rendered actual PDF shows the nondefault G5 recipe caption clearly.

## Black-and-white paper grades

Seven pinned TH-KG3 fitted M/Y/exposure packs provide grades 00, 0, 1, 2, 3, 4 and 5 for compatible black-and-white recipes. Old recipes default to grade 2. This control is independent of scan contrast; color recipes ignore the stored grade.

Five device tests passed: real ramp differences across all grade pairs, cold/warm/PNG equality, color compatibility and real-photo sample/persistence/background/recreation. Real grade 00, 5 and restored 4 screenshots were viewed. The fitted pack is not a calibration for every film stock or laboratory.

## Negative captures

The independent module preserves original JPEG/PNG bytes, samples an actual source-pixel patch with EXIF/rotation mapping, removes the sampled base cast through creative density inversion, and supports exposure, temperature, tint and serial same-roll export snapshots. The current roll is bounded to 64 captures and 128 queued jobs; preview/export long edges are 1024/2560 pixels.

Fourteen negative device tests and the production-entry test passed. Actual touch sampling, sliders, rotation, photograph switching, whole-roll PNG export and failed-job UI retry after Activity stop/resume were exercised. Decoded output pixels matched known synthetic inputs; gallery/private source SHA-256 values stayed unchanged. Retry retained its saved recipe; the successful output URI/hash remained fixed, with no duplicate/pending output in the tested cases. Controlled orphan imports are recovered without deleting indexed, queued or unknown files.

## Integrated evidence and limits

The accepted implementation passed 62 core tests and 24 affected Android tests, plus the earlier five black-and-white device tests and 60-combination material matrix. Lint has zero errors and 14 baseline warnings. Full-source and install archives are distributed separately; the source archive includes pinned dependency contents and licenses.

Tests used an API 35 x86_64 emulator, not S25 Ultra hardware. Negative inversion is an 8-bit sRGB creative workflow, with no RAW/16-bit/HDR or calibrated reconstruction. Only known synthetic negatives were available; real-film/camera/backlight validation and full 64-capture stress were not performed. Contact-sheet 13 entries repeat three real photographs, not 13 unique sources or 2000 actual imports.

No real OS process-kill test was performed. A forced kill between shared MediaStore insertion and persistence of its pending URI can leave an untracked pending row; this is not claimed fixed. Normal injected failure/cancel and persisted-URI recovery passed.
