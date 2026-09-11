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
        self.dailyTryCount = UserDefaults.standard.integer(forKey: "daily_try_count")
        
        if apiClient.authToken != nil && apiClient.isRealUser {
            self.isLoggedIn = true
            self.currentUser = apiClient.currentUser
        } else {
            self.isLoggedIn = false
            self.currentUser = nil
        }
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

        // Rule 3: Free logged-in users get 1 free try daily
        if apiClient.userCredits > 0 || apiClient.paidCredits > 0 || (currentUser?.isPremium ?? false) {
            return true
        }

        // Daily limit reached
        showChoicePopupModal = true
        return false
    }

    public func deductTryOnCredit() {
        if !isLifetimeFirstTryOnDone {
            markFirstTryOnDone()
            return
        }

        if apiClient.paidCredits > 0 {
            apiClient.paidCredits -= 1
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
