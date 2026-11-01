# Privacy

The app processes photographs on the device. Its manifest requests no Internet, broad media, storage, location or camera permissions.

The Android photo picker grants access to your chosen photograph. The app copies it into private application storage and retains the current import and last recipe so editing can resume. Importing a replacement removes obsolete private copies. Uninstalling the app removes its private data.

Exports are new files under Pictures / SpektraLab and remain after uninstall. Original EXIF, including GPS location, is not copied into exports. No photograph is uploaded or sent to a computer. The optional source-code link opens a separate browser only when requested.

Cloud backup and device transfer are disabled for private photo and recipe data. There are no analytics, ads, crash-reporting services, tracking SDKs or remote profiles.

This first build has no persistent multi-photo library. Keep the app open during export; Android may terminate background processing. Cancelled or failed exports are removed rather than exposed as complete photographs.
