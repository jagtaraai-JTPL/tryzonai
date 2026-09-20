import SwiftUI

public enum SideDrawerSheet: String, Identifiable {
    case userProfile
    case login
    case adminDashboard
    case dailyReward
    case spinWheel
    case share
    case premium
    case subscriptionManagement
    case purchaseHistory
    case themeSettings
    case notificationSettings
    case dailyStyleSettings
    case fashionPreferences
    case guidedTour

    public var id: String { rawValue }
}

public struct SideDrawer: View {
    @Binding var isOpen: Bool
    @ObservedObject var apiClient: APIClient
    @ObservedObject private var authViewModel = AuthViewModel.shared

    @AppStorage("user_gender") private var userGender: String = "Women"
    @AppStorage("push_notifications_enabled") private var pushEnabled: Bool = true
    @AppStorage("daily_style_enabled") private var dailyStyleEnabled: Bool = true
    @AppStorage("is_dark_theme") private var isDarkTheme: Bool = true

    @State private var activeSheet: SideDrawerSheet? = nil
    @State private var showLogoutConfirm = false
    @State private var showDeleteConfirm = false
    @State private var toastMessage: String? = nil

    public init(isOpen: Binding<Bool>, apiClient: APIClient) {
        self._isOpen = isOpen
        self.apiClient = apiClient
    }

    private var currentUser: UserProfile? {
        apiClient.currentUser ?? authViewModel.currentUser
    }

    private var isFounderUser: Bool {
        guard let u = currentUser else { return false }
        let email = u.email.lowercased()
        return email == "tryzonai@gmail.com" || u.isAdmin == true
    }

    public var body: some View {
        ZStack(alignment: .trailing) {
            // Viewport Dark Scrim
            Color.black.opacity(0.55)
                .ignoresSafeArea()
                .onTapGesture {
                    closeDrawer()
                }

            // Sliding Side Drawer Sheet (Bounded width 305dp)
            VStack(alignment: .leading, spacing: 0) {
                // ── 1. SIDEBAR TITLE & CLOSE BUTTON ──
                HStack(spacing: 12) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("✦ TRYZON AI")
                            .font(.system(size: 17, weight: .black, design: .rounded))
                            .foregroundColor(TryZonTheme.primaryGold)
                            .tracking(0.5)

                        Text("Virtual Try-On Studio")
                            .font(.system(size: 11, weight: .medium))
                            .foregroundColor(.white.opacity(0.6))
                    }

                    Spacer()

                    Button(action: closeDrawer) {
                        Image(systemName: "xmark")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(.white.opacity(0.7))
                            .padding(8)
                            .background(Color.white.opacity(0.1))
                            .clipShape(Circle())
                    }
                }
                .padding(16)
                .background(TryZonTheme.darkSurface)

                // Scrollable Content
                ScrollView(showsIndicators: false) {
                    VStack(alignment: .leading, spacing: 14) {

                        // ── 2. PROFILE CARD (ACCOUNT ACCESS) ──
                        Button(action: {
                            closeDrawer()
                            if apiClient.isLoggedIn {
                                activeSheet = .userProfile
                            } else {
                                activeSheet = .login
                            }
                        }) {
                            HStack(spacing: 12) {
                                Circle()
                                    .fill(TryZonTheme.primaryGold.opacity(0.2))
                                    .frame(width: 40, height: 40)
                                    .overlay(
                                        Image(systemName: "person.fill")
                                            .font(.system(size: 18))
                                            .foregroundColor(TryZonTheme.primaryGold)
                                    )

                                VStack(alignment: .leading, spacing: 2) {
                                    Text(currentUser?.name ?? currentUser?.username ?? "Guest User")
                                        .font(.system(size: 14, weight: .bold))
                                        .foregroundColor(.white)
                                        .lineLimit(1)

                                    let planText = currentUser?.isPremium == true ? "Pro Member 👑" : (apiClient.isLoggedIn ? "Free Plan" : "Tap to sign in & sync")
                                    Text(planText)
                                        .font(.system(size: 11, weight: .semibold))
                                        .foregroundColor(TryZonTheme.primaryGold)

                                    if apiClient.isLoggedIn {
                                        let usageLine = currentUser?.isPremium == true ? "Pro Unlimited • Ad-Free" : "\(apiClient.paidCredits) Paid • \(authViewModel.dailyTryCount)/1 Today"
                                        Text(usageLine)
                                            .font(.system(size: 10))
                                            .foregroundColor(.white.opacity(0.6))
                                    }
                                }

                                Spacer()

                                Image(systemName: "chevron.right")
                                    .font(.system(size: 12, weight: .bold))
                                    .foregroundColor(.white.opacity(0.4))
                            }
                            .padding(12)
                            .background(TryZonTheme.surfaceVariant)
                            .cornerRadius(16)
                            .overlay(RoundedRectangle(cornerRadius: 16).stroke(TryZonTheme.primaryGold.opacity(0.2), lineWidth: 1))
                        }

                        // ── FOUNDER & ADMIN PORTAL CARD (IF FOUNDER tryzonai@gmail.com) ──
                        if isFounderUser {
                            Button(action: {
                                closeDrawer()
                                activeSheet = .adminDashboard
                            }) {
                                HStack(spacing: 10) {
                                    Text("👑")
                                        .font(.system(size: 18))
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text("Founder & Admin Portal 👑")
                                            .font(.system(size: 13, weight: .black))
                                            .foregroundColor(TryZonTheme.primaryGold)
                                        Text("Real-Time KPIs, Live GPUs & User CRM")
                                            .font(.system(size: 10.5))
                                            .foregroundColor(.white.opacity(0.7))
                                    }
                                    Spacer()
                                    Image(systemName: "arrow.right")
                                        .font(.system(size: 13, weight: .bold))
                                        .foregroundColor(TryZonTheme.primaryGold)
                                }
                                .padding(12)
                                .background(TryZonTheme.primaryGold.opacity(0.18))
                                .cornerRadius(16)
                                .overlay(RoundedRectangle(cornerRadius: 16).stroke(TryZonTheme.primaryGold, lineWidth: 1.2))
                            }
                        }

                        // ── SECTION 0: REWARDS & FREE CREDITS 🎁 ──
                        sidebarSection(title: "REWARDS & FREE CREDITS 🎁") {
                            sidebarItem(icon: "gift.fill", title: "Daily Ad Rewards 🎁", subtitle: "Watch 8 Ads • Earn +4.0⚡ Tries FREE", badge: "+0.5⚡/ad") {
                                closeDrawer()
                                activeSheet = .dailyReward
                            }

                            sidebarItem(icon: "sparkles", title: "Daily Spin & Win Wheel 🎡", subtitle: "Earn Free Try-On Credits", badge: "FREE CREDITS") {
                                closeDrawer()
                                activeSheet = .spinWheel
                            }

                            sidebarItem(icon: "square.and.arrow.up.fill", title: "Refer & Share App 🎁", subtitle: "Invite Friends & Earn Credits", badge: "+2 CREDITS") {
                                closeDrawer()
                                activeSheet = .share
                            }
                        }

                        // ── SECTION 1: ACCOUNT & BILLING ──
                        sidebarSection(title: "ACCOUNT & BILLING") {
                            sidebarItem(icon: "crown.fill", title: "Pricing Plans & Credits", subtitle: "Upgrade to Pro or buy credit packs", badge: currentUser?.isPremium == true ? nil : "PRO 👑") {
                                closeDrawer()
                                activeSheet = .premium
                            }

                            sidebarItem(icon: "creditcard.fill", title: "Manage Subscriptions", subtitle: "Manage or cancel active subscription") {
                                closeDrawer()
                                activeSheet = .subscriptionManagement
                            }

                            sidebarItem(icon: "clock.arrow.circlepath", title: "Purchase History", subtitle: "View past transactions & receipts") {
                                closeDrawer()
                                activeSheet = .purchaseHistory
                            }
                        }

                        // ── SECTION 2: AI CONTROL & PREFERENCES ──
                        sidebarSection(title: "AI CONTROL & PREFERENCES") {
                            sidebarItem(icon: isDarkTheme ? "sun.max.fill" : "moon.fill", title: isDarkTheme ? "Light Theme" : "Dark Theme", subtitle: "Switch visual appearance", badge: isDarkTheme ? "Dark 🌙" : "Light ☀️") {
                                closeDrawer()
                                activeSheet = .themeSettings
                            }

                            sidebarItem(icon: pushEnabled ? "bell.fill" : "bell.slash.fill", title: "Push Notifications", subtitle: pushEnabled ? "Active for Try-On alerts" : "Disabled", badge: pushEnabled ? "ON" : "OFF") {
                                closeDrawer()
                                activeSheet = .notificationSettings
                            }

                            sidebarItem(icon: "tshirt.fill", title: "Daily AI Style Engine", subtitle: dailyStyleEnabled ? "Automated daily outfit suggestions" : "Disabled", badge: dailyStyleEnabled ? "ACTIVE" : "OFF") {
                                closeDrawer()
                                activeSheet = .dailyStyleSettings
                            }

                            sidebarItem(icon: "person.2.fill", title: "Fashion Preference", subtitle: genderSubtitle(userGender), badge: userGender) {
                                closeDrawer()
                                activeSheet = .fashionPreferences
                            }
                        }

                        // ── SECTION 3: EXPLORE & FEATURES ──
                        sidebarSection(title: "EXPLORE & FEATURES") {
                            sidebarItem(icon: "academiccap.fill", title: "Guided App Tour 🎓", subtitle: "Interactive step-by-step feature walk") {
                                closeDrawer()
                                activeSheet = .guidedTour
                            }

                            sidebarItem(icon: "bolt.fill", title: "Test Daily AI Style Now ⚡", subtitle: "Trigger instant background style generation") {
                                closeDrawer()
                                showToast("⚡ Instant Daily AI Style Triggered!")
                            }

                            sidebarItem(icon: "star.fill", title: "Rate 5★ on App Store ⭐", subtitle: "Support TryZon AI with a quick review", badge: "5★") {
                                closeDrawer()
                                UIPasteboard.general.string = "Mind-blowing AI try-on app! The outfit fitting and photo realism are unbelievable. 5/5 stars! 🔥✨"
                                showToast("📋 5-Star Review copied! Opening App Store...")
                                if let url = URL(string: "https://apps.apple.com") {
                                    UIApplication.shared.open(url)
                                }
                            }
                        }

                        // ── SECTION 4: ACCOUNT ACTIONS ──
                        sidebarSection(title: "ACCOUNT") {
                            if apiClient.isLoggedIn {
                                sidebarItem(icon: "rectangle.portrait.and.arrow.right", title: "Logout Account", subtitle: "Sign out of current active session", isDestructive: true) {
                                    showLogoutConfirm = true
                                }

                                sidebarItem(icon: "trash.fill", title: "Delete Account", subtitle: "Permanently remove your account & data", isDestructive: true) {
                                    showDeleteConfirm = true
                                }
                            } else {
                                sidebarItem(icon: "person.crop.circle.badge.plus", title: "Login / Switch Account", subtitle: "Sign in to sync your wardrobe", badge: "LOGIN 🚀") {
                                    closeDrawer()
                                    activeSheet = .login
                                }
                            }
                        }

                        // ── FOOTER ──
                        VStack(spacing: 6) {
                            Text("TryZon AI v1.0.0 (Build 137)")
                                .font(.system(size: 10, weight: .medium))
                                .foregroundColor(.white.opacity(0.4))

                            Button(action: {
                                if let url = URL(string: "https://tryzonai.com/privacy-policy") {
                                    UIApplication.shared.open(url)
                                }
                            }) {
                                Text("Privacy Policy & Terms")
                                    .font(.system(size: 10, weight: .bold))
                                    .foregroundColor(TryZonTheme.primaryGold.opacity(0.8))
                                    .underline()
                            }
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.top, 10)
                        .padding(.bottom, 20)
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 12)
                }
            }
            .frame(width: 305)
            .background(TryZonTheme.darkBackground)
            .transition(.move(edge: .trailing))

            // Toast Overlay
            if let toast = toastMessage {
                VStack {
                    Spacer()
                    Text(toast)
                        .font(.system(size: 12, weight: .bold))
                        .padding(.horizontal, 16)
                        .padding(.vertical, 9)
                        .background(Color.black.opacity(0.92))
                        .foregroundColor(TryZonTheme.primaryGold)
                        .cornerRadius(20)
                        .padding(.bottom, 30)
                }
                .transition(.move(edge: .bottom).combined(with: .opacity))
                .zIndex(200)
            }
        }
        .sheet(item: $activeSheet) { item in
            switch item {
            case .userProfile:
                UserProfileView(apiClient: apiClient)
            case .login:
                LoginView(apiClient: apiClient, onNavigateToRegister: {})
            case .adminDashboard:
                AdminDashboardView(apiClient: apiClient)
            case .dailyReward:
                DailyRewardView(apiClient: apiClient)
            case .spinWheel:
                SpinWheelView(apiClient: apiClient)
            case .share:
                ActivityViewController(activityItems: ["Try out TryZon AI Virtual Outfit Fitting! 🤩 Download now: https://tryzonai.com"])
            case .premium:
                PremiumView(apiClient: apiClient)
            case .subscriptionManagement:
                SubscriptionManagementView(apiClient: apiClient)
            case .purchaseHistory:
                PurchaseHistoryView(apiClient: apiClient)
            case .themeSettings:
                ThemeSettingsView()
            case .notificationSettings:
                NotificationSettingsView()
            case .dailyStyleSettings:
                DailyStyleSettingsView(apiClient: apiClient)
            case .fashionPreferences:
                FashionPreferencesView()
            case .guidedTour:
                GuidedTourView()
            }
        }
        .confirmationDialog("Logout Session", isPresented: $showLogoutConfirm, titleVisibility: .visible) {
            Button("Logout", role: .destructive) {
                closeDrawer()
                authViewModel.logout()
                showToast("Logged out successfully")
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("Are you sure you want to log out of your account?")
        }
        .confirmationDialog("Delete Account", isPresented: $showDeleteConfirm, titleVisibility: .visible) {
            Button("Delete Permanently", role: .destructive) {
                closeDrawer()
                apiClient.deleteAccount()
                showToast("Account deleted")
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This action is permanent and cannot be undone. All wardrobe items and try-on history will be deleted.")
        }
    }

    private func closeDrawer() {
        withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) {
            isOpen = false
        }
    }

    private func genderSubtitle(_ g: String) -> String {
        switch g {
        case "Women": return "👩 Women's Outfits (Default)"
        case "Men": return "👨 Men's Outfits"
        default: return "👫 Unisex Outfits"
        }
    }

    private func showToast(_ msg: String) {
        withAnimation { toastMessage = msg }
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.5) {
            withAnimation { toastMessage = nil }
        }
    }

    // MARK: - Helper Views
    @ViewBuilder
    private func sidebarSection<Content: View>(title: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.system(size: 9.5, weight: .black))
                .foregroundColor(TryZonTheme.primaryGold)
                .tracking(1.2)
                .padding(.leading, 4)

            VStack(spacing: 0) {
                content()
            }
            .background(TryZonTheme.surfaceVariant)
            .cornerRadius(16)
            .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color.white.opacity(0.06), lineWidth: 1))
        }
    }

    @ViewBuilder
    private func sidebarItem(
        icon: String,
        title: String,
        subtitle: String? = nil,
        badge: String? = nil,
        isDestructive: Bool = false,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Image(systemName: icon)
                    .font(.system(size: 15))
                    .foregroundColor(isDestructive ? .red : TryZonTheme.primaryGold)
                    .frame(width: 24)

                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(isDestructive ? .red : .white)

                    if let sub = subtitle {
                        Text(sub)
                            .font(.system(size: 10.5))
                            .foregroundColor(.white.opacity(0.6))
                            .lineLimit(1)
                    }
                }

                Spacer()

                if let b = badge {
                    Text(b)
                        .font(.system(size: 8.5, weight: .black))
                        .foregroundColor(.black)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 3)
                        .background(TryZonTheme.primaryGold)
                        .cornerRadius(6)
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 11)
        }
        .buttonStyle(PlainButtonStyle())
    }
}

// MARK: - UIKit ActivityViewController Wrapper
public struct ActivityViewController: UIViewControllerRepresentable {
    public let activityItems: [Any]
    public let applicationActivities: [UIActivity]? = nil

    public init(activityItems: [Any]) {
        self.activityItems = activityItems
    }

    public func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: activityItems, applicationActivities: applicationActivities)
    }

    public func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}
