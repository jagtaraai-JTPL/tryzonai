import SwiftUI
import PhotosUI
import UIKit

public struct TryOnUploadView: View {
    @Environment(\.presentationMode) var presentationMode
    @StateObject private var apiClient = APIClient.shared

    @State private var selectedPersonItem: PhotosPickerItem? = nil
    @State private var selectedPersonImage: UIImage? = nil

    @State private var selectedGarmentItem: PhotosPickerItem? = nil
    @State private var selectedGarmentImage: UIImage? = nil

    @State private var selectedCategory: String = "Tops"
    private let categories = ["Tops", "Bottoms", "Dresses", "Outerwear"]

    @State private var isProcessing = false
    @State private var resultResponse: TryOnTaskResponse? = nil
    @State private var errorMessage: String? = nil

    public init() {}

    public var body: some View {
        NavigationView {
            ScrollView {
                VStack(spacing: 20) {
                    // Category Selector
                    Picker("Category", selection: $selectedCategory) {
                        ForEach(categories, id: \.self) { cat in
                            Text(cat).tag(cat)
                        }
                    }
                    .pickerStyle(SegmentedPickerStyle())
                    .padding(.horizontal)

                    // Image Pickers Row
                    HStack(spacing: 16) {
                        // Person Photo Picker
                        VStack(spacing: 8) {
                            Text("1. Person Photo")
                                .font(.caption.bold())
                                .foregroundColor(.secondary)

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
                                            .fill(Color(UIColor.secondarySystemBackground))
                                            .frame(width: 150, height: 200)

                                        VStack(spacing: 8) {
                                            Image(systemName: "person.crop.rectangle.badge.plus")
                                                .font(.title)
                                                .foregroundColor(.purple)
                                            Text("Upload Photo")
                                                .font(.caption.bold())
                                                .foregroundColor(.purple)
                                        }
                                    }
                                }
                            }
                            .onChange(of: selectedPersonItem) { _, newItem in
                                Task {
                                    if let data = try? await newItem?.loadTransferable(type: Data.self),
                                       let image = UIImage(data: data) {
                                        selectedPersonImage = image
                                    }
                                }
                            }
                        }

                        // Garment Photo Picker
                        VStack(spacing: 8) {
                            Text("2. Garment Image")
                                .font(.caption.bold())
                                .foregroundColor(.secondary)

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
                                            .fill(Color(UIColor.secondarySystemBackground))
                                            .frame(width: 150, height: 200)

                                        VStack(spacing: 8) {
                                            Image(systemName: "tshirt.fill")
                                                .font(.title)
                                                .foregroundColor(.purple)
                                            Text("Upload Garment")
                                                .font(.caption.bold())
                                                .foregroundColor(.purple)
                                        }
                                    }
                                }
                            }
                            .onChange(of: selectedGarmentItem) { _, newItem in
                                Task {
                                    if let data = try? await newItem?.loadTransferable(type: Data.self),
                                       let image = UIImage(data: data) {
                                        selectedGarmentImage = image
                                    }
                                }
                            }
                        }
                    }
                    .padding(.horizontal)

                    // Error Message Banner
                    if let err = errorMessage {
                        Text(err)
                            .font(.caption.bold())
                            .foregroundColor(.red)
                            .padding(.horizontal)
                    }

                    // Generate Button
                    Button(action: startTryOnProcess) {
                        HStack {
                            if isProcessing {
                                ProgressView()
                                    .progressViewStyle(CircularProgressViewStyle(tint: .black))
                                Text("Generating AI Try-On...")
                                    .fontWeight(.bold)
                            } else {
                                Image(systemName: "sparkles")
                                Text("Generate Try-On ⚡ (\(apiClient.userCredits) Credits Left)")
                                    .fontWeight(.bold)
                            }
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(
                            (selectedPersonImage != nil && selectedGarmentImage != nil && !isProcessing) ? Color.yellow : Color.gray.opacity(0.4)
                        )
                        .foregroundColor(.black)
                        .cornerRadius(14)
                    }
                    .disabled(selectedPersonImage == nil || selectedGarmentImage == nil || isProcessing)
                    .padding(.horizontal)
                }
                .padding(.top, 10)
            }
            .navigationTitle("Virtual Fitting Room")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Close") {
                        presentationMode.wrappedValue.dismiss()
                    }
                }
            }
            .sheet(item: Binding(
                get: {
                    if let res = resultResponse, res.status == "completed", let url = res.resultImageUrl {
                        return ResultSheetItem(id: res.id, url: url)
                    }
                    return nil
                },
                set: { _ in resultResponse = nil }
            )) { item in
                TryOnResultView(resultImageUrl: item.url)
            }
        }
    }

    private func startTryOnProcess() {
        guard let person = selectedPersonImage, let garment = selectedGarmentImage else { return }

        isProcessing = true
        errorMessage = nil

        Task {
            do {
                let response = try await apiClient.generateTryOn(personImage: person, garmentImage: garment, category: selectedCategory)
                
                // Poll until task finishes
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
