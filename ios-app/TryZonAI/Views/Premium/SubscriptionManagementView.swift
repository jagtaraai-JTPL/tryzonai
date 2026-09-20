import SwiftUI

public struct SubscriptionManagementView: View {
    @ObservedObject var apiClient: APIClient
    @Environment(\.dismiss) private var dismiss
    @Environment(\.colorScheme) private var colorScheme

    public init(apiClient: APIClient) {
        self.apiClient = apiClient
    }

    private var isPro: Bool {
        apiClient.currentUser?.isPremium == true
    }

    public var body: some View {
        NavigationView {
            ScrollView(showsIndicators: false) {
                VStack(spacing: 20) {
                    Spacer(minLength: 10)

                    // Card Header
                    VStack(spacing: 12) {
                        Circle()
                            .fill(TryZonTheme.primaryGold.opacity(0.2))
                            .frame(width: 72, height: 72)
                            .overlay(
                                Image(systemName: "crown.fill")
                                    .font(.system(size: 36))
                                    .foregroundColor(TryZonTheme.primaryGold)
                            )

                        Text(isPro ? "Pro VIP Subscription Active 👑" : "Free Plan Account ✨")
                            .font(.system(size: 20, weight: .bold))
                            .foregroundColor(TryZonTheme.textColor(for: colorScheme))

                        Text(isPro ? "Unlimited Daily Try-Ons • 100% Ad-Free • VIP Fast Pass Queue" : "Upgrade to Pro for unlimited try-ons and zero ads")
                            .font(.system(size: 13, weight: .medium))
                            .foregroundColor(TryZonTheme.subtextColor(for: colorScheme))
                            .multilineTextAlignment(.center)
                            .padding(.horizontal)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(24)
                    .background(TryZonTheme.surfaceVariantColor(for: colorScheme))
                    .cornerRadius(24)

                    // Plan Benefits
                    VStack(alignment: .leading, spacing: 14) {
                        Text("PRO PRIVILEGES INCLUDED 💎")
                            .font(.system(size: 10, weight: .black))
                            .foregroundColor(TryZonTheme.primaryGold)
                            .tracking(1)

                        VStack(spacing: 12) {
                            benefitRow(icon: "infinity", title: "Unlimited Try-Ons", desc: "No daily limit, no credit deductions")
                            benefitRow(icon: "nosign", title: "100% Zero Ads Forever", desc: "No video ads on try-ons, exports or app launch")
                            benefitRow(icon: "bolt.fill", title: "VIP Fast Pass GPU Queue", desc: "Instant high-speed AI rendering in seconds")
                            benefitRow(icon: "sparkles", title: "Daily AI Style Auto-Fitting", desc: "Automated daily outfit suggestions in background")
                        }
                    }
                    .padding(18)
                    .background(TryZonTheme.surfaceVariantColor(for: colorScheme))
                    .cornerRadius(20)

                    // Manage Button
                    Button(action: {
                        if let url = URL(string: "https://apps.apple.com/account/subscriptions") {
                            UIApplication.shared.open(url)
                        }
                    }) {
                        HStack {
                            Image(systemName: "applelogo")
                            Text("Manage via Apple ID Subscriptions ")
                                .font(.system(size: 14, weight: .black))
                        }
                        .foregroundColor(.black)
                        .frame(maxWidth: .infinity)
                        .frame(height: 50)
                        .background(TryZonTheme.primaryGold)
                        .cornerRadius(25)
                        .shadow(color: TryZonTheme.primaryGold.opacity(0.3), radius: 6, y: 3)
                    }
                    .buttonStyle(BounceButtonStyle())

                    Spacer(minLength: 20)
                }
                .padding(20)
            }
            .navigationTitle("Manage Subscription")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") { dismiss() }
                        .font(.system(size: 14, weight: .bold))
                }
            }
        }
    }

    private func benefitRow(icon: String, title: String, desc: String) -> some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 16, weight: .bold))
                .foregroundColor(TryZonTheme.primaryGold)
                .frame(width: 24)

            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(TryZonTheme.textColor(for: colorScheme))
                Text(desc)
                    .font(.system(size: 11))
                    .foregroundColor(TryZonTheme.subtextColor(for: colorScheme))
            }
            Spacer()
        }
    }
}
