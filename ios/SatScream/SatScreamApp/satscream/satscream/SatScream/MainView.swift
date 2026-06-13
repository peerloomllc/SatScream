import SwiftUI

struct MainView: View {
    @EnvironmentObject var viewModel: BitcoinViewModel
    @State private var showPumpSheet = false
    @State private var showDumpSheet = false
    @State private var showAbout      = false
    @State private var showAudio      = false
    @State private var toastMessage: String? = nil

    private var colors: AppColors { AppColors(isDark: viewModel.isDarkMode) }

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
                    .foregroundColor(colors.textPrimary)
                    .minimumScaleFactor(0.4)
                    .lineLimit(1)
                    .padding(.horizontal, 16)
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

                            Text(viewModel.pumpAlertStatus)
                            .font(.system(size: 10))
                            .foregroundColor(colors.textTertiary)
                            .multilineTextAlignment(.center)

                            if viewModel.pumpAlertTriggered {
                                Image("ic_pump_hit_light", bundle: .main)
                                .resizable()
                                .scaledToFit()
                                .frame(width: 48, height: 48)
                            }
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
                                .cornerRadius(8)
                            }

                            Text(viewModel.dumpAlertStatus)
                            .font(.system(size: 10))
                            .foregroundColor(colors.textTertiary)
                            .multilineTextAlignment(.center)

                            if viewModel.dumpAlertTriggered {
                                Image("ic_dump_hit_light", bundle: .main)
                                .resizable()
                                .scaledToFit()
                                .frame(width: 48, height: 48)
                            }
                        }
                    }
                    .padding(.horizontal, 24)

                    Spacer()

                    // Spacer for bottom bar height
                    Spacer().frame(height: 64)
                }
                .frame(width: geo.size.width)

                // Bottom bar: audio | dark mode toggle | info
                VStack {
                    Spacer()
                    ZStack {
                        colors.background
                        .frame(height: 64)
                        .shadow(color: colors.divider, radius: 1, y: -1)

                        HStack {
                            Button {
                                UIImpactFeedbackGenerator(style: .light).impactOccurred()
                                showAudio = true
                            } label: {
                                Image(systemName: "speaker.wave.2")
                                .foregroundColor(colors.textSecondary)
                                .frame(width: 48, height: 48)
                            }
                            .padding(.leading, 24)

                            Spacer()

                            HStack(spacing: 8) {
                                Text("Dark Mode")
                                .font(.system(size: 10))
                                .foregroundColor(colors.textSecondary)
                                Toggle("", isOn: Binding(
                                    get: { viewModel.isDarkMode },
                                    set: { viewModel.toggleDarkMode($0) }
                                ))
                                .labelsHidden()
                                .tint(Color(hex: 0x4F4F4F))
                            }

                            Spacer()

                            Button {
                                UIImpactFeedbackGenerator(style: .light).impactOccurred()
                                showAbout = true
                            } label: {
                                Image(systemName: "info.circle")
                                .foregroundColor(colors.textSecondary)
                                .frame(width: 48, height: 48)
                            }
                            .padding(.trailing, 24)
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
        }
        .sheet(isPresented: $showDumpSheet) {
            PriceInputSheet(isPump: false)
            .environmentObject(viewModel)
        }
        .sheet(isPresented: $showAbout) {
            AboutView()
            .environmentObject(viewModel)
        }
        .sheet(isPresented: $showAudio) {
            AudioSettingsView()
            .environmentObject(viewModel)
        }
    }

    private func showToast(_ message: String) {
        withAnimation { toastMessage = message }
        DispatchQueue.main.asyncAfter(deadline: .now() + 2) {
            withAnimation { toastMessage = nil }
        }
    }
}
