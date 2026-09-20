import SwiftUI

public struct PurchaseTransaction: Identifiable {
    public let id: String
    public let title: String
    public let credits: Int
    public let amount: String
    public let date: String
    public let status: String
    public let isPro: Bool
}

public struct PurchaseHistoryView: View {
    @ObservedObject var apiClient: APIClient
    @Environment(\.dismiss) private var dismiss
    @Environment(\.colorScheme) private var colorScheme

    @State private var sampleTransactions: [PurchaseTransaction] = [
        PurchaseTransaction(id: "TXN-90214", title: "Welcome First Buyer Offer ⚡", credits: 25, amount: "₹39", date: "Today, 11:20 AM", status: "SUCCESS", isPro: false),
        PurchaseTransaction(id: "TXN-88102", title: "1 Daily Free Quota Try ✦", credits: 1, amount: "FREE", date: "Yesterday, 09:15 PM", status: "COMPLETED", isPro: false),
        PurchaseTransaction(id: "TXN-84310", title: "Daily Ad Rewards Bonus 🎁", credits: 4, amount: "FREE", date: "Sep 18, 2026", status: "COMPLETED", isPro: false),
        PurchaseTransaction(id: "TXN-79104", title: "Pro Subscription Trial 👑", credits: 999, amount: "₹379", date: "Sep 01, 2026", status: "ACTIVE", isPro: true)
    ]

    public init(apiClient: APIClient) {
        self.apiClient = apiClient
    }

    public var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                // Summary Header
                VStack(spacing: 8) {
                    HStack(spacing: 12) {
                        ZStack {
                            Circle()
                                .fill(TryZonTheme.primaryGold.opacity(0.2))
                                .frame(width: 48, height: 48)
                            Image(systemName: "clock.arrow.circlepath")
                                .font(.system(size: 22))
                                .foregroundColor(TryZonTheme.primaryGold)
                        }

                        VStack(alignment: .leading, spacing: 2) {
                            Text("Purchase & Credit History")
                                .font(.system(size: 18, weight: .bold))
                                .foregroundColor(TryZonTheme.textColor(for: colorScheme))
                            Text("All past credit top-ups, orders & subscriptions")
                                .font(.system(size: 12))
                                .foregroundColor(TryZonTheme.subtextColor(for: colorScheme))
                        }

                        Spacer()
                    }
                    .padding(16)
                    .background(TryZonTheme.surfaceVariantColor(for: colorScheme))
                    .cornerRadius(20)
                }
                .padding(16)

                // Transactions List
                List {
                    Section(header: Text("RECENT TRANSACTIONS & REWARDS").font(.system(size: 10, weight: .black)).foregroundColor(TryZonTheme.primaryGold)) {
                        ForEach(sampleTransactions) { txn in
                            HStack(spacing: 12) {
                                Circle()
                                    .fill(txn.isPro ? TryZonTheme.primaryGold.opacity(0.2) : Color.blue.opacity(0.15))
                                    .frame(width: 38, height: 38)
                                    .overlay(
                                        Image(systemName: txn.isPro ? "crown.fill" : "bolt.fill")
                                            .foregroundColor(txn.isPro ? TryZonTheme.primaryGold : .blue)
                                    )

                                VStack(alignment: .leading, spacing: 3) {
                                    Text(txn.title)
                                        .font(.system(size: 13.5, weight: .bold))
                                        .foregroundColor(TryZonTheme.textColor(for: colorScheme))

                                    Text("\(txn.date) • \(txn.id)")
                                        .font(.system(size: 10.5))
                                        .foregroundColor(TryZonTheme.subtextColor(for: colorScheme))
                                }

                                Spacer()

                                VStack(alignment: .trailing, spacing: 3) {
                                    Text(txn.amount)
                                        .font(.system(size: 14, weight: .black, design: .rounded))
                                        .foregroundColor(txn.amount == "FREE" ? .green : TryZonTheme.textColor(for: colorScheme))

                                    Text(txn.status)
                                        .font(.system(size: 9, weight: .black))
                                        .padding(.horizontal, 6)
                                        .padding(.vertical, 2)
                                        .background(Color.green.opacity(0.18))
                                        .foregroundColor(.green)
                                        .clipShape(Capsule())
                                }
                            }
                            .padding(.vertical, 4)
                        }
                    }
                }
                .listStyle(InsetGroupedListStyle())
            }
            .navigationTitle("Purchase History")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") { dismiss() }
                        .font(.system(size: 14, weight: .bold))
                }
            }
        }
    }
}
