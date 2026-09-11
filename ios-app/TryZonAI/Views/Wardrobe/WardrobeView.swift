import SwiftUI

public struct WardrobeView: View {
    @ObservedObject var apiClient: APIClient
    @State private var historyItems: [TryOnHistoryItem] = []
    @State private var isLoading: Bool = false
    @State private var selectedItem: TryOnHistoryItem? = nil

    public init(apiClient: APIClient) {
        self.apiClient = apiClient
    }

    public var body: some View {
        VStack(spacing: 12) {
            // Header
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("My Virtual Wardrobe 🖼️")
                        .font(.system(size: 20, weight: .bold))
                        .foregroundColor(.white)
                    Text("Your saved AI outfit fitting history")
                        .font(.system(size: 12))
                        .foregroundColor(.white.opacity(0.6))
                }
                Spacer()
            }
            .padding(.horizontal, 16)
            .padding(.top, 8)

            if isLoading {
                ProgressView()
                    .progressViewStyle(CircularProgressViewStyle(tint: TryZonTheme.primaryGold))
                    .frame(maxHeight: .infinity)
            } else if historyItems.isEmpty {
                VStack(spacing: 12) {
                    Image(systemName: "photo.on.rectangle.angled")
                        .font(.system(size: 48))
                        .foregroundColor(TryZonTheme.primaryGold.opacity(0.6))
                    Text("No Try-Ons Saved Yet")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(.white)
                    Text("Generate your 1st virtual fitting to save outfits to your wardrobe!")
                        .font(.system(size: 12))
                        .foregroundColor(.white.opacity(0.6))
                        .multilineTextAlignment(.center)
                }
                .padding(32)
                .frame(maxHeight: .infinity)
            } else {
                ScrollView {
                    LazyVGrid(columns: [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)], spacing: 12) {
                        ForEach(historyItems) { item in
                            VStack(alignment: .leading, spacing: 6) {
                                AsyncImage(url: URL(string: item.result_image)) { img in
                                    img.resizable().scaledToFill()
                                } placeholder: {
                                    Color(TryZonTheme.surfaceVariant)
                                }
                                .frame(height: 180)
                                .cornerRadius(14)
                                .clipped()

                                Text(item.category ?? "Outfit Fitting")
                                    .font(.system(size: 12, weight: .bold))
                                    .foregroundColor(.white)
                            }
                            .padding(8)
                            .background(TryZonTheme.darkSurface)
                            .cornerRadius(16)
                            .onTapGesture {
                                selectedItem = item
                            }
                        }
                    }
                    .padding(.horizontal, 16)
                }
            }
        }
        .background(TryZonTheme.darkBackground)
        .onAppear {
            fetchHistory()
        }
    }

    private func fetchHistory() {
        isLoading = true
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
            self.historyItems = [
                TryOnHistoryItem(task_id: "w1", result_image: "https://images.unsplash.com/photo-1515372039744-b8f02a3ae446?w=500", original_photo: "", category: "Floral Sundress", created_at: "Today", is_favorite: true),
                TryOnHistoryItem(task_id: "w2", result_image: "https://images.unsplash.com/photo-1544441893-675973e31985?w=500", original_photo: "", category: "Denim Jacket", created_at: "Yesterday", is_favorite: false)
            ]
            self.isLoading = false
        }
    }
}
