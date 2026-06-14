import SwiftUI

struct MainView: View {
    @EnvironmentObject var viewModel: BitcoinViewModel
    @State private var showPumpSheet = false
    @State private var showDumpSheet = false
    @State private var showAudio      = false
    @State private var toastMessage: String? = nil

    private var colors: AppColors { AppColors(isDark: viewModel.isDarkMode) }

    private var priceColor: Color {
        switch viewModel.priceDirection {
        case .up:   return colors.priceUp
        case .down: return colors.priceDown
        case .none: return colors.textPrimary
        }
    }

    var body: some View {
        GeometryReader { geo in
            ZStack {
                colors.background.ignoresSafeArea()

                // Main content — vertically centered in available space above bottom bar
                VStack(spacing: 0) {
                    Spacer()

                    // Hero price display — tap to toggle Bitcoin Standard Mode
                    Text(viewModel.formattedPrice)
                    .font(.system(size: 64, weight: .thin))
                    .foregroundColor(priceColor)
                    .contentTransition(.numericText())
                    .minimumScaleFactor(0.4)
                    .lineLimit(1)
                    .padding(.horizontal, 16)
                    .animation(.spring(response: 0.35, dampingFraction: 0.85), value: viewModel.formattedPrice)
                    .animation(.easeInOut(duration: 0.3), value: viewModel.priceDirection)
                    .onTapGesture {
                        UIImpactFeedbackGenerator(style: .light).impactOccurred()
                        viewModel.toggleBitcoinStandardMode()
                        let msg = viewModel.isBitcoinStandardMode
                        ? "Bitcoin Standard Mode engaged"
                        : "Fiat Mode engaged"
                        showToast(msg)
                    }

                    Text(viewModel.lastUpdatedText)
                    .font(.system(size: 10, weight: .light))
                    .foregroundColor(colors.textSecondary)
                    .padding(.top, 16)

                    // Divider
                    Rectangle()
                    .fill(colors.divider)
                    .frame(height: 1)
                    .padding(.horizontal, 24)
                    .padding(.top, 32)
                    .padding(.bottom, 24)

                    Text("Set your price targets")
                    .font(.system(size: 10, weight: .medium))
                    .foregroundColor(colors.textSecondary)
                    .kerning(1.5)
                    .padding(.bottom, 16)

                    // Pump & Dump buttons side by side
                    HStack(alignment: .top, spacing: 16) {
                        // Pump column
                        VStack(spacing: 8) {
                            Button {
                                UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                                showPumpSheet = true
                            } label: {
                                Text("Set Pump Alert")
                                .font(.system(size: 12, weight: .medium))
                                .foregroundColor(colors.btnText)
                                .frame(maxWidth: .infinity, minHeight: 48)
                                .background(colors.btnPump)
                                .cornerRadius(8)
                            }
                            .buttonStyle(PressableButtonStyle())

                            Text(viewModel.pumpAlertStatus)
                            .font(.system(size: 10))
                            .foregroundColor(colors.textTertiary)
                            .multilineTextAlignment(.center)

                            alertHitIcon(triggered: viewModel.pumpAlertTriggered, isPump: true)
                        }
                        .animation(.spring(response: 0.45, dampingFraction: 0.6), value: viewModel.pumpAlertTriggered)

                        // Dump column
                        VStack(spacing: 8) {
                            Button {
                                UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                                showDumpSheet = true
                            } label: {
                                Text("Set Dump Alert")
                                .font(.system(size: 12, weight: .medium))
                                .foregroundColor(colors.btnText)
                                .frame(maxWidth: .infinity, minHeight: 48)
                                .background(colors.btnDump)
                                .cornerRadius(8)
                            }
                            .buttonStyle(PressableButtonStyle())

                            Text(viewModel.dumpAlertStatus)
                            .font(.system(size: 10))
                            .foregroundColor(colors.textTertiary)
                            .multilineTextAlignment(.center)

                            alertHitIcon(triggered: viewModel.dumpAlertTriggered, isPump: false)
                        }
                        .animation(.spring(response: 0.45, dampingFraction: 0.6), value: viewModel.dumpAlertTriggered)
                    }
                    .padding(.horizontal, 24)

                    Spacer()

                    // Spacer for bottom bar height
                    Spacer().frame(height: 64)
                }
                .frame(width: geo.size.width)

                // Bottom bar: audio | dark mode toggle
                VStack {
                    Spacer()
                    ZStack {
                        colors.background
                        .frame(height: 64)
                        .shadow(color: colors.divider, radius: 1, y: -1)

                        // Two icons, evenly distributed (Info/About button removed —
                        // it linked to a page with a donate button).
                        HStack {
                            Spacer()

                            Button {
                                UIImpactFeedbackGenerator(style: .light).impactOccurred()
                                showAudio = true
                            } label: {
                                Image(systemName: "bell.badge")
                                .foregroundColor(colors.textSecondary)
                                .frame(width: 48, height: 48)
                            }
                            .buttonStyle(PressableButtonStyle())

                            Spacer()

                            // Dark-mode toggle as a single sun/moon icon (shows the
                            // current theme; tap flips it).
                            Button {
                                UIImpactFeedbackGenerator(style: .light).impactOccurred()
                                viewModel.toggleDarkMode(!viewModel.isDarkMode)
                            } label: {
                                Image(systemName: viewModel.isDarkMode ? "moon.stars" : "sun.max")
                                .foregroundColor(colors.textSecondary)
                                .frame(width: 48, height: 48)
                                .symbolReplaceTransition()
                                .animation(.easeInOut(duration: 0.25), value: viewModel.isDarkMode)
                            }
                            .buttonStyle(PressableButtonStyle())

                            Spacer()
                        }
                    }
                    .frame(height: 64)
                }
                .ignoresSafeArea(edges: .bottom)

                // Toast overlay
                if let msg = toastMessage {
                    VStack {
                        Spacer()
                        Text(msg)
                        .font(.system(size: 14))
                        .foregroundColor(.white)
                        .padding(.horizontal, 20)
                        .padding(.vertical, 10)
                        .background(Color.black.opacity(0.75))
                        .cornerRadius(20)
                        .padding(.bottom, 100)
                    }
                    .transition(.opacity)
                }
            }
        }
        .sheet(isPresented: $showPumpSheet) {
            PriceInputSheet(isPump: true)
            .environmentObject(viewModel)
            .sheetChrome()
        }
        .sheet(isPresented: $showDumpSheet) {
            PriceInputSheet(isPump: false)
            .environmentObject(viewModel)
            .sheetChrome()
        }
        .sheet(isPresented: $showAudio) {
            AudioSettingsView()
            .environmentObject(viewModel)
            .sheetChrome()
        }
    }

    // Fixed-height slot for the "alert hit" rocket so toggling its visibility
    // never reflows the centered content above it. The icon springs in within
    // the reserved space instead of pushing the buttons up.
    @ViewBuilder
    private func alertHitIcon(triggered: Bool, isPump: Bool) -> some View {
        ZStack {
            if triggered, let img = Self.hitIcon(isPump: isPump, isDark: viewModel.isDarkMode) {
                Image(uiImage: img)
                .resizable()
                .scaledToFit()
                .frame(width: 48, height: 48)
                .transition(.scale.combined(with: .opacity))
            }
        }
        .frame(height: 48)
    }

    // Loads the rocket variant matching the current theme. The PNGs are opaque
    // (white bg for light, black bg for dark) so they blend into the app
    // background. Resolved by file URL — the reliable path for loose bundle
    // resources (named-image lookup can miss root-level PNGs on device).
    private static func hitIcon(isPump: Bool, isDark: Bool) -> UIImage? {
        let name = "ic_\(isPump ? "pump" : "dump")_hit_\(isDark ? "dark" : "light")"
        if let url = Bundle.appResources.url(forResource: name, withExtension: "png"),
           let img = UIImage(contentsOfFile: url.path) {
            return img
        }
        return UIImage(named: name, in: Bundle.appResources, compatibleWith: nil)
    }

    private func showToast(_ message: String) {
        withAnimation { toastMessage = message }
        DispatchQueue.main.asyncAfter(deadline: .now() + 2) {
            withAnimation { toastMessage = nil }
        }
    }
}