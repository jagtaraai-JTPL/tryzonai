import Foundation

// MARK: - User Models
public struct UserProfile: Codable, Identifiable {
    public var id: String
    public var email: String
    public var name: String?
    public var credits: Int
    public var paidCredits: Int
    public var isPremium: Bool
    public var subscriptionTier: String?
    public var tryOnsToday: Int
    public var dailyRewardAdCount: Int
    public var avatarUrl: String?

    public init(id: String, email: String, name: String? = nil, credits: Int = 3, paidCredits: Int = 0, isPremium: Bool = false, subscriptionTier: String? = nil, tryOnsToday: Int = 0, dailyRewardAdCount: Int = 0, avatarUrl: String? = nil) {
        self.id = id
        self.email = email
        self.name = name
        self.credits = credits
        self.paidCredits = paidCredits
        self.isPremium = isPremium
        self.subscriptionTier = subscriptionTier
        self.tryOnsToday = tryOnsToday
        self.dailyRewardAdCount = dailyRewardAdCount
        self.avatarUrl = avatarUrl
    }

    enum CodingKeys: String, CodingKey {
        case id = "user_id"
        case email
        case name
        case credits
        case paidCredits = "paid_credits"
        case isPremium = "is_premium"
        case subscriptionTier = "subscription_tier"
        case tryOnsToday = "try_ons_today"
        case dailyRewardAdCount = "daily_reward_ad_count"
        case avatarUrl = "avatar_url"
    }
}

// MARK: - Try-On Task Models
public struct TryOnTaskResponse: Codable, Identifiable {
    public var id: String
    public var status: String // "pending", "processing", "completed", "failed"
    public var resultImageUrl: String?
    public var originalPhotoUrl: String?
    public var errorMessage: String?

    enum CodingKeys: String, CodingKey {
        case id = "task_id"
        case status
        case resultImageUrl = "result_image_url"
        case originalPhotoUrl = "original_photo_url"
        case errorMessage = "error_message"
    }
}

// MARK: - Catalog Item
public struct CatalogItem: Codable, Identifiable {
    public var id: String { product_id }
    public var product_id: String
    public var name: String
    public var category: String
    public var image_url: String
    public var price_inr: Int?
    public var store_url: String?
    public var brand: String?
    public var gender: String?
}

// MARK: - Complement Product
public struct ComplementProduct: Codable, Identifiable {
    public var id: String
    public var name: String
    public var category: String
    public var price: Int
    public var image: String
    public var url: String
    public var matchScore: Int
}

// MARK: - Try-On History Item
public struct TryOnHistoryItem: Codable, Identifiable {
    public var id: String { task_id }
    public var task_id: String
    public var result_image: String
    public var original_photo: String
    public var category: String?
    public var created_at: String?
    public var is_favorite: Bool?
}

// MARK: - Pricing Models
public struct PricingPlan: Identifiable {
    public var id: String { productId }
    public var name: String
    public var productId: String
    public var subtitle: String
    public var price: String
    public var tag: String
    public var features: [String]
    public var isHighlight: Bool
    public var isSubscription: Bool
    public var billingPeriod: String

    public init(name: String, productId: String, subtitle: String, price: String, tag: String, features: [String], isHighlight: Bool = false, isSubscription: Bool = false, billingPeriod: String = "") {
        self.name = name
        self.productId = productId
        self.subtitle = subtitle
        self.price = price
        self.tag = tag
        self.features = features
        self.isHighlight = isHighlight
        self.isSubscription = isSubscription
        self.billingPeriod = billingPeriod
    }
}
