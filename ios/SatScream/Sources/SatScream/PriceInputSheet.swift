import SwiftUI

struct PriceInputSheet: View {
    @EnvironmentObject var viewModel: BitcoinViewModel
    @Environment(\.dismiss) private var dismiss

    let isPump: Bool

    @State private var currentInput = ""
    @State private var toastMessage: String? = nil

    private var colors: AppColors { AppColors(isDark: viewModel.isDarkMode) }
    private var title: String { isPump ? "Set Pump Alert" : "Set Dump Alert" }

    var body: some View {
        ZStack {
            colors.background.ignoresSafeArea()

            VStack(spacing: 0) {
                // Title
                Text(title)
                .font(.system(size: 18, weight: .medium))
                .foregroundColor(colors.textPrimary)
                .padding(.top, 20)
                .padding(.bottom, 12)

                // Price display
                Text(viewModel.formatInputDisplay(currentInput))
                .font(.system(size: 32, weight: .thin))
                .foregroundColor(colors.textPrimary)
                .frame(maxWidth: .infinity, minHeight: 50)
                .background(colors.inputSurface)
                .padding(.horizontal, 16)
                .padding(.bottom, 16)

                // Numpad
                numpad
                .padding(.horizontal, 16)
                .padding(.bottom, 8)

                // Action buttons
                HStack(spacing: 16) {
                    Button {
                        UIImpactFeedbackGenerator(style: .light).impactOccurred()
                        dismiss()
                    } label: {
                        Text("Cancel")
                        .font(.system(size: 16, weight: .medium))
                        .foregroundColor(colors.textPrimary)
                        .frame(maxWidth: .infinity, minHeight: 48)
                        .background(colors.inputSurface)
                        .clipShape(Capsule())
                        .overlay(Capsule().stroke(colors.textPrimary.opacity(0.3), lineWidth: 1))
                    }
                    .buttonStyle(PressableButtonStyle())

                    Button {
                        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                        createAlert()
                    } label: {
                        Text("Create Alert")
                        .font(.system(size: 16, weight: .medium))
                        .foregroundColor(colors.btnText)
                        .frame(maxWidth: .infinity, minHeight: 48)
                        .background(colors.textPrimary)
                        .clipShape(Capsule())
                    }
                    .buttonStyle(PressableButtonStyle())
                }
                .padding(.horizontal, 16)
                .padding(.top, 16)
                .padding(.bottom, 24)
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
    }

    // 3-column numpad grid matching bottom_sheet_price_input.xml layout
    private var numpad: some View {
        let rows: [[String]] = [
            ["1", "2", "3"],
            ["4", "5", "6"],
            ["7", "8", "9"],
            ["C", "0", "⌫"]
        ]

        return VStack(spacing: 8) {
            ForEach(rows, id: \.self) { row in
                HStack(spacing: 8) {
                    ForEach(row, id: \.self) { key in
                        Button {
                            UIImpactFeedbackGenerator(style: .light).impactOccurred()
                            handleKey(key)
                        } label: {
                            Text(key)
                            .font(.system(size: 22, weight: key == "C" || key == "⌫" ? .medium : .light))
                            .foregroundColor(colors.textPrimary)
                            .frame(maxWidth: .infinity, minHeight: 64)
                            .background(colors.inputSurface)
                            .clipShape(Capsule())
                            .overlay(Capsule().stroke(colors.textPrimary.opacity(0.2), lineWidth: 1))
                        }
                        .buttonStyle(PressableButtonStyle())
                    }
                }
            }
        }
    }

    private func handleKey(_ key: String) {
        switch key {
        case "C":
            currentInput = ""
        case "⌫":
            if !currentInput.isEmpty { currentInput.removeLast() }
        default:
            if currentInput.count < 10 { currentInput += key }
        }
    }

    private func createAlert() {
        if currentInput.isEmpty {
            // Empty input = clear the alert
            if isPump { viewModel.clearPumpAlert() } else { viewModel.clearDumpAlert() }
            let msg = isPump ? "Pump alert cleared" : "Dump alert cleared"
            showToast(msg)
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) { dismiss() }
            return
        }

        guard let target = Float(currentInput), target > 0 else {
            showToast("Please enter a valid price > $0")
            return
        }

        if isPump {
            viewModel.setPumpAlert(value: target)
            showToast("Pump Alert Set!")
        } else {
            viewModel.setDumpAlert(value: target)
            showToast("Dump Alert Set!")
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) { dismiss() }
    }

    private func showToast(_ message: String) {
        withAnimation { toastMessage = message }
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
            withAnimation { toastMessage = nil }
        }
    }
}