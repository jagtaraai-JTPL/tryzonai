import SwiftUI
import PhotosUI
import UIKit

// MARK: - Try-On Screen State Machine
enum TryOnScreen: Hashable {
    case upload
    case processing(Int) // session_id
    case result(TryOnStatusResponse)

    func hash(into hasher: inout Hasher) {
        switch self {
        case .upload: hasher.combine(0)
        case .processing(let id): hasher.combine(1); hasher.combine(id)
        case .result(let r): hasher.combine(2); hasher.combine(r.session_id)
        }
    }

    static func == (lhs: TryOnScreen, rhs: TryOnScreen) -> Bool {
        switch (lhs, rhs) {
        case (.upload, .upload): return true
        case (.processing(let a), .processing(let b)): return a == b
        case (.result(let a), .result(let b)): return a.session_id == b.session_id
        default: return false
        }
    }
}

public struct TryOnUploadView: View {
    @ObservedObject var apiClient: APIClient
    @StateObject private var viewModel = TryOnViewModel()
    @ObservedObject private var authViewModel = AuthViewModel.shared

    let onNavigateToResult: (TryOnStatusResponse) -> Void

    @State private var navPath: [TryOnScreen] = []
    @State private var selectedPersonItem: PhotosPickerItem? = nil
    @State private var selectedGarmentItem: PhotosPickerItem? = nil
    @State private var selectedOutfitUrl: String = ""

    @State private var showingCameraForPerson = false
    @State private var showingCameraForGarment = false

    @State private var showLoginRequiredModal = false
    @State private var showChoicePopupModal = false
    @State private var showPremiumModal = false
    @State private var showLoginSheet = false
    @State private var showDailyRewardModal = false
    @State private var showDemoTooltip = false
    @State private var demoStep = 0

    private let sampleOutfits = [
        SampleOutfit(name: "Riviera Linen", badge: "👑 OLD MONEY", category: "Suits", color: Color.blue,
                     imageUrl: "https://tryzonai.com/api/v1/outfits/premium_catalog/ai_premium_mens_italian_riviera_linen_suit.webp"),
        SampleOutfit(name: "Executive Suit", badge: "💼 EXECUTIVE", category: "Suits", color: Color.gray,
                     imageUrl: "https://tryzonai.com/api/v1/outfits/premium_catalog/ai_premium_outfit_formal_ceo_black_suit_1778055862174.webp"),
        SampleOutfit(name: "Teal Knit Dress", badge: "✨ ELEGANT", category: "Dresses", color: Color.teal,
                     imageUrl: "https://tryzonai.com/api/v1/outfits/premium_catalog/ai_premium_women_teal_knit_dress.webp"),
        SampleOutfit(name: "Royal Emerald", badge: "👑 LUXURY", category: "Dresses", color: Color.purple,
                     imageUrl: "https://tryzonai.com/api/v1/outfits/premium_catalog/ai_premium_outfit_royal_queen_emerald_gold_1778038089053.webp"),
        SampleOutfit(name: "Crimson Saree", badge: "🎆 ETHNIC", category: "Dresses", color: Color.red,
                     imageUrl: "https://tryzonai.com/api/v1/outfits/premium_catalog/ai_premium_women_red_bridal_lehenga.webp"),
        SampleOutfit(name: "Navy Blazer", badge: "🏆 FORMAL", category: "Suits", color: Color.indigo,
                     imageUrl: "https://tryzonai.com/api/v1/outfits/premium_catalog/ai_premium_mens_navy_blazer.webp"),
    ]

    private let sampleModels = [
        ("Female Model", "https://tryzonai.com/api/v1/outfits/premium_catalog/ai_premium_women_teal_knit_dress.webp"),
        ("Male Model", "https://tryzonai.com/api/v1/outfits/premium_catalog/ai_premium_mens_italian_riviera_linen_suit.webp"),
        ("Asian Model", "https://tryzonai.com/api/v1/outfits/premium_catalog/ai_premium_outfit_formal_ceo_black_suit_1778055862174.webp")
    ]

    private let samplePersonModelUrl = "https://tryzonai.com/api/v1/outfits/premium_catalog/ai_premium_women_teal_knit_dress.webp"

    public init(apiClient: APIClient, onNavigateToResult: @escaping (TryOnStatusResponse) -> Void) {
        self.apiClient = apiClient
        self.onNavigateToResult = onNavigateToResult
    }

    public var body: some View {
        NavigationStack(path: $navPath) {
            uploadScreenContent
                .navigationBarHidden(true)
                .navigationDestination(for: TryOnScreen.self) { screen in
                    switch screen {
                    case .upload:
                        EmptyView()
                    case .processing(let sessionId):
                        TryOnProcessingView(
                            sessionId: sessionId,
                            apiClient: apiClient,
                            userPhotoUrl: nil,
                            garmentPhotoUrl: selectedOutfitUrl.isEmpty ? nil : selectedOutfitUrl,
                            onCompleted: { statusRes in
                                navPath = [.result(statusRes)]
                            },
                            onCancel: {
                                navPath = []
                                viewModel.isProcessing = false
                            }
                        )
                        .navigationBarHidden(true)


                    case .result(let statusRes):
                        TryOnResultView(
                            resultImageUrl: statusRes.fullResultURL?.absoluteString ?? "",
                            highresUrl: statusRes.fullHighresURL?.absoluteString,
                            originalPhotoUrl: statusRes.fullOriginalURL?.absoluteString,
                            onTryAnother: {
                                navPath = []
                                viewModel.reset()
                            }
                        )
                        .navigationBarHidden(true)
                    }
                }
        }
        .onAppear {
            autoPrepareDefaultPhotos()
        }
        .overlay(
            Group {
                if showLoginRequiredModal {
                    LoginRequiredDialog(isPresented: $showLoginRequiredModal, onNavigateToLogin: {
                        showLoginRequiredModal = false
                        showLoginSheet = true
                    })
                }
            }
        )
        .overlay(
            Group {
                if showChoicePopupModal {
                    ChoicePopup(
                        isPresented: $showChoicePopupModal,
                        apiClient: apiClient,
                        onUseCredit: {
                            authViewModel.deductTryOnCredit()
                            startSubmissionTask()
                        },
                        onWatchAd: {
                            startSubmissionTask()
                        },
                        onNavigateToPremium: {
                            showPremiumModal = true
                        }
                    )
                }
            }
        )
        .sheet(isPresented: $showPremiumModal) {
            PremiumView(apiClient: apiClient)
        }
        .sheet(isPresented: $showLoginSheet) {
            LoginView(apiClient: apiClient, onNavigateToRegister: {})
        }
        .sheet(isPresented: $showDailyRewardModal) {
            DailyRewardView(apiClient: apiClient)
        }
    }


    // MARK: - Upload Screen Content (Matching Android TryOnUploadScreen)
    private var uploadScreenContent: some View {
        ScrollView {
            VStack(spacing: 16) {

                // ── 1. STUDIO HEADER: STEP PROCESS INDICATOR & QUICK ICON BUTTONS (DEMO 💡 & DAILY REWARDS 🎁) ──
                HStack(spacing: 6) {
                    // Process Step Indicator (1. MODEL • 2. OUTFIT • 3. RESULT)
                    HStack(spacing: 4) {
                        stepPill(step: 1, label: "MODEL", isActive: viewModel.selectedPersonImage == nil)
                        Text("•")
                            .font(.system(size: 10))
                            .foregroundColor(.white.opacity(0.3))
                        stepPill(step: 2, label: "OUTFIT", isActive: viewModel.selectedPersonImage != nil && viewModel.selectedGarmentImage == nil)
                        Text("•")
                            .font(.system(size: 10))
                            .foregroundColor(.white.opacity(0.3))
                        stepPill(step: 3, label: "RESULT", isActive: false)
                    }
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(TryZonTheme.surfaceVariant)
                    .cornerRadius(20)

                    Spacer()

                    // Demo Icon Button (💡)
                    Button(action: {
                        withAnimation {
                            showDemoTooltip.toggle()
                        }
                    }) {
                        Circle()
                            .fill(TryZonTheme.primaryGold.opacity(0.15))
                            .frame(width: 34, height: 34)
                            .overlay(Circle().stroke(TryZonTheme.primaryGold.opacity(0.4), lineWidth: 1))
                            .overlay(Text("💡").font(.system(size: 15)))
                    }

                    // Daily Reward Icon Button (🎁)
                    Button(action: {
                        showDailyRewardModal = true
                    }) {
                        Circle()
                            .fill(TryZonTheme.primaryGold)
                            .frame(width: 34, height: 34)
                            .overlay(Text("🎁").font(.system(size: 16)))
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 12)

                if showDemoTooltip {
                    HStack(spacing: 8) {
                        Text("💡 Quick Demo: 1. Select/Upload Photo ➔ 2. Choose Outfit ➔ 3. Tap Generate Try-On!")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundColor(.black)
                        Spacer()
                        Button(action: { showDemoTooltip = false }) {
                            Image(systemName: "xmark")
                               .font(.system(size: 10, weight: .bold))
                               .foregroundColor(.black)
                        }
                    }
                    .padding(10)
                    .background(TryZonTheme.primaryGold)
                    .cornerRadius(12)
                    .padding(.horizontal, 16)
                    .transition(.opacity.combined(with: .move(edge: .top)))
                }

                // ── 2. OVERLAPPING LIQUID GLASS DUO VIEWFINDER CARDS ──
                ZStack(alignment: .center) {
                    HStack(spacing: 10) {
                        // YOUR PHOTO CARD
                        studioDropCard(
                            title: "YOUR PHOTO",
                            iconName: "person.fill",
                            image: viewModel.selectedPersonImage,
                            hint: "Upload Selfie",
                            onClear: { viewModel.selectedPersonImage = nil },
                            pickerItem: $selectedPersonItem,
                            onCamera: { showingCameraForPerson = true }
                        )
                        .onChange(of: selectedPersonItem) { newItem in
                            Task {
                                if let data = try? await newItem?.loadTransferable(type: Data.self),
                                   let img = UIImage(data: data) {
                                    viewModel.selectedPersonImage = img
                                }
                            }
                        }

                        // CHOOSE OUTFIT CARD
                        studioDropCard(
                            title: "CHOOSE OUTFIT",
                            iconName: "tshirt.fill",
                            image: viewModel.selectedGarmentImage,
                            hint: "Upload Outfit",
                            onClear: { viewModel.selectedGarmentImage = nil },
                            pickerItem: $selectedGarmentItem,
                            onCamera: { showingCameraForGarment = true }
                        )
                        .onChange(of: selectedGarmentItem) { newItem in
                            Task {
                                if let data = try? await newItem?.loadTransferable(type: Data.self),
                                   let img = UIImage(data: data) {
                                    viewModel.selectedGarmentImage = img
                                }
                            }
                        }
                    }

                    // Central AI Fusion Match Pill Overlay (✦)
                    Circle()
                        .fill(Color.black.opacity(0.85))
                        .frame(width: 36, height: 36)
                        .shadow(color: TryZonTheme.primaryGold.opacity(0.4), radius: 6)
                        .overlay(Circle().stroke(TryZonTheme.primaryGold.opacity(0.6), lineWidth: 1.2))
                        .overlay(
                            Image(systemName: "sparkles")
                                .font(.system(size: 16))
                                .foregroundColor(TryZonTheme.primaryGold)
                        )
                }
                .padding(.horizontal, 16)

                // ── 3. FLOATING GLASS MODEL & OUTFIT SELECTOR STRIPS ──
                HStack(spacing: 10) {
                    // MODELS SELECTION STRIP
                    VStack(alignment: .leading, spacing: 8) {
                        HStack {
                            Text("Models")
                                .font(.system(size: 11, weight: .bold))
                                .foregroundColor(.white)
                            Spacer()
                            Text("View all ›")
                                .font(.system(size: 10, weight: .bold))
                                .foregroundColor(TryZonTheme.primaryGold)
                        }

                        HStack(spacing: 6) {
                            ForEach(0..<sampleModels.count, id: \.self) { idx in
                                let m = sampleModels[idx]
                                AsyncImage(url: URL(string: m.1)) { phase in
                                    if let img = phase.image {
                                        img.resizable().scaledToFill()
                                    } else {
                                        TryZonTheme.darkSurface
                                    }
                                }
                                .frame(width: 38, height: 38)
                                .cornerRadius(10)
                                .clipped()
                                .overlay(RoundedRectangle(cornerRadius: 10).stroke(TryZonTheme.primaryGold.opacity(0.4), lineWidth: 1))
                                .onTapGesture {
                                    loadSamplePersonImage(urlStr: m.1)
                                }
                            }
                        }
                    }
                    .padding(10)
                    .background(TryZonTheme.surfaceVariant.opacity(0.4))
                    .cornerRadius(16)
                    .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color.white.opacity(0.08), lineWidth: 1))

                    // OUTFITS SELECTION STRIP
                    VStack(alignment: .leading, spacing: 8) {
                        HStack {
                            Text("Outfits")
                                .font(.system(size: 11, weight: .bold))
                                .foregroundColor(.white)
                            Spacer()
                            Button(action: {
                                if let randomOutfit = sampleOutfits.randomElement() {
                                    selectedOutfitUrl = randomOutfit.imageUrl
                                    loadSampleGarmentImage(urlStr: randomOutfit.imageUrl)
                                }
                            }) {
                                Text("View all ›")
                                    .font(.system(size: 10, weight: .bold))
                                    .foregroundColor(TryZonTheme.primaryGold)
                            }
                        }

                        HStack(spacing: 6) {
                            ForEach(sampleOutfits.prefix(3)) { outfit in
                                let isSelected = selectedOutfitUrl == outfit.imageUrl
                                AsyncImage(url: URL(string: outfit.imageUrl)) { phase in
                                    if let img = phase.image {
                                        img.resizable().scaledToFill()
                                    } else {
                                        TryZonTheme.darkSurface
                                    }
                                }
                                .frame(width: 38, height: 38)
                                .cornerRadius(10)
                                .clipped()
                                .overlay(
                                    RoundedRectangle(cornerRadius: 10)
                                        .stroke(isSelected ? TryZonTheme.primaryGold : Color.white.opacity(0.15), lineWidth: isSelected ? 2 : 1)
                                )
                                .onTapGesture {
                                    selectedOutfitUrl = outfit.imageUrl
                                    loadSampleGarmentImage(urlStr: outfit.imageUrl)
                                }
                            }
                        }
                    }
                    .padding(10)
                    .background(TryZonTheme.surfaceVariant.opacity(0.4))
                    .cornerRadius(16)
                    .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color.white.opacity(0.08), lineWidth: 1))
                }
                .padding(.horizontal, 16)

                // ── 4. PRIMARY CTA BUTTON (GENERATE VIRTUAL TRY-ON ⚡) ──
                Button(action: executeTryOnFlow) {
                    HStack(spacing: 8) {
                        Image(systemName: "sparkles")
                            .font(.system(size: 18, weight: .bold))
                            .foregroundColor(.black)

                        Text("GENERATE VIRTUAL TRY-ON")
                            .font(.system(size: 14, weight: .black, design: .rounded))
                            .foregroundColor(.black)
                            .tracking(0.6)

                        Image(systemName: "arrow.right")
                            .font(.system(size: 15, weight: .bold))
                            .foregroundColor(.black)
                    }
                    .frame(maxWidth: .infinity)
                    .frame(height: 54)
                    .background(TryZonTheme.primaryGold)
                    .cornerRadius(27)
                    .shadow(color: TryZonTheme.primaryGold.opacity(0.4), radius: 8, y: 4)
                }
                .disabled(viewModel.isProcessing)
                .padding(.horizontal, 16)
                .padding(.top, 6)

                // ── 5. AI SAFETY POLICY FOOTER NOTE ──
                HStack(spacing: 6) {
                    Image(systemName: "shield.fill")
                        .font(.system(size: 11))
                        .foregroundColor(TryZonTheme.primaryGold.opacity(0.7))

                    Text("AI Safety Policy: Standard apparel required. Nudity restricted.")
                        .font(.system(size: 10.5, weight: .medium))
                        .foregroundColor(.white.opacity(0.65))
                }
                .padding(.top, 2)

                // ── 6. PRO FITTING TIPS & AI QUALITY CARD ──
                VStack(alignment: .leading, spacing: 6) {
                    HStack(spacing: 6) {
                        Image(systemName: "lightbulb.fill")
                            .font(.system(size: 13))
                            .foregroundColor(TryZonTheme.primaryGold)

                        Text("PRO FITTING TIPS")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundColor(TryZonTheme.primaryGold)
                            .tracking(0.5)
                    }

                    Text("For crisp 8K results, use well-lit front-facing photos. TryZon AI automatically handles lighting, texture warping, & pose alignment.")
                        .font(.system(size: 11, weight: .regular))
                        .foregroundColor(.white.opacity(0.75))
                        .lineSpacing(2)

                    HStack(spacing: 8) {
                        HStack(spacing: 4) {
                            Text("⚡ High-Res Render")
                                .font(.system(size: 10, weight: .bold))
                                .foregroundColor(TryZonTheme.primaryGold)
                        }
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(TryZonTheme.primaryGold.opacity(0.12))
                        .cornerRadius(12)
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(TryZonTheme.primaryGold.opacity(0.25), lineWidth: 1))

                        HStack(spacing: 4) {
                            Text("🔒 100% Private")
                                .font(.system(size: 10, weight: .bold))
                                .foregroundColor(.white.opacity(0.8))
                        }
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(Color.white.opacity(0.08))
                        .cornerRadius(12)
                    }
                    .padding(.top, 2)
                }
                .padding(14)
                .background(TryZonTheme.surfaceVariant.opacity(0.3))
                .cornerRadius(18)
                .overlay(RoundedRectangle(cornerRadius: 18).stroke(Color.white.opacity(0.08), lineWidth: 1))
                .padding(.horizontal, 16)

                // ── 7. TRUST BADGES ROW ──
                HStack(spacing: 20) {
                    trustBadge(icon: "bolt.fill", text: "Fast GPU")
                    trustBadge(icon: "lock.shield.fill", text: "Private")
                    trustBadge(icon: "star.fill", text: "HD Quality")
                    trustBadge(icon: "arrow.clockwise", text: "Real-Time")
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 24)
            }
        }
        .background(TryZonTheme.darkBackground)
    }

    // MARK: - Step Pill Helper
    private func stepPill(step: Int, label: String, isActive: Bool) -> some View {
        HStack(spacing: 3) {
            Text("\(step).")
                .font(.system(size: 10, weight: .black))
                .foregroundColor(isActive ? TryZonTheme.primaryGold : .white.opacity(0.5))

            Text(label)
                .font(.system(size: 9.5, weight: .bold))
                .foregroundColor(isActive ? TryZonTheme.primaryGold : .white.opacity(0.5))
        }
    }

    // MARK: - Studio Drop Card Helper View
    @ViewBuilder
    private func studioDropCard(
        title: String,
        iconName: String,
        image: UIImage?,
        hint: String,
        onClear: @escaping () -> Void,
        pickerItem: Binding<PhotosPickerItem?>,
        onCamera: @escaping () -> Void
    ) -> some View {
        VStack(spacing: 6) {
            HStack(spacing: 4) {
                Image(systemName: iconName)
                    .font(.system(size: 11))
                    .foregroundColor(TryZonTheme.primaryGold)
                Text(title)
                    .font(.system(size: 10.5, weight: .bold))
                    .foregroundColor(TryZonTheme.primaryGold)
            }

            ZStack {
                if let img = image {
                    Image(uiImage: img)
                        .resizable()
                        .scaledToFill()
                        .frame(height: 180)
                        .frame(maxWidth: .infinity)
                        .cornerRadius(16)
                        .clipped()
                        .overlay(
                            Button(action: onClear) {
                                Image(systemName: "xmark.circle.fill")
                                    .font(.title3)
                                    .foregroundColor(.red)
                                    .background(Circle().fill(Color.black))
                            }
                            .padding(8),
                            alignment: .topTrailing
                        )
                } else {
                    RoundedRectangle(cornerRadius: 16)
                        .fill(TryZonTheme.surfaceVariant)
                        .frame(height: 180)
                        .frame(maxWidth: .infinity)
                        .overlay(
                            RoundedRectangle(cornerRadius: 16)
                                .strokeBorder(
                                    TryZonTheme.primaryGold.opacity(0.3),
                                    style: StrokeStyle(lineWidth: 1.5, dash: [6])
                                )
                        )

                    VStack(spacing: 10) {
                        PhotosPicker(selection: pickerItem, matching: .images) {
                            VStack(spacing: 4) {
                                Image(systemName: iconName)
                                    .font(.system(size: 24))
                                    .foregroundColor(TryZonTheme.primaryGold)
                                Text(hint)
                                    .font(.system(size: 11, weight: .bold))
                                    .foregroundColor(TryZonTheme.primaryGold)
                            }
                            .padding(.horizontal, 10)
                            .padding(.vertical, 8)
                            .background(TryZonTheme.darkSurface)
                            .cornerRadius(12)
                        }

                        Button(action: onCamera) {
                            HStack(spacing: 4) {
                                Image(systemName: "camera.fill")
                                    .font(.caption2)
                                Text("Camera")
                                    .font(.system(size: 10, weight: .bold))
                            }
                            .foregroundColor(.white)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 5)
                            .background(Color.white.opacity(0.12))
                            .cornerRadius(8)
                        }
                    }
                }
            }
        }
        .frame(maxWidth: .infinity)
        .padding(8)
        .background(TryZonTheme.surfaceVariant.opacity(0.3))
        .cornerRadius(20)
        .overlay(RoundedRectangle(cornerRadius: 20).stroke(Color.white.opacity(0.08), lineWidth: 1))
    }

    // MARK: - Trust Badge
    private func trustBadge(icon: String, text: String) -> some View {
        VStack(spacing: 4) {
            Image(systemName: icon)
                .font(.system(size: 13))
                .foregroundColor(TryZonTheme.primaryGold)
            Text(text)
                .font(.system(size: 9, weight: .bold))
                .foregroundColor(.white.opacity(0.6))
        }
    }

    // MARK: - Actions
    private func autoPrepareDefaultPhotos() {
        if viewModel.selectedGarmentImage == nil && !sampleOutfits.isEmpty {
            let firstOutfit = sampleOutfits[0]
            selectedOutfitUrl = firstOutfit.imageUrl
            loadSampleGarmentImage(urlStr: firstOutfit.imageUrl)
        }
        if viewModel.selectedPersonImage == nil {
            loadSamplePersonImage(urlStr: samplePersonModelUrl)
        }
    }

    private func executeTryOnFlow() {
        if !authViewModel.canExecuteTryOn() {
            if authViewModel.showLoginRequiredModal {
                showLoginRequiredModal = true
            } else if authViewModel.showChoicePopupModal {
                showChoicePopupModal = true
            }
            return
        }

        self.authViewModel.deductTryOnCredit()
        startSubmissionTask()
    }

    private func startSubmissionTask() {
        Task {
            let (person, garment) = await ensureImagesPrepared()
            guard person != nil, garment != nil else {
                DispatchQueue.main.async {
                    self.viewModel.errorMessage = "Please upload both your photo and garment image."
                }
                return
            }

            await self.viewModel.startTryOn { sessionId in
                self.navPath = [.processing(sessionId)]
            }
        }
    }

    private func ensureImagesPrepared() async -> (UIImage?, UIImage?) {
        var personImg = viewModel.selectedPersonImage
        var garmentImg = viewModel.selectedGarmentImage

        if personImg == nil {
            personImg = await downloadSampleImage(from: samplePersonModelUrl)
            if let img = personImg {
                DispatchQueue.main.async { self.viewModel.selectedPersonImage = img }
            }
        }

        if garmentImg == nil && !sampleOutfits.isEmpty {
            let outfitUrl = selectedOutfitUrl.isEmpty ? sampleOutfits[0].imageUrl : selectedOutfitUrl
            garmentImg = await downloadSampleImage(from: outfitUrl)
            if let img = garmentImg {
                DispatchQueue.main.async { self.viewModel.selectedGarmentImage = img }
            }
        }

        return (personImg, garmentImg)
    }

    private func downloadSampleImage(from urlStr: String) async -> UIImage? {
        guard let url = URL(string: urlStr),
              let (data, _) = try? await URLSession.shared.data(from: url),
              let img = UIImage(data: data) else {
            return nil
        }
        return img
    }

    private func loadSampleGarmentImage(urlStr: String) {
        guard let url = URL(string: urlStr) else { return }
        Task {
            if let img = await downloadSampleImage(from: urlStr) {
                DispatchQueue.main.async {
                    self.viewModel.selectedGarmentImage = img
                }
            }
        }
    }

    private func loadSamplePersonImage(urlStr: String) {
        guard let url = URL(string: urlStr) else { return }
        Task {
            if let img = await downloadSampleImage(from: urlStr) {
                DispatchQueue.main.async {
                    self.viewModel.selectedPersonImage = img
                }
            }
        }
    }
}

// MARK: - Supporting Sample Outfit Struct
public struct SampleOutfit: Identifiable {
    public let id = UUID().uuidString
    public let name: String
    public let badge: String
    public let category: String
    public let color: Color
    public let imageUrl: String

    public init(name: String, badge: String, category: String, color: Color, imageUrl: String) {
        self.name = name
        self.badge = badge
        self.category = category
        self.color = color
        self.imageUrl = imageUrl
    }
}
