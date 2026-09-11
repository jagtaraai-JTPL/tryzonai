import SwiftUI

// MARK: - Mandatory Login Required Dialog
public struct LoginRequiredDialog: View {
    @Binding var isPresented: Bool
    let onNavigateToLogin: () -> Void

    public var body: some View {
        ZStack {
            Color.black.opacity(0.7).ignoresSafeArea()

            VStack(spacing: 20) {
                // Header Icon
                ZStack {
                    Circle()
                        .fill(TryZonTheme.primaryGold.opacity(0.15))
                        .frame(width: 70, height: 70)

                    Image(systemName: "lock.shield.fill")
                        .font(.system(size: 32))
                        .foregroundColor(TryZonTheme.primaryGold)
                }

                VStack(spacing: 6) {
                    Text("Free Trial Used ⚡")
                        .font(.system(size: 20, weight: .black, design: .rounded))
                        .foregroundColor(.white)

                    Text("Sign in to claim your 2 FREE bonus credits & save your virtual fitting room outfits!")
                        .font(.system(size: 13))
                        .foregroundColor(.white.opacity(0.7))
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 8)
                }

                VStack(spacing: 12) {
                    // Sign In Button
                    ShimmeringGoldButton(title: "SIGN IN / REGISTER 🚀", subtitle: "+2 Welcome Bonus Credits") {
                        isPresented = false
                        onNavigateToLogin()
                    }

                    // Cancel
                    Button(action: { isPresented = false }) {
                        Text("Maybe Later")
                            .font(.system(size: 13, weight: .medium))
                            .foregroundColor(.white.opacity(0.5))
                    }
                }
            }
            .padding(24)
            .background(TryZonTheme.darkSurface)
            .cornerRadius(24)
            .overlay(
                RoundedRectangle(cornerRadius: 24)
                    .stroke(TryZonTheme.primaryGold.opacity(0.4), lineWidth: 1.5)
            )
            .padding(.horizontal, 24)
        }
    }
}

// MARK: - Smart Choice Popup (Ad / Fast Pass / Pocket Pack)
public struct ChoicePopup: View {
    @Binding var isPresented: Bool
    @ObservedObject var apiClient: APIClient
    let onUseCredit: () -> Void
    let onWatchAd: () -> Void
    let onNavigateToPremium: () -> Void

    public var body: some View {
        ZStack {
            Color.black.opacity(0.75).ignoresSafeArea()

            VStack(spacing: 18) {
                // Title
                VStack(spacing: 4) {
                    Text("⚡ CHOOSE GENERATION MODE")
                        .font(.system(size: 18, weight: .black, design: .rounded))
                        .foregroundColor(TryZonTheme.primaryGold)

                    Text("You have \(apiClient.userCredits + apiClient.paidCredits) credits remaining today")
                        .font(.system(size: 12))
                        .foregroundColor(.white.opacity(0.7))
                }

                VStack(spacing: 12) {
                    // Option 1: Use Paid / Free Credit (Fast Pass Zero Ads)
                    if (apiClient.userCredits + apiClient.paidCredits) > 0 {
                        Button(action: {
                            isPresented = false
                            onUseCredit()
                        }) {
                            HStack {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text("⚡ Use 1 Credit")
                                        .font(.system(size: 15, weight: .bold))
                                        .foregroundColor(.black)
                                    Text("100% Zero Ads • Instant Fast Pass")
                                        .font(.system(size: 11))
                                        .foregroundColor(.black.opacity(0.7))
                                }
                                Spacer()
                                Image(systemName: "bolt.fill")
                                    .font(.title3)
                                    .foregroundColor(.black)
                            }
                            .padding(14)
                            .background(TryZonTheme.primaryGold)
                            .cornerRadius(14)
                        }
                    }

                    // Option 2: Watch Video Ad
                    Button(action: {
                        isPresented = false
                        onWatchAd()
                    }) {
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text("📺 Watch Short Video Ad")
                                    .font(.system(size: 15, weight: .bold))
                                    .foregroundColor(.white)
                                Text("Keep your credits safe (0 deduct)")
                                    .font(.system(size: 11))
                                    .foregroundColor(.white.opacity(0.7))
                            }
                            Spacer()
                            Image(systemName: "play.rectangle.fill")
                                .font(.title3)
                                .foregroundColor(TryZonTheme.primaryGold)
                        }
                        .padding(14)
                        .background(TryZonTheme.surfaceVariant)
                        .cornerRadius(14)
                        .overlay(
                            RoundedRectangle(cornerRadius: 14)
                                .stroke(TryZonTheme.primaryGold.opacity(0.3), lineWidth: 1)
                        )
                    }

                    // Option 3: Buy Pocket Pack ₹39 / $0.99 (25 Credits)
                    Button(action: {
                        isPresented = false
                        onNavigateToPremium()
                    }) {
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text("⚡ Pocket Pack ₹39 / $0.99")
                                    .font(.system(size: 15, weight: .bold))
                                    .foregroundColor(TryZonTheme.primaryGold)
                                Text("Get 25 Credits (15 + 10 BONUS)")
                                    .font(.system(size: 11))
                                    .foregroundColor(.white.opacity(0.8))
                            }
                            Spacer()
                            Text("👑 BEST VALUE")
                                .font(.system(size: 10, weight: .bold))
                                .padding(.horizontal, 8)
                                .padding(.vertical, 4)
                                .background(TryZonTheme.primaryGold)
                                .foregroundColor(.black)
                                .cornerRadius(6)
                        }
                        .padding(14)
                        .background(Color.black.opacity(0.5))
                        .cornerRadius(14)
                        .overlay(
                            RoundedRectangle(cornerRadius: 14)
                                .stroke(TryZonTheme.primaryGold, lineWidth: 1.5)
                        )
                    }
                }

                Button(action: { isPresented = false }) {
                    Text("Close")
                        .font(.system(size: 13, weight: .medium))
                        .foregroundColor(.white.opacity(0.5))
                }
            }
            .padding(22)
            .background(TryZonTheme.darkSurface)
            .cornerRadius(24)
            .padding(.horizontal, 24)
        }
    }
}
