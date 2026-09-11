import SwiftUI

public struct WardrobeView: View {
    @ObservedObject var apiClient: APIClient

    @State private var historyItems: [TryOnHistoryItem] = []
    @State private var isLoading: Bool = false
    @State private var selectedHistoryItem: TryOnHistoryItem? = nil

    public init(apiClient: APIClient) {
        self.apiClient = apiClient
    }

    public var body: some View {
        VStack(spacing: 12) {
            // Header
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("MY WARDROBE CLOSET 🛍️")
                        .font(.system(size: 18, weight: .black, design: .rounded))
                        .foregroundColor(TryZonTheme.primaryGold)

                    Text("Your saved AI virtual outfit fitting history")
                        .font(.system(size: 11))
                        .foregroundColor(.white.opacity(0.7))
                }
                Spacer()
            }
            .padding(.horizontal, 16)
            .padding(.top, 10)

            if isLoading {
                ProgressView()
                    .progressViewStyle(CircularProgressViewStyle(tint: TryZonTheme.primaryGold))
                    .frame(maxHeight: .infinity)
            } else if historyItems.isEmpty {
                VStack(spacing: 12) {
                    Image(systemName: "tshirt.fill")
                        .font(.system(size: 48))
                        .foregroundColor(TryZonTheme.primaryGold.opacity(0.5))

                    Text("Your Closet is Empty")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(.white)

                    Text("Perform your first Virtual Outfit Try-On to save fits to your personal wardrobe!")
                        .font(.system(size: 12))
                        .foregroundColor(.white.opacity(0.6))
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 32)
                }
                .frame(maxHeight: .infinity)
            } else {
                ScrollView {
                    LazyVGrid(columns: [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)], spacing: 12) {
                        ForEach(historyItems) { item in
                            Button(action: { selectedHistoryItem = item }) {
                                VStack(alignment: .leading, spacing: 6) {
                                    AsyncImage(url: item.fullResultURL) { img in
                                        img.resizable().scaledToFill()
                                    } placeholder: {
                                        TryZonTheme.surfaceVariant
                                    }
                                    .frame(height: 190)
                                    .cornerRadius(14)
                                    .clipped()

                                    Text(item.garment_name ?? "Outfit Fitting")
                                        .font(.system(size: 12, weight: .bold))
                                        .foregroundColor(.white)
                                        .lineLimit(1)
                                }
                                .padding(8)
                                .background(TryZonTheme.darkSurface)
                                .cornerRadius(16)
                            }
                        }
                    }
                    .padding(.horizontal, 16)
                }
            }
        }
        .background(TryZonTheme.darkBackground)
        .onAppear {
            loadHistory()
        }
        .sheet(item: $selectedHistoryItem) { item in
            TryOnResultView(resultImageUrl: item.fullResultURL?.absoluteString ?? "", onTryAnother: {
                selectedHistoryItem = nil
            })
        }
    }

    private func loadHistory() {
        isLoading = true
        Task {
            do {
                let fetched = try await apiClient.fetchHistory()
                DispatchQueue.main.async {
                    self.historyItems = fetched
                    self.isLoading = false
                }
            } catch {
                DispatchQueue.main.async {
                    self.isLoading = false
                }
            }
        }
    }
}
