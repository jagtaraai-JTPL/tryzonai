package com.jagtarapvtltd.tryzonai.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jagtarapvtltd.tryzonai.network.FeedItem
import com.jagtarapvtltd.tryzonai.network.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class InspirationViewModel : ViewModel() {
    private val _looks = MutableStateFlow<List<FeedItem>>(emptyList())
    val looks: StateFlow<List<FeedItem>> = _looks.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadInspiration()
    }

    fun loadInspiration() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = RetrofitClient.apiService.getFeed()
                if (response.items.isNotEmpty()) {
                    _looks.value = response.items
                } else {
                    throw Exception("Empty")
                }
            } catch (e: Exception) {
                android.util.Log.e("TryZonAPI", "Inspiration API Failed: ${e.message}", e)
                // Premium Inspiration Data (Synced with Website GLOBAL_IDEAS)
                _looks.value = listOf(
                    FeedItem("mens_raw_denim_jacket", "Raw Denim Jacket", "Iron Heart", 12000, "/outfits/premium_catalog/ai_premium_outfit_korean_minimalist_black_suit_turtleneck_1778092243378.webp", "TRENDING"),
                    FeedItem("vera_wang_gown", "White Silk Gown", "Vera Wang", 85000, "/outfits/premium_catalog/ai_premium_white_elegant_outfit_v2.webp", "EXCLUSIVE"),
                    FeedItem("emerald_green_suit", "Emerald Green Suit", "Zara", 12999, "/outfits/premium_catalog/ai_premium_outfit_formal_emerald_velvet_suit_1778132122000_1778124726836.webp", "AI PICK"),
                    FeedItem("emerald_gown", "Emerald Sequin Gown", "Jenny Packham", 145000, "/outfits/premium_catalog/ai_premium_women_emerald_gown.webp", "GALA"),
                    FeedItem("classic_trench", "Parisian Trench Coat", "Burberry", 95000, "/outfits/premium_catalog/ai_premium_women_classic_trench.webp", "CLASSIC"),
                    FeedItem("luxury_polo", "Quiet Luxury Polo", "Loro Piana", 45000, "/outfits/premium_catalog/ai_premium_outfit_old_money_beige_linen_silk_1778132082000_1778124695233.webp", "OLD MONEY"),
                    FeedItem("floral_puff", "Cottagecore Floral Dress", "LoveShackFancy", 32000, "/outfits/premium_catalog/ai_premium_women_floral_sundress.webp", "SUMMER"),
                    FeedItem("nyc_streetwear", "NYC Luxury Streetwear", "TryZon AI", 18500, "/outfits/premium_catalog/ai_premium_outfit_fantasy_kimono_cyberpunk_neon_red_1778092453825.webp", "STREET STYLE")
                )
            } finally {
                _isLoading.value = false
            }
        }
    }
}
