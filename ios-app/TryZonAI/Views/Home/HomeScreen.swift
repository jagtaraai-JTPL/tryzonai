import SwiftUI

// MARK: - HomeScreen — Exact Android Native Replica (Clean Responsive Bounds)
public struct HomeScreen: View {
    @ObservedObject var apiClient: APIClient
    let onNavigateToTryOn: () -> Void
    let onNavigateToCatalog: () -> Void

    @State private var trendingProducts: [CatalogItem] = []
    @State private var heroIndex: Int = 0
    @State private var isLoadingProducts = false
    @State private var showSpinWheel = false

    private let heroOutfits: [(String, String, String)] = [
        ("Monaco Riviera Linen 🇲🇨", "👑 OLD MONEY", "https://tryzonai.com/api/v1/outfits/premium_catalog/ai_premium_mens_italian_riviera_linen_suit.webp"),
        ("NYC Executive Tuxedo 🇺🇸", "💼 CEO LOOK", "https://tryzonai.com/api/v1/outfits/premium_catalog/ai_premium_outfit_formal_ceo_black_suit_1778055862174.webp"),
        ("Seoul K-Style Knit 🇰🇷", "✨ ELEGANT", "https://tryzonai.com/api/v1/outfits/premium_catalog/ai_premium_women_teal_knit_dress.webp"),
        ("Dubai Royal Gown 🇦🇪", "🏆 LUXURY", "https://tryzonai.com/api/v1/outfits/premium_catalog/ai_premium_outfit_royal_queen_emerald_gold_1778038089053.webp"),
    ]

    private let fashionQuotes = [
        "\"Style is a way to say who you are without having to speak.\" — Rachel Zoe",
        "\"Fashion is the armor to survive everyday life.\" — Bill Cunningham",
        "\"Elegance is not about being noticed, it's about being remembered.\" — Giorgio Armani",
        "\"Dress impeccably and they remember the woman.\" — Coco Chanel",
    ]
    @State private var quoteIndex: Int = 0

    public init(apiClient: APIClient, onNavigateToTryOn: @escaping () -> Void, onNavigateToCatalog: @escaping () -> Void) {
        self.apiClient = apiClient
        self.onNavigateToTryOn = onNavigateToTryOn
        self.onNavigateToCatalog = onNavigateToCatalog
    }

    public var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(spacing: 0) {

                // ── 1. HERO SECTION (Compact & Premium like Android) ──
                heroSection
                    .padding(.horizontal, 16)
                    .padding(.top, 12)

                // ── 2. STATS BAR ──
                statsBar
                    .padding(.horizontal, 16)
                    .padding(.top, 16)

                // ── 3. GAMIFICATION ROW (Streak + Refer) ──
                gamificationRow
                    .padding(.horizontal, 16)
                    .padding(.top, 16)

                // ── 4. SPIN THE WHEEL BANNER ──
                spinWheelBanner
                    .padding(.horizontal, 16)
                    .padding(.top, 14)

                // ── 5. FASHION QUOTE OF THE DAY ──
                dailyFashionQuote
                    .padding(.horizontal, 16)
                    .padding(.top, 24)

                // ── 6. DISCOVER TRENDING ──
                discoverSection
                    .padding(.top, 28)

                // ── 7. HOW IT WORKS ──
                howItWorksSection
                    .padding(.top, 36)

                Spacer(minLength: 40)
            }
        }
        .clipped()
        .background(Color(red: 18/255, green: 18/255, blue: 20/255))
        .onAppear {
            loadTrending()
            startTimers()
        }
    }

    // ─────────────────────────────────────
    // MARK: 1. HERO
    // ─────────────────────────────────────
    private var heroSection: some View {
        ZStack(alignment: .bottomLeading) {
            // Crossfade outfit images
            ZStack {
                ForEach(Array(heroOutfits.enumerated()), id: \.offset) { i, outfit in
                    AsyncImage(url: URL(string: outfit.2)) { img in
                        img.resizable().scaledToFill()
                    } placeholder: {
                        TryZonTheme.surfaceVariant
                            .overlay(ProgressView().tint(TryZonTheme.primaryGold))
                    }
                    .frame(height: 440)
                    .frame(maxWidth: .infinity)
                    .clipped()
                    .opacity(i == heroIndex ? 1 : 0)
                    .animation(.easeInOut(duration: 0.8), value: heroIndex)
                }
            }
            .frame(height: 440)
            .clipped()

            // Gradient overlay
            LinearGradient(
                colors: [Color.clear, Color.black.opacity(0.45), Color.black.opacity(0.9)],
                startPoint: .top,
                endPoint: .bottom
            )
            .frame(height: 440)

            // Text content
            VStack(alignment: .leading, spacing: 12) {
                // Badge chip
                Text(heroOutfits[heroIndex].1)
                    .font(.system(size: 10, weight: .black))
                    .tracking(1.5)
                    .foregroundColor(TryZonTheme.primaryGold)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background(TryZonTheme.primaryGold.opacity(0.2))
                    .cornerRadius(8)
                    .overlay(RoundedRectangle(cornerRadius: 8).stroke(TryZonTheme.primaryGold.opacity(0.4), lineWidth: 1))
                    .animation(.easeInOut, value: heroIndex)

                // Headline
                Text("Try Any Outfit\nVirtually ✨")
                    .font(.system(size: 32, weight: .black, design: .rounded))
                    .foregroundColor(.white)
                    .lineSpacing(3)
                    .shadow(color: .black.opacity(0.6), radius: 4, x: 2, y: 2)

                Text("Photorealistic AI Try-On at the speed of thought.")
                    .font(.system(size: 13))
                    .foregroundColor(.white.opacity(0.85))
                    .lineLimit(1)
                    .minimumScaleFactor(0.85)

                // CTA Button
                Button(action: onNavigateToTryOn) {
                    HStack(spacing: 8) {
                        Image(systemName: "sparkles")
                            .font(.system(size: 15, weight: .bold))
                        Text("START NOW")
                            .font(.system(size: 15, weight: .black))
                            .tracking(1)
                    }
                    .foregroundColor(.black)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                    .background(TryZonTheme.primaryGold)
                    .cornerRadius(14)
                    .shadow(color: TryZonTheme.primaryGold.opacity(0.5), radius: 12, y: 4)
                }
                .buttonStyle(BounceButtonStyle())

                // Outfit dots
                HStack(spacing: 6) {
                    Spacer()
                    ForEach(0..<heroOutfits.count, id: \.self) { i in
                        RoundedRectangle(cornerRadius: 3)
                            .fill(i == heroIndex ? TryZonTheme.primaryGold : Color.white.opacity(0.35))
                            .frame(width: i == heroIndex ? 20 : 6, height: 6)
                            .animation(.spring(response: 0.3), value: heroIndex)
                    }
                    Spacer()
                }
                .padding(.top, 4)
            }
            .padding(.horizontal, 18)
            .padding(.bottom, 20)
        }
        .frame(height: 440)
        .cornerRadius(24)
        .clipped()
        .overlay(RoundedRectangle(cornerRadius: 24).stroke(Color.white.opacity(0.1), lineWidth: 1))
    }

    // ─────────────────────────────────────
    // MARK: 2. STATS BAR (Android StatsMarquee replica)
    // ─────────────────────────────────────
    private var statsBar: some View {
        HStack(spacing: 0) {
            statItem("50K+", "GLOBAL STYLES")
            divider
            statItem("98.5%", "ACCURACY")
            divider
            statItem("2M+", "TRY-ONS")
        }
        .padding(.vertical, 12)
        .padding(.horizontal, 8)
        .background(TryZonTheme.surfaceVariant.opacity(0.6))
        .cornerRadius(20)
        .overlay(
            RoundedRectangle(cornerRadius: 20)
                .stroke(Color.white.opacity(0.08), lineWidth: 1)
        )
    }

    private var divider: some View {
        Rectangle()
            .fill(Color.white.opacity(0.12))
            .frame(width: 1, height: 20)
    }

    private func statItem(_ value: String, _ label: String) -> some View {
        VStack(spacing: 2) {
            Text(value)
                .font(.system(size: 16, weight: .bold))
                .foregroundColor(.white)
                .lineLimit(1)
                .minimumScaleFactor(0.8)
            Text(label)
                .font(.system(size: 9, weight: .semibold))
                .foregroundColor(.white.opacity(0.6))
                .tracking(0.3)
                .lineLimit(1)
                .minimumScaleFactor(0.75)
        }
        .frame(maxWidth: .infinity)
    }

    // ─────────────────────────────────────
    // MARK: 3. GAMIFICATION ROW
    // ─────────────────────────────────────
    private var gamificationRow: some View {
        HStack(spacing: 12) {
            // Streak Widget
            streakWidget

            // Refer & Earn Banner
            referAndEarnBanner
        }
    }

    private var streakWidget: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 4) {
                Text("🔥")
                    .font(.system(size: 16))
                Text("STREAK")
                    .font(.system(size: 9, weight: .black))
                    .foregroundColor(TryZonTheme.primaryGold)
                    .tracking(1)
            }
            Text("1 Day")
                .font(.system(size: 18, weight: .black))
                .foregroundColor(.white)
            Text("Keep trying daily!")
                .font(.system(size: 9.5))
                .foregroundColor(.white.opacity(0.5))
                .lineLimit(1)
                .minimumScaleFactor(0.8)
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(TryZonTheme.surfaceVariant)
        .cornerRadius(18)
        .overlay(RoundedRectangle(cornerRadius: 18).stroke(TryZonTheme.primaryGold.opacity(0.25), lineWidth: 1))
    }

    private var referAndEarnBanner: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("🎁 REFER & EARN")
                .font(.system(size: 9, weight: .black))
                .foregroundColor(TryZonTheme.primaryGold)
                .tracking(1)
            Text("Get 2 Free Credits")
                .font(.system(size: 12, weight: .bold))
                .foregroundColor(.white)
                .lineLimit(1)
                .minimumScaleFactor(0.8)
            Button("Share →") {}
                .font(.system(size: 10, weight: .bold))
                .foregroundColor(.black)
                .padding(.horizontal, 10)
                .padding(.vertical, 4)
                .background(TryZonTheme.primaryGold)
                .cornerRadius(100)
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(TryZonTheme.primaryGold.opacity(0.08))
        .cornerRadius(18)
        .overlay(RoundedRectangle(cornerRadius: 18).stroke(TryZonTheme.primaryGold.opacity(0.3), lineWidth: 1))
    }

    // ─────────────────────────────────────
    // MARK: 4. SPIN WHEEL BANNER
    // ─────────────────────────────────────
    private var spinWheelBanner: some View {
        Button(action: { showSpinWheel = true }) {
            HStack(spacing: 12) {
                Text("🎡")
                    .font(.system(size: 26))
                VStack(alignment: .leading, spacing: 2) {
                    Text("SPIN THE WHEEL")
                        .font(.system(size: 11, weight: .black))
                        .foregroundColor(TryZonTheme.primaryGold)
                        .tracking(1)
                    Text("Win Free Credits & Exclusive Try-Ons!")
                        .font(.system(size: 11))
                        .foregroundColor(.white.opacity(0.8))
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                }
                Spacer(minLength: 4)
                Image(systemName: "chevron.right")
                    .foregroundColor(.white.opacity(0.4))
                    .font(.system(size: 12, weight: .bold))
            }
            .padding(14)
            .background(
                LinearGradient(
                    colors: [TryZonTheme.primaryGold.opacity(0.12), Color.purple.opacity(0.08)],
                    startPoint: .leading,
                    endPoint: .trailing
                )
            )
            .cornerRadius(18)
            .overlay(RoundedRectangle(cornerRadius: 18).stroke(TryZonTheme.primaryGold.opacity(0.3), lineWidth: 1))
        }
        .buttonStyle(BounceButtonStyle())
    }

    // ─────────────────────────────────────
    // MARK: 5. FASHION QUOTE
    // ─────────────────────────────────────
    private var dailyFashionQuote: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("🗣 STYLE QUOTE OF THE DAY")
                    .font(.system(size: 10, weight: .black))
                    .foregroundColor(TryZonTheme.primaryGold)
                    .tracking(1.2)
                Spacer()
            }
            Text(fashionQuotes[quoteIndex])
                .font(.system(size: 13, weight: .medium, design: .serif))
                .foregroundColor(.white.opacity(0.9))
                .lineSpacing(4)
                .id(quoteIndex)
                .transition(.opacity)
        }
        .padding(16)
        .background(TryZonTheme.surfaceVariant)
        .cornerRadius(20)
        .overlay(RoundedRectangle(cornerRadius: 20).stroke(TryZonTheme.primaryGold.opacity(0.15), lineWidth: 1))
    }

    // ─────────────────────────────────────
    // MARK: 6. DISCOVER TRENDING
    // ─────────────────────────────────────
    private var discoverSection: some View {
        VStack(spacing: 0) {
            // Section header
            VStack(spacing: 4) {
                Text("DISCOVER")
                    .font(.system(size: 10, weight: .black))
                    .foregroundColor(TryZonTheme.primaryGold)
                    .tracking(2)
                Text("Trending Global Fashion")
                    .font(.system(size: 22, weight: .black, design: .rounded))
                    .foregroundColor(.white)
            }
            .padding(.bottom, 16)

            // Horizontal scroll cards — clipped properly
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 14) {
                    if trendingProducts.isEmpty {
                        ForEach(Array(heroOutfits.enumerated()), id: \.offset) { _, outfit in
                            presetProductCard(outfit)
                        }
                    } else {
                        ForEach(trendingProducts.prefix(6)) { product in
                            productCard(product)
                        }
                    }
                }
                .padding(.horizontal, 16)
            }
        }
    }

    private func productCard(_ product: CatalogItem) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            ZStack(alignment: .topLeading) {
                AsyncImage(url: product.fullImageURL) { img in
                    img.resizable().scaledToFill()
                } placeholder: {
                    TryZonTheme.surfaceVariant
                        .overlay(ProgressView().tint(TryZonTheme.primaryGold))
                }
                .frame(width: 200, height: 240)
                .clipped()

                if let badge = product.badge {
                    Text(badge)
                        .font(.system(size: 9, weight: .bold))
                        .foregroundColor(TryZonTheme.primaryGold)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .background(Color.black.opacity(0.75))
                        .cornerRadius(100)
                        .overlay(RoundedRectangle(cornerRadius: 100).stroke(TryZonTheme.primaryGold.opacity(0.4), lineWidth: 1))
                        .padding(8)
                }
            }

            VStack(alignment: .leading, spacing: 4) {
                if let brand = product.brand {
                    Text(brand)
                        .font(.system(size: 9.5, weight: .semibold))
                        .foregroundColor(.white.opacity(0.5))
                        .lineLimit(1)
                }
                Text(product.name)
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(.white)
                    .lineLimit(1)

                Spacer(minLength: 6)

                Button(action: onNavigateToTryOn) {
                    Text("TRY ON")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(.black)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                        .background(TryZonTheme.primaryGold)
                        .cornerRadius(100)
                }
                .buttonStyle(BounceButtonStyle())
            }
            .padding(10)
        }
        .frame(width: 200)
        .background(TryZonTheme.surfaceVariant)
        .cornerRadius(18)
        .clipped()
        .overlay(RoundedRectangle(cornerRadius: 18).stroke(Color.white.opacity(0.08), lineWidth: 1))
    }

    private func presetProductCard(_ outfit: (String, String, String)) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            ZStack(alignment: .topLeading) {
                AsyncImage(url: URL(string: outfit.2)) { img in
                    img.resizable().scaledToFill()
                } placeholder: {
                    TryZonTheme.surfaceVariant
                }
                .frame(width: 200, height: 240)
                .clipped()

                Text(outfit.1)
                    .font(.system(size: 9, weight: .bold))
                    .foregroundColor(TryZonTheme.primaryGold)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background(Color.black.opacity(0.75))
                    .cornerRadius(100)
                    .overlay(RoundedRectangle(cornerRadius: 100).stroke(TryZonTheme.primaryGold.opacity(0.4), lineWidth: 1))
                    .padding(8)
            }

            VStack(alignment: .leading, spacing: 4) {
                Text(outfit.0)
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(.white)
                    .lineLimit(1)

                Spacer(minLength: 6)

                Button(action: onNavigateToTryOn) {
                    Text("TRY ON")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(.black)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                        .background(TryZonTheme.primaryGold)
                        .cornerRadius(100)
                }
                .buttonStyle(BounceButtonStyle())
            }
            .padding(10)
        }
        .frame(width: 200)
        .background(TryZonTheme.surfaceVariant)
        .cornerRadius(18)
        .clipped()
        .overlay(RoundedRectangle(cornerRadius: 18).stroke(Color.white.opacity(0.08), lineWidth: 1))
    }

    // ─────────────────────────────────────
    // MARK: 7. HOW IT WORKS
    // ─────────────────────────────────────
    private var howItWorksSection: some View {
        VStack(spacing: 4) {
            Text("HOW IT WORKS")
                .font(.system(size: 10, weight: .black))
                .foregroundColor(TryZonTheme.primaryGold)
                .tracking(2)
                .padding(.bottom, 14)

            VStack(spacing: 10) {
                stepCard("01", "📸", "Upload Photo", "Our AI understands your body shape instantly.")
                stepCard("02", "👗", "Pick Outfit", "TryZon Vision Engine maps any style to your body.")
                stepCard("03", "✨", "See Result", "Get photorealistic results in seconds.")
            }
            .padding(.horizontal, 16)
        }
    }

    private func stepCard(_ num: String, _ icon: String, _ title: String, _ desc: String) -> some View {
        HStack(spacing: 14) {
            VStack(spacing: 2) {
                Text(num)
                    .font(.system(size: 10, weight: .semibold))
                    .foregroundColor(.white.opacity(0.4))
                Text(icon)
                    .font(.system(size: 24))
            }
            .frame(width: 44)

            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.system(size: 18, weight: .bold))
                    .foregroundColor(.white)
                Text(desc)
                    .font(.system(size: 12))
                    .foregroundColor(.white.opacity(0.55))
                    .lineLimit(2)
            }

            Spacer()
        }
        .padding(16)
        .background(TryZonTheme.surfaceVariant)
        .cornerRadius(20)
        .overlay(RoundedRectangle(cornerRadius: 20).stroke(Color.white.opacity(0.08), lineWidth: 1))
    }

    // ─────────────────────────────────────
    // MARK: Helpers
    // ─────────────────────────────────────
    private func loadTrending() {
        isLoadingProducts = true
        Task {
            if let items = try? await apiClient.fetchCatalog() {
                DispatchQueue.main.async {
                    trendingProducts = items
                    isLoadingProducts = false
                }
            } else {
                DispatchQueue.main.async { isLoadingProducts = false }
            }
        }
    }

    private func startTimers() {
        Timer.scheduledTimer(withTimeInterval: 3.0, repeats: true) { _ in
            withAnimation(.easeInOut(duration: 0.8)) {
                heroIndex = (heroIndex + 1) % heroOutfits.count
            }
        }
        Timer.scheduledTimer(withTimeInterval: 8.0, repeats: true) { _ in
            withAnimation(.easeInOut(duration: 0.5)) {
                quoteIndex = (quoteIndex + 1) % fashionQuotes.count
            }
        }
    }
}
