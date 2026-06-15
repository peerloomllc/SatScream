import SwiftUI

struct MainView: View {
    @EnvironmentObject var viewModel: BitcoinViewModel
    @State private var showPumpSheet = false
    @State private var showDumpSheet = false
    @State private var showAudio      = false
    @State private var toastMessage: String? = nil

    // Drives the blast-off: bump `hitTick` each time an alert fires so the rocket
    // view is recreated and re-animates. `hitIsPump` picks the direction/art for
    // whichever alert fired most recently — no pump-over-dump priority masking.
    @State private var hitTick = 0
    @State private var hitIsPump = true

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

                // "Alert hit" rocket blasts across the whole screen: bottom→top for
                // pump, top→bottom for dump (plays twice, then disappears). Placed
                // just above the background and beneath the content so its opaque
                // square blends into the background and the text/buttons it flies
                // past cover it instead of being covered by its square. Keyed on
                // hitTick so each fire restarts the animation.
                if hitTick > 0, let img = Self.hitIcon(isPump: hitIsPump, isDark: viewModel.isDarkMode) {
                    AlertHitRocket(image: img, isPump: hitIsPump, screenHeight: geo.size.height)
                    .id(hitTick)
                }

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
                                .clipShape(Capsule())
                            }
                            .buttonStyle(PressableButtonStyle())

                            Text(viewModel.pumpAlertStatus)
                            .font(.system(size: 10))
                            .foregroundColor(colors.textTertiary)
                            .multilineTextAlignment(.center)
                        }

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
                                .clipShape(Capsule())
                            }
                            .buttonStyle(PressableButtonStyle())

                            Text(viewModel.dumpAlertStatus)
                            .font(.system(size: 10))
                            .foregroundColor(colors.textTertiary)
                            .multilineTextAlignment(.center)
                        }
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
        // Fire the rocket for whichever alert just transitioned to "hit".
        .onChange(of: viewModel.pumpAlertTriggered) { fired in
            if fired { hitIsPump = true; hitTick += 1 }
        }
        .onChange(of: viewModel.dumpAlertTriggered) { fired in
            if fired { hitIsPump = false; hitTick += 1 }
        }
        .onAppear {
            // Replay once for an alert that was already hit when the screen opened.
            if viewModel.dumpAlertTriggered { hitIsPump = false; hitTick += 1 }
            else if viewModel.pumpAlertTriggered { hitIsPump = true; hitTick += 1 }
        }
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

// "Alert hit" rocket that blasts across the screen at its original size. The art
// points up-right, so it's rotated upright for pump (nose up, flying up) and
// inverted for dump (nose down, flying down). It travels the full screen height
// twice — with a subtle size pulse — then disappears. No back-and-forth rotation.
private struct AlertHitRocket: View {
    let image: UIImage
    let isPump: Bool
    let screenHeight: CGFloat

    @State private var progress: CGFloat = 0   // 0 = start edge, 1 = far edge
    @State private var pulse = false
    @State private var finished = false

    private let passDuration = 1.4
    private let passes = 2

    // Fixed orientation so each rocket points the way it travels: pump nose-up,
    // dump nose-down. (The two art assets aren't oriented the same, hence the
    // different angles.)
    private var rotation: Double { isPump ? -45 : 45 }

    // Offset from screen center. Pump starts below the screen and ends above it;
    // dump is the reverse.
    private var offsetY: CGFloat {
        let edge = screenHeight / 2 + 80
        let start = isPump ? edge : -edge
        let end = isPump ? -edge : edge
        return start + (end - start) * progress
    }

    var body: some View {
        Image(uiImage: image)
        .resizable()
        .scaledToFit()
        .frame(width: 48, height: 48)
        .rotationEffect(.degrees(rotation))
        .scaleEffect(pulse ? 1.12 : 0.9)
        .offset(y: offsetY)
        .opacity(finished ? 0 : 1)
        .onAppear {
            withAnimation(.easeInOut(duration: 0.5).repeatForever(autoreverses: true)) { pulse = true }
            // Constant speed so the rocket is clearly visible across the whole
            // screen in both directions (an easing curve hid the dump pass: it
            // lingered off-screen at the top then zipped through too fast).
            withAnimation(.linear(duration: passDuration).repeatCount(passes, autoreverses: false)) {
                progress = 1
            }
            // Hide once both passes complete.
            DispatchQueue.main.asyncAfter(deadline: .now() + passDuration * Double(passes)) {
                withAnimation(.easeOut(duration: 0.3)) { finished = true }
            }
        }
    }
}