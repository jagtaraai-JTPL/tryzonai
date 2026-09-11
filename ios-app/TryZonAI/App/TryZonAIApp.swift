import SwiftUI

@main
struct TryZonAIApp: App {
    @AppStorage("onboarding_completed") private var onboardingCompleted: Bool = false

    var body: some Scene {
        WindowGroup {
            if onboardingCompleted {
                MainTabView()
            } else {
                OnboardingView(onFinish: {
                    onboardingCompleted = true
                })
            }
        }
    }
}
