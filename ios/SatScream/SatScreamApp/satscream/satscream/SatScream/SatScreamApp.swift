import SwiftUI
import BackgroundTasks
import UserNotifications

// Delegate that allows notifications to show as banners even when app is foregrounded
class NotificationDelegate: NSObject, UNUserNotificationCenterDelegate {
    func userNotificationCenter(
    _ center: UNUserNotificationCenter,
    willPresent notification: UNNotification,
    withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        // Show banner + play sound even when the app is open (mirrors Android behavior)
        completionHandler([.banner, .sound, .badge])
    }

    func userNotificationCenter(
    _ center: UNUserNotificationCenter,
    didReceive response: UNNotificationResponse,
    withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        completionHandler()
    }
}

@main
struct SatScreamApp: App {
    @StateObject private var viewModel = BitcoinViewModel()
    @AppStorage("WELCOME_SHOWN") private var welcomeShown = false

    // Hold a strong reference so the delegate isn't deallocated
    private let notificationDelegate = NotificationDelegate()

    init() {
        // Set delegate before anything else so no notifications are missed
        UNUserNotificationCenter.current().delegate = notificationDelegate

        // Register background refresh task
        BGTaskScheduler.shared.register(
            forTaskWithIdentifier: "com.peerloomllc.satscream.pricerefresh",
            using: nil
        ) { task in
            task.setTaskCompleted(success: true)
        }
    }

    var body: some Scene {
        WindowGroup {
            Group {
                if welcomeShown {
                    MainView()
                    .environmentObject(viewModel)
                } else {
                    WelcomeView()
                    .environmentObject(viewModel)
                }
            }
            .preferredColorScheme(viewModel.isDarkMode ? .dark : .light)
            .onAppear {
                UNUserNotificationCenter.current().requestAuthorization(
                    options: [.alert, .sound, .badge]
                ) { _, _ in }
            }
        }
    }
}
