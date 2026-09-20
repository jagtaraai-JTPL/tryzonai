import SwiftUI
import Combine

public class AuthViewModel: ObservableObject {
    public static let shared = AuthViewModel()

    @Published public var currentUser: UserProfile?
    @Published public var isLoggedIn: Bool = false
    @Published public var isLifetimeFirstTryOnDone: Bool = false
    @Published public var dailyTryCount: Int = 0

    @Published public var showLoginRequiredModal: Bool = false
    @Published public var showChoicePopupModal: Bool = false
    @Published public var showSpinWheelModal: Bool = false

    private let apiClient = APIClient.shared

    private init() {
        self.isLifetimeFirstTryOnDone = UserDefaults.standard.bool(forKey: "is_lifetime_first_tryon_done")
        
        // Reset daily try count if date has changed
        let lastTryDate = UserDefaults.standard.string(forKey: "last_try_date") ?? ""
        let todayStr = AuthViewModel.todayDateString()
        if lastTryDate != todayStr {
            self.dailyTryCount = 0
            UserDefaults.standard.set(0, forKey: "daily_try_count")
            UserDefaults.standard.set(todayStr, forKey: "last_try_date")
        } else {
            self.dailyTryCount = UserDefaults.standard.integer(forKey: "daily_try_count")
        }
        
        if apiClient.authToken != nil && apiClient.isRealUser {
            self.isLoggedIn = true
            self.currentUser = apiClient.currentUser
        } else {
            self.isLoggedIn = false
            self.currentUser = nil
        }
    }

    public static func todayDateString() -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        formatter.timeZone = TimeZone(abbreviation: "UTC")
        return formatter.string(from: Date())
    }

    public func markFirstTryOnDone() {
        isLifetimeFirstTryOnDone = true
        UserDefaults.standard.set(true, forKey: "is_lifetime_first_tryon_done")
    }

    public func canExecuteTryOn() -> Bool {
        // Rule 1: 1st Lifetime Try-On is 100% Free & Zero Login Required
        if !isLifetimeFirstTryOnDone {
            return true
        }

        // Rule 2: 2nd Try-On Onwards requires mandatory login
        if !apiClient.isLoggedIn {
            showLoginRequiredModal = true
            return false
        }

        // Rule 6: Pro subscribers get unlimited access
        if currentUser?.isPremium == true {
            return true
        }

        // Check if daily reset is needed
        let lastTryDate = UserDefaults.standard.string(forKey: "last_try_date") ?? ""
        let todayStr = AuthViewModel.todayDateString()
        if lastTryDate != todayStr {
            dailyTryCount = 0
            UserDefaults.standard.set(0, forKey: "daily_try_count")
            UserDefaults.standard.set(todayStr, forKey: "last_try_date")
        }

        // Rule 3: Free logged-in users get 1 free try daily (dailyTryCount < 1)
        if dailyTryCount < 1 {
            return true
        }

        // Paid credits or Bonus credits available -> Show Smart Choice Popup or allow execution
        if apiClient.paidCredits > 0 || apiClient.bonusCredits > 0 {
            showChoicePopupModal = true
            return false
        }

        // Daily limit reached (0/1 Free) and no credits -> Show Choice Popup
        showChoicePopupModal = true
        return false
    }

    public func deductTryOnCredit() {
        if !isLifetimeFirstTryOnDone {
            markFirstTryOnDone()
            return
        }

        let todayStr = AuthViewModel.todayDateString()
        UserDefaults.standard.set(todayStr, forKey: "last_try_date")

        if dailyTryCount < 1 {
            dailyTryCount += 1
            UserDefaults.standard.set(dailyTryCount, forKey: "daily_try_count")
            return
        }

        if apiClient.paidCredits > 0 {
            apiClient.paidCredits -= 1
        } else if apiClient.bonusCredits > 0 {
            apiClient.bonusCredits -= 1
        } else if apiClient.userCredits > 0 {
            apiClient.userCredits -= 1
        }
        dailyTryCount += 1
        UserDefaults.standard.set(dailyTryCount, forKey: "daily_try_count")
    }

    public func logout() {
        apiClient.logout()
        currentUser = nil
        isLoggedIn = false
    }
}
