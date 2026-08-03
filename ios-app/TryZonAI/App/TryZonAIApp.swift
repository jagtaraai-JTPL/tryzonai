import SwiftUI
import UIKit

@main
struct TryZonAIApp: App {
    @StateObject private var storeKit = StoreKitManager.shared
    @StateObject private var apiClient = APIClient.shared

    init() {
        // Request Local Notification Permissions on App Launch
        NotificationManager.shared.requestAuthorization()
    }

    var body: some Scene {
        WindowGroup {
            TabView {
                HomeScreen()
                    .tabItem {
                        Label("Home", systemImage: "sparkles")
                    }

                TryOnUploadView()
                    .tabItem {
                        Label("Fitting Room", systemImage: "camera.fill")
                    }

                WardrobeView()
                    .tabItem {
                        Label("Wardrobe", systemImage: "tshirt.fill")
                    }

                PremiumView()
                    .tabItem {
                        Label("Store", systemImage: "bag.fill")
                    }
            }
            .accentColor(.purple)
            .onReceive(NotificationCenter.default.publisher(for: UIApplication.willEnterForegroundNotification)) { _ in
                // Check Clipboard for copied Myntra/Amazon/Meesho shopping links
                NotificationManager.shared.checkClipboardForShoppingLink()
            }
        }
    }
}
