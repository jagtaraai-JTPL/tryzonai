import SwiftUI

public enum TabItem: Int, CaseIterable {
    case tryon = 0
    case catalog = 1
    case wardrobe = 2
    case premium = 3

    var title: String {
        switch self {
        case .tryon: return "Fitting"
        case .catalog: return "Catalog"
        case .wardrobe: return "Wardrobe"
        case .premium: return "VIP Pro"
        }
    }

    var iconName: String {
        switch self {
        case .tryon: return "camera.fill"
        case .catalog: return "tshirt.fill"
        case .wardrobe: return "photo.on.rectangle.angled"
        case .premium: return "crown.fill"
        }
    }
}

public struct MainTabView: View {
    @StateObject private var apiClient = APIClient.shared
    @State private var selectedTab: TabItem = .tryon
    @State private var isDrawerOpen: Bool = false
    @State private var showingAuthSheet: Bool = false

    public init() {}

    public var body: some View {
        ZStack {
            TryZonTheme.darkBackground.ignoresSafeArea()

            VStack(spacing: 0) {
                // Top Bar Header
                TopBar(
                    apiClient: apiClient,
                    onOpenDrawer: { withAnimation { isDrawerOpen = true } },
                    onOpenPremium: { selectedTab = .premium }
                )

                // Tab Content Switcher
                ZStack {
                    switch selectedTab {
                    case .tryon:
                        TryOnUploadView(apiClient: apiClient, onNavigateToResult: { _ in })
                    case .catalog:
                        CatalogView(apiClient: apiClient, onSelectProduct: { item in
                            selectedTab = .tryon
                        })
                    case .wardrobe:
                        WardrobeView(apiClient: apiClient)
                    case .premium:
                        PremiumView(apiClient: apiClient)
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)

                // Custom Floating Gold Bottom Navigation Bar
                HStack(spacing: 0) {
                    ForEach(TabItem.allCases, id: \.self) { tab in
                        Button(action: {
                            withAnimation(.spring(response: 0.3, dampingFraction: 0.7)) {
                                selectedTab = tab
                            }
                        }) {
                            VStack(spacing: 4) {
                                Image(systemName: tab.iconName)
                                    .font(.system(size: 18, weight: selectedTab == tab ? .black : .medium))
                                    .foregroundColor(selectedTab == tab ? TryZonTheme.primaryGold : .white.opacity(0.5))

                                Text(tab.title)
                                    .font(.system(size: 10, weight: selectedTab == tab ? .bold : .regular))
                                    .foregroundColor(selectedTab == tab ? TryZonTheme.primaryGold : .white.opacity(0.5))
                            }
                            .frame(maxWidth: .infinity)
                        }
                    }
                }
                .padding(.vertical, 8)
                .background(TryZonTheme.darkSurface.opacity(0.95))
                .cornerRadius(24)
                .padding(.horizontal, 16)
                .padding(.bottom, 6)
            }

            // Side Drawer Overlay
            SideDrawer(isOpen: $isDrawerOpen, apiClient: apiClient)
        }
        .preferredColorScheme(.dark)
    }
}
