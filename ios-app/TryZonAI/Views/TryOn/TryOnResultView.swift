import SwiftUI
import UIKit

public struct TryOnResultView: View {
    let resultImageUrl: String
    let originalPhotoUrl: String?
    let onTryAnother: () -> Void

    @State private var sliderOffset: CGFloat = 0.5
    @State private var showHeartAnimation = false
    @State private var heartPosition: CGPoint = .zero
    @State private var isSavedToWardrobe = false
    @State private var showToastMessage: String? = nil

    public init(resultImageUrl: String, originalPhotoUrl: String? = nil, onTryAnother: @escaping () -> Void) {
        self.resultImageUrl = resultImageUrl
        self.originalPhotoUrl = originalPhotoUrl
        self.onTryAnother = onTryAnother
    }

    public var body: some View {
        ZStack {
            TryZonTheme.darkBackground.ignoresSafeArea()

            VStack(spacing: 16) {
                // Top Header Bar
                HStack {
                    Button(action: onTryAnother) {
                        HStack(spacing: 4) {
                            Image(systemName: "chevron.left")
                            Text("Try Another")
                        }
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(TryZonTheme.primaryGold)
                    }

                    Spacer()

                    Text("FITTING RESULT 👑")
                        .font(.system(size: 14, weight: .black, design: .rounded))
                        .foregroundColor(.white)

                    Spacer()

                    Button(action: shareResultImage) {
                        Image(systemName: "square.and.arrow.up")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(TryZonTheme.primaryGold)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 12)

                // Interactive Split-Screen Comparison Viewport
                GeometryReader { geo in
                    ZStack {
                        // Background Layer: AI Result Image
                        AsyncImage(url: URL(string: resultImageUrl)) { img in
                            img.resizable().scaledToFill()
                        } placeholder: {
                            TryZonTheme.surfaceVariant
                        }
                        .frame(width: geo.size.width, height: geo.size.height)
                        .clipped()

                        // Foreground Clipped Layer: Original Photo (if present)
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

                            // Vertical Drag Handle Divider Bar
                            Rectangle()
                                .fill(TryZonTheme.primaryGold)
                                .frame(width: 3)
                                .offset(x: (geo.size.width * sliderOffset) - (geo.size.width / 2))
                                .overlay(
                                    ZStack {
                                        Circle()
                                            .fill(TryZonTheme.primaryGold)
                                            .frame(width: 36, height: 36)
                                            .shadow(color: .black.opacity(0.5), radius: 4)

                                        HStack(spacing: 2) {
                                            Image(systemName: "chevron.left")
                                            Image(systemName: "chevron.right")
                                        }
                                        .font(.system(size: 10, weight: .bold))
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

                        // Floating Heart Animation overlay on Double-Tap
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
                .padding(.horizontal, 16)

                // Action Buttons Bar
                HStack(spacing: 12) {
                    // Save to Wardrobe Button
                    Button(action: {
                        isSavedToWardrobe.toggle()
                        showToast("Saved to Wardrobe Closet!")
                    }) {
                        HStack(spacing: 6) {
                            Image(systemName: isSavedToWardrobe ? "heart.fill" : "heart")
                                .foregroundColor(isSavedToWardrobe ? .red : TryZonTheme.primaryGold)
                            Text(isSavedToWardrobe ? "Saved" : "Save")
                                .font(.system(size: 12, weight: .bold))
                        }
                        .padding(.vertical, 10)
                        .padding(.horizontal, 14)
                        .background(TryZonTheme.surfaceVariant)
                        .cornerRadius(12)
                    }

                    // Download HD Button
                    Button(action: downloadHDImage) {
                        HStack(spacing: 6) {
                            Image(systemName: "arrow.down.circle.fill")
                                .foregroundColor(.black)
                            Text("Download HD")
                                .font(.system(size: 12, weight: .bold))
                                .foregroundColor(.black)
                        }
                        .padding(.vertical, 10)
                        .padding(.horizontal, 16)
                        .background(TryZonTheme.primaryGold)
                        .cornerRadius(12)
                    }

                    // Affiliate Store Buy Link
                    Button(action: {
                        if let url = URL(string: "https://myntra.com") {
                            UIApplication.shared.open(url)
                        }
                    }) {
                        HStack(spacing: 4) {
                            Image(systemName: "bag.fill")
                            Text("Buy Outfit")
                        }
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(.white)
                        .padding(.vertical, 10)
                        .padding(.horizontal, 14)
                        .background(Color.pink.opacity(0.8))
                        .cornerRadius(12)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 16)
            }

            // Toast Message Notification Overlay
            if let toast = showToastMessage {
                VStack {
                    Spacer()
                    Text(toast)
                        .font(.system(size: 12, weight: .bold))
                        .padding(.horizontal, 16)
                        .padding(.vertical, 10)
                        .background(Color.black.opacity(0.85))
                        .foregroundColor(TryZonTheme.primaryGold)
                        .cornerRadius(20)
                        .padding(.bottom, 80)
                }
                .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
    }

    private func downloadHDImage() {
        guard let url = URL(string: resultImageUrl) else { return }
        Task {
            if let (data, _) = try? await URLSession.shared.data(from: url),
               let image = UIImage(data: data) {
                UIImageWriteToSavedPhotosAlbum(image, nil, nil, nil)
                DispatchQueue.main.async {
                    showToast("HD Image Saved to Photos Gallery! 📸")
                }
            }
        }
    }

    private func shareResultImage() {
        guard let url = URL(string: resultImageUrl) else { return }
        let activityVC = UIActivityViewController(activityItems: [url], applicationActivities: nil)
        if let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
           let rootVC = windowScene.windows.first?.rootViewController {
            rootVC.present(activityVC, animated: true)
        }
    }

    private func showToast(_ msg: String) {
        withAnimation { showToastMessage = msg }
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.5) {
            withAnimation { showToastMessage = nil }
        }
    }
}
