import Foundation
import UserNotifications
import UIKit

public class NotificationManager: NSObject, UNUserNotificationCenterDelegate {
    public static let shared = NotificationManager()

    override private init() {
        super.init()
        UNUserNotificationCenter.current().delegate = self
    }

    // MARK: - Permission Request
    public func requestAuthorization() {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { granted, error in
            if granted {
                DispatchQueue.main.async {
                    self.scheduleDailyReminder()
                }
            }
        }
    }

    // MARK: - Daily Engagement Reminder
    public func scheduleDailyReminder() {
        let center = UNUserNotificationCenter.current()
        center.removePendingNotificationRequests(withIdentifiers: ["tryzon_daily_reminder"])

        let content = UNMutableNotificationContent()
        content.title = "🎁 3 Free AI Try-Ons Waiting For You!"
        content.body = "Your daily free try-ons have reset! Tap to upload a photo & try on trending outfits now. 👗✨"
        content.sound = .default

        // Trigger after 20 hours
        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: 72000, repeats: true)
        let request = UNNotificationRequest(identifier: "tryzon_daily_reminder", content: content, trigger: trigger)
        
        center.add(request)
    }

    // MARK: - Shopping Clipboard Detection
    public func checkClipboardForShoppingLink() {
        guard UIPasteboard.general.hasStrings, let clipText = UIPasteboard.general.string else { return }
        let lower = clipText.lowercased()
        let isShopping = lower.contains("myntra") || lower.contains("meesho") || lower.contains("amazon") ||
                         lower.contains("ajio") || lower.contains("flipkart") || lower.contains("zara") ||
                         lower.contains("nykaa") || lower.hasSuffix(".jpg") || lower.hasSuffix(".png")

        if isShopping {
            let lastClip = UserDefaults.standard.string(forKey: "last_ios_clipboard_url")
            if lastClip != clipText {
                UserDefaults.standard.set(clipText, forKey: "last_ios_clipboard_url")
                sendShoppingAlertNotification(urlText: clipText)
            }
        }
    }

    private func sendShoppingAlertNotification(urlText: String) {
        let content = UNMutableNotificationContent()
        content.title = "🛍️ Saw a cool outfit while shopping?"
        content.body = "Copied clothing link detected! Tap to see how it looks on YOU in 5 seconds! 👗✨"
        content.sound = .default

        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: 1, repeats: false)
        let request = UNNotificationRequest(identifier: "tryzon_shopping_alert", content: content, trigger: trigger)
        
        UNUserNotificationCenter.current().add(request)
    }

    // Delegate method to present notification even when app is in foreground
    public func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification, withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        completionHandler([.banner, .sound, .badge])
    }
}
