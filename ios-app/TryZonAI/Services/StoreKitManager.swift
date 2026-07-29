import Foundation
import StoreKit

@MainActor
public class StoreKitManager: ObservableObject {
    public static let shared = StoreKitManager()

    @Published public var products: [Product] = []
    @Published public var purchasedProductIDs = Set<String>()
    @Published public var isLoading: Bool = false
    @Published public var statusMessage: String?

    private let productIDs: [String] = [
        "credits_pocket",
        "credits_starter",
        "credits_value",
        "credits_business",
        "credits_enterprise",
        "sub_weekly_pro",
        "sub_monthly_pro",
        "sub_yearly_legend"
    ]

    private var updates: Task<Void, Never>? = nil

    private init() {
        updates = observeTransactionUpdates()
        Task {
            await fetchProducts()
            await updatePurchasedProducts()
        }
    }

    deinit {
        updates?.cancel()
    }

    // MARK: - Fetch App Store Products
    public func fetchProducts() async {
        isLoading = true
        do {
            let storeProducts = try await Product.products(for: productIDs)
            self.products = storeProducts
            isLoading = false
        } catch {
            print("Failed to fetch StoreKit products: \(error)")
            isLoading = false
        }
    }

    // MARK: - Purchase Flow
    public func purchase(_ product: Product) async throws -> StoreKit.Transaction? {
        let result = try await product.purchase()

        switch result {
        case .success(let verification):
            let transaction = try checkVerified(verification)
            await updatePurchasedProducts()
            await transaction.finish()
            
            // Sync credits with backend
            APIClient.shared.fetchUserCredits()
            return transaction

        case .userCancelled, .pending:
            return nil
        @unknown default:
            return nil
        }
    }

    // MARK: - Restore Purchases (Required by Apple Review Guidelines 3.1.1)
    public func restorePurchases() async {
        isLoading = true
        do {
            try await AppStore.sync()
            await updatePurchasedProducts()
            statusMessage = "Purchases restored successfully!"
            isLoading = false
        } catch {
            statusMessage = "Failed to restore purchases: \(error.localizedDescription)"
            isLoading = false
        }
    }

    // MARK: - Transaction Verification
    private func checkVerified<T>(_ result: VerificationResult<T>) throws -> T {
        switch result {
        case .unverified(_, let error):
            throw error
        case .verified(let safe):
            return safe
        }
    }

    // MARK: - Transaction Updates Observer
    private func observeTransactionUpdates() -> Task<Void, Never> {
        Task.detached {
            for await result in Transaction.updates {
                do {
                    let transaction = try self.checkVerified(result)
                    await self.updatePurchasedProducts()
                    await transaction.finish()
                } catch {
                    print("Transaction update unverified: \(error)")
                }
            }
        }
    }

    private func updatePurchasedProducts() async {
        var purchased = Set<String>()
        for await result in Transaction.currentEntitlements {
            if case .verified(let transaction) = result, transaction.revocationDate == nil {
                purchased.insert(transaction.productID)
            }
        }
        self.purchasedProductIDs = purchased
    }
}
