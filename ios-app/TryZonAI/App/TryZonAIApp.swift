import SwiftUI

@main
struct TryZonAIApp: App {
    @AppStorage("onboarding_completed") private var onboardingCompleted: Bool = false

    var body: some Scene {
        WindowGroup {
            Group {
                if onboardingCompleted {
                    MainTabView()
                } else {
                    OnboardingView(onFinish: {
                        onboardingCompleted = true
                    })
                }
            }
            .onAppear {
                NotificationManager.shared.requestAuthorization()
            }
            .onReceive(NotificationCenter.default.publisher(for: UIApplication.willEnterForegroundNotification)) { _ in
                NotificationManager.shared.checkClipboardForShoppingLink()
            }
        }
    }
}
