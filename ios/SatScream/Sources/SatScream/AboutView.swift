import SwiftUI

struct AboutView: View {
    @EnvironmentObject var viewModel: BitcoinViewModel
    @Environment(\.dismiss) private var dismiss

    @State private var showNoWallet = false
    @State private var showLearnMore = false
    @State private var showLightningAddress = false
    @State private var noWalletOpacity = 0.0
    @State private var learnMoreOpacity = 0.0
    @State private var addressOpacity = 0.0
    @State private var showWebView = false
    @State private var toastMessage: String? = nil

    private let lightningAddress = "timmy2383@strike.me"
    private var colors: AppColors { AppColors(isDark: viewModel.isDarkMode) }

    // Version string - mirrors AboutActivity's dynamic version lookup
    private var versionString: String {
        let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0"
        let build   = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "1"
        return "Version \(version) (\(build))"
    }

    var body: some View {
        ZStack {
            colors.background.ignoresSafeArea()

            VStack(spacing: 0) {
                // Back button (top-left)
                HStack {
                    Button {
                        UIImpactFeedbackGenerator(style: .light).impactOccurred()
                        dismiss()
                    } label: {
                        Image(systemName: "xmark")
                        .foregroundColor(colors.textPrimary)
                        .frame(width: 48, height: 48)
                    }
                    .padding(.leading, 16)
                    .padding(.top, 16)
                    Spacer()
                }

                Spacer()

                // Centered content
                VStack(spacing: 0) {
                    Text("Hey there, Pleb.")
                    .font(.system(size: 20, weight: .medium))
                    .foregroundColor(colors.textPrimary)
                    .multilineTextAlignment(.center)
                    .padding(.bottom, 16)

                    Text("Please consider donating if you receive value from this app.")
                    .font(.system(size: 16, weight: .light))
                    .foregroundColor(colors.textSecondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 24)
                    .padding(.bottom, 4)

                    Text("Stay humble, stack sats.")
                    .font(.system(size: 16, weight: .light))
                    .foregroundColor(colors.textSecondary)
                    .multilineTextAlignment(.center)
                    .padding(.bottom, 24)

                    // Donate button
                    Button {
                        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                        openLightningWallet()
                    } label: {
                        Text("⚡ Donate ⚡")
                        .font(.system(size: 16, weight: .medium))
                        .foregroundColor(colors.btnText)
                        .frame(width: 250, height: 48)
                        .background(colors.textPrimary)
                        .clipShape(Capsule())
                    }
                    .buttonStyle(PressableButtonStyle())

                    // No wallet message (revealed on failed lightning open)
                    if showNoWallet {
                        Text("It looks like you may not have a compatible Bitcoin Lightning wallet installed. Consider installing one today.")
                        .font(.system(size: 14))
                        .foregroundColor(colors.textSecondary)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 24)
                        .padding(.top, 16)
                        .opacity(noWalletOpacity)
                    }

                    if showLearnMore {
                        Button {
                            UIImpactFeedbackGenerator(style: .light).impactOccurred()
                            showWebView = true
                        } label: {
                            Text("Learn more about Lightning wallets")
                            .font(.system(size: 14, weight: .medium))
                            .foregroundColor(colors.btnText)
                            .frame(width: 250, height: 44)
                            .background(colors.textPrimary)
                            .clipShape(Capsule())
                        }
                        .buttonStyle(PressableButtonStyle())
                        .padding(.top, 12)
                        .opacity(learnMoreOpacity)
                    }

                    if showLightningAddress {
                        HStack(spacing: 0) {
                            Text("Donations can be sent to\u{00a0}")
                            .font(.system(size: 14))
                            .foregroundColor(colors.textSecondary)
                            Button {
                                UIImpactFeedbackGenerator(style: .light).impactOccurred()
                                UIPasteboard.general.string = lightningAddress
                                showToast("Lightning address copied to clipboard")
                            } label: {
                                Text(lightningAddress)
                                .font(.system(size: 14))
                                .foregroundColor(colors.textPrimary)
                                .underline()
                            }
                        }
                        .padding(.horizontal, 24)
                        .padding(.top, 12)
                        .opacity(addressOpacity)
                    }
                }

                Spacer()

                // Version at bottom
                Text(versionString)
                .font(.system(size: 12, weight: .light))
                .foregroundColor(colors.textSecondary.opacity(0.6))
                .padding(.bottom, 16)
            }

            // Toast
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
                    .padding(.bottom, 40)
                }
                .transition(.opacity)
            }
        }
        .sheet(isPresented: $showWebView) {
            WebContentView()
            .environmentObject(viewModel)
        }
    }

    // canOpenURL is unreliable for lightning: on iOS — attempt open() and check result
    private func openLightningWallet() {
        guard let url = URL(string: "lightning:\(lightningAddress)") else {
            showNoWalletSequence()
            return
        }
        UIApplication.shared.open(url, options: [:]) { success in
            if !success {
                DispatchQueue.main.async { self.showNoWalletSequence() }
            }
        }
    }

    // Mirrors AboutActivity.showNoWalletMessages() fade-in sequence
    private func showNoWalletSequence() {
        showNoWallet = true
        withAnimation(.easeInOut(duration: 2.0)) { noWalletOpacity = 1.0 }

        DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
            showLearnMore = true
            withAnimation(.easeInOut(duration: 2.0)) { learnMoreOpacity = 1.0 }

            DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
                showLightningAddress = true
                withAnimation(.easeInOut(duration: 2.0)) { addressOpacity = 1.0 }
            }
        }
    }

    private func showToast(_ message: String) {
        withAnimation { toastMessage = message }
        DispatchQueue.main.asyncAfter(deadline: .now() + 2) {
            withAnimation { toastMessage = nil }
        }
    }
}