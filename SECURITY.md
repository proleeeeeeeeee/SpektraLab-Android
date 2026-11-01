# Security

Do not post personal photographs, private file paths, signing credentials or access tokens in public issues. Report reproducible, non-sensitive defects through this repository's issue tracker; use GitHub private vulnerability reporting when available for exploitable issues.

The app has no Internet permission or external service credentials. Import is limited to picker-granted content URIs, bounded byte counts and bounded decoded dimensions. Display names do not become paths. Native handles, bitmap formats and strides are checked. Incomplete MediaStore exports are deleted.

Only bundled, trusted resources and fonts are accepted by the native engine. This app does not expose the engine to network or intent-supplied profiles. Keep dependencies and the upstream pin reviewed together, and verify actual Vulkan output after upgrades.

The first release is a debug test APK. It is intended for device evaluation; it is not a hardened production release.
