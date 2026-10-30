# Third-party notices

This project is MIT-licensed (see `LICENSE`). The following third-party
components are included under their own licenses:

- **Three.js** (`app/src/main/assets/www/three.module.js`,
  `three.core.js`, and the `addons/` loaders/controls): MIT License,
  Copyright 2010-2025 Three.js Authors. The full license text is in
  `app/src/main/assets/www/THREE-LICENSE.txt`. Version: three 0.182.0
  (r182), taken from the three npm package and vendored so the scene
  works offline in the WebView.

Everything else in this repository (Kotlin code, the WebView scene script,
and documentation) is covered by this project's MIT license.

Note: the original app's Marhey font (SIL Open Font License 1.1) was
carried in v2.0.0–v2.0.2 and dropped in v2.0.3, because the Material 3
version in the Compose BOM made `Typography`'s constructor internal and
a global default font could no longer be wired in. No Marhey files
remain in this repository.
