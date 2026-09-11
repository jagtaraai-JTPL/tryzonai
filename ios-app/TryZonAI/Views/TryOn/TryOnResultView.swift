import SwiftUI
import UIKit

public struct TryOnResultView: View {
    let resultImageUrl: String
    let highresUrl: String?
    let originalPhotoUrl: String?
    let onTryAnother: () -> Void

    @State private var sliderOffset: CGFloat = 0.5
    @State private var showHeartAnimation = false
    @State private var heartPosition: CGPoint = .zero
    @State private var isSavedToWardrobe = false
    @State private var showToastMessage: String? = nil
    @State private var isDownloading = false
    @State private var showShareSheet = false
    @State private var sharedItems: [Any] = []

    public init(
        resultImageUrl: String,
        highresUrl: String? = nil,
        originalPhotoUrl: String? = nil,
        onTryAnother: @escaping () -> Void
    ) {
        self.resultImageUrl = resultImageUrl
        self.highresUrl = highresUrl
        self.originalPhotoUrl = originalPhotoUrl
        self.onTryAnother = onTryAnother
    }

    private var displayUrl: String {
        resultImageUrl
    }

    public var body: some View {
        ZStack {
            TryZonTheme.darkBackground.ignoresSafeArea()

            VStack(spacing: 0) {
                // Top Header Bar
                HStack {
                    Button(action: onTryAnother) {
                        HStack(spacing: 6) {
                            Image(systemName: "chevron.left")
                            Text("Try Another")
                        }
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(TryZonTheme.primaryGold)
                    }

                    Spacer()

                    VStack(spacing: 2) {
                        Text("FITTING RESULT 👑")
                            .font(.system(size: 13, weight: .black, design: .rounded))
                            .foregroundColor(.white)
                        Text("AI Virtual Try-On")
                            .font(.system(size: 10))
                            .foregroundColor(.white.opacity(0.5))
                    }

                    Spacer()

                    Button(action: shareResultImage) {
                        Image(systemName: "square.and.arrow.up")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(TryZonTheme.primaryGold)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 16)
                .padding(.bottom, 12)

                // Result Banner Chip
                HStack(spacing: 6) {
                    Image(systemName: "checkmark.seal.fill")
                        .foregroundColor(.green)
                        .font(.system(size: 12))
                    Text("AI Generation Complete • Swipe to Compare")
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundColor(.white.opacity(0.85))
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 7)
                .background(Color.green.opacity(0.15))
                .cornerRadius(20)
                .overlay(
                    RoundedRectangle(cornerRadius: 20)
                        .stroke(Color.green.opacity(0.4), lineWidth: 1)
                )
                .padding(.bottom, 10)

                // Interactive Split-Screen Comparison Viewport
                GeometryReader { geo in
                    ZStack {
                        // Background Layer: AI Result Image (always visible)
                        AsyncImage(url: URL(string: displayUrl)) { phase in
                            switch phase {
                            case .success(let img):
                                img.resizable().scaledToFill()
                            case .failure:
                                VStack(spacing: 8) {
                                    Image(systemName: "exclamationmark.triangle")
                                        .foregroundColor(.red)
                                    Text("Failed to load result")
                                        .font(.caption)
                                        .foregroundColor(.white.opacity(0.5))
                                }
                            case .empty:
                                VStack(spacing: 12) {
                                    ProgressView()
                                        .tint(TryZonTheme.primaryGold)
                                    Text("Loading HD Result...")
                                        .font(.caption)
                                        .foregroundColor(.white.opacity(0.7))
                                }
                            @unknown default:
                                EmptyView()
                            }
                        }
                        .frame(width: geo.size.width, height: geo.size.height)
                        .clipped()

                        // Foreground: Original Photo with sliding mask
                        if let origUrl = originalPhotoUrl, let url = URL(string: origUrl) {
                            AsyncImage(url: url) { img in
                                img.resizable().scaledToFill()
                            } placeholder: {
                                TryZonTheme.darkSurface
                            }
                            .frame(width: geo.size.width, height: geo.size.height)
                            .mask(
                                HStack(spacing: 0) {
                                    Rectangle()
                                        .frame(width: geo.size.width * sliderOffset)
                                    Spacer(minLength: 0)
                                }
                            )
                            .clipped()

                            // Labels
                            HStack {
                                Text("BEFORE")
                                    .font(.system(size: 9, weight: .black))
                                    .foregroundColor(.white)
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 4)
                                    .background(Color.black.opacity(0.6))
                                    .cornerRadius(6)
                                    .padding(12)
                                    .opacity(sliderOffset > 0.15 ? 1 : 0)
                                Spacer()
                                Text("AFTER ✨")
                                    .font(.system(size: 9, weight: .black))
                                    .foregroundColor(TryZonTheme.primaryGold)
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 4)
                                    .background(Color.black.opacity(0.6))
                                    .cornerRadius(6)
                                    .padding(12)
                                    .opacity(sliderOffset < 0.85 ? 1 : 0)
                            }
                            .frame(maxHeight: .infinity, alignment: .bottom)

                            // Drag Handle
                            Rectangle()
                                .fill(TryZonTheme.primaryGold)
                                .frame(width: 2)
                                .offset(x: (geo.size.width * sliderOffset) - (geo.size.width / 2))
                                .overlay(
                                    ZStack {
                                        Circle()
                                            .fill(TryZonTheme.primaryGold)
                                            .frame(width: 40, height: 40)
                                            .shadow(color: .black.opacity(0.6), radius: 6)
                                        HStack(spacing: 2) {
                                            Image(systemName: "chevron.left")
                                            Image(systemName: "chevron.right")
                                        }
                                        .font(.system(size: 10, weight: .black))
                                        .foregroundColor(.black)
                                    }
                                    .offset(x: (geo.size.width * sliderOffset) - (geo.size.width / 2))
                                )
                                .gesture(
                                    DragGesture()
                                        .onChanged { value in
                                            let newOffset = value.location.x / geo.size.width
                                            sliderOffset = min(max(newOffset, 0.05), 0.95)
                                        }
                                )
                        }

                        // Double-tap Heart Animation
                        if showHeartAnimation {
                            Image(systemName: "heart.fill")
                                .font(.system(size: 80))
                                .foregroundColor(.red)
                                .shadow(color: .black.opacity(0.5), radius: 10)
                                .position(heartPosition)
                                .transition(.scale.combined(with: .opacity))
                        }
                    }
                    .contentShape(Rectangle())
                    .onTapGesture(count: 2) { location in
                        heartPosition = location
                        withAnimation(.spring(response: 0.3, dampingFraction: 0.5)) {
                            showHeartAnimation = true
                        }
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.8) {
                            withAnimation { showHeartAnimation = false }
                        }
                    }
                }
                .frame(maxHeight: .infinity)
                .cornerRadius(20)
                .padding(.horizontal, 12)

                // Action Buttons
                HStack(spacing: 10) {
                    // Save / Heart
                    Button(action: {
                        withAnimation(.spring()) { isSavedToWardrobe.toggle() }
                        showToast(isSavedToWardrobe ? "❤️ Saved to Wardrobe!" : "Removed from Wardrobe")
                    }) {
                        VStack(spacing: 3) {
                            Image(systemName: isSavedToWardrobe ? "heart.fill" : "heart")
                                .font(.system(size: 18))
                                .foregroundColor(isSavedToWardrobe ? .red : .white)
                                .scaleEffect(isSavedToWardrobe ? 1.2 : 1.0)
                            Text("Save")
                                .font(.system(size: 9, weight: .bold))
                                .foregroundColor(.white.opacity(0.7))
                        }
                        .padding(.vertical, 10)
                        .padding(.horizontal, 14)
                        .background(TryZonTheme.surfaceVariant)
                        .cornerRadius(14)
                    }

                    // Download HD
                    Button(action: downloadHDImage) {
                        HStack(spacing: 6) {
                            if isDownloading {
                                ProgressView()
                                    .tint(.black)
                                    .scaleEffect(0.8)
                            } else {
                                Image(systemName: "arrow.down.circle.fill")
                            }
                            Text(isDownloading ? "Saving..." : "Download HD")
                                .font(.system(size: 13, weight: .bold))
                        }
                        .foregroundColor(.black)
                        .padding(.vertical, 12)
                        .frame(maxWidth: .infinity)
                        .background(TryZonTheme.primaryGold)
                        .cornerRadius(14)
                    }
                    .disabled(isDownloading)

                    // Share
                    Button(action: shareResultImage) {
                        VStack(spacing: 3) {
                            Image(systemName: "square.and.arrow.up")
                                .font(.system(size: 18))
                                .foregroundColor(.white)
                            Text("Share")
                                .font(.system(size: 9, weight: .bold))
                                .foregroundColor(.white.opacity(0.7))
                        }
                        .padding(.vertical, 10)
                        .padding(.horizontal, 14)
                        .background(TryZonTheme.surfaceVariant)
                        .cornerRadius(14)
                    }
                }
                .padding(.horizontal, 12)
                .padding(.top, 12)

                // Buy Outfit CTA
                Button(action: {
                    if let url = URL(string: "https://myntra.com") {
                        UIApplication.shared.open(url)
                    }
                }) {
                    HStack(spacing: 6) {
                        Image(systemName: "bag.fill")
                        Text("Shop This Look on Myntra / Ajio")
                            .font(.system(size: 13, weight: .bold))
                    }
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(
                        LinearGradient(
                            colors: [Color.pink.opacity(0.8), Color.purple.opacity(0.7)],
                            startPoint: .leading,
                            endPoint: .trailing
                        )
                    )
                    .cornerRadius(14)
                }
                .padding(.horizontal, 16)
                .padding(.top, 8)
                .padding(.bottom, 24)
            }

            // Toast Overlay
            if let toast = showToastMessage {
                VStack {
                    Spacer()
                    Text(toast)
                        .font(.system(size: 13, weight: .bold))
                        .padding(.horizontal, 20)
                        .padding(.vertical, 12)
                        .background(Color.black.opacity(0.88))
                        .foregroundColor(TryZonTheme.primaryGold)
                        .cornerRadius(24)
                        .shadow(color: .black.opacity(0.4), radius: 8)
                        .padding(.bottom, 100)
                }
                .transition(.move(edge: .bottom).combined(with: .opacity))
                .zIndex(10)
            }
        }
        .sheet(isPresented: $showShareSheet) {
            ActivityViewController(activityItems: sharedItems)
        }
    }

    private func downloadHDImage() {
        let urlToDownload = highresUrl ?? resultImageUrl
        guard let url = URL(string: urlToDownload) else {
            showToast("Invalid image URL")
            return
        }
        isDownloading = true
        Task {
            do {
                let (data, _) = try await URLSession.shared.data(from: url)
                if let image = UIImage(data: data) {
                    UIImageWriteToSavedPhotosAlbum(image, nil, nil, nil)
                    DispatchQueue.main.async {
                        isDownloading = false
                        showToast("📸 HD Photo Saved to Camera Roll!")
                    }
                }
            } catch {
                DispatchQueue.main.async {
                    isDownloading = false
                    showToast("Download failed — please retry")
                }
            }
        }
    }

    private func shareResultImage() {
        guard let url = URL(string: highresUrl ?? resultImageUrl) else { return }
        Task {
            if let (data, _) = try? await URLSession.shared.data(from: url),
               let image = UIImage(data: data) {
                DispatchQueue.main.async {
                    sharedItems = [image, "Check out my AI virtual try-on! 🤩 #TryZonAI"]
                    showShareSheet = true
                }
            } else {
                DispatchQueue.main.async {
                    sharedItems = [url]
                    showShareSheet = true
                }
            }
        }
    }

    private func showToast(_ msg: String) {
        withAnimation { showToastMessage = msg }
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.5) {
            withAnimation { showToastMessage = nil }
        }
    }
}

// MARK: - UIKit Share Sheet Wrapper
struct ActivityViewController: UIViewControllerRepresentable {
    let activityItems: [Any]

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: activityItems, applicationActivities: nil)
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}
