import SwiftUI

// MARK: - TryZon AI Home Screen (Android Parity)
public struct HomeScreen: View {
    @ObservedObject var apiClient: APIClient
    let onNavigateToTryOn: () -> Void
    let onNavigateToCatalog: () -> Void

    @State private var trendingProducts: [CatalogItem] = []
    @State private var heroIndex: Int = 0
    @State private var isLoadingProducts = false
    @State private var fashionTipIndex: Int = 0

    // Hero outfit images (using server preset catalog)
    private let heroOutfits: [(String, String, String)] = [
        ("Old Money Riviera", "👑 LUXURY", "https://tryzonai.com/api/v1/outfits/premium_catalog/ai_premium_mens_italian_riviera_linen_suit.webp"),
        ("Executive Formals", "💼 CEO LOOK", "https://tryzonai.com/api/v1/outfits/premium_catalog/ai_premium_outfit_formal_ceo_black_suit_1778055862174.webp"),
        ("Teal Knit Dress", "✨ ELEGANT", "https://tryzonai.com/api/v1/outfits/premium_catalog/ai_premium_women_teal_knit_dress.webp"),
        ("Royal Emerald Gown", "🏆 ROYAL", "https://tryzonai.com/api/v1/outfits/premium_catalog/ai_premium_outfit_royal_queen_emerald_gold_1778038089053.webp"),
    ]

    private let fashionTips = [
        "\"Style is a way to say who you are without having to speak.\" — Rachel Zoe",
        "\"Fashion is the armor to survive the reality of everyday life.\" — Bill Cunningham",
        "\"Elegance is not about being noticed, it's about being remembered.\" — Giorgio Armani",
        "\"Dress shabbily and they remember the dress; dress impeccably and they remember the woman.\" — Coco Chanel",
    ]

    private let stats: [(String, String)] = [
        ("2.4M+", "Try-Ons Done"),
        ("98%", "Accuracy Rate"),
        ("< 3s", "GPU Speed"),
        ("50K+", "Outfits Catalog"),
    ]

    private let howItWorksSteps: [(String, String, String)] = [
        ("1", "photo.badge.plus", "Upload Your Photo"),
        ("2", "tshirt.fill", "Pick an Outfit"),
        ("3", "sparkles", "AI Generates Fit"),
        ("4", "crown.fill", "Look Stunning"),
    ]

    public init(apiClient: APIClient, onNavigateToTryOn: @escaping () -> Void, onNavigateToCatalog: @escaping () -> Void) {
        self.apiClient = apiClient
        self.onNavigateToTryOn = onNavigateToTryOn
        self.onNavigateToCatalog = onNavigateToCatalog
    }

    public var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(spacing: 0) {
                // 1. HERO SECTION
                heroSection

                // 2. STATS MARQUEE
                statsMarquee
                    .padding(.top, 20)

                // 3. QUICK ACTIONS
                quickActionsRow
                    .padding(.horizontal, 20)
                    .padding(.top, 24)

                // 4. FASHION QUOTE OF THE DAY
                fashionQuoteCard
                    .padding(.horizontal, 20)
                    .padding(.top, 24)

                // 5. TRENDING CATALOG
                trendingSection
                    .padding(.top, 28)

                // 6. HOW IT WORKS
                howItWorksSection
                    .padding(.top, 36)

                // 7. FINAL CTA BANNER
                finalCtaBanner
                    .padding(.horizontal, 20)
                    .padding(.top, 28)
                    .padding(.bottom, 40)
            }
        }
        .background(TryZonTheme.darkBackground)
        .onAppear {
            loadTrendingProducts()
            startHeroCycling()
            startTipCycling()
        }
    }

    // MARK: - 1. Hero Section
    private var heroSection: some View {
        ZStack(alignment: .bottomLeading) {
            // Background: Animated outfit cycling
            ZStack {
                ForEach(Array(heroOutfits.enumerated()), id: \.offset) { idx, outfit in
                    AsyncImage(url: URL(string: outfit.2)) { img in
                        img.resizable().scaledToFill()
                    } placeholder: {
                        TryZonTheme.surfaceVariant
                    }
                    .frame(maxWidth: .infinity)
                    .frame(height: 480)
                    .clipped()
                    .opacity(idx == heroIndex ? 1.0 : 0.0)
                    .animation(.easeInOut(duration: 0.8), value: heroIndex)
                }
            }
            .frame(height: 480)

            // Gradient overlay
            LinearGradient(
                colors: [
                    Color.clear,
                    Color.black.opacity(0.4),
                    Color.black.opacity(0.85),
                ],
                startPoint: .top,
                endPoint: .bottom
            )
            .frame(maxWidth: .infinity)
            .frame(height: 480)

            // Hero Text + CTA
            VStack(alignment: .leading, spacing: 16) {
                // Badge chip
                Text(heroOutfits[heroIndex].1)
                    .font(.system(size: 10, weight: .black))
                    .tracking(1.5)
                    .foregroundColor(TryZonTheme.primaryGold)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                    .background(TryZonTheme.primaryGold.opacity(0.15))
                    .cornerRadius(8)
                    .overlay(
                        RoundedRectangle(cornerRadius: 8)
                            .stroke(TryZonTheme.primaryGold.opacity(0.4), lineWidth: 1)
                    )
                    .animation(.easeInOut, value: heroIndex)

                Text("Try Any Outfit\nVirtually ✨")
                    .font(.system(size: 36, weight: .black, design: .rounded))
                    .foregroundColor(.white)
                    .lineSpacing(4)
                    .shadow(color: .black.opacity(0.5), radius: 4, x: 2, y: 2)

                Text("Photorealistic AI Try-On at the speed of thought.")
                    .font(.system(size: 14))
                    .foregroundColor(.white.opacity(0.85))

                // START NOW CTA
                Button(action: onNavigateToTryOn) {
                    HStack(spacing: 8) {
                        Image(systemName: "sparkles")
                            .font(.system(size: 16, weight: .bold))
                        Text("START NOW")
                            .font(.system(size: 16, weight: .black))
                            .tracking(1)
                    }
                    .foregroundColor(.black)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 16)
                    .background(TryZonTheme.primaryGold)
                    .cornerRadius(16)
                    .shadow(color: TryZonTheme.primaryGold.opacity(0.5), radius: 12, y: 4)
                }

                // Outfit indicator dots
                HStack(spacing: 6) {
                    ForEach(0..<heroOutfits.count, id: \.self) { i in
                        RoundedRectangle(cornerRadius: 3)
                            .fill(i == heroIndex ? TryZonTheme.primaryGold : Color.white.opacity(0.4))
                            .frame(width: i == heroIndex ? 20 : 6, height: 6)
                            .animation(.spring(response: 0.3), value: heroIndex)
                    }
                }
            }
            .padding(.horizontal, 24)
            .padding(.bottom, 28)
        }
        .frame(height: 480)
        .cornerRadius(28)
        .padding(.horizontal, 12)
        .padding(.top, 12)
    }

    // MARK: - 2. Stats Marquee
    private var statsMarquee: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 0) {
                ForEach(stats + stats, id: \.0) { stat in
                    HStack(spacing: 8) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(stat.0)
                                .font(.system(size: 18, weight: .black, design: .rounded))
                                .foregroundColor(TryZonTheme.primaryGold)
                            Text(stat.1)
                                .font(.system(size: 10, weight: .medium))
                                .foregroundColor(.white.opacity(0.6))
                        }
                        .padding(.horizontal, 20)
                        .padding(.vertical, 12)

                        Rectangle()
                            .fill(TryZonTheme.primaryGold.opacity(0.3))
                            .frame(width: 1, height: 36)
                    }
                }
            }
        }
        .background(TryZonTheme.surfaceVariant.opacity(0.6))
    }

    // MARK: - 3. Quick Actions
    private var quickActionsRow: some View {
        HStack(spacing: 14) {
            quickActionButton(
                icon: "camera.fill",
                title: "Try On Now",
                subtitle: "Upload & Fit",
                gradient: [TryZonTheme.primaryGold, Color.yellow],
                action: onNavigateToTryOn
            )

            quickActionButton(
                icon: "tshirt.fill",
                title: "Browse Catalog",
                subtitle: "5000+ Outfits",
                gradient: [Color.purple.opacity(0.8), Color.pink.opacity(0.7)],
                action: onNavigateToCatalog
            )
        }
    }

    private func quickActionButton(icon: String, title: String, subtitle: String, gradient: [Color], action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 12) {
                ZStack {
                    Circle()
                        .fill(LinearGradient(colors: gradient, startPoint: .topLeading, endPoint: .bottomTrailing))
                        .frame(width: 44, height: 44)
                    Image(systemName: icon)
                        .font(.system(size: 18, weight: .bold))
                        .foregroundColor(.black)
                }

                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(.white)
                    Text(subtitle)
                        .font(.system(size: 10))
                        .foregroundColor(.white.opacity(0.5))
                }

                Spacer()

                Image(systemName: "chevron.right")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(.white.opacity(0.4))
            }
            .padding(16)
            .background(TryZonTheme.surfaceVariant)
            .cornerRadius(18)
            .overlay(
                RoundedRectangle(cornerRadius: 18)
                    .stroke(TryZonTheme.primaryGold.opacity(0.2), lineWidth: 1)
            )
        }
    }

    // MARK: - 4. Fashion Quote
    private var fashionQuoteCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("STYLE QUOTE OF THE DAY")
                    .font(.system(size: 10, weight: .black))
                    .foregroundColor(TryZonTheme.primaryGold)
                    .tracking(1.2)
                Spacer()
                Image(systemName: "quote.bubble.fill")
                    .foregroundColor(TryZonTheme.primaryGold.opacity(0.6))
            }

            Text(fashionTips[fashionTipIndex])
                .font(.system(size: 14, weight: .medium, design: .serif))
                .foregroundColor(.white.opacity(0.9))
                .lineSpacing(5)
                .multilineTextAlignment(.leading)
                .id(fashionTipIndex)
                .transition(.opacity)
        }
        .padding(20)
        .background(
            LinearGradient(
                colors: [TryZonTheme.primaryGold.opacity(0.08), TryZonTheme.surfaceVariant],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        )
        .cornerRadius(20)
        .overlay(
            RoundedRectangle(cornerRadius: 20)
                .stroke(TryZonTheme.primaryGold.opacity(0.2), lineWidth: 1)
        )
    }

    // MARK: - 5. Trending Catalog
    private var trendingSection: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("DISCOVER")
                        .font(.system(size: 11, weight: .black))
                        .foregroundColor(TryZonTheme.primaryGold)
                        .tracking(2)
                    Text("Trending Global Fashion")
                        .font(.system(size: 20, weight: .black, design: .rounded))
                        .foregroundColor(.white)
                }
                Spacer()
                Button("View All", action: onNavigateToCatalog)
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(TryZonTheme.primaryGold)
            }
            .padding(.horizontal, 20)

            if isLoadingProducts {
                HStack(spacing: 12) {
                    ForEach(0..<4, id: \.self) { _ in
                        RoundedRectangle(cornerRadius: 16)
                            .fill(TryZonTheme.surfaceVariant)
                            .frame(width: 150, height: 220)
                            .shimmer()
                    }
                }
                .padding(.horizontal, 20)
            } else if trendingProducts.isEmpty {
                // Fallback: show preset outfit cards
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 14) {
                        ForEach(heroOutfits, id: \.0) { outfit in
                            presetOutfitCard(outfit)
                        }
                    }
                    .padding(.horizontal, 20)
                }
            } else {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 14) {
                        ForEach(trendingProducts.prefix(8)) { product in
                            trendingProductCard(product)
                        }
                    }
                    .padding(.horizontal, 20)
                }
            }
        }
    }

    private func presetOutfitCard(_ outfit: (String, String, String)) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            AsyncImage(url: URL(string: outfit.2)) { img in
                img.resizable().scaledToFill()
            } placeholder: {
                TryZonTheme.surfaceVariant
            }
            .frame(width: 150, height: 200)
            .clipped()
            .cornerRadius(14)

            VStack(alignment: .leading, spacing: 3) {
                Text(outfit.1)
                    .font(.system(size: 9, weight: .bold))
                    .foregroundColor(TryZonTheme.primaryGold)
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(TryZonTheme.primaryGold.opacity(0.15))
                    .cornerRadius(4)

                Text(outfit.0)
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(.white)
                    .lineLimit(1)

                Button(action: onNavigateToTryOn) {
                    Text("Try On →")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(.black)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(TryZonTheme.primaryGold)
                        .cornerRadius(8)
                }
            }
            .padding(.horizontal, 4)
        }
        .frame(width: 150)
        .padding(.bottom, 8)
        .background(TryZonTheme.surfaceVariant)
        .cornerRadius(18)
    }

    private func trendingProductCard(_ product: CatalogItem) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            AsyncImage(url: product.fullImageURL) { img in
                img.resizable().scaledToFill()
            } placeholder: {
                TryZonTheme.surfaceVariant
                    .overlay(ProgressView().tint(TryZonTheme.primaryGold))
            }
            .frame(width: 150, height: 200)
            .clipped()
            .cornerRadius(14)

            VStack(alignment: .leading, spacing: 4) {
                if let badge = product.badge {
                    Text(badge)
                        .font(.system(size: 9, weight: .bold))
                        .foregroundColor(TryZonTheme.primaryGold)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2)
                        .background(TryZonTheme.primaryGold.opacity(0.15))
                        .cornerRadius(4)
                }

                Text(product.name)
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(.white)
                    .lineLimit(2)

                Text("₹\(product.price)")
                    .font(.system(size: 13, weight: .black))
                    .foregroundColor(TryZonTheme.primaryGold)

                Button(action: onNavigateToTryOn) {
                    Text("Try On →")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(.black)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(TryZonTheme.primaryGold)
                        .cornerRadius(8)
                }
            }
            .padding(.horizontal, 6)
        }
        .frame(width: 150)
        .padding(.bottom, 8)
        .background(TryZonTheme.surfaceVariant)
        .cornerRadius(18)
    }

    // MARK: - 6. How It Works
    private var howItWorksSection: some View {
        VStack(spacing: 20) {
            VStack(spacing: 4) {
                Text("HOW IT WORKS")
                    .font(.system(size: 11, weight: .black))
                    .foregroundColor(TryZonTheme.primaryGold)
                    .tracking(2)
                Text("4 Simple Steps")
                    .font(.system(size: 20, weight: .black, design: .rounded))
                    .foregroundColor(.white)
            }

            HStack(spacing: 0) {
                ForEach(Array(howItWorksSteps.enumerated()), id: \.offset) { idx, step in
                    HStack(spacing: 0) {
                        VStack(spacing: 8) {
                            ZStack {
                                Circle()
                                    .fill(TryZonTheme.primaryGold.opacity(0.15))
                                    .frame(width: 56, height: 56)
                                    .overlay(
                                        Circle()
                                            .stroke(TryZonTheme.primaryGold.opacity(0.4), lineWidth: 1.5)
                                    )

                                VStack(spacing: 2) {
                                    Text(step.0)
                                        .font(.system(size: 10, weight: .black))
                                        .foregroundColor(TryZonTheme.primaryGold.opacity(0.6))
                                    Image(systemName: step.1)
                                        .font(.system(size: 16, weight: .bold))
                                        .foregroundColor(TryZonTheme.primaryGold)
                                }
                            }

                            Text(step.2)
                                .font(.system(size: 10, weight: .bold))
                                .foregroundColor(.white.opacity(0.8))
                                .multilineTextAlignment(.center)
                                .frame(width: 70)
                        }

                        if idx < howItWorksSteps.count - 1 {
                            Rectangle()
                                .fill(TryZonTheme.primaryGold.opacity(0.3))
                                .frame(height: 1)
                                .frame(maxWidth: .infinity)
                                .padding(.bottom, 36)
                        }
                    }
                }
            }
            .padding(.horizontal, 24)
        }
    }

    // MARK: - 7. Final CTA Banner
    private var finalCtaBanner: some View {
        VStack(spacing: 16) {
            Text("🎁 TRY YOUR FIRST LOOK FREE")
                .font(.system(size: 16, weight: .black))
                .foregroundColor(TryZonTheme.primaryGold)

            Text("No credit card required • Instant GPU processing • HD quality result")
                .font(.system(size: 12))
                .foregroundColor(.white.opacity(0.7))
                .multilineTextAlignment(.center)

            Button(action: onNavigateToTryOn) {
                HStack(spacing: 8) {
                    Image(systemName: "camera.fill")
                    Text("START FREE TRY-ON NOW →")
                        .font(.system(size: 14, weight: .black))
                }
                .foregroundColor(.black)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 16)
                .background(
                    LinearGradient(
                        colors: [TryZonTheme.primaryGold, Color.yellow],
                        startPoint: .leading,
                        endPoint: .trailing
                    )
                )
                .cornerRadius(16)
                .shadow(color: TryZonTheme.primaryGold.opacity(0.4), radius: 12, y: 4)
            }
        }
        .padding(24)
        .background(
            LinearGradient(
                colors: [TryZonTheme.primaryGold.opacity(0.08), TryZonTheme.surfaceVariant],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        )
        .cornerRadius(24)
        .overlay(
            RoundedRectangle(cornerRadius: 24)
                .stroke(TryZonTheme.primaryGold.opacity(0.3), lineWidth: 1)
        )
    }

    // MARK: - Helpers
    private func loadTrendingProducts() {
        isLoadingProducts = true
        Task {
            if let items = try? await apiClient.fetchCatalog(category: nil, gender: nil, search: nil) {
                DispatchQueue.main.async {
                    trendingProducts = items
                    isLoadingProducts = false
                }
            } else {
                DispatchQueue.main.async {
                    isLoadingProducts = false
                }
            }
        }
    }

    private func startHeroCycling() {
        Timer.scheduledTimer(withTimeInterval: 3.0, repeats: true) { _ in
            withAnimation(.easeInOut(duration: 0.8)) {
                heroIndex = (heroIndex + 1) % heroOutfits.count
            }
        }
    }

    private func startTipCycling() {
        Timer.scheduledTimer(withTimeInterval: 8.0, repeats: true) { _ in
            withAnimation(.easeInOut(duration: 0.5)) {
                fashionTipIndex = (fashionTipIndex + 1) % fashionTips.count
            }
        }
    }
}

// MARK: - Shimmer Effect Modifier
private struct ShimmerModifier: ViewModifier {
    @State private var phase: CGFloat = 0

    func body(content: Content) -> some View {
        content
            .overlay(
                LinearGradient(
                    gradient: Gradient(colors: [
                        Color.white.opacity(0),
                        Color.white.opacity(0.12),
                        Color.white.opacity(0),
                    ]),
                    startPoint: .init(x: phase, y: 0.5),
                    endPoint: .init(x: phase + 0.5, y: 0.5)
                )
            )
            .onAppear {
                withAnimation(.linear(duration: 1.4).repeatForever(autoreverses: false)) {
                    phase = 1.5
                }
            }
    }
}

extension View {
    func shimmer() -> some View {
        modifier(ShimmerModifier())
    }
}
