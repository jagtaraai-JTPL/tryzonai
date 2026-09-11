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

    public init() {
        let appearance = UITabBarAppearance()
        appearance.configureWithOpaqueBackground()
        appearance.backgroundColor = UIColor(red: 18/255, green: 18/255, blue: 20/255, alpha: 1.0)

        let goldColor = UIColor(red: 255/255, green: 215/255, blue: 0/255, alpha: 1.0)
        let unselectedColor = UIColor.white.withAlphaComponent(0.45)

        appearance.stackedLayoutAppearance.selected.iconColor = goldColor
        appearance.stackedLayoutAppearance.selected.titleTextAttributes = [.foregroundColor: goldColor]

        appearance.stackedLayoutAppearance.normal.iconColor = unselectedColor
        appearance.stackedLayoutAppearance.normal.titleTextAttributes = [.foregroundColor: unselectedColor]

        UITabBar.appearance().standardAppearance = appearance
        if #available(iOS 15.0, *) {
            UITabBar.appearance().scrollEdgeAppearance = appearance
        }
    }

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

                // Native iOS TabView (100% Touch Responsive across all iPhones & iPads)
                TabView(selection: $selectedTab) {
                    HomeScreen(
                        apiClient: apiClient,
                        onNavigateToTryOn: { selectedTab = .tryon },
                        onNavigateToCatalog: { selectedTab = .catalog }
                    )
                    .tabItem {
                        Label("Home", systemImage: "house.fill")
                    }
                    .tag(TabItem.home)

                    TryOnUploadView(
                        apiClient: apiClient,
                        onNavigateToResult: { _ in }
                    )
                    .tabItem {
                        Label("Fitting", systemImage: "camera.fill")
                    }
                    .tag(TabItem.tryon)

                    CatalogView(
                        apiClient: apiClient,
                        onSelectGarmentForTryOn: { _ in
                            selectedTab = .tryon
                        }
                    )
                    .tabItem {
                        Label("Catalog", systemImage: "tshirt.fill")
                    }
                    .tag(TabItem.catalog)

                    WardrobeView(apiClient: apiClient)
                        .tabItem {
                            Label("Wardrobe", systemImage: "photo.on.rectangle.angled")
                        }
                        .tag(TabItem.wardrobe)

                    PremiumView(apiClient: apiClient)
                        .tabItem {
                            Label("VIP Pro", systemImage: "crown.fill")
                        }
                        .tag(TabItem.premium)
                }
                .accentColor(TryZonTheme.primaryGold)
            }

            // Side Drawer Overlay (Only mounted when isDrawerOpen == true)
            if isDrawerOpen {
                SideDrawer(isOpen: $isDrawerOpen, apiClient: apiClient)
                    .zIndex(100)
            }
        }
        .preferredColorScheme(.dark)
    }
}

