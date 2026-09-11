import SwiftUI
import PhotosUI
import UIKit

public struct TryOnUploadView: View {
    @ObservedObject var apiClient: APIClient
    @StateObject private var viewModel = TryOnViewModel()
    @ObservedObject private var authViewModel = AuthViewModel.shared

    let onNavigateToResult: (TryOnStatusResponse) -> Void

    @State private var selectedPersonItem: PhotosPickerItem? = nil
    @State private var selectedGarmentItem: PhotosPickerItem? = nil

    @State private var showingCameraForPerson = false
    @State private var showingCameraForGarment = false

    @State private var activeProcessingSessionId: Int? = nil
    @State private var activeResultStatus: TryOnStatusResponse? = nil

    @State private var showLoginRequiredModal = false
    @State private var showChoicePopupModal = false
    @State private var showPremiumModal = false

    private let categories = ["Tops", "Bottoms", "Dresses", "Suits", "Outerwear"]

    private let sampleOutfits = [
        SampleOutfit(name: "Monaco Yacht", badge: "👑 OLD MONEY", category: "Tops", color: Color.blue),
        SampleOutfit(name: "NYC Suit", badge: "💼 EXECUTIVE", category: "Suits", color: Color.gray),
        SampleOutfit(name: "Tokyo Cyber", badge: "⚡ NEON AI", category: "Tops", color: Color.purple),
        SampleOutfit(name: "Seoul Black", badge: "🫰 K-STYLE", category: "Suits", color: Color.black)
    ]

    public init(apiClient: APIClient, onNavigateToResult: @escaping (TryOnStatusResponse) -> Void) {
        self.apiClient = apiClient
        self.onNavigateToResult = onNavigateToResult
    }

    public var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                // Header Banner
                VStack(spacing: 4) {
                    Text("TRY-ON FITTING ROOM ⚡")
                        .font(.system(size: 18, weight: .black, design: .rounded))
                        .foregroundColor(TryZonTheme.primaryGold)

                    Text("Upload your photo & garment to generate a 100% realistic AI outfit fit")
                        .font(.system(size: 11, weight: .medium))
                        .foregroundColor(.white.opacity(0.7))
                        .multilineTextAlignment(.center)
                }
                .padding(.top, 10)

                // Category Selector Tabs
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

                // Photos Picker Row (Person vs Garment)
                HStack(spacing: 16) {
                    // Person Photo Card
                    VStack(spacing: 8) {
                        Text("1. Your Photo")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(TryZonTheme.primaryGold)

                        ZStack {
                            if let img = viewModel.selectedPersonImage {
                                Image(uiImage: img)
                                    .resizable()
                                    .scaledToFill()
                                    .frame(width: 155, height: 210)
                                    .cornerRadius(16)
                                    .clipped()
                                    .overlay(
                                        Button(action: { viewModel.selectedPersonImage = nil }) {
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

                                VStack(spacing: 10) {
                                    PhotosPicker(selection: $selectedPersonItem, matching: .images) {
                                        VStack(spacing: 4) {
                                            Image(systemName: "photo.badge.plus")
                                                .font(.system(size: 26))
                                                .foregroundColor(TryZonTheme.primaryGold)
                                            Text("Choose Gallery")
                                                .font(.system(size: 11, weight: .bold))
                                                .foregroundColor(TryZonTheme.primaryGold)
                                        }
                                        .padding(10)
                                        .background(TryZonTheme.darkSurface)
                                        .cornerRadius(12)
                                    }

                                    Button(action: { showingCameraForPerson = true }) {
                                        HStack(spacing: 4) {
                                            Image(systemName: "camera.fill")
                                                .font(.caption)
                                            Text("Take Camera")
                                                .font(.system(size: 10, weight: .bold))
                                        }
                                        .foregroundColor(.white)
                                        .padding(.horizontal, 10)
                                        .padding(.vertical, 6)
                                        .background(Color.white.opacity(0.15))
                                        .cornerRadius(8)
                                    }
                                }
                            }
                        }
                    }

                    // Garment Photo Card
                    VStack(spacing: 8) {
                        Text("2. Garment Image")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(TryZonTheme.primaryGold)

                        ZStack {
                            if let img = viewModel.selectedGarmentImage {
                                Image(uiImage: img)
                                    .resizable()
                                    .scaledToFill()
                                    .frame(width: 155, height: 210)
                                    .cornerRadius(16)
                                    .clipped()
                                    .overlay(
                                        Button(action: { viewModel.selectedGarmentImage = nil }) {
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

                                VStack(spacing: 10) {
                                    PhotosPicker(selection: $selectedGarmentItem, matching: .images) {
                                        VStack(spacing: 4) {
                                            Image(systemName: "tshirt.fill")
                                                .font(.system(size: 26))
                                                .foregroundColor(TryZonTheme.primaryGold)
                                            Text("Choose Garment")
                                                .font(.system(size: 11, weight: .bold))
                                                .foregroundColor(TryZonTheme.primaryGold)
                                        }
                                        .padding(10)
                                        .background(TryZonTheme.darkSurface)
                                        .cornerRadius(12)
                                    }

                                    Button(action: { showingCameraForGarment = true }) {
                                        HStack(spacing: 4) {
                                            Image(systemName: "camera.fill")
                                                .font(.caption)
                                            Text("Take Camera")
                                                .font(.system(size: 10, weight: .bold))
                                        }
                                        .foregroundColor(.white)
                                        .padding(.horizontal, 10)
                                        .padding(.vertical, 6)
                                        .background(Color.white.opacity(0.15))
                                        .cornerRadius(8)
                                    }
                                }
                            }
                        }
                    }
                }
                .padding(.horizontal, 16)
                .onChange(of: selectedPersonItem) { newItem in
                    Task {
                        if let data = try? await newItem?.loadTransferable(type: Data.self),
                           let image = UIImage(data: data) {
                            viewModel.selectedPersonImage = image
                        }
                    }
                }
                .onChange(of: selectedGarmentItem) { newItem in
                    Task {
                        if let data = try? await newItem?.loadTransferable(type: Data.self),
                           let image = UIImage(data: data) {
                            viewModel.selectedGarmentImage = image
                        }
                    }
                }

                // Sample Preset Outfits Bar
                VStack(alignment: .leading, spacing: 8) {
                    Text("Or Select Sample Preset Outfit")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(.white.opacity(0.6))
                        .padding(.horizontal, 16)

                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 12) {
                            ForEach(sampleOutfits) { outfit in
                                Button(action: {
                                    viewModel.selectedGarmentImage = createSampleGarmentImage(for: outfit)
                                    viewModel.selectedCategory = outfit.category
                                }) {
                                    VStack(alignment: .leading, spacing: 4) {
                                        Text(outfit.badge)
                                            .font(.system(size: 9, weight: .bold))
                                            .padding(.horizontal, 6)
                                            .padding(.vertical, 2)
                                            .background(TryZonTheme.primaryGold)
                                            .foregroundColor(.black)
                                            .cornerRadius(4)

                                        Text(outfit.name)
                                            .font(.system(size: 12, weight: .bold))
                                            .foregroundColor(.white)
                                    }
                                    .padding(10)
                                    .frame(width: 120, height: 60)
                                    .background(outfit.color.opacity(0.4))
                                    .cornerRadius(12)
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 12)
                                            .stroke(TryZonTheme.primaryGold.opacity(0.4), lineWidth: 1)
                                    )
                                }
                            }
                        }
                        .padding(.horizontal, 16)
                    }
                }

                // Store URL Import Field
                VStack(alignment: .leading, spacing: 6) {
                    Text("Or Import Product Link (Myntra / Ajio / Amazon)")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(.white.opacity(0.6))

                    HStack {
                        Image(systemName: "link")
                            .foregroundColor(TryZonTheme.primaryGold)
                        TextField("https://myntra.com/product/...", text: $viewModel.garmentStoreUrl)
                            .font(.system(size: 12))
                            .foregroundColor(.white)
                    }
                    .padding(12)
                    .background(TryZonTheme.surfaceVariant)
                    .cornerRadius(12)
                }
                .padding(.horizontal, 16)

                // Error Message Display
                if let err = viewModel.errorMessage {
                    Text(err)
                        .font(.caption.bold())
                        .foregroundColor(.red)
                        .padding(.horizontal, 16)
                }

                // Generate CTA Shimmer Button
                ShimmeringGoldButton(
                    title: viewModel.isProcessing ? "PROCESSING TRY-ON..." : "GENERATE VIRTUAL TRY-ON ⚡",
                    subtitle: "100% Realistic Fit • Fast GPU Queue",
                    isEnabled: (viewModel.selectedPersonImage != nil && viewModel.selectedGarmentImage != nil && !viewModel.isProcessing)
                ) {
                    executeTryOnFlow()
                }
                .padding(.horizontal, 16)
            }
            .padding(.bottom, 30)
        }
        .background(TryZonTheme.darkBackground)

        // Sheet: TryOn Processing
        .sheet(item: Binding<ProcessingSessionItem?>(
            get: { activeProcessingSessionId != nil ? ProcessingSessionItem(id: activeProcessingSessionId!) : nil },
            set: { _ in activeProcessingSessionId = nil }
        )) { item in
            TryOnProcessingView(
                sessionId: item.id,
                apiClient: apiClient,
                onCompleted: { statusRes in
                    activeProcessingSessionId = nil
                    activeResultStatus = statusRes
                },
                onCancel: {
                    activeProcessingSessionId = nil
                    viewModel.isProcessing = false
                }
            )
        }

        // Sheet: TryOn Result
        .sheet(item: Binding<ResultStatusItem?>(
            get: { activeResultStatus != nil ? ResultStatusItem(status: activeResultStatus!) : nil },
            set: { _ in activeResultStatus = nil }
        )) { item in
            TryOnResultView(
                resultImageUrl: item.status.fullResultURL?.absoluteString ?? "",
                originalPhotoUrl: item.status.fullOriginalURL?.absoluteString,
                onTryAnother: {
                    activeResultStatus = nil
                    viewModel.reset()
                }
            )
        }

        // Dialog: Mandatory Login Required
        .overlay(
            Group {
                if showLoginRequiredModal {
                    LoginRequiredDialog(isPresented: $showLoginRequiredModal, onNavigateToLogin: {
                        // Trigger Auth Sheet or Tab
                    })
                }
            }
        )

        // Dialog: Smart Choice Popup
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

        runActualTryOnSubmission()
    }

    private func runActualTryOnSubmission() {
        Task {
            await viewModel.startTryOn { sessionId in
                self.activeProcessingSessionId = sessionId
            }
        }
    }

    private func createSampleGarmentImage(for outfit: SampleOutfit) -> UIImage {
        let size = CGSize(width: 400, height: 600)
        UIGraphicsBeginImageContextWithOptions(size, true, 0)
        let context = UIGraphicsGetCurrentContext()!

        context.setFillColor(UIColor(outfit.color).cgColor)
        context.fill(CGRect(origin: .zero, size: size))

        let font = UIFont.systemFont(ofSize: 28, weight: .bold)
        let attrs: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: UIColor.white]
        let string = NSString(string: outfit.name)
        let textSize = string.size(withAttributes: attrs)

        string.draw(at: CGPoint(x: (size.width - textSize.width)/2, y: (size.height - textSize.height)/2), withAttributes: attrs)

        let image = UIGraphicsGetImageFromCurrentImageContext()!
        UIGraphicsEndImageContext()
        return image
    }
}

struct SampleOutfit: Identifiable {
    let id = UUID().uuidString
    let name: String
    let badge: String
    let category: String
    let color: Color
}

struct ProcessingSessionItem: Identifiable {
    let id: Int
}

struct ResultStatusItem: Identifiable {
    let id = UUID().uuidString
    let status: TryOnStatusResponse
}
