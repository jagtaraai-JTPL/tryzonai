import SwiftUI

public struct SideDrawer: View {
    @Binding var isOpen: Bool
    @ObservedObject var apiClient: APIClient
    
    @AppStorage("user_gender") private var userGender: String = "Women"
    @AppStorage("is_dark_theme") private var isDarkTheme: Bool = true
    @AppStorage("push_notifications_enabled") private var pushEnabled: Bool = true
    @AppStorage("daily_style_enabled") private var dailyStyleEnabled: Bool = true

    @State private var showLogoutDialog = false
    @State private var showDeleteDialog = false

    public init(isOpen: Binding<Bool>, apiClient: APIClient) {
        self._isOpen = isOpen
        self.apiClient = apiClient
    }

    public var body: some View {
        ZStack(alignment: .trailing) {
            if isOpen {
                // Dimmed Overlay
                Color.black.opacity(0.6)
                    .ignoresSafeArea()
                    .onTapGesture {
                        withAnimation(.easeInOut(duration: 0.25)) {
                            isOpen = false
                        }
                    }

                // Sliding Drawer
                VStack(alignment: .leading, spacing: 0) {
                    // Profile Header
                    HStack(spacing: 12) {
                        ZStack {
                            Circle()
                                .fill(TryZonTheme.primaryGold.opacity(0.2))
                                .frame(width: 48, height: 48)
                            Image(systemName: "person.crop.circle.fill")
                                .font(.system(size: 32))
                                .foregroundColor(TryZonTheme.primaryGold)
                        }

                        VStack(alignment: .leading, spacing: 2) {
                            Text(apiClient.currentUser?.name ?? (apiClient.currentUser != nil ? "TryZon Member" : "Guest Stylist"))
                                .font(.system(size: 16, weight: .bold))
                                .foregroundColor(.white)
                            Text(apiClient.currentUser?.email ?? "Sign in for unlimited cloud sync")
                                .font(.system(size: 11))
                                .foregroundColor(.white.opacity(0.6))
                        }
                        Spacer()
                    }
                    .padding(16)
                    .background(TryZonTheme.surfaceVariant)

                    Divider().background(TryZonTheme.cardBorder)

                    // Scrollable Drawer Content
                    ScrollView {
                        VStack(alignment: .leading, spacing: 18) {
                            // Section: Preferences
                            Group {
                                Text("PREFERENCES")
                                    .font(.system(size: 10, weight: .bold))
                                    .foregroundColor(TryZonTheme.primaryGold)

                                // Gender Preference Selector
                                HStack {
                                    Image(systemName: "person.2.fill")
                                        .foregroundColor(.white.opacity(0.7))
                                    Text("Model Gender Filter")
                                        .font(.system(size: 14, weight: .medium))
                                        .foregroundColor(.white)
                                    Spacer()
                                    Picker("Gender", selection: $userGender) {
                                        Text("Women").tag("Women")
                                        Text("Men").tag("Men")
                                        Text("Unisex").tag("Unisex")
                                    }
                                    .pickerStyle(MenuPickerStyle())
                                    .accentColor(TryZonTheme.primaryGold)
                                }

                                // Daily AI Style Engine Toggle
                                Toggle(isOn: $dailyStyleEnabled) {
                                    HStack {
                                        Image(systemName: "wand.and.stars")
                                            .foregroundColor(TryZonTheme.primaryGold)
                                        VStack(alignment: .leading, spacing: 2) {
                                            Text("Daily AI Style Push")
                                                .font(.system(size: 14, weight: .medium))
                                                .foregroundColor(.white)
                                            Text("Automated daily outfit suggestions")
                                                .font(.system(size: 10))
                                                .foregroundColor(.white.opacity(0.5))
                                        }
                                    }
                                }
                                .toggleStyle(SwitchToggleStyle(tint: TryZonTheme.primaryGold))

                                // Push Notifications Toggle
                                Toggle(isOn: $pushEnabled) {
                                    HStack {
                                        Image(systemName: "bell.fill")
                                            .foregroundColor(.white.opacity(0.7))
                                        Text("Push Notifications")
                                            .font(.system(size: 14, weight: .medium))
                                            .foregroundColor(.white)
                                    }
                                }
                                .toggleStyle(SwitchToggleStyle(tint: TryZonTheme.primaryGold))
                            }

                            Divider().background(TryZonTheme.cardBorder)

                            // Section: Account & Security
                            Group {
                                Text("ACCOUNT & LEGAL")
                                    .font(.system(size: 10, weight: .bold))
                                    .foregroundColor(TryZonTheme.primaryGold)

                                Button(action: {
                                    if let url = URL(string: "https://tryzonai.com/privacy-policy") {
                                        UIApplication.shared.open(url)
                                    }
                                }) {
                                    HStack {
                                        Image(systemName: "shield.fill")
                                            .foregroundColor(.white.opacity(0.7))
                                        Text("Privacy Policy & Terms")
                                            .font(.system(size: 14, weight: .medium))
                                            .foregroundColor(.white)
                                        Spacer()
                                        Image(systemName: "arrow.up.right")
                                            .font(.system(size: 12))
                                            .foregroundColor(.white.opacity(0.4))
                                    }
                                }

                                if apiClient.currentUser != nil {
                                    Button(action: { showLogoutDialog = true }) {
                                        HStack {
                                            Image(systemName: "rectangle.portrait.and.arrow.right")
                                                .foregroundColor(.red.opacity(0.8))
                                            Text("Logout Session")
                                                .font(.system(size: 14, weight: .medium))
                                                .foregroundColor(.red.opacity(0.9))
                                        }
                                    }

                                    Button(action: { showDeleteDialog = true }) {
                                        HStack {
                                            Image(systemName: "trash.fill")
                                                .foregroundColor(.red)
                                            Text("Delete Account")
                                                .font(.system(size: 14, weight: .bold))
                                                .foregroundColor(.red)
                                        }
                                    }
                                }
                            }
                        }
                        .padding(16)
                    }

                    Spacer()

                    // Footer
                    VStack(alignment: .center, spacing: 4) {
                        Text("TryZon AI v1.0.0 (Build 1)")
                            .font(.system(size: 10, weight: .medium))
                            .foregroundColor(.white.opacity(0.4))
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.bottom, 16)
                }
                .frame(width: 280)
                .background(TryZonTheme.darkSurface)
                .transition(.move(edge: .trailing))
                .alert(isPresented: $showLogoutDialog) {
                    Alert(
                        title: Text("Logout"),
                        message: Text("Are you sure you want to log out?"),
                        primaryButton: .destructive(Text("Logout")) {
                            apiClient.logout()
                            withAnimation { isOpen = false }
                        },
                        secondaryButton: .cancel()
                    )
                }
            }
        }
    }
}
