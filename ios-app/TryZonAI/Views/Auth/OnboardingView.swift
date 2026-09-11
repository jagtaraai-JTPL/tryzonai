import SwiftUI

// MARK: - Onboarding Root (4-page funnel matching Android)
public struct OnboardingView: View {
    let onFinish: () -> Void

    @State private var currentPage: Int = 0
    @State private var selectedGender: String = "Women"
    @State private var selectedGoalIndex: Int = 0
    @State private var selectedVibeIndex: Int = 0
    @State private var selectedPlanIndex: Int = 0
    @State private var shimmerOffset: CGFloat = -300

    @AppStorage("pref_gender") private var savedGender: String = "Women"

    private let totalPages = 4

    // Shimmer animation
    private var shimmerBrush: LinearGradient {
        LinearGradient(
            colors: [
                TryZonTheme.primaryGold,
                Color(red: 1.0, green: 0.97, blue: 0.76),
                TryZonTheme.primaryGold,
                TryZonTheme.primaryGold.opacity(0.8),
            ],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
    }

    private var ctaButtonText: String {
        switch currentPage {
        case 0: return "Start My Transformation →"
        case 1: return "Personalize My AI Studio →"
        case 2: return "Unlock My Free Daily Pass →"
        default:
            switch selectedPlanIndex {
            case 1: return "Unlock 15 Credits (₹39) ⚡"
            case 2: return "Upgrade to VIP Pro (₹379/mo) 👑"
            default: return "Claim Free Pass & Try First Outfit 🚀"
            }
        }
    }

    private var footnoteText: String {
        switch currentPage {
        case 0: return "🔒 100% Private · Preserves your face & identity"
        case 1: return "✨ Tailoring 8K neural engine to your fashion preferences"
        case 2: return "⚡ Takes less than 5 seconds · Powered by ComfyUI GPU cluster"
        default:
            switch selectedPlanIndex {
            case 1: return "⚡ 15 Paid Credits added instantly · 100% Ad-Free Pass"
            case 2: return "👑 VIP Turbo Speed Queue · Unlimited 8K AI Fitting"
            default: return "🎁 Zero credit card required · Instant free fitting access"
            }
        }
    }

    public init(onFinish: @escaping () -> Void) {
        self.onFinish = onFinish
    }

    public var body: some View {
        ZStack {
            // Dark background with radial gold glow
            TryZonTheme.darkBackground.ignoresSafeArea()
            ambientGlow

            VStack(spacing: 0) {
                // Top brand bar + skip
                topBar
                    .padding(.horizontal, 20)
                    .padding(.top, 16)

                Spacer(minLength: 12)

                // Main animated funnel content
                ZStack {
                    switch currentPage {
                    case 0: FunnelStep1_AttentionDesire()
                    case 1: FunnelStep2_Personalization(
                        selectedGender: $selectedGender,
                        selectedGoalIndex: $selectedGoalIndex,
                        selectedVibeIndex: $selectedVibeIndex
                    )
                    case 2: FunnelStep3_Simplicity()
                    default: FunnelStep4_LossAversion(selectedPlanIndex: $selectedPlanIndex)
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .transition(.asymmetric(
                    insertion: .move(edge: .trailing).combined(with: .opacity),
                    removal: .move(edge: .leading).combined(with: .opacity)
                ))
                .animation(.spring(response: 0.45, dampingFraction: 0.85), value: currentPage)
                .id(currentPage)

                Spacer(minLength: 12)

                // Progress dots + CTA
                VStack(spacing: 14) {
                    // Animated progress dots
                    HStack(spacing: 6) {
                        ForEach(0..<totalPages, id: \.self) { i in
                            RoundedRectangle(cornerRadius: 3)
                                .fill(i == currentPage ? TryZonTheme.primaryGold : Color.white.opacity(0.2))
                                .frame(width: i == currentPage ? 36 : 8, height: 6)
                                .animation(.spring(response: 0.3), value: currentPage)
                        }
                    }

                    // CTA Shimmer Button
                    ctaButton

                    // Footnote
                    Text(footnoteText)
                        .font(.system(size: 11.5))
                        .foregroundColor(.white.opacity(0.65))
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 24)
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 32)
            }
        }
        .preferredColorScheme(.dark)
    }

    // MARK: - Ambient Glow Background
    private var ambientGlow: some View {
        RadialGradient(
            colors: [TryZonTheme.primaryGold.opacity(0.18), Color.clear],
            center: .center,
            startRadius: 0,
            endRadius: 500
        )
        .ignoresSafeArea()
        .animation(.easeInOut(duration: 2.5).repeatForever(autoreverses: true), value: currentPage)
    }

    // MARK: - Top Bar
    private var topBar: some View {
        HStack(spacing: 8) {
            Image("AppLogoTransparent")
                .resizable()
                .scaledToFit()
                .frame(width: 28, height: 28)

            Text("TRYZON AI")
                .font(.system(size: 16, weight: .black))
                .foregroundColor(TryZonTheme.primaryGold)
                .tracking(2.5)

            Spacer()

            if currentPage < totalPages - 1 {
                Button("Skip") { onFinish() }
                    .font(.system(size: 13.5, weight: .bold))
                    .foregroundColor(.white.opacity(0.6))
            }
        }
    }

    // MARK: - CTA Button with shimmer
    private var ctaButton: some View {
        Button(action: advancePage) {
            ZStack {
                // Shimmer background
                RoundedRectangle(cornerRadius: 100)
                    .fill(shimmerBrush)
                    .frame(height: 54)
                    .shadow(color: TryZonTheme.primaryGold.opacity(0.5), radius: 14, y: 4)

                // Moving shimmer overlay
                GeometryReader { geo in
                    Rectangle()
                        .fill(
                            LinearGradient(
                                colors: [.clear, .white.opacity(0.35), .clear],
                                startPoint: .leading,
                                endPoint: .trailing
                            )
                        )
                        .frame(width: 80)
                        .offset(x: shimmerOffset)
                        .mask(RoundedRectangle(cornerRadius: 100).frame(width: geo.size.width))
                }
                .frame(height: 54)

                Text(ctaButtonText)
                    .font(.system(size: 15.5, weight: .black))
                    .foregroundColor(.black)
                    .tracking(0.5)
            }
            .frame(height: 54)
        }
        .buttonStyle(BounceButtonStyle())
        .onAppear {
            withAnimation(.linear(duration: 2.2).repeatForever(autoreverses: false)) {
                shimmerOffset = 400
            }
        }
    }

    // MARK: - Page Advance Logic
    private func advancePage() {
        savedGender = selectedGender
        if currentPage < totalPages - 1 {
            withAnimation { currentPage += 1 }
        } else {
            // Last page: proceed based on selected plan
            onFinish()
        }
    }
}


// ─────────────────────────────────────────────────
// MARK: - FUNNEL STEP 1: ATTENTION & DESIRE HOOK
// ─────────────────────────────────────────────────
struct FunnelStep1_AttentionDesire: View {
    private let outfits: [(String, String)] = [
        ("https://tryzonai.com/api/v1/outfits/premium_catalog/ai_premium_mens_italian_riviera_linen_suit.webp", "Monaco Atelier Linen Suit 🇲🇨"),
        ("https://tryzonai.com/api/v1/outfits/premium_catalog/ai_premium_outfit_formal_ceo_black_suit_1778055862174.webp", "New York Executive Tuxedo 🇺🇸"),
        ("https://tryzonai.com/api/v1/outfits/premium_catalog/ai_premium_women_teal_knit_dress.webp", "Seoul K-Style Minimalist 🇰🇷"),
        ("https://tryzonai.com/api/v1/outfits/premium_catalog/ai_premium_outfit_royal_queen_emerald_gold_1778038089053.webp", "Dubai Royal Velvet Couture 🇦🇪"),
    ]

    @State private var currentIndex: Int = 0
    @State private var sliderOffset: CGFloat = 0.5

    var body: some View {
        VStack(spacing: 20) {
            // Animated Outfit Comparison Showcase
            GeometryReader { geo in
                ZStack {
                    // Base: AI Result
                    AsyncImage(url: URL(string: outfits[currentIndex].0)) { img in
                        img.resizable().scaledToFill()
                    } placeholder: {
                        TryZonTheme.surfaceVariant
                    }
                    .frame(width: geo.size.width, height: geo.size.height)
                    .clipped()

                    // Gradient overlay
                    LinearGradient(
                        colors: [Color.clear, Color.black.opacity(0.7)],
                        startPoint: .top,
                        endPoint: .bottom
                    )

                    // Outfit label bottom
                    VStack {
                        Spacer()
                        HStack {
                            VStack(alignment: .leading, spacing: 4) {
                                Text("AI TRY-ON RESULT")
                                    .font(.system(size: 9, weight: .black))
                                    .foregroundColor(TryZonTheme.primaryGold)
                                    .tracking(1.5)
                                Text(outfits[currentIndex].1)
                                    .font(.system(size: 15, weight: .bold))
                                    .foregroundColor(.white)
                            }
                            Spacer()
                        }
                        .padding(16)
                    }

                    // Dots row
                    VStack {
                        HStack(spacing: 5) {
                            ForEach(0..<outfits.count, id: \.self) { i in
                                Circle()
                                    .fill(i == currentIndex ? TryZonTheme.primaryGold : Color.white.opacity(0.4))
                                    .frame(width: i == currentIndex ? 8 : 5, height: i == currentIndex ? 8 : 5)
                                    .animation(.spring(), value: currentIndex)
                            }
                        }
                        .padding(.top, 12)
                        Spacer()
                    }
                }
                .cornerRadius(24)
                .onTapGesture {
                    withAnimation(.easeInOut(duration: 0.5)) {
                        currentIndex = (currentIndex + 1) % outfits.count
                    }
                }
            }
            .frame(maxWidth: .infinity)
            .frame(height: 320)

            // Trust badges row
            HStack(spacing: 24) {
                trustBadge("🔒", "Private")
                trustBadge("⚡", "< 5 Secs")
                trustBadge("✨", "HD Quality")
                trustBadge("🆓", "First Free")
            }
        }
        .padding(.horizontal, 20)
        .onAppear {
            Timer.scheduledTimer(withTimeInterval: 2.5, repeats: true) { _ in
                withAnimation(.easeInOut(duration: 0.6)) {
                    currentIndex = (currentIndex + 1) % outfits.count
                }
            }
        }
    }

    private func trustBadge(_ icon: String, _ label: String) -> some View {
        VStack(spacing: 4) {
            Text(icon).font(.system(size: 20))
            Text(label)
                .font(.system(size: 9, weight: .bold))
                .foregroundColor(.white.opacity(0.65))
        }
    }
}

// ─────────────────────────────────────────────────
// MARK: - FUNNEL STEP 2: PERSONALIZATION
// ─────────────────────────────────────────────────
struct FunnelStep2_Personalization: View {
    @Binding var selectedGender: String
    @Binding var selectedGoalIndex: Int
    @Binding var selectedVibeIndex: Int

    private let genders = ["Women", "Men", "Non-Binary"]
    private let goals = ["Verify Fit Online", "Social Media Fashion", "Special Event Wardrobe"]
    private let vibes = ["All Styles & Mix", "Old Money & Quiet Luxury", "Oversized Streetwear", "Party & Date Night", "Royal Ethnic & Festive", "Minimal & Airport Chic"]

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 24) {
                // Title
                VStack(alignment: .leading, spacing: 6) {
                    Text("PERSONALIZE YOUR")
                        .font(.system(size: 11, weight: .black))
                        .foregroundColor(TryZonTheme.primaryGold)
                        .tracking(2)
                    Text("AI Fitting Studio")
                        .font(.system(size: 28, weight: .black, design: .rounded))
                        .foregroundColor(.white)
                }

                // Gender selector
                sectionLabel("👤 Your Style Profile")
                HStack(spacing: 10) {
                    ForEach(genders, id: \.self) { g in
                        genderChip(g)
                    }
                }

                // Fashion Goal
                sectionLabel("🎯 Your Fashion Goal")
                VStack(spacing: 8) {
                    ForEach(Array(goals.enumerated()), id: \.offset) { i, goal in
                        selectionRow(title: goal, isSelected: selectedGoalIndex == i) {
                            withAnimation(.spring(response: 0.25)) { selectedGoalIndex = i }
                        }
                    }
                }

                // Style Vibe
                sectionLabel("✨ Your Style Vibe")
                LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
                    ForEach(Array(vibes.enumerated()), id: \.offset) { i, vibe in
                        vibeChip(vibe, isSelected: selectedVibeIndex == i) {
                            withAnimation(.spring(response: 0.25)) { selectedVibeIndex = i }
                        }
                    }
                }
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 20)
        }
    }

    private func sectionLabel(_ text: String) -> some View {
        Text(text)
            .font(.system(size: 13, weight: .bold))
            .foregroundColor(.white.opacity(0.85))
    }

    private func genderChip(_ g: String) -> some View {
        Button(action: {
            withAnimation(.spring(response: 0.25)) { selectedGender = g }
        }) {
            Text(g)
                .font(.system(size: 13, weight: .bold))
                .foregroundColor(selectedGender == g ? .black : .white)
                .padding(.horizontal, 18)
                .padding(.vertical, 10)
                .background(selectedGender == g ? TryZonTheme.primaryGold : TryZonTheme.surfaceVariant)
                .cornerRadius(100)
                .overlay(
                    RoundedRectangle(cornerRadius: 100)
                        .stroke(selectedGender == g ? TryZonTheme.primaryGold : Color.white.opacity(0.15), lineWidth: 1)
                )
        }
    }

    private func selectionRow(title: String, isSelected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack {
                Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                    .foregroundColor(isSelected ? TryZonTheme.primaryGold : .white.opacity(0.35))
                    .font(.system(size: 18))
                Text(title)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(isSelected ? .white : .white.opacity(0.7))
                Spacer()
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
            .background(isSelected ? TryZonTheme.primaryGold.opacity(0.12) : TryZonTheme.surfaceVariant)
            .cornerRadius(16)
            .overlay(
                RoundedRectangle(cornerRadius: 16)
                    .stroke(isSelected ? TryZonTheme.primaryGold.opacity(0.5) : Color.white.opacity(0.08), lineWidth: 1)
            )
        }
    }

    private func vibeChip(_ vibe: String, isSelected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(vibe)
                .font(.system(size: 11, weight: .semibold))
                .foregroundColor(isSelected ? .black : .white.opacity(0.75))
                .multilineTextAlignment(.center)
                .padding(.horizontal, 10)
                .padding(.vertical, 10)
                .frame(maxWidth: .infinity)
                .background(isSelected ? TryZonTheme.primaryGold : TryZonTheme.surfaceVariant)
                .cornerRadius(14)
                .overlay(
                    RoundedRectangle(cornerRadius: 14)
                        .stroke(isSelected ? TryZonTheme.primaryGold : Color.white.opacity(0.08), lineWidth: 1)
                )
        }
    }
}

// ─────────────────────────────────────────────────
// MARK: - FUNNEL STEP 3: COGNITIVE SIMPLICITY
// ─────────────────────────────────────────────────
struct FunnelStep3_Simplicity: View {
    private let steps: [(String, String, String, String)] = [
        ("01", "📸", "Upload Photo", "Our AI understands your body shape instantly."),
        ("02", "👗", "Pick Outfit", "TryZon Vision Engine maps any style to your body."),
        ("03", "✨", "See Result", "Get photorealistic results in seconds."),
    ]

    @State private var visibleStep: Int = 0

    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            VStack(alignment: .leading, spacing: 6) {
                Text("HOW IT WORKS")
                    .font(.system(size: 11, weight: .black))
                    .foregroundColor(TryZonTheme.primaryGold)
                    .tracking(2)
                Text("3 Simple Steps")
                    .font(.system(size: 28, weight: .black, design: .rounded))
                    .foregroundColor(.white)
                Text("Powered by ComfyUI GPU Cluster")
                    .font(.system(size: 12))
                    .foregroundColor(.white.opacity(0.5))
            }

            ForEach(Array(steps.enumerated()), id: \.offset) { idx, step in
                stepCard(step, isVisible: visibleStep >= idx)
                    .animation(.spring(response: 0.5, dampingFraction: 0.8).delay(Double(idx) * 0.2), value: visibleStep)
            }

            // Social proof pill
            HStack(spacing: 8) {
                Image(systemName: "star.fill")
                    .foregroundColor(TryZonTheme.primaryGold)
                    .font(.system(size: 12))
                Text("2.4M+ try-ons done • 98.5% accuracy • < 5 second AI fit")
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundColor(.white.opacity(0.7))
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .background(TryZonTheme.surfaceVariant)
            .cornerRadius(100)
        }
        .padding(.horizontal, 20)
        .onAppear {
            for i in 0..<steps.count {
                DispatchQueue.main.asyncAfter(deadline: .now() + Double(i) * 0.3) {
                    withAnimation { visibleStep = i }
                }
            }
        }
    }

    private func stepCard(_ step: (String, String, String, String), isVisible: Bool) -> some View {
        HStack(spacing: 16) {
            // Number + icon
            VStack(spacing: 4) {
                Text(step.0)
                    .font(.system(size: 10, weight: .black))
                    .foregroundColor(.white.opacity(0.35))
                Text(step.1)
                    .font(.system(size: 28))
            }
            .frame(width: 50)

            VStack(alignment: .leading, spacing: 3) {
                Text(step.2)
                    .font(.system(size: 17, weight: .bold))
                    .foregroundColor(.white)
                Text(step.3)
                    .font(.system(size: 13))
                    .foregroundColor(.white.opacity(0.6))
            }

            Spacer()

            Image(systemName: "checkmark.circle.fill")
                .foregroundColor(TryZonTheme.primaryGold)
                .font(.system(size: 20))
                .opacity(isVisible ? 1 : 0)
        }
        .padding(20)
        .background(TryZonTheme.surfaceVariant)
        .cornerRadius(24)
        .overlay(
            RoundedRectangle(cornerRadius: 24)
                .stroke(Color.white.opacity(0.08), lineWidth: 1)
        )
        .opacity(isVisible ? 1 : 0)
        .offset(x: isVisible ? 0 : 30)
    }
}

// ─────────────────────────────────────────────────
// MARK: - FUNNEL STEP 4: LOSS AVERSION + PLAN SELECT
// ─────────────────────────────────────────────────
struct FunnelStep4_LossAversion: View {
    @Binding var selectedPlanIndex: Int

    private let plans: [(Int, String, String, String, String, String)] = [
        // (index, badge, title, subtitle, price, color)
        (0, "🎁 FREE", "Free Daily Pass", "1 try-on/day + Welcome 2 Bonus Credits", "₹0 / Always Free", "white"),
        (1, "⚡ POPULAR", "Pocket Credit Pack", "15 Credits = 15 Instant HD Try-Ons (100% Ad-Free)", "₹39 One-Time", "gold"),
        (2, "👑 VIP", "Pro Unlimited", "Unlimited AI Try-Ons • Zero Ads • VIP GPU Queue", "₹379/month", "purple"),
    ]

    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            VStack(alignment: .leading, spacing: 6) {
                Text("CHOOSE YOUR PLAN")
                    .font(.system(size: 11, weight: .black))
                    .foregroundColor(TryZonTheme.primaryGold)
                    .tracking(2)
                Text("Start Free · Upgrade Anytime")
                    .font(.system(size: 26, weight: .black, design: .rounded))
                    .foregroundColor(.white)
            }

            ForEach(plans, id: \.0) { plan in
                planCard(plan)
            }

            // Guarantee line
            HStack(spacing: 6) {
                Image(systemName: "lock.shield.fill")
                    .foregroundColor(.green)
                    .font(.system(size: 14))
                Text("Secure payment via App Store · Cancel anytime · No hidden fees")
                    .font(.system(size: 10))
                    .foregroundColor(.white.opacity(0.5))
            }
        }
        .padding(.horizontal, 20)
    }

    private func planCard(_ plan: (Int, String, String, String, String, String)) -> some View {
        let isSelected = selectedPlanIndex == plan.0
        let isGold = plan.0 == 1
        let isPurple = plan.0 == 2

        let circleColor: Color = {
            if !isSelected { return Color.white.opacity(0.1) }
            if isGold { return TryZonTheme.primaryGold }
            if isPurple { return Color.purple }
            return Color.white
        }()

        let badgeBg: Color = {
            if isGold { return TryZonTheme.primaryGold }
            if isPurple { return Color.purple }
            return Color.white.opacity(0.2)
        }()

        let priceColor: Color = {
            if isGold { return TryZonTheme.primaryGold }
            if isPurple { return Color.purple.opacity(0.9) }
            return Color.white.opacity(0.7)
        }()

        let cardBg: Color = {
            if !isSelected { return TryZonTheme.surfaceVariant }
            if isGold { return TryZonTheme.primaryGold.opacity(0.12) }
            if isPurple { return Color.purple.opacity(0.12) }
            return Color.white.opacity(0.08)
        }()

        let strokeColor: Color = {
            if !isSelected { return Color.white.opacity(0.08) }
            if isGold { return TryZonTheme.primaryGold.opacity(0.6) }
            if isPurple { return Color.purple.opacity(0.5) }
            return Color.white.opacity(0.3)
        }()

        let strokeWidth: CGFloat = isSelected ? 1.5 : 1.0

        return Button(action: {
            withAnimation(.spring(response: 0.3)) { selectedPlanIndex = plan.0 }
        }) {
            HStack(spacing: 14) {
                // Selection indicator
                ZStack {
                    Circle()
                        .fill(circleColor)
                        .frame(width: 24, height: 24)
                    if isSelected {
                        Image(systemName: "checkmark")
                            .font(.system(size: 11, weight: .black))
                            .foregroundColor(isGold ? .black : isPurple ? .white : .black)
                    }
                }

                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 6) {
                        Text(plan.1)
                            .font(.system(size: 9, weight: .black))
                            .foregroundColor(isGold ? .black : .white)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 3)
                            .background(badgeBg)
                            .cornerRadius(6)

                        Text(plan.2)
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(.white)
                    }

                    Text(plan.3)
                        .font(.system(size: 11))
                        .foregroundColor(.white.opacity(0.6))
                        .lineLimit(2)

                    Text(plan.4)
                        .font(.system(size: 13, weight: .black))
                        .foregroundColor(priceColor)
                }

                Spacer()
            }
            .padding(16)
            .background(cardBg)
            .cornerRadius(20)
            .overlay(
                RoundedRectangle(cornerRadius: 20)
                    .stroke(strokeColor, lineWidth: strokeWidth)
            )
            .scaleEffect(isSelected ? 1.01 : 1.0)
        }
        .buttonStyle(BounceButtonStyle())
    }
}
