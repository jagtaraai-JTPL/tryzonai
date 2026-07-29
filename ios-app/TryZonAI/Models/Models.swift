import Foundation

// MARK: - User Models
public struct UserProfile: Codable, Identifiable {
    public var id: String
    public var email: String
    public var name: String?
    public var credits: Int
    public var isPremium: Bool
    public var avatarUrl: String?

    enum CodingKeys: String, CodingKey {
        case id = "_id"
        case email
        case name
        case credits
        case isPremium = "is_premium"
        case avatarUrl = "avatar_url"
    }
}

// MARK: - Try-On Task Models
public struct TryOnRequest: Codable {
    public var modelImageUrl: String
    public var garmentImageUrl: String
    public var category: String
}

public struct TryOnTaskResponse: Codable, Identifiable {
    public var id: String
    public var status: String // "pending", "processing", "completed", "failed"
    public var resultImageUrl: String?
    public var errorMessage: String?
    public var createdAt: String?

    enum CodingKeys: String, CodingKey {
        case id = "task_id"
        case status
        case resultImageUrl = "result_image_url"
        case errorMessage = "error_message"
        case createdAt = "created_at"
    }
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
