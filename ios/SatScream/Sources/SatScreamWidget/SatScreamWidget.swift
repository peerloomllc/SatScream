import WidgetKit
import SwiftUI
import Foundation

// Shared App Group suite name — must match BitcoinViewModel
private let suiteName = "group.com.peerloomllc.satscream"

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
        completion(makeEntry())
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<PriceEntry>) -> Void) {
        let entry = makeEntry()
        // WidgetKit enforces a minimum ~15 min refresh; we request every 15 min
        let nextUpdate = Calendar.current.date(byAdding: .minute, value: 15, to: Date())!
        completion(Timeline(entries: [entry], policy: .after(nextUpdate)))
    }

    private func makeEntry() -> PriceEntry {
        // If the shared container URL is nil, the App Group isn't provisioned for this
        // extension — the widget then can't see the app's data and will render the "—"
        // placeholder. Log it so the cause is visible in Console during diagnosis.
        if FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: suiteName) == nil {
            NSLog("[SatScreamWidget] ⚠️ App Group '\(suiteName)' is not provisioned for the widget extension — cannot read shared price data.")
        }

        let defaults = UserDefaults(suiteName: suiteName)
        let price    = Double(defaults?.float(forKey: "LAST_PRICE") ?? 0)
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
        ZStack {
            bgColor
            Text(entry.priceText)
            .font(.system(size: fontSize, weight: .thin, design: .default))
            .foregroundColor(textColor)
            .minimumScaleFactor(0.3)
            .lineLimit(1)
            .padding(10)
        }
        .modifier(WidgetBackgroundModifier(color: bgColor))
    }
}


// iOS 16/17 compatible background modifier
private struct WidgetBackgroundModifier: ViewModifier {
    let color: Color

    func body(content: Content) -> some View {
        if #available(iOS 17.0, *) {
            content.containerBackground(color, for: .widget)
        } else {
            content
        }
    }
}

// MARK: - Widget Configuration

@main
struct SatScreamWidget: Widget {
    let kind = "SatScreamWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: PriceProvider()) { entry in
            SatScreamWidgetView(entry: entry)
        }
        .configurationDisplayName("SatScream")
        .description("Live Bitcoin price on your home screen.")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge])
    }
}