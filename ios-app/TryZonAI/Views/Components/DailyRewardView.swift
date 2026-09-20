import SwiftUI

public struct DailyRewardView: View {
    @ObservedObject var apiClient: APIClient
    @Environment(\.dismiss) private var dismiss
    @Environment(\.colorScheme) private var colorScheme

    @State private var adCountToday: Int = 0
    @State private var isLoadingAd = false
    @State private var toastMessage: String? = nil

    public init(apiClient: APIClient) {
        self.apiClient = apiClient
    }

    private var maxAds: Int { 8 }

    public var body: some View {
        ZStack {
            TryZonTheme.darkBackground.ignoresSafeArea()

            VStack(spacing: 20) {
                // Header
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("DAILY REWARDS 🎁")
                            .font(.system(size: 18, weight: .black, design: .rounded))
                            .foregroundColor(TryZonTheme.primaryGold)
                        Text("Watch short video ads to earn free try-on credits")
                            .font(.system(size: 11))
                            .foregroundColor(.white.opacity(0.6))
                    }
                    Spacer()
                    Button(action: { dismiss() }) {
                        Image(systemName: "xmark.circle.fill")
                            .font(.system(size: 24))
                            .foregroundColor(.white.opacity(0.6))
                    }
                }
                .padding(.horizontal, 20)
                .padding(.top, 16)

                // Ad Tracker Card
                VStack(spacing: 14) {
                    Text("Today's Ad Rewards Progress")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(.white.opacity(0.8))

                    HStack(spacing: 6) {
                        ForEach(0..<maxAds, id: \.self) { idx in
                            Circle()
                                .fill(idx < adCountToday ? TryZonTheme.primaryGold : Color.white.opacity(0.15))
                                .frame(width: 28, height: 28)
                                .overlay(
                                    Text("\(idx + 1)")
                                        .font(.system(size: 10, weight: .black))
                                        .foregroundColor(idx < adCountToday ? .black : .white.opacity(0.5))
                                )
                        }
                    }

                    Text("\(adCountToday)/\(maxAds) Ads Watched Today")
                        .font(.system(size: 13, weight: .black))
                        .foregroundColor(TryZonTheme.primaryGold)

                    Text("Each watched ad earns +0.5⚡ Try-On Credit. Maximum 8 ads (+4.0 Credits) per day!")
                        .font(.system(size: 11))
                        .foregroundColor(.white.opacity(0.7))
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 10)
                }
                .padding(18)
                .background(TryZonTheme.surfaceVariant)
                .cornerRadius(20)
                .overlay(RoundedRectangle(cornerRadius: 20).stroke(TryZonTheme.primaryGold.opacity(0.3), lineWidth: 1))
                .padding(.horizontal, 20)

                // Watch Video Ad Button
                Button(action: watchAdAndClaim) {
                    HStack(spacing: 8) {
                        if isLoadingAd {
                            ProgressView().tint(.black)
                            Text("Loading Video Ad...")
                                .font(.system(size: 14, weight: .bold))
                        } else if adCountToday >= maxAds {
                            Text("ALL 8 ADS COMPLETED TODAY! 🎉")
                                .font(.system(size: 14, weight: .bold))
                        } else {
                            Image(systemName: "play.tv.fill")
                            Text("WATCH VIDEO AD (+0.5⚡ CREDIT)")
                                .font(.system(size: 14, weight: .black, design: .rounded))
                        }
                    }
                    .foregroundColor(adCountToday >= maxAds ? .white.opacity(0.5) : .black)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                    .background(adCountToday >= maxAds ? Color.gray.opacity(0.3) : TryZonTheme.primaryGold)
                    .cornerRadius(14)
                    .shadow(color: TryZonTheme.primaryGold.opacity(0.3), radius: 6)
                }
                .disabled(isLoadingAd || adCountToday >= maxAds)
                .padding(.horizontal, 20)
                .buttonStyle(BounceButtonStyle())

                Spacer()
            }

            if let toast = toastMessage {
                VStack {
                    Spacer()
                    Text(toast)
                        .font(.system(size: 13, weight: .bold))
                        .padding(.horizontal, 18)
                        .padding(.vertical, 10)
                        .background(Color.black.opacity(0.92))
                        .foregroundColor(TryZonTheme.primaryGold)
                        .cornerRadius(20)
                        .padding(.bottom, 40)
                }
                .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .onAppear {
            let lastDate = UserDefaults.standard.string(forKey: "ad_reward_date") ?? ""
            let today = AuthViewModel.todayDateString()
            if lastDate != today {
                adCountToday = 0
                UserDefaults.standard.set(0, forKey: "ad_reward_count")
                UserDefaults.standard.set(today, forKey: "ad_reward_date")
            } else {
                adCountToday = UserDefaults.standard.integer(forKey: "ad_reward_count")
            }
        }
    }

    private func watchAdAndClaim() {
        guard adCountToday < maxAds else { return }
        isLoadingAd = true

        Task {
            try? await Task.sleep(nanoseconds: 1_200_000_000)
            do {
                try await apiClient.claimRewardCredit(amount: 1)
                await MainActor.run {
                    self.isLoadingAd = false
                    self.adCountToday += 1
                    let today = AuthViewModel.todayDateString()
                    UserDefaults.standard.set(self.adCountToday, forKey: "ad_reward_count")
                    UserDefaults.standard.set(today, forKey: "ad_reward_date")
                    self.showToast("🎁 +0.5 Credit Claimed! Total: \(self.adCountToday)/\(self.maxAds)")
                }
            } catch {
                await MainActor.run {
                    self.isLoadingAd = false
                    self.showToast("Ad reward error — please retry")
                }
            }
        }
    }

    private func showToast(_ msg: String) {
        withAnimation { toastMessage = msg }
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.5) {
            withAnimation { toastMessage = nil }
        }
    }
}
