import SwiftUI

public struct DailyRewardView: View {
    @ObservedObject var apiClient: APIClient
    @ObservedObject private var authViewModel = AuthViewModel.shared
    @Environment(\.dismiss) private var dismiss
    @Environment(\.colorScheme) private var colorScheme

    @State private var dailyAdsWatched: Int = 0
    @State private var timeRemainingStr: String = ""
    @State private var isAdShowing: Bool = false
    @State private var adCountdown: Int = 5
    @State private var showRewardPopUp: Bool = false
    @State private var showPremiumSheet: Bool = false
    @State private var toastMessage: String? = nil

    private let timer = Timer.publish(every: 1.0, on: .main, in: .common).autoconnect()

    public init(apiClient: APIClient) {
        self.apiClient = apiClient
    }

    private var effectiveBalance: Double {
        let paid = Double(apiClient.paidCredits)
        let bonus = Double(apiClient.currentUser?.bonusCredits ?? 0)
        let adBonus = (dailyAdsWatched % 2 == 1) ? 0.5 : 0.0
        return paid + bonus + adBonus
    }

    public var body: some View {
        ZStack {
            TryZonTheme.darkBackground.ignoresSafeArea()

            ScrollView(showsIndicators: false) {
                VStack(spacing: 16) {
                    Spacer(minLength: 10)

                    // ── 1. GAMIFIED HEADER BANNER WITH RESET COUNTER ──
                    HStack {
                        Text("🎁 DAILY AD REWARDS")
                            .font(.system(size: 16, weight: .black, design: .rounded))
                            .foregroundColor(TryZonTheme.primaryGold)
                            .tracking(0.5)

                        Spacer()

                        HStack(spacing: 4) {
                            Text("⏰")
                                .font(.system(size: 11))
                            Text(timeRemainingStr.isEmpty ? "Resets Daily" : "Resets in \(timeRemainingStr)")
                                .font(.system(size: 10, weight: .bold))
                                .foregroundColor(Color(red: 255/255, green: 82/255, blue: 82/255))
                        }
                        .padding(.horizontal, 10)
                        .padding(.vertical, 5)
                        .background(Color(red: 255/255, green: 82/255, blue: 82/255).opacity(0.15))
                        .cornerRadius(20)
                        .overlay(Capsule().stroke(Color(red: 255/255, green: 82/255, blue: 82/255).opacity(0.6), lineWidth: 1))
                    }
                    .padding(.horizontal, 20)

                    // ── 2. WALLET BALANCE & DAILY METER CARD ──
                    VStack(spacing: 12) {
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text("Available Balance")
                                    .font(.system(size: 11, weight: .medium))
                                    .foregroundColor(.white.opacity(0.7))

                                HStack(spacing: 4) {
                                    Text("⚡")
                                        .font(.system(size: 20))
                                    Text(String(format: "%.1f Credits", effectiveBalance))
                                        .font(.system(size: 22, weight: .black, design: .rounded))
                                        .foregroundColor(TryZonTheme.primaryGold)
                                }
                            }

                            Spacer()

                            Text(dailyAdsWatched >= 8 ? "🔥 8/8 COMPLETE" : "\(dailyAdsWatched) / 8 ADS")
                                .font(.system(size: 11, weight: .black))
                                .padding(.horizontal, 12)
                                .padding(.vertical, 5)
                                .background(dailyAdsWatched >= 8 ? Color.green.opacity(0.2) : TryZonTheme.primaryGold.opacity(0.2))
                                .foregroundColor(dailyAdsWatched >= 8 ? Color.green : TryZonTheme.primaryGold)
                                .cornerRadius(20)
                                .overlay(Capsule().stroke(dailyAdsWatched >= 8 ? Color.green : TryZonTheme.primaryGold, lineWidth: 1))
                        }

                        // Progress Bar
                        GeometryReader { geo in
                            ZStack(alignment: .leading) {
                                Capsule()
                                    .fill(Color.white.opacity(0.15))
                                    .frame(height: 8)

                                Capsule()
                                    .fill(TryZonTheme.primaryGold)
                                    .frame(width: geo.size.width * CGFloat(min(Double(dailyAdsWatched) / 8.0, 1.0)), height: 8)
                            }
                        }
                        .frame(height: 8)

                        Text(dailyAdsWatched >= 8 ? "🎉 Grand Streak! All 8 Daily Rewards Claimed Today!" : "1 Video Ad (30s) = +0.5 ⚡ Credit  •  2 Ads = 1 Try-On")
                            .font(.system(size: 10.5, weight: .bold))
                            .foregroundColor(.white.opacity(0.7))
                            .multilineTextAlignment(.center)
                    }
                    .padding(16)
                    .background(TryZonTheme.surfaceVariant)
                    .cornerRadius(20)
                    .overlay(RoundedRectangle(cornerRadius: 20).stroke(TryZonTheme.primaryGold.opacity(0.5), lineWidth: 1))
                    .padding(.horizontal, 20)

                    // ── 3. 8-CARD GAMIFIED REWARD GRID (4 Rows x 2 Cols) ──
                    HStack {
                        Text("DAILY REWARD STREAK")
                            .font(.system(size: 11.5, weight: .black))
                            .foregroundColor(.white)
                            .tracking(0.8)

                        Spacer()

                        Text("\(max(0, 4 - (dailyAdsWatched / 2))) TRY-ONS LEFT")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundColor(TryZonTheme.primaryGold)
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 4)

                    VStack(spacing: 10) {
                        ForEach(0..<4, id: \.self) { row in
                            HStack(spacing: 10) {
                                ForEach(0..<2, id: \.self) { col in
                                    let idx = row * 2 + col
                                    adCardView(index: idx)
                                }
                            }
                        }
                    }
                    .padding(.horizontal, 20)

                    // ── 4. TIRED OF ADS PROMO CARD (₹20 MICRO PACK) ──
                    Button(action: { showPremiumSheet = true }) {
                        HStack(spacing: 10) {
                            Text("⚡")
                                .font(.system(size: 22))

                            VStack(alignment: .leading, spacing: 2) {
                                Text("Tired of Video Ads?")
                                    .font(.system(size: 13, weight: .black))
                                    .foregroundColor(.white)
                                Text("Get 10 Ad-Free Fast Passes for ₹20")
                                    .font(.system(size: 10.5, weight: .bold))
                                    .foregroundColor(TryZonTheme.primaryGold)
                            }

                            Spacer()

                            Text("BUY ₹20")
                                .font(.system(size: 11, weight: .black))
                                .foregroundColor(.black)
                                .padding(.horizontal, 14)
                                .padding(.vertical, 6)
                                .background(TryZonTheme.primaryGold)
                                .cornerRadius(20)
                        }
                        .padding(14)
                        .background(TryZonTheme.primaryGold.opacity(0.12))
                        .cornerRadius(18)
                        .overlay(RoundedRectangle(cornerRadius: 18).stroke(TryZonTheme.primaryGold, lineWidth: 1.2))
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 4)

                    Spacer(minLength: 20)
                }
            }

            // ── 5. REWARD CLAIMED CELEBRATORY POPUP ──
            if showRewardPopUp {
                Color.black.opacity(0.65).ignoresSafeArea()

                VStack(spacing: 16) {
                    Text("🎉 ⚡ 🎁")
                        .font(.system(size: 52))

                    Text("REWARD UNLOCKED!")
                        .font(.system(size: 20, weight: .black, design: .rounded))
                        .foregroundColor(.white)

                    Text("+0.5 ⚡ Credit Added!")
                        .font(.system(size: 15, weight: .black))
                        .padding(.horizontal, 16)
                        .padding(.vertical, 6)
                        .background(TryZonTheme.primaryGold.opacity(0.2))
                        .foregroundColor(TryZonTheme.primaryGold)
                        .cornerRadius(10)
                        .overlay(RoundedRectangle(cornerRadius: 10).stroke(TryZonTheme.primaryGold, lineWidth: 1))

                    Text((dailyAdsWatched % 2 == 0) ? "🔥 1 FULL FREE TRY-ON UNLOCKED!" : "🎯 Just 1 more ad to unlock next Free Try-On!")
                        .font(.system(size: 12.5, weight: .bold))
                        .foregroundColor(Color(red: 0/255, green: 230/255, blue: 118/255))
                        .multilineTextAlignment(.center)
                        .padding(.horizontal)

                    Button(action: { showRewardPopUp = false }) {
                        Text("CONTINUE 🚀")
                            .font(.system(size: 14, weight: .black))
                            .foregroundColor(.black)
                            .frame(maxWidth: .infinity)
                            .frame(height: 46)
                            .background(TryZonTheme.primaryGold)
                            .cornerRadius(23)
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 8)
                }
                .padding(24)
                .background(TryZonTheme.darkSurface)
                .cornerRadius(28)
                .overlay(RoundedRectangle(cornerRadius: 28).stroke(TryZonTheme.primaryGold, lineWidth: 1.5))
                .padding(32)
                .transition(.scale.combined(with: .opacity))
                .zIndex(100)
            }

            // ── 6. FULL-SCREEN VIDEO AD SIMULATOR ──
            if isAdShowing {
                ZStack {
                    Color.black.ignoresSafeArea()

                    VStack(spacing: 20) {
                        HStack {
                            Text("SPONSORED AD")
                                .font(.system(size: 10, weight: .black))
                                .padding(.horizontal, 8)
                                .padding(.vertical, 4)
                                .background(Color.white.opacity(0.2))
                                .foregroundColor(.white)
                                .cornerRadius(6)

                            Spacer()

                            Text("Reward in \(adCountdown)s")
                                .font(.system(size: 12, weight: .bold))
                                .foregroundColor(TryZonTheme.primaryGold)
                        }
                        .padding(20)

                        Spacer()

                        // High Fashion Sponsor Visual
                        VStack(spacing: 12) {
                            Image(systemName: "sparkles.tv")
                                .font(.system(size: 64))
                                .foregroundColor(TryZonTheme.primaryGold)

                            Text("TryZon AI Virtual Outfit Fitting")
                                .font(.system(size: 20, weight: .black))
                                .foregroundColor(.white)

                            Text("Transform your style in seconds with ultra-realistic AI")
                                .font(.system(size: 13, weight: .medium))
                                .foregroundColor(.white.opacity(0.7))
                                .multilineTextAlignment(.center)
                        }
                        .padding(30)

                        Spacer()

                        // Progress Countdown Line
                        ProgressView(value: Double(5 - adCountdown), total: 5.0)
                            .tint(TryZonTheme.primaryGold)
                            .padding(.horizontal, 30)
                            .padding(.bottom, 30)
                    }
                }
                .zIndex(200)
            }
        }
        .onReceive(timer) { _ in
            updateCountdown()
        }
        .onAppear {
            loadSavedAdProgress()
            updateCountdown()
        }
        .sheet(isPresented: $showPremiumSheet) {
            PremiumView(apiClient: apiClient)
        }
    }

    @ViewBuilder
    private func adCardView(index: Int) -> some View {
        let isCompleted = index < dailyAdsWatched
        let isReady = index == dailyAdsWatched
        let isEven = (index + 1) % 2 == 0
        let isGrandPrize = index == 7

        Button(action: {
            if isReady && !isAdShowing {
                startRewardedAd()
            }
        }) {
            VStack(spacing: 6) {
                // Top Header: Card Title & Badge
                HStack {
                    Text("CARD \(index + 1)")
                        .font(.system(size: 9, weight: .black))
                        .foregroundColor(isReady ? TryZonTheme.primaryGold : .white.opacity(0.5))

                    Spacer()

                    let badgeText = isGrandPrize ? "🎁 GRAND 4x" : (isEven ? "✨ 1 TRY-ON" : "🎯 1 MORE AD")
                    Text(badgeText)
                        .font(.system(size: 7, weight: .black))
                        .padding(.horizontal, 5)
                        .padding(.vertical, 2)
                        .background(isGrandPrize ? TryZonTheme.primaryGold : (isEven ? TryZonTheme.primaryGold : Color.white.opacity(0.12)))
                        .foregroundColor(isGrandPrize || isEven ? .black : TryZonTheme.primaryGold)
                        .cornerRadius(10)
                }

                Spacer(minLength: 0)

                // Center Content: Icon + Reward Amount
                HStack(spacing: 4) {
                    if isCompleted {
                        Image(systemName: "checkmark.circle.fill")
                            .foregroundColor(Color(red: 0/255, green: 230/255, blue: 118/255))
                            .font(.system(size: 14))
                        Text("+0.5 ⚡")
                            .font(.system(size: 14, weight: .black))
                            .foregroundColor(Color(red: 0/255, green: 230/255, blue: 118/255))
                    } else if isReady {
                        Image(systemName: "play.fill")
                            .foregroundColor(TryZonTheme.primaryGold)
                            .font(.system(size: 14))
                        Text("+0.5 ⚡")
                            .font(.system(size: 14, weight: .black))
                            .foregroundColor(TryZonTheme.primaryGold)
                    } else {
                        Image(systemName: "lock.fill")
                            .foregroundColor(.white.opacity(0.3))
                            .font(.system(size: 12))
                        Text("+0.5 ⚡")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(.white.opacity(0.3))
                    }
                }

                Spacer(minLength: 0)

                // Bottom Status
                Text(isCompleted ? "CLAIMED" : (isReady ? "⚡ TAP TO WATCH" : "LOCKED"))
                    .font(.system(size: 8.5, weight: .black))
                    .foregroundColor(isCompleted ? Color(red: 0/255, green: 230/255, blue: 118/255) : (isReady ? TryZonTheme.primaryGold : .white.opacity(0.3)))
            }
            .padding(10)
            .frame(maxWidth: .infinity)
            .frame(height: 84)
            .background(isCompleted ? Color(red: 0/255, green: 230/255, blue: 118/255).opacity(0.12) : (isReady ? TryZonTheme.primaryGold.opacity(0.18) : TryZonTheme.surfaceVariant.opacity(0.4)))
            .cornerRadius(14)
            .overlay(
                RoundedRectangle(cornerRadius: 14)
                    .stroke(isCompleted ? Color(red: 0/255, green: 230/255, blue: 118/255) : (isReady ? TryZonTheme.primaryGold : Color.white.opacity(0.08)), lineWidth: isReady ? 1.8 : 1)
            )
        }
        .disabled(!isReady || isAdShowing)
    }

    private func startRewardedAd() {
        guard dailyAdsWatched < 8 else { return }
        isAdShowing = true
        adCountdown = 5

        Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { timer in
            if adCountdown > 1 {
                adCountdown -= 1
            } else {
                timer.invalidate()
                isAdShowing = false
                claimAdReward()
            }
        }
    }

    private func claimAdReward() {
        Task {
            do {
                try await apiClient.claimRewardCredit(amount: 1)
                await MainActor.run {
                    self.dailyAdsWatched += 1
                    saveAdProgress()
                    self.showRewardPopUp = true
                }
            } catch {
                await MainActor.run {
                    self.dailyAdsWatched += 1
                    saveAdProgress()
                    self.showRewardPopUp = true
                }
            }
        }
    }

    private func loadSavedAdProgress() {
        let lastDate = UserDefaults.standard.string(forKey: "daily_ad_date") ?? ""
        let today = AuthViewModel.todayDateString()
        if lastDate != today {
            dailyAdsWatched = 0
            UserDefaults.standard.set(0, forKey: "daily_ad_count")
            UserDefaults.standard.set(today, forKey: "daily_ad_date")
        } else {
            dailyAdsWatched = UserDefaults.standard.integer(forKey: "daily_ad_count")
        }
    }

    private func saveAdProgress() {
        let today = AuthViewModel.todayDateString()
        UserDefaults.standard.set(dailyAdsWatched, forKey: "daily_ad_count")
        UserDefaults.standard.set(today, forKey: "daily_ad_date")
    }

    private func updateCountdown() {
        var calendar = Calendar.current
        calendar.timeZone = TimeZone(identifier: "UTC")!
        let now = Date()
        let tomorrow = calendar.startOfDay(for: calendar.date(byAdding: .day, value: 1, to: now)!)
        let diff = Int(tomorrow.timeIntervalSince(now))

        let hours = diff / 3600
        let mins = (diff % 3600) / 60
        let secs = diff % 60
        timeRemainingStr = String(format: "%02dh %02dm %02ds", hours, mins, secs)
    }
}
