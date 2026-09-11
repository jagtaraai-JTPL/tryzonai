import SwiftUI

public struct TryOnResultView: View {
    let resultImageUrl: String
    let originalPhotoUrl: String?
    let onTryAnother: () -> Void

    @State private var sliderOffset: CGFloat = 0.5
    @State private var showHeart: Bool = false
    @State private var isFullscreen: Bool = false

    public init(resultImageUrl: String, originalPhotoUrl: String? = nil, onTryAnother: @escaping () -> Void = {}) {
        self.resultImageUrl = resultImageUrl
        self.originalPhotoUrl = originalPhotoUrl
        self.onTryAnother = onTryAnother
    }

    public var body: some View {
        VStack(spacing: 16) {
            // Header
            HStack {
                Text("HD AI Fitting Result ✨")
                    .font(.system(size: 18, weight: .black))
                    .foregroundColor(TryZonTheme.primaryGold)
                Spacer()
                Button(action: onTryAnother) {
                    Text("Try Another Outfit 🔄")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(.black)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(TryZonTheme.primaryGold)
                        .cornerRadius(14)
                }
            }
            .padding(.horizontal, 16)

            // Interactive Split-Screen Comparison Slider
            GeometryReader { geo in
                ZStack {
                    // Result AI Outfit Image (Background layer)
                    AsyncImage(url: URL(string: resultImageUrl)) { img in
                        img.resizable().scaledToFill()
                    } placeholder: {
                        Color(TryZonTheme.surfaceVariant)
                    }
                    .frame(width: geo.size.width, height: geo.size.height)
                    .clipped()

                    // Original User Photo (Foreground clipped layer)
                    if let origUrl = originalPhotoUrl, !origUrl.isEmpty {
                        AsyncImage(url: URL(string: origUrl)) { img in
                            img.resizable().scaledToFill()
                        } placeholder: {
                            Color(TryZonTheme.darkSurface)
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
                    }

                    // Floating Slider Handle
                    Rectangle()
                        .fill(TryZonTheme.primaryGold)
                        .frame(width: 3, height: geo.size.height)
                        .offset(x: (sliderOffset - 0.5) * geo.size.width)
                        .overlay(
                            Circle()
                                .fill(TryZonTheme.primaryGold)
                                .frame(width: 32, height: 32)
                                .overlay(
                                    Image(systemName: "chevron.left.chevron.right")
                                        .font(.system(size: 12, weight: .black))
                                        .foregroundColor(.black)
                                )
                                .offset(x: (sliderOffset - 0.5) * geo.size.width)
                        )
                        .gesture(
                            DragGesture()
                                .onChanged { value in
                                    let newOffset = value.location.x / geo.size.width
                                    sliderOffset = min(max(newOffset, 0.05), 0.95)
                                }
                        )

                    // Double Tap Heart Overlay
                    if showHeart {
                        Image(systemName: "heart.fill")
                            .font(.system(size: 80))
                            .foregroundColor(.red)
                            .shadow(radius: 10)
                            .transition(.scale.combined(with: .opacity))
                    }
                }
                .cornerRadius(24)
                .overlay(
                    RoundedRectangle(cornerRadius: 24)
                        .stroke(TryZonTheme.primaryGold.opacity(0.3), lineWidth: 1.5)
                )
                .onTapGesture(count: 2) {
                    withAnimation(.spring()) {
                        showHeart = true
                    }
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.8) {
                        withAnimation { showHeart = false }
                    }
                }
            }
            .frame(height: 380)
            .padding(.horizontal, 16)

            // Instruction Pill
            Text("👈 Drag handle to compare • Double tap to ❤️")
                .font(.system(size: 11, weight: .bold))
                .foregroundColor(.white.opacity(0.6))

            // Action Buttons (Save HD, Share, Store Links)
            VStack(spacing: 10) {
                HStack(spacing: 10) {
                    Button(action: {
                        // Share Image
                    }) {
                        HStack {
                            Image(systemName: "square.and.arrow.up")
                            Text("Share Look")
                        }
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(TryZonTheme.surfaceVariant)
                        .cornerRadius(14)
                    }

                    Button(action: {
                        // Download Image
                    }) {
                        HStack {
                            Image(systemName: "arrow.down.to.line")
                            Text("Save HD")
                        }
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(.black)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(TryZonTheme.primaryGold)
                        .cornerRadius(14)
                    }
                }

                // Affiliate Store Links
                HStack(spacing: 8) {
                    StoreButton(name: "Myntra", color: .pink)
                    StoreButton(name: "Ajio", color: .black)
                    StoreButton(name: "Flipkart", color: .blue)
                    StoreButton(name: "Amazon", color: .orange)
                }
            }
            .padding(.horizontal, 16)
        }
        .padding(.vertical, 12)
        .background(TryZonTheme.darkBackground)
    }
}

struct StoreButton: View {
    let name: String
    let color: Color

    var body: some View {
        Button(action: {
            if let url = URL(string: "https://\(name.lowercased()).com") {
                UIApplication.shared.open(url)
            }
        }) {
            Text(name)
                .font(.system(size: 11, weight: .bold))
                .foregroundColor(.white)
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(color.opacity(0.8))
                .cornerRadius(10)
        }
    }
}
