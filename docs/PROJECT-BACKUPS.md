# Offline project backups

Open **Settings > Project backups**. **Create backup** opens Android's document
picker so the user chooses the destination. No account or application network
service is required. Choose a local provider for an offline copy.

The archive contains all photo projects, saved recipes and presets, photo ratings,
keep/reject flags, colour labels, picks, filters and light-table positions. Original
photo files are included by default. The ZIP is not encrypted: keep it in a trusted
location. Negative-conversion rolls, generated exports and pending export jobs are
not included. Existing export jobs on the restoring device are preserved.

Without originals, the archive stores source identities and SHA-256 fingerprints.
Restore succeeds only while byte-identical source files remain in the application's
current photo library. A metadata-only archive cannot recover deleted originals or
move a library to an empty device. Include originals for device migration.

## Restore and conflicts

**Restore backup** asks for confirmation before opening the file picker. Validation
checks the supported format version, exact ZIP entry list, paths, sizes, every
SHA-256 checksum, project/recipe identities and layout references. Validation uses
private staging and does not modify live projects. Restore creates fresh project,
photo and preset identities, remaps marks/layouts to those identities and adds
`(restored)` to project names. Existing projects are never replaced. Repeating a
restore intentionally creates another set of copies.

Source files and layouts are installed before one atomic photo-index write. An I/O
failure rolls back only newly installed files. All PhotoStore instances use a shared
mutex and read the current atomic index, so a later queue update from another store
instance cannot discard restored projects.

Keep the app in the foreground. Cancel is available during validation/creation;
the final installation is short and non-cancellable. Rotation retains the job.
Leaving the activity cancels unfinished work. A destination provider that cannot
delete an incomplete backup is reported explicitly; remove that file manually.
Force-stop/power-loss recovery of staging/orphan files is not a durable background
job guarantee.

## Bounds

Per original: 512 MiB; original/archive data: 16 GiB; one metadata entry: 4 MiB;
combined metadata: 16 MiB. A minimum 64 MiB storage reserve is retained during
validation. The current library limits also apply after merging: 100 projects,
2,000 photographs and 200 presets. Restore requires room for the compressed ZIP
and validated source copies. Oversized, corrupt or unsupported archives fail before
they become visible as projects. Older applications without this feature cannot
restore these backups.
