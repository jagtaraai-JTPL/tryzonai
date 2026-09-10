import SwiftUI
import UIKit

public struct TryOnResultView: View {
    @Environment(\.presentationMode) var presentationMode
    public let resultImageUrl: String
    public var productName: String = "Outfit"
    
    @State private var loadedImage: UIImage? = nil
    @State private var isLoading = true
    @State private var saveStatusMessage: String? = nil
    @State private var showingShareSheet = false

    @State private var showingReportAlert = false
    @State private var reportSuccessMessage: String? = nil

    public init(resultImageUrl: String, productName: String = "Outfit") {
        self.resultImageUrl = resultImageUrl
        self.productName = productName
    }

    public var body: some View {
        NavigationView {
            ScrollView {
                VStack(spacing: 16) {
                    ZStack {
                        if let image = loadedImage {
                            Image(uiImage: image)
                                .resizable()
                                .scaledToFit()
                                .cornerRadius(20)
                                .shadow(color: Color.black.opacity(0.3), radius: 10, x: 0, y: 5)
                                .padding(.horizontal)
                        } else if isLoading {
                            VStack(spacing: 12) {
                                ProgressView()
                                    .scaleEffect(1.5)
                                Text("Loading HD Try-On Result...")
                                    .font(.subheadline)
                                    .foregroundColor(.secondary)
                            }
                            .frame(height: 320)
                        } else {
                            VStack(spacing: 8) {
                                Image(systemName: "exclamationmark.triangle.fill")
                                    .font(.largeTitle)
                                    .foregroundColor(.orange)
                                Text("Failed to load result image")
                                    .font(.subheadline)
                                    .foregroundColor(.secondary)
                            }
                            .frame(height: 320)
                        }
                    }

                    if let message = saveStatusMessage ?? reportSuccessMessage {
                        Text(message)
                            .font(.caption.bold())
                            .foregroundColor(.yellow)
                            .transition(.opacity)
                    }

                    // 🛍️ STORE CHOICE CHIPS WITH OFFICIAL AFFILIATE TAGS
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Get This Look Real-Life Ready 🛍️")
                            .font(.system(size: 14, weight: .bold))
                        Text("AI Stylist: Select a store to check live stock & delivery")
                            .font(.system(size: 11))
                            .foregroundColor(.secondary)

                        HStack(spacing: 8) {
                            // Myntra
                            Link(destination: getStoreUrl(targetStore: "myntra")) {
                                Text("Myntra")
                                    .font(.system(size: 11, weight: .bold))
                                    .foregroundColor(.white)
                                    .frame(maxWidth: .infinity)
                                    .frame(height: 36)
                                    .background(Color(red: 1.0, green: 0.24, blue: 0.42))
                                    .cornerRadius(10)
                            }

                            // Ajio
                            Link(destination: getStoreUrl(targetStore: "ajio")) {
                                Text("AJIO")
                                    .font(.system(size: 11, weight: .bold))
                                    .foregroundColor(.white)
                                    .frame(maxWidth: .infinity)
                                    .frame(height: 36)
                                    .background(Color(red: 0.17, green: 0.25, blue: 0.32))
                                    .cornerRadius(10)
                            }

                            // Flipkart
                            Link(destination: getStoreUrl(targetStore: "flipkart")) {
                                Text("Flipkart")
                                    .font(.system(size: 11, weight: .bold))
                                    .foregroundColor(.white)
                                    .frame(maxWidth: .infinity)
                                    .frame(height: 36)
                                    .background(Color(red: 0.16, green: 0.45, blue: 0.94))
                                    .cornerRadius(10)
                            }

                            // Amazon
                            Link(destination: getStoreUrl(targetStore: "amazon")) {
                                Text("Amazon")
                                    .font(.system(size: 11, weight: .bold))
                                    .foregroundColor(.white)
                                    .frame(maxWidth: .infinity)
                                    .frame(height: 36)
                                    .background(Color(red: 1.0, green: 0.60, blue: 0.0))
                                    .cornerRadius(10)
                            }
                        }
                    }
                    .padding()
                    .background(Color(UIColor.secondarySystemBackground))
                    .cornerRadius(16)
                    .padding(.horizontal)

                    // Action Buttons Row (Save & Share)
                    HStack(spacing: 16) {
                        // Download to Photos
                        Button(action: saveImageToPhotos) {
                            HStack {
                                Image(systemName: "square.and.arrow.down.fill")
                                Text("Save HD")
                                    .fontWeight(.bold)
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 12)
                            .background(Color.yellow)
                            .foregroundColor(.black)
                            .cornerRadius(12)
                        }
                        .disabled(loadedImage == nil)

                        // Share Button
                        Button(action: { showingShareSheet = true }) {
                            HStack {
                                Image(systemName: "square.and.arrow.up")
                                Text("Share")
                                    .fontWeight(.bold)
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 12)
                            .background(Color(UIColor.systemGray5))
                            .foregroundColor(.primary)
                            .cornerRadius(12)
                        }
                        .disabled(loadedImage == nil)
                    }
                    .padding(.horizontal)

                    // Report AI Content Button (Mandatory Safety Policy)
                    Button(action: { showingReportAlert = true }) {
                        HStack {
                            Image(systemName: "flag.fill")
                                .foregroundColor(.red)
                            Text("Report Inappropriate Content 🚩")
                                .font(.caption.bold())
                                .foregroundColor(.red)
                        }
                        .padding(.vertical, 8)
                    }
                    .padding(.bottom, 20)
                }
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
            .alert(isPresented: $showingReportAlert) {
                Alert(
                    title: Text("Report Content 🚩"),
                    message: Text("Help keep TryZon AI safe. Report this AI generated image if it contains inappropriate or unsafe content."),
                    primaryButton: .destructive(Text("Submit Report")) {
                        submitReport()
                    },
                    secondaryButton: .cancel()
                )
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

    private func getStoreUrl(targetStore: String) -> URL {
        let encodedName = productName.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? "Outfit"
        let urlString: String
        switch targetStore.lowercased() {
        case "myntra":
            urlString = "https://www.myntra.com/search?rawQuery=\(encodedName)&subid=tryzonai"
        case "ajio":
            urlString = "https://www.ajio.com/search/?text=\(encodedName)&subid=tryzonai"
        case "flipkart":
            urlString = "https://www.flipkart.com/search?q=\(encodedName)&affid=tryzonai"
        case "amazon":
            urlString = "https://www.amazon.in/s?k=\(encodedName)&tag=tryzonai-21"
        default:
            urlString = "https://www.google.com/search?q=\(encodedName)"
        }
        return URL(string: urlString) ?? URL(string: "https://www.myntra.com")!
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

    private func submitReport() {
        guard let url = URL(string: "https://tryzonai.com/api/v1/report") else { return }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        let payload: [String: String] = [
            "content_type": "tryon_result",
            "image_url": resultImageUrl,
            "reason": "inappropriate_ai",
            "details": "User reported AI output via iOS App"
        ]

        request.httpBody = try? JSONSerialization.data(withJSONObject: payload)

        URLSession.shared.dataTask(with: request) { _, _, _ in
            DispatchQueue.main.async {
                withAnimation {
                    reportSuccessMessage = "Report Submitted. Thank you for keeping TryZon AI safe! 🚩"
                }
                DispatchQueue.main.asyncAfter(deadline: .now() + 4) {
                    reportSuccessMessage = nil
                }
            }
        }.resume()
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
