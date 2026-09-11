import SwiftUI

// MARK: - CatalogView — Exact 1:1 Replica of Android CatalogScreen
public struct CatalogView: View {
    @ObservedObject var apiClient: APIClient
    let onSelectGarmentForTryOn: (CatalogItem) -> Void

    @State private var selectedCategory: String = "All AI Outfits"
    @State private var selectedGender: String = "All"
    @State private var selectedSort: String = "Popularity"
    @State private var searchText: String = ""
    @State private var items: [CatalogItem] = []
    @State private var isLoading: Bool = false
    @State private var selectedProductDetail: CatalogItem? = nil

    private let filterCategories = [
        "All AI Outfits", "Suits & Formal", "Dresses & Gowns", "Streetwear & Cyber", "Ethnic & Festive", "Casual & Shirts"
    ]

    private let sortOptions = ["Popularity", "Newest AI"]
    private let genderOptions = ["All", "Men", "Women"]

    public init(apiClient: APIClient, onSelectGarmentForTryOn: @escaping (CatalogItem) -> Void) {
        self.apiClient = apiClient
        self.onSelectGarmentForTryOn = onSelectGarmentForTryOn
    }

    public var body: some View {
        VStack(spacing: 0) {
            // ── 1. SINGLE COMPACT FILTER & CATEGORY BAR ──
            filterBar
                .padding(.vertical, 8)
                .background(TryZonTheme.darkBackground)

            // ── 2. CATALOG GRID CONTENT ──
            if isLoading && items.isEmpty {
                shimmerLoadingGrid
            } else {
                ScrollView(showsIndicators: false) {
                    VStack(spacing: 16) {
                        // ── FEATURED #1 VIRAL HERO BANNER (Android Replica) ──
                        if selectedCategory == "All AI Outfits" && !displayedItems.isEmpty {
                            featuredHeroBanner(displayedItems.first!)
                        }

                        // ── 2-COLUMN PRODUCT GRID ──
                        LazyVGrid(columns: [GridItem(.flexible(), spacing: 14), GridItem(.flexible(), spacing: 14)], spacing: 14) {
                            ForEach(displayedItems) { item in
                                catalogProductCard(item)
                            }
                        }

                        // Load More Button
                        Button(action: loadCatalogData) {
                            Text("LOAD MORE AI STYLES")
                                .font(.system(size: 11, weight: .bold))
                                .foregroundColor(TryZonTheme.primaryGold)
                                .padding(.horizontal, 24)
                                .padding(.vertical, 10)
                                .background(TryZonTheme.darkSurface)
                                .cornerRadius(100)
                                .overlay(RoundedRectangle(cornerRadius: 100).stroke(TryZonTheme.primaryGold, lineWidth: 1))
                        }
                        .padding(.top, 16)
                        .padding(.bottom, 40)
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 8)
                }
            }
        }
        .background(TryZonTheme.darkBackground)
        .onAppear {
            loadCatalogData()
        }
        .sheet(item: $selectedProductDetail) { product in
            ProductDetailView(item: product, onTryOn: { item in
                onSelectGarmentForTryOn(item)
            })
        }
    }

    // ─────────────────────────────────────
    // MARK: - FILTER & CATEGORY BAR
    // ─────────────────────────────────────
    private var filterBar: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                // Sort Dropdown Pill
                Menu {
                    ForEach(sortOptions, id: \.self) { opt in
                        Button(opt) {
                            selectedSort = opt
                            loadCatalogData()
                        }
                    }
                } label: {
                    HStack(spacing: 4) {
                        Text(selectedSort == "Popularity" ? "Sort" : selectedSort)
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(selectedSort == "Popularity" ? .white.opacity(0.7) : TryZonTheme.primaryGold)
                        Image(systemName: "chevron.down")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundColor(selectedSort == "Popularity" ? .white.opacity(0.7) : TryZonTheme.primaryGold)
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 7)
                    .background(selectedSort != "Popularity" ? TryZonTheme.primaryGold.opacity(0.15) : TryZonTheme.surfaceVariant.opacity(0.4))
                    .cornerRadius(100)
                    .overlay(RoundedRectangle(cornerRadius: 100).stroke(Color.white.opacity(0.08), lineWidth: 1))
                }

                // Gender Dropdown Pill
                Menu {
                    ForEach(genderOptions, id: \.self) { opt in
                        Button(opt) {
                            selectedGender = opt
                            loadCatalogData()
                        }
                    }
                } label: {
                    HStack(spacing: 4) {
                        Text(selectedGender == "All" ? "Gender" : selectedGender)
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(selectedGender == "All" ? .white.opacity(0.7) : TryZonTheme.primaryGold)
                        Image(systemName: "chevron.down")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundColor(selectedGender == "All" ? .white.opacity(0.7) : TryZonTheme.primaryGold)
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 7)
                    .background(selectedGender != "All" ? TryZonTheme.primaryGold.opacity(0.15) : TryZonTheme.surfaceVariant.opacity(0.4))
                    .cornerRadius(100)
                    .overlay(RoundedRectangle(cornerRadius: 100).stroke(Color.white.opacity(0.08), lineWidth: 1))
                }

                // Vertical Divider
                Rectangle()
                    .fill(Color.white.opacity(0.15))
                    .frame(width: 1, height: 20)

                // Category Chips
                ForEach(filterCategories, id: \.self) { cat in
                    let isSelected = selectedCategory == cat
                    Button(action: {
                        selectedCategory = cat
                        loadCatalogData()
                    }) {
                        Text(cat)
                            .font(.system(size: 12, weight: isSelected ? .bold : .medium))
                            .padding(.horizontal, 14)
                            .padding(.vertical, 7)
                            .background(isSelected ? TryZonTheme.primaryGold : TryZonTheme.surfaceVariant.opacity(0.4))
                            .foregroundColor(isSelected ? .black : .white.opacity(0.75))
                            .cornerRadius(100)
                            .overlay(RoundedRectangle(cornerRadius: 100).stroke(isSelected ? TryZonTheme.primaryGold : Color.white.opacity(0.08), lineWidth: 1))
                    }
                }
            }
            .padding(.horizontal, 16)
        }
    }

    // ─────────────────────────────────────
    // MARK: - FEATURED HERO BANNER (#1 VIRAL)
    // ─────────────────────────────────────
    private func featuredHeroBanner(_ item: CatalogItem) -> some View {
        Button(action: { selectedProductDetail = item }) {
            ZStack(alignment: .bottomLeading) {
                AsyncImage(url: item.fullImageURL) { phase in
                    if let img = phase.image {
                        img.resizable().aspectRatio(contentMode: .fill)
                    } else {
                        TryZonTheme.surfaceVariant
                    }
                }
                .frame(height: 180)
                .frame(maxWidth: .infinity)
                .clipped()

                // Gradient Overlay
                LinearGradient(
                    colors: [Color.clear, Color.black.opacity(0.85)],
                    startPoint: .top,
                    endPoint: .bottom
                )
                .frame(height: 180)

                // Content
                VStack(alignment: .leading, spacing: 6) {
                    Text("🔥 #1 VIRAL AI TRY-ON OUTFIT")
                        .font(.system(size: 10, weight: .bold))
                        .foregroundColor(.black)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 4)
                        .background(TryZonTheme.primaryGold)
                        .cornerRadius(100)

                    Text(item.name)
                        .font(.system(size: 18, weight: .bold))
                        .foregroundColor(.white)
                        .lineLimit(1)

                    Text("Photorealistic AI Try-On ready in 2s")
                        .font(.system(size: 12))
                        .foregroundColor(.white.opacity(0.8))
                }
                .padding(16)
            }
            .frame(height: 180)
            .cornerRadius(24)
            .clipped()
            .overlay(RoundedRectangle(cornerRadius: 24).stroke(TryZonTheme.primaryGold.opacity(0.4), lineWidth: 1))
        }
        .buttonStyle(BounceButtonStyle())
    }

    // ─────────────────────────────────────
    // MARK: - PRODUCT CARD REPLICA
    // ─────────────────────────────────────
    private func catalogProductCard(_ product: CatalogItem) -> some View {
        Button(action: { selectedProductDetail = product }) {
            VStack(alignment: .leading, spacing: 0) {
                ZStack(alignment: .topLeading) {
                    AsyncImage(url: product.fullImageURL) { phase in
                        if let image = phase.image {
                            image.resizable().aspectRatio(contentMode: .fill)
                        } else {
                            TryZonTheme.surfaceVariant
                                .overlay(ProgressView().tint(TryZonTheme.primaryGold))
                        }
                    }
                    .frame(height: 210)
                    .frame(maxWidth: .infinity)
                    .clipped()

                    if let badge = product.badge {
                        Text(badge)
                            .font(.system(size: 9, weight: .bold))
                            .foregroundColor(TryZonTheme.primaryGold)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 3)
                            .background(Color.black.opacity(0.75))
                            .cornerRadius(100)
                            .overlay(RoundedRectangle(cornerRadius: 100).stroke(TryZonTheme.primaryGold.opacity(0.4), lineWidth: 1))
                            .padding(8)
                    }
                }

                VStack(alignment: .leading, spacing: 4) {
                    if let brand = product.brand {
                        Text(brand)
                            .font(.system(size: 9.5, weight: .semibold))
                            .foregroundColor(.white.opacity(0.5))
                            .lineLimit(1)
                    }
                    Text(product.name)
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(.white)
                        .lineLimit(1)

                    HStack(spacing: 6) {
                        Text("₹\(product.price)")
                            .font(.system(size: 13, weight: .black))
                            .foregroundColor(TryZonTheme.primaryGold)

                        if let orig = product.original_price, orig > product.price {
                            Text("₹\(orig)")
                                .font(.system(size: 10))
                                .foregroundColor(.white.opacity(0.4))
                                .strikethrough()
                        }
                    }

                    Spacer(minLength: 8)

                    Button(action: { onSelectGarmentForTryOn(product) }) {
                        Text("TRY ON")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundColor(.black)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 8)
                            .background(TryZonTheme.primaryGold)
                            .cornerRadius(100)
                    }
                    .buttonStyle(BounceButtonStyle())
                }
                .padding(10)
            }
            .background(TryZonTheme.surfaceVariant)
            .cornerRadius(18)
            .clipped()
            .overlay(RoundedRectangle(cornerRadius: 18).stroke(Color.white.opacity(0.08), lineWidth: 1))
        }
        .buttonStyle(BounceButtonStyle())
    }

    // ─────────────────────────────────────
    // MARK: - SHIMMER LOADING GRID
    // ─────────────────────────────────────
    private var shimmerLoadingGrid: some View {
        ScrollView {
            LazyVGrid(columns: [GridItem(.flexible(), spacing: 14), GridItem(.flexible(), spacing: 14)], spacing: 14) {
                ForEach(0..<6, id: \.self) { _ in
                    RoundedRectangle(cornerRadius: 18)
                        .fill(TryZonTheme.surfaceVariant)
                        .frame(height: 280)
                        .overlay(ProgressView().tint(TryZonTheme.primaryGold))
                }
            }
            .padding(16)
        }
    }

    // ─────────────────────────────────────
    // MARK: - HELPERS
    // ─────────────────────────────────────
    private var displayedItems: [CatalogItem] {
        if items.isEmpty {
            return sampleCatalogItems
        }
        return items
    }

    private func loadCatalogData() {
        isLoading = true
        Task {
            do {
                let fetched = try await apiClient.fetchCatalog(category: selectedCategory == "All AI Outfits" ? "All" : selectedCategory, gender: selectedGender, search: searchText)
                DispatchQueue.main.async {
                    self.items = fetched
                    self.isLoading = false
                }
            } catch {
                DispatchQueue.main.async {
                    self.isLoading = false
                }
            }
        }
    }

    private var sampleCatalogItems: [CatalogItem] {
        [
            CatalogItem(id: "1", name: "Monaco Riviera Yacht Linen", brand: "TryZon AI Exclusives", price: 2999, original_price: 4499, image_url: "https://tryzonai.com/api/v1/outfits/premium_catalog/ai_premium_mens_italian_riviera_linen_suit.webp", category: "Suits", store: "TryZon AI", gender: "Men", badge: "👑 OLD MONEY"),
            CatalogItem(id: "2", name: "NYC Executive Tuxedo", brand: "TryZon AI Haute Couture", price: 5499, original_price: 8999, image_url: "https://tryzonai.com/api/v1/outfits/premium_catalog/ai_premium_outfit_formal_ceo_black_suit_1778055862174.webp", category: "Suits", store: "TryZon AI", gender: "Men", badge: "💼 EXECUTIVE"),
            CatalogItem(id: "3", name: "Seoul K-Style Teal Dress", brand: "TryZon AI Exclusives", price: 4299, original_price: 6499, image_url: "https://tryzonai.com/api/v1/outfits/premium_catalog/ai_premium_women_teal_knit_dress.webp", category: "Dresses", store: "TryZon AI", gender: "Women", badge: "✨ ELEGANT"),
            CatalogItem(id: "4", name: "Dubai Royal Emerald Velvet", brand: "TryZon AI Haute Couture", price: 5999, original_price: 9499, image_url: "https://tryzonai.com/api/v1/outfits/premium_catalog/ai_premium_outfit_royal_queen_emerald_gold_1778038089053.webp", category: "Dresses", store: "TryZon AI", gender: "Women", badge: "🏆 LUXURY")
        ]
    }
}
