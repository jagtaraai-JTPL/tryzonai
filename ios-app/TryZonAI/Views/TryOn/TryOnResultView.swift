import SwiftUI
import UIKit

public struct TryOnResultView: View {
    @Environment(\.presentationMode) var presentationMode
    public let resultImageUrl: String
    
    @State private var loadedImage: UIImage? = nil
    @State private var isLoading = true
    @State private var saveStatusMessage: String? = nil
    @State private var showingShareSheet = false

    public init(resultImageUrl: String) {
        self.resultImageUrl = resultImageUrl
    }

    public var body: some View {
        NavigationView {
            VStack(spacing: 16) {
                ZStack {
                    if let image = loadedImage {
                        Image(uiImage: image)
                            .resizable()
                            .scaledToFit()
                            .cornerRadius(16)
                            .shadow(radius: 8)
                            .padding(.horizontal)
                    } else if isLoading {
                        VStack(spacing: 12) {
                            ProgressView()
                                .scaleEffect(1.5)
                            Text("Loading HD Try-On Result...")
                                .font(.subheadline)
                                .foregroundColor(.secondary)
                        }
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                    } else {
                        VStack(spacing: 8) {
                            Image(systemName: "exclamationmark.triangle.fill")
                                .font(.largeTitle)
                                .foregroundColor(.orange)
                            Text("Failed to load result image")
                                .font(.subheadline)
                                .foregroundColor(.secondary)
                        }
                    }
                }
                .frame(maxHeight: .infinity)

                if let message = saveStatusMessage {
                    Text(message)
                        .font(.caption.bold())
                        .foregroundColor(.purple)
                        .transition(.opacity)
                }

                // Action Buttons Row
                HStack(spacing: 16) {
                    // Download to Photos
                    Button(action: saveImageToPhotos) {
                        HStack {
                            Image(systemName: "square.and.arrow.down.fill")
                            Text("Save to Photos")
                                .fontWeight(.bold)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(Color.purple)
                        .foregroundColor(.white)
                        .cornerRadius(12)
                    }
                    .disabled(loadedImage == nil)

                    // Share Button
                    Button(action: { showingShareSheet = true }) {
                        HStack {
                            Image(systemName: "square.and.arrow.up")
                            Text("Share Outfit")
                                .fontWeight(.bold)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(Color.yellow)
                        .foregroundColor(.black)
                        .cornerRadius(12)
                    }
                    .disabled(loadedImage == nil)
                }
                .padding(.horizontal)
                .padding(.bottom, 16)
            }
            .navigationTitle("Your AI Fitting Result ✨")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") {
                        presentationMode.wrappedValue.dismiss()
                    }
                }
            }
            .onAppear {
                loadImageData()
            }
            .sheet(isPresented: $showingShareSheet) {
                if let image = loadedImage {
                    ActivityView(activityItems: [image, "Check out my new virtual try-on outfit created with TryZon AI! 👗✨"])
                }
            }
        }
    }

    private func loadImageData() {
        guard let url = URL(string: resultImageUrl) else {
            isLoading = false
            return
        }

        URLSession.shared.dataTask(with: url) { data, response, error in
            DispatchQueue.main.async {
                self.isLoading = false
                if let data = data, let image = UIImage(data: data) {
                    self.loadedImage = image
                }
            }
        }.resume()
    }

    private func saveImageToPhotos() {
        guard let image = loadedImage else { return }
        UIImageWriteToSavedPhotosAlbum(image, nil, nil, nil)
        
        withAnimation {
            saveStatusMessage = "Saved to Photos Gallery! 📸"
        }
        
        DispatchQueue.main.asyncAfter(deadline: .now() + 3) {
            saveStatusMessage = nil
        }
    }
}

// UIActivityViewController wrapper for native iOS Share Sheet
struct ActivityView: UIViewControllerRepresentable {
    var activityItems: [Any]
    var applicationActivities: [UIActivity]? = nil

    func makeUIViewController(context: Context) -> UIActivityViewController {
        let controller = UIActivityViewController(activityItems: activityItems, applicationActivities: applicationActivities)
        return controller
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}
