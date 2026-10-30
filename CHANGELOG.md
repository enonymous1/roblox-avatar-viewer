# Changelog

All notable changes to this project are documented here.

## 2.0.0 — 2026-10-30

Modernized standalone export of the 2023–2024 `justin-lyons-2312` project
(see "History" in the README). The original code is not in this
repository's history; this is a single squashed commit under the public
identity.

### Added

- Kotlin + Jetpack Compose Material 3 UI (single screen, edge-to-edge),
  ViewModel + StateFlow, and DataStore saved usernames.
- Retrofit + kotlinx.serialization client with typed DTOs for the users
  search, usernames, and avatar-3d endpoints, replacing hand-rolled
  `HttpURLConnection` calls and `org.json` parsing.
- Structured error handling: Roblox's `{"errors": [...]}` envelope is
  parsed and shown, instead of returning null on any failure.
- Correct avatar-3d parsing: the `data` wrapper is parsed, and the
  thumbnail state is handled (`Completed`, `Pending`/`InReview`,
  `Blocked`) instead of the original lowercase `pending` check.
- WebView scene on Three.js r182 (vendored from the three 0.182.0 npm
  package), with load-error callbacks, model disposal on reload, a
  single render loop, and no hard-coded fallback models.
- Capture flow that saves through MediaStore (API 29+) or shares from
  the app cache through a FileProvider, with no storage permission.
- Background color picker with a fixed, validated palette (no
  third-party color-picker dependency).
- Version catalog, MIT license, GitHub Actions CI (setup-android@v4
  plus an explicit `sdkmanager` install of platform/build-tools 36),
  and R8 + resource shrinking on release.

### Changed

- Package and application ID: `com.example.roblox_avatar` →
  `com.enonymous1.robloxavatar`; app name `roblox_avatar` → "Roblox Avatar".
- Toolchain: AGP 8.13.0 / Gradle 8.14.3 / Kotlin 2.2.0 / Java 17,
  compile + target SDK 36, minSdk 24 (was AGP 8.2.2, SDK 34, Java 8).
- Saved usernames: comma-joined string with substring matching →
  DataStore string set (exact matches, no duplicates).
- WebView hardening: file-URL and universal file-URL access off,
  assets served only through `WebViewAssetLoader`.
- Branch `master` → `main`; tracked `.idea/` files removed.

### Removed

- The JitPack color-picker dependency, the Views layouts, the
  `WRITE_EXTERNAL_STORAGE` permission request, and the original
  template-only tests (replaced by parser, repository, ViewModel, and
  image-saver tests).

### Known limitations

- Not built on a Mac/Windows machine here: this box has no JDK or
  Android SDK, so the build gate is CI plus Android Studio after push.
- The live Roblox run is unverified: see "Live API status" in the README.
