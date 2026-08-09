import WidgetKit
import SwiftUI
import Foundation
import AppIntents

// Shared App Group suite name — must match BitcoinViewModel
private let suiteName = "group.com.peerloomllc.satscream"

// Widget kind, shared between the configuration and the refresh intent
private let widgetKind = "SatScreamWidget"

// MARK: - Timeline Entry

struct PriceEntry: TimelineEntry {
    let date: Date
    let priceText: String
    let isDarkMode: Bool
}

// MARK: - Timeline Provider

struct PriceProvider: TimelineProvider {

    func placeholder(in context: Context) -> PriceEntry {
        PriceEntry(date: Date(), priceText: "$97,432", isDarkMode: false)
    }

    func getSnapshot(in context: Context, completion: @escaping (PriceEntry) -> Void) {
        // Snapshots must return immediately (widget gallery, transitions), so never
        // hit the network here — render whatever price is already cached.
        completion(makeEntry())
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<PriceEntry>) -> Void) {
        Task {
            // Fetch our own price instead of only mirroring whatever the app last wrote.
            // Without this the widget shows stale data whenever the app hasn't run
            // recently, since iOS gives the app no background price loop.
            let fresh = await PriceFetcher.fetch()
            if let fresh {
                persist(price: fresh)
                NSLog("[SatScreamWidget] timeline refreshed with live price \(fresh)")
            } else {
                NSLog("[SatScreamWidget] timeline fell back to the cached price")
            }

            let entry = makeEntry(freshPrice: fresh)
            // Ask for 5 min. iOS will not grant all of these - it rations reloads out of a
            // daily budget (roughly 40-70) and spends them around the times the user is
            // actually on the device. Asking often is how we get the most of that budget
            // during active use; the system throttles the rest.
            let nextUpdate = Calendar.current.date(byAdding: .minute, value: 5, to: Date())!
            completion(Timeline(entries: [entry], policy: .after(nextUpdate)))
        }
    }

    /// Write the freshly fetched price back to the shared App Group so the app opens
    /// on the current number rather than its own older cache.
    private func persist(price: Double) {
        guard let defaults = UserDefaults(suiteName: suiteName) else { return }
        let fmt = DateFormatter()
        fmt.dateFormat = "hh:mm:ss a"
        fmt.locale = Locale(identifier: "en_US")
        defaults.set(Float(price), forKey: "LAST_PRICE")
        defaults.set("Last updated: \(fmt.string(from: Date()))", forKey: "LAST_UPDATE_TIME")
    }

    private func makeEntry(freshPrice: Double? = nil) -> PriceEntry {
        // If the shared container URL is nil, the App Group isn't provisioned for this
        // extension — the widget then can't see the app's data and will render the "—"
        // placeholder. Log it so the cause is visible in Console during diagnosis.
        if FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: suiteName) == nil {
            NSLog("[SatScreamWidget] ⚠️ App Group '\(suiteName)' is not provisioned for the widget extension — cannot read shared price data.")
        }

        let defaults = UserDefaults(suiteName: suiteName)
        // Fall back to the cached price when the fetch failed (offline, API down).
        let price    = freshPrice ?? Double(defaults?.float(forKey: "LAST_PRICE") ?? 0)
        let isBSM    = defaults?.bool(forKey: "BITCOIN_STANDARD_MODE") ?? false
        let isDark   = defaults?.bool(forKey: "DARK_MODE") ?? false

        let priceText: String
        if price > 0 {
            if isBSM {
                let sats = Int64(100_000_000.0 / price)
                priceText = "\(formatCommas(sats))/$"
            } else {
                priceText = "$\(formatCommas(Int64(price)))"
            }
        } else {
            priceText = isBSM ? "—/$" : "$—"
        }

        return PriceEntry(date: Date(), priceText: priceText, isDarkMode: isDark)
    }

    private func formatCommas(_ value: Int64) -> String {
        let f = NumberFormatter()
        f.numberStyle = .decimal
        f.groupingSeparator = ","
        return f.string(from: NSNumber(value: value)) ?? "\(value)"
    }
}

// MARK: - Price Fetching

/// Mirrors BitcoinViewModel's fetch: CoinGecko first, Coinbase spot as fallback.
/// Kept deliberately small and self-contained - the widget extension is a separate
/// binary and cannot reach the app's view model.
private enum PriceFetcher {

    /// Widget extensions are killed if a timeline takes too long, so cap the wait
    /// well under that and let the cached price cover a timeout.
    private static let session: URLSession = {
        let cfg = URLSessionConfiguration.ephemeral
        cfg.timeoutIntervalForRequest  = 8
        cfg.timeoutIntervalForResource = 8
        return URLSession(configuration: cfg)
    }()

    static func fetch() async -> Double? {
        let primary = URL(string: "https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=usd")!
        if let price = await coinGecko(primary) { return price }

        let secondary = URL(string: "https://api.coinbase.com/v2/prices/BTC-USD/spot")!
        return await coinbase(secondary)
    }

    private static func coinGecko(_ url: URL) async -> Double? {
        struct Response: Codable {
            let bitcoin: BitcoinData
            struct BitcoinData: Codable { let usd: Double }
        }
        do {
            let (data, _) = try await session.data(from: url)
            return try JSONDecoder().decode(Response.self, from: data).bitcoin.usd
        } catch {
            NSLog("[SatScreamWidget] CoinGecko fetch failed: \(error.localizedDescription)")
            return nil
        }
    }

    private static func coinbase(_ url: URL) async -> Double? {
        struct Response: Codable {
            let data: PriceData
            struct PriceData: Codable { let amount: String }
        }
        do {
            let (data, _) = try await session.data(from: url)
            return Double(try JSONDecoder().decode(Response.self, from: data).data.amount)
        } catch {
            NSLog("[SatScreamWidget] Coinbase fetch failed: \(error.localizedDescription)")
            return nil
        }
    }
}

// MARK: - Tap-to-Refresh Intent

/// Interactive widgets (iOS 17+) are the only way to refresh on demand: iOS gives
/// widgets no "the user is looking at me" callback, so a deliberate tap is the closest
/// thing to an instant update. Reloading the timeline runs `getTimeline`, which fetches.
@available(iOS 17.0, *)
struct RefreshPriceIntent: AppIntent {
    static var title: LocalizedStringResource = "Refresh Bitcoin Price"
    static var description = IntentDescription("Fetches the current Bitcoin price for the widget.")

    // Keep it out of Shortcuts and Spotlight - it only makes sense from the widget.
    static var isDiscoverable: Bool = false

    func perform() async throws -> some IntentResult {
        WidgetCenter.shared.reloadTimelines(ofKind: widgetKind)
        return .result()
    }
}

// MARK: - Widget View

struct SatScreamWidgetView: View {
    let entry: PriceEntry
    @Environment(\.widgetFamily) private var family

    // Mirror Android: #121212 dark, #FFFFFF light
    private var bgColor: Color {
        entry.isDarkMode
        ? Color(red: 0.071, green: 0.071, blue: 0.071)
        : Color.white
    }

    // Mirror Android: #E0E0E0 dark, #212121 light
    private var textColor: Color {
        entry.isDarkMode
        ? Color(red: 0.878, green: 0.878, blue: 0.878)
        : Color(red: 0.129, green: 0.129, blue: 0.129)
    }

    // Scale font to family size, matching Android's "50% of smaller dimension" heuristic
    private var fontSize: CGFloat {
        switch family {
        case .systemSmall:  return 34
        case .systemMedium: return 48
        case .systemLarge:  return 72
        default:            return 34
        }
    }

    var body: some View {
        ZStack(alignment: .topTrailing) {
            Text(entry.priceText)
                .font(.system(size: fontSize, weight: .thin, design: .default))
                .foregroundColor(textColor)
                .minimumScaleFactor(0.3)
                .lineLimit(1)
                .padding(10)
                .frame(maxWidth: .infinity, maxHeight: .infinity)

            refreshButton
        }
        .widgetBackground(bgColor)
    }

    /// Small, deliberately low-contrast so it doesn't compete with the price. Tapping it
    /// refreshes in place; tapping anywhere else still opens the app as before.
    @ViewBuilder
    private var refreshButton: some View {
        if #available(iOS 17.0, *) {
            Button(intent: RefreshPriceIntent()) {
                Image(systemName: "arrow.clockwise")
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundColor(textColor.opacity(0.4))
            }
            .buttonStyle(.plain)
            .padding(.top, 6)
            .padding(.trailing, 8)
        }
    }
}


// Apply the background full-bleed. This must be applied directly (not wrapped in a
// custom ViewModifier) so WidgetKit recognizes it as the container background —
// otherwise iOS draws its own default (slate-gray) background in the bleed area while
// the app color only fills the inset content, producing the two-tone look.
private extension View {
    @ViewBuilder
    func widgetBackground(_ color: Color) -> some View {
        if #available(iOS 17.0, *) {
            containerBackground(color, for: .widget)
        } else {
            background(color)
        }
    }
}

// MARK: - Widget Configuration

@main
struct SatScreamWidget: Widget {
    let kind = widgetKind

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: PriceProvider()) { entry in
            SatScreamWidgetView(entry: entry)
        }
        .configurationDisplayName("SatScream")
        .description("Live Bitcoin price on your home screen.")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge])
    }
}