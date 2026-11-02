# Privacy

The app processes photographs on the device. Its manifest requests no Internet, broad media, storage, location or camera permissions.

The Android photo/document picker grants access to your selected photographs. The app copies them into UUID-named private files. Projects, recipes, thumbnails, presets and export-queue snapshots are stored in private application storage. Delete photographs or a project from the app to remove those private copies and edits. Uninstalling removes all private data. Copy settings uses app memory, not the system clipboard.

Photograph exports are new files under Pictures / SpektraLab; contact sheets are new files under Downloads / SpektraLab. These published files remain after uninstall. Original EXIF, including GPS location, is not copied into exports. No photograph is uploaded or sent to a computer. The optional source-code link opens a separate browser only when requested.

Cloud backup and device transfer are disabled for private photo and recipe data. There are no analytics, ads, crash-reporting services, tracking SDKs or remote profiles.

Keep the app open during export; Android may terminate background processing. Batch queues are saved before work and after each job. Completed exports remain, and pending jobs can be resumed. Incomplete MediaStore rows are removed on cancellation/failure or before that job is retried. Queue recovery checks an already published row before retrying, avoiding duplicate completed exports.

Generated export files are private temporary cache files and are removed after publication or a handled failure. Android may reclaim cache data. No original URI or user-provided display name is used as a private filesystem path. Limits apply to import bytes, source dimensions, index size, photo counts and processing memory.

The separate negative-scan workflow preserves byte-identical private source files and a roll/settings/job index. It accepts selected JPEG/PNG captures and does not upload them. Controlled orphan import files are cleaned without deleting indexed or queued sources or unknown files.

Cleanup and queue recovery cover handled failures/cancellations and recorded pending URIs. If the process is forcibly killed after MediaStore insertion but before its URI is recorded, an untracked pending row can remain. This narrow crash window is not claimed fixed.
