import SwiftUI

// Maps directly to Android res/values/colors.xml and res/values-night/colors.xml
extension Color {
    // Use these via environment's colorScheme, or let SwiftUI's dark mode handle it
    // by defining light/dark pairs.

    static let backgroundMain = Color("backgroundMain")
    static let dividerLine    = Color("dividerLine")
    static let textPrimary    = Color("textPrimary")
    static let textSecondary  = Color("textSecondary")
    static let textTertiary   = Color("textTertiary")
    static let btnBackground  = Color("btnBackground")   // button TEXT color
    static let btnPump        = Color("btnPump")         // #808080 light / #E0E0E0 dark
    static let btnDump        = Color("btnDump")         // #808080 light / #E0E0E0 dark
    static let inputSurface   = Color("inputSurface")    // 12% overlay

    // Hex initializer for colors used only in code (widget etc.)
    init(hex: UInt32, opacity: Double = 1.0) {
        let r = Double((hex >> 16) & 0xFF) / 255.0
        let g = Double((hex >> 8)  & 0xFF) / 255.0
        let b = Double( hex        & 0xFF) / 255.0
        self.init(.sRGB, red: r, green: g, blue: b, opacity: opacity)
    }
}

// Since xtool doesn't support asset catalogs, we define colors in code.
// We resolve light/dark manually using a passed colorScheme.
struct AppColors {
    let isDark: Bool

    var background:    Color { isDark ? Color(hex: 0x000000) : Color(hex: 0xFFFFFF) }
    var divider:       Color { isDark ? Color(hex: 0x333333) : Color(hex: 0xE0E0E0) }
    var textPrimary:   Color { isDark ? Color(hex: 0xFFFFFF) : Color(hex: 0x000000) }
    var textSecondary: Color { isDark ? Color(hex: 0xB0B0B0) : Color(hex: 0x4F4F4F) }
    var textTertiary:  Color { isDark ? Color(hex: 0x808080) : Color(hex: 0x7F7F7F) }
    var btnText:       Color { isDark ? Color(hex: 0x000000) : Color(hex: 0xFFFFFF) } // text ON buttons
    var btnPump:       Color { isDark ? Color(hex: 0xE0E0E0) : Color(hex: 0x808080) }
    var btnDump:       Color { isDark ? Color(hex: 0xE0E0E0) : Color(hex: 0x808080) }
    var inputSurface:  Color { isDark ? Color(hex: 0xFFFFFF, opacity: 0.12) : Color(hex: 0x000000, opacity: 0.12) }
}
