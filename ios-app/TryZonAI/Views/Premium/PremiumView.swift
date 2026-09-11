import SwiftUI
import StoreKit

public struct PremiumView: View {
    @ObservedObject var apiClient: APIClient
    @State private var selectedTab: Int = 1 // 0: Subscriptions, 1: Credit Packs (Pocket Pack ₹39 at top)

    public init(apiClient: APIClient) {
        self.apiClient = apiClient
    }

    public var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                // Header Title
                VStack(spacing: 4) {
                    Text("TRYZON AI PRO 👑")
                        .font(.system(size: 24, weight: .black, design: .rounded))
                        .foregroundColor(TryZonTheme.primaryGold)

                    Text("Virtual Outfit Fitting Room Subscriptions & Credit Refills")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundColor(.white.opacity(0.7))
                        .multilineTextAlignment(.center)
                }
                .padding(.top, 12)

                // Segmented Control (Subscriptions vs Credit Packs)
                HStack(spacing: 0) {
                    Button(action: { withAnimation { selectedTab = 0 } }) {
                        Text("Subscriptions")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundColor(selectedTab == 0 ? .black : .white)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 10)
                            .background(selectedTab == 0 ? TryZonTheme.primaryGold : Color.clear)
                            .cornerRadius(20)
                    }

                    Button(action: { withAnimation { selectedTab = 1 } }) {
                        Text("Credit Packs")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundColor(selectedTab == 1 ? .black : .white)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 10)
                            .background(selectedTab == 1 ? TryZonTheme.primaryGold : Color.clear)
                            .cornerRadius(20)
                    }
                }
                .padding(4)
                .background(TryZonTheme.surfaceVariant)
                .cornerRadius(24)
                .padding(.horizontal, 24)

                // Content View
                if selectedTab == 1 {
                    // Credit Packs List
                    VStack(spacing: 12) {
                        // Pocket Pack (First-Time Buyer Special)
                        CreditPackCard(
                            name: "🎁 First Buyer Special (25 Fits)",
                            price: "₹39.00 ($0.99)",
                            subtitle: "15 + 10 BONUS Credits • 100% Zero Ads",
                            tag: "BEST OFFER ⭐",
                            isHighlight: true
                        )

                        CreditPackCard(
                            name: "Starter Pack (60 Credits)",
                            price: "₹99.00 ($1.99)",
                            subtitle: "60 AI Try-Ons • Standard Priority",
                            tag: "",
                            isHighlight: false
                        )

                        CreditPackCard(
                            name: "Popular Pack (500 Credits)",
                            price: "₹349.00 ($9.99)",
                            subtitle: "500 AI Try-Ons • Priority Processing",
                            tag: "POPULAR 🔥",
                            isHighlight: false
                        )

                        CreditPackCard(
                            name: "Ultimate Pack (7,000 Credits)",
                            price: "₹2,699.00 ($79.99)",
                            subtitle: "7,000 AI Try-Ons • Bulk Volume",
                            tag: "MAX VALUE 👑",
                            isHighlight: false
                        )
                    }
                    .padding(.horizontal, 16)
                } else {
                    // Subscriptions List
                    VStack(spacing: 12) {
                        SubscriptionCard(
                            name: "Monthly Pro",
                            price: "₹379.00 / month ($14.99)",
                            subtitle: "Unlimited AI Try-Ons • 100% Zero Ads • VIP Turbo Speed",
                            tag: "RECOMMENDED ⭐",
                            isHighlight: true
                        )

                        SubscriptionCard(
                            name: "Weekly Pro",
                            price: "₹119.00 / week ($4.99)",
                            subtitle: "150 AI Try-On Credits / week • Fast Speed",
                            tag: "",
                            isHighlight: false
                        )

                        SubscriptionCard(
                            name: "Yearly Legend",
                            price: "₹1,799.00 / year ($59.99)",
                            subtitle: "Unlimited AI Try-Ons • Save 60%",
                            tag: "SAVE 60% 👑",
                            isHighlight: false
                        )
                    }
                    .padding(.horizontal, 16)
                }

                Text("One-time purchases do not expire. Subscriptions auto-renew until cancelled in App Store settings.")
                    .font(.system(size: 10))
                    .foregroundColor(.white.opacity(0.4))
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 24)
                    .padding(.top, 8)
            }
            .padding(.bottom, 30)
        }
        .background(TryZonTheme.darkBackground)
    }
}

struct CreditPackCard: View {
    let name: String
    let price: String
    let subtitle: String
    let tag: String
    let isHighlight: Bool

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                if !tag.isEmpty {
                    Text(tag)
                        .font(.system(size: 9, weight: .black))
                        .padding(.horizontal, 8)
                        .padding(.vertical, 2)
                        .background(TryZonTheme.primaryGold)
                        .foregroundColor(.black)
                        .cornerRadius(6)
                }

                Text(name)
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(.white)

                Text(subtitle)
                    .font(.system(size: 11))
                    .foregroundColor(.white.opacity(0.6))
            }

            Spacer()

            Text(price)
                .font(.system(size: 14, weight: .black))
                .foregroundColor(TryZonTheme.primaryGold)
        }
        .padding(16)
        .background(isHighlight ? TryZonTheme.surfaceVariant : TryZonTheme.darkSurface)
        .cornerRadius(18)
        .overlay(
            RoundedRectangle(cornerRadius: 18)
                .stroke(isHighlight ? TryZonTheme.primaryGold : TryZonTheme.cardBorder, lineWidth: isHighlight ? 1.5 : 1)
        )
    }
}

struct SubscriptionCard: View {
    let name: String
    let price: String
    let subtitle: String
    let tag: String
    let isHighlight: Bool

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                if !tag.isEmpty {
                    Text(tag)
                        .font(.system(size: 9, weight: .black))
                        .padding(.horizontal, 8)
                        .padding(.vertical, 2)
                        .background(TryZonTheme.primaryGold)
                        .foregroundColor(.black)
                        .cornerRadius(6)
                }

                Text(name)
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(.white)

                Text(subtitle)
                    .font(.system(size: 11))
                    .foregroundColor(.white.opacity(0.6))
            }

            Spacer()

            Text(price)
                .font(.system(size: 13, weight: .black))
                .foregroundColor(TryZonTheme.primaryGold)
        }
        .padding(16)
        .background(isHighlight ? TryZonTheme.surfaceVariant : TryZonTheme.darkSurface)
        .cornerRadius(18)
        .overlay(
            RoundedRectangle(cornerRadius: 18)
                .stroke(isHighlight ? TryZonTheme.primaryGold : TryZonTheme.cardBorder, lineWidth: isHighlight ? 1.5 : 1)
        )
    }
}
