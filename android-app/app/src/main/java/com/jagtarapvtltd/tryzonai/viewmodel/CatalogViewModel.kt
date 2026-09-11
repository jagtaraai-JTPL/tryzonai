package com.jagtarapvtltd.tryzonai.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jagtarapvtltd.tryzonai.models.Product
import com.jagtarapvtltd.tryzonai.network.FeedItem
import com.jagtarapvtltd.tryzonai.network.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CatalogViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("catalog_cache", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val _products = MutableStateFlow<List<Product>>(emptyList())
    val products: StateFlow<List<Product>> = _products.asStateFlow()

    private val _categories = MutableStateFlow<List<String>>(listOf("All"))
    val categories: StateFlow<List<String>> = _categories.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isMoreLoading = MutableStateFlow(false)
    val isMoreLoading: StateFlow<Boolean> = _isMoreLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var currentOffset = 0
    private val LIMIT = 24
    private var totalCount = 0

    private var activeCategory = "All"
    private var currentSearch = ""
    private var activeBrand: String? = null
    private var activeColor: String? = null
    private var activeOccasion: String? = null
    private var activeGender: String? = null

    private var activeSort = "Newest"

    private val _currentHeroFrame = MutableStateFlow((0..5).random())
    val currentHeroFrame: StateFlow<Int> = _currentHeroFrame.asStateFlow()

    init {
        // Load offline cache or master catalog immediately so UI is never empty
        _products.value = applyFiltersAndSorting(loadCachedCatalog())
        fetchCategories()
        loadCatalog(reset = true)
        
        // Background loop for Hero Try-On animations
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(5200)
                _currentHeroFrame.value = (_currentHeroFrame.value + 1) % 6
            }
        }
    }

    private fun getMasterCatalog(): List<Product> {
        return listOf(
            Product("2", "Swiss Tech Alpine Weatherproof Jacket", "TryZon Cyber Lab", 4499, 6999, "res:${com.jagtarapvtltd.tryzonai.R.drawable.swiss_var}", "Alpine & Outdoor", store = "TryZon AI", is_featured = true, badge = "❄️ PRO AI", colors = listOf("White", "Black")),
            Product("1", "Dubai Elite Royal Abaya", "TryZon AI Haute Couture", 3999, 5999, "res:${com.jagtarapvtltd.tryzonai.R.drawable.uae_var}", "Gowns & Luxury", store = "TryZon AI", is_featured = true, badge = "✨ VIRAL AI", colors = listOf("Black", "Gold")),
            Product("3", "Tokyo Shibuya Cyberpunk Techwear", "TryZon Cyber Lab", 4999, 7999, "res:${com.jagtarapvtltd.tryzonai.R.drawable.japan_var}", "Streetwear & Cyber", store = "TryZon AI", is_featured = true, badge = "⚡ NEON AI", colors = listOf("Black")),
            Product("4", "Monaco Riviera Yacht Club Linen", "TryZon AI Exclusives", 2999, 4499, "res:${com.jagtarapvtltd.tryzonai.R.drawable.monaco_var}", "Riviera & Summer", store = "TryZon AI", is_featured = true, badge = "👑 OLD MONEY", colors = listOf("White")),
            Product("5", "New York Fifth Ave Executive Suit", "TryZon AI Haute Couture", 5499, 8999, "res:${com.jagtarapvtltd.tryzonai.R.drawable.usa_var}", "Suits & Formal", store = "TryZon AI", is_featured = true, badge = "💼 EXECUTIVE", colors = listOf("Blue")),
            Product("6", "Seoul Gangnam K-Minimalist Black Suit", "TryZon AI Exclusives", 4299, 6499, "res:${com.jagtarapvtltd.tryzonai.R.drawable.korea_var}", "Suits & Formal", store = "TryZon AI", is_featured = true, badge = "🫰 K-STYLE", colors = listOf("Black")),
            Product("7", "Hollywood Gala Royal Sapphire Gown", "TryZon AI Haute Couture", 6999, 10999, "res:${com.jagtarapvtltd.tryzonai.R.drawable.sample_outfit_usa_gown}", "Gowns & Luxury", store = "TryZon AI", is_featured = true, badge = "🌟 GALA AI", colors = listOf("Blue")),
            Product("8", "Beverly Hills Chic Silk Jumpsuit", "TryZon AI Exclusives", 3499, 5499, "res:${com.jagtarapvtltd.tryzonai.R.drawable.sample_outfit_usa_jumpsuit}", "Gowns & Luxury", store = "TryZon AI", is_featured = false, badge = "✨ CHIC AI", colors = listOf("Black")),
            Product("9", "Wall Street Custom Italian Bespoke Blazer", "TryZon AI Haute Couture", 5999, 9499, "res:${com.jagtarapvtltd.tryzonai.R.drawable.sample_outfit_usa_suit}", "Suits & Formal", store = "TryZon AI", is_featured = true, badge = "👔 BESPOKE", colors = listOf("Blue")),
            Product("10", "Royal Emerald Silk Festival Kurta", "TryZon AI Exclusives", 2999, 4499, "res:${com.jagtarapvtltd.tryzonai.R.drawable.sample_model_male}", "Ethnic & Traditional", store = "TryZon AI", is_featured = false, badge = "🪔 ETHNIC AI", colors = listOf("Green")),
            Product("11", "Cottagecore Floral Summer Sundress", "TryZon AI Exclusives", 2499, 3999, "res:${com.jagtarapvtltd.tryzonai.R.drawable.sample_model_female}", "Riviera & Summer", store = "TryZon AI", is_featured = false, badge = "🌸 SUMMER AI", colors = listOf("Pink")),
            Product("12", "Luxury Gold Chronograph AI Watch", "TryZon Cyber Lab", 7999, 11999, "https://images.unsplash.com/photo-1524805444758-089113d48a6d?w=500", "Accessories", store = "TryZon AI", is_featured = true, badge = "💎 LUXE ACC", colors = listOf("Gold"))
        )
    }

    private var rawFetchedProducts: List<Product> = emptyList()

    private fun applyFiltersAndSorting(sourceList: List<Product>): List<Product> {
        val master = if (sourceList.isNotEmpty()) sourceList else getMasterCatalog()
        
        var filtered = master.filter { item ->
            val matchesCategory = if (activeCategory == "All AI Outfits" || activeCategory == "All") true else {
                item.category.equals(activeCategory, ignoreCase = true) ||
                item.category.contains(activeCategory, ignoreCase = true) ||
                (activeCategory == "Suits & Formal" && (item.category.contains("Formal", ignoreCase = true) || item.name.contains("Suit", ignoreCase = true) || item.name.contains("Blazer", ignoreCase = true) || item.name.contains("Executive", ignoreCase = true))) ||
                (activeCategory == "Gowns & Luxury" && (item.category.contains("Luxury", ignoreCase = true) || item.name.contains("Abaya", ignoreCase = true) || item.name.contains("Gown", ignoreCase = true) || item.name.contains("Jumpsuit", ignoreCase = true) || item.name.contains("Dress", ignoreCase = true))) ||
                (activeCategory == "Streetwear & Cyber" && (item.category.contains("Cyber", ignoreCase = true) || item.name.contains("Techwear", ignoreCase = true) || item.name.contains("Streetwear", ignoreCase = true) || item.name.contains("Casual", ignoreCase = true))) ||
                (activeCategory == "Ethnic & Traditional" && (item.category.contains("Ethnic", ignoreCase = true) || item.name.contains("Kurta", ignoreCase = true) || item.name.contains("Saree", ignoreCase = true) || item.name.contains("Lehenga", ignoreCase = true))) ||
                (activeCategory == "Riviera & Summer" && (item.category.contains("Summer", ignoreCase = true) || item.name.contains("Linen", ignoreCase = true) || item.name.contains("Sundress", ignoreCase = true) || item.name.contains("Beach", ignoreCase = true))) ||
                (activeCategory == "Alpine & Outdoor" && (item.category.contains("Alpine", ignoreCase = true) || item.name.contains("Jacket", ignoreCase = true) || item.name.contains("Coat", ignoreCase = true)))
            }

            val matchesBrand = if (activeBrand.isNullOrEmpty() || activeBrand == "All") true else {
                val brand = activeBrand ?: ""
                item.brand.contains(brand, ignoreCase = true) || brand.contains(item.brand, ignoreCase = true)
            }

            val matchesColor = if (activeColor.isNullOrEmpty() || activeColor == "All") true else {
                val color = activeColor ?: ""
                item.colors.any { it.equals(color, ignoreCase = true) } ||
                item.name.contains(color, ignoreCase = true) ||
                item.badge?.contains(color, ignoreCase = true) == true
            }

            val matchesGender = if (activeGender.isNullOrEmpty() || activeGender == "All") true else {
                val g = (activeGender ?: "").lowercase()
                when (g) {
                    "men" -> item.name.contains("Men", ignoreCase = true) || item.name.contains("Kurta", ignoreCase = true) || item.name.contains("Blazer", ignoreCase = true) || item.name.contains("Suit", ignoreCase = true) || item.name.contains("Linen", ignoreCase = true)
                    "women" -> item.name.contains("Women", ignoreCase = true) || item.name.contains("Dress", ignoreCase = true) || item.name.contains("Abaya", ignoreCase = true) || item.name.contains("Gown", ignoreCase = true) || item.name.contains("Jumpsuit", ignoreCase = true) || item.name.contains("Sundress", ignoreCase = true)
                    "unisex" -> item.colors.contains("White") || item.name.contains("Techwear", ignoreCase = true) || item.category == "Accessories"
                    else -> true
                }
            }

            val matchesOccasion = if (activeOccasion.isNullOrEmpty() || activeOccasion == "All") true else {
                val occ = (activeOccasion ?: "").lowercase()
                item.category.contains(occ, ignoreCase = true) ||
                item.name.contains(occ, ignoreCase = true) ||
                (occ == "formal" && (item.category == "Suits & Formal" || item.name.contains("Executive", ignoreCase = true))) ||
                (occ == "ethnic" && (item.name.contains("Kurta", ignoreCase = true) || item.name.contains("Abaya", ignoreCase = true))) ||
                (occ == "summer" && (item.category == "Riviera & Summer" || item.name.contains("Linen", ignoreCase = true))) ||
                (occ == "winter" && (item.category == "Alpine & Outdoor" || item.name.contains("Jacket", ignoreCase = true))) ||
                (occ == "streetwear" && (item.category == "Streetwear & Cyber" || item.name.contains("Techwear", ignoreCase = true)))
            }

            val matchesSearch = if (currentSearch.isEmpty()) true else {
                item.name.contains(currentSearch, ignoreCase = true) ||
                item.brand.contains(currentSearch, ignoreCase = true) ||
                item.category.contains(currentSearch, ignoreCase = true)
            }

            matchesCategory && matchesBrand && matchesColor && matchesGender && matchesOccasion && matchesSearch
        }

        if (filtered.isEmpty()) {
            filtered = master.filter { item ->
                if (activeCategory != "All AI Outfits" && activeCategory != "All") item.category.contains(activeCategory, ignoreCase = true) else true
            }.ifEmpty { master }
        }

        return when (activeSort) {
            "Price: Low to High" -> filtered.sortedBy { it.price }
            "Price: High to Low" -> filtered.sortedByDescending { it.price }
            "Popularity" -> filtered.sortedByDescending { if (it.is_featured) 1 else 0 }
            else -> {
                // Smart Personalized Ranking using Onboarding User Preferences
                val prefs = getApplication<Application>().getSharedPreferences("tryzon_user_prefs", Context.MODE_PRIVATE)
                val tryOnPrefs = getApplication<Application>().getSharedPreferences("try_on_prefs", Context.MODE_PRIVATE)
                val userGender = (prefs.getString("user_gender", null) ?: tryOnPrefs.getString("user_gender", "Women") ?: "Women").lowercase()
                val userGoalIndex = if (prefs.contains("user_fashion_goal")) prefs.getInt("user_fashion_goal", 0) else tryOnPrefs.getInt("user_fashion_goal", 0)
                val userVibeIndex = if (prefs.contains("user_style_vibe")) prefs.getInt("user_style_vibe", 0) else tryOnPrefs.getInt("user_style_vibe", 0)

                filtered.sortedByDescending { item ->
                    var score = 0
                    val nameLower = item.name.lowercase()
                    val catLower = item.category.lowercase()

                    // Gender Preference Match
                    if (userGender == "women" && (nameLower.contains("women") || nameLower.contains("dress") || nameLower.contains("abaya") || nameLower.contains("gown") || nameLower.contains("jumpsuit") || nameLower.contains("sundress"))) {
                        score += 30
                    } else if (userGender == "men" && (nameLower.contains("men") || nameLower.contains("suit") || nameLower.contains("blazer") || nameLower.contains("tuxedo") || nameLower.contains("kurta"))) {
                        score += 30
                    }

                    // Style Vibe Preference Match
                    when (userVibeIndex) {
                        0 -> score += 25 // All Styles & Mix (gives universal boost so all trending styles are shown!)
                        1 -> if (catLower.contains("luxury") || nameLower.contains("old money") || nameLower.contains("aesthetic") || nameLower.contains("monaco") || nameLower.contains("chic") || nameLower.contains("linen")) score += 25 // Old Money & Quiet Luxury
                        2 -> if (catLower.contains("streetwear") || catLower.contains("cyber") || nameLower.contains("techwear") || nameLower.contains("oversized") || nameLower.contains("baggy") || nameLower.contains("viral")) score += 25 // Oversized Streetwear
                        3 -> if (catLower.contains("formal") || nameLower.contains("party") || nameLower.contains("gown") || nameLower.contains("glam") || nameLower.contains("gala") || nameLower.contains("cocktail")) score += 25 // Party & Date Night
                        4 -> if (catLower.contains("ethnic") || nameLower.contains("kurta") || nameLower.contains("traditional") || nameLower.contains("lehenga") || nameLower.contains("wedding") || nameLower.contains("festive")) score += 25 // Royal Ethnic & Festive
                        5 -> if (catLower.contains("casual") || nameLower.contains("minimal") || nameLower.contains("airport") || nameLower.contains("basic") || nameLower.contains("blazer") || nameLower.contains("jacket")) score += 25 // Minimal Airport Chic
                    }

                    // Fashion Goal Preference Match
                    when (userGoalIndex) {
                        0 -> if (item.is_featured) score += 15 // Verify Fit Online
                        1 -> if (nameLower.contains("royal") || nameLower.contains("gala") || nameLower.contains("viral") || nameLower.contains("cyber")) score += 15 // Social Media Looks
                        2 -> if (catLower.contains("formal") || catLower.contains("luxury") || nameLower.contains("bespoke")) score += 15 // Special Event Wardrobe
                    }

                    score
                }
            }
        }
    }

    private fun loadCachedCatalog(): List<Product> {
        val cachedJson = prefs.getString("cached_products", null)
        if (cachedJson != null) {
            try {
                val type = object : TypeToken<List<Product>>() {}.type
                val cachedList: List<Product> = gson.fromJson(cachedJson, type)
                if (cachedList.isNotEmpty()) return cachedList
            } catch (e: Exception) {
                android.util.Log.e("CatalogCache", "Error reading cache", e)
            }
        }
        return getMasterCatalog()
    }

    private fun saveCatalogToCache(products: List<Product>) {
        val toCache = products.take(24)
        prefs.edit().putString("cached_products", gson.toJson(toCache)).apply()
    }

    fun fetchCategories() {
        _categories.value = listOf(
            "All AI Outfits",
            "Suits & Formal",
            "Gowns & Luxury",
            "Streetwear & Cyber",
            "Ethnic & Traditional",
            "Riviera & Summer",
            "Alpine & Outdoor",
            "Accessories"
        )
    }

    fun randomizeOutfits() {
        if (rawFetchedProducts.isNotEmpty()) {
            rawFetchedProducts = rawFetchedProducts.shuffled()
            _products.value = applyFiltersAndSorting(rawFetchedProducts)
        } else {
            _products.value = applyFiltersAndSorting(getMasterCatalog().shuffled())
        }
    }

    fun loadCatalog(reset: Boolean = true) {
        viewModelScope.launch {
            if (reset && _products.value.isEmpty()) {
                _isLoading.value = true
                currentOffset = 0
            } else if (!reset) {
                _isMoreLoading.value = true
            }

            try {
                val response = RetrofitClient.apiService.getCatalog(
                    limit = 100,
                    offset = currentOffset,
                    category = if (activeCategory == "All AI Outfits" || activeCategory == "All") null else activeCategory,
                    brand = activeBrand, color = activeColor, occasion = activeOccasion, gender = activeGender, search = if (currentSearch.isEmpty()) null else currentSearch
                )
                
                val fetched = response.products
                totalCount = response.total

                if (reset) {
                    val featured = getMasterCatalog().shuffled()
                    val raw = if (fetched.isNotEmpty()) (featured + fetched.shuffled()).distinctBy { it.id }.shuffled() else featured
                    rawFetchedProducts = raw
                    _products.value = applyFiltersAndSorting(rawFetchedProducts)
                    if (activeCategory == "All AI Outfits" && currentSearch.isEmpty()) {
                        saveCatalogToCache(_products.value)
                    }
                } else {
                    val combined = (rawFetchedProducts + fetched).distinctBy { it.id }.shuffled()
                    rawFetchedProducts = combined
                    _products.value = applyFiltersAndSorting(rawFetchedProducts)
                }
                
                currentOffset += 100
            } catch (e: Exception) {
                android.util.Log.e("TryZonAPI", "Catalog API Failed: ${e.message}")
                if (_products.value.isEmpty()) {
                    rawFetchedProducts = getMasterCatalog()
                    _products.value = applyFiltersAndSorting(rawFetchedProducts)
                }
            } finally {
                _isLoading.value = false
                _isMoreLoading.value = false
            }
        }
    }

    private fun updateProductsInstantly() {
        val listToFilter = if (rawFetchedProducts.isNotEmpty()) rawFetchedProducts else getMasterCatalog()
        _products.value = applyFiltersAndSorting(listToFilter)
    }

    fun setCategory(category: String) {
        activeCategory = category
        updateProductsInstantly()
    }

    fun setSearch(query: String) {
        currentSearch = query
        updateProductsInstantly()
    }

    fun searchSimilar(look: FeedItem) {
        currentSearch = look.name
        updateProductsInstantly()
    }

    fun setSort(sortOption: String) {
        activeSort = sortOption
        updateProductsInstantly()
    }

    fun hasMore(): Boolean = _products.value.size < totalCount

    fun setBrand(brand: String?) {
        activeBrand = if (brand == "All") null else brand
        updateProductsInstantly()
    }

    fun setColor(color: String?) {
        activeColor = if (color == "All") null else color
        updateProductsInstantly()
    }

    fun setOccasion(occasion: String?) {
        activeOccasion = if (occasion == "All") null else occasion
        updateProductsInstantly()
    }

    fun setGender(gender: String?) {
        activeGender = if (gender == "All") null else gender
        updateProductsInstantly()
    }

    fun resetFilters() {
        activeBrand = null
        activeColor = null
        activeOccasion = null
        activeGender = null
        activeSort = "Newest"
        activeCategory = "All AI Outfits"
        currentSearch = ""
        updateProductsInstantly()
    }
}
