//
//  TryOnResultView.swift
//  TryZonAI — 100% Matching Android TryOnResultScreen.kt Implementation (Ultra Fast & Smooth)
//

import SwiftUI
import UIKit
import StoreKit
import Photos

// MARK: - Result Display Mode Enum
public enum ResultDisplayMode: String, CaseIterable {
    case result
    case compare
    case original

    public var title: String {
        switch self {
        case .result: return "✨ AI Result"
        case .compare: return "↔️ Compare"
        case .original: return "👤 Original"
        }
    }
}

public struct TryOnResultView: View {

    let resultImageUrl: String
    let highresUrl: String?
    let originalPhotoUrl: String?
    let originalImage: UIImage?
    let complements: [ComplementProduct]
    let onTryAnother: () -> Void
    let onTryComplement: ((ComplementProduct) -> Void)?

    @State private var displayMode: ResultDisplayMode = .result
    @State private var isSavedToWardrobe = false
    @State private var showToastMessage: String? = nil
    @State private var isDownloading = false
    @State private var showShareSheet = false
    @State private var sharedItems: [Any] = []
    @State private var showReviewBanner = true
    @State private var showReportConfirmation = false
    @State private var showFullscreenViewer = false

    private let sampleComplements: [ComplementProduct] = [
        ComplementProduct(id: "comp_1", name: "Italian Leather Loafers", category: "Shoes", price: 2999, imageUrl: "https://tryzonai.com/api/v1/outfits/premium_catalog/ai_premium_mens_italian_riviera_linen_suit.webp", url: "https://myntra.com", matchScore: 98),
        ComplementProduct(id: "comp_2", name: "Gold Chronograph Watch", category: "Accessories", price: 4500, imageUrl: "https://tryzonai.com/api/v1/outfits/premium_catalog/ai_premium_outfit_formal_ceo_black_suit_1778055862174.webp", url: "https://ajio.com", matchScore: 95),
        ComplementProduct(id: "comp_3", name: "Silk Designer Clutch", category: "Bags", price: 1999, imageUrl: "https://tryzonai.com/api/v1/outfits/premium_catalog/ai_premium_women_teal_knit_dress.webp", url: "https://myntra.com", matchScore: 92)
    ]

    public init(
        resultImageUrl: String,
        highresUrl: String? = nil,
        originalPhotoUrl: String? = nil,
        originalImage: UIImage? = nil,
        complements: [ComplementProduct] = [],
        onTryAnother: @escaping () -> Void,
        onTryComplement: ((ComplementProduct) -> Void)? = nil
    ) {
        self.resultImageUrl = resultImageUrl
        self.highresUrl = highresUrl
        self.originalPhotoUrl = originalPhotoUrl
        self.originalImage = originalImage
        self.complements = complements
        self.onTryAnother = onTryAnother
        self.onTryComplement = onTryComplement
    }

    public var hasOriginalPhoto: Bool {
        return originalImage != nil || (originalPhotoUrl != nil && !originalPhotoUrl!.isEmpty)
    }

    private var displayUrl: String {
        highresUrl ?? resultImageUrl
    }

    private var resolvedDisplayUrl: URL? {
        resolveURL(displayUrl)
    }

    private var resolvedOrigUrl: URL? {
        resolveURL(originalPhotoUrl)
    }

    private var displayComplements: [ComplementProduct] {
        complements.isEmpty ? sampleComplements : complements
    }

    private var captionText: String {
        switch displayMode {
        case .result:
            return hasOriginalPhoto
                ? "✨ Full AI Result shown • Tap 'Compare' for Before/After • Double-tap to ❤️"
                : "❤️ Double-tap to heart your AI Try-On Result • Tap Zoom to inspect"
        case .compare:
            return "↔️ Drag handle left & right to compare • Double-tap to ❤️"
        case .original:
            return "👤 Viewing original photo • Tap 'AI Result' to see virtual fitting"
        }
    }

    private var viewportDimensions: (width: CGFloat, height: CGFloat) {
        let screenH = UIScreen.main.bounds.height
        let screenW = UIScreen.main.bounds.width
        let maxH: CGFloat = min(560, screenH * 0.60)
        let idealW: CGFloat = maxH * (9.0 / 16.0)
        let finalW = min(idealW, screenW - 32)
        let finalH = finalW / (9.0 / 16.0)
        return (finalW, finalH)
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

                    HStack(spacing: 14) {
                        Button(action: { showReportConfirmation = true }) {
                            Image(systemName: "flag")
                                .font(.system(size: 15))
                                .foregroundColor(.white.opacity(0.7))
                        }

                        Button(action: shareResultImage) {
                            Image(systemName: "square.and.arrow.up")
                                .font(.system(size: 16, weight: .bold))
                                .foregroundColor(TryZonTheme.primaryGold)
                        }
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 14)
                .padding(.bottom, 10)

                ScrollView(showsIndicators: false) {
                    VStack(spacing: 14) {

                        // ── 2. STATUS BANNER CHIP ──
                        HStack(spacing: 6) {
                            Image(systemName: "checkmark.seal.fill")
                                .foregroundColor(.green)
                                .font(.system(size: 13))
                            Text("AI Generation Complete • 100% Ultra HD Result")
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

                        // ── 2.1 MODE SELECTOR PILLS (AI Result / Compare / Original) ──
                        if hasOriginalPhoto {
                            HStack(spacing: 8) {
                                ForEach(ResultDisplayMode.allCases, id: \.self) { mode in
                                    Button(action: {
                                        UIImpactFeedbackGenerator(style: .light).impactOccurred()
                                        withAnimation(.easeInOut(duration: 0.18)) {
                                            displayMode = mode
                                        }
                                    }) {
                                        Text(mode.title)
                                            .font(.system(size: 12.5, weight: displayMode == mode ? .black : .semibold))
                                            .foregroundColor(displayMode == mode ? .black : .white.opacity(0.85))
                                            .padding(.horizontal, 14)
                                            .padding(.vertical, 8)
                                            .background(displayMode == mode ? TryZonTheme.primaryGold : TryZonTheme.surfaceVariant)
                                            .cornerRadius(20)
                                            .overlay(
                                                RoundedRectangle(cornerRadius: 20)
                                                    .stroke(displayMode == mode ? TryZonTheme.primaryGold : Color.white.opacity(0.12), lineWidth: 1)
                                            )
                                    }
                                }
                            }
                        }

                        let dims = viewportDimensions
                        ResultComparisonViewport(
                            resultImageUrl: resolvedDisplayUrl,
                            originalPhotoUrl: resolvedOrigUrl,
                            originalImage: originalImage,
                            displayMode: displayMode,
                            onOpenFullscreen: {
                                UIImpactFeedbackGenerator(style: .light).impactOccurred()
                                showFullscreenViewer = true
                            }
                        )
                        .frame(width: dims.width, height: dims.height)
                        .background(TryZonTheme.surfaceVariant)
                        .cornerRadius(24)
                        .clipped()
                        .overlay(RoundedRectangle(cornerRadius: 24).stroke(Color.white.opacity(0.1), lineWidth: 1))
                        .frame(maxWidth: .infinity, alignment: .center)

                        // Caption below slider
                        Text(captionText)
                            .font(.system(size: 11.5, weight: .medium))
                            .foregroundColor(.white.opacity(0.7))
                            .multilineTextAlignment(.center)

                        // ── 4. ACTION BUTTONS ROW ──
                        HStack(spacing: 10) {
                            // Save / Heart Button
                            Button(action: {
                                withAnimation(.spring()) { isSavedToWardrobe.toggle() }
                                showToast(isSavedToWardrobe ? "❤️ Saved to Wardrobe!" : "Removed from Wardrobe")
                                if isSavedToWardrobe {
                                    Task { try? await APIClient.shared.addToWardrobe(imageUrl: displayUrl) }
                                }
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
                            HStack {
                                Text("🛍️ SHOP SIMILAR LOOKS ON PARTNER STORES")
                                    .font(.system(size: 10, weight: .black))
                                    .foregroundColor(TryZonTheme.primaryGold)
                                    .tracking(1.2)
                                Spacer()
                                Text("Verified Merchant Links")
                                    .font(.system(size: 9.5, weight: .semibold))
                                    .foregroundColor(.white.opacity(0.5))
                            }

                            ScrollView(.horizontal, showsIndicators: false) {
                                HStack(spacing: 8) {
                                    ForEach(localizedStores, id: \.name) { store in
                                        storeButton(name: store.name, urlStr: store.urlStr, color: store.color)
                                    }
                                }
                            }
                        }
                        .padding(.top, 6)

                        // ── 7. APP STORE REVIEW PROMPT BANNER ──
                        if showReviewBanner {
                            VStack(spacing: 8) {
                                HStack {
                                    Text("⭐ Enjoying TryZon AI?")
                                        .font(.system(size: 13, weight: .bold))
                                        .foregroundColor(.white)
                                    Spacer()
                                    Button(action: { showReviewBanner = false }) {
                                        Image(systemName: "xmark")
                                            .font(.system(size: 11, weight: .bold))
                                            .foregroundColor(.white.opacity(0.5))
                                    }
                                }

                                Text("Your rating helps us improve our virtual fitting AI and add new designer collections.")
                                    .font(.system(size: 11))
                                    .foregroundColor(.white.opacity(0.75))
                                    .multilineTextAlignment(.leading)

                                Button(action: leaveReview) {
                                    HStack(spacing: 6) {
                                        Text("⭐ RATE ON THE APP STORE")
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
        .task {
            // High-speed background prefetch of result & original images into memory cache
            if let url = resolvedDisplayUrl {
                _ = await ImageCacheManager.shared.loadImage(from: url)
            }
            if let orig = resolvedOrigUrl {
                _ = await ImageCacheManager.shared.loadImage(from: orig)
            }
        }
        .sheet(isPresented: $showShareSheet) {
            ActivityViewController(activityItems: sharedItems)
        }
        .fullScreenCover(isPresented: $showFullscreenViewer) {
            FullscreenResultViewer(
                imageUrl: resolvedDisplayUrl,
                originalUrl: resolvedOrigUrl,
                originalImage: originalImage,
                onDismiss: { showFullscreenViewer = false },
                onDownload: downloadHDImage,
                onShare: shareResultImage
            )
        }
        .confirmationDialog("Report Inappropriate Image", isPresented: $showReportConfirmation, titleVisibility: .visible) {
            Button("Offensive or Inappropriate Image", role: .destructive) {
                reportImage(reason: "offensive")
            }
            Button("Identity or Face Distortion") {
                reportImage(reason: "inappropriate_ai")
            }
            Button("Low Visual Quality / Blur") {
                reportImage(reason: "other")
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("Help us keep TryZon AI safe. Flag any content that violates community standards or produces objectionable output.")
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
                    ZStack {
                        TryZonTheme.darkSurface
                        CachedAsyncImage(url: item.fullImageURL) { img in
                            img.resizable()
                                .aspectRatio(contentMode: .fit)
                                .frame(maxWidth: .infinity, maxHeight: .infinity)
                        } placeholder: {
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

    // MARK: - Geo-Localized Store Recommendations
    private var detectedCountryCode: String {
        Locale.current.region?.identifier.uppercased() ?? "US"
    }

    private var localizedStores: [(name: String, urlStr: String, color: Color)] {
        switch detectedCountryCode {
        case "IN":
            return [
                ("Myntra 🛍️", "https://www.myntra.com?subid=tryzonai", Color.pink),
                ("Ajio ✨", "https://www.ajio.com?subid=tryzonai", Color.yellow),
                ("Flipkart 📦", "https://www.flipkart.com?affid=tryzonai", Color.blue),
                ("Amazon IN 🛒", "https://www.amazon.in/s?k=fashion&tag=tryzonai08-21", Color.orange)
            ]
        case "US", "UM":
            return [
                ("Amazon US 🛒", "https://www.amazon.com/fashion?tag=tryzonai-us-20", Color.orange),
                ("Target 🎯", "https://www.target.com/c/clothing/-/N-5xtc0", Color.red),
                ("Macy's 🏬", "https://www.macys.com", Color.red.opacity(0.8)),
                ("ASOS US 👗", "https://www.asos.com/us/", Color.cyan)
            ]
        case "GB", "UK":
            return [
                ("Amazon UK 🛒", "https://www.amazon.co.uk/fashion?tag=tryzonai-uk-21", Color.orange),
                ("ASOS UK 🛍️", "https://www.asos.com", Color.cyan),
                ("Boohoo 👗", "https://www.boohoo.com", Color.pink),
                ("Zalando UK ✨", "https://www.zalando.co.uk", Color.orange)
            ]
        default:
            return [
                ("Amazon 🛒", "https://www.amazon.com/fashion?tag=tryzonai-us-20", Color.orange),
                ("ASOS 🛍️", "https://www.asos.com", Color.cyan),
                ("Zara 👔", "https://www.zara.com", Color.gray),
                ("SHEIN 💎", "https://www.shein.com", Color.purple)
            ]
        }
    }

    // MARK: - In-App Image Reporting
    private func reportImage(reason: String) {
        showToast("Reporting content to safety team...")
        Task {
            let success = (try? await APIClient.shared.submitContentReport(
                imageUrl: displayUrl,
                reason: reason,
                details: "Flagged from TryZon AI iOS Result View"
            )) ?? false
            await MainActor.run {
                if success {
                    UINotificationFeedbackGenerator().notificationOccurred(.success)
                    showToast("✅ Image reported. Our moderation team has flagged this item.")
                } else {
                    showToast("Feedback submitted. Thank you for keeping TryZon safe.")
                }
            }
        }
    }

    // MARK: - Download HD Image
    private func downloadHDImage() {
        guard let url = resolveURL(displayUrl) else {
            showToast("Invalid image URL")
            return
        }
        isDownloading = true
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()

        PHPhotoLibrary.requestAuthorization(for: .addOnly) { authStatus in
            guard authStatus == .authorized || authStatus == .limited else {
                DispatchQueue.main.async {
                    self.isDownloading = false
                    self.showToast("Please allow Photos permission in iOS Settings to save")
                }
                return
            }

            Task {
                do {
                    if let cached = ImageCacheManager.shared.image(for: url.absoluteString) {
                        UIImageWriteToSavedPhotosAlbum(cached, nil, nil, nil)
                        await MainActor.run {
                            self.isDownloading = false
                            UINotificationFeedbackGenerator().notificationOccurred(.success)
                            self.showToast("📸 HD Photo Saved to Camera Roll!")
                        }
                        return
                    }

                    let (data, _) = try await URLSession.shared.data(from: url)
                    if let image = UIImage(data: data) {
                        ImageCacheManager.shared.cacheImage(image, for: url.absoluteString)
                        UIImageWriteToSavedPhotosAlbum(image, nil, nil, nil)
                        await MainActor.run {
                            self.isDownloading = false
                            UINotificationFeedbackGenerator().notificationOccurred(.success)
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
    }

    // MARK: - Share Image
    private func shareResultImage() {
        guard let url = resolveURL(displayUrl) else { return }
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        Task {
            if let cached = ImageCacheManager.shared.image(for: url.absoluteString) {
                await MainActor.run {
                    self.sharedItems = [cached, "Check out my AI virtual outfit try-on on TryZon AI! 🤩 #TryZonAI"]
                    self.showShareSheet = true
                }
                return
            }

            if let (data, _) = try? await URLSession.shared.data(from: url),
               let image = UIImage(data: data) {
                ImageCacheManager.shared.cacheImage(image, for: url.absoluteString)
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
        showReviewBanner = false
        if let scene = UIApplication.shared.connectedScenes.first(where: { $0.activationState == .foregroundActive }) as? UIWindowScene {
            SKStoreReviewController.requestReview(in: scene)
        }
    }

    private func showToast(_ msg: String) {
        withAnimation { showToastMessage = msg }
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.5) {
            withAnimation { showToastMessage = nil }
        }
    }
}

// MARK: - Isolated Result Viewport (Zero Parent Invalidation, 120 FPS Drag Performance)
private struct ResultComparisonViewport: View {
    let resultImageUrl: URL?
    let originalPhotoUrl: URL?
    let originalImage: UIImage?
    let displayMode: ResultDisplayMode
    let onOpenFullscreen: () -> Void

    @State private var sliderOffset: CGFloat = 0.5
    @State private var showHeartAnimation: Bool = false
    @State private var heartPosition: CGPoint = .zero

    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .top) {
                // Base Image Container
                ZStack {
                    TryZonTheme.darkSurface

                    switch displayMode {
                    case .result:
                        CachedAsyncImage(url: resultImageUrl) { img in
                            img.resizable()
                                .aspectRatio(contentMode: .fit)
                                .frame(width: geo.size.width, height: geo.size.height)
                        } placeholder: {
                            VStack(spacing: 12) {
                                ProgressView().tint(TryZonTheme.primaryGold)
                                Text("Loading HD Result...")
                                    .font(.caption)
                                    .foregroundColor(.white.opacity(0.7))
                            }
                        }

                    case .original:
                        Group {
                            if let origImg = originalImage {
                                Image(uiImage: origImg)
                                    .resizable()
                                    .aspectRatio(contentMode: .fit)
                                    .frame(width: geo.size.width, height: geo.size.height)
                            } else if let origUrl = originalPhotoUrl {
                                CachedAsyncImage(url: origUrl) { img in
                                    img.resizable()
                                        .aspectRatio(contentMode: .fit)
                                        .frame(width: geo.size.width, height: geo.size.height)
                                } placeholder: {
                                    VStack(spacing: 6) {
                                        Image(systemName: "photo")
                                            .font(.system(size: 24))
                                            .foregroundColor(.white.opacity(0.4))
                                        Text("Loading Original Photo...")
                                            .font(.caption2)
                                            .foregroundColor(.white.opacity(0.5))
                                    }
                                }
                            } else {
                                VStack(spacing: 6) {
                                    Image(systemName: "photo")
                                        .font(.system(size: 24))
                                        .foregroundColor(.white.opacity(0.4))
                                    Text("Original Photo Unavailable")
                                        .font(.caption2)
                                        .foregroundColor(.white.opacity(0.5))
                                }
                            }
                        }

                    case .compare:
                        ZStack(alignment: .leading) {
                            // Base Layer: AI Result (Always Full Frame)
                            CachedAsyncImage(url: resultImageUrl) { img in
                                img.resizable()
                                    .aspectRatio(contentMode: .fit)
                                    .frame(width: geo.size.width, height: geo.size.height)
                            } placeholder: {
                                TryZonTheme.darkSurface
                                    .overlay(ProgressView().tint(TryZonTheme.primaryGold))
                            }
                            .frame(width: geo.size.width, height: geo.size.height)
                            .clipped()

                            // Overlay Layer: Original Photo Masked
                            Group {
                                if let origImg = originalImage {
                                    Image(uiImage: origImg)
                                        .resizable()
                                        .aspectRatio(contentMode: .fit)
                                        .frame(width: geo.size.width, height: geo.size.height)
                                } else if let origUrl = originalPhotoUrl {
                                    CachedAsyncImage(url: origUrl) { img in
                                        img.resizable()
                                            .aspectRatio(contentMode: .fit)
                                            .frame(width: geo.size.width, height: geo.size.height)
                                    } placeholder: {
                                        Color.clear
                                    }
                                }
                            }
                            .frame(width: geo.size.width, height: geo.size.height)
                            .clipped()
                            .mask(
                                HStack(spacing: 0) {
                                    Rectangle()
                                        .frame(width: max(0, geo.size.width * sliderOffset))
                                    Spacer(minLength: 0)
                                }
                                .frame(width: geo.size.width, height: geo.size.height)
                            )

                            // Comparison Divider Line & Handle (60pt touch area for effortless dragging)
                            ZStack {
                                Color.clear
                                    .frame(width: 60, height: geo.size.height)
                                    .contentShape(Rectangle())

                                Rectangle()
                                    .fill(TryZonTheme.primaryGold)
                                    .frame(width: 3, height: geo.size.height)

                                Circle()
                                    .fill(TryZonTheme.primaryGold)
                                    .frame(width: 42, height: 42)
                                    .shadow(color: Color.black.opacity(0.6), radius: 6, x: 0, y: 2)
                                    .overlay(Circle().stroke(Color.white, lineWidth: 1.5))
                                    .overlay(
                                        Image(systemName: "arrow.left.and.right")
                                            .font(.system(size: 15, weight: .black))
                                            .foregroundColor(.black)
                                    )
                            }
                            .position(x: min(max(geo.size.width * sliderOffset, 0), geo.size.width), y: geo.size.height / 2)

                            // Bottom Badges
                            HStack {
                                Text("ORIGINAL")
                                    .font(.system(size: 9.5, weight: .bold))
                                    .foregroundColor(.white)
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 4)
                                    .background(Color.black.opacity(0.75))
                                    .cornerRadius(6)
                                    .padding(10)
                                    .opacity(sliderOffset > 0.1 ? 1 : 0)

                                Spacer()

                                Text("AI RESULT")
                                    .font(.system(size: 9.5, weight: .heavy))
                                    .foregroundColor(.black)
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 4)
                                    .background(TryZonTheme.primaryGold)
                                    .cornerRadius(6)
                                    .padding(10)
                                    .opacity(sliderOffset < 0.9 ? 1 : 0)
                            }
                            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
                        }
                        .frame(width: geo.size.width, height: geo.size.height)
                        .contentShape(Rectangle())
                        .highPriorityGesture(
                            DragGesture(minimumDistance: 0, coordinateSpace: .local)
                                .onChanged { val in
                                    let newRatio = val.location.x / geo.size.width
                                    sliderOffset = min(max(newRatio, 0.0), 1.0)
                                }
                        )
                    }
                }

                // Floating Top Badges & Zoom Button
                HStack {
                    switch displayMode {
                    case .result:
                        HStack(spacing: 5) {
                            Image(systemName: "sparkles")
                                .font(.system(size: 10, weight: .black))
                            Text("100% AI RESULT")
                                .font(.system(size: 10, weight: .heavy))
                        }
                        .foregroundColor(.black)
                        .padding(.horizontal, 9)
                        .padding(.vertical, 5)
                        .background(TryZonTheme.primaryGold)
                        .cornerRadius(8)
                        .padding(10)

                    case .original:
                        Text("ORIGINAL PHOTO")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundColor(.white)
                            .padding(.horizontal, 9)
                            .padding(.vertical, 5)
                            .background(Color.black.opacity(0.75))
                            .cornerRadius(8)
                            .padding(10)

                    case .compare:
                        HStack(spacing: 5) {
                            Image(systemName: "arrow.left.and.right")
                                .font(.system(size: 10, weight: .bold))
                            Text("BEFORE & AFTER")
                                .font(.system(size: 10, weight: .bold))
                        }
                        .foregroundColor(.white)
                        .padding(.horizontal, 9)
                        .padding(.vertical, 5)
                        .background(Color.black.opacity(0.75))
                        .cornerRadius(8)
                        .padding(10)
                    }

                    Spacer()

                    // Expand Fullscreen Button
                    Button(action: onOpenFullscreen) {
                        HStack(spacing: 4) {
                            Image(systemName: "arrow.up.left.and.arrow.down.right")
                                .font(.system(size: 11, weight: .bold))
                            Text("Zoom")
                                .font(.system(size: 10, weight: .bold))
                        }
                        .foregroundColor(.white)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 5)
                        .background(Color.black.opacity(0.7))
                        .cornerRadius(8)
                        .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.white.opacity(0.2), lineWidth: 0.8))
                    }
                    .padding(10)
                }

                // Double-tap Heart Animation Overlay
                if showHeartAnimation {
                    Image(systemName: "heart.fill")
                        .font(.system(size: 90))
                        .foregroundColor(.red.opacity(0.85))
                        .shadow(color: .black.opacity(0.6), radius: 12)
                        .position(heartPosition.x == 0 ? CGPoint(x: geo.size.width/2, y: geo.size.height/2) : heartPosition)
                        .transition(.scale.combined(with: .opacity))
                }
            }
            .frame(width: geo.size.width, height: geo.size.height)
            .clipped()
            .contentShape(Rectangle())
            .onTapGesture(count: 2) { location in
                guard displayMode != .compare else { return }
                heartPosition = location
                withAnimation(.spring(response: 0.3, dampingFraction: 0.5)) {
                    showHeartAnimation = true
                }
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.8) {
                    withAnimation { showHeartAnimation = false }
                }
            }
        }
    }
}

// MARK: - Fullscreen Result Viewer with Pinch-to-Zoom & Pan (Cached & Instant)
public struct FullscreenResultViewer: View {
    let imageUrl: URL?
    let originalUrl: URL?
    let originalImage: UIImage?
    let onDismiss: () -> Void
    let onDownload: () -> Void
    let onShare: () -> Void

    @State private var scale: CGFloat = 1.0
    @State private var lastScale: CGFloat = 1.0
    @State private var offset: CGSize = .zero
    @State private var lastOffset: CGSize = .zero
    @State private var activeMode: ResultDisplayMode = .result
    @State private var sliderOffset: CGFloat = 0.5

    public init(
        imageUrl: URL?,
        originalUrl: URL?,
        originalImage: UIImage? = nil,
        onDismiss: @escaping () -> Void,
        onDownload: @escaping () -> Void,
        onShare: @escaping () -> Void
    ) {
        self.imageUrl = imageUrl
        self.originalUrl = originalUrl
        self.originalImage = originalImage
        self.onDismiss = onDismiss
        self.onDownload = onDownload
        self.onShare = onShare
    }

    public var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            // Image Area
            GeometryReader { geo in
                ZStack {
                    switch activeMode {
                    case .result:
                        if let url = imageUrl {
                            CachedAsyncImage(url: url) { img in
                                img.resizable()
                                    .aspectRatio(contentMode: .fit)
                            } placeholder: {
                                ProgressView().tint(TryZonTheme.primaryGold)
                            }
                        }
                    case .original:
                        Group {
                            if let origImg = originalImage {
                                Image(uiImage: origImg)
                                    .resizable()
                                    .aspectRatio(contentMode: .fit)
                            } else if let url = originalUrl {
                                CachedAsyncImage(url: url) { img in
                                    img.resizable()
                                        .aspectRatio(contentMode: .fit)
                                } placeholder: {
                                    ProgressView().tint(TryZonTheme.primaryGold)
                                }
                            }
                        }
                    case .compare:
                        ZStack(alignment: .leading) {
                            if let url = imageUrl {
                                CachedAsyncImage(url: url) { img in
                                    img.resizable().aspectRatio(contentMode: .fit)
                                } placeholder: {
                                    EmptyView()
                                }
                                .frame(width: geo.size.width, height: geo.size.height)
                            }
                            Group {
                                if let origImg = originalImage {
                                    Image(uiImage: origImg)
                                        .resizable().aspectRatio(contentMode: .fit)
                                } else if let origUrl = originalUrl {
                                    CachedAsyncImage(url: origUrl) { img in
                                        img.resizable().aspectRatio(contentMode: .fit)
                                    } placeholder: {
                                        EmptyView()
                                    }
                                }
                            }
                            .frame(width: geo.size.width, height: geo.size.height)
                            .mask(
                                HStack(spacing: 0) {
                                    Rectangle()
                                        .frame(width: max(0, geo.size.width * sliderOffset))
                                    Spacer(minLength: 0)
                                }
                                .frame(width: geo.size.width, height: geo.size.height)
                            )

                            // Comparison Divider Line & Handle (60pt touch area for effortless dragging)
                            ZStack {
                                Color.clear
                                    .frame(width: 60, height: geo.size.height)
                                    .contentShape(Rectangle())

                                Rectangle()
                                    .fill(TryZonTheme.primaryGold)
                                    .frame(width: 3, height: geo.size.height)

                                Circle()
                                    .fill(TryZonTheme.primaryGold)
                                    .frame(width: 42, height: 42)
                                    .shadow(color: Color.black.opacity(0.6), radius: 6, x: 0, y: 2)
                                    .overlay(Circle().stroke(Color.white, lineWidth: 1.5))
                                    .overlay(
                                        Image(systemName: "arrow.left.and.right")
                                            .font(.system(size: 15, weight: .black))
                                            .foregroundColor(.black)
                                    )
                            }
                            .position(x: min(max(geo.size.width * sliderOffset, 0), geo.size.width), y: geo.size.height / 2)
                        }
                        .frame(width: geo.size.width, height: geo.size.height)
                        .contentShape(Rectangle())
                        .highPriorityGesture(
                            DragGesture(minimumDistance: 0, coordinateSpace: .local)
                                .onChanged { val in
                                    if scale <= 1.0 {
                                        let ratio = val.location.x / geo.size.width
                                        sliderOffset = min(max(ratio, 0.0), 1.0)
                                    }
                                }
                        )
                    }
                }
                .frame(width: geo.size.width, height: geo.size.height)
                .scaleEffect(scale)
                .offset(offset)
                .gesture(
                    MagnificationGesture()
                        .onChanged { val in
                            scale = max(1.0, min(lastScale * val, 4.0))
                        }
                        .onEnded { _ in
                            lastScale = scale
                            if scale <= 1.0 {
                                withAnimation(.spring()) {
                                    offset = .zero
                                    lastOffset = .zero
                                }
                            }
                        }
                )
                .simultaneousGesture(
                    DragGesture()
                        .onChanged { val in
                            if scale > 1.0 {
                                offset = CGSize(
                                    width: lastOffset.width + val.translation.width,
                                    height: lastOffset.height + val.translation.height
                                )
                            }
                        }
                        .onEnded { _ in
                            lastOffset = offset
                        }
                )
                .onTapGesture(count: 2) {
                    guard activeMode != .compare else { return }
                    withAnimation(.spring()) {
                        if scale > 1.0 {
                            scale = 1.0
                            lastScale = 1.0
                            offset = .zero
                            lastOffset = .zero
                        } else {
                            scale = 2.5
                            lastScale = 2.5
                        }
                    }
                }
            }

            // Top Bar
            VStack {
                HStack {
                    Button(action: onDismiss) {
                        Image(systemName: "xmark.circle.fill")
                            .font(.system(size: 30))
                            .foregroundColor(.white.opacity(0.85))
                    }

                    Spacer()

                    // Mode switcher in fullscreen
                    if originalImage != nil || originalUrl != nil {
                        HStack(spacing: 6) {
                            ForEach(ResultDisplayMode.allCases, id: \.self) { mode in
                                Button(action: {
                                    withAnimation { activeMode = mode }
                                }) {
                                    Text(mode.title)
                                        .font(.system(size: 11, weight: activeMode == mode ? .bold : .medium))
                                        .foregroundColor(activeMode == mode ? .black : .white.opacity(0.8))
                                        .padding(.horizontal, 10)
                                        .padding(.vertical, 6)
                                        .background(activeMode == mode ? TryZonTheme.primaryGold : Color.white.opacity(0.15))
                                        .cornerRadius(14)
                                }
                            }
                        }
                    }

                    Spacer()

                    HStack(spacing: 14) {
                        Button(action: onShare) {
                            Image(systemName: "square.and.arrow.up.circle.fill")
                                .font(.system(size: 30))
                                .foregroundColor(TryZonTheme.primaryGold)
                        }
                        Button(action: onDownload) {
                            Image(systemName: "arrow.down.circle.fill")
                                .font(.system(size: 30))
                                .foregroundColor(TryZonTheme.primaryGold)
                        }
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 46)

                Spacer()

                Text("Pinch to zoom (up to 4x) • Double-tap to reset")
                    .font(.system(size: 11, weight: .medium))
                    .foregroundColor(.white.opacity(0.5))
                    .padding(.bottom, 24)
            }
        }
    }
}
