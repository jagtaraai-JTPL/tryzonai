import SwiftUI

public struct ProductDetailView: View {
    let item: CatalogItem
    let onTryOn: (CatalogItem) -> Void
    @Environment(\.dismiss) private var dismiss

    @State private var selectedSize: String = "M"
    @State private var selectedColor: Color = .blue
    private let availableSizes = ["S", "M", "L", "XL"]

    public init(item: CatalogItem, onTryOn: @escaping (CatalogItem) -> Void) {
        self.item = item
        self.onTryOn = onTryOn
    }

    public var body: some View {
        ZStack {
            TryZonTheme.darkBackground.ignoresSafeArea()

            VStack(spacing: 0) {
                // Header Navigation
                HStack {
                    Button(action: { dismiss() }) {
                        Image(systemName: "chevron.left")
                            .font(.system(size: 18, weight: .bold))
                            .foregroundColor(TryZonTheme.primaryGold)
                    }

                    Spacer()

                    Text("PRODUCT DETAILS")
                        .font(.system(size: 13, weight: .black))
                        .foregroundColor(.white)
                        .tracking(1.5)

                    Spacer()

                    Button(action: { dismiss() }) {
                        Image(systemName: "xmark.circle.fill")
                            .font(.system(size: 22))
                            .foregroundColor(.white.opacity(0.6))
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 14)
                .padding(.bottom, 10)

                ScrollView(showsIndicators: false) {
                    VStack(alignment: .leading, spacing: 18) {
                        // Product Main Image
                        ZStack(alignment: .topTrailing) {
                            AsyncImage(url: item.fullImageURL) { phase in
                                if let img = phase.image {
                                    img.resizable().aspectRatio(contentMode: .fill)
                                } else {
                                    TryZonTheme.surfaceVariant
                                        .overlay(ProgressView().tint(TryZonTheme.primaryGold))
                                }
                            }
                            .frame(height: 380)
                            .frame(maxWidth: .infinity)
                            .cornerRadius(24)
                            .clipped()

                            if let badge = item.badge {
                                Text(badge)
                                    .font(.system(size: 10, weight: .bold))
                                    .foregroundColor(TryZonTheme.primaryGold)
                                    .padding(.horizontal, 10)
                                    .padding(.vertical, 5)
                                    .background(Color.black.opacity(0.8))
                                    .cornerRadius(100)
                                    .overlay(RoundedRectangle(cornerRadius: 100).stroke(TryZonTheme.primaryGold.opacity(0.4), lineWidth: 1))
                                    .padding(14)
                            }
                        }

                        // Brand & Product Title
                        VStack(alignment: .leading, spacing: 4) {
                            if let brand = item.brand {
                                Text(brand)
                                    .font(.system(size: 12, weight: .bold))
                                    .foregroundColor(TryZonTheme.primaryGold)
                            }
                            Text(item.name)
                                .font(.system(size: 22, weight: .black, design: .rounded))
                                .foregroundColor(.white)
                        }

                        // Price & Discount Row
                        HStack(spacing: 10) {
                            Text("₹\(item.price)")
                                .font(.system(size: 24, weight: .black))
                                .foregroundColor(TryZonTheme.primaryGold)

                            if let orig = item.original_price, orig > item.price {
                                Text("₹\(orig)")
                                    .font(.system(size: 16))
                                    .foregroundColor(.white.opacity(0.4))
                                    .strikethrough()

                                Text("50% OFF")
                                    .font(.system(size: 10, weight: .bold))
                                    .foregroundColor(.white)
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 3)
                                    .background(Color.red)
                                    .cornerRadius(6)
                            }
                        }

                        Divider().background(Color.white.opacity(0.12))

                        // Size Selection Row
                        VStack(alignment: .leading, spacing: 10) {
                            Text("SELECT SIZE")
                                .font(.system(size: 11, weight: .bold))
                                .foregroundColor(.white.opacity(0.7))
                                .tracking(1)

                            HStack(spacing: 12) {
                                ForEach(availableSizes, id: \.self) { sz in
                                    Button(action: { selectedSize = sz }) {
                                        Text(sz)
                                            .font(.system(size: 13, weight: .bold))
                                            .frame(width: 44, height: 44)
                                            .background(selectedSize == sz ? TryZonTheme.primaryGold : TryZonTheme.surfaceVariant)
                                            .foregroundColor(selectedSize == sz ? .black : .white)
                                            .cornerRadius(12)
                                            .overlay(RoundedRectangle(cornerRadius: 12).stroke(selectedSize == sz ? TryZonTheme.primaryGold : Color.white.opacity(0.1), lineWidth: 1))
                                    }
                                }
                            }
                        }

                        Divider().background(Color.white.opacity(0.12))

                        // Features List
                        VStack(alignment: .leading, spacing: 8) {
                            Text("TRYZON AI ADVANCED FEATURES")
                                .font(.system(size: 11, weight: .bold))
                                .foregroundColor(TryZonTheme.primaryGold)
                                .tracking(1)

                            featureRow(icon: "sparkles", text: "100% Photorealistic Neural Fabric Mapping")
                            featureRow(icon: "bolt.fill", text: "Fast GPU Render Queue (< 2s)")
                            featureRow(icon: "shield.fill", text: "Private & Secure Fitting Guarantee")
                        }

                        Spacer(minLength: 30)
                    }
                    .padding(.horizontal, 16)
                }

                // Sticky Bottom CTAs
                VStack(spacing: 10) {
                    Button(action: {
                        dismiss()
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
                            onTryOn(item)
                        }
                    }) {
                        HStack(spacing: 8) {
                            Image(systemName: "sparkles")
                                .font(.system(size: 16, weight: .bold))
                            Text("TRY ON THIS OUTFIT ⚡")
                                .font(.system(size: 15, weight: .black))
                                .tracking(1)
                        }
                        .foregroundColor(.black)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(TryZonTheme.primaryGold)
                        .cornerRadius(16)
                        .shadow(color: TryZonTheme.primaryGold.opacity(0.5), radius: 12, y: 4)
                    }
                    .buttonStyle(BounceButtonStyle())

                    Button(action: {
                        if let url = URL(string: "https://myntra.com") {
                            UIApplication.shared.open(url)
                        }
                    }) {
                        HStack(spacing: 6) {
                            Image(systemName: "bag.fill")
                            Text("Buy on Myntra / Ajio Store")
                                .font(.system(size: 12, weight: .bold))
                        }
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                        .background(TryZonTheme.surfaceVariant)
                        .cornerRadius(12)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
                .background(TryZonTheme.darkSurface)
            }
        }
    }

    private func featureRow(icon: String, text: String) -> some View {
        HStack(spacing: 10) {
            Image(systemName: icon)
                .font(.system(size: 13))
                .foregroundColor(TryZonTheme.primaryGold)
            Text(text)
                .font(.system(size: 12))
                .foregroundColor(.white.opacity(0.8))
        }
    }
}
