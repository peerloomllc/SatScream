import SwiftUI

struct WelcomeView: View {
    @EnvironmentObject var viewModel: BitcoinViewModel
    @AppStorage("WELCOME_SHOWN") private var welcomeShown = false

    @State private var titleOpacity    = 0.0
    @State private var titleVisible    = true
    @State private var messageOpacity  = 0.0
    @State private var messageVisible  = false
    @State private var buttonsOpacity  = 0.0
    @State private var buttonsVisible  = false

    private var colors: AppColors { AppColors(isDark: viewModel.isDarkMode) }

    var body: some View {
        ZStack {
            colors.background.ignoresSafeArea()

            // Title: fades in then out
            if titleVisible {
                Text("Welcome")
                    .font(.system(size: 32, weight: .medium))
                    .foregroundColor(colors.textPrimary)
                    .opacity(titleOpacity)
                    .multilineTextAlignment(.center)
            }

            // Message + Buttons
            ScrollView {
                VStack(spacing: 0) {
                    Spacer().frame(height: 40)

                    if messageVisible {
                        Text("Thanks for downloading my app. This is meant to be a simple, fun and consistent BTC price alert app that lets you set up custom audio alerts for price targets. No bloat, no ads. If you are already convinced bitcoin is the best monetary technology then get started setting up your alerts. If you are new to bitcoin or are not yet convinced, please consider doing more research and seeing how deep the rabbit hole goes. Stay humble, stack sats.")
                            .font(.system(size: 16))
                            .foregroundColor(colors.textSecondary)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 24)
                            .opacity(messageOpacity)
                    }

                    if buttonsVisible {
                        HStack(spacing: 12) {
                            // Learn More button
                            Button {
                                UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                                if let url = URL(string: "https://nakamotoinstitute.org/crash-course/") {
                                    UIApplication.shared.open(url)
                                }
                            } label: {
                                Text("Learn More")
                                    .font(.system(size: 14, weight: .medium))
                                    .foregroundColor(colors.btnText)
                                    .frame(width: 150, height: 44)
                                    .background(colors.textPrimary)
                                    .clipShape(Capsule())
                            }
                            .buttonStyle(PressableButtonStyle())

                            // Continue button
                            Button {
                                UINotificationFeedbackGenerator().notificationOccurred(.success)
                                welcomeShown = true
                            } label: {
                                Text("Continue")
                                    .font(.system(size: 14, weight: .medium))
                                    .foregroundColor(colors.btnText)
                                    .frame(width: 150, height: 44)
                                    .background(colors.btnPump)
                                    .clipShape(Capsule())
                            }
                            .buttonStyle(PressableButtonStyle())
                        }
                        .padding(.top, 32)
                        .opacity(buttonsOpacity)
                    }

                    Spacer().frame(height: 40)
                }
                .frame(maxWidth: .infinity)
                .frame(minHeight: UIScreen.main.bounds.height)
            }
        }
        .onAppear { startSequence() }
    }

    // Mirrors WelcomeActivity.startWelcomeSequence() exactly:
    // Title fades in 2s → fades out 2s → message fades in 2s → buttons fade in 2s
    private func startSequence() {
        // Step 1: Fade in title (2s)
        withAnimation(.easeInOut(duration: 2.0)) {
            titleOpacity = 1.0
        }
        // Step 2: Fade out title (2s) after 2s delay
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
            withAnimation(.easeInOut(duration: 2.0)) {
                titleOpacity = 0.0
            }
            // Step 3: Show message (2s fade in) after title fades out
            DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
                titleVisible   = false
                messageVisible = true
                withAnimation(.easeInOut(duration: 2.0)) {
                    messageOpacity = 1.0
                }
                // Step 4: Show buttons (2s fade in) after message appears
                DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
                    buttonsVisible = true
                    withAnimation(.easeInOut(duration: 2.0)) {
                        buttonsOpacity = 1.0
                    }
                }
            }
        }
    }
}
