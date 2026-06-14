import SwiftUI
import UniformTypeIdentifiers

struct AudioSettingsView: View {
    @EnvironmentObject var viewModel: BitcoinViewModel
    @Environment(\.dismiss) private var dismiss

    // Single picker state — two .fileImporter modifiers on one view is a SwiftUI bug
    // where only the last one reliably opens. Use one picker + a flag instead.
    @State private var showPicker     = false
    @State private var pickingForPump = true
    @State private var errorMessage: String? = nil
    @State private var toastMessage: String? = nil

    private var colors: AppColors { AppColors(isDark: viewModel.isDarkMode) }

    private let audioTypes: [UTType] = [.audio, .mp3, .wav, .aiff, .mpeg4Audio]

    var body: some View {
        ZStack {
            colors.background.ignoresSafeArea()

            VStack(spacing: 0) {
                // Back button
                HStack {
                    Button {
                        UIImpactFeedbackGenerator(style: .light).impactOccurred()
                        dismiss()
                    } label: {
                        Image(systemName: "xmark")
                        .foregroundColor(colors.textPrimary)
                        .frame(width: 48, height: 48)
                    }
                    .buttonStyle(PressableButtonStyle())
                    .padding(.leading, 16)
                    .padding(.top, 16)
                    Spacer()
                }

                ScrollView {
                    VStack(spacing: 0) {
                        Text("Audio Settings")
                        .font(.system(size: 28, weight: .medium))
                        .foregroundColor(colors.textPrimary)
                        .padding(.bottom, 32)

                        audioSection(
                            title: "Pump Alert Audio",
                            statusText: viewModel.pumpAudioName.map { "Using custom audio \"\($0)\"" } ?? "Using default audio",
                            isPump: true
                        )
                        .padding(.bottom, 32)

                        audioSection(
                            title: "Dump Alert Audio",
                            statusText: viewModel.dumpAudioName.map { "Using custom audio \"\($0)\"" } ?? "Using default audio",
                            isPump: false
                        )
                    }
                    .padding(24)
                }
            }

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
        // Single fileImporter — pickingForPump flag tells the handler which section triggered it
        .fileImporter(
            isPresented: $showPicker,
            allowedContentTypes: audioTypes,
            allowsMultipleSelection: false
        ) { result in
            handleFilePick(result: result, isPump: pickingForPump)
        }
        .alert("Error", isPresented: .constant(errorMessage != nil), actions: {
            Button("OK") { errorMessage = nil }
        }, message: {
            Text(errorMessage ?? "")
        })
    }

    @ViewBuilder
    private func audioSection(title: String, statusText: String, isPump: Bool) -> some View {
        VStack(spacing: 8) {
            Text(title)
            .font(.system(size: 20, weight: .medium))
            .foregroundColor(colors.textPrimary)

            Text(statusText)
            .font(.system(size: 14))
            .foregroundColor(colors.textSecondary)
            .multilineTextAlignment(.center)
            .padding(.horizontal, 16)

            HStack(spacing: 12) {
                Button {
                    UIImpactFeedbackGenerator(style: .light).impactOccurred()
                    pickingForPump = isPump
                    showPicker = true
                } label: {
                    Text("Select Audio")
                    .font(.system(size: 14, weight: .medium))
                    .foregroundColor(colors.btnText)
                    .frame(width: 130, height: 56)
                    .background(isPump ? colors.btnPump : colors.btnDump)
                    .clipShape(Capsule())
                }
                .buttonStyle(PressableButtonStyle())

                Button {
                    UIImpactFeedbackGenerator(style: .light).impactOccurred()
                    viewModel.resetCustomAudio(isPump: isPump)
                    showToast(isPump ? "Reset to default pump audio" : "Reset to default dump audio")
                } label: {
                    Text("Use Default")
                    .font(.system(size: 14, weight: .medium))
                    .foregroundColor(colors.btnText)
                    .frame(width: 130, height: 56)
                    .background(colors.textPrimary)
                    .clipShape(Capsule())
                }
                .buttonStyle(PressableButtonStyle())
            }

            Button {
                UIImpactFeedbackGenerator(style: .light).impactOccurred()
                viewModel.playAlertSound(isPump: isPump)
            } label: {
                Image(systemName: "play.fill")
                .foregroundColor(colors.textPrimary)
                .frame(width: 56, height: 56)
            }
            .buttonStyle(PressableButtonStyle())
        }
    }

    private func handleFilePick(result: Result<[URL], Error>, isPump: Bool) {
        switch result {
        case .failure(let err):
            errorMessage = err.localizedDescription
        case .success(let urls):
            guard let url = urls.first else { return }
            guard url.startAccessingSecurityScopedResource() else {
                errorMessage = "Could not access the selected file."
                return
            }
            defer { url.stopAccessingSecurityScopedResource() }

            do {
                try viewModel.saveCustomAudio(sourceURL: url, isPump: isPump)
                showToast(isPump ? "Pump alert audio updated" : "Dump alert audio updated")
            } catch {
                errorMessage = error.localizedDescription
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