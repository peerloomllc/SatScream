# TODO

## Format

Each item: `- [ ] Description` with metadata tags: `[type]` `[complexity]` `[priority]`

**Types:** `feature`, `bug`, `polish`, `refactor`
**Complexity:** `small`, `medium`, `large`
**Priority:** `high`, `medium`, `low`

---

### ANR / Responsiveness

- [x] Move custom-audio import off the main thread — `AudioSettingsActivity.handleAudioSelection()` now runs the file-size read, `getAudioDuration()`, and the file copy inside `withContext(Dispatchers.IO)` via `lifecycleScope.launch`, marshalling Toast/UI back to main. Verified on Pixel 9. `[bug]` `[medium]` `[high]`
- [x] Replace synchronous `MediaPlayer.prepare()` on the main thread in `playTestAudio()` with `prepareAsync()` + `setOnPreparedListener { start() }` (plus completion/error listeners that release the player). (The same call in `BitcoinService` is fine — it's on `Dispatchers.IO`.) Verified on Pixel 9. `[bug]` `[small]` `[high]`
- [x] Replace the 1-second main-thread polling loop in `MainActivity` with an event-driven `SharedPreferences.OnSharedPreferenceChangeListener` (registered in `onResume`, unregistered in `onPause`; UI work marshalled to main via `runOnUiThread` since the service writes from a background thread). Removes per-second main-thread work; reduces jank and battery drain. Verified on Pixel 9. `[refactor]` `[medium]` `[medium]`

### Build & Dependencies

- [ ] Remove Gson — declared in `app/build.gradle.kts` but never used (JSON is parsed with `kotlinx.serialization`) `[refactor]` `[small]` `[medium]`
- [ ] Remove all of Jetpack Compose — enabled (`buildFeatures.compose`, compose plugin, `compose-bom`, `material3`, `activity-compose`, `ui-tooling`) and `ui/theme/` exists, but nothing uses it (UI is 100% XML Views, no `setContent {}`). Shrinks APK and speeds up builds `[refactor]` `[medium]` `[medium]`
- [ ] De-duplicate conflicting dependency declarations — `core-ktx` and `lifecycle-runtime-ktx` are declared both via the version catalog and as hardcoded `implementation("…")` lines with different versions. Keep the catalog as the single source `[refactor]` `[small]` `[medium]`
- [ ] Remove the unused `WAKE_LOCK` permission from `AndroidManifest.xml` (declared but never acquired) — or actually acquire it if alerts must fire during device doze `[refactor]` `[small]` `[low]`

### Code Quality / Refactors

- [ ] Consolidate the duplicated `formatPrice()` helper copy-pasted in `MainActivity`, `BitcoinService`, and `BitcoinWidget` into one shared util `[refactor]` `[small]` `[medium]`
- [ ] Extract the Fiat ↔ Bitcoin-Standard conversion/comparison logic into a shared util — currently duplicated between `BitcoinService` (alert firing) and `MainActivity`, and `MainActivity` has two overlapping methods (`updateAlertStatusDisplay` and the near-identical `updateAlertStatusDisplays`). Merge them to stop the firing/display logic drifting out of sync `[refactor]` `[medium]` `[medium]`
- [ ] Centralize SharedPreferences keys — constants exist in `BitcoinService.companion` but most call sites use raw string literals (`"TARGET_PRICE_PUMP"`, `"BITCOIN_STANDARD_MODE"`, `"CUSTOM_PUMP_AUDIO_PATH"`, …). Move to one shared object so a rename can't silently break the prefs-as-IPC bus `[refactor]` `[small]` `[medium]`
- [ ] Release `MediaPlayer` on completion — add `setOnCompletionListener { it.release() }` to avoid leaking native players. (Done in `AudioSettingsActivity.playTestAudio()` alongside the `prepareAsync` change; still TODO in `BitcoinService.playAlertSound()`.) `[bug]` `[small]` `[low]`

### Widget

- [ ] Stop allocating a 400×400 `ARGB_8888` bitmap on every widget update (`BitcoinWidget.kt:111`) — replace the canvas-drawn rounded background with a static `res/drawable` XML shape used as the layout background. Removes per-update (~640 KB) allocation / GC pressure `[polish]` `[small]` `[medium]`

### Investigations

- [ ] "iOS widget doesn't work" — there is **no iOS code in this repository** (Android-only; the widget is an `AppWidgetProvider` on `RemoteViews`, which cannot run on iOS). Clarify whether there's a separate iOS project to point at, or whether this is a planned port (would require a from-scratch SwiftUI/WidgetKit app — none of the Kotlin is reusable) `[bug]` `[medium]` `[medium]`
