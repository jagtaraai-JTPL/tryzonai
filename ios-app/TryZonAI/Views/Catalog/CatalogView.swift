import SwiftUI

public struct CatalogView: View {
    @ObservedObject var apiClient: APIClient
    let onSelectGarmentForTryOn: (CatalogItem) -> Void

    @State private var selectedCategory: String = "All"
    @AppStorage("user_gender") private var selectedGender: String = "Women"
    @State private var searchText: String = ""
    @State private var items: [CatalogItem] = []
    @State private var isLoading: Bool = false
    @State private var errorMessage: String? = nil

    private let categories = ["All", "Tops", "Bottoms", "Dresses", "Suits", "Outerwear"]

    public init(apiClient: APIClient, onSelectGarmentForTryOn: @escaping (CatalogItem) -> Void) {
        self.apiClient = apiClient
        self.onSelectGarmentForTryOn = onSelectGarmentForTryOn
    }

    public var body: some View {
        VStack(spacing: 12) {
            // Search Bar & Gender Filter Pill
            HStack(spacing: 10) {
                HStack {
                    Image(systemName: "magnifyingglass")
                        .foregroundColor(TryZonTheme.primaryGold)
                    TextField("Search outfits, brands...", text: $searchText)
                        .font(.system(size: 13))
                        .foregroundColor(.white)
                        .onSubmit {
                            loadCatalogData()
                        }
                }
                .padding(10)
                .background(TryZonTheme.surfaceVariant)
                .cornerRadius(12)

                // Gender Selector Pill
                Menu {
                    Button("Women") { selectedGender = "Women"; loadCatalogData() }
                    Button("Men") { selectedGender = "Men"; loadCatalogData() }
                    Button("Unisex") { selectedGender = "Unisex"; loadCatalogData() }
                } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "figure.dress.line.vertical.figure")
                        Text(selectedGender)
                    }
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(.black)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 10)
                    .background(TryZonTheme.primaryGold)
                    .cornerRadius(12)
                }
            }
            .padding(.horizontal, 16)

            // Category Selector Tabs
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(categories, id: \.self) { cat in
                        Button(action: {
                            selectedCategory = cat
                            loadCatalogData()
                        }) {
                            Text(cat)
                                .font(.system(size: 12, weight: .bold))
                                .padding(.horizontal, 14)
                                .padding(.vertical, 7)
                                .background(selectedCategory == cat ? TryZonTheme.primaryGold : TryZonTheme.surfaceVariant)
                                .foregroundColor(selectedCategory == cat ? .black : .white)
                                .cornerRadius(18)
                        }
                    }
                }
                .padding(.horizontal, 16)
            }

            // Main Catalog Grid
            if isLoading {
                ProgressView()
                    .progressViewStyle(CircularProgressViewStyle(tint: TryZonTheme.primaryGold))
                    .frame(maxHeight: .infinity)
            } else {
                ScrollView {
                    LazyVGrid(columns: [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)], spacing: 12) {
                        ForEach(displayedItems) { item in
                            VStack(alignment: .leading, spacing: 6) {
                                ZStack(alignment: .topTrailing) {
                                    AsyncImage(url: item.fullImageURL) { img in
                                        img.resizable().scaledToFill()
                                    } placeholder: {
                                        TryZonTheme.surfaceVariant
                                    }
                                    .frame(height: 200)
                                    .cornerRadius(16)
                                    .clipped()

                                    if let badge = item.badge {
                                        Text(badge)
                                            .font(.system(size: 9, weight: .bold))
                                            .padding(.horizontal, 8)
                                            .padding(.vertical, 4)
                                            .background(TryZonTheme.primaryGold)
                                            .foregroundColor(.black)
                                            .cornerRadius(6)
                                            .padding(8)
                                    }
                                }

                                Text(item.name)
                                    .font(.system(size: 13, weight: .bold))
                                    .foregroundColor(.white)
                                    .lineLimit(1)

                                HStack {
                                    Text("₹\(item.price)")
                                        .font(.system(size: 13, weight: .black))
                                        .foregroundColor(TryZonTheme.primaryGold)

                                    Spacer()

                                    // 1-Tap Try On Button
                                    Button(action: {
                                        onSelectGarmentForTryOn(item)
                                    }) {
                                        HStack(spacing: 4) {
                                            Image(systemName: "sparkles")
                                            Text("Try On")
                                        }
                                        .font(.system(size: 11, weight: .bold))
                                        .padding(.horizontal, 10)
                                        .padding(.vertical, 6)
                                        .background(TryZonTheme.primaryGold)
                                        .foregroundColor(.black)
                                        .cornerRadius(10)
                                    }
                                }
                            }
                            .padding(10)
                            .background(TryZonTheme.darkSurface)
                            .cornerRadius(16)
                        }
                    }
                    .padding(.horizontal, 16)
                }
            }
        }
        .background(TryZonTheme.darkBackground)
        .onAppear {
            loadCatalogData()
        }
    }

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
                let fetched = try await apiClient.fetchCatalog(category: selectedCategory, gender: selectedGender, search: searchText)
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
            CatalogItem(id: "1", name: "Monaco Riviera Linen Blazer", brand: "TryZon AI", price: 2999, original_price: 4999, image_url: "/inputs/sample1.jpg", category: "Suits", store: "TryZon AI", gender: "Men", badge: "👑 OLD MONEY"),
            CatalogItem(id: "2", name: "NYC Fifth Ave Evening Gown", brand: "TryZon AI", price: 4499, original_price: 6999, image_url: "/inputs/sample2.jpg", category: "Dresses", store: "TryZon AI", gender: "Women", badge: "✨ ELEGANT"),
            CatalogItem(id: "3", name: "Tokyo Cyberpunk Jacket", brand: "TryZon AI", price: 3499, original_price: 5499, image_url: "/inputs/sample3.jpg", category: "Tops", store: "TryZon AI", gender: "Unisex", badge: "⚡ NEON AI"),
            CatalogItem(id: "4", name: "Seoul Black Slim Tuxedo", brand: "TryZon AI", price: 5299, original_price: 7999, image_url: "/inputs/sample4.jpg", category: "Suits", store: "TryZon AI", gender: "Men", badge: "🫰 K-STYLE")
        ]
    }
}
