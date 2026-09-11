import SwiftUI

public struct TopBar: View {
    @ObservedObject var apiClient: APIClient
    let onOpenDrawer: () -> Void
    let onOpenPremium: () -> Void

    public var body: some View {
        HStack {
            // Gold Brand Official Logo Badge
            HStack(spacing: 8) {
                Image("AppLogo")
                    .resizable()
                    .scaledToFit()
                    .frame(width: 30, height: 30)
                    .cornerRadius(8)

                Text("TryZon AI")
                    .font(.system(size: 18, weight: .black, design: .rounded))
                    .foregroundColor(.white)
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
                            .foregroundColor(.white)
                    }
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 5)
                .background(TryZonTheme.surfaceVariant)
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
                        .fill(TryZonTheme.surfaceVariant)
                        .frame(width: 34, height: 34)
                    Image(systemName: "line.3.horizontal")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(.white)
                }
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background(TryZonTheme.darkBackground)
    }
}
