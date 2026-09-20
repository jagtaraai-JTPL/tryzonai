import SwiftUI

public struct TopBar: View {
    @ObservedObject var apiClient: APIClient
    @Environment(\.colorScheme) private var colorScheme
    let onOpenDrawer: () -> Void
    let onOpenPremium: () -> Void

    public var body: some View {
        HStack {
            // Dynamic Brand Official Logo Badge
            HStack(spacing: 8) {
                DynamicAppLogo(width: 32, height: 32)

                Text("TryZon AI")
                    .font(.system(size: 18, weight: .black, design: .rounded))
                    .foregroundColor(TryZonTheme.textColor(for: colorScheme))
            }

            Spacer()

            // Credits Counter / Pro Pill
            Button(action: onOpenPremium) {
                HStack(spacing: 4) {
                    if apiClient.currentUser?.isPremium == true {
                        Text("Pro ✨")
                            .font(.system(size: 12, weight: .black))
                            .foregroundColor(TryZonTheme.primaryGold)
                    } else if apiClient.paidCredits > 0 {
                        Image(systemName: "bolt.fill")
                            .font(.system(size: 10))
                            .foregroundColor(TryZonTheme.primaryGold)
                        Text("\(apiClient.paidCredits) Paid")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(TryZonTheme.textColor(for: colorScheme))
                    } else if apiClient.bonusCredits > 0 {
                        Image(systemName: "sparkles")
                            .font(.system(size: 10))
                            .foregroundColor(TryZonTheme.primaryGold)
                        Text("\(apiClient.bonusCredits) Bonus")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(TryZonTheme.textColor(for: colorScheme))
                    } else {
                        let dailyCount = AuthViewModel.shared.dailyTryCount
                        let freeText = dailyCount < 1 ? "1/1 Free" : "0/1 Free"
                        Image(systemName: "bolt.fill")
                            .font(.system(size: 10))
                            .foregroundColor(dailyCount < 1 ? TryZonTheme.primaryGold : .red)
                        Text(freeText)
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(TryZonTheme.textColor(for: colorScheme))
                    }
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 5)
                .background(TryZonTheme.surfaceVariantColor(for: colorScheme))
                .cornerRadius(20)
                .overlay(
                    RoundedRectangle(cornerRadius: 20)
                        .stroke(TryZonTheme.primaryGold.opacity(0.4), lineWidth: 1)
                )
            }

            // Profile / Side Drawer Button
            Button(action: onOpenDrawer) {
                ZStack {
                    Circle()
                        .fill(TryZonTheme.surfaceVariantColor(for: colorScheme))
                        .frame(width: 34, height: 34)
                    Image(systemName: "line.3.horizontal")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(TryZonTheme.textColor(for: colorScheme))
                }
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background(TryZonTheme.backgroundColor(for: colorScheme))
    }
}
