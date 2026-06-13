import Foundation
import AVFoundation
import UserNotifications
import Combine
import WidgetKit

// Mirrors Android's SharedPreferences "BitcoinPrefs" keys exactly
private enum Prefs {
    static let suite             = "group.com.peerloomllc.satscream"  // App Group — shared with widget

    static let welcomeShown      = "WELCOME_SHOWN"
    static let darkMode          = "DARK_MODE"
    static let lastPrice         = "LAST_PRICE"
    static let lastUpdateTime    = "LAST_UPDATE_TIME"
    static let btcStandardMode   = "BITCOIN_STANDARD_MODE"

    static let pumpTarget        = "TARGET_PRICE_PUMP"
    static let dumpTarget        = "TARGET_PRICE_DUMP"
    static let pumpTriggered     = "PUMP_ALERT_TRIGGERED"
    static let dumpTriggered     = "DUMP_ALERT_TRIGGERED"
    static let pumpIsBitcoinMode = "PUMP_ALERT_IS_BITCOIN_MODE"
    static let dumpIsBitcoinMode = "DUMP_ALERT_IS_BITCOIN_MODE"

    static let customPumpAudioPath = "CUSTOM_PUMP_AUDIO_PATH"
    static let customDumpAudioPath = "CUSTOM_DUMP_AUDIO_PATH"
    static let customPumpAudioName = "CUSTOM_PUMP_AUDIO_NAME"
    static let customDumpAudioName = "CUSTOM_DUMP_AUDIO_NAME"
}

@MainActor
class BitcoinViewModel: ObservableObject {

    // MARK: - Published State
    @Published var formattedPrice: String = "Loading…"
    @Published var lastUpdatedText: String = "Last updated: —"
    @Published var isDarkMode: Bool = false
    @Published var isBitcoinStandardMode: Bool = false

    @Published var pumpAlertStatus: String = "No pump alert set"
    @Published var dumpAlertStatus: String = "No dump alert set"
    @Published var pumpAlertTriggered: Bool = false
    @Published var dumpAlertTriggered: Bool = false

    @Published var pumpAudioName: String? = nil
    @Published var dumpAudioName: String? = nil

    // MARK: - Private
    private let defaults: UserDefaults
    private var currentPrice: Double? = nil
    private var timer: Timer?
    private var audioPlayer: AVAudioPlayer?

    // MARK: - Init
    init() {
        // Resolve the shared App Group store. UserDefaults(suiteName:) returns a non-nil
        // instance even when the App Group isn't entitled, so checking it for nil is not a
        // reliable signal. The shared *container URL* is nil unless the App Group is actually
        // provisioned — use that. If it's unavailable, the widget (a separate process) cannot
        // read our data, so fail loudly instead of silently writing to a private store.
        let groupAvailable = FileManager.default
            .containerURL(forSecurityApplicationGroupIdentifier: Prefs.suite) != nil
        if let shared = UserDefaults(suiteName: Prefs.suite), groupAvailable {
            defaults = shared
        } else {
            NSLog("[SatScream] ⚠️ App Group '\(Prefs.suite)' is NOT provisioned — the home-screen widget will not receive price updates. Enable the App Group capability on BOTH the app and the widget App IDs and ensure it is in their provisioning profiles.")
            defaults = .standard
        }
        loadPreferences()
        startPriceTimer()
    }

    // MARK: - Preferences
    private func loadPreferences() {
        isDarkMode          = defaults.bool(forKey: Prefs.darkMode)
        isBitcoinStandardMode = defaults.bool(forKey: Prefs.btcStandardMode)

        let lastPrice = defaults.float(forKey: Prefs.lastPrice)
        if lastPrice > 0 {
            currentPrice = Double(lastPrice)
            formattedPrice = formatDisplay(price: Double(lastPrice))
            lastUpdatedText = defaults.string(forKey: Prefs.lastUpdateTime) ?? "Waiting for update..."
        }
        updateAlertStatusDisplay()

        pumpAudioName = defaults.string(forKey: Prefs.customPumpAudioName)
        dumpAudioName = defaults.string(forKey: Prefs.customDumpAudioName)
    }

    // MARK: - Timer
    private func startPriceTimer() {
        // Fetch immediately, then every 60 seconds (mirrors Android BitcoinService)
        Task { await fetchBitcoinPrice() }
        timer = Timer.scheduledTimer(withTimeInterval: 60, repeats: true) { [weak self] _ in
            Task { await self?.fetchBitcoinPrice() }
        }
    }

    func stopTimer() {
        timer?.invalidate()
        timer = nil
    }

    // MARK: - Price Fetching
    func fetchBitcoinPrice() async {
        // Primary: CoinGecko
        let primaryURL = URL(string: "https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=usd")!
        if let price = await fetchFromCoinGecko(url: primaryURL) {
            await handlePriceUpdate(price)
            return
        }
        // Secondary: Coinbase
        let secondaryURL = URL(string: "https://api.coinbase.com/v2/prices/BTC-USD/spot")!
        if let price = await fetchFromCoinbase(url: secondaryURL) {
            await handlePriceUpdate(price)
        }
    }

    private func fetchFromCoinGecko(url: URL) async -> Double? {
        struct Response: Codable {
            let bitcoin: BitcoinData
            struct BitcoinData: Codable { let usd: Double }
        }
        do {
            let (data, _) = try await URLSession.shared.data(from: url)
            let decoded = try JSONDecoder().decode(Response.self, from: data)
            return decoded.bitcoin.usd
        } catch { return nil }
    }

    private func fetchFromCoinbase(url: URL) async -> Double? {
        struct Response: Codable {
            let data: PriceData
            struct PriceData: Codable { let amount: String }
        }
        do {
            let (data, _) = try await URLSession.shared.data(from: url)
            let decoded = try JSONDecoder().decode(Response.self, from: data)
            return Double(decoded.data.amount)
        } catch { return nil }
    }

    private func handlePriceUpdate(_ price: Double) async {
        currentPrice = price

        // Save to UserDefaults
        let timeStr = "Last updated: \(formatTime(Date()))"
        defaults.set(Float(price), forKey: Prefs.lastPrice)
        defaults.set(timeStr, forKey: Prefs.lastUpdateTime)

        // Update UI
        formattedPrice  = formatDisplay(price: price)
        lastUpdatedText = timeStr

        // Reload widget so home screen price stays current
        WidgetCenter.shared.reloadAllTimelines()

        // Check alerts
        checkAlerts(price: price)
    }

    // MARK: - Display Formatting
    func formatDisplay(price: Double) -> String {
        if isBitcoinStandardMode {
            let satsPerDollar = Int64(100_000_000.0 / price)
            return "\(formatWithCommas(satsPerDollar))/$"
        } else {
            return "$\(formatWithCommas(Int64(price)))"
        }
    }

    private func formatWithCommas(_ value: Int64) -> String {
        let formatter = NumberFormatter()
        formatter.numberStyle = .decimal
        formatter.groupingSeparator = ","
        return formatter.string(from: NSNumber(value: value)) ?? "\(value)"
    }

    private func formatTime(_ date: Date) -> String {
        let fmt = DateFormatter()
        fmt.dateFormat = "hh:mm:ss a"
        fmt.locale = Locale(identifier: "en_US")
        return fmt.string(from: date)
    }

    // MARK: - Alert Checking (mirrors BitcoinService logic exactly)
    private func checkAlerts(price: Double) {
        let pumpTarget = defaults.float(forKey: Prefs.pumpTarget)
        let dumpTarget = defaults.float(forKey: Prefs.dumpTarget)
        let pumpTriggered = defaults.bool(forKey: Prefs.pumpTriggered)
        let dumpTriggered = defaults.bool(forKey: Prefs.dumpTriggered)

        if isBitcoinStandardMode {
            let satsPerDollar = 100_000_000.0 / price

            // Pump: sats/$ <= target (BTC price going up)
            if pumpTarget > 0 && satsPerDollar <= Double(pumpTarget) && !pumpTriggered {
                defaults.set(true, forKey: Prefs.pumpTriggered)
                self.pumpAlertTriggered = true
                let sats = Int64(satsPerDollar)
                sendAlertNotification(title: "PUMP ALERT!", body: "PUMPING! \(formatWithCommas(sats)) sats/$")
                playAlertSound(isPump: true)
            }
            // Dump: sats/$ >= target (BTC price going down)
            if dumpTarget > 0 && satsPerDollar >= Double(dumpTarget) && !dumpTriggered {
                defaults.set(true, forKey: Prefs.dumpTriggered)
                self.dumpAlertTriggered = true
                let sats = Int64(satsPerDollar)
                sendAlertNotification(title: "DUMP ALERT!", body: "DUMPING! \(formatWithCommas(sats)) sats/$")
                playAlertSound(isPump: false)
            }
            // Reset when price moves away
            if pumpTarget > 0 && satsPerDollar > Double(pumpTarget) * 1.01 && pumpTriggered {
                defaults.set(false, forKey: Prefs.pumpTriggered)
                self.pumpAlertTriggered = false
            }
            if dumpTarget > 0 && satsPerDollar < Double(dumpTarget) * 0.99 && dumpTriggered {
                defaults.set(false, forKey: Prefs.dumpTriggered)
                self.dumpAlertTriggered = false
            }
        } else {
            // Fiat mode
            if pumpTarget > 0 && price >= Double(pumpTarget) && !pumpTriggered {
                defaults.set(true, forKey: Prefs.pumpTriggered)
                self.pumpAlertTriggered = true
                sendAlertNotification(title: "PUMP ALERT!", body: "PUMPING! BTC: $\(formatWithCommas(Int64(price)))")
                playAlertSound(isPump: true)
            }
            if dumpTarget > 0 && price <= Double(dumpTarget) && !dumpTriggered {
                defaults.set(true, forKey: Prefs.dumpTriggered)
                self.dumpAlertTriggered = true
                sendAlertNotification(title: "DUMP ALERT!", body: "DUMPING! BTC: $\(formatWithCommas(Int64(price)))")
                playAlertSound(isPump: false)
            }
            if pumpTarget > 0 && price < Double(pumpTarget) * 0.99 && pumpTriggered {
                defaults.set(false, forKey: Prefs.pumpTriggered)
                self.pumpAlertTriggered = false
            }
            if dumpTarget > 0 && price > Double(dumpTarget) * 1.01 && dumpTriggered {
                defaults.set(false, forKey: Prefs.dumpTriggered)
                self.dumpAlertTriggered = false
            }
        }

        updateAlertStatusDisplay()
    }

    // MARK: - Alert Status Display
    func updateAlertStatusDisplay() {
        let pumpTarget = defaults.float(forKey: Prefs.pumpTarget)
        let dumpTarget = defaults.float(forKey: Prefs.dumpTarget)
        let pumpTriggered = defaults.bool(forKey: Prefs.pumpTriggered)
        let dumpTriggered = defaults.bool(forKey: Prefs.dumpTriggered)
        let pumpWasBitcoinMode = defaults.bool(forKey: Prefs.pumpIsBitcoinMode)
        let dumpWasBitcoinMode = defaults.bool(forKey: Prefs.dumpIsBitcoinMode)
        let lastPrice = Double(defaults.float(forKey: Prefs.lastPrice))

        self.pumpAlertTriggered = pumpTriggered
        self.dumpAlertTriggered = dumpTriggered

        // Pump status
        if pumpTarget > 0 {
            if pumpTriggered {
                if isBitcoinStandardMode {
                    let sats = Int64(100_000_000.0 / lastPrice)
                    pumpAlertStatus = "PUMP HIT: \(formatWithCommas(sats)) sats/$"
                } else {
                    pumpAlertStatus = "PUMP HIT: $\(formatWithCommas(Int64(lastPrice)))"
                }
            } else {
                if isBitcoinStandardMode {
                    if pumpWasBitcoinMode {
                        pumpAlertStatus = "Pump alert: \(Int64(pumpTarget)) sats/$"
                    } else {
                        let sats = Int64(100_000_000.0 / Double(pumpTarget))
                        pumpAlertStatus = "Pump alert: \(formatWithCommas(sats)) sats/$"
                    }
                } else {
                    if pumpWasBitcoinMode {
                        let usd = 100_000_000.0 / Double(pumpTarget)
                        pumpAlertStatus = "Pump alert: $\(formatWithCommas(Int64(usd)))"
                    } else {
                        pumpAlertStatus = "Pump alert: $\(formatWithCommas(Int64(pumpTarget)))"
                    }
                }
            }
        } else {
            pumpAlertStatus = "No pump alert set"
        }

        // Dump status
        if dumpTarget > 0 {
            if dumpTriggered {
                if isBitcoinStandardMode {
                    let sats = Int64(100_000_000.0 / lastPrice)
                    dumpAlertStatus = "DUMP HIT: \(formatWithCommas(sats)) sats/$"
                } else {
                    dumpAlertStatus = "DUMP HIT: $\(formatWithCommas(Int64(lastPrice)))"
                }
            } else {
                if isBitcoinStandardMode {
                    if dumpWasBitcoinMode {
                        dumpAlertStatus = "Dump alert: \(Int64(dumpTarget)) sats/$"
                    } else {
                        let sats = Int64(100_000_000.0 / Double(dumpTarget))
                        dumpAlertStatus = "Dump alert: \(formatWithCommas(sats)) sats/$"
                    }
                } else {
                    if dumpWasBitcoinMode {
                        let usd = 100_000_000.0 / Double(dumpTarget)
                        dumpAlertStatus = "Dump alert: $\(formatWithCommas(Int64(usd)))"
                    } else {
                        dumpAlertStatus = "Dump alert: $\(formatWithCommas(Int64(dumpTarget)))"
                    }
                }
            }
        } else {
            dumpAlertStatus = "No dump alert set"
        }
    }

    // MARK: - Set Alerts
    func setPumpAlert(value: Float) {
        defaults.set(value, forKey: Prefs.pumpTarget)
        defaults.set(false, forKey: Prefs.pumpTriggered)
        defaults.set(isBitcoinStandardMode, forKey: Prefs.pumpIsBitcoinMode)
        updateAlertStatusDisplay()
    }

    func setDumpAlert(value: Float) {
        defaults.set(value, forKey: Prefs.dumpTarget)
        defaults.set(false, forKey: Prefs.dumpTriggered)
        defaults.set(isBitcoinStandardMode, forKey: Prefs.dumpIsBitcoinMode)
        updateAlertStatusDisplay()
    }

    func clearPumpAlert() {
        defaults.removeObject(forKey: Prefs.pumpTarget)
        defaults.removeObject(forKey: Prefs.pumpTriggered)
        pumpAlertStatus = "No pump alert set"
        pumpAlertTriggered = false
    }

    func clearDumpAlert() {
        defaults.removeObject(forKey: Prefs.dumpTarget)
        defaults.removeObject(forKey: Prefs.dumpTriggered)
        dumpAlertStatus = "No dump alert set"
        dumpAlertTriggered = false
    }

    // MARK: - Display Format for Input (mirrors Android showPriceInputBottomSheet)
    func formatInputDisplay(_ input: String) -> String {
        guard !input.isEmpty else {
            return isBitcoinStandardMode ? "0/$" : "$0"
        }
        let number = Int64(input) ?? 0
        let commas = formatWithCommas(number)
        return isBitcoinStandardMode ? "\(commas)/$" : "$\(commas)"
    }

    // MARK: - Mode Toggle
    func toggleBitcoinStandardMode() {
        isBitcoinStandardMode = !isBitcoinStandardMode
        defaults.set(isBitcoinStandardMode, forKey: Prefs.btcStandardMode)
        if let price = currentPrice {
            formattedPrice = formatDisplay(price: price)
        }
        updateAlertStatusDisplay()
    }

    // MARK: - Dark Mode
    func toggleDarkMode(_ value: Bool) {
        isDarkMode = value
        defaults.set(value, forKey: Prefs.darkMode)
    }

    // MARK: - Notifications
    private func sendAlertNotification(title: String, body: String) {
        let content = UNMutableNotificationContent()
        content.title = title
        content.body  = body
        content.sound = .default

        let request = UNNotificationRequest(
            identifier: UUID().uuidString,
            content: content,
            trigger: nil
        )
        UNUserNotificationCenter.current().add(request, withCompletionHandler: nil)
    }

    // MARK: - Audio
    func playAlertSound(isPump: Bool) {
        // Route to .playback so the alert is audible even with the ring/silent switch on,
        // and activate the session — without this an AVAudioPlayer often produces no sound.
        let session = AVAudioSession.sharedInstance()
        try? session.setCategory(.playback, mode: .default, options: [])
        try? session.setActive(true)

        let customPathKey = isPump ? Prefs.customPumpAudioPath : Prefs.customDumpAudioPath

        if let customPath = defaults.string(forKey: customPathKey) {
            let file = URL(fileURLWithPath: customPath)
            if FileManager.default.fileExists(atPath: customPath),
            let player = try? AVAudioPlayer(contentsOf: file) {
                audioPlayer = player
                player.play()
                return
            } else {
                // Clean up invalid path
                defaults.removeObject(forKey: customPathKey)
            }
        }

        // Default audio from bundle
        let assetName = isPump ? "pump" : "dump"
        if let url = Bundle.appResources.url(forResource: assetName, withExtension: "wav"),
        let player = try? AVAudioPlayer(contentsOf: url) {
            audioPlayer = player
            player.play()
        }
    }

    // MARK: - Custom Audio Management
    func saveCustomAudio(sourceURL: URL, isPump: Bool) throws {
        let fileName = isPump ? "custom_pump_audio.wav" : "custom_dump_audio.wav"
        let docsDir  = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!
        let dest     = docsDir.appendingPathComponent(fileName)

        // Check duration (5 seconds max, mirrors Android)
        let asset    = try AVAudioPlayer(contentsOf: sourceURL)
        guard asset.duration <= 5.0 else {
            throw AudioError.tooLong(Int(asset.duration))
        }

        if FileManager.default.fileExists(atPath: dest.path) {
            try FileManager.default.removeItem(at: dest)
        }
        try FileManager.default.copyItem(at: sourceURL, to: dest)

        let pathKey = isPump ? Prefs.customPumpAudioPath : Prefs.customDumpAudioPath
        let nameKey = isPump ? Prefs.customPumpAudioName : Prefs.customDumpAudioName
        defaults.set(dest.path, forKey: pathKey)
        defaults.set(sourceURL.lastPathComponent, forKey: nameKey)

        if isPump { pumpAudioName = sourceURL.lastPathComponent }
        else       { dumpAudioName = sourceURL.lastPathComponent }
    }

    func resetCustomAudio(isPump: Bool) {
        let pathKey = isPump ? Prefs.customPumpAudioPath : Prefs.customDumpAudioPath
        let nameKey = isPump ? Prefs.customPumpAudioName : Prefs.customDumpAudioName

        if let path = defaults.string(forKey: pathKey) {
            try? FileManager.default.removeItem(atPath: path)
        }
        defaults.removeObject(forKey: pathKey)
        defaults.removeObject(forKey: nameKey)

        if isPump { pumpAudioName = nil } else { dumpAudioName = nil }
    }

    enum AudioError: LocalizedError {
        case tooLong(Int)
        var errorDescription: String? {
            if case .tooLong(let sec) = self {
                return "Audio file too long (\(sec)s). Maximum is 5 seconds."
            }
            return nil
        }
    }
}
