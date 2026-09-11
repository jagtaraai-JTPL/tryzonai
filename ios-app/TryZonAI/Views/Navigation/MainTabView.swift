import SwiftUI

public enum TabItem: Int, CaseIterable {
    case home = 0
    case tryon = 1
    case catalog = 2
    case wardrobe = 3
    case premium = 4

    var title: String {
        switch self {
        case .home: return "Home"
        case .tryon: return "Fitting"
        case .catalog: return "Catalog"
        case .wardrobe: return "Wardrobe"
        case .premium: return "VIP Pro"
        }
    }

    var iconName: String {
        switch self {
        case .home: return "house.fill"
        case .tryon: return "camera.fill"
        case .catalog: return "tshirt.fill"
        case .wardrobe: return "photo.on.rectangle.angled"
        case .premium: return "crown.fill"
        }
    }
}

public struct MainTabView: View {
    @StateObject private var apiClient = APIClient.shared
    @State private var selectedTab: TabItem = .home
    @State private var isDrawerOpen: Bool = false

    public init() {}

    public var body: some View {
        ZStack {
            TryZonTheme.darkBackground.ignoresSafeArea()

            VStack(spacing: 0) {
                // Top Bar Header
                TopBar(
                    apiClient: apiClient,
                    onOpenDrawer: { withAnimation(.spring(response: 0.3)) { isDrawerOpen = true } },
                    onOpenPremium: { withAnimation { selectedTab = .premium } }
                )

                // Tab Content
                ZStack {
                    switch selectedTab {
                    case .home:
                        HomeScreen(
                            apiClient: apiClient,
                            onNavigateToTryOn: {
                                withAnimation(.spring(response: 0.3)) { selectedTab = .tryon }
                            },
                            onNavigateToCatalog: {
                                withAnimation { selectedTab = .catalog }
                            }
                        )

                    case .tryon:
                        TryOnUploadView(
                            apiClient: apiClient,
                            onNavigateToResult: { _ in }
                        )

                    case .catalog:
                        CatalogView(
                            apiClient: apiClient,
                            onSelectGarmentForTryOn: { _ in
                                withAnimation(.spring(response: 0.3)) { selectedTab = .tryon }
                            }
                        )

                    case .wardrobe:
                        WardrobeView(apiClient: apiClient)

                    case .premium:
                        PremiumView(apiClient: apiClient)
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)

                // Custom Gold Bottom Tab Bar
                customTabBar
            }

            // Side Drawer Overlay (Only mounted when isDrawerOpen == true to prevent blocking bottom tabs)
            if isDrawerOpen {
                SideDrawer(isOpen: $isDrawerOpen, apiClient: apiClient)
                    .zIndex(100)
            }
        }
        .preferredColorScheme(.dark)
    }

    // MARK: - Premium Custom Tab Bar (100% Touch Responsive)
    private var customTabBar: some View {
        HStack(spacing: 0) {
            ForEach(TabItem.allCases, id: \.self) { tab in
                Button(action: {
                    withAnimation(.spring(response: 0.3, dampingFraction: 0.7)) {
                        selectedTab = tab
                    }
                }) {
                    VStack(spacing: 4) {
                        ZStack {
                            if selectedTab == tab {
                                RoundedRectangle(cornerRadius: 8)
                                    .fill(TryZonTheme.primaryGold.opacity(0.18))
                                    .frame(width: 44, height: 30)
                            }

                            Image(systemName: tab.iconName)
                                .font(.system(
                                    size: selectedTab == tab ? 19 : 17,
                                    weight: selectedTab == tab ? .black : .medium
                                ))
                                .foregroundColor(
                                    selectedTab == tab ? TryZonTheme.primaryGold : .white.opacity(0.45)
                                )
                                .scaleEffect(selectedTab == tab ? 1.1 : 1.0)
                        }
                        .frame(height: 30)

                        Text(tab.title)
                            .font(.system(
                                size: 9.5,
                                weight: selectedTab == tab ? .black : .regular
                            ))
                            .foregroundColor(
                                selectedTab == tab ? TryZonTheme.primaryGold : .white.opacity(0.45)
                            )

                        // Active indicator dot
                        Circle()
                            .fill(TryZonTheme.primaryGold)
                            .frame(width: 4, height: 4)
                            .opacity(selectedTab == tab ? 1 : 0)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 6)
                    .contentShape(Rectangle())
                }
                .buttonStyle(PlainButtonStyle())
            }
        }
        .padding(.bottom, max(8, safeAreaBottomInset))
        .background(
            ZStack(alignment: .top) {
                TryZonTheme.darkSurface
                    .opacity(0.98)
                    .ignoresSafeArea(edges: .bottom)

                // Shimmer top border line
                LinearGradient(
                    colors: [
                        TryZonTheme.primaryGold.opacity(0.6),
                        TryZonTheme.primaryGold.opacity(0.2),
                        Color.clear
                    ],
                    startPoint: .leading,
                    endPoint: .trailing
                )
                .frame(height: 1)
            }
        )
    }

    private var safeAreaBottomInset: CGFloat {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first?.windows.first?
            .safeAreaInsets.bottom ?? 0
    }
}
