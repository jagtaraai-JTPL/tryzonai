package com.jagtarapvtltd.tryzonai.network

import retrofit2.http.*

interface TryZonApiService {
    
    // Auth
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): LoginResponse

    @POST("auth/google")
    suspend fun googleLogin(@Body request: GoogleLoginRequest): LoginResponse

    // Catalog
    @GET("catalog")
    suspend fun getCatalog(
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0,
        @Query("category") category: String? = null,
        @Query("brand") brand: String? = null,
        @Query("color") color: String? = null,
        @Query("occasion") occasion: String? = null,
        @Query("gender") gender: String? = null,
        @Query("search") search: String? = null
    ): CatalogResponse

    @GET("catalog/categories")
    suspend fun getCategories(): CategoryResponse

    @GET("trending-outfit")
    suspend fun getTrendingOutfit(
        @Query("gender") gender: String? = null
    ): com.jagtarapvtltd.tryzonai.models.TrendingOutfitResponse

    // Feed / Inspiration
    @GET("feed")
    suspend fun getFeed(
        @Query("limit") limit: Int = 20
    ): FeedResponse

    // Try-On
    @Multipart
    @POST("tryon")
    suspend fun submitTryOn(
        @Part person_image: okhttp3.MultipartBody.Part,
        @Part garment_image: okhttp3.MultipartBody.Part,
        @Part("product_id") productId: okhttp3.RequestBody? = null,
        @Header("X-Daily-Style") isDailyStyle: String? = null,
        @Header("X-Reward-Ad-Bonus") isRewardAdBonus: String? = null
    ): TryOnSubmissionResponse

    @POST("tryon/claim-reward-credit")
    suspend fun claimRewardCredit(): GenericResponse

    @GET("tryon/status/{session_id}")
    suspend fun getTryOnStatus(
        @Path("session_id") sessionId: Int
    ): TryOnStatusResponse

    // Wardrobe / History
    @GET("tryon/history")
    suspend fun getTryOnHistory(): List<TryOnHistoryItem>
 
    @DELETE("tryon/history/{session_id}")
    suspend fun deleteTryOnHistoryItem(
        @Path("session_id") sessionId: String
    ): GenericResponse

    @POST("wardrobe")
    suspend fun saveToWardrobe(
        @Query("image_url") imageUrl: String
    ): GenericResponse

    @GET("wardrobe")
    suspend fun getWardrobe(): List<WardrobeItem>

    @DELETE("wardrobe/{item_id}")
    suspend fun removeWardrobeItem(
        @Path("item_id") itemId: Int
    ): GenericResponse

    // AI Stylist
    @POST("stylist")
    suspend fun sendMessage(
        @Body request: StylistRequest
    ): StylistResponse

    // User Profile
    @GET("auth/me")
    suspend fun getMe(): UserResponse

    @PUT("auth/profile")
    suspend fun updateProfile(
        @Body request: ProfileUpdateRequest
    ): UserResponse

    @POST("auth/rate")
    suspend fun rateApp(
        @Body request: RateRequest
    ): UserResponse

    @DELETE("auth/me")
    suspend fun deleteAccount(): retrofit2.Response<Unit>

    // Devices (Push Notifications)
    @POST("auth/me/devices")
    suspend fun registerDevice(
        @Body request: DeviceRegisterRequest
    ): GenericResponse

    // Payments (Google Play Billing)
    @POST("payments/google/verify")
    suspend fun verifyGooglePurchase(
        @Body request: GoogleVerifyRequest
    ): GenericResponse
}

data class DeviceRegisterRequest(
    val fcm_token: String,
    val device_type: String = "android"
)

// Response Models
data class CatalogResponse(
    val products: List<com.jagtarapvtltd.tryzonai.models.Product>,
    val total: Int
)

data class CategoryResponse(
    val categories: List<String>
)

data class FeedResponse(
    val items: List<FeedItem>
)

data class FeedItem(
    val id: String,
    val name: String,
    val brand: String,
    val price: Int,
    val image: String,
    val badge: String?
)

data class TryOnSubmissionResponse(
    val session_id: Int,
    val status: String,
    val credits_remaining: Int? = null
)

data class TryOnStatusResponse(
    val session_id: Int,
    val status: String,
    val original_url: String? = null,
    val result_url: String? = null,
    val processing_time_ms: Int?,
    val complements: List<com.jagtarapvtltd.tryzonai.models.ComplementProduct>? = null,
    val price_options: List<com.jagtarapvtltd.tryzonai.models.PriceOption>? = null
)

data class TryOnHistoryItem(
    @com.google.gson.annotations.SerializedName(value = "session_id", alternate = ["id"])
    val id: String = "",
    val product_id: String? = null,
    val result_url: String = "",
    @com.google.gson.annotations.SerializedName(value = "timestamp", alternate = ["created_at"])
    val timestamp: String = "",
    val garment_name: String? = null
)

data class WardrobeItem(
    val id: Int,
    val image_url: String,
    val created_at: String
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class RegisterRequest(
    val email: String,
    val name: String,
    val password: String
)

data class GoogleLoginRequest(
    @com.google.gson.annotations.SerializedName("id_token")
    val idToken: String
)

data class LoginResponse(
    val token: String,
    val user: UserResponse
)

data class UserResponse(
    val id: Int,
    val email: String,
    val username: String,
    val name: String? = null,
    val is_premium: Boolean,
    val credits: Int,
    val paid_credits: Int = 0,
    val try_ons_today: Int,
    val try_ons_limit: Int,
    val wardrobe_count: Int,
    val subscription_tier: String? = null,
    val daily_reward_ad_count: Int = 0,
    val has_given_5_star: Boolean = false,
    val rating_stars: Int? = null,
    val pref_gender: String? = null,
    val pref_fashion_goal: String? = null,
    val pref_style_vibe: String? = null
)

data class ProfileUpdateRequest(
    val name: String? = null,
    val has_given_5_star: Boolean? = null,
    val pref_gender: String? = null,
    val pref_fashion_goal: String? = null,
    val pref_style_vibe: String? = null
)

data class RateRequest(
    val stars: Int,
    val comment: String? = null
)

data class StylistRequest(
    val message: String,
    val history: List<Map<String, String>>? = null
)

data class StylistResponse(
    val response: String,
    val recommendations: List<com.jagtarapvtltd.tryzonai.models.Product>? = null
)

data class GenericResponse(
    val status: String,
    val message: String
)

// Payment Models
data class GoogleVerifyRequest(
    val purchaseToken: String,
    val productId: String,
    val packageName: String = "com.jagtarapvtltd.tryzonai"
)
