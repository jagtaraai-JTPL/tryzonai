import SwiftUI
import StoreKit

public struct PremiumView: View {
    @ObservedObject var apiClient: APIClient
    @Environment(\.dismiss) private var dismiss

    @State private var selectedTab: Int = 1 // 0: Subscriptions, 1: Credit Packs (Pocket Pack ₹39 at top)
    @State private var selectedPackId: String = "credits_pocket"
    @State private var isPurchasing: Bool = false
    @State private var toastMessage: String? = nil

    public init(apiClient: APIClient) {
        self.apiClient = apiClient
    }

    public var body: some View {
        ZStack {
            TryZonTheme.darkBackground.ignoresSafeArea()

            VStack(spacing: 0) {
                // Header Bar with Close Button (if presented in sheet)
                HStack {
                    Spacer()
                    Button(action: { dismiss() }) {
                        Image(systemName: "xmark.circle.fill")
                            .font(.system(size: 24))
                            .foregroundColor(.white.opacity(0.6))
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 12)

                ScrollView(showsIndicators: false) {
                    VStack(spacing: 18) {
                        // Header Title
                        VStack(spacing: 4) {
                            Text("TRYZON AI PRO 👑")
                                .font(.system(size: 22, weight: .black, design: .rounded))
                                .foregroundColor(TryZonTheme.primaryGold)

                            Text("Virtual Outfit Fitting Room Subscriptions & Credit Refills")
                                .font(.system(size: 11, weight: .medium))
                                .foregroundColor(.white.opacity(0.7))
                                .multilineTextAlignment(.center)
                                .padding(.horizontal, 20)
                        }

                        // Segmented Control (Subscriptions vs Credit Packs)
                        HStack(spacing: 0) {
                            Button(action: {
                                withAnimation {
                                    selectedTab = 0
                                    selectedPackId = "sub_monthly_pro"
                                }
                            }) {
                                Text("Subscriptions")
                                    .font(.system(size: 12.5, weight: .bold))
                                    .foregroundColor(selectedTab == 0 ? .black : .white)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 9)
                                    .background(selectedTab == 0 ? TryZonTheme.primaryGold : Color.clear)
                                    .cornerRadius(20)
                            }

                            Button(action: {
                                withAnimation {
                                    selectedTab = 1
                                    selectedPackId = "credits_pocket"
                                }
                            }) {
                                Text("Credit Packs")
                                    .font(.system(size: 12.5, weight: .bold))
                                    .foregroundColor(selectedTab == 1 ? .black : .white)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 9)
                                    .background(selectedTab == 1 ? TryZonTheme.primaryGold : Color.clear)
                                    .cornerRadius(20)
                            }
                        }
                        .padding(4)
                        .background(TryZonTheme.surfaceVariant)
                        .cornerRadius(24)
                        .padding(.horizontal, 20)

                        // Content View
                        if selectedTab == 1 {
                            // Credit Packs List
                            VStack(spacing: 10) {
                                CreditPackCard(
                                    id: "credits_pocket",
                                    name: "🎁 First Buyer Special (25 Fits)",
                                    price: "₹39.00 ($0.99)",
                                    subtitle: "15 + 10 BONUS Credits • 100% Zero Ads",
                                    tag: "BEST OFFER ⭐",
                                    isSelected: selectedPackId == "credits_pocket",
                                    onSelect: { selectedPackId = "credits_pocket" }
                                )

                                CreditPackCard(
                                    id: "credits_starter",
                                    name: "Starter Pack (60 Credits)",
                                    price: "₹99.00 ($1.99)",
                                    subtitle: "60 AI Try-Ons • Standard Priority",
                                    tag: "",
                                    isSelected: selectedPackId == "credits_starter",
                                    onSelect: { selectedPackId = "credits_starter" }
                                )

                                CreditPackCard(
                                    id: "credits_popular",
                                    name: "Popular Pack (500 Credits)",
                                    price: "₹349.00 ($9.99)",
                                    subtitle: "500 AI Try-Ons • Priority GPU Queue",
                                    tag: "POPULAR 🔥",
                                    isSelected: selectedPackId == "credits_popular",
                                    onSelect: { selectedPackId = "credits_popular" }
                                )

                                CreditPackCard(
                                    id: "credits_ultimate",
                                    name: "Ultimate Pack (7,000 Credits)",
                                    price: "₹2,699.00 ($79.99)",
                                    subtitle: "7,000 AI Try-Ons • Maximum Volume",
                                    tag: "MAX VALUE 👑",
                                    isSelected: selectedPackId == "credits_ultimate",
                                    onSelect: { selectedPackId = "credits_ultimate" }
                                )
                            }
                            .padding(.horizontal, 16)
                        } else {
                            // Subscriptions List
                            VStack(spacing: 10) {
                                SubscriptionCard(
                                    id: "sub_monthly_pro",
                                    name: "Monthly Pro",
                                    price: "₹379.00 / mo ($14.99)",
                                    subtitle: "Unlimited AI Try-Ons • 100% Zero Ads • VIP Turbo Speed",
                                    tag: "RECOMMENDED ⭐",
                                    isSelected: selectedPackId == "sub_monthly_pro",
                                    onSelect: { selectedPackId = "sub_monthly_pro" }
                                )

                                SubscriptionCard(
                                    id: "sub_weekly_pro",
                                    name: "Weekly Pro",
                                    price: "₹119.00 / wk ($4.99)",
                                    subtitle: "150 AI Try-On Credits / week • Fast Speed",
                                    tag: "",
                                    isSelected: selectedPackId == "sub_weekly_pro",
                                    onSelect: { selectedPackId = "sub_weekly_pro" }
                                )

                                SubscriptionCard(
                                    id: "sub_yearly_legend",
                                    name: "Yearly Legend",
                                    price: "₹1,799.00 / yr ($59.99)",
                                    subtitle: "Unlimited AI Try-Ons • Save 60%",
                                    tag: "SAVE 60% 👑",
                                    isSelected: selectedPackId == "sub_yearly_legend",
                                    onSelect: { selectedPackId = "sub_yearly_legend" }
                                )
                            }
                            .padding(.horizontal, 16)
                        }

                        // Restore Purchases Button
                        Button(action: restorePurchases) {
                            Text("Restore Purchases")
                                .font(.system(size: 11, weight: .bold))
                                .foregroundColor(TryZonTheme.primaryGold)
                                .underline()
                        }
                        .padding(.top, 2)

                        Text("One-time purchases do not expire. Subscriptions auto-renew until cancelled in App Store settings.")
                            .font(.system(size: 9.5))
                            .foregroundColor(.white.opacity(0.4))
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 24)
                            .padding(.top, 2)
                    }
                    .padding(.bottom, 16)
                }

                // Sticky CTA Purchase Button
                VStack(spacing: 0) {
                    Divider().background(Color.white.opacity(0.1))
                    Button(action: processPurchase) {
                        HStack(spacing: 8) {
                            if isPurchasing {
                                ProgressView()
                                    .tint(.black)
                                    .scaleEffect(0.9)
                            } else {
                                Image(systemName: "bolt.fill")
                                    .font(.system(size: 15, weight: .bold))
                            }
                            Text(isPurchasing ? "PROCESSING..." : "GET INSTANT ACCESS ⚡")
                                .font(.system(size: 14, weight: .black))
                                .tracking(1)
                        }
                        .foregroundColor(.black)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(TryZonTheme.primaryGold)
                        .cornerRadius(14)
                        .shadow(color: TryZonTheme.primaryGold.opacity(0.5), radius: 10, y: 3)
                    }
                    .disabled(isPurchasing)
                    .buttonStyle(BounceButtonStyle())
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                    .background(TryZonTheme.darkSurface)
                }
            }

            // Toast overlay
            if let toast = toastMessage {
                VStack {
                    Spacer()
                    Text(toast)
                        .font(.system(size: 12, weight: .bold))
                        .padding(.horizontal, 18)
                        .padding(.vertical, 10)
                        .background(Color.black.opacity(0.9))
                        .foregroundColor(TryZonTheme.primaryGold)
                        .cornerRadius(20)
                        .shadow(color: .black.opacity(0.5), radius: 6)
                        .padding(.bottom, 80)
                }
                .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
    }

    private func processPurchase() {
        isPurchasing = true
        Task {
            try? await Task.sleep(nanoseconds: 1_200_000_000)
            DispatchQueue.main.async {
                isPurchasing = false
                showToast("✨ Selected plan processed successfully!")
            }
        }
    }

    private func restorePurchases() {
        Task {
            showToast("Checking App Store for existing purchases...")
            try? await Task.sleep(nanoseconds: 1_000_000_000)
            DispatchQueue.main.async {
                showToast("Purchases restored!")
            }
        }
    }

    private func showToast(_ msg: String) {
        withAnimation { toastMessage = msg }
        DispatchQueue.main.asyncAfter(deadline: .now() + 3.0) {
            withAnimation { toastMessage = nil }
        }
    }
}
