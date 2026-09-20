import SwiftUI

public struct UserProfileView: View {
    @ObservedObject var apiClient: APIClient
    @ObservedObject private var authViewModel = AuthViewModel.shared
    @Environment(\.dismiss) private var dismiss
    @Environment(\.colorScheme) private var colorScheme

    @State private var activeSheet: UserProfileSheet? = nil
    @State private var showLogoutConfirm = false
    @State private var showDeleteConfirm = false
    @State private var toastMessage: String? = nil

    public init(apiClient: APIClient) {
        self.apiClient = apiClient
    }

    private var currentUser: UserProfile? {
        apiClient.currentUser ?? authViewModel.currentUser
    }

    public var body: some View {
        NavigationView {
            ScrollView(showsIndicators: false) {
                VStack(spacing: 20) {
                    Spacer(minLength: 10)

                    // ── 1. PROFILE HEADER CARD ──
                    VStack(spacing: 12) {
                        ZStack(alignment: .bottomTrailing) {
                            Circle()
                                .fill(TryZonTheme.primaryGold.opacity(0.18))
                                .frame(width: 86, height: 86)
                                .overlay(
                                    Image(systemName: "person.fill")
                                        .font(.system(size: 42))
                                        .foregroundColor(TryZonTheme.primaryGold)
                                )

                            if currentUser?.isPremium == true {
                                Text("👑")
                                    .font(.system(size: 20))
                                    .padding(4)
                                    .background(Color.black)
                                    .clipShape(Circle())
                            }
                        }

                        VStack(spacing: 4) {
                            Text(currentUser?.name ?? currentUser?.username ?? "TryZon Member")
                                .font(.system(size: 20, weight: .bold))
                                .foregroundColor(TryZonTheme.textColor(for: colorScheme))

                            Text(currentUser?.email ?? "No Email Registered")
                                .font(.system(size: 13, weight: .medium))
                                .foregroundColor(TryZonTheme.subtextColor(for: colorScheme))

                            HStack(spacing: 6) {
                                Text(currentUser?.isPremium == true ? "PRO SUBSCRIBER 👑" : "FREE MEMBER ✨")
                                    .font(.system(size: 10, weight: .black))
                                    .padding(.horizontal, 10)
                                    .padding(.vertical, 4)
                                    .background(currentUser?.isPremium == true ? TryZonTheme.primaryGold : Color.gray.opacity(0.2))
                                    .foregroundColor(currentUser?.isPremium == true ? .black : TryZonTheme.textColor(for: colorScheme))
                                    .clipShape(Capsule())

                                if currentUser?.isAdmin == true {
                                    Text("FOUNDER / ADMIN 🛡️")
                                        .font(.system(size: 10, weight: .black))
                                        .padding(.horizontal, 10)
                                        .padding(.vertical, 4)
                                        .background(Color.purple)
                                        .foregroundColor(.white)
                                        .clipShape(Capsule())
                                }
                            }
                            .padding(.top, 4)
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .padding(20)
                    .background(TryZonTheme.surfaceVariantColor(for: colorScheme))
                    .cornerRadius(24)

                    // ── 2. CREDIT & QUOTA SUMMARY GRID ──
                    VStack(alignment: .leading, spacing: 12) {
                        Text("ACCOUNT BALANCE & QUOTA ⚡")
                            .font(.system(size: 10, weight: .black))
                            .foregroundColor(TryZonTheme.primaryGold)
                            .tracking(1)

                        HStack(spacing: 12) {
                            // Paid Credits
                            VStack(alignment: .leading, spacing: 6) {
                                HStack {
                                    Image(systemName: "bolt.fill")
                                        .foregroundColor(TryZonTheme.primaryGold)
                                    Text("Paid Credits")
                                        .font(.system(size: 11, weight: .bold))
                                        .foregroundColor(TryZonTheme.subtextColor(for: colorScheme))
                                }
                                Text("\(apiClient.paidCredits) ⚡")
                                    .font(.system(size: 22, weight: .black, design: .rounded))
                                    .foregroundColor(TryZonTheme.textColor(for: colorScheme))
                                Text("Never Expire")
                                    .font(.system(size: 9.5, weight: .semibold))
                                    .foregroundColor(TryZonTheme.primaryGold)
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(14)
                            .background(TryZonTheme.surfaceVariantColor(for: colorScheme))
                            .cornerRadius(18)

                            // Daily Quota
                            VStack(alignment: .leading, spacing: 6) {
                                HStack {
                                    Image(systemName: "calendar")
                                        .foregroundColor(.green)
                                    Text("Daily Free Try")
                                        .font(.system(size: 11, weight: .bold))
                                        .foregroundColor(TryZonTheme.subtextColor(for: colorScheme))
                                }
                                Text("\(authViewModel.dailyTryCount)/1 Free")
                                    .font(.system(size: 22, weight: .black, design: .rounded))
                                    .foregroundColor(TryZonTheme.textColor(for: colorScheme))
                                Text("Resets Midnight UTC")
                                    .font(.system(size: 9.5, weight: .semibold))
                                    .foregroundColor(.green)
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(14)
                            .background(TryZonTheme.surfaceVariantColor(for: colorScheme))
                            .cornerRadius(18)
                        }
                    }

                    // Top-Up Call to Action
                    Button(action: { activeSheet = .premium }) {
                        HStack {
                            Image(systemName: "crown.fill")
                                .font(.system(size: 16))
                                .foregroundColor(.black)
                            Text("BUY CREDITS OR UPGRADE TO PRO")
                                .font(.system(size: 13, weight: .black, design: .rounded))
                                .foregroundColor(.black)
                            Spacer()
                            Image(systemName: "arrow.right")
                                .font(.system(size: 13, weight: .bold))
                                .foregroundColor(.black)
                        }
                        .padding(16)
                        .background(TryZonTheme.primaryGold)
                        .cornerRadius(20)
                        .shadow(color: TryZonTheme.primaryGold.opacity(0.3), radius: 6, y: 3)
                    }
                    .buttonStyle(BounceButtonStyle())

                    // ── 3. ACCOUNT ACTIONS ──
                    VStack(alignment: .leading, spacing: 12) {
                        Text("ACCOUNT SETTINGS")
                            .font(.system(size: 10, weight: .black))
                            .foregroundColor(TryZonTheme.primaryGold)
                            .tracking(1)

                        VStack(spacing: 0) {
                            Button(action: { activeSheet = .dailyReward }) {
                                HStack {
                                    Image(systemName: "gift.fill")
                                        .foregroundColor(TryZonTheme.primaryGold)
                                    Text("Daily Ad Rewards 🎁")
                                        .font(.system(size: 14, weight: .bold))
                                        .foregroundColor(TryZonTheme.textColor(for: colorScheme))
                                    Spacer()
                                    Text("+4.0⚡ Free")
                                        .font(.system(size: 11, weight: .black))
                                        .foregroundColor(TryZonTheme.primaryGold)
                                    Image(systemName: "chevron.right")
                                        .font(.system(size: 12, weight: .bold))
                                        .foregroundColor(.gray)
                                }
                                .padding(14)
                            }

                            Divider().padding(.horizontal, 14)

                            Button(action: { showLogoutConfirm = true }) {
                                HStack {
                                    Image(systemName: "rectangle.portrait.and.arrow.right")
                                        .foregroundColor(.red)
                                    Text("Logout Account")
                                        .font(.system(size: 14, weight: .bold))
                                        .foregroundColor(.red)
                                    Spacer()
                                    Image(systemName: "chevron.right")
                                        .font(.system(size: 12, weight: .bold))
                                        .foregroundColor(.gray)
                                }
                                .padding(14)
                            }

                            Divider().padding(.horizontal, 14)

                            Button(action: { showDeleteConfirm = true }) {
                                HStack {
                                    Image(systemName: "trash.fill")
                                        .foregroundColor(.red)
                                    Text("Delete Account Permanently")
                                        .font(.system(size: 14, weight: .bold))
                                        .foregroundColor(.red)
                                    Spacer()
                                    Image(systemName: "chevron.right")
                                        .font(.system(size: 12, weight: .bold))
                                        .foregroundColor(.gray)
                                }
                                .padding(14)
                            }
                        }
                        .background(TryZonTheme.surfaceVariantColor(for: colorScheme))
                        .cornerRadius(18)
                    }

                    Spacer(minLength: 20)
                }
                .padding(20)
            }
            .navigationTitle("User Profile")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") { dismiss() }
                        .font(.system(size: 14, weight: .bold))
                }
            }
            .sheet(item: $activeSheet) { item in
                switch item {
                case .premium:
                    PremiumView(apiClient: apiClient)
                case .dailyReward:
                    DailyRewardView(apiClient: apiClient)
                }
            }
            .confirmationDialog("Logout", isPresented: $showLogoutConfirm, titleVisibility: .visible) {
                Button("Logout", role: .destructive) {
                    authViewModel.logout()
                    dismiss()
                }
                Button("Cancel", role: .cancel) {}
            } message: {
                Text("Are you sure you want to log out?")
            }
            .confirmationDialog("Delete Account", isPresented: $showDeleteConfirm, titleVisibility: .visible) {
                Button("Delete Permanently", role: .destructive) {
                    apiClient.deleteAccount()
                    dismiss()
                }
                Button("Cancel", role: .cancel) {}
            } message: {
                Text("All try-on history and saved closet outfits will be erased permanently.")
            }
        }
    }
}
