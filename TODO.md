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

- [x] Remove Gson — was declared in `app/build.gradle.kts` but never used (JSON is parsed with `kotlinx.serialization`). Removed in #2. `[refactor]` `[small]` `[medium]`
- [x] Remove all of Jetpack Compose — plugin, `buildFeatures.compose`, all Compose dependencies, version-catalog entries, and the unused `ui/theme/` files. UI is 100% XML Views. Removed in #2; `assembleDebug`/`assembleRelease` and Pixel 9 run verified. `[refactor]` `[medium]` `[medium]`
- [x] De-duplicate conflicting dependency declarations — dropped the hardcoded `core-ktx`/`lifecycle-runtime-ktx` lines and bumped the version-catalog entries to those versions (catalog is now the single source, effective versions unchanged). Done in #2. `[refactor]` `[small]` `[medium]`
- [x] Remove the unused `WAKE_LOCK` permission from `AndroidManifest.xml` (declared but never acquired). Removed in #2. `[refactor]` `[small]` `[low]`

### Code Quality / Refactors

- [x] Consolidate the duplicated `formatPrice()` helper into a shared `BtcPrice` util (`formatUsd`/`satsPerDollar`/`formatSatsPerDollar` + `SATS_PER_BTC`), used by `MainActivity`, `BitcoinService`, and `BitcoinWidget`. Verified on Pixel 9. `[refactor]` `[small]` `[medium]`
- [x] Extract the Fiat ↔ Bitcoin-Standard conversion into `BtcPrice`, and merge `MainActivity`'s overlapping `updateAlertStatusDisplay`/`updateAlertStatusDisplays` into one method backed by a per-alert `renderAlertStatus()` helper (also fixes HIT state not rendering after a mode toggle). Verified on Pixel 9. `[refactor]` `[medium]` `[medium]`
- [x] Centralize SharedPreferences keys in a `Prefs` object (file name + all 15 keys); replaced every raw literal and the old `KEY_*`/`PREF_*` constants. String values unchanged (on-disk compatible). Verified on Pixel 9. `[refactor]` `[small]` `[medium]`
- [x] Release `MediaPlayer` on completion — done in both `AudioSettingsActivity.playTestAudio()` (PR #1) and `BitcoinService.playAlertSound()` (self-releasing factory). `[bug]` `[small]` `[low]`

### Widget

- [x] Stop allocating a 400×400 `ARGB_8888` bitmap on every widget update — `BitcoinWidget` now selects a static `res/drawable` shape (`widget_background_light`/`widget_background_dark`, 20dp corners) via `setImageViewResource`. Removes the per-update (~640 KB) allocation. Verified on Pixel 9. `[polish]` `[small]` `[medium]`

### iOS

- [x] Local iOS dev build + install script — `scripts/ios-dev-install.sh` (rsync → xcodegen → xcodebuild over SSH on the Mac mini → devicectl install/launch), mirroring PearCircle. Verified end-to-end on iPhone SE. `[feature]` `[medium]` `[medium]`
- [ ] Local iOS release/upload script — App Store distribution under team `G79ALD29NA` (archive → export → App Store Connect upload), mirroring PearCircle's `release.sh`; replaces the removed `upload.yml` CI. `[feature]` `[medium]` `[low]`

### Investigations

- [x] "iOS widget doesn't show the price" — root cause: the App Group `group.com.peerloomllc.satscream` was never provisioned (the headless wildcard profile can't carry capabilities), so the widget couldn't read the app's shared container. Fixed by migrating the iOS build to XcodeGen + xcodebuild and signing under team G79ALD29NA with the App Group registered (entitlement now present in both the app and `.appex`). Also fixed alert audio (missing `AVAudioSession`) and the two-tone widget background found during verification. Verified on iPhone SE. `[bug]` `[medium]` `[medium]`
