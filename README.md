# Roblox Avatar Viewer

An unofficial Android app that looks up a Roblox username, fetches that
user's 3D avatar model, and renders it in an orbitable Three.js scene —
with a background color picker and a capture/share action.

> **Not affiliated with Roblox Corporation.** This app is not made by,
> endorsed by, or connected to Roblox. It never asks for a Roblox account
> or password, and it uses only Roblox's public users and thumbnails web
> APIs. Roblox names, avatars, and artwork belong to their owners.

## What it does

1. Enter a Roblox username and tap **Fetch**.
2. An exact username match loads directly. If there is no exact match,
   the app falls back to the users search and asks you to pick from the
   results.
3. The avatar's OBJ/MTL model and textures load from Roblox's CDN into
   the Three.js scene. Drag to orbit, pinch to zoom.
4. Pick a background color, save the username for next time, or capture
   the scene and share it (or save it to `Pictures/Roblox_Avatar`).

## Stack

- Kotlin 2.2.0, Jetpack Compose Material 3, ViewModel + StateFlow,
  DataStore Preferences
- Retrofit 2.11.0 + kotlinx.serialization for the Roblox APIs
- WebView + Three.js r182 (vendored from the three 0.182.0 npm package;
  see `THIRD_PARTY_NOTICES.md`) for the 3D scene — Three.js has no
  native Android equivalent, and Roblox delivers OBJ/MTL, so the
  WebView bridge is deliberate, not a leftover
- AGP 8.13.0, Gradle 8.14.3, Java 17, compile + target SDK 36, minSdk 24
- Version catalog (`gradle/libs.versions.toml`), R8 + resource shrinking
  on release, GitHub Actions CI
- `com.google.android.material` 1.11.0 for the window theme only
  (`Theme.Material3.DayNight.NoActionBar` in `themes.xml`); all UI is
  Compose, see CHANGELOG 2.0.1

## Build

```sh
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

The debug APK lands at `app/build/outputs/apk/debug/app-debug.apk`.
CI runs the same two steps (setup-android@v4, with an explicit
`sdkmanager` install of platform and build-tools 36).

## Tests

JVM unit tests cover the CDN hash mapping, the response DTOs, the
repository against a MockWebServer (exact match, search fallback,
pending, API error envelope, bad model hash, bad field of view), the
ViewModel state transitions, and the capture image decoder. Run them
with `./gradlew testDebugUnitTest`. The instrumented test only asserts
the application ID; a device or emulator run is the gate for the
Compose screen and the WebView scene.

## Live API status — read this before judging a failed run

On 2026-10-30, an unauthenticated browser check of the two endpoints
returned:

- `GET thumbnails.roblox.com/v1/users/avatar-3d?userId=1` →
  `{"errors":[{"code":0,"message":"Invalid authentication data provided"}]}`
- `GET users.roblox.com/v1/users/search?keyword=Roblox&limit=3` →
  `{"errors":[{"code":0,"message":"Allowed values: 10, 25, 50, 100","field":"limit"}]}`

Two things follow. First, the search endpoint is reachable
unauthenticated and validates its parameters (this app always sends
`limit=10`, one of the allowed values). Second, avatar-3d may now
require authentication that the original 2023–2024 app did not need —
or it may behave differently from an Android client than from a
browser. This app surfaces that error message instead of failing
silently, but **a live on-device run is the gate**: if avatar-3d still
rejects unauthenticated requests from the app, the feature needs an
authenticated approach, and no credentials are (or should be) baked
into this code to work around it.

Success response shapes in the code are the documented ones: both user
endpoints return a `data` array, avatar-3d returns a `data` array whose
entry holds `targetId`, `state`, `imageUrl`, and `version`, and the
document at `imageUrl` holds `camera`, `aabb`, `mtl`, `obj`, and
`textures`. Those shapes were not re-verified against a live success
response in this build, because the unauthenticated avatar-3d call
above errored before returning one.

## Privacy

- Usernames you type are sent to Roblox's public APIs to perform the
  lookup — that is the feature.
- Saved usernames are stored only on the device (DataStore).
- Captures are written to the app cache for sharing, or to
  `Pictures/Roblox_Avatar` via MediaStore when you choose to save.
- No analytics, no account, no tokens, no keys.

## History

Version 1.0 was a Full Sail course project (`justin-lyons-2312`,
December 2023 – February 2024) in Java with Views and a WebView scene.
This repository is the 2.0.0 modernization built from that code. The
original project's history is not preserved here: every commit in it
carried a student email address, so the public copy is a single
squashed commit under `enonymous1`. The original zip is otherwise
untouched. Behavior kept from v1: the username → avatar → orbit →
background color → capture/share flow, the original Material 3 palette
(seed `#247BA0`) and the launcher icons. The original Marhey font was
dropped in 2.0.3 (Material 3 1.4 made `Typography`'s constructor
internal, so a global default font can no longer be constructed).
Behavior fixed or dropped is listed in the [Changelog](CHANGELOG.md).
