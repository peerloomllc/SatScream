# App Review Notes (iOS)

Paste into App Store Connect → App Review Information → Notes for each submission.

## 1. Screen recording
Record on the iPhone SE in one ~30–45s take: launch → Continue → tap price to show sats mode → Set Pump/Dump Alert with a target near the current price → **Allow** notifications → show the alert firing.

Permissions context: Notifications only, for the user's own price alerts. No location, contacts, camera, microphone, photos, or App Tracking Transparency; no tracking.

## 2. Devices & OS tested
iPhone SE (2nd generation) running iOS 26.4.2.

## 3. App purpose & audience
SatScream is a free, native iOS Bitcoin price utility built around several iOS-specific surfaces that a website cannot provide:

- **Home Screen widget** (small, medium, and large families) showing the live BTC price, updated on a WidgetKit timeline.
- **Local price alerts** — the user sets custom "pump" (up) and "dump" (down) targets; when the live price crosses a target, the app posts a local notification and plays an alert sound, even while backgrounded.
- **Custom alert sounds** — users import their own audio for pump/dump alerts, stored locally on device.
- **Two display modes** — tap the price to toggle between USD-per-BTC and sats-per-dollar.
- **Manual dark mode.**

For Bitcoin users who want a lightweight, ad-free, no-account price tracker with a Home Screen widget and audible alerts. No account, no login, no paywall, no ads, no tracking.

## 4. Setup & main features
No login or credentials needed — fully functional on first launch.
1. Launch; the live BTC price appears automatically.
2. Add the **SatScream Home Screen widget** (small/medium/large) to keep the live price on your Home Screen.
3. Tap "Set Pump Alert" or "Set Dump Alert" and enter a target.
4. Allow notifications when prompted (used only for the user's own price alerts).
5. When the price crosses a target, the app posts a local notification and plays a sound. (To demo quickly, set a target just past the current price so it fires within one ~60s poll.)
6. Optionally import a **custom alert sound** in Audio Settings.
7. Tap the price to switch between USD and sats-per-dollar.

## 5. External services
Price data from two public, keyless market-data APIs: CoinGecko (primary) and Coinbase (fallback). No authentication, no user data sent — only the public BTC/USD price is read. No analytics, ad, tracking, or third-party SDKs. Local notifications only (no remote push). No in-app purchases or subscriptions.

## 6. Regional differences
None. Behaves identically in all regions; price always in USD; no geo-gating or region-locked content.

## 7. Regulated industry / third-party material
Not applicable. SatScream is an informational price-display utility. It does not buy, sell, hold, exchange, transmit, or custody any funds, and offers no trading or financial services. The price is read from public CoinGecko and Coinbase endpoints (public data, no license required).

## 8. Re: Guideline 4.2 — why SatScream requires a native app
Paste into Resolution Center if rejected under 4.2 (Minimum Functionality).

We appreciate the review and would like to clarify why SatScream's functionality depends on native iOS capabilities that are not possible as a website or web app:

- It provides a **Home Screen widget** in three sizes (WidgetKit), rendering the live price directly on the user's Home Screen — something a website cannot do.
- It delivers **local notifications** for user-defined price alerts, firing from background polling even when the app is closed.
- It plays **custom, user-imported alert sounds** stored locally on the device.
- It offers an offline-capable, account-free native experience with manual dark mode and two display modes (USD and sats-per-dollar).

These are platform-native features that a mobile web page cannot deliver, and they form the core of the app's value to users. We're glad to add any further detail or walk through the functionality on a call.
