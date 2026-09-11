import Foundation

// MARK: - Helper Function to Resolve Relative Server URLs
public func resolveURL(_ path: String?) -> URL? {
    guard let path = path, !path.isEmpty else { return nil }
    if path.hasPrefix("http://") || path.hasPrefix("https://") {
        return URL(string: path)
    }
    let host = "https://tryzonai.com"
    let cleanPath = path.hasPrefix("/") ? path : "/\(path)"
    return URL(string: "\(host)\(cleanPath)")
}

// MARK: - User Profile Model
public struct UserProfile: Codable, Identifiable {
    public var id: Int
    public var email: String
    public var username: String
    public var name: String?
    public var credits: Int
    public var paidCredits: Int
    public var isPremium: Bool
    public var tryOnsToday: Int
    public var tryOnsLimit: Int
    public var subscriptionTier: String?
    public var dailyRewardAdCount: Int
    public var prefGender: String?

    public init(id: Int, email: String, username: String, name: String? = nil, credits: Int = 3, paidCredits: Int = 0, isPremium: Bool = false, tryOnsToday: Int = 0, tryOnsLimit: Int = 1, subscriptionTier: String? = nil, dailyRewardAdCount: Int = 0, prefGender: String? = "Women") {
        self.id = id
        self.email = email
        self.username = username
        self.name = name
        self.credits = credits
        self.paidCredits = paidCredits
        self.isPremium = isPremium
        self.tryOnsToday = tryOnsToday
        self.tryOnsLimit = tryOnsLimit
        self.subscriptionTier = subscriptionTier
        self.dailyRewardAdCount = dailyRewardAdCount
        self.prefGender = prefGender
    }

    enum CodingKeys: String, CodingKey {
        case id
        case email
        case username
        case name
        case credits
        case paidCredits = "paid_credits"
        case isPremium = "is_premium"
        case tryOnsToday = "try_ons_today"
        case tryOnsLimit = "try_ons_limit"
        case subscriptionTier = "subscription_tier"
        case dailyRewardAdCount = "daily_reward_ad_count"
        case prefGender = "pref_gender"
    }
}

// MARK: - Auth Response
public struct AuthResponse: Codable {
    public var token: String
    public var user: UserProfile
}

// MARK: - Try-On Submission Response
public struct TryOnSubmissionResponse: Codable {
    public var session_id: Int
    public var status: String
    public var credits_remaining: Int?
}

// MARK: - Try-On Status Polling Response
public struct TryOnStatusResponse: Codable, Identifiable {
    public var id: String { String(session_id) }
    public var session_id: Int
    public var status: String // "processing", "done", "failed"
    public var original_url: String?
    public var result_url: String?
    public var highres_url: String?
    public var processing_time_ms: Int?
    public var complements: [ComplementProduct]?
    public var price_options: [PriceOption]?

    public var isCompleted: Bool {
        return status == "done" || status == "completed"
    }

    public var isFailed: Bool {
        return status == "failed"
    }

    public var fullResultURL: URL? {
        return resolveURL(highres_url ?? result_url)
    }

    public var fullOriginalURL: URL? {
        return resolveURL(original_url)
    }
}

// MARK: - Complement Product Model
public struct ComplementProduct: Codable, Identifiable {
    public var id: String
    public var name: String
    public var category: String
    public var price: Int
    public var imageUrl: String?
    public var url: String?
    public var matchScore: Int?

    public var fullImageURL: URL? {
        return resolveURL(imageUrl)
    }
}

// MARK: - Price Option Model
public struct PriceOption: Codable, Identifiable {
    public var id: String { store + name }
    public var store: String
    public var name: String
    public var price: Int
    public var original_price: Int?
    public var url: String?
    public var badge: String?
}

// MARK: - Catalog Item Model
public struct CatalogItem: Codable, Identifiable {
    public var id: String
    public var name: String
    public var brand: String?
    public var price: Int
    public var original_price: Int?
    public var image_url: String
    public var category: String
    public var store: String?
    public var store_url: String?
    public var gender: String?
    public var badge: String?

    public init(id: String, name: String, brand: String? = nil, price: Int, original_price: Int? = nil, image_url: String, category: String, store: String? = nil, store_url: String? = nil, gender: String? = nil, badge: String? = nil) {
        self.id = id
        self.name = name
        self.brand = brand
        self.price = price
        self.original_price = original_price
        self.image_url = image_url
        self.category = category
        self.store = store
        self.store_url = store_url
        self.gender = gender
        self.badge = badge
    }

    enum CodingKeys: String, CodingKey {
        case id
        case name
        case brand
        case price
        case original_price
        case image_url = "image"
        case category
        case store
        case store_url = "url"
        case gender
        case badge
    }

    public var fullImageURL: URL? {
        return resolveURL(image_url)
    }
}

// MARK: - Catalog Response Model
public struct CatalogResponse: Codable {
    public var products: [CatalogItem]
    public var total: Int
}

// MARK: - Try-On History Item Model
public struct TryOnHistoryItem: Codable, Identifiable {
    public var id: String { String(session_id) }
    public var session_id: Int
    public var product_id: String?
    public var result_url: String
    public var created_at: String?
    public var garment_name: String?

    enum CodingKeys: String, CodingKey {
        case session_id
        case product_id
        case result_url
        case created_at = "timestamp"
        case garment_name
    }

    public var fullResultURL: URL? {
        return resolveURL(result_url)
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
