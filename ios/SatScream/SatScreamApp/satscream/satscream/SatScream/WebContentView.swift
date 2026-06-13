import SwiftUI
import WebKit

// UIViewRepresentable wrapper for WKWebView (mirrors WebViewActivity)
struct WebContentView: View {
    @EnvironmentObject var viewModel: BitcoinViewModel
    @Environment(\.dismiss) private var dismiss

    private var colors: AppColors { AppColors(isDark: viewModel.isDarkMode) }

    var body: some View {
        ZStack(alignment: .topLeading) {
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
                    .padding(.leading, 16)
                    .padding(.top, 16)
                    Spacer()
                }

                // WebView loading local HTML asset
                LocalWebView()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
    }
}

struct LocalWebView: UIViewRepresentable {
    func makeCoordinator() -> Coordinator { Coordinator() }

    func makeUIView(context: Context) -> WKWebView {
        let webView = WKWebView()
        webView.navigationDelegate = context.coordinator

        // Load local HTML from bundle (mirrors WebViewActivity loading from assets)
        if let url = Bundle.main.url(forResource: "use-lightning-network-modified", withExtension: "html") {
            webView.loadFileURL(url, allowingReadAccessTo: url.deletingLastPathComponent())
        }

        return webView
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {}

    class Coordinator: NSObject, WKNavigationDelegate {
        // Open external links in Safari (mirrors WebViewActivity's shouldOverrideUrlLoading)
        func webView(_ webView: WKWebView,
                     decidePolicyFor navigationAction: WKNavigationAction,
                     decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
            guard let url = navigationAction.request.url else {
                decisionHandler(.allow)
                return
            }
            // Allow file:// (local HTML), block and open externally for http/https
            if url.scheme == "file" {
                decisionHandler(.allow)
            } else {
                UIApplication.shared.open(url)
                decisionHandler(.cancel)
            }
        }
    }
}
