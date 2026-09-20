import SwiftUI

public struct NotificationSettingsView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.colorScheme) private var colorScheme

    @AppStorage("push_notifications_enabled") private var pushEnabled: Bool = true
    @AppStorage("notif_tryon_complete") private var notifTryonComplete: Bool = true
    @AppStorage("notif_daily_style") private var notifDailyStyle: Bool = true
    @AppStorage("notif_credit_refill") private var notifCreditRefill: Bool = true
    @AppStorage("notif_rewards_promos") private var notifRewardsPromos: Bool = true

    public init() {}

    public var body: some View {
        NavigationView {
            ScrollView(showsIndicators: false) {
                VStack(spacing: 20) {
                    Spacer(minLength: 10)

                    // Header
                    VStack(spacing: 10) {
                        Circle()
                            .fill(TryZonTheme.primaryGold.opacity(0.18))
                            .frame(width: 72, height: 72)
                            .overlay(
                                Image(systemName: "bell.badge.fill")
                                    .font(.system(size: 36))
                                    .foregroundColor(TryZonTheme.primaryGold)
                            )

                        Text("Push Notifications")
                            .font(.system(size: 22, weight: .bold))
                            .foregroundColor(TryZonTheme.textColor(for: colorScheme))

                        Text("Manage your TryZon AI alert preferences")
                            .font(.system(size: 13, weight: .medium))
                            .foregroundColor(TryZonTheme.subtextColor(for: colorScheme))
                    }

                    // Master Toggle
                    Toggle(isOn: $pushEnabled) {
                        HStack(spacing: 12) {
                            Image(systemName: "bell.fill")
                                .font(.system(size: 18))
                                .foregroundColor(TryZonTheme.primaryGold)

                            VStack(alignment: .leading, spacing: 2) {
                                Text("Allow Push Notifications")
                                    .font(.system(size: 15, weight: .bold))
                                    .foregroundColor(TryZonTheme.textColor(for: colorScheme))
                                Text("Master switch for all app notifications")
                                    .font(.system(size: 11))
                                    .foregroundColor(TryZonTheme.subtextColor(for: colorScheme))
                            }
                        }
                    }
                    .padding(16)
                    .background(TryZonTheme.surfaceVariantColor(for: colorScheme))
                    .cornerRadius(20)
                    .tint(TryZonTheme.primaryGold)

                    // Category Toggles
                    if pushEnabled {
                        VStack(alignment: .leading, spacing: 12) {
                            Text("NOTIFICATION CATEGORIES")
                                .font(.system(size: 10, weight: .black))
                                .foregroundColor(TryZonTheme.primaryGold)
                                .tracking(1)

                            VStack(spacing: 0) {
                                notificationToggleRow(title: "Try-On Ready Alerts ⚡", desc: "Notify when background HD rendering finishes", isOn: $notifTryonComplete)
                                Divider().padding(.horizontal, 14)
                                notificationToggleRow(title: "Daily AI Style Push ✨", desc: "Receive automated morning outfit recommendations", isOn: $notifDailyStyle)
                                Divider().padding(.horizontal, 14)
                                notificationToggleRow(title: "Daily Free Credit Reset 🎁", desc: "Remind when 1/1 free try resets at midnight UTC", isOn: $notifCreditRefill)
                                Divider().padding(.horizontal, 14)
                                notificationToggleRow(title: "Exclusive Offers & Promos 👑", desc: "First-buyer discounts and double-credit events", isOn: $notifRewardsPromos)
                            }
                            .background(TryZonTheme.surfaceVariantColor(for: colorScheme))
                            .cornerRadius(20)
                        }
                    }

                    Spacer(minLength: 20)
                }
                .padding(20)
            }
            .navigationTitle("Notification Settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") { dismiss() }
                        .font(.system(size: 14, weight: .bold))
                }
            }
        }
    }

    private func notificationToggleRow(title: String, desc: String, isOn: Binding<Bool>) -> some View {
        Toggle(isOn: isOn) {
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.system(size: 13.5, weight: .bold))
                    .foregroundColor(TryZonTheme.textColor(for: colorScheme))
                Text(desc)
                    .font(.system(size: 11))
                    .foregroundColor(TryZonTheme.subtextColor(for: colorScheme))
            }
        }
        .padding(14)
        .tint(TryZonTheme.primaryGold)
    }
}
