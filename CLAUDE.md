# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

SatScream is a single-module Android app (Kotlin) that shows the live Bitcoin price and fires customizable "pump"/"dump" price alerts. Package `com.peerloomllc.satscream`, minSdk 24, compile/target SDK 36.

## Build & run

Uses the Gradle wrapper with a version catalog (`gradle/libs.versions.toml`); AGP 8.13.2, Kotlin 2.0.21.

```bash
./gradlew assembleDebug          # build debug APK
./gradlew installDebug           # build + install on connected device/emulator
./gradlew assembleRelease        # minified release (R8 + shrinkResources, proguard-rules.pro)
./gradlew lint                   # Android lint
./gradlew test                   # local JVM unit tests
./gradlew connectedAndroidTest   # instrumented tests (needs a device/emulator)
./gradlew :app --tasks           # list module tasks
```

There is currently no meaningful test suite — only the default template `junit`/Espresso scaffolding. `local.properties` (SDK path) and `app/release/` are untracked build artifacts.

## Architecture

**The app is built on the traditional Android View system (XML layouts + `AppCompatActivity` + `findViewById`), not Jetpack Compose.** Compose is enabled in `app/build.gradle.kts` and `ui/theme/` (Color/Theme/Type.kt) contains Compose scaffolding, but **none of it is used** — these are leftovers from the Android Studio template. Add UI as XML layouts under `res/layout/`, not `@Composable`s.

**`BitcoinService` is the heart of the app.** It's a foreground service (`foregroundServiceType=dataSync`, `START_STICKY`) that runs an infinite coroutine loop polling the price every 60 seconds, with OkHttp:
- Primary API: CoinGecko (`/simple/price`); on any failure/parse-error it falls back to the Coinbase spot API. See `tryPrimaryApi`/`trySecondaryApi`.
- On each successful fetch it writes price + timestamp to SharedPreferences, refreshes the ongoing notification, updates widgets, and evaluates alert thresholds.
- Alert sound plays via `MediaPlayer` (custom file from internal storage, else `pump.wav`/`dump.wav` from assets). The alert NotificationChannel is deliberately silent (`setSound(null, null)`) so the custom sound isn't doubled.

**SharedPreferences (`"BitcoinPrefs"`, `MODE_PRIVATE`) is the single source of truth and the de-facto IPC bus** between the service, activities, and the widget. There is no database, ViewModel, or bound-service IPC. Consequences to respect:
- `MainActivity` does **not** fetch the price itself — it polls `BitcoinPrefs` every 1 second (`startPriceMonitoring`) and renders whatever the service last wrote.
- `BitcoinWidget` (an `AppWidgetProvider`) likewise reads price/mode/theme straight from `BitcoinPrefs` and renders a `RemoteViews`. Call `BitcoinWidget.updateAllWidgets(context)` after changing any pref that affects display.
- Pref keys are declared as constants in `BitcoinService.companion` but most call sites use **raw string literals** (`"TARGET_PRICE_PUMP"`, `"BITCOIN_STANDARD_MODE"`, `"CUSTOM_PUMP_AUDIO_PATH"`, etc.). When adding/renaming a key, grep across all files — there is no shared constants object.

**Two display/alert modes, toggled by tapping the price (`tvPrice`):**
- *Fiat mode* — USD per BTC. Pump = price ≥ target; dump = price ≤ target.
- *Bitcoin Standard mode* (`BITCOIN_STANDARD_MODE` pref) — sats per dollar = `100_000_000 / price`. **The comparison logic inverts:** pump = sats/$ ≤ target (BTC up), dump = sats/$ ≥ target (BTC down).
- Alerts remember the mode they were set in (`*_ALERT_IS_BITCOIN_MODE`) so the status text converts correctly when the user later switches modes. Alert "triggered" flags (`*_ALERT_TRIGGERED`) latch and reset with a ±1% hysteresis band to avoid repeated firing. This dual-mode + conversion logic is duplicated across `BitcoinService` (firing) and `MainActivity` (display) — keep them in sync.

**Activity map** (registered in `AndroidManifest.xml`):
- `WelcomeActivity` — shown once on first launch (`WELCOME_SHOWN` pref), then `MainActivity` becomes the entry point.
- `MainActivity` — price display, dark-mode toggle, mode toggle, and the numeric bottom-sheet (`bottom_sheet_price_input.xml`) for setting pump/dump targets.
- `AudioSettingsActivity` — pick custom alert sounds; copies the picked file into `filesDir`, enforces ≤10 MB and ≤5 s limits.
- `AboutActivity` → `WebViewActivity` — the WebView loads a bundled local asset (`assets/use-lightning-network-modified.html`), not remote content.
- `BootReceiver` — restarts `BitcoinService` after `BOOT_COMPLETED`.

**Source layout quirk:** several files (`BitcoinService.kt`, `Audiosettingsactivity.kt`, `BootReceiver.kt`, `AboutActivity.kt`, `WebViewActivity.kt`, `Welcomeactivity.kt`, `BitcoinPrice.kt`) live flat in `app/src/main/java/` yet declare `package com.peerloomllc.satscream`, while `MainActivity.kt`, `BitcoinWidget.kt`, and `ui/theme/` sit in the matching `com/peerloomllc/satscream/` directory tree. Kotlin doesn't require the directory to mirror the package, so both compile — don't be surprised that filenames and on-disk paths don't line up with packages.

Dark mode is a manual app preference (`DARK_MODE` pref + `AppCompatDelegate.setDefaultNightMode`), not the system setting; toggling it recreates the activity.

## Conventions

- The repo is **not** a PeerLoom P2P project, so the `/home/tim/peerloomllc/CONSTITUTION.md` tiers/proposals and the p2p-wiki do not apply here.
- Commit style in history: terse, often leading-dash bullet summaries; version bumps are their own commits (`versionCode`/`versionName` in `app/build.gradle.kts`).
- `docs/` is the GitHub Pages site (privacy policy + assets), unrelated to app code.
