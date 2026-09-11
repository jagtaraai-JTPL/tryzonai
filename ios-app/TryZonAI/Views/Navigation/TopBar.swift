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
                    } else {
                        Image(systemName: "bolt.fill")
                            .font(.system(size: 10))
                            .foregroundColor(TryZonTheme.primaryGold)
                        Text("\(apiClient.userCredits) Credits")
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
