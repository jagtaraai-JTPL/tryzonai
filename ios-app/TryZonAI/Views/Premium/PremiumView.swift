import SwiftUI
import StoreKit
import UIKit

public struct PremiumView: View {
    @Environment(\.presentationMode) var presentationMode
    @StateObject private var storeKit = StoreKitManager.shared
    @State private var selectedTab = 0

    private let creditPacks = [
        PricingPlan(name: "Pocket Try", productId: "credits_pocket", subtitle: "15 Credits", price: "₹29.00 ($0.49)", tag: "IMPULSE TRY", features: ["15 AI Try-On Credits", "One-Time Purchase", "No Expiration"], isHighlight: true),
        PricingPlan(name: "Starter Pack", productId: "credits_starter", subtitle: "60 Credits", price: "₹99.00 ($1.29)", tag: "POPULAR", features: ["60 AI Try-On Credits", "One-Time Purchase", "No Expiration"]),
        PricingPlan(name: "Value Pack", productId: "credits_value", subtitle: "500 Credits", price: "₹299.00 ($3.99)", tag: "BEST VALUE", features: ["500 AI Try-On Credits", "One-Time Purchase", "Priority Processing"]),
        PricingPlan(name: "Business Pack", productId: "credits_business", subtitle: "2000 Credits", price: "₹799.00 ($9.99)", tag: "CREATORS", features: ["2000 AI Try-On Credits", "One-Time Purchase", "Turbo GPU Speed"]),
        PricingPlan(name: "Enterprise Pack", productId: "credits_enterprise", subtitle: "7000 Credits", price: "₹2,499.00 ($29.99)", tag: "BULK VOLUME", features: ["7000 AI Try-On Credits", "One-Time Purchase", "Commercial License"])
    ]

    private let subscriptions = [
        PricingPlan(name: "Weekly Pro", productId: "sub_weekly_pro", subtitle: "150 Credits/wk", price: "₹99.00 ($1.29)", tag: "MOST POPULAR", features: ["150 AI Try-On Credits / week", "Ad-Free Experience", "Turbo GPU Processing", "Resets Weekly"], isHighlight: true, isSubscription: true, billingPeriod: " / week"),
        PricingPlan(name: "Monthly Pro", productId: "sub_monthly_pro", subtitle: "600 Credits/mo", price: "₹299.00 ($3.99)", tag: "BEST VALUE", features: ["600 AI Try-On Credits / month", "Advanced Style Insights", "Priority Customer Support", "Resets Monthly"], isSubscription: true, billingPeriod: " / month"),
        PricingPlan(name: "Yearly Legend", productId: "sub_yearly_legend", subtitle: "3,600 Credits/yr", price: "₹1,499.00 ($18.99)", tag: "SAVE BIG", features: ["3,600 AI Try-On Credits / year", "Exclusive Early Access", "Personal Stylist Support", "Resets Yearly"], isSubscription: true, billingPeriod: " / year")
    ]

    public init() {}

    public var body: some View {
        NavigationView {
            ScrollView {
                VStack(spacing: 16) {
                    // MARK: - Top Global Apple Policy Compliance Banner
                    VStack(alignment: .leading, spacing: 6) {
                        HStack {
                            Image(systemName: "checkmark.circle.fill")
                                .foregroundColor(.yellow)
                            Text("Subscription is OPTIONAL")
                                .font(.system(size: 15, weight: .bold))
                                .foregroundColor(.yellow)
                        }
                        Text("You can continue using TryZon AI for FREE with 3 daily try-ons.")
                            .font(.caption.bold())
                            .foregroundColor(.primary)
                        Text("A paid subscription is NOT required to use this app. Pay-As-You-Go credit packs are also available.")
                            .font(.caption2)
                            .foregroundColor(.secondary)
                    }
                    .padding(14)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color(UIColor.secondarySystemBackground))
                    .cornerRadius(16)
                    .overlay(
                        RoundedRectangle(cornerRadius: 16)
                            .stroke(Color.yellow, lineWidth: 1.5)
                    )
                    .padding(.horizontal)

                    // MARK: - Segmented Switcher
                    Picker("Plan Type", selection: $selectedTab) {
                        Text("Pay-As-You-Go").tag(0)
                        Text("Unlimited Subscriptions").tag(1)
                    }
                    .pickerStyle(SegmentedPickerStyle())
                    .padding(.horizontal)

                    // MARK: - Plan Cards List
                    if selectedTab == 0 {
                        VStack(spacing: 12) {
                            ForEach(creditPacks) { plan in
                                PlanCard(plan: plan)
                            }
                        }
                        .padding(.horizontal)
                    } else {
                        VStack(spacing: 12) {
                            ForEach(subscriptions) { plan in
                                PlanCard(plan: plan)
                            }
                        }
                        .padding(.horizontal)
                    }

                    // MARK: - Apple Review Guidelines 3.1.1 Mandatory Legal Footer & Restore Purchases
                    VStack(spacing: 10) {
                        Button(action: {
                            Task {
                                await storeKit.restorePurchases()
                            }
                        }) {
                            Text("Restore Purchases")
                                .font(.subheadline.bold())
                                .foregroundColor(.purple)
                        }
                        .padding(.top, 8)

                        HStack(spacing: 16) {
                            Link("Privacy Policy", destination: URL(string: "https://tryzonai.com/privacy-policy")!)
                            Text("•").foregroundColor(.secondary)
                            Link("Terms of Use (EULA)", destination: URL(string: "https://www.apple.com/legal/internet-services/itunes/dev/stdeula/")!)
                        }
                        .font(.caption)
                        .foregroundColor(.secondary)

                        Text("Payment charged to Apple ID account at purchase confirmation. Subscriptions auto-renew unless canceled at least 24h prior to period end via Settings > Apple ID > Subscriptions.")
                            .font(.system(size: 10))
                            .foregroundColor(.secondary)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal)
                    }
                    .padding(.bottom, 24)
                }
                .padding(.top, 10)
            }
            .navigationTitle("TryZon Pro & Credits")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") {
                        presentationMode.wrappedValue.dismiss()
                    }
                }
            }
        }
    }
}

struct PlanCard: View {
    let plan: PricingPlan
    @StateObject private var storeKit = StoreKitManager.shared

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text(plan.name)
                    .font(.headline.bold())
                Spacer()
                Text(plan.tag)
                    .font(.system(size: 10, weight: .bold))
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(Color.yellow.opacity(0.2))
                    .foregroundColor(.orange)
                    .cornerRadius(8)
            }

            Text(plan.price)
                .font(.title2.bold())
                .foregroundColor(.primary)

            VStack(alignment: .leading, spacing: 4) {
                ForEach(plan.features, id: \.self) { feat in
                    HStack(spacing: 6) {
                        Image(systemName: "checkmark.circle.fill")
                            .foregroundColor(.yellow)
                            .font(.caption)
                        Text(feat)
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                }
            }

            Button(action: {
                if let skProduct = storeKit.products.first(where: { $0.id == plan.productId }) {
                    Task {
                        _ = try? await storeKit.purchase(skProduct)
                    }
                } else {
                    print("Initiating purchase for \(plan.productId)")
                }
            }) {
                Text(plan.isSubscription ? "Subscribe – \(plan.price)" : "Buy Pack – \(plan.price)")
                    .font(.subheadline.bold())
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 10)
                    .background(plan.isHighlight ? Color.yellow : Color.purple.opacity(0.15))
                    .foregroundColor(plan.isHighlight ? .black : .purple)
                    .cornerRadius(10)
            }
        }
        .padding(14)
        .background(Color(UIColor.secondarySystemBackground))
        .cornerRadius(16)
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(plan.isHighlight ? Color.yellow : Color.clear, lineWidth: 1.5)
        )
    }
}
