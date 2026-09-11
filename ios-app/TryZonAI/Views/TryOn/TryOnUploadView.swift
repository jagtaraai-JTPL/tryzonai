import SwiftUI
import PhotosUI
import UIKit

public struct TryOnUploadView: View {
    @ObservedObject var apiClient: APIClient
    let onNavigateToResult: (TryOnTaskResponse) -> Void

    @State private var selectedPersonItem: PhotosPickerItem? = nil
    @State private var selectedPersonImage: UIImage? = nil
    @State private var selectedGarmentItem: PhotosPickerItem? = nil
    @State private var selectedGarmentImage: UIImage? = nil

    @State private var selectedCategory: String = "Tops"
    @State private var garmentStoreUrl: String = ""
    @State private var isProcessing = false
    @State private var resultResponse: TryOnTaskResponse? = nil
    @State private var errorMessage: String? = nil
    @State private var showLoginRequired: Bool = false

    private let categories = ["Tops", "Bottoms", "Dresses", "Outerwear"]

    public init(apiClient: APIClient, onNavigateToResult: @escaping (TryOnTaskResponse) -> Void) {
        self.apiClient = apiClient
        self.onNavigateToResult = onNavigateToResult
    }

    public var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                // Category Selector Tabs
                Picker("Category", selection: $selectedCategory) {
                    ForEach(categories, id: \.self) { cat in
                        Text(cat).tag(cat)
                    }
                }
                .pickerStyle(SegmentedPickerStyle())
                .padding(.horizontal, 16)

                // Photos Picker Row
                HStack(spacing: 16) {
                    // Person Photo Section
                    VStack(spacing: 8) {
                        Text("1. Person Photo")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(TryZonTheme.primaryGold)

                        PhotosPicker(selection: $selectedPersonItem, matching: .images) {
                            ZStack {
                                if let image = selectedPersonImage {
                                    Image(uiImage: image)
                                        .resizable()
                                        .scaledToFill()
                                        .frame(width: 150, height: 200)
                                        .cornerRadius(16)
                                        .clipped()
                                } else {
                                    RoundedRectangle(cornerRadius: 16)
                                        .fill(TryZonTheme.surfaceVariant)
                                        .frame(width: 150, height: 200)

                                    VStack(spacing: 8) {
                                        Image(systemName: "person.crop.rectangle.badge.plus")
                                            .font(.title)
                                            .foregroundColor(TryZonTheme.primaryGold)
                                        Text("Upload Photo")
                                            .font(.caption.bold())
                                            .foregroundColor(TryZonTheme.primaryGold)
                                    }
                                }
                            }
                        }
                        .onChange(of: selectedPersonItem) { newItem in
                            Task {
                                if let data = try? await newItem?.loadTransferable(type: Data.self),
                                   let image = UIImage(data: data) {
                                    selectedPersonImage = image
                                }
                            }
                        }
                    }

                    // Garment Photo Section
                    VStack(spacing: 8) {
                        Text("2. Garment Image")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(TryZonTheme.primaryGold)

                        PhotosPicker(selection: $selectedGarmentItem, matching: .images) {
                            ZStack {
                                if let image = selectedGarmentImage {
                                    Image(uiImage: image)
                                        .resizable()
                                        .scaledToFill()
                                        .frame(width: 150, height: 200)
                                        .cornerRadius(16)
                                        .clipped()
                                } else {
                                    RoundedRectangle(cornerRadius: 16)
                                        .fill(TryZonTheme.surfaceVariant)
                                        .frame(width: 150, height: 200)

                                    VStack(spacing: 8) {
                                        Image(systemName: "tshirt.fill")
                                            .font(.title)
                                            .foregroundColor(TryZonTheme.primaryGold)
                                        Text("Upload Garment")
                                            .font(.caption.bold())
                                            .foregroundColor(TryZonTheme.primaryGold)
                                    }
                                }
                            }
                        }
                        .onChange(of: selectedGarmentItem) { newItem in
                            Task {
                                if let data = try? await newItem?.loadTransferable(type: Data.self),
                                   let image = UIImage(data: data) {
                                    selectedGarmentImage = image
                                }
                            }
                        }
                    }
                }
                .padding(.horizontal, 16)

                // Garment Store Link Input
                VStack(alignment: .leading, spacing: 6) {
                    Text("Or Paste Store Product Link (Myntra / Ajio / Amazon)")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(.white.opacity(0.6))

                    HStack {
                        Image(systemName: "link")
                            .foregroundColor(TryZonTheme.primaryGold)
                        TextField("https://myntra.com/product/...", text: $garmentStoreUrl)
                            .font(.system(size: 13))
                            .foregroundColor(.white)
                    }
                    .padding(12)
                    .background(TryZonTheme.surfaceVariant)
                    .cornerRadius(12)
                }
                .padding(.horizontal, 16)

                // Error Message
                if let err = errorMessage {
                    Text(err)
                        .font(.caption.bold())
                        .foregroundColor(.red)
                        .padding(.horizontal, 16)
                }

                // Shimmering Gold Generate Button
                ShimmeringGoldButton(
                    title: isProcessing ? "AI NEURAL FITTING IN PROGRESS..." : "GENERATE VIRTUAL TRY-ON ⚡",
                    subtitle: "100% Realistic Fit • Fast GPU Queue",
                    isEnabled: (selectedPersonImage != nil && selectedGarmentImage != nil && !isProcessing)
                ) {
                    startTryOnProcess()
                }
                .padding(.horizontal, 16)
            }
            .padding(.top, 10)
            .padding(.bottom, 30)
        }
        .background(TryZonTheme.darkBackground)
        .sheet(item: Binding<ResultSheetItem?>(
            get: {
                if let res = resultResponse, res.status == "completed", let url = res.resultImageUrl {
                    return ResultSheetItem(id: res.id, url: url)
                }
                return nil
            },
            set: { _ in resultResponse = nil }
        )) { item in
            TryOnResultView(resultImageUrl: item.url, onTryAnother: { resultResponse = nil })
        }
    }

    private func startTryOnProcess() {
        guard let person = selectedPersonImage, let garment = selectedGarmentImage else { return }

        isProcessing = true
        errorMessage = nil

        Task {
            do {
                let response = try await apiClient.generateTryOn(personImage: person, garmentImage: garment, category: selectedCategory)
                var currentStatus = response
                var tries = 0
                while currentStatus.status == "pending" || currentStatus.status == "processing" {
                    if tries > 30 { break }
                    try await Task.sleep(nanoseconds: 2_000_000_000)
                    currentStatus = try await apiClient.pollTaskStatus(taskId: response.id)
                    tries += 1
                }

                DispatchQueue.main.async {
                    self.isProcessing = false
                    self.resultResponse = currentStatus
                    if currentStatus.status != "completed" {
                        self.errorMessage = currentStatus.errorMessage ?? "Failed to process AI Try-On."
                    }
                }
            } catch {
                DispatchQueue.main.async {
                    self.isProcessing = false
                    self.errorMessage = error.localizedDescription
                }
            }
        }
    }
}

struct ResultSheetItem: Identifiable {
    let id: String
    let url: String
}
