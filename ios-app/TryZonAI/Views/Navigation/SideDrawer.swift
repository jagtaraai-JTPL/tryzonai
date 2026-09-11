import SwiftUI

public struct SideDrawer: View {
    @Binding var isOpen: Bool
    @ObservedObject var apiClient: APIClient

    @AppStorage("user_gender") private var userGender: String = "Women"
    @AppStorage("push_notifications_enabled") private var pushEnabled: Bool = true
    @AppStorage("daily_style_enabled") private var dailyStyleEnabled: Bool = true

    @State private var showLogoutConfirm = false
    @State private var showDeleteConfirm = false

    public init(isOpen: Binding<Bool>, apiClient: APIClient) {
        self._isOpen = isOpen
        self.apiClient = apiClient
    }

    public var body: some View {
        ZStack(alignment: .trailing) {
            // Dimmed Backdrop Overlay
            Color.black.opacity(0.55)
                .ignoresSafeArea()
                .onTapGesture {
                    closeDrawer()
                }

            // Sliding Side Drawer Panel
            VStack(alignment: .leading, spacing: 0) {
                // Header with Close Button
                HStack(spacing: 12) {
                    ZStack {
                        Circle()
                            .fill(TryZonTheme.primaryGold.opacity(0.2))
                            .frame(width: 44, height: 44)
                        Image(systemName: "person.crop.circle.fill")
                            .font(.system(size: 28))
                            .foregroundColor(TryZonTheme.primaryGold)
                    }

                    VStack(alignment: .leading, spacing: 2) {
                        Text(apiClient.currentUser?.name ?? (apiClient.currentUser != nil ? "TryZon Member" : "Guest Stylist"))
                            .font(.system(size: 15, weight: .bold))
                            .foregroundColor(.white)
                            .lineLimit(1)
                        Text(apiClient.currentUser?.email ?? "Sign in for cloud sync & bonus")
                            .font(.system(size: 11))
                            .foregroundColor(.white.opacity(0.6))
                            .lineLimit(1)
                    }

                    Spacer()

                    // Close (X) Button
                    Button(action: closeDrawer) {
                        Image(systemName: "xmark.circle.fill")
                            .font(.system(size: 22))
                            .foregroundColor(.white.opacity(0.7))
                    }
                }
                .padding(16)
                .background(TryZonTheme.surfaceVariant)

                Divider().background(TryZonTheme.cardBorder)

                // Scrollable Content
                ScrollView {
                    VStack(alignment: .leading, spacing: 18) {
                        // Section: Preferences
                        Group {
                            Text("PREFERENCES")
                                .font(.system(size: 10, weight: .black))
                                .foregroundColor(TryZonTheme.primaryGold)
                                .tracking(1)

                            // Gender Preference Selector
                            HStack {
                                Image(systemName: "person.2.fill")
                                    .foregroundColor(.white.opacity(0.7))
                                Text("Model Gender Filter")
                                    .font(.system(size: 13, weight: .medium))
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
                                            .font(.system(size: 13, weight: .medium))
                                            .foregroundColor(.white)
                                        Text("Automated daily outfit fits")
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
                                        .font(.system(size: 13, weight: .medium))
                                        .foregroundColor(.white)
                                }
                            }
                            .toggleStyle(SwitchToggleStyle(tint: TryZonTheme.primaryGold))
                        }

                        Divider().background(TryZonTheme.cardBorder)

                        // Section: Account & Legal
                        Group {
                            Text("ACCOUNT & LEGAL")
                                .font(.system(size: 10, weight: .black))
                                .foregroundColor(TryZonTheme.primaryGold)
                                .tracking(1)

                            Button(action: {
                                if let url = URL(string: "https://tryzonai.com/privacy-policy") {
                                    UIApplication.shared.open(url)
                                }
                            }) {
                                HStack {
                                    Image(systemName: "shield.fill")
                                        .foregroundColor(.white.opacity(0.7))
                                    Text("Privacy Policy & Terms")
                                        .font(.system(size: 13, weight: .medium))
                                        .foregroundColor(.white)
                                    Spacer()
                                    Image(systemName: "arrow.up.right")
                                        .font(.system(size: 11))
                                        .foregroundColor(.white.opacity(0.4))
                                }
                            }

                            if apiClient.currentUser != nil {
                                Button(action: { showLogoutConfirm = true }) {
                                    HStack {
                                        Image(systemName: "rectangle.portrait.and.arrow.right")
                                            .foregroundColor(.red.opacity(0.8))
                                        Text("Logout Session")
                                            .font(.system(size: 13, weight: .medium))
                                            .foregroundColor(.red.opacity(0.9))
                                    }
                                }

                                Button(action: { showDeleteConfirm = true }) {
                                    HStack {
                                        Image(systemName: "trash.fill")
                                            .foregroundColor(.red)
                                        Text("Delete Account")
                                            .font(.system(size: 13, weight: .bold))
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
                    Text("TryZon AI v1.0.0 (Build 137)")
                        .font(.system(size: 10, weight: .medium))
                        .foregroundColor(.white.opacity(0.4))
                }
                .frame(maxWidth: .infinity)
                .padding(.bottom, 16)
            }
            .frame(width: 280)
            .background(TryZonTheme.darkSurface)
            .transition(.move(edge: .trailing))
        }
        .confirmationDialog("Logout Session", isPresented: $showLogoutConfirm, titleVisibility: .visible) {
            Button("Logout", role: .destructive) {
                apiClient.logout()
                closeDrawer()
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("Are you sure you want to log out of TryZon AI?")
        }
    }

    private func closeDrawer() {
        withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) {
            isOpen = false
        }
    }
}
