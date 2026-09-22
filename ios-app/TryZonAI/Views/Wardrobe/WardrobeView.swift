import SwiftUI

// MARK: - Result Preview Wrapper for Identifiable Sheet Presentation
public struct ResultPreviewItem: Identifiable {
    public let id = UUID()
    public let url: String

    public init(url: String) {
        self.url = url
    }
}

// MARK: - WardrobeView — 100% 1:1 Replica of Android ClosetScreen.kt
public struct WardrobeView: View {
    @ObservedObject var apiClient: APIClient

    @State private var savedItems: [WardrobeItemModel] = []
    @State private var historyItems: [TryOnHistoryItem] = []
    @State private var isLoading: Bool = false
    @State private var showCompareModal: Bool = false
    @State private var showFullHistorySheet: Bool = false
    @State private var showLoginSheet: Bool = false
    @State private var showPremiumSheet: Bool = false
    @State private var selectedPreviewResultItem: ResultPreviewItem? = nil

    public init(apiClient: APIClient) {
        self.apiClient = apiClient
    }

    private var currentUser: UserProfile? {
        apiClient.currentUser
    }

    private var isLoggedIn: Bool {
        apiClient.isLoggedIn && currentUser != nil
    }

    private var isPro: Bool {
        currentUser?.isPremium == true
    }

    public var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(spacing: 16) {
                // ── 1. HEADER TITLE ──
                HStack(alignment: .center) {
                    VStack(alignment: .leading, spacing: 3) {
                        Text("My Account & Closet")
                            .font(.system(size: 20, weight: .bold, design: .rounded))
                            .foregroundColor(.white)

                        Text("Personal dashboard & saved fashion looks")
                            .font(.system(size: 11.5))
                            .foregroundColor(.white.opacity(0.65))
                    }

                    Spacer()

                    ZStack {
                        Circle()
                            .fill(TryZonTheme.primaryGold.opacity(0.15))
                            .frame(width: 38, height: 38)
                        Image(systemName: "tshirt.fill")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(TryZonTheme.primaryGold)
                    }
                }
                .padding(.horizontal, 20)
                .padding(.top, 14)

                if !isLoggedIn {
                    // ── LOGGED OUT STATE ──
                    loggedOutCard
                } else {
                    // ── 2. PROFILE SUMMARY CARD ──
                    profileSummaryCard

                    // ── 3. SAVED CLOSET LOOKS SECTION ──
                    savedClosetSection

                    // ── 4. QUICK ACCESS LINKS SECTION ──
                    quickAccessLinksSection
                }
            }
            .padding(.bottom, 100)
        }
        .background(TryZonTheme.darkBackground.ignoresSafeArea())
        .onAppear {
            loadData()
        }
        .sheet(isPresented: $showCompareModal) {
            CompareOutfitsModal(items: savedItems, isPresented: $showCompareModal)
        }
        .sheet(isPresented: $showFullHistorySheet) {
            FullHistorySheet(historyItems: historyItems, apiClient: apiClient)
        }
        .sheet(isPresented: $showLoginSheet) {
            LoginView(apiClient: apiClient, onNavigateToRegister: {})
        }
        .sheet(isPresented: $showPremiumSheet) {
            PremiumView(apiClient: apiClient)
        }
        .sheet(item: $selectedPreviewResultItem) { preview in
            TryOnResultView(resultImageUrl: preview.url, onTryAnother: {
                selectedPreviewResultItem = nil
            })
        }
    }

    // ─────────────────────────────────────
    // MARK: - LOGGED OUT CARD
    // ─────────────────────────────────────
    private var loggedOutCard: some View {
        VStack(spacing: 16) {
            ZStack {
                Circle()
                    .fill(TryZonTheme.primaryGold.opacity(0.15))
                    .frame(width: 64, height: 64)
                Image(systemName: "lock.fill")
                    .font(.system(size: 28))
                    .foregroundColor(TryZonTheme.primaryGold)
            }

            Text("Sign In to Access Your Dashboard")
                .font(.system(size: 16, weight: .bold))
                .foregroundColor(.white)

            Text("Sync your credits, subscription status, and saved outfits across all devices.")
                .font(.system(size: 12))
                .foregroundColor(.white.opacity(0.65))
                .multilineTextAlignment(.center)
                .padding(.horizontal, 16)

            Button(action: { showLoginSheet = true }) {
                Text("SIGN IN / REGISTER")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(.black)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(TryZonTheme.primaryGold)
                    .cornerRadius(100)
            }
        }
        .padding(24)
        .background(TryZonTheme.surfaceVariant.opacity(0.4))
        .cornerRadius(20)
        .overlay(RoundedRectangle(cornerRadius: 20).stroke(Color.white.opacity(0.1), lineWidth: 1))
        .padding(.horizontal, 20)
    }

    // ─────────────────────────────────────
    // MARK: - PROFILE SUMMARY CARD
    // ─────────────────────────────────────
    private var profileSummaryCard: some View {
        VStack(spacing: 14) {
            HStack(spacing: 14) {
                // Profile Avatar
                ZStack {
                    Circle()
                        .fill(TryZonTheme.primaryGold.opacity(0.2))
                        .frame(width: 48, height: 48)
                        .overlay(Circle().stroke(TryZonTheme.primaryGold, lineWidth: 1))
                    Image(systemName: "person.fill")
                        .font(.system(size: 22))
                        .foregroundColor(TryZonTheme.primaryGold)
                }

                VStack(alignment: .leading, spacing: 3) {
                    Text(currentUser?.name ?? currentUser?.username ?? "Account")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(.white)

                    Text(isPro ? "Pro Subscriber 👑" : "Free Plan Account")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(TryZonTheme.primaryGold)
                }
                Spacer()
            }

            Divider().background(Color.white.opacity(0.1))

            // Usage & Credits Summary Row
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Available Balance")
                        .font(.system(size: 11))
                        .foregroundColor(.white.opacity(0.6))

                    let paid = currentUser?.paidCredits ?? apiClient.paidCredits
                    let bonus = currentUser?.bonusCredits ?? apiClient.bonusCredits
                    Text("\(paid + bonus) Credits")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundColor(.white)
                }

                Spacer()

                VStack(alignment: .trailing, spacing: 2) {
                    Text("Today's Free Usage")
                        .font(.system(size: 11))
                        .foregroundColor(.white.opacity(0.6))

                    let credits = currentUser?.credits ?? apiClient.userCredits
                    let usageText = credits <= 0 ? "1 / 1 Free Used" : "0 / 1 Free Used"
                    Text(usageText)
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(TryZonTheme.primaryGold)
                }
            }

            Button(action: { showPremiumSheet = true }) {
                HStack(spacing: 6) {
                    Image(systemName: "crown.fill")
                        .font(.system(size: 14))
                    Text("VIEW PLANS & CREDITS")
                        .font(.system(size: 12, weight: .bold))
                }
                .foregroundColor(.black)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 10)
                .background(TryZonTheme.primaryGold)
                .cornerRadius(100)
            }
        }
        .padding(16)
        .background(TryZonTheme.surfaceVariant.opacity(0.45))
        .cornerRadius(20)
        .overlay(RoundedRectangle(cornerRadius: 20).stroke(TryZonTheme.primaryGold.opacity(0.25), lineWidth: 1))
        .padding(.horizontal, 20)
    }

    // ─────────────────────────────────────
    // MARK: - SAVED CLOSET LOOKS SECTION
    // ─────────────────────────────────────
    private var savedClosetSection: some View {
        VStack(spacing: 12) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("My Saved Closet")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(.white)

                    Text("\(savedItems.count) favorite outfits saved")
                        .font(.system(size: 11))
                        .foregroundColor(.white.opacity(0.6))
                }

                Spacer()

                if savedItems.count >= 2 {
                    Button(action: { showCompareModal = true }) {
                        HStack(spacing: 4) {
                            Image(systemName: "arrow.left.and.right.square.fill")
                                .font(.system(size: 12))
                            Text("VERSUS ⚔️")
                                .font(.system(size: 10, weight: .bold))
                        }
                        .foregroundColor(TryZonTheme.primaryGold)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 5)
                        .overlay(RoundedRectangle(cornerRadius: 100).stroke(TryZonTheme.primaryGold, lineWidth: 1))
                    }
                }
            }
            .padding(.horizontal, 20)

            if savedItems.isEmpty {
                VStack(spacing: 10) {
                    Image(systemName: "heart.slash.fill")
                        .font(.system(size: 36))
                        .foregroundColor(TryZonTheme.primaryGold.opacity(0.6))

                    Text("No Saved Outfits Yet")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(.white)

                    Text("Tap the ❤️ heart icon on any Try-On result to save your favorite transformations here!")
                        .font(.system(size: 11))
                        .foregroundColor(.white.opacity(0.6))
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 24)
                }
                .padding(20)
                .frame(maxWidth: .infinity)
                .background(TryZonTheme.surfaceVariant.opacity(0.3))
                .cornerRadius(16)
                .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color.white.opacity(0.08), lineWidth: 1))
                .padding(.horizontal, 20)
            } else {
                LazyVGrid(columns: [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)], spacing: 12) {
                    ForEach(savedItems) { item in
                        ZStack(alignment: .topTrailing) {
                            Button(action: {
                                if let urlStr = item.fullImageURL?.absoluteString {
                                    selectedPreviewResultItem = ResultPreviewItem(url: urlStr)
                                }
                            }) {
                                AsyncImage(url: item.fullImageURL) { phase in
                                    if let img = phase.image {
                                        img.resizable().aspectRatio(contentMode: .fill)
                                    } else {
                                        TryZonTheme.surfaceVariant
                                    }
                                }
                                .frame(height: 190)
                                .frame(maxWidth: .infinity)
                                .cornerRadius(16)
                                .clipped()
                                .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color.white.opacity(0.1), lineWidth: 1))
                            }

                            // Delete button
                            Button(action: { removeSavedItem(item) }) {
                                ZStack {
                                    Circle()
                                        .fill(Color.black.opacity(0.65))
                                        .frame(width: 28, height: 28)
                                    Image(systemName: "trash.fill")
                                        .font(.system(size: 12))
                                        .foregroundColor(.red)
                                }
                            }
                            .padding(8)
                        }
                    }
                }
                .padding(.horizontal, 20)
            }
        }
    }

    // ─────────────────────────────────────
    // MARK: - QUICK ACCESS LINKS SECTION
    // ─────────────────────────────────────
    private var quickAccessLinksSection: some View {
        VStack(spacing: 0) {
            Button(action: { showFullHistorySheet = true }) {
                HStack(spacing: 12) {
                    Image(systemName: "clock.arrow.circlepath")
                        .font(.system(size: 18))
                        .foregroundColor(TryZonTheme.primaryGold)

                    VStack(alignment: .leading, spacing: 2) {
                        Text("Full Try-On History")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundColor(.white)
                        Text("View all past AI outfit transformations")
                            .font(.system(size: 11))
                            .foregroundColor(.white.opacity(0.6))
                    }

                    Spacer()

                    Image(systemName: "chevron.right")
                        .font(.system(size: 13))
                        .foregroundColor(.white.opacity(0.4))
                }
                .padding(14)
            }

            Divider().background(Color.white.opacity(0.08)).padding(.horizontal, 14)

            Button(action: {
                if let url = URL(string: "https://apps.apple.com/account/subscriptions") {
                    UIApplication.shared.open(url)
                }
            }) {
                HStack(spacing: 12) {
                    Image(systemName: "doc.plaintext.fill")
                        .font(.system(size: 18))
                        .foregroundColor(TryZonTheme.primaryGold)

                    VStack(alignment: .leading, spacing: 2) {
                        Text("Purchase Receipts & Subscriptions")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundColor(.white)
                        Text("View past Apple Store transactions")
                            .font(.system(size: 11))
                            .foregroundColor(.white.opacity(0.6))
                    }

                    Spacer()

                    Image(systemName: "chevron.right")
                        .font(.system(size: 13))
                        .foregroundColor(.white.opacity(0.4))
                }
                .padding(14)
            }
        }
        .background(TryZonTheme.surfaceVariant.opacity(0.35))
        .cornerRadius(16)
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color.white.opacity(0.08), lineWidth: 1))
        .padding(.horizontal, 20)
    }

    // ─────────────────────────────────────
    // MARK: - DATA LOADING & ACTIONS
    // ─────────────────────────────────────
    private func loadData() {
        isLoading = true
        Task {
            async let closetTask = (try? apiClient.fetchWardrobeItems()) ?? []
            async let historyTask = (try? apiClient.fetchHistory()) ?? []
            let closet = await closetTask
            let history = await historyTask
            DispatchQueue.main.async {
                self.savedItems = closet
                self.historyItems = history
                self.isLoading = false
            }
        }
    }

    private func removeSavedItem(_ item: WardrobeItemModel) {
        withAnimation {
            savedItems.removeAll(where: { $0.id == item.id })
        }
        Task {
            try? await apiClient.deleteWardrobeItem(id: item.id)
        }
    }
}

// ─────────────────────────────────────
// MARK: - COMPARE OUTFITS MODAL (VERSUS ⚔️)
// ─────────────────────────────────────
public struct CompareOutfitsModal: View {
    let items: [WardrobeItemModel]
    @Binding var isPresented: Bool

    @State private var indexA: Int = 0
    @State private var indexB: Int = 1

    public var body: some View {
        ZStack {
            TryZonTheme.darkBackground.ignoresSafeArea()

            VStack(spacing: 16) {
                // Modal Header
                HStack {
                    Text("OUTFIT VERSUS ⚔️")
                        .font(.system(size: 16, weight: .black, design: .rounded))
                        .foregroundColor(.white)

                    Spacer()

                    Button(action: { isPresented = false }) {
                        Image(systemName: "xmark.circle.fill")
                            .font(.title3)
                            .foregroundColor(.white.opacity(0.6))
                    }
                }

                Text("Compare 2 outfits side-by-side to pick your best look!")
                    .font(.system(size: 11.5))
                    .foregroundColor(.white.opacity(0.7))

                if items.count >= 2 {
                    let itemA = items[min(indexA, items.count - 1)]
                    let itemB = items[min(indexB, items.count - 1)]

                    HStack(spacing: 10) {
                        // Style A Card
                        VStack(spacing: 6) {
                            ZStack(alignment: .topLeading) {
                                AsyncImage(url: itemA.fullImageURL) { phase in
                                    if let img = phase.image {
                                        img.resizable().aspectRatio(contentMode: .fill)
                                    } else {
                                        TryZonTheme.surfaceVariant
                                    }
                                }
                                .frame(height: 240)
                                .cornerRadius(18)
                                .clipped()
                                .overlay(RoundedRectangle(cornerRadius: 18).stroke(TryZonTheme.primaryGold, lineWidth: 2))

                                Text("STYLE A")
                                    .font(.system(size: 10, weight: .black))
                                    .foregroundColor(TryZonTheme.primaryGold)
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 4)
                                    .background(Color.black.opacity(0.75))
                                    .cornerRadius(100)
                                    .padding(8)
                            }

                            Picker("Style A", selection: $indexA) {
                                ForEach(0..<items.count, id: \.self) { i in
                                    Text("Outfit #\(i + 1)").tag(i)
                                }
                            }
                            .pickerStyle(MenuPickerStyle())
                            .accentColor(TryZonTheme.primaryGold)
                        }

                        // VS Badge
                        ZStack {
                            Circle()
                                .fill(TryZonTheme.primaryGold)
                                .frame(width: 32, height: 32)
                            Text("VS")
                                .font(.system(size: 11, weight: .black))
                                .foregroundColor(.black)
                        }

                        // Style B Card
                        VStack(spacing: 6) {
                            ZStack(alignment: .topLeading) {
                                AsyncImage(url: itemB.fullImageURL) { phase in
                                    if let img = phase.image {
                                        img.resizable().aspectRatio(contentMode: .fill)
                                    } else {
                                        TryZonTheme.surfaceVariant
                                    }
                                }
                                .frame(height: 240)
                                .cornerRadius(18)
                                .clipped()
                                .overlay(RoundedRectangle(cornerRadius: 18).stroke(Color.green, lineWidth: 2))

                                Text("STYLE B")
                                    .font(.system(size: 10, weight: .black))
                                    .foregroundColor(.green)
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 4)
                                    .background(Color.black.opacity(0.75))
                                    .cornerRadius(100)
                                    .padding(8)
                            }

                            Picker("Style B", selection: $indexB) {
                                ForEach(0..<items.count, id: \.self) { i in
                                    Text("Outfit #\(i + 1)").tag(i)
                                }
                            }
                            .pickerStyle(MenuPickerStyle())
                            .accentColor(Color.green)
                        }
                    }
                }

                Spacer()
            }
            .padding(20)
        }
    }
}

// ─────────────────────────────────────
// MARK: - FULL HISTORY SHEET
// ─────────────────────────────────────
public struct FullHistorySheet: View {
    let historyItems: [TryOnHistoryItem]
    @ObservedObject var apiClient: APIClient

    @Environment(\.dismiss) private var dismiss
    @State private var selectedHistoryItem: TryOnHistoryItem? = nil

    public var body: some View {
        ZStack {
            TryZonTheme.darkBackground.ignoresSafeArea()

            VStack(spacing: 14) {
                HStack {
                    Text("FULL TRY-ON HISTORY 📜")
                        .font(.system(size: 16, weight: .bold, design: .rounded))
                        .foregroundColor(TryZonTheme.primaryGold)

                    Spacer()

                    Button(action: { dismiss() }) {
                        Image(systemName: "xmark.circle.fill")
                            .font(.title3)
                            .foregroundColor(.white.opacity(0.6))
                    }
                }
                .padding(.horizontal, 20)
                .padding(.top, 16)

                if historyItems.isEmpty {
                    VStack(spacing: 12) {
                        Image(systemName: "clock")
                            .font(.system(size: 40))
                            .foregroundColor(.white.opacity(0.4))
                        Text("No try-on history found")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(.white)
                    }
                    .frame(maxHeight: .infinity)
                } else {
                    ScrollView {
                        LazyVGrid(columns: [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)], spacing: 12) {
                            ForEach(historyItems) { item in
                                Button(action: { selectedHistoryItem = item }) {
                                    VStack(alignment: .leading, spacing: 6) {
                                        AsyncImage(url: item.fullResultURL) { img in
                                            img.resizable().aspectRatio(contentMode: .fill)
                                        } placeholder: {
                                            TryZonTheme.surfaceVariant
                                        }
                                        .frame(height: 190)
                                        .cornerRadius(14)
                                        .clipped()

                                        Text(item.garment_name ?? "AI Fitting Result")
                                            .font(.system(size: 11, weight: .bold))
                                            .foregroundColor(.white)
                                            .lineLimit(1)
                                    }
                                    .padding(8)
                                    .background(TryZonTheme.surfaceVariant.opacity(0.4))
                                    .cornerRadius(16)
                                }
                            }
                        }
                        .padding(.horizontal, 20)
                    }
                }
            }
        }
        .sheet(item: $selectedHistoryItem) { item in
            TryOnResultView(resultImageUrl: item.fullResultURL?.absoluteString ?? "", onTryAnother: {
                selectedHistoryItem = nil
            })
        }
    }
}
