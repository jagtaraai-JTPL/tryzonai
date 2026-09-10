package com.jagtarapvtltd.tryzonai.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jagtarapvtltd.tryzonai.models.*
import com.jagtarapvtltd.tryzonai.network.RetrofitClient
import com.jagtarapvtltd.tryzonai.network.TryOnHistoryItem
import com.jagtarapvtltd.tryzonai.utils.FileHelper
import com.jagtarapvtltd.tryzonai.utils.isAlive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

data class AppNotification(
    val title: String,
    val message: String,
    val timestamp: Long,
    val sessionId: Int? = null
)

class TryOnViewModel(application: Application) : AndroidViewModel(application) {
    
    private val context = application.applicationContext
    private val sessionManager = com.jagtarapvtltd.tryzonai.utils.SessionManager(context)
    val adManager = com.jagtarapvtltd.tryzonai.utils.AdManager.getInstance(context)
    
    private val _garmentImage = MutableStateFlow<Uri?>(null)
    val garmentImage: StateFlow<Uri?> = _garmentImage.asStateFlow()
    
    private val _userPhoto = MutableStateFlow<Uri?>(null)
    val userPhoto: StateFlow<Uri?> = _userPhoto.asStateFlow()
    
    private val _processingState = MutableStateFlow(ProcessingState.IDLE)
    val processingState: StateFlow<ProcessingState> = _processingState.asStateFlow()
    
    private val _tryOnResult = MutableStateFlow<TryOnResult?>(null)
    val tryOnResult: StateFlow<TryOnResult?> = _tryOnResult.asStateFlow()

    private var cachedUserPhotoFile: File? = null
    private var cachedGarmentFile: File? = null

    fun setTryOnResult(resultUrl: String, sessionId: String, originalPhoto: String? = null) {
        _tryOnResult.value = TryOnResult(
            originalPhoto = originalPhoto ?: _userPhoto.value?.toString() ?: "",
            resultImage = resultUrl,
            complements = listOf(
                ComplementProduct("1", "Sunglasses", "Accessories", 0, "https://images.unsplash.com/photo-1511499767150-a48a237f0083?w=500", "https://myntr.it/sQ4bpfb", 95),
                ComplementProduct("2", "Watch", "Accessories", 0, "https://images.unsplash.com/photo-1524805444758-089113d48a6d?w=500", "https://myntr.it/sQ4bpfb", 96),
                ComplementProduct("3", "Shoes", "Footwear", 0, "https://images.unsplash.com/photo-1549298916-b41d501d3772?w=500", "https://myntr.it/sQ4bpfb", 94)
            ),
            priceOptions = emptyList()
        )
        _processingState.value = ProcessingState.COMPLETED
    }
    
    private val _selectedProduct = MutableStateFlow<Product?>(null)
    val selectedProduct: StateFlow<Product?> = _selectedProduct.asStateFlow()

    init {
        if (_userPhoto.value == null) {
            _userPhoto.value = Uri.parse("res:${com.jagtarapvtltd.tryzonai.R.drawable.sample_model_female}")
        }
        if (_selectedProduct.value == null) {
            _selectedProduct.value = Product(
                id = "4",
                name = "Monaco Riviera Yacht Linen",
                brand = "TryZon AI Exclusives",
                price = 2999,
                originalPrice = 4499,
                image = "res:${com.jagtarapvtltd.tryzonai.R.drawable.monaco_var}",
                category = "Riviera & Summer"
            )
        }
    }
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    private val _limitReachedEvent = MutableStateFlow(false)
    val limitReachedEvent: StateFlow<Boolean> = _limitReachedEvent.asStateFlow()
    
    fun resetLimitEvent() {
        _limitReachedEvent.value = false
    }
    
    private fun getGuestTryOnsTotal(): Int {
        val prefs = context.getSharedPreferences("try_on_prefs", Context.MODE_PRIVATE)
        return prefs.getInt("guest_tries_count", 0)
    }

    fun incrementGuestTryOns() {
        if (com.google.firebase.auth.FirebaseAuth.getInstance().currentUser != null) {
            return
        }
        val prefs = context.getSharedPreferences("try_on_prefs", Context.MODE_PRIVATE)
        val count = getGuestTryOnsTotal()
        val newCount = count + 1
        prefs.edit().putInt("guest_tries_count", newCount).apply()
        val remaining = Math.max(0, 1 - newCount)
        _guestTriesRemaining.value = remaining
        if (remaining == 0) {
            _limitReachedEvent.value = true
        }
    }
    
    fun setGuestExhausted() {
        if (com.google.firebase.auth.FirebaseAuth.getInstance().currentUser != null) {
            return
        }
        val prefs = context.getSharedPreferences("try_on_prefs", Context.MODE_PRIVATE)
        prefs.edit().putInt("guest_tries_count", 1).apply()
        _guestTriesRemaining.value = 0
        _limitReachedEvent.value = true
    }
    
    private val _guestTriesRemaining = MutableStateFlow(
        Math.max(0, 1 - getGuestTryOnsTotal())
    )
    val guestTriesRemaining: StateFlow<Int> = _guestTriesRemaining.asStateFlow()
    
    val notificationsEnabled = androidx.compose.runtime.mutableStateOf(true)
    
    val hasUnreadNotifications = androidx.compose.runtime.mutableStateOf(false)
    
    val notificationsList = androidx.compose.runtime.mutableStateListOf<AppNotification>()
    
    @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
    private fun sendCompletionNotification(sessionId: Int? = null) {
        if (!notificationsEnabled.value) return
        
        hasUnreadNotifications.value = true
        notificationsList.add(
            0, 
            AppNotification(
                title = "Try-On Ready!",
                message = "Your AI Virtual Try-On is complete.",
                timestamp = System.currentTimeMillis(),
                sessionId = sessionId
            )
        )
        
        val resultUrl = _tryOnResult.value?.resultImage
        
        viewModelScope.launch(Dispatchers.IO) {
            var resultBitmap: android.graphics.Bitmap? = null
            if (!resultUrl.isNullOrEmpty()) {
                try {
                    val fullUrl = com.jagtarapvtltd.tryzonai.utils.UrlUtils.getFullUrl(resultUrl)
                    val request = okhttp3.Request.Builder().url(fullUrl).build()
                    val response = okhttp3.OkHttpClient().newCall(request).execute()
                    if (response.isSuccessful) {
                        response.body.byteStream().use { stream ->
                            val options = android.graphics.BitmapFactory.Options().apply {
                                inSampleSize = 2
                                inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
                            }
                            resultBitmap = android.graphics.BitmapFactory.decodeStream(stream, null, options)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("TryZonAPI", "Failed to load notification image: ${e.message}")
                }
            }

            // Trigger actual Android System Notification
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "tryzon_ai_channel"
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "TryZon AI Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications for Try-On status and updates"
                    enableVibration(true)
                }
                notificationManager.createNotificationChannel(channel)
            }
            
            // Create an intent to open the app
            val intent = android.content.Intent(context, com.jagtarapvtltd.tryzonai.MainActivity::class.java).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("deep_link_type", "tryon_complete")
                putExtra("notification_type", "tryon_complete")
                putExtra("from_notification", true)
                if (sessionId != null) {
                    putExtra("session_id", sessionId.toString())
                }
                if (!resultUrl.isNullOrEmpty()) {
                    putExtra("result_url", resultUrl)
                }
                val origPhoto = _userPhoto.value?.toString() ?: _tryOnResult.value?.originalPhoto
                if (!origPhoto.isNullOrEmpty()) {
                    putExtra("original_photo", origPhoto)
                }
            }
            val requestCode = sessionId ?: System.currentTimeMillis().toInt()
            val pendingIntent = android.app.PendingIntent.getActivity(
                context, requestCode, intent,
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
            )
            
            val largeIcon = android.graphics.BitmapFactory.decodeResource(context.resources, com.jagtarapvtltd.tryzonai.R.drawable.ic_tryzon_logo)
            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(com.jagtarapvtltd.tryzonai.R.drawable.ic_notification_t)
                .setLargeIcon(resultBitmap ?: largeIcon)
                .setContentTitle("Try-On Ready! 👗")
                .setContentText("Your AI Virtual Try-On is complete. Tap to view your new look.")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                .setColor(android.graphics.Color.parseColor("#FFD700"))
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setDefaults(NotificationCompat.DEFAULT_ALL) // Sound & Vibration

            if (resultBitmap != null) {
                builder.setStyle(NotificationCompat.BigPictureStyle().bigPicture(resultBitmap).bigLargeIcon(null as android.graphics.Bitmap?))
            }
                
            // Use a random ID or a fixed one
            notificationManager.notify(1001, builder.build())
        }
    }
    
    private val _userModelsHistory = MutableStateFlow<List<Uri>>(emptyList())
    val userModelsHistory: StateFlow<List<Uri>> = _userModelsHistory.asStateFlow()

    init {
        adManager.loadRewardedAd()
        viewModelScope.launch(Dispatchers.IO) {
            loadUserModelsHistory()
        }
    }

    private fun loadUserModelsHistory() {
        val modelsDir = File(context.filesDir, "user_models")
        if (!modelsDir.exists()) modelsDir.mkdirs()
        
        val savedFiles = modelsDir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
        val uris = savedFiles.map { Uri.fromFile(it) }
        _userModelsHistory.value = uris

        if (uris.isNotEmpty()) {
            _userPhoto.value = uris.first()
        } else {
            val legacyFile = File(context.filesDir, "persisted_user_photo.jpg")
            if (legacyFile.exists()) {
                _userPhoto.value = Uri.fromFile(legacyFile)
            } else {
                _userPhoto.value = Uri.parse("res:${com.jagtarapvtltd.tryzonai.R.drawable.sample_model_female}")
            }
        }
        _garmentImage.value = null
    }

    fun setGarmentImage(uri: Uri) { 
        _garmentImage.value = uri 
        com.jagtarapvtltd.tryzonai.utils.AnalyticsHelper.logEvent("select_cloth")
    }

    fun setUserPhoto(uri: Uri) { 
        _userPhoto.value = uri 
        com.jagtarapvtltd.tryzonai.utils.AnalyticsHelper.logEvent("upload_person_photo")
        
        val uriString = uri.toString()
        val isSampleModel = uriString.startsWith("res:") || 
                            uriString.startsWith("android.resource://") || 
                            uriString.contains("sample_model") || 
                            uriString.contains("base") || 
                            uriString.contains("drawable")

        if (!isSampleModel) {
            viewModelScope.launch {
                withContext(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        val modelsDir = File(context.filesDir, "user_models")
                        if (!modelsDir.exists()) modelsDir.mkdirs()
                        
                        val file = File(modelsDir, "user_model_${System.currentTimeMillis()}.jpg")
                        val inputStream = context.contentResolver.openInputStream(uri)
                        val outputStream = java.io.FileOutputStream(file)
                        inputStream?.use { input ->
                            outputStream.use { output ->
                                input.copyTo(output)
                            }
                        }
                        val savedUri = Uri.fromFile(file)
                        withContext(kotlinx.coroutines.Dispatchers.Main) {
                            _userPhoto.value = savedUri
                            val updated = listOf(savedUri) + _userModelsHistory.value.filter { it != savedUri }
                            _userModelsHistory.value = updated.take(10)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }
    fun setSelectedProduct(product: Product) { 
        _selectedProduct.value = product 
        _garmentImage.value = null
        com.jagtarapvtltd.tryzonai.utils.AnalyticsHelper.logEvent("select_cloth")
    }
    
    fun clearUserPhoto() {
        _userPhoto.value = Uri.parse("res:${com.jagtarapvtltd.tryzonai.R.drawable.sample_model_female}")
        _userModelsHistory.value = emptyList()
        val file = File(context.filesDir, "persisted_user_photo.jpg")
        if (file.exists()) file.delete()
        val modelsDir = File(context.filesDir, "user_models")
        if (modelsDir.exists()) modelsDir.deleteRecursively()
    }
    
    fun clearGarmentImage() {
        _garmentImage.value = null
        _selectedProduct.value = null
    }

    fun extractProductFromUrl(sharedUrl: String) {
        viewModelScope.launch {
            try {
                val client = okhttp3.OkHttpClient()
                var imageUrl: String? = null
                var title = "Shared Outfit"

                // 1. Direct HTML Fetch (Bypasses some blocks like Myntra if UA is set)
                val directRequest = okhttp3.Request.Builder()
                    .url(sharedUrl)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; SM-S901B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Mobile Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                    .build()
                val response = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    try { client.newCall(directRequest).execute() } catch(e: Exception) { null }
                }
                
                if (response != null && response.isSuccessful) {
                    val html = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        response.body.string()
                    }
                    
                    // Regex for og:image
                    val ogImageRegex = java.util.regex.Pattern.compile("<meta[^>]*property=[\"']og:image[\"'][^>]*content=[\"']([^\"']+)[\"']")
                    val ogMatcher = ogImageRegex.matcher(html)
                    if (ogMatcher.find()) {
                        imageUrl = ogMatcher.group(1)
                    }
                    
                    // Fallback for Amazon hiRes/large
                    if (imageUrl == null || imageUrl.isEmpty()) {
                        val hiResRegex = java.util.regex.Pattern.compile("[\"']hiRes[\"']\\s*:\\s*[\"'](https://[^\"]+\\.(?:jpg|png|jpeg))[\"']")
                        val hiMatcher = hiResRegex.matcher(html)
                        if (hiMatcher.find()) {
                            imageUrl = hiMatcher.group(1)
                        } else {
                            val largeRegex = java.util.regex.Pattern.compile("[\"']large[\"']\\s*:\\s*[\"'](https://[^\"]+\\.(?:jpg|png|jpeg))[\"']")
                            val largeMatcher = largeRegex.matcher(html)
                            if (largeMatcher.find()) {
                                imageUrl = largeMatcher.group(1)
                            }
                        }
                    }

                    // Regex for title
                    val titleRegex = java.util.regex.Pattern.compile("<title>(.*?)</title>")
                    val titleMatcher = titleRegex.matcher(html)
                    if (titleMatcher.find()) {
                        title = titleMatcher.group(1) ?: "Shared Outfit"
                    }
                }

                // 2. Fallback to Microlink API
                if (imageUrl == null || !imageUrl.startsWith("http")) {
                    val apiRequest = okhttp3.Request.Builder()
                        .url("https://api.microlink.io/?url=${java.net.URLEncoder.encode(sharedUrl, "UTF-8")}")
                        .build()
                    val fallbackResponse = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        try { client.newCall(apiRequest).execute() } catch(e: Exception) { null }
                    }
                    if (fallbackResponse != null && fallbackResponse.isSuccessful) {
                        val jsonString = fallbackResponse.body.string()
                        val dataObj = org.json.JSONObject(jsonString).optJSONObject("data")
                        imageUrl = dataObj?.optJSONObject("image")?.optString("url")
                        val apiTitle = dataObj?.optString("title")
                        if (!apiTitle.isNullOrEmpty()) title = apiTitle
                    }
                }
                
                if (imageUrl != null && imageUrl.isNotEmpty()) {
                    val product = Product(
                        id = "shared_${System.currentTimeMillis()}",
                        name = title,
                        category = "Shared Link",
                        price = 0, // Mock price for UI
                        image = imageUrl,
                        url = sharedUrl
                    )
                    _selectedProduct.value = product
                } else {
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        android.widget.Toast.makeText(
                            getApplication(), 
                            "Could not extract photo. Some shopping apps block direct sharing. Please upload manually.", 
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        getApplication(), 
                        "Failed to extract photo: ${e.message}", 
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
    
    var pendingAdActivity: java.lang.ref.WeakReference<android.app.Activity>? = null

    var lastTryOnWasPremium = false
    var hasRewardAdBonus = false

    fun claimRewardedAdCredit(onCreditUpdated: ((String) -> Unit)? = null) {
        // NOTE: DO NOT set hasRewardAdBonus here. This is a standalone credit top-up.
        // hasRewardAdBonus is only set in submitTryOnWithAd() for the mandatory try-on ad flow.
        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.claimRewardCredit()
                android.util.Log.d("TryOnViewModel", "Claimed reward credit: ${response.message}")
                onCreditUpdated?.invoke(response.message)
            } catch (e: retrofit2.HttpException) {
                val errorMsg = try {
                    val body = e.response()?.errorBody()?.string()
                    if (body != null) org.json.JSONObject(body).optString("detail", "Daily limit of 3 bonus credits reached!")
                    else "Daily limit of 3 bonus credits reached!"
                } catch (ex: Exception) {
                    "Daily limit of 3 bonus credits reached for today!"
                }
                onCreditUpdated?.invoke(errorMsg)
            } catch (e: Exception) {
                android.util.Log.e("TryOnViewModel", "Failed to claim reward credit: ${e.message}")
                onCreditUpdated?.invoke("Failed to claim credit: ${e.message}")
            }
        }
    }

    fun grantSpinWheelReward(rewardText: String, onRewardGranted: ((String) -> Unit)? = null) {
        val freeTriesCount = when {
            rewardText.contains("5") -> 5
            rewardText.contains("2") -> 2
            rewardText.contains("1") -> 1
            else -> 0
        }
        if (freeTriesCount <= 0) return
        viewModelScope.launch {
            try {
                if (com.google.firebase.auth.FirebaseAuth.getInstance().currentUser == null) {
                    val prefs = context.getSharedPreferences("try_on_prefs", Context.MODE_PRIVATE)
                    val currentGuestCount = prefs.getInt("guest_tries_count", 0)
                    val newGuestCount = Math.max(0, currentGuestCount - freeTriesCount)
                    prefs.edit().putInt("guest_tries_count", newGuestCount).apply()
                    _guestTriesRemaining.value = Math.max(0, 1 - newGuestCount)
                    _limitReachedEvent.value = false
                    onRewardGranted?.invoke("🎉 Granted +$freeTriesCount Free Try-Ons!")
                } else {
                    repeat(freeTriesCount) {
                        try {
                            RetrofitClient.apiService.claimRewardCredit()
                        } catch (e: Exception) {
                            android.util.Log.e("TryOnViewModel", "Spin wheel credit grant error: ${e.message}")
                        }
                    }
                    onRewardGranted?.invoke("🎉 Granted +$freeTriesCount Free Try-Ons to your account!")
                }
            } catch (e: Exception) {
                android.util.Log.e("TryOnViewModel", "Failed to grant spin wheel reward: ${e.message}")
            }
        }
    }

    fun submitTryOnWithAd(activity: android.app.Activity?, isPremium: Boolean) {
        lastTryOnWasPremium = isPremium
        if (activity != null) {
            pendingAdActivity = java.lang.ref.WeakReference(activity)
        }

        viewModelScope.launch {
            _processingState.value = ProcessingState.IDLE
            _errorMessage.value = null
            _tryOnResult.value = null

            // ── 1. PRE-VALIDATE NETWORK CONNECTION ──
            if (!com.jagtarapvtltd.tryzonai.utils.NetworkMonitor.isInternetAvailable(context)) {
                _errorMessage.value = "Internet connection required for AI Try-On"
                _processingState.value = ProcessingState.ERROR
                return@launch
            }

            // ── 2. PRE-VALIDATE & PRE-CONVERT PHOTOS BEFORE ANY AD PLAYS ──
            val userPhotoUri = _userPhoto.value
            val garmentUri = _garmentImage.value
            val product = _selectedProduct.value

            if (userPhotoUri == null) {
                _errorMessage.value = "Please select or upload your photo first"
                _processingState.value = ProcessingState.ERROR
                return@launch
            }

            val userPhotoFile = withContext(kotlinx.coroutines.Dispatchers.IO) { FileHelper.uriToFile(context, userPhotoUri) }
            val garmentFile = withContext(kotlinx.coroutines.Dispatchers.IO) {
                if (garmentUri != null) {
                    FileHelper.uriToFile(context, garmentUri)
                } else if (product != null) {
                    val file = downloadProductImage(product.image)
                    if (file != null && file.exists() && file.length() > 0) {
                        file
                    } else {
                        val retroUrl = com.jagtarapvtltd.tryzonai.network.RetrofitClient.BASE_URL.removeSuffix("/") + "/" + product.image.trimStart('/')
                        downloadProductImage(retroUrl)
                    }
                } else null
            }

            if (userPhotoFile == null || garmentFile == null) {
                _errorMessage.value = "Failed to process photos. Please re-select your photo."
                _processingState.value = ProcessingState.ERROR
                return@launch
            }

            // Photos & Network are 100% pre-validated! Cache files for guaranteed try-on execution.
            cachedUserPhotoFile = userPhotoFile
            cachedGarmentFile = garmentFile

            val prefs = context.getSharedPreferences("tryzon_user_prefs", android.content.Context.MODE_PRIVATE)
            val isFirstTryOnDone = prefs.getBoolean("is_lifetime_first_tryon_done", false)
            prefs.edit().putBoolean("is_premium_subscriber", isPremium).apply()

            if (!isFirstTryOnDone || isPremium) {
                if (!isFirstTryOnDone) {
                    prefs.edit().putBoolean("is_lifetime_first_tryon_done", true).apply()
                }
                submitTryOn()
                return@launch
            }

            val act = activity ?: pendingAdActivity?.get()
            if (act == null || !act.isAlive()) {
                submitTryOn()
                pendingAdActivity = null
                return@launch
            }

            // ── PRE-CHECK LIVE USER SERVER STATUS BEFORE PLAYING ANY AD ──
            val liveUser = try {
                withContext(kotlinx.coroutines.Dispatchers.IO) {
                    RetrofitClient.apiService.getMe()
                }
            } catch (e: Exception) {
                null
            }

            if (liveUser != null) {
                val liveIsPro = liveUser.is_premium || listOf("pro", "premium", "vip").contains(liveUser.subscription_tier?.lowercase())
                val liveTryOnsToday = liveUser.try_ons_today
                val liveDailyAds = liveUser.daily_reward_ad_count
                val liveCredits = liveUser.credits
                val livePaidCredits = liveUser.paid_credits

                if (!liveIsPro && liveTryOnsToday >= 1 && liveDailyAds >= 2) {
                    if (liveCredits <= 0 && livePaidCredits <= 0) {
                        _errorMessage.value = "Daily free try-ons & bonus ad limit reached (Max 3 tries/day). Top up credits or upgrade to Pro!"
                        _processingState.value = ProcessingState.ERROR
                        _limitReachedEvent.value = true
                        return@launch
                    }
                }
            }

            // ── 3. EVERYTHING IS PRE-VALIDATED & GUARANTEED -> NOW SHOW REWARDED VIDEO AD ──
            var wasRewardEarned = false
            adManager.showRewardedAd(
                activity = act,
                onAdDismissed = {
                    hasRewardAdBonus = true
                    submitTryOn()
                    pendingAdActivity = null
                },
                onRewardEarned = {
                    wasRewardEarned = true
                    hasRewardAdBonus = true
                    com.jagtarapvtltd.tryzonai.utils.AnalyticsHelper.logEvent("rewarded_ad_watched")
                },
                onAdFailedToLoad = {
                    hasRewardAdBonus = true
                    submitTryOn()
                    pendingAdActivity = null
                }
            )
        }
    }

    fun proceedWithAd(activity: android.app.Activity? = null) {
        val act = activity ?: pendingAdActivity?.get()
        if (act == null || !act.isAlive()) {
            submitTryOn()
            pendingAdActivity = null
            return
        }

        var wasRewardEarned = false
        adManager.showRewardedAd(
            activity = act,
            onAdDismissed = {
                hasRewardAdBonus = true
                submitTryOn()
                pendingAdActivity = null
            },
            onRewardEarned = {
                wasRewardEarned = true
                hasRewardAdBonus = true
            },
            onAdFailedToLoad = {
                hasRewardAdBonus = true
                submitTryOn()
                pendingAdActivity = null
            }
        )
    }

    fun cancelAd() {
        _processingState.value = ProcessingState.ERROR
        _errorMessage.value = "Try-On was cancelled."
        pendingAdActivity = null
    }

    private var pollingJob: kotlinx.coroutines.Job? = null

    fun submitTryOn(onSubmissionComplete: (() -> Unit)? = null) {
        val isAlreadyRunning = _processingState.value == ProcessingState.ANALYZING ||
                               _processingState.value == ProcessingState.EXTRACTING ||
                               _processingState.value == ProcessingState.FITTING ||
                               _processingState.value == ProcessingState.ENHANCING ||
                               _processingState.value == ProcessingState.FINALIZING ||
                               _processingState.value == ProcessingState.WAITING_FOR_AD

        if (isAlreadyRunning) {
            return
        }
        pollingJob?.cancel()
        _processingState.value = ProcessingState.ANALYZING
        _errorMessage.value = null
        _limitReachedEvent.value = false
        _tryOnResult.value = null

        viewModelScope.launch {
            try {
                val userPhotoUri = _userPhoto.value ?: return@launch
                val garmentUri = _garmentImage.value
                val product = _selectedProduct.value
                
                if (garmentUri == null && product == null) {
                    _errorMessage.value = "Please select a garment"
                    _processingState.value = ProcessingState.ERROR
                    return@launch
                }

                // Determine Garment Source
                val garmentSource = if (product != null) {
                    "catalog"
                } else if (garmentUri?.toString()?.contains("com.jagtarapvtltd.tryzonai") == true) {
                    "camera_or_sample"
                } else if (garmentUri?.toString()?.startsWith("content://media") == true || garmentUri?.toString()?.startsWith("content://com.android.providers") == true) {
                    "gallery"
                } else {
                    "shared_intent_or_external"
                }

                com.jagtarapvtltd.tryzonai.utils.AnalyticsHelper.logEvent("tryon_started", android.os.Bundle().apply { 
                    putString("garment_source", garmentSource)
                    putString("garment_name", product?.name ?: "Custom Upload")
                })
                
                val fUser = cachedUserPhotoFile
                val userPhotoFile = if (fUser != null && fUser.exists() && fUser.length() > 0) {
                    fUser
                } else {
                    withContext(kotlinx.coroutines.Dispatchers.IO) { FileHelper.uriToFile(context, userPhotoUri) }
                }
                
                // Handle garment file from Uri or URL
                val fGarm = cachedGarmentFile
                val garmentFile = if (fGarm != null && fGarm.exists() && fGarm.length() > 0) {
                    fGarm
                } else {
                    withContext(kotlinx.coroutines.Dispatchers.IO) {
                        if (garmentUri != null) {
                            FileHelper.uriToFile(context, garmentUri)
                        } else if (product != null) {
                            // Download product image if it's from catalog
                            val file = downloadProductImage(product.image)
                            if (file != null && file.exists() && file.length() > 0) {
                                file
                            } else {
                                val retroUrl = com.jagtarapvtltd.tryzonai.network.RetrofitClient.BASE_URL.removeSuffix("/") + "/" + product.image.trimStart('/')
                                downloadProductImage(retroUrl)
                            }
                        } else null
                    }
                }
                
                // Reset cached pre-validated files
                cachedUserPhotoFile = null
                cachedGarmentFile = null
                
                if (userPhotoFile == null || garmentFile == null) {
                    _errorMessage.value = "Failed to process photos. Please try picking the photo again."
                    _processingState.value = ProcessingState.ERROR
                    return@launch
                }

                if (!com.jagtarapvtltd.tryzonai.utils.NetworkMonitor.isInternetAvailable(context)) {
                    val persistentUserPhoto = File(context.filesDir, "offline_user_${System.currentTimeMillis()}.jpg")
                    val persistentGarment = File(context.filesDir, "offline_garment_${System.currentTimeMillis()}.jpg")
                    withContext(kotlinx.coroutines.Dispatchers.IO) {
                        userPhotoFile.copyTo(persistentUserPhoto, overwrite = true)
                        garmentFile.copyTo(persistentGarment, overwrite = true)
                    }

                    val inputData = androidx.work.workDataOf(
                        "userPhotoPath" to persistentUserPhoto.absolutePath,
                        "garmentPath" to persistentGarment.absolutePath,
                        "productId" to product?.id
                    )
                    val constraints = androidx.work.Constraints.Builder()
                        .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                        .build()
                    val request = androidx.work.OneTimeWorkRequestBuilder<com.jagtarapvtltd.tryzonai.workers.OfflineTryOnWorker>()
                        .setConstraints(constraints)
                        .setInputData(inputData)
                        .build()
                    androidx.work.WorkManager.getInstance(context).enqueue(request)
                    
                    _errorMessage.value = "Offline Mode: Your job is queued! It will run automatically when internet returns and notify you."
                    _processingState.value = ProcessingState.ERROR // This shows the message on the processing screen
                    return@launch
                }

                val personPart = MultipartBody.Part.createFormData(
                    "person_image", userPhotoFile.name, userPhotoFile.asRequestBody("image/*".toMediaTypeOrNull())
                )
                val garmentPart = MultipartBody.Part.createFormData(
                    "garment_image", garmentFile.name, garmentFile.asRequestBody("image/*".toMediaTypeOrNull())
                )
                
                val productIdPart = product?.id?.let {
                    it.toRequestBody("text/plain".toMediaTypeOrNull())
                }

                try {
                    val bonusHeader = if (hasRewardAdBonus) "true" else null
                    val submission = RetrofitClient.apiService.submitTryOn(personPart, garmentPart, productIdPart, isRewardAdBonus = bonusHeader)
                    hasRewardAdBonus = false
                    pollForStatus(submission.session_id)
                    onSubmissionComplete?.invoke()
                } catch (e: retrofit2.HttpException) {
                    if (e.code() == 401) {
                        _errorMessage.value = "Session expired. Please log in again to continue."
                        _processingState.value = ProcessingState.ERROR
                        _limitReachedEvent.value = true
                    } else if (e.code() == 403) {
                        try {
                            val errorBody = e.response()?.errorBody()?.string()
                            val detail = if (errorBody != null) org.json.JSONObject(errorBody).optString("detail") else ""
                            if (detail.contains("BANNED", ignoreCase = true) || detail.contains("Access Denied", ignoreCase = true)) {
                                _errorMessage.value = detail
                            } else if (detail.contains("Guest", ignoreCase = true)) {
                                setGuestExhausted()
                                _errorMessage.value = detail.ifEmpty { "Guest free limit reached." }
                                _limitReachedEvent.value = true
                            } else if (detail.contains("credits", ignoreCase = true) || detail.contains("limit", ignoreCase = true)) {
                                _errorMessage.value = detail.ifEmpty { "Insufficient credits." }
                            } else {
                                _errorMessage.value = detail.ifEmpty { "Access restricted." }
                            }
                        } catch (ex: Exception) {
                            _errorMessage.value = "Access restricted."
                        }
                        _processingState.value = ProcessingState.ERROR
                    } else if (e.code() == 429) {
                        try {
                            val errorBody = e.response()?.errorBody()?.string()
                            val detail = if (errorBody != null) org.json.JSONObject(errorBody).optString("detail") else ""
                            _errorMessage.value = detail.ifEmpty { "Too many requests. Please try again tomorrow." }
                        } catch (ex: Exception) {
                            _errorMessage.value = "Too many requests. Please try again tomorrow."
                        }
                        _processingState.value = ProcessingState.ERROR
                    } else if (e.code() == 409) {
                        _errorMessage.value = "You already have a Try-On currently processing! Please wait a few seconds for it to finish."
                        _processingState.value = ProcessingState.ERROR
                    } else if (e.code() == 400) {
                        try {
                            val errorString = e.response()?.errorBody()?.string()
                            if (errorString != null) {
                                val jsonObject = org.json.JSONObject(errorString)
                                val detail = jsonObject.optString("detail", "Invalid request")
                                _errorMessage.value = detail
                            } else {
                                _errorMessage.value = "Invalid request"
                            }
                        } catch (ex: Exception) {
                            _errorMessage.value = "Invalid request: ${e.message()}"
                        }
                        _processingState.value = ProcessingState.ERROR
                    } else if (e.code() == 413) {
                        try {
                            val errorBody = e.response()?.errorBody()?.string()
                            val detail = if (errorBody != null) org.json.JSONObject(errorBody).optString("detail") else ""
                            _errorMessage.value = detail.ifEmpty { "Selected image is too large. Please select a smaller photo." }
                        } catch (ex: Exception) {
                            _errorMessage.value = "Selected image is too large. Please select a smaller photo."
                        }
                        _processingState.value = ProcessingState.ERROR
                    } else if (e.code() >= 500) {
                        try {
                            val errorBody = e.response()?.errorBody()?.string()
                            val detail = if (errorBody != null) org.json.JSONObject(errorBody).optString("detail") else ""
                            _errorMessage.value = detail.ifEmpty { "Server is temporarily busy. Please try again in a few moments." }
                        } catch (ex: Exception) {
                            _errorMessage.value = "Server error occurred (${e.code()}). Please try again."
                        }
                        _processingState.value = ProcessingState.ERROR
                    } else {
                        try {
                            val errorBody = e.response()?.errorBody()?.string()
                            val detail = if (errorBody != null) org.json.JSONObject(errorBody).optString("detail") else ""
                            _errorMessage.value = detail.ifEmpty { "Request failed (${e.code()}). Please try again." }
                        } catch (ex: Exception) {
                            _errorMessage.value = "Server Error (${e.code()}): ${e.message()}"
                        }
                        _processingState.value = ProcessingState.ERROR
                    }
                } catch (e: Exception) {
                    android.util.Log.e("TryZonAPI", "Network Error: ${e.message}")
                    _errorMessage.value = "Network Error: ${e.message}"
                    _processingState.value = ProcessingState.ERROR
                } finally {
                    FileHelper.clearCache(context)
                }
            } catch (e: Exception) {
                android.util.Log.e("TryZonAPI", "Try-On Submission Error: ${e.message}", e)
                _processingState.value = ProcessingState.ERROR
                _errorMessage.value = "An error occurred: ${e.message}"
            }
        }
    }

    private suspend fun downloadProductImage(imagePath: String): File? {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val file = File(context.cacheDir, "temp_garment_${System.currentTimeMillis()}.jpg")
                
                // Case 1: Local Resource ID starting with "res:"
                if (imagePath.startsWith("res:")) {
                    val resId = imagePath.removePrefix("res:").toIntOrNull()
                    if (resId != null) {
                        val bitmap = android.graphics.BitmapFactory.decodeResource(context.resources, resId)
                        if (bitmap != null) {
                            file.outputStream().use { out ->
                                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, out)
                            }
                            return@withContext file
                        }
                    }
                }

                // Case 2: Resource / Content / File URI
                val fullUrl = com.jagtarapvtltd.tryzonai.utils.UrlUtils.getFullUrl(imagePath)
                if (fullUrl.startsWith("android.resource://") || fullUrl.startsWith("content://") || fullUrl.startsWith("file://")) {
                    val uri = android.net.Uri.parse(fullUrl)
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        file.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (file.exists() && file.length() > 0) return@withContext file
                }

                // Case 3: HTTP/HTTPS Remote Web URL
                val downloadUrl = if (fullUrl.contains("localhost")) {
                    fullUrl.replace("http://localhost:8000", "https://api.tryzonai.com")
                } else fullUrl

                val request = okhttp3.Request.Builder()
                    .url(downloadUrl)
                    .header("User-Agent", "TryZonAI-AndroidApp")
                    .build()
                
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                
                val response = client.newCall(request).execute()
                val body = response.body
                if (response.isSuccessful) {
                    body.byteStream().use { input ->
                        file.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (file.exists() && file.length() > 0) return@withContext file
                }
                
                android.util.Log.e("TryOnViewModel", "Download failed for $imagePath, HTTP code: ${response.code}")
                null
            } catch (e: Exception) {
                android.util.Log.e("TryOnViewModel", "downloadProductImage exception for $imagePath", e)
                null
            }
        }
    }

    private fun pollForStatus(sessionId: Int) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            var isComplete = false
            var retryCount = 0
            val maxRetries = 150 // 5 minutes max

            while (!isComplete && retryCount < maxRetries) {
                try {
                    val statusResponse = RetrofitClient.apiService.getTryOnStatus(sessionId)
                    android.util.Log.d("TryZonAPI", "Status Check: ${statusResponse.status} for session $sessionId")
                    
                    when (statusResponse.status) {
                        "done" -> {
                            isComplete = true
                            incrementGuestTryOns()
                            com.jagtarapvtltd.tryzonai.utils.AnalyticsHelper.logEvent("tryon_completed")
                            android.util.Log.i("TryZonAPI", "Try-On Complete! Result URL: ${statusResponse.result_url}")
                            _tryOnResult.value = TryOnResult(
                                originalPhoto = _userPhoto.value?.toString() ?: "",
                                resultImage = statusResponse.result_url ?: "",
                                complements = statusResponse.complements ?: emptyList(),
                                priceOptions = statusResponse.price_options ?: emptyList()
                            )
                            _processingState.value = ProcessingState.COMPLETED
                            
                            sessionManager.addLocalHistoryItem(
                                com.jagtarapvtltd.tryzonai.network.TryOnHistoryItem(
                                    id = sessionId.toString(),
                                    product_id = _selectedProduct.value?.id,
                                    result_url = statusResponse.result_url ?: "",
                                    timestamp = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date()),
                                    garment_name = _selectedProduct.value?.name
                                )
                            )
                            com.jagtarapvtltd.tryzonai.utils.GoogleDriveSyncManager.autoSyncSessionManager(context, sessionManager)
                            
                            sendCompletionNotification(sessionId)
                        }
                        "failed" -> {
                            isComplete = true
                            android.util.Log.e("TryZonAPI", "Try-On Failed on server")
                            _errorMessage.value = "AI processing failed or image was blocked by safety filters."
                            _processingState.value = ProcessingState.ERROR
                        }
                        "processing" -> updateProgressStage(retryCount)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("TryZonAPI", "Error polling status: ${e.message}")
                }
                kotlinx.coroutines.delay(2000)
                retryCount++
            }
            if (!isComplete && _errorMessage.value == null) {
                _errorMessage.value = "Processing timed out"
                _processingState.value = ProcessingState.ERROR
            }
        }
    }

    private fun updateProgressStage(count: Int) {
        _processingState.value = when {
            count < 5 -> ProcessingState.ANALYZING
            count < 10 -> ProcessingState.EXTRACTING
            count < 20 -> ProcessingState.FITTING
            count < 25 -> ProcessingState.ENHANCING
            else -> ProcessingState.FINALIZING
        }
    }
    
    private suspend fun processWithStages() {
        _processingState.value = ProcessingState.ANALYZING
        delay(1000)
        _processingState.value = ProcessingState.EXTRACTING
        delay(1000)
        _processingState.value = ProcessingState.FITTING
        delay(1000)
        _processingState.value = ProcessingState.ENHANCING
        delay(1000)
        _processingState.value = ProcessingState.FINALIZING
        delay(500)
        generateMockResult()
        _processingState.value = ProcessingState.COMPLETED
        sendCompletionNotification(null)
    }
    
    fun generateMockResult() {
        _tryOnResult.value = TryOnResult(
            originalPhoto = _userPhoto.value?.toString() ?: "res:${com.jagtarapvtltd.tryzonai.R.drawable.sample_model_female}",
            resultImage = "res:${com.jagtarapvtltd.tryzonai.R.drawable.uae_var}",
            complements = listOf(
                ComplementProduct("1", "Sunglasses", "Accessories", 0, "https://images.unsplash.com/photo-1511499767150-a48a237f0083?w=500", "https://myntr.it/sQ4bpfb", 95),
                ComplementProduct("2", "Watch", "Accessories", 0, "https://images.unsplash.com/photo-1524805444758-089113d48a6d?w=500", "https://myntr.it/sQ4bpfb", 96),
                ComplementProduct("3", "Shoes", "Footwear", 0, "https://images.unsplash.com/photo-1549298916-b41d501d3772?w=500", "https://myntr.it/sQ4bpfb", 94),
                ComplementProduct("4", "Bag", "Accessories", 0, "https://images.unsplash.com/photo-1553062407-98eeb64c6a62?w=500", "https://myntr.it/sQ4bpfb", 95),
                ComplementProduct("5", "Jacket", "Outerwear", 0, "https://images.unsplash.com/photo-1551028719-00167b16eac5?w=500", "https://myntr.it/sQ4bpfb", 93)
            ),
            priceOptions = emptyList()
        )
        _processingState.value = ProcessingState.COMPLETED
    }
    
    fun retryTryOn(item: TryOnHistoryItem, activity: android.app.Activity?, isPremium: Boolean) {
        _processingState.value = ProcessingState.ANALYZING
        submitTryOnWithAd(activity, isPremium)
    }

    fun loadSession(sessionId: Int) {
        if (_tryOnResult.value?.resultImage != null && _processingState.value == ProcessingState.COMPLETED) {
            return
        }
        viewModelScope.launch {
            _processingState.value = ProcessingState.ANALYZING
            _errorMessage.value = null
            try {
                // Instantly open the correct result from local history if it exists
                val localItems = sessionManager.localHistory.first()
                val existing = localItems.find { it.id == sessionId.toString() }
                if (existing != null && existing.result_url.isNotEmpty()) {
                    _tryOnResult.value = TryOnResult(
                        originalPhoto = _userPhoto.value?.toString() ?: "",
                        resultImage = existing.result_url,
                        complements = listOf(
                            ComplementProduct("1", "Sunglasses", "Accessories", 0, "https://images.unsplash.com/photo-1511499767150-a48a237f0083?w=500", "https://myntr.it/sQ4bpfb", 95),
                            ComplementProduct("2", "Watch", "Accessories", 0, "https://images.unsplash.com/photo-1524805444758-089113d48a6d?w=500", "https://myntr.it/sQ4bpfb", 96),
                            ComplementProduct("3", "Shoes", "Footwear", 0, "https://images.unsplash.com/photo-1549298916-b41d501d3772?w=500", "https://myntr.it/sQ4bpfb", 94)
                        ),
                        priceOptions = emptyList()
                    )
                    _processingState.value = ProcessingState.COMPLETED
                    return@launch
                }

                val response = RetrofitClient.apiService.getTryOnStatus(sessionId)
                if (response.status == "done" && !response.result_url.isNullOrEmpty()) {
                    _tryOnResult.value = TryOnResult(
                        originalPhoto = _userPhoto.value?.toString() ?: (response.original_url ?: ""),
                        resultImage = response.result_url,
                        complements = if (!response.complements.isNullOrEmpty()) response.complements else listOf(
                            ComplementProduct("1", "Sunglasses", "Accessories", 0, "https://images.unsplash.com/photo-1511499767150-a48a237f0083?w=500", "https://myntr.it/sQ4bpfb", 95),
                            ComplementProduct("2", "Watch", "Accessories", 0, "https://images.unsplash.com/photo-1524805444758-089113d48a6d?w=500", "https://myntr.it/sQ4bpfb", 96),
                            ComplementProduct("3", "Shoes", "Footwear", 0, "https://images.unsplash.com/photo-1549298916-b41d501d3772?w=500", "https://myntr.it/sQ4bpfb", 94)
                        ),
                        priceOptions = response.price_options ?: emptyList()
                    )
                    _processingState.value = ProcessingState.COMPLETED
                    viewModelScope.launch {
                        sessionManager.addLocalHistoryItem(
                            com.jagtarapvtltd.tryzonai.network.TryOnHistoryItem(
                                id = sessionId.toString(),
                                product_id = _selectedProduct.value?.id,
                                result_url = response.result_url,
                                timestamp = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date()),
                                garment_name = _selectedProduct.value?.name
                            )
                        )
                        com.jagtarapvtltd.tryzonai.utils.GoogleDriveSyncManager.autoSyncSessionManager(context, sessionManager)
                    }
                    return@launch
                } else if (response.status == "failed") {
                    _errorMessage.value = "AI processing failed"
                    _processingState.value = ProcessingState.ERROR
                } else {
                    pollForStatus(sessionId)
                }
            } catch (e: Exception) {
                if (_tryOnResult.value == null) {
                    _errorMessage.value = "Failed to load session: ${e.message}"
                    _processingState.value = ProcessingState.ERROR
                }
            }
        }
    }
    
    fun resetProcessingState() {
        _processingState.value = ProcessingState.IDLE
        _errorMessage.value = null
        _tryOnResult.value = null
    }

    fun reset() {
        _garmentImage.value = null
        _userPhoto.value = null
        _processingState.value = ProcessingState.IDLE
        _tryOnResult.value = null
        _errorMessage.value = null
    }
}

enum class ProcessingState(val step: Int) {
    IDLE(0), WAITING_FOR_AD(0), ANALYZING(1), EXTRACTING(2), FITTING(3), ENHANCING(4), FINALIZING(5), COMPLETED(6), ERROR(-1)
}
