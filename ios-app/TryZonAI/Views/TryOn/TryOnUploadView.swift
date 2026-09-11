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

    private let categories = ["Tops", "Bottoms", "Dresses", "Suits", "Outerwear"]

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
                            runActualTryOnSubmission()
                        },
                        onWatchAd: {
                            runActualTryOnSubmission()
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
    }

    // MARK: - Upload Screen Content
    private var uploadScreenContent: some View {
        ScrollView {
            VStack(spacing: 20) {
                // Header Banner
                VStack(spacing: 6) {
                    HStack(spacing: 6) {
                        Image("AppLogoTransparent")
                            .resizable()
                            .scaledToFit()
                            .frame(width: 28, height: 28)
                        Text("TRY-ON FITTING ROOM")
                            .font(.system(size: 18, weight: .black, design: .rounded))
                            .foregroundColor(TryZonTheme.primaryGold)
                        Text("⚡")
                    }

                    Text("Upload your photo & garment for 100% realistic AI outfit fit")
                        .font(.system(size: 11, weight: .medium))
                        .foregroundColor(.white.opacity(0.7))
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 24)
                }
                .padding(.top, 14)

                // Category Selector
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 10) {
                        ForEach(categories, id: \.self) { cat in
                            Button(action: { viewModel.selectedCategory = cat }) {
                                Text(cat)
                                    .font(.system(size: 12, weight: .bold))
                                    .padding(.horizontal, 16)
                                    .padding(.vertical, 8)
                                    .background(viewModel.selectedCategory == cat ? TryZonTheme.primaryGold : TryZonTheme.surfaceVariant)
                                    .foregroundColor(viewModel.selectedCategory == cat ? .black : .white)
                                    .cornerRadius(20)
                            }
                        }
                    }
                    .padding(.horizontal, 16)
                }

                // Dual Photo Pickers (Person + Garment)
                HStack(spacing: 14) {
                    photoUploadCard(
                        label: "YOUR MODEL PHOTO",
                        icon: "person.crop.rectangle.fill",
                        hint: "Upload Photo",
                        image: viewModel.selectedPersonImage,
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

                    photoUploadCard(
                        label: "GARMENT OUTFIT",
                        icon: "tshirt.fill",
                        hint: "Upload Outfit",
                        image: viewModel.selectedGarmentImage,
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
                .padding(.horizontal, 16)

                // Quick Preset Outfits Selector
                VStack(alignment: .leading, spacing: 10) {
                    Text("SELECT FROM POPULAR AI OUTFITS")
                        .font(.system(size: 10, weight: .black))
                        .foregroundColor(TryZonTheme.primaryGold.opacity(0.9))
                        .tracking(1)
                        .padding(.horizontal, 16)

                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 10) {
                            ForEach(sampleOutfits) { outfit in
                                sampleOutfitCard(outfit)
                            }
                        }
                        .padding(.horizontal, 16)
                    }
                }

                // Store URL Option
                VStack(alignment: .leading, spacing: 6) {
                    Text("OR PASTE CLOTHING STORE URL (MYNTRA / AJIO)")
                        .font(.system(size: 9, weight: .bold))
                        .foregroundColor(TryZonTheme.primaryGold.opacity(0.8))
                        .tracking(1)
                        .padding(.horizontal, 16)

                    HStack {
                        Image(systemName: "link")
                            .foregroundColor(TryZonTheme.primaryGold)
                        TextField("https://myntra.com/product/...", text: $viewModel.garmentStoreUrl)
                            .font(.system(size: 12))
                            .foregroundColor(.white)
                            .autocapitalization(.none)
                    }
                    .padding(12)
                    .background(TryZonTheme.surfaceVariant)
                    .cornerRadius(12)
                    .padding(.horizontal, 16)
                }

                // Error Display
                if let err = viewModel.errorMessage {
                    HStack {
                        Image(systemName: "exclamationmark.triangle.fill")
                            .foregroundColor(.red)
                        Text(err)
                            .font(.caption.bold())
                            .foregroundColor(.red)
                    }
                    .padding(.horizontal, 16)
                }

                // Generate CTA Button (Always Interactive)
                ShimmeringGoldButton(
                    title: viewModel.isProcessing ? "SUBMITTING..." : "GENERATE VIRTUAL TRY-ON ⚡",
                    subtitle: "100% Realistic Fit • Fast GPU Queue",
                    isEnabled: !viewModel.isProcessing
                ) {
                    executeTryOnFlow()
                }
                .padding(.horizontal, 16)

                // Trust Badges Row
                HStack(spacing: 20) {
                    trustBadge(icon: "bolt.fill", text: "Fast GPU")
                    trustBadge(icon: "lock.shield.fill", text: "Private")
                    trustBadge(icon: "star.fill", text: "HD Quality")
                    trustBadge(icon: "arrow.clockwise", text: "Real-Time")
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 30)
            }
        }
        .background(TryZonTheme.darkBackground)
    }

    // MARK: - Photo Upload Card
    @ViewBuilder
    private func photoUploadCard(
        label: String,
        icon: String,
        hint: String,
        image: UIImage?,
        onClear: @escaping () -> Void,
        pickerItem: Binding<PhotosPickerItem?>,
        onCamera: @escaping () -> Void
    ) -> some View {
        VStack(spacing: 8) {
            Text(label)
                .font(.system(size: 11, weight: .bold))
                .foregroundColor(TryZonTheme.primaryGold)

            ZStack {
                if let img = image {
                    Image(uiImage: img)
                        .resizable()
                        .scaledToFill()
                        .frame(width: 155, height: 210)
                        .cornerRadius(16)
                        .clipped()
                        .overlay(
                            Button(action: onClear) {
                                Image(systemName: "xmark.circle.fill")
                                    .font(.title2)
                                    .foregroundColor(.red)
                                    .background(Circle().fill(Color.black))
                            }
                            .padding(8),
                            alignment: .topTrailing
                        )
                } else {
                    RoundedRectangle(cornerRadius: 16)
                        .fill(TryZonTheme.surfaceVariant)
                        .frame(width: 155, height: 210)
                        .overlay(
                            RoundedRectangle(cornerRadius: 16)
                                .strokeBorder(
                                    TryZonTheme.primaryGold.opacity(0.3),
                                    style: StrokeStyle(lineWidth: 1.5, dash: [6])
                                )
                        )

                    VStack(spacing: 12) {
                        PhotosPicker(selection: pickerItem, matching: .images) {
                            VStack(spacing: 6) {
                                Image(systemName: icon)
                                    .font(.system(size: 28))
                                    .foregroundColor(TryZonTheme.primaryGold)
                                Text(hint)
                                    .font(.system(size: 11, weight: .bold))
                                    .foregroundColor(TryZonTheme.primaryGold)
                            }
                            .padding(.horizontal, 12)
                            .padding(.vertical, 10)
                            .background(TryZonTheme.darkSurface)
                            .cornerRadius(12)
                        }

                        Button(action: onCamera) {
                            HStack(spacing: 4) {
                                Image(systemName: "camera.fill")
                                    .font(.caption)
                                Text("Camera")
                                    .font(.system(size: 10, weight: .bold))
                            }
                            .foregroundColor(.white)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 6)
                            .background(Color.white.opacity(0.12))
                            .cornerRadius(8)
                        }
                    }
                }
            }
        }
    }

    // MARK: - Sample Outfit Card
    private func sampleOutfitCard(_ outfit: SampleOutfit) -> some View {
        let isSelected = selectedOutfitUrl == outfit.imageUrl
        return Button(action: {
            selectedOutfitUrl = outfit.imageUrl
            viewModel.selectedCategory = outfit.category
            loadSampleGarmentImage(urlStr: outfit.imageUrl)
        }) {
            VStack(alignment: .leading, spacing: 6) {
                ZStack(alignment: .topTrailing) {
                    AsyncImage(url: URL(string: outfit.imageUrl)) { img in
                        img.resizable().scaledToFill()
                    } placeholder: {
                        outfit.color.opacity(0.5)
                    }
                    .frame(width: 90, height: 110)
                    .clipped()
                    .cornerRadius(10)

                    if isSelected {
                        Image(systemName: "checkmark.circle.fill")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(TryZonTheme.primaryGold)
                            .background(Circle().fill(Color.black))
                            .padding(6)
                    }
                }

                Text(outfit.badge)
                    .font(.system(size: 8, weight: .bold))
                    .padding(.horizontal, 5)
                    .padding(.vertical, 2)
                    .background(TryZonTheme.primaryGold)
                    .foregroundColor(.black)
                    .cornerRadius(4)

                Text(outfit.name)
                    .font(.system(size: 10, weight: .bold))
                    .foregroundColor(.white)
                    .lineLimit(1)
            }
            .frame(width: 100)
            .padding(8)
            .background(isSelected ? TryZonTheme.primaryGold.opacity(0.18) : TryZonTheme.surfaceVariant)
            .cornerRadius(12)
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(isSelected ? TryZonTheme.primaryGold : TryZonTheme.primaryGold.opacity(0.3), lineWidth: isSelected ? 2 : 1)
            )
        }
    }

    // MARK: - Trust Badge
    private func trustBadge(icon: String, text: String) -> some View {
        VStack(spacing: 4) {
            Image(systemName: icon)
                .font(.system(size: 14))
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
        if viewModel.selectedGarmentImage == nil && !sampleOutfits.isEmpty {
            let firstOutfit = sampleOutfits[0]
            selectedOutfitUrl = firstOutfit.imageUrl
            loadSampleGarmentImage(urlStr: firstOutfit.imageUrl)
        }
        if viewModel.selectedPersonImage == nil {
            loadSamplePersonImage(urlStr: samplePersonModelUrl)
        }

        if !authViewModel.canExecuteTryOn() {
            if authViewModel.showLoginRequiredModal {
                showLoginRequiredModal = true
            } else if authViewModel.showChoicePopupModal {
                showChoicePopupModal = true
            }
            return
        }
        runActualTryOnSubmission()
    }

    private func runActualTryOnSubmission() {
        Task {
            await viewModel.startTryOn { sessionId in
                self.navPath = [.processing(sessionId)]
            }
        }
    }

    private func loadSampleGarmentImage(urlStr: String) {
        guard let url = URL(string: urlStr) else { return }
        Task {
            if let (data, _) = try? await URLSession.shared.data(from: url),
               let img = UIImage(data: data) {
                DispatchQueue.main.async {
                    self.viewModel.selectedGarmentImage = img
                }
            }
        }
    }

    private func loadSamplePersonImage(urlStr: String) {
        guard let url = URL(string: urlStr) else { return }
        Task {
            if let (data, _) = try? await URLSession.shared.data(from: url),
               let img = UIImage(data: data) {
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

