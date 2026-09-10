package com.jagtarapvtltd.tryzonai.models

import android.net.Uri

data class Product(
    val id: String,
    val name: String = "",
    val brand: String = "",
    val price: Int = 0,
    val originalPrice: Int = 0,
    val image: String = "",
    val category: String = "",
    val store: String? = null,
    val url: String? = null,
    val is_featured: Boolean = false,
    val badge: String? = null,
    // Modern fields for 2026 UI
    val discount: Int = 0,
    val rating: Float = 0f,
    val colors: List<String> = emptyList(),
    val sizes: List<String> = emptyList(),
    val imageUrl: String = ""
)

data class TrendingProduct(
    val id: String,
    val name: String,
    val price: String,
    val discount: String,
    val imageUrl: String
)

data class Collection(
    val name: String,
    val itemCount: String,
    val color: androidx.compose.ui.graphics.Color
)

data class WardrobeItem(
    val id: String,
    val garmentImageUrl: String,
    val userPhotoUrl: String,
    val resultImageUrl: String,
    val productId: String?,
    val createdAt: Long
)

data class TryOnResult(
    val originalPhoto: String,
    val resultImage: String,
    val complements: List<ComplementProduct>,
    val priceOptions: List<PriceOption>
)

data class ComplementProduct(
    val id: String,
    val name: String,
    val category: String,
    val price: Int,
    val imageUrl: String,
    val url: String? = null,
    val matchScore: Int
)

data class PriceOption(
    val productId: String,
    val store: String,
    val originalPrice: Int,
    val discount: Int,
    val couponCode: String,
    val couponDiscount: Int,
    val cashback: Int,
    val finalPrice: Int,
    val totalSavings: Int,
    val url: String? = null
)

data class TrendingOutfitResponse(
    val id: String,
    val name: String,
    val category: String,
    val gender: String? = null,
    val image_url: String,
    val price: Int
)
