import SwiftUI

public struct CatalogView: View {
    @ObservedObject var apiClient: APIClient
    let onSelectProduct: (CatalogItem) -> Void

    @State private var selectedCategory: String = "All"
    @AppStorage("user_gender") private var userGender: String = "Women"
    @State private var catalogItems: [CatalogItem] = []
    @State private var isLoading: Bool = false

    private let categories = ["All", "Tops", "Dresses", "Bottoms", "Outerwear"]

    public init(apiClient: APIClient, onSelectProduct: @escaping (CatalogItem) -> Void) {
        self.apiClient = apiClient
        self.onSelectProduct = onSelectProduct
    }

    public var body: some View {
        VStack(spacing: 12) {
            // Category & Gender Filter Bar
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(categories, id: \.self) { cat in
                        Button(action: { selectedCategory = cat }) {
                            Text(cat)
                                .font(.system(size: 12, weight: .bold))
                                .padding(.horizontal, 14)
                                .padding(.vertical, 8)
                                .background(selectedCategory == cat ? TryZonTheme.primaryGold : TryZonTheme.surfaceVariant)
                                .foregroundColor(selectedCategory == cat ? .black : .white)
                                .cornerRadius(20)
                        }
                    }
                }
                .padding(.horizontal, 16)
            }

            // Catalog Grid
            if isLoading {
                ProgressView()
                    .progressViewStyle(CircularProgressViewStyle(tint: TryZonTheme.primaryGold))
                    .frame(maxHeight: .infinity)
            } else {
                ScrollView {
                    LazyVGrid(columns: [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)], spacing: 12) {
                        ForEach(sampleCatalogItems.filter { item in
                            (selectedCategory == "All" || item.category.lowercased() == selectedCategory.lowercased())
                        }) { item in
                            VStack(alignment: .leading, spacing: 6) {
                                ZStack(alignment: .topTrailing) {
                                    AsyncImage(url: URL(string: item.image_url)) { image in
                                        image
                                            .resizable()
                                            .scaledToFill()
                                    } placeholder: {
                                        Color(TryZonTheme.surfaceVariant)
                                    }
                                    .frame(height: 200)
                                    .cornerRadius(16)
                                    .clipped()

                                    Text("⚡ Try On")
                                        .font(.system(size: 10, weight: .bold))
                                        .padding(.horizontal, 8)
                                        .padding(.vertical, 4)
                                        .background(TryZonTheme.primaryGold)
                                        .foregroundColor(.black)
                                        .cornerRadius(8)
                                        .padding(8)
                                }

                                Text(item.name)
                                    .font(.system(size: 13, weight: .bold))
                                    .foregroundColor(.white)
                                    .lineLimit(1)

                                HStack {
                                    if let price = item.price_inr {
                                        Text("₹\(price)")
                                            .font(.system(size: 12, weight: .bold))
                                            .foregroundColor(TryZonTheme.primaryGold)
                                    }
                                    Spacer()
                                    Text(item.brand ?? "Fashion")
                                        .font(.system(size: 10))
                                        .foregroundColor(.white.opacity(0.5))
                                }
                            }
                            .padding(8)
                            .background(TryZonTheme.darkSurface)
                            .cornerRadius(18)
                            .overlay(
                                RoundedRectangle(cornerRadius: 18)
                                    .stroke(TryZonTheme.cardBorder, lineWidth: 1)
                            )
                            .onTapGesture {
                                onSelectProduct(item)
                            }
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.bottom, 20)
                }
            }
        }
        .background(TryZonTheme.darkBackground)
    }

    private var sampleCatalogItems: [CatalogItem] {
        return [
            CatalogItem(product_id: "c1", name: "Floral Summer Sundress", category: "Dresses", image_url: "https://images.unsplash.com/photo-1515372039744-b8f02a3ae446?w=500", price_inr: 1299, store_url: "https://myntr.it/sQ4bpfb", brand: "Zara", gender: "Women"),
            CatalogItem(product_id: "c2", name: "Classic Denim Jacket", category: "Outerwear", image_url: "https://images.unsplash.com/photo-1544441893-675973e31985?w=500", price_inr: 2499, store_url: "https://myntr.it/sQ4bpfb", brand: "Levi's", gender: "Women"),
            CatalogItem(product_id: "c3", name: "Silk Satin Blouse", category: "Tops", image_url: "https://images.unsplash.com/photo-1564257631407-4deb1f99d992?w=500", price_inr: 1799, store_url: "https://myntr.it/sQ4bpfb", brand: "H&M", gender: "Women"),
            CatalogItem(product_id: "c4", name: "High-Waist Trousers", category: "Bottoms", image_url: "https://images.unsplash.com/photo-1509631179647-0177331693ae?w=500", price_inr: 1999, store_url: "https://myntr.it/sQ4bpfb", brand: "Mango", gender: "Women"),
            CatalogItem(product_id: "c5", name: "Elegance Evening Gown", category: "Dresses", image_url: "https://images.unsplash.com/photo-1566174053879-31528523f8ae?w=500", price_inr: 3499, store_url: "https://myntr.it/sQ4bpfb", brand: "Forever New", gender: "Women"),
            CatalogItem(product_id: "c6", name: "Casual Cotton Tee", category: "Tops", image_url: "https://images.unsplash.com/photo-1521572267360-ee0c2909d518?w=500", price_inr: 799, store_url: "https://myntr.it/sQ4bpfb", brand: "Uniqlo", gender: "Women")
        ]
    }
}
