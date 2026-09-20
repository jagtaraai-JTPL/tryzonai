import SwiftUI
import UIKit

public struct TryOnResultView: View {
    let resultImageUrl: String
    let highresUrl: String?
    let originalPhotoUrl: String?
    let complements: [ComplementProduct]
    let onTryAnother: () -> Void
    let onTryComplement: ((ComplementProduct) -> Void)?

    @State private var sliderOffset: CGFloat = 0.5
    @State private var showHeartAnimation = false
    @State private var heartPosition: CGPoint = .zero
    @State private var isSavedToWardrobe = false
    @State private var showToastMessage: String? = nil
    @State private var isDownloading = false
    @State private var showShareSheet = false
    @State private var sharedItems: [Any] = []
    @State private var showReviewBanner = true

    private let sampleComplements: [ComplementProduct] = [
        ComplementProduct(id: "comp_1", name: "Italian Leather Loafers", category: "Shoes", price: 2999, imageUrl: "https://tryzonai.com/api/v1/outfits/premium_catalog/ai_premium_mens_italian_riviera_linen_suit.webp", url: "https://myntra.com", matchScore: 98),
        ComplementProduct(id: "comp_2", name: "Gold Chronograph Watch", category: "Accessories", price: 4500, imageUrl: "https://tryzonai.com/api/v1/outfits/premium_catalog/ai_premium_outfit_formal_ceo_black_suit_1778055862174.webp", url: "https://ajio.com", matchScore: 95),
        ComplementProduct(id: "comp_3", name: "Silk Designer Clutch", category: "Bags", price: 1999, imageUrl: "https://tryzonai.com/api/v1/outfits/premium_catalog/ai_premium_women_teal_knit_dress.webp", url: "https://myntra.com", matchScore: 92)
    ]

    public init(
        resultImageUrl: String,
        highresUrl: String? = nil,
        originalPhotoUrl: String? = nil,
        complements: [ComplementProduct] = [],
        onTryAnother: @escaping () -> Void,
        onTryComplement: ((ComplementProduct) -> Void)? = nil
    ) {
        self.resultImageUrl = resultImageUrl
        self.highresUrl = highresUrl
        self.originalPhotoUrl = originalPhotoUrl
        self.complements = complements
        self.onTryAnother = onTryAnother
        self.onTryComplement = onTryComplement
    }

    private var displayUrl: String {
        highresUrl ?? resultImageUrl
    }

    private var displayComplements: [ComplementProduct] {
        complements.isEmpty ? sampleComplements : complements
    }

    public var body: some View {
        ZStack {
            TryZonTheme.darkBackground.ignoresSafeArea()

            VStack(spacing: 0) {
                // ── 1. TOP HEADER BAR ──
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
                            .font(.system(size: 14, weight: .black, design: .rounded))
                            .foregroundColor(.white)
                        Text("AI Virtual Try-On")
                            .font(.system(size: 10.5))
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
                .padding(.top, 14)
                .padding(.bottom, 10)

                ScrollView(showsIndicators: false) {
                    VStack(spacing: 16) {

                        // ── 2. STATUS BANNER CHIP ──
                        HStack(spacing: 6) {
                            Image(systemName: "checkmark.seal.fill")
                                .foregroundColor(.green)
                                .font(.system(size: 13))
                            Text("AI Generation Complete • Swipe to Compare")
                                .font(.system(size: 11.5, weight: .semibold))
                                .foregroundColor(.white.opacity(0.9))
                        }
                        .padding(.horizontal, 14)
                        .padding(.vertical, 7)
                        .background(Color.green.opacity(0.15))
                        .cornerRadius(20)
                        .overlay(
                            RoundedRectangle(cornerRadius: 20)
                                .stroke(Color.green.opacity(0.4), lineWidth: 1)
                        )

                        // ── 3. INTERACTIVE BEFORE/AFTER SLIDER VIEWPORT ──
                        GeometryReader { geo in
                            let validOrigUrl = (originalPhotoUrl != nil && !originalPhotoUrl!.isEmpty) ? originalPhotoUrl! : displayUrl

                            ZStack(alignment: .leading) {
                                // Background Layer: AI Result Image
                                AsyncImage(url: URL(string: displayUrl)) { phase in
                                    switch phase {
                                    case .success(let img):
                                        img.resizable().aspectRatio(contentMode: .fill)
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
                                            ProgressView().tint(TryZonTheme.primaryGold)
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

                                // Foreground Layer: Original Photo (Clipped by sliderOffset)
                                AsyncImage(url: URL(string: validOrigUrl)) { phase in
                                    if let img = phase.image {
                                        img.resizable().aspectRatio(contentMode: .fill)
                                    } else {
                                        TryZonTheme.darkSurface
                                    }
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

                                // Floating Badges ("ORIGINAL" & "RESULT ✨")
                                HStack {
                                    Text("ORIGINAL")
                                        .font(.system(size: 10, weight: .black))
                                        .foregroundColor(.white)
                                        .padding(.horizontal, 10)
                                        .padding(.vertical, 5)
                                        .background(Color.black.opacity(0.7))
                                        .cornerRadius(8)
                                        .padding(12)
                                        .opacity(sliderOffset > 0.12 ? 1 : 0)

                                    Spacer()

                                    Text("RESULT ✨")
                                        .font(.system(size: 10, weight: .black))
                                        .foregroundColor(.black)
                                        .padding(.horizontal, 10)
                                        .padding(.vertical, 5)
                                        .background(TryZonTheme.primaryGold)
                                        .cornerRadius(8)
                                        .padding(12)
                                        .opacity(sliderOffset < 0.88 ? 1 : 0)
                                }
                                .frame(maxHeight: .infinity, alignment: .bottom)

                                // Vertical Divider Line & Golden Touch Handle
                                ZStack {
                                    Rectangle()
                                        .fill(TryZonTheme.primaryGold)
                                        .frame(width: 3, height: geo.size.height)

                                    Circle()
                                        .fill(TryZonTheme.primaryGold)
                                        .frame(width: 40, height: 40)
                                        .shadow(color: Color.black.opacity(0.6), radius: 8, y: 2)
                                        .overlay(
                                            Circle().stroke(Color.white, lineWidth: 1.5)
                                        )
                                        .overlay(
                                            HStack(spacing: 2) {
                                                Image(systemName: "chevron.left")
                                                Image(systemName: "chevron.right")
                                            }
                                            .font(.system(size: 11, weight: .black))
                                            .foregroundColor(.black)
                                        )
                                }
                                .position(x: geo.size.width * sliderOffset, y: geo.size.height / 2)

                                // Full Viewport Drag Gesture
                                Color.clear
                                    .contentShape(Rectangle())
                                    .gesture(
                                        DragGesture(minimumDistance: 0)
                                            .onChanged { val in
                                                let newRatio = val.location.x / geo.size.width
                                                sliderOffset = min(max(newRatio, 0.02), 0.98)
                                            }
                                    )

                                // Double-tap Heart Animation Overlay
                                if showHeartAnimation {
                                    Image(systemName: "heart.fill")
                                        .font(.system(size: 90))
                                        .foregroundColor(.red.opacity(0.9))
                                        .shadow(color: .black.opacity(0.6), radius: 12)
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
                        .frame(height: 380)
                        .cornerRadius(24)
                        .overlay(RoundedRectangle(cornerRadius: 24).stroke(Color.white.opacity(0.1), lineWidth: 1))

                        // Caption below slider
                        Text("👆 Swipe handle to compare • Double-tap to heart")
                            .font(.system(size: 11.5, weight: .medium))
                            .foregroundColor(.white.opacity(0.6))
                            .multilineTextAlignment(.center)

                        // ── 4. ACTION BUTTONS ROW ──
                        HStack(spacing: 10) {
                            // Save / Heart Button
                            Button(action: {
                                withAnimation(.spring()) { isSavedToWardrobe.toggle() }
                                showToast(isSavedToWardrobe ? "❤️ Saved to Wardrobe!" : "Removed from Wardrobe")
                            }) {
                                VStack(spacing: 4) {
                                    Image(systemName: isSavedToWardrobe ? "heart.fill" : "heart")
                                        .font(.system(size: 18))
                                        .foregroundColor(isSavedToWardrobe ? .red : .white)
                                        .scaleEffect(isSavedToWardrobe ? 1.2 : 1.0)
                                    Text("Save")
                                        .font(.system(size: 9.5, weight: .bold))
                                        .foregroundColor(.white.opacity(0.8))
                                }
                                .padding(.vertical, 10)
                                .padding(.horizontal, 14)
                                .background(TryZonTheme.surfaceVariant)
                                .cornerRadius(14)
                                .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color.white.opacity(0.08), lineWidth: 1))
                            }

                            // Download HD Button
                            Button(action: downloadHDImage) {
                                HStack(spacing: 8) {
                                    if isDownloading {
                                        ProgressView()
                                            .tint(.black)
                                            .scaleEffect(0.8)
                                    } else {
                                        Image(systemName: "arrow.down.circle.fill")
                                            .font(.system(size: 16))
                                    }
                                    Text(isDownloading ? "Saving..." : "Download HD")
                                        .font(.system(size: 13.5, weight: .bold))
                                }
                                .foregroundColor(.black)
                                .padding(.vertical, 12)
                                .frame(maxWidth: .infinity)
                                .background(TryZonTheme.primaryGold)
                                .cornerRadius(14)
                                .shadow(color: TryZonTheme.primaryGold.opacity(0.4), radius: 8, y: 3)
                            }
                            .disabled(isDownloading)
                            .buttonStyle(BounceButtonStyle())

                            // Share Button
                            Button(action: shareResultImage) {
                                VStack(spacing: 4) {
                                    Image(systemName: "square.and.arrow.up")
                                        .font(.system(size: 18))
                                        .foregroundColor(.white)
                                    Text("Share")
                                        .font(.system(size: 9.5, weight: .bold))
                                        .foregroundColor(.white.opacity(0.8))
                                }
                                .padding(.vertical, 10)
                                .padding(.horizontal, 14)
                                .background(TryZonTheme.surfaceVariant)
                                .cornerRadius(14)
                                .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color.white.opacity(0.08), lineWidth: 1))
                            }
                        }

                        // ── 5. MATCHING STYLIST COMPLEMENTS ROW ──
                        VStack(alignment: .leading, spacing: 10) {
                            Text("✨ MATCHING STYLIST ACCESSORIES")
                                .font(.system(size: 10, weight: .black))
                                .foregroundColor(TryZonTheme.primaryGold)
                                .tracking(1.2)

                            ScrollView(.horizontal, showsIndicators: false) {
                                HStack(spacing: 12) {
                                    ForEach(displayComplements) { item in
                                        complementCard(item)
                                    }
                                }
                            }
                        }
                        .padding(.top, 6)

                        // ── 6. MULTI-STORE SHOPPING PILLS ──
                        VStack(alignment: .leading, spacing: 10) {
                            Text("🛍️ SHOP THIS LOOK ON OFFICIAL STORES")
                                .font(.system(size: 10, weight: .black))
                                .foregroundColor(TryZonTheme.primaryGold)
                                .tracking(1.2)

                            HStack(spacing: 8) {
                                storeButton(name: "Myntra 🛍️", urlStr: "https://myntra.com", color: Color.pink)
                                storeButton(name: "Ajio 🏬", urlStr: "https://ajio.com", color: Color.yellow)
                                storeButton(name: "Flipkart ⚡", urlStr: "https://flipkart.com", color: Color.blue)
                                storeButton(name: "Amazon 📦", urlStr: "https://amazon.in", color: Color.orange)
                            }
                        }
                        .padding(.top, 6)

                        // ── 7. APP STORE 5-STAR REVIEW PROMPT BANNER ──
                        if showReviewBanner {
                            VStack(spacing: 8) {
                                HStack {
                                    Text("⭐ Love your AI Try-On Result?")
                                        .font(.system(size: 13, weight: .bold))
                                        .foregroundColor(.white)
                                    Spacer()
                                    Button(action: { showReviewBanner = false }) {
                                        Image(systemName: "xmark")
                                            .font(.system(size: 11, weight: .bold))
                                            .foregroundColor(.white.opacity(0.5))
                                    }
                                }

                                Text("Leave a 5-star review on the App Store to help us add more viral outfits!")
                                    .font(.system(size: 11))
                                    .foregroundColor(.white.opacity(0.75))
                                    .multilineTextAlignment(.leading)

                                Button(action: leaveReview) {
                                    HStack(spacing: 6) {
                                        Text("⭐ LEAVE 5-STAR REVIEW")
                                            .font(.system(size: 11.5, weight: .black))
                                            .foregroundColor(.black)
                                    }
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 8)
                                    .background(TryZonTheme.primaryGold)
                                    .cornerRadius(10)
                                }
                                .buttonStyle(BounceButtonStyle())
                            }
                            .padding(14)
                            .background(TryZonTheme.surfaceVariant)
                            .cornerRadius(18)
                            .overlay(RoundedRectangle(cornerRadius: 18).stroke(TryZonTheme.primaryGold.opacity(0.3), lineWidth: 1))
                            .padding(.top, 6)
                        }

                        Spacer(minLength: 30)
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 6)
                }
            }

            // Toast Overlay
            if let toast = showToastMessage {
                VStack {
                    Spacer()
                    Text(toast)
                        .font(.system(size: 13, weight: .bold))
                        .padding(.horizontal, 20)
                        .padding(.vertical, 12)
                        .background(Color.black.opacity(0.92))
                        .foregroundColor(TryZonTheme.primaryGold)
                        .cornerRadius(24)
                        .shadow(color: .black.opacity(0.4), radius: 8)
                        .padding(.bottom, 60)
                }
                .transition(.move(edge: .bottom).combined(with: .opacity))
                .zIndex(10)
            }
        }
        .sheet(isPresented: $showShareSheet) {
            ActivityViewController(activityItems: sharedItems)
        }
    }

    // MARK: - Complement Product Card
    private func complementCard(_ item: ComplementProduct) -> some View {
        Button(action: {
            if let cb = onTryComplement {
                cb(item)
            } else if let urlStr = item.url, let url = URL(string: urlStr) {
                UIApplication.shared.open(url)
            }
        }) {
            VStack(alignment: .leading, spacing: 4) {
                ZStack(alignment: .bottomTrailing) {
                    AsyncImage(url: item.fullImageURL) { phase in
                        if let img = phase.image {
                            img.resizable().aspectRatio(contentMode: .fill)
                        } else {
                            TryZonTheme.surfaceVariant
                        }
                    }
                    .frame(width: 115, height: 110)
                    .cornerRadius(12)
                    .clipped()

                    if let score = item.matchScore {
                        Text("\(score)% MATCH")
                            .font(.system(size: 7.5, weight: .black))
                            .foregroundColor(TryZonTheme.primaryGold)
                            .padding(.horizontal, 5)
                            .padding(.vertical, 2)
                            .background(Color.black.opacity(0.8))
                            .cornerRadius(4)
                            .overlay(RoundedRectangle(cornerRadius: 4).stroke(TryZonTheme.primaryGold.opacity(0.5), lineWidth: 0.5))
                            .padding(4)
                    }
                }

                Text(item.name)
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(.white)
                    .lineLimit(1)
            }
            .frame(width: 115)
            .padding(6)
            .background(TryZonTheme.surfaceVariant)
            .cornerRadius(14)
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color.white.opacity(0.08), lineWidth: 1))
        }
        .buttonStyle(BounceButtonStyle())
    }

    // MARK: - Store Button
    private func storeButton(name: String, urlStr: String, color: Color) -> some View {
        Button(action: {
            if let url = URL(string: urlStr) {
                UIApplication.shared.open(url)
            }
        }) {
            Text(name)
                .font(.system(size: 10.5, weight: .bold))
                .foregroundColor(.white)
                .padding(.horizontal, 10)
                .padding(.vertical, 8)
                .frame(maxWidth: .infinity)
                .background(color.opacity(0.3))
                .cornerRadius(10)
                .overlay(RoundedRectangle(cornerRadius: 10).stroke(color.opacity(0.6), lineWidth: 1))
        }
    }

    // MARK: - Download HD Image
    private func downloadHDImage() {
        guard let url = URL(string: displayUrl) else {
            showToast("Invalid image URL")
            return
        }
        isDownloading = true
        Task {
            do {
                let (data, _) = try await URLSession.shared.data(from: url)
                if let image = UIImage(data: data) {
                    UIImageWriteToSavedPhotosAlbum(image, nil, nil, nil)
                    await MainActor.run {
                        self.isDownloading = false
                        self.showToast("📸 HD Photo Saved to Camera Roll!")
                    }
                }
            } catch {
                await MainActor.run {
                    self.isDownloading = false
                    self.showToast("Download failed — please retry")
                }
            }
        }
    }

    // MARK: - Share Image
    private func shareResultImage() {
        guard let url = URL(string: displayUrl) else { return }
        Task {
            if let (data, _) = try? await URLSession.shared.data(from: url),
               let image = UIImage(data: data) {
                await MainActor.run {
                    self.sharedItems = [image, "Check out my AI virtual outfit try-on on TryZon AI! 🤩 #TryZonAI"]
                    self.showShareSheet = true
                }
            } else {
                await MainActor.run {
                    self.sharedItems = [url]
                    self.showShareSheet = true
                }
            }
        }
    }

    // MARK: - Leave Review
    private func leaveReview() {
        UIPasteboard.general.string = "Mind-blowing AI try-on app! The outfit fitting and photo realism are unbelievable. 5/5 stars! 🔥✨"
        showToast("📋 5-Star Review copied to clipboard!")
        if let url = URL(string: "https://apps.apple.com") {
            UIApplication.shared.open(url)
        }
    }

    private func showToast(_ msg: String) {
        withAnimation { showToastMessage = msg }
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.5) {
            withAnimation { showToastMessage = nil }
        }
    }
}
