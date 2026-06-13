# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

SatScream shows the live Bitcoin price and fires customizable "pump"/"dump" price alerts. This is a **monorepo with two independent native apps** that share no code:

- `android/` — the Android app (Kotlin). Package `com.peerloomllc.satscream`, minSdk 24, compile/target SDK 36.
- `ios/` — the iOS app (Swift Package, built with `xtool`). Widget extension under `ios/SatScream/Sources/SatScreamWidget/`.
- `docs/` — the GitHub Pages site (privacy policy + assets), unrelated to app code.
- The iOS app was consolidated in from the former `peerloomllc/SatScream-iOS` repo (snapshot, no history). `ios/.github/` is that repo's old CI, kept for reference and **inactive** (GitHub only runs workflows from the repo-root `.github/`).

## Android (`android/`)

Run Gradle from `android/` (the wrapper, `build.gradle.kts`, version catalog `gradle/libs.versions.toml`, and `local.properties` all live there). AGP 8.13.2, Kotlin 2.0.21.

```bash
cd android
./gradlew assembleDebug          # build debug APK
./gradlew installDebug           # build + install on connected device/emulator
./gradlew assembleRelease        # minified release (R8 + shrinkResources, app/proguard-rules.pro)
./gradlew lint                   # Android lint
./gradlew test                   # local JVM unit tests
./gradlew connectedAndroidTest   # instrumented tests (needs a device/emulator)
```

There is no meaningful test suite — only the default template `junit`/Espresso scaffolding. `local.properties` (SDK path) and `app/release/` are untracked build artifacts.

### Architecture

**The Android UI is the traditional View system (XML layouts + `AppCompatActivity` + `findViewById`) — there is no Jetpack Compose** (it was removed; add UI as XML layouts under `android/app/src/main/res/layout/`, not `@Composable`s).

**`BitcoinService` is the heart of the app.** A foreground service (`foregroundServiceType=dataSync`, `START_STICKY`) running an infinite coroutine loop that polls the price every 60 seconds via OkHttp:
- Primary API CoinGecko (`/simple/price`); on any failure/parse-error it falls back to the Coinbase spot API (`tryPrimaryApi`/`trySecondaryApi`).
- On each successful fetch it writes price + timestamp to SharedPreferences, refreshes the ongoing notification, updates widgets, and evaluates alert thresholds.
- Alert sound plays via `MediaPlayer` (custom file from internal storage, else `pump.wav`/`dump.wav` from assets). The alert NotificationChannel is deliberately silent (`setSound(null, null)`) so the custom sound isn't doubled. Players self-release on completion (`newSelfReleasingPlayer`).

**SharedPreferences (file `Prefs.FILE` = `"BitcoinPrefs"`, `MODE_PRIVATE`) is the single source of truth and the de-facto IPC bus** between the service, activities, and the widget. No database, ViewModel, or bound-service IPC. Consequences:
- All keys live in the `Prefs` object (`Prefs.kt`) — the one place that defines the on-disk contract. Use it; don't reintroduce raw key literals. Changing a key's string **value** is a data migration, not a rename.
- `MainActivity` does **not** fetch the price — it registers a `SharedPreferences.OnSharedPreferenceChangeListener` (in `onResume`, removed in `onPause`) and refreshes when the service writes a new price or flips an alert flag. The service writes via `apply()` on a background thread, so the callback marshals UI work with `runOnUiThread`.
- `BitcoinWidget` (an `AppWidgetProvider`) reads price/mode/theme straight from prefs and renders `RemoteViews`; its rounded background is a static drawable (`widget_background_light/dark`). Call `BitcoinWidget.updateAllWidgets(context)` after changing any pref that affects display.

**Pricing/formatting lives in the `BtcPrice` object** (`BtcPrice.kt`): `formatUsd`, `satsPerDollar`, `formatSatsPerDollar`, and `SATS_PER_BTC`. Use it instead of re-deriving `100_000_000 / price` or re-formatting.

**Two display/alert modes, toggled by tapping the price (`tvPrice`):**
- *Fiat mode* — USD per BTC. Pump = price ≥ target; dump = price ≤ target.
- *Bitcoin Standard mode* (`Prefs.BITCOIN_STANDARD_MODE`) — sats per dollar = `100_000_000 / price`. **The comparison inverts:** pump = sats/$ ≤ target (BTC up), dump = sats/$ ≥ target (BTC down).
- Alerts remember the mode they were set in (`*_IS_BITCOIN_MODE`) so the status text converts when the user switches modes (see `MainActivity.formatAlertTarget`). "Triggered" flags latch and reset with a ±1% hysteresis band. The firing logic (`BitcoinService`) and display logic (`MainActivity.renderAlertStatus`) are separate paths over the same prefs — keep them consistent.

**Activity map** (`android/app/src/main/AndroidManifest.xml`): `WelcomeActivity` (first launch, gated by `Prefs.WELCOME_SHOWN`) → `MainActivity` (price, toggles, numeric bottom-sheet for targets) ; `AudioSettingsActivity` (custom alert sounds, copies into `filesDir`, ≤10 MB / ≤5 s, all I/O off the main thread) ; `AboutActivity` → `WebViewActivity` (loads bundled `assets/use-lightning-network-modified.html`, not remote) ; `BootReceiver` (restarts the service after `BOOT_COMPLETED`).

**Source layout quirk:** several files (`BitcoinService.kt`, `Audiosettingsactivity.kt`, `BootReceiver.kt`, `AboutActivity.kt`, `WebViewActivity.kt`, `Welcomeactivity.kt`, `BitcoinPrice.kt`) sit flat in `android/app/src/main/java/` yet declare `package com.peerloomllc.satscream`, while `MainActivity.kt`, `BitcoinWidget.kt`, `BtcPrice.kt`, and `Prefs.kt` live in the matching `com/peerloomllc/satscream/` tree. Kotlin doesn't require the directory to mirror the package, so both compile — on-disk paths won't always line up with packages.

Dark mode is a manual app preference (`Prefs.DARK_MODE` + `AppCompatDelegate.setDefaultNightMode`), not the system setting; toggling it recreates the activity.

## iOS (`ios/`)

Swift Package at `ios/SatScream/` (`Package.swift`), built/run with [`xtool`](https://github.com/xtool-org/xtool) — see `ios/SatScream/xtool.yml`. App sources under `Sources/SatScream/`, the home-screen widget under `Sources/SatScreamWidget/`. The `xtool/SatScream.app` build artifact is gitignored.

Per the project's device workflow, iOS builds happen on the mac-mini and install to the USB-connected iPhone.

## Conventions

- Not a PeerLoom P2P project, so `/home/tim/peerloomllc/CONSTITUTION.md` tiers/proposals and the p2p-wiki do not apply.
- Verify changes on a real device before marking work complete (Android: debug APK on the Pixel 9 / TCL over ADB).
- Commit style in history: terse, often leading-dash bullet summaries; Android version bumps are their own commits (`versionCode`/`versionName` in `android/app/build.gradle.kts`).
- `TODO.md` tracks outstanding work, tagged `[type]` `[complexity]` `[priority]`.
