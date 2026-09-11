// app/src/main/java/com.jagtarapvtltd.tryzonai/MainActivity.kt
package com.jagtarapvtltd.tryzonai

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.*
import com.jagtarapvtltd.tryzonai.ui.components.bounceClick
import com.jagtarapvtltd.tryzonai.ui.components.ShimmeringButton
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.jagtarapvtltd.tryzonai.ui.theme.TryZonAITheme
import com.jagtarapvtltd.tryzonai.ui.theme.*
import com.jagtarapvtltd.tryzonai.ui.screens.*
import com.jagtarapvtltd.tryzonai.viewmodel.*
import com.jagtarapvtltd.tryzonai.network.*
import com.jagtarapvtltd.tryzonai.utils.findActivity
import androidx.navigation.NavGraph.Companion.findStartDestination
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/**
 * Navigation Destinations (Type-Safe Navigation - Modern 2026 Standard)
 */
@Serializable object Home
@Serializable object Catalog
@Serializable object TryOnUpload
@Serializable object TryOnCapture
@Serializable object TryOnProcessing
@Serializable object TryOnResult
@Serializable data class ProductDetail(val productId: String)
@Serializable object Inspiration
@Serializable data class InspirationDetail(val lookId: String)
@Serializable object Wardrobe
@Serializable object Closet
@Serializable object Premium
@Serializable object CheckoutDestination
@Serializable object Login
@Serializable object Register
@Serializable object Onboarding

/**
 * TryZon AI - Complete Android App
 * AI-Powered Virtual Try-On & Fashion Discovery Platform
 * Upgraded for May 2026: Type-Safe Navigation, Coil 3, Target SDK 37
 */
class MainActivity : ComponentActivity() {
    private val _intentFlow = kotlinx.coroutines.flow.MutableStateFlow<android.content.Intent?>(null)
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        _intentFlow.value = intent
    }
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_BACKGROUND || level >= TRIM_MEMORY_MODERATE) {
            System.gc()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _intentFlow.value = intent
        
        // Edge-to-Edge is default in 2026, but we ensure it here
        enableEdgeToEdge()
        
        // Non-UI Initialization offloaded to Dispatchers.IO to achieve < 3.0% Cold Start Vitals threshold
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            // Init Analytics
            com.jagtarapvtltd.tryzonai.utils.AnalyticsHelper.init(this@MainActivity)
            
            // Persist X-Session-ID across app restarts
            com.jagtarapvtltd.tryzonai.network.RetrofitClient.initSession(this@MainActivity)
            
            // User-Owned Cloud Sync (Restore/Sync history with user's personal cloud vault)
            val sessionMgr = com.jagtarapvtltd.tryzonai.utils.SessionManager(this@MainActivity)
            com.jagtarapvtltd.tryzonai.utils.GoogleDriveSyncManager.autoSyncSessionManager(this@MainActivity, sessionMgr)

            // Schedule Daily Push Notifications via WorkManager
            setupDailyReminders()

            // Initialize AdMob SDK with Test Device Configuration
            val testDeviceIds = listOf("6981B0FFEFA155A36EE3AC1F91C93A00", com.google.android.gms.ads.AdRequest.DEVICE_ID_EMULATOR)
            val configuration = com.google.android.gms.ads.RequestConfiguration.Builder()
                .setTestDeviceIds(testDeviceIds)
                .build()
            com.google.android.gms.ads.MobileAds.setRequestConfiguration(configuration)

            com.google.android.gms.ads.MobileAds.initialize(this@MainActivity) {
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                    com.jagtarapvtltd.tryzonai.utils.AdManager.getInstance(this@MainActivity).loadRewardedAd()
                }
            }
        }

        // Request Notification Permission on Android 13+ (API 33+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
        
        setContent {
            var isDarkTheme by remember { mutableStateOf(false) }
            
            TryZonAITheme(darkTheme = isDarkTheme, dynamicColor = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TryZonAIApp(
                        intentFlow = _intentFlow,
                        isDarkTheme = isDarkTheme,
                        onThemeToggle = { isDarkTheme = !isDarkTheme }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            checkShoppingClipboard()
            checkRecentScreenshot()
        }
    }

    private fun checkRecentScreenshot() {
        try {
            val projection = arrayOf(
                android.provider.MediaStore.Images.Media._ID,
                android.provider.MediaStore.Images.Media.DATA,
                android.provider.MediaStore.Images.Media.DATE_ADDED
            )
            val sortOrder = "${android.provider.MediaStore.Images.Media.DATE_ADDED} DESC"
            val cursor = contentResolver.query(
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    val pathIndex = it.getColumnIndex(android.provider.MediaStore.Images.Media.DATA)
                    val dateIndex = it.getColumnIndex(android.provider.MediaStore.Images.Media.DATE_ADDED)

                    if (pathIndex != -1 && dateIndex != -1) {
                        val path = it.getString(pathIndex) ?: ""
                        val dateAdded = it.getLong(dateIndex)
                        val currentTime = System.currentTimeMillis() / 1000

                        val isScreenshot = path.lowercase().contains("screenshot") || path.lowercase().contains("screenshots")
                        val isRecent = (currentTime - dateAdded) < 180 // Within 3 minutes

                        if (isScreenshot && isRecent) {
                            val prefs = getSharedPreferences("try_on_prefs", MODE_PRIVATE)
                            val lastNotifiedScreenshot = prefs.getString("last_screenshot_path", "")
                            if (lastNotifiedScreenshot != path) {
                                prefs.edit().putString("last_screenshot_path", path).apply()
                                sendScreenshotNotification(path)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun sendScreenshotNotification(imagePath: String) {
        val channelId = "tryzon_daily_reminders"
        val notificationManager = getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

        val intent = android.content.Intent(this, MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("from_notification", true)
            putExtra("notification_type", "screenshot_detected")
            putExtra("shared_image_path", imagePath)
        }

        val pendingIntent = android.app.PendingIntent.getActivity(
            this,
            2003,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or (if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) android.app.PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val notification = androidx.core.app.NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification_t)
            .setContentTitle("📸 Captured an outfit screenshot?")
            .setContentText("New outfit screenshot detected! Tap to try this clothes on YOUR body photo now! 👗✨")
            .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText("New outfit screenshot detected! Tap to try this clothes on YOUR body photo right now in TryZon AI! 👗✨"))
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setDefaults(androidx.core.app.NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(4004, notification)
    }

    private fun checkShoppingClipboard() {
        try {
            val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            if (clipboard != null && clipboard.hasPrimaryClip()) {
                val item = clipboard.primaryClip?.getItemAt(0)
                val clipText = item?.text?.toString() ?: item?.uri?.toString() ?: ""
                val lowerText = clipText.lowercase()
                val isShoppingUrl = lowerText.contains("myntra") || lowerText.contains("meesho") ||
                        lowerText.contains("amazon") || lowerText.contains("ajio") ||
                        lowerText.contains("flipkart") || lowerText.contains("zara") ||
                        lowerText.contains("nykaa") || lowerText.endsWith(".jpg") ||
                        lowerText.endsWith(".png") || lowerText.endsWith(".webp")

                if (isShoppingUrl) {
                    val prefs = getSharedPreferences("try_on_prefs", MODE_PRIVATE)
                    val lastNotified = prefs.getString("last_clipboard_url", "")
                    if (lastNotified != clipText) {
                        prefs.edit().putString("last_clipboard_url", clipText).apply()
                        sendShoppingNotification(clipText)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun sendShoppingNotification(clipText: String) {
        val channelId = "tryzon_daily_reminders"
        val notificationManager = getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

        val intent = android.content.Intent(this, MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("from_notification", true)
            putExtra("notification_type", "shopping_detected")
            putExtra("shared_url", clipText)
        }

        val pendingIntent = android.app.PendingIntent.getActivity(
            this,
            2002,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or (if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) android.app.PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val notification = androidx.core.app.NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification_t)
            .setContentTitle("🛍️ Saw a cool outfit while shopping?")
            .setContentText("Copied clothing link detected! Tap to see how it looks on YOU in 5 seconds! 👗✨")
            .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText("Copied clothing link detected! Tap to see how this outfit looks on YOU right now in TryZon AI! 👗✨"))
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setDefaults(androidx.core.app.NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(3003, notification)
    }

    private fun setupDailyReminders() {
        try {
            val workManager = androidx.work.WorkManager.getInstance(applicationContext)

            // 1. Same-day engagement reminder (triggers 6 hours after app launch)
            val sameDayReminderRequest = androidx.work.OneTimeWorkRequestBuilder<com.jagtarapvtltd.tryzonai.workers.DailyReminderWorker>()
                .setInitialDelay(6, java.util.concurrent.TimeUnit.HOURS)
                .setConstraints(
                    androidx.work.Constraints.Builder()
                        .setRequiredNetworkType(androidx.work.NetworkType.NOT_REQUIRED)
                        .build()
                )
                .build()

            workManager.enqueueUniqueWork(
                "TryZonSameDayReminder",
                androidx.work.ExistingWorkPolicy.KEEP,
                sameDayReminderRequest
            )

            // 2. Periodic Daily Reminder every 24 hours (UPDATE policy keeps it alive across restarts)
            val dailyReminderRequest = androidx.work.PeriodicWorkRequestBuilder<com.jagtarapvtltd.tryzonai.workers.DailyReminderWorker>(
                24, java.util.concurrent.TimeUnit.HOURS
            )
                .setInitialDelay(20, java.util.concurrent.TimeUnit.HOURS)
                .setConstraints(
                    androidx.work.Constraints.Builder()
                        .setRequiredNetworkType(androidx.work.NetworkType.NOT_REQUIRED)
                        .build()
                )
                .build()

            workManager.enqueueUniquePeriodicWork(
                "TryZonDailyReminderWorker",
                androidx.work.ExistingPeriodicWorkPolicy.UPDATE,
                dailyReminderRequest
            )

            // 3. Periodic Daily AI Style Engine every 24 hours (Starts from Day 2 to preserve Day 1 manual try-on)
            val dailyStyleRequest = androidx.work.PeriodicWorkRequestBuilder<com.jagtarapvtltd.tryzonai.workers.DailyStyleWorker>(
                24, java.util.concurrent.TimeUnit.HOURS
            )
                .setInitialDelay(24, java.util.concurrent.TimeUnit.HOURS)
                .setConstraints(
                    androidx.work.Constraints.Builder()
                        .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                        .build()
                )
                .build()

            workManager.enqueueUniquePeriodicWork(
                "TryZonDailyStyleWorker",
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                dailyStyleRequest
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

@Composable
fun TryZonAIApp(
    intentFlow: kotlinx.coroutines.flow.StateFlow<android.content.Intent?> = kotlinx.coroutines.flow.MutableStateFlow(null),
    isDarkTheme: Boolean,
    onThemeToggle: () -> Unit
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    
    // Dual-Layer Force Update System (Google Play In-App Update API + Remote Backend Version Gate)
    val forceUpdateManager = remember { com.jagtarapvtltd.tryzonai.utils.ForceUpdateManager(context) }
    var forceUpdateConfig by remember { mutableStateOf<com.jagtarapvtltd.tryzonai.utils.RemoteVersionConfig?>(null) }
    var showForceUpdateModal by remember { mutableStateOf(false) }

    // Permission Handling for Notifications (Android 13+) & Force Update Check
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.util.Log.d("CountryDetectorTest", "=== LIVE COUNTRY & CURRENCY VERIFICATION ===")
            android.util.Log.d("CountryDetectorTest", "Detected Country: ${com.jagtarapvtltd.tryzonai.utils.CountryDetector.getUserCountry(context)}")
        }
        
        val activity = context.findActivity()
        if (activity != null) {
            forceUpdateManager.checkResumeUpdate(activity)
            forceUpdateManager.checkForPlayStoreUpdate(activity) {
                // Fallback to backend remote version check if Play Store in-app update isn't active yet
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                    val remote = forceUpdateManager.fetchRemoteMinVersion()
                    if (remote != null && com.jagtarapvtltd.tryzonai.BuildConfig.VERSION_CODE < remote.minRequiredVersionCode) {
                        forceUpdateConfig = remote
                        showForceUpdateModal = true
                    }
                }
            }
        }
    }

    val currentConfig = forceUpdateConfig
    if (showForceUpdateModal && currentConfig != null) {
        val config = currentConfig
        AlertDialog(
            onDismissRequest = { /* Non-dismissible mandatory update */ },
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(config.title, fontWeight = FontWeight.Black, fontSize = 18.sp, textAlign = TextAlign.Center)
                }
            },
            text = {
                Text(
                    config.message,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = { forceUpdateManager.openPlayStorePage() },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold)
                ) {
                    Text("UPDATE FROM PLAY STORE 🛍️", color = Color.Black, fontWeight = FontWeight.Black)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // Track navigation to show/hide bottom bar
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Automatic Screen Tracking for Firebase Analytics
    LaunchedEffect(currentDestination) {
        currentDestination?.route?.let { route ->
            // Extract the clean screen name from the full route (e.g. com.jagtarapvtltd.tryzonai.Home -> Home)
            val screenName = route.substringAfterLast(".").substringBefore("/")
            val bundle = android.os.Bundle().apply {
                putString(com.google.firebase.analytics.FirebaseAnalytics.Param.SCREEN_NAME, screenName)
                putString(com.google.firebase.analytics.FirebaseAnalytics.Param.SCREEN_CLASS, "MainActivity")
            }
            com.jagtarapvtltd.tryzonai.utils.AnalyticsHelper.logEvent(com.google.firebase.analytics.FirebaseAnalytics.Event.SCREEN_VIEW, bundle)
        }
    }
    
    var isDrawerOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(currentDestination) {
        isDrawerOpen = false
    }

    // Logic to hide elements on specific screens
    val showBottomBar = when {
        currentDestination?.route?.contains("TryOnCapture") == true -> false
        currentDestination?.route?.contains("Onboarding") == true -> false
        currentDestination?.route?.contains("TryOnProcessing") == true -> false
        else -> true
    }

    val showHeader = when {
        currentDestination?.route?.contains("TryOnCapture") == true -> false
        currentDestination?.route?.contains("Onboarding") == true -> false
        currentDestination?.route?.contains("TryOnProcessing") == true -> false
        else -> true
    }
    
    val canNavigateBack = currentDestination?.route != TryOnUpload::class.qualifiedName &&
                         currentDestination?.route != Home::class.qualifiedName && 
                         currentDestination?.route != Catalog::class.qualifiedName &&
                         currentDestination?.route != Wardrobe::class.qualifiedName
    
    val sessionManager = remember { com.jagtarapvtltd.tryzonai.utils.SessionManager(context) }
    val initialOnboardingDone = remember { sessionManager.isOnboardingCompletedSync() }
    val isOnboardingCompleted by sessionManager.isOnboardingCompleted.collectAsState(initial = initialOnboardingDone)
    val startDestinationRoute = remember { if (initialOnboardingDone) TryOnUpload else Onboarding }
    val tryOnViewModel: TryOnViewModel = viewModel()
    val authViewModel: AuthViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return AuthViewModel(sessionManager) as T
            }
        }
    )

    // ── Contextual Guided Tour 2.0 Global State ──
    val prefs = remember { context.getSharedPreferences("try_on_prefs", android.content.Context.MODE_PRIVATE) }
    var showGuidedTour by remember { mutableStateOf(false) }
    var guidedTourStep by remember { mutableStateOf(0) }
    val tourStepBoundsMap = remember { mutableStateMapOf<Int, androidx.compose.ui.geometry.Rect>() }

    LaunchedEffect(isOnboardingCompleted) {
        if (isOnboardingCompleted && !prefs.getBoolean("has_completed_guided_tour", false)) {
            // Disabled full screen dark canvas tour in favor of native TryOnUploadScreen hand demo
            prefs.edit().putBoolean("has_completed_guided_tour", true).apply()
            showGuidedTour = false
        }
    }

    val tourSteps = remember {
        listOf(
            com.jagtarapvtltd.tryzonai.ui.components.TourStepData(
                icon = "📸",
                title = "1. Choose Your Photo",
                description = "Upload a clear, front-facing portrait or full-body photo of yourself in good lighting.",
                explanation = "💡 PRO TIP: Clear posture gives 100% realistic AI clothing fitting!",
                ctaText = "Next Step →"
            ),
            com.jagtarapvtltd.tryzonai.ui.components.TourStepData(
                icon = "👗",
                title = "2. Pick Outfit or Paste Link",
                description = "Select styles from our catalog or paste any product link from Myntra, Flipkart, or Meesho.",
                explanation = "💡 PRO TIP: Works with dresses, suits, jackets, ethnic wear & casual outfits!",
                ctaText = "Next Step →"
            ),
            com.jagtarapvtltd.tryzonai.ui.components.TourStepData(
                icon = "✨",
                title = "3. Instant Virtual Try-On",
                description = "Tap Generate to see realistic AI clothing fitting on your photo in seconds!",
                explanation = "💡 PRO TIP: Save your look to Magic Closet or download HD results ad-free.",
                ctaText = "Try It Yourself 🚀"
            )
        )
    }

    androidx.activity.compose.BackHandler(enabled = isDrawerOpen) {
        isDrawerOpen = false
    }

    androidx.activity.compose.BackHandler(enabled = showGuidedTour) {
        if (guidedTourStep > 0) {
            guidedTourStep--
        } else {
            prefs.edit().putBoolean("has_completed_guided_tour", true).apply()
            showGuidedTour = false
        }
    }

    val incomingIntent by intentFlow.collectAsState()

    // Deep Link Handling for System Notifications & Intent Shares
    LaunchedEffect(incomingIntent) {
        try {
            val activity = context.findActivity()
            val intent = incomingIntent ?: activity?.intent
            if (intent == null) return@LaunchedEffect

            // Extract deep-link type and session parameters robustly
            val rawSessionId = intent.extras?.get("session_id")
            val sessionId = when (rawSessionId) {
                is Int -> rawSessionId
                is String -> rawSessionId.toIntOrNull()
                is Long -> rawSessionId.toInt()
                else -> null
            }

            val type = intent.getStringExtra("deep_link_type") 
                ?: intent.getStringExtra("type") 
                ?: intent.getStringExtra("notification_type")
                ?: intent.extras?.getString("type")
                ?: intent.extras?.getString("notification_type")

            val notificationType = intent.getStringExtra("notification_type") 
                ?: intent.extras?.getString("notification_type")

            val isFromNotification = intent.getBooleanExtra("from_notification", false) 
                || intent.extras?.getBoolean("from_notification") == true
                || intent.hasExtra("notification_type") 
                || intent.hasExtra("deep_link_type")

            // 1. Handle Incoming Image/Text Share (from Gallery or Browser)
            if (intent.action == android.content.Intent.ACTION_SEND) {
                if (intent.type?.startsWith("image/") == true) {
                    val imageUri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(android.content.Intent.EXTRA_STREAM, android.net.Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra<android.net.Uri>(android.content.Intent.EXTRA_STREAM)
                    }
                    imageUri?.let { uri ->
                        tryOnViewModel.setGarmentImage(uri)
                        navController.navigate(com.jagtarapvtltd.tryzonai.TryOnUpload) { launchSingleTop = true }
                    }
                    intent.action = null
                } else if (intent.type == "text/plain") {
                    val streamUri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(android.content.Intent.EXTRA_STREAM, android.net.Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra<android.net.Uri>(android.content.Intent.EXTRA_STREAM)
                    }
                    if (streamUri != null) {
                        tryOnViewModel.setGarmentImage(streamUri)
                        navController.navigate(com.jagtarapvtltd.tryzonai.TryOnUpload) { launchSingleTop = true }
                        intent.action = null
                    } else {
                        val text = intent.getStringExtra(android.content.Intent.EXTRA_TEXT) ?: ""
                        if (text.startsWith("http://") || text.startsWith("https://")) {
                            tryOnViewModel.extractProductFromUrl(text)
                            navController.navigate(com.jagtarapvtltd.tryzonai.TryOnUpload) { launchSingleTop = true }
                            intent.action = null
                        }
                    }
                }
            }

            // 2. Direct Session Result Deep Link
            if (type == "session_result" && sessionId != null) {
                val resultUrl = intent.getStringExtra("result_url")
                if (!resultUrl.isNullOrEmpty()) {
                    tryOnViewModel.setTryOnResult(resultUrl, sessionId.toString())
                    kotlinx.coroutines.delay(200)
                    navController.navigate(com.jagtarapvtltd.tryzonai.TryOnResult) {
                        launchSingleTop = true
                    }
                } else {
                    tryOnViewModel.loadSession(sessionId)
                }
                intent.removeExtra("deep_link_type")
                intent.removeExtra("type")
                intent.removeExtra("session_id")
                intent.removeExtra("notification_type")
                intent.removeExtra("result_url")
                intent.removeExtra("original_photo")
                intent.removeExtra("from_notification")
                activity?.intent?.removeExtra("deep_link_type")
                activity?.intent?.removeExtra("type")
                activity?.intent?.removeExtra("session_id")
                activity?.intent?.removeExtra("notification_type")
                activity?.intent?.removeExtra("result_url")
                activity?.intent?.removeExtra("original_photo")
                activity?.intent?.removeExtra("from_notification")
                return@LaunchedEffect
            }

            // 3. Instant Daily Style Work Test
            if (intent.getBooleanExtra("test_instant_daily_style", false)) {
                val testDay = intent.getStringExtra("test_day")
                val dataBuilder = androidx.work.Data.Builder()
                if (!testDay.isNullOrEmpty()) {
                    dataBuilder.putString("test_day", testDay)
                }
                val testWork = androidx.work.OneTimeWorkRequestBuilder<com.jagtarapvtltd.tryzonai.workers.DailyStyleWorker>()
                    .setInputData(dataBuilder.build())
                    .setConstraints(
                        androidx.work.Constraints.Builder()
                            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                            .build()
                    )
                    .build()
                androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
                    "TryZonDailyStyleInstantTest",
                    androidx.work.ExistingWorkPolicy.REPLACE,
                    testWork
                )
                android.widget.Toast.makeText(context, "⚡ Daily AI Style Generation Started in Background!", android.widget.Toast.LENGTH_LONG).show()
                intent.removeExtra("test_instant_daily_style")
                intent.removeExtra("test_day")
                activity?.intent?.removeExtra("test_instant_daily_style")
            }

            // 4. Daily AI Style Notification (`daily_style`)
            if (notificationType == "daily_style") {
                val resultUrl = intent.getStringExtra("daily_style_url")
                val sessIdStr = intent.getStringExtra("session_id")
                if (!resultUrl.isNullOrEmpty() && !sessIdStr.isNullOrEmpty()) {
                    tryOnViewModel.setTryOnResult(resultUrl, sessIdStr)
                    kotlinx.coroutines.delay(200)
                    navController.navigate(com.jagtarapvtltd.tryzonai.TryOnResult) {
                        launchSingleTop = true
                    }
                } else {
                    navController.navigate(com.jagtarapvtltd.tryzonai.TryOnUpload) {
                        launchSingleTop = true
                    }
                }
                intent.removeExtra("notification_type")
                intent.removeExtra("daily_style_url")
                intent.removeExtra("from_notification")
                activity?.intent?.removeExtra("notification_type")
                return@LaunchedEffect
            } 
            
            // 5. Daily Reminder Notification (`daily_reminder`)
            if (notificationType == "daily_reminder") {
                navController.navigate(com.jagtarapvtltd.tryzonai.TryOnUpload) {
                    launchSingleTop = true
                }
                intent.removeExtra("notification_type")
                intent.removeExtra("from_notification")
                activity?.intent?.removeExtra("notification_type")
                return@LaunchedEffect
            }
            
            // 6. Shopping App Link Detected Notification (`shopping_detected`)
            if (notificationType == "shopping_detected") {
                val sharedUrl = intent.getStringExtra("shared_url")
                if (!sharedUrl.isNullOrEmpty()) {
                    tryOnViewModel.extractProductFromUrl(sharedUrl)
                    navController.navigate(com.jagtarapvtltd.tryzonai.TryOnUpload) {
                        launchSingleTop = true
                    }
                }
                intent.removeExtra("notification_type")
                intent.removeExtra("shared_url")
                activity?.intent?.removeExtra("notification_type")
                return@LaunchedEffect
            } 
            
            // 7. Screenshot Detected Notification (`screenshot_detected`)
            if (notificationType == "screenshot_detected") {
                val imagePath = intent.getStringExtra("shared_image_path")
                if (!imagePath.isNullOrEmpty()) {
                    val file = java.io.File(imagePath)
                    if (file.exists()) {
                        tryOnViewModel.setGarmentImage(android.net.Uri.fromFile(file))
                        navController.navigate(com.jagtarapvtltd.tryzonai.TryOnUpload) {
                            launchSingleTop = true
                        }
                    }
                }
                intent.removeExtra("notification_type")
                intent.removeExtra("shared_image_path")
                activity?.intent?.removeExtra("notification_type")
                return@LaunchedEffect
            }

            // 8. General System Notification click fallback (`from_notification == true`)
            if (isFromNotification) {
                navController.navigate(com.jagtarapvtltd.tryzonai.TryOnUpload) {
                    launchSingleTop = true
                }
                intent.removeExtra("from_notification")
                activity?.intent?.removeExtra("from_notification")
            }
        } catch (e: Exception) {
            android.util.Log.e("TryZonAIApp", "Error processing deep link intent", e)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (showHeader) {
                val authUser by authViewModel.user.collectAsState()
                val guestTries by tryOnViewModel.guestTriesRemaining.collectAsState()

                val user = authUser
                val triesText = if (user != null) {
                    val isPro = user.is_premium || listOf("pro", "premium", "vip").contains(user.subscription_tier?.lowercase())
                    val dailyFreeRemaining = Math.max(0, 1 - user.try_ons_today)
                    if (isPro) {
                        "Pro ✨"
                    } else if (dailyFreeRemaining > 0) {
                        "$dailyFreeRemaining/1 Free"
                    } else if (user.paid_credits > 0) {
                        "${user.paid_credits} Credits"
                    } else if (user.credits > 0) {
                        "${user.credits} Bonus"
                    } else {
                        "0/1 Free"
                    }
                } else {
                    "$guestTries/1 Free"
                }

                TryZonHeader(
                    hasUnreadNotifications = tryOnViewModel.hasUnreadNotifications.value,
                    notificationsList = tryOnViewModel.notificationsList,
                    onNotificationClick = { 
                        tryOnViewModel.hasUnreadNotifications.value = false
                        if (tryOnViewModel.notificationsList.isEmpty()) {
                            tryOnViewModel.notificationsList.add(
                                com.jagtarapvtltd.tryzonai.viewmodel.AppNotification(
                                    title = "🎁 Welcome to TryZon AI!",
                                    message = "Your 1 free daily AI Try-On credit is active. Tap to try any outfit now!",
                                    timestamp = System.currentTimeMillis()
                                )
                            )
                        }
                    },
                    onNotificationItemClick = { notification ->
                        if (notification.sessionId != null) {
                            tryOnViewModel.loadSession(notification.sessionId)
                            navController.navigate(TryOnResult) { launchSingleTop = true }
                        } else {
                            navController.navigate(TryOnUpload) { launchSingleTop = true }
                        }
                    },
                    onProfileClick = { authViewModel.refresh(); isDrawerOpen = true },
                    canNavigateBack = canNavigateBack,
                    onBackClick = { 
                        val popped = navController.popBackStack()
                        if (!popped) {
                            navController.navigate(TryOnUpload) { launchSingleTop = true }
                        }
                    },
                    remainingTriesText = triesText,
                    onUpgradeClick = { navController.navigate(Premium) }
                )
            }
        },
        bottomBar = {
            if (showBottomBar) {
                TryZonBottomBar(
                    navController = navController,
                    currentDestination = currentDestination,
                    isDrawerOpen = isDrawerOpen,
                    onCloseDrawer = { isDrawerOpen = false },
                    onNavigate = {
                        tryOnViewModel.clearGarmentImage()
                        tryOnViewModel.resetProcessingState()
                    }
                )
            }
        }
    ) { paddingValues ->
        val networkMonitor = remember { com.jagtarapvtltd.tryzonai.utils.NetworkMonitor(context) }
        val isConnected by networkMonitor.isConnected.collectAsState()

        DisposableEffect(Unit) {
            onDispose { networkMonitor.stop() }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            NavigationHost(
                tryOnViewModel = tryOnViewModel,
                navController = navController,
                authViewModel = authViewModel,
                isDarkTheme = isDarkTheme,
                onThemeToggle = onThemeToggle,
                isOnboardingCompleted = isOnboardingCompleted,
                sessionManager = sessionManager,
                onTargetBoundsPositioned = { index, rect -> tourStepBoundsMap[index] = rect }
            )

            // ── Viewport Dark Scrim (Dims ONLY the content viewport area, leaving header & bottom nav undimmed!) ──
            androidx.compose.animation.AnimatedVisibility(
                visible = isDrawerOpen,
                enter = androidx.compose.animation.fadeIn(
                    animationSpec = androidx.compose.animation.core.tween(320, easing = androidx.compose.animation.core.LinearOutSlowInEasing)
                ),
                exit = androidx.compose.animation.fadeOut(
                    animationSpec = androidx.compose.animation.core.tween(260, easing = androidx.compose.animation.core.FastOutLinearInEasing)
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.50f))
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) {
                            isDrawerOpen = false
                        }
                )
            }

            // ── Sliding Viewport Drawer Sheet (Bounded strictly to content viewport between header and bottom nav!) ──
            androidx.compose.animation.AnimatedVisibility(
                visible = isDrawerOpen,
                modifier = Modifier.align(Alignment.CenterEnd),
                enter = androidx.compose.animation.slideInHorizontally(
                    initialOffsetX = { fullWidth -> fullWidth },
                    animationSpec = androidx.compose.animation.core.tween(340, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                ) + androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(220)),
                exit = androidx.compose.animation.slideOutHorizontally(
                    targetOffsetX = { fullWidth -> fullWidth },
                    animationSpec = androidx.compose.animation.core.tween(250, easing = androidx.compose.animation.core.FastOutLinearInEasing)
                ) + androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(200))
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(305.dp),
                    color = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shape = androidx.compose.ui.graphics.RectangleShape
                ) {
                    val user by authViewModel.user.collectAsState()
                    UserSidebarContent(
                        user = user,
                        isDarkTheme = isDarkTheme,
                        onThemeToggle = onThemeToggle,
                        notificationsEnabled = tryOnViewModel.notificationsEnabled.value,
                        onToggleNotifications = { tryOnViewModel.notificationsEnabled.value = !tryOnViewModel.notificationsEnabled.value },
                        onLogout = {
                            isDrawerOpen = false
                            scope.launch {
                                kotlinx.coroutines.delay(220)
                                authViewModel.logout()
                                navController.navigate(Login) {
                                    popUpTo(0) { inclusive = true }
                                }
                            }
                        },
                        onDeleteAccount = {
                            isDrawerOpen = false
                            scope.launch {
                                kotlinx.coroutines.delay(220)
                                authViewModel.deleteAccount()
                                navController.navigate(Login) {
                                    popUpTo(0) { inclusive = true }
                                }
                            }
                        },
                        onNavigate = { route ->
                            isDrawerOpen = false
                            scope.launch {
                                kotlinx.coroutines.delay(220)
                                navController.navigate(route)
                            }
                        },
                        onStartGuidedTour = {
                            isDrawerOpen = false
                            scope.launch {
                                kotlinx.coroutines.delay(220)
                                prefs.edit().putBoolean("has_completed_guided_tour", false).apply()
                                showGuidedTour = true
                                guidedTourStep = 0
                            }
                        },
                        onClose = { isDrawerOpen = false }
                    )
                }
            }

                        androidx.compose.animation.AnimatedVisibility(
                            visible = !isConnected,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .statusBarsPadding()
                                .padding(top = 8.dp),
                            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically(initialOffsetY = { -it }),
                            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically(targetOffsetY = { -it })
                        ) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = Color(0xFFD32F2F),
                                shadowElevation = 8.dp
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        Icons.Default.WifiOff,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "No Internet Connection",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // ── Contextual Guided Tour Overlay ──
                        if (showGuidedTour) {
                            com.jagtarapvtltd.tryzonai.ui.components.GuidedTourOverlay(
                                steps = tourSteps,
                                currentStep = guidedTourStep,
                                onNext = {
                                    if (guidedTourStep < tourSteps.size - 1) {
                                        guidedTourStep++
                                    } else {
                                        prefs.edit().putBoolean("has_completed_guided_tour", true).apply()
                                        showGuidedTour = false
                                    }
                                },
                                onBack = {
                                    if (guidedTourStep > 0) guidedTourStep--
                                },
                                onSkip = {
                                    prefs.edit().putBoolean("has_completed_guided_tour", true).apply()
                                    showGuidedTour = false
                                }
                            )
                        }
                    }
                }
            }

@Composable
fun UserSidebarContent(
    user: com.jagtarapvtltd.tryzonai.network.UserResponse?,
    isDarkTheme: Boolean,
    onThemeToggle: () -> Unit,
    notificationsEnabled: Boolean,
    onToggleNotifications: () -> Unit,
    onLogout: () -> Unit,
    onDeleteAccount: () -> Unit,
    onNavigate: (Any) -> Unit,
    onStartGuidedTour: (() -> Unit)? = null,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("try_on_prefs", android.content.Context.MODE_PRIVATE) }
    var dailyStyleEnabled by remember { mutableStateOf(prefs.getBoolean("daily_style_enabled", true)) }
    var userGender by remember { mutableStateOf(prefs.getString("user_gender", "Women") ?: "Women") }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // ── SINGLE SCROLLABLE COLUMN (No sticky inner header or footer) ──
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(14.dp))

        // ── 1. SIDEBAR TITLE & CLOSE BUTTON ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "✦ TRYZON AI",
                    style = MaterialTheme.typography.titleMedium,
                    color = PrimaryGold,
                    fontWeight = FontWeight.Black,
                    fontSize = 17.sp,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "Virtual Try-On Studio",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                shape = CircleShape,
                onClick = onClose,
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // ── 2. PROFILE CARD (ACCOUNT ACCESS) ──
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            border = BorderStroke(1.dp, PrimaryGold.copy(alpha = 0.2f)),
            shape = RoundedCornerShape(16.dp),
            onClick = { 
                onClose()
                if (user != null) onNavigate(Closet) else onNavigate(Login) 
            }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(38.dp),
                    shape = CircleShape,
                    color = PrimaryGold.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, PrimaryGold.copy(alpha = 0.4f))
                ) {
                    val photoUrl = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.photoUrl?.toString()
                    if (user != null && photoUrl != null) {
                        coil3.compose.AsyncImage(
                            model = photoUrl,
                            contentDescription = "Profile Picture",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = PrimaryGold,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = user?.name ?: user?.username ?: "Guest User",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    val isPro = user != null && (user.is_premium || user.subscription_tier?.lowercase()?.contains("pro") == true)
                    val planName = if (user != null) {
                        val subTier = user.subscription_tier?.takeIf { it != "free" }
                        if (subTier != null) "$subTier ✨" else if (user.is_premium) "Pro Member 👑" else "Free Plan"
                    } else "Tap to sign in & sync"
                    
                    Text(
                        text = planName,
                        color = PrimaryGold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (user != null) {
                        val todayCount = user.try_ons_today
                        val todayText = if (todayCount >= 1) "1/1 Free Used" else "$todayCount/1 Today"
                        val creditText = if (user.paid_credits > 0) "${user.paid_credits} Paid Credits" else "${user.credits} Free Bonus"
                        val usageLine = if (isPro) "Pro Unlimited • Ad-Free" else "$creditText • $todayText"
                        Text(
                            text = usageLine,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            fontSize = 10.sp
                        )
                    }
                }
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // ── SECTION 1: ACCOUNT & BILLING ──
        SidebarSectionCard(title = "ACCOUNT & BILLING") {
            val isPro = user != null && (user.is_premium || user.subscription_tier?.lowercase()?.contains("pro") == true)
            SidebarItem(
                icon = Icons.Default.WorkspacePremium,
                title = "Pricing Plans & Credits",
                subtitle = "Upgrade to Pro or buy credit packs",
                badgeText = if (!isPro) "PRO 👑" else null,
                onClick = {
                    onClose()
                    val bundle = android.os.Bundle().apply {
                        putString("plan_popup_source", "manual_upgrade")
                    }
                    com.jagtarapvtltd.tryzonai.utils.AnalyticsHelper.logEvent("plan_popup_impression", bundle)
                    onNavigate(Premium)
                }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 12.dp))

            SidebarItem(
                icon = Icons.Default.ManageAccounts,
                title = "Manage Subscriptions",
                subtitle = "Manage or cancel your active subscription",
                onClick = { 
                    onClose()
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://play.google.com/store/account/subscriptions?package=${context.packageName}"))
                    context.startActivity(intent)
                }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 12.dp))

            SidebarItem(
                icon = Icons.Default.History,
                title = "Purchase History",
                subtitle = "View past transactions & receipts",
                onClick = { 
                    onClose()
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://play.google.com/store/account/orderhistory"))
                    context.startActivity(intent)
                }
            )
        }

        // ── SECTION 2: AI CONTROL & PREFERENCES ──
        SidebarSectionCard(title = "AI CONTROL & PREFERENCES") {
            SidebarItem(
                icon = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                title = if (isDarkTheme) "Light Theme" else "Dark Theme",
                subtitle = "Switch visual appearance",
                badgeText = if (isDarkTheme) "Dark 🌙" else "Light ☀️",
                onClick = {
                    onClose()
                    onThemeToggle()
                }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 12.dp))

            SidebarItem(
                icon = if (notificationsEnabled) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                title = "Push Notifications",
                subtitle = if (notificationsEnabled) "Active for Try-On alerts" else "Disabled",
                badgeText = if (notificationsEnabled) "ON" else "OFF",
                onClick = {
                    onClose()
                    onToggleNotifications()
                }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 12.dp))

            SidebarItem(
                icon = Icons.Default.Checkroom,
                title = "Daily AI Style Engine",
                subtitle = if (dailyStyleEnabled) "Automated daily outfit suggestions" else "Disabled",
                badgeText = if (dailyStyleEnabled) "ACTIVE" else "OFF",
                onClick = {
                    onClose()
                    val newState = !dailyStyleEnabled
                    dailyStyleEnabled = newState
                    prefs.edit().putBoolean("daily_style_enabled", newState).apply()
                    android.widget.Toast.makeText(context, if (newState) "Daily AI Style Enabled ✨" else "Daily AI Style Disabled", android.widget.Toast.LENGTH_SHORT).show()
                }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 12.dp))

            SidebarItem(
                icon = Icons.Default.Face,
                title = "Fashion Preference",
                subtitle = when (userGender) {
                    "Women" -> "👩 Women's Outfits (Default)"
                    "Men" -> "👨 Men's Outfits"
                    else -> "👫 Unisex Outfits"
                },
                badgeText = userGender,
                onClick = {
                    onClose()
                    val nextGender = when (userGender) {
                        "Women" -> "Men"
                        "Men" -> "Unisex"
                        else -> "Women"
                    }
                    userGender = nextGender
                    prefs.edit().putString("user_gender", nextGender).apply()
                    val toastMsg = when (nextGender) {
                        "Women" -> "Preference: 👩 Women's Outfits"
                        "Men" -> "Preference: 👨 Men's Outfits"
                        else -> "Preference: 👫 Unisex Outfits"
                    }
                    android.widget.Toast.makeText(context, toastMsg, android.widget.Toast.LENGTH_SHORT).show()
                }
            )
        }

        // ── SECTION 3: EXPLORE & FEATURES ──
        SidebarSectionCard(title = "EXPLORE & FEATURES") {
            SidebarItem(
                icon = Icons.Default.School,
                title = "Guided App Tour 🎓",
                subtitle = "Interactive step-by-step feature walk",
                onClick = {
                    prefs.edit().putBoolean("has_completed_guided_tour", false).apply()
                    onClose()
                    if (onStartGuidedTour != null) {
                        onStartGuidedTour()
                    } else {
                        android.widget.Toast.makeText(context, "Tour will start on Home screen 🎓", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 12.dp))

            SidebarItem(
                icon = Icons.Default.AutoAwesome,
                title = "Test Daily AI Style Now ⚡",
                subtitle = "Trigger instant background style generation",
                onClick = {
                    onClose()
                    val testWork = androidx.work.OneTimeWorkRequestBuilder<com.jagtarapvtltd.tryzonai.workers.DailyStyleWorker>()
                        .setConstraints(
                            androidx.work.Constraints.Builder()
                                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                                .build()
                        )
                        .build()
                    androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
                        "TryZonDailyStyleInstantTest",
                        androidx.work.ExistingWorkPolicy.REPLACE,
                        testWork
                    )
                    android.widget.Toast.makeText(context, "⚡ Instant Daily AI Style Started in Background!", android.widget.Toast.LENGTH_LONG).show()
                }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 12.dp))

            SidebarItem(
                icon = Icons.Default.Star,
                title = "Rate 5★ on Play Store ⭐",
                subtitle = "Support TryZon AI with a quick review",
                badgeText = "5★",
                onClick = {
                    onClose()
                    com.jagtarapvtltd.tryzonai.ui.screens.launchGoogleInAppReview(context)
                }
            )
        }

        // ── SECTION 4: ACCOUNT ACTIONS ──
        SidebarSectionCard(title = "ACCOUNT") {
            SidebarItem(
                icon = Icons.AutoMirrored.Filled.Logout,
                title = "Logout Account",
                subtitle = "Sign out of current active session",
                onClick = {
                    onClose()
                    onLogout()
                }
            )

            if (user != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 12.dp))

                SidebarItem(
                    icon = Icons.Default.DeleteForever,
                    title = "Delete Account",
                    subtitle = "Permanently remove your account & data",
                    onClick = { showDeleteDialog = true }
                )
            } else {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 12.dp))

                SidebarItem(
                    icon = Icons.AutoMirrored.Filled.Login,
                    title = "Login / Switch Account",
                    subtitle = "Sign in to sync your wardrobe",
                    onClick = {
                        onClose()
                        onNavigate(Login)
                    }
                )
            }
        }

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("Delete Account?", fontWeight = FontWeight.Bold) },
                text = { Text("This action is permanent and will delete all your try-on history, wardrobe items, and personal data. This cannot be undone.") },
                confirmButton = {
                    TextButton(
                        onClick = { 
                            showDeleteDialog = false
                            onClose()
                            onDeleteAccount()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                    ) {
                        Text("DELETE PERMANENTLY", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("CANCEL")
                    }
                }
            )
        }

        // ── SECTION 5: SUPPORT, LEGAL & VERSION (Natural end of scroll) ──
        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { 
                        onClose()
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://tryzonai.com/#faq"))
                        context.startActivity(intent)
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("Help & Support", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                }
                Text(" • ", color = PrimaryGold.copy(alpha = 0.6f), fontSize = 11.5.sp)
                TextButton(
                    onClick = { 
                        onClose()
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://tryzonai.com/privacy-policy"))
                        context.startActivity(intent)
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("Privacy & Terms", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                }
            }
            
            val appVersionName = remember {
                try {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                } catch (e: Exception) {
                    com.jagtarapvtltd.tryzonai.BuildConfig.VERSION_NAME
                }
            }
            
            Text(
                text = "TryZon AI • v$appVersionName • Ref: 585c",
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun SidebarSectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            text = title,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(start = 6.dp, bottom = 6.dp)
        )
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            content()
        }
        Spacer(modifier = Modifier.height(4.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.06f))
    }
}

@Composable
fun StatCard(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = PrimaryGold, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.height(2.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.5.sp)
        }
    }
}

@Composable
fun SidebarItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    badgeText: String? = null,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        color = Color.Transparent,
        onClick = onClick,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(32.dp),
                shape = CircleShape,
                color = PrimaryGold.copy(alpha = 0.12f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = PrimaryGold,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
            if (badgeText != null) {
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    color = PrimaryGold.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryGold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            } else {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TryZonHeader(
    hasUnreadNotifications: Boolean = false,
    notificationsList: List<com.jagtarapvtltd.tryzonai.viewmodel.AppNotification> = emptyList(),
    onNotificationClick: () -> Unit,
    onNotificationItemClick: (com.jagtarapvtltd.tryzonai.viewmodel.AppNotification) -> Unit = {},
    onProfileClick: () -> Unit,
    canNavigateBack: Boolean = false,
    onBackClick: () -> Unit = {},
    remainingTriesText: String = "✦ 1 Free",
    onUpgradeClick: () -> Unit = {}
) {
    var showCreditInfoDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.88f),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (canNavigateBack) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(34.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), CircleShape)
                            .border(1.dp, PrimaryGold.copy(alpha = 0.2f), CircleShape)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = PrimaryGold,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(id = com.jagtarapvtltd.tryzonai.R.drawable.ic_tryzon_transparent_logo),
                        contentDescription = "TryZon AI Logo",
                        modifier = Modifier.size(30.dp)
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Text(
                    "TRYZON AI",
                    fontSize = 16.5.sp,
                    color = PrimaryGold,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.weight(1f)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Credit Badge Pill with Gold Glass Accent
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = PrimaryGold.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, PrimaryGold.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .height(34.dp)
                            .clickable { showCreditInfoDialog = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (remainingTriesText.startsWith("✦")) remainingTriesText else "✦ $remainingTriesText",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryGold
                            )
                        }
                    }

                    val infiniteTransition = rememberInfiniteTransition()
                    val rotation by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 0f,
                        animationSpec = infiniteRepeatable(
                            animation = keyframes {
                                durationMillis = 2500
                                0f at 0
                                0f at 1500
                                -15f at 1600
                                15f at 1700
                                -15f at 1800
                                15f at 1900
                                0f at 2000
                            },
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "bell_shake"
                    )

                    Box(contentAlignment = Alignment.Center) {
                        var expanded by remember { mutableStateOf(false) }
                        Surface(
                            onClick = { 
                                onNotificationClick()
                                expanded = true
                            },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, PrimaryGold.copy(alpha = 0.2f)),
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.NotificationsNone,
                                    contentDescription = "Notifications",
                                    tint = PrimaryGold,
                                    modifier = Modifier
                                        .size(17.dp)
                                        .rotate(if (hasUnreadNotifications) rotation else 0f)
                                )
                                if (hasUnreadNotifications) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .background(Color.Red, CircleShape)
                                            .align(Alignment.TopEnd)
                                            .offset(x = (-4).dp, y = 4.dp)
                                    )
                                }
                            }
                        }
                        if (expanded) {
                            androidx.compose.ui.window.Dialog(onDismissRequest = { expanded = false }) {
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    shadowElevation = 12.dp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 24.dp)
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "Notifications",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            IconButton(
                                                onClick = { expanded = false },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
                                        
                                        if (notificationsList.isEmpty()) {
                                            Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                                                Text("No new notifications", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        } else {
                                            androidx.compose.foundation.lazy.LazyColumn(
                                                modifier = Modifier.weight(1f, fill = false)
                                            ) {
                                                items(notificationsList.size) { index ->
                                                    val notification = notificationsList[index]
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable { 
                                                                expanded = false
                                                                onNotificationItemClick(notification)
                                                            }
                                                            .padding(horizontal = 24.dp, vertical = 16.dp),
                                                        verticalAlignment = Alignment.Top
                                                    ) {
                                                        Icon(
                                                            Icons.Default.NotificationsActive,
                                                            contentDescription = null,
                                                            tint = PrimaryGold,
                                                            modifier = Modifier.size(24.dp).padding(top = 4.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(16.dp))
                                                        Column {
                                                            Text(notification.title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                                            Spacer(modifier = Modifier.height(4.dp))
                                                            Text(notification.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                            Spacer(modifier = Modifier.height(6.dp))
                                                            val timeFormat = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.ENGLISH)
                                                            Text(timeFormat.format(java.util.Date(notification.timestamp)), style = MaterialTheme.typography.labelSmall, color = PrimaryGold)
                                                        }
                                                    }
                                                    if (index < notificationsList.size - 1) {
                                                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.05f))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    Surface(
                        onClick = onProfileClick,
                        modifier = Modifier.size(34.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, PrimaryGold.copy(alpha = 0.2f))
                    ) {
                        val photoUrl = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.photoUrl?.toString()
                        if (photoUrl != null) {
                            coil3.compose.AsyncImage(
                                model = photoUrl,
                                contentDescription = "Profile Picture",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        } else {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = "Profile",
                                    tint = PrimaryGold,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Subtle Gold Accent Rim Highlight at the bottom of the header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                PrimaryGold.copy(alpha = 0.4f),
                                PrimaryGold.copy(alpha = 0.8f),
                                PrimaryGold.copy(alpha = 0.4f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }
    }

    // Spatial Credit Info Dialog
    if (showCreditInfoDialog) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showCreditInfoDialog = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 16.dp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "✦ TryZon AI Credits",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Current Status: $remainingTriesText",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PrimaryGold,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Free daily credits reset automatically every night at midnight.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    ShimmeringButton(
                        text = "Get Unlimited Pro Tries →",
                        onClick = {
                            showCreditInfoDialog = false
                            onUpgradeClick()
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    )
                }
            }
        }
    }
}


@Composable
fun NavigationHost(
    tryOnViewModel: TryOnViewModel,
    navController: NavHostController,
    authViewModel: AuthViewModel,
    isDarkTheme: Boolean,
    onThemeToggle: () -> Unit,
    isOnboardingCompleted: Boolean,
    sessionManager: com.jagtarapvtltd.tryzonai.utils.SessionManager,
    onTargetBoundsPositioned: ((Int, androidx.compose.ui.geometry.Rect) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // Shared ViewModels
    val catalogViewModel: CatalogViewModel = viewModel()
    val wardrobeViewModel: WardrobeViewModel = viewModel()
    val closetViewModel: com.jagtarapvtltd.tryzonai.viewmodel.ClosetViewModel = viewModel()
    val inspirationViewModel: InspirationViewModel = viewModel()
    
    val context = androidx.compose.ui.platform.LocalContext.current
    val initialOnboardingDone = remember { sessionManager.isOnboardingCompletedSync() }
    val startDestinationRoute = remember { if (initialOnboardingDone) TryOnUpload else Onboarding }
    val scope = rememberCoroutineScope()
    val handleTryOn: (com.jagtarapvtltd.tryzonai.models.Product) -> Unit = { product ->
        if (!com.jagtarapvtltd.tryzonai.utils.NetworkMonitor.isInternetAvailable(context)) {
            android.widget.Toast.makeText(context, "Internet connection required for AI Try-On", android.widget.Toast.LENGTH_LONG).show()
        } else {
            tryOnViewModel.setSelectedProduct(product)
            val user = authViewModel.user.value
            val isLoggedIn = user != null
            val prefs = context.getSharedPreferences("tryzon_user_prefs", android.content.Context.MODE_PRIVATE)
            val isFirstTryOnDone = prefs.getBoolean("is_lifetime_first_tryon_done", false)

            if (!isLoggedIn && isFirstTryOnDone) {
                navController.navigate(TryOnUpload)
            } else if (tryOnViewModel.userPhoto.value != null) {
                val isUnlimited = user?.is_premium == true || (user?.subscription_tier != null && user?.subscription_tier != "free")
                val activity = context.findActivity()
                tryOnViewModel.submitTryOnWithAd(activity, isUnlimited)
                navController.navigate(TryOnProcessing)
            } else {
                navController.navigate(TryOnUpload)
            }
        }
    }
    
    NavHost(
        navController = navController,
        startDestination = startDestinationRoute,
        modifier = modifier,
        enterTransition = { 
            fadeIn(animationSpec = androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow)) + 
            slideInHorizontally(
                animationSpec = androidx.compose.animation.core.spring(dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy, stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow), 
                initialOffsetX = { (it * 0.14f).toInt() }
            ) 
        },
        exitTransition = { 
            fadeOut(animationSpec = androidx.compose.animation.core.tween(180)) 
        },
        popEnterTransition = { 
            fadeIn(animationSpec = androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow)) 
        },
        popExitTransition = { 
            fadeOut(animationSpec = androidx.compose.animation.core.tween(180)) + 
            slideOutHorizontally(
                animationSpec = androidx.compose.animation.core.spring(dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy, stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow), 
                targetOffsetX = { (it * 0.14f).toInt() }
            ) 
        }
    ) {
        composable<Onboarding> {
            OnboardingScreen(
                authViewModel = authViewModel,
                onFinish = {
                    scope.launch { sessionManager.setOnboardingCompleted() }
                    navController.navigate(TryOnUpload) {
                        popUpTo<Onboarding> { inclusive = true }
                    }
                }
            )
        }

        val handleProductClickAsTryOn: (String) -> Unit = { productId ->
            val product = catalogViewModel.products.value.find { it.id == productId }
            if (product != null) {
                handleTryOn(product)
            } else {
                // Fallback if not found in catalog cache
                navController.navigate(ProductDetail(productId))
            }
        }

        composable<Home> {
            HomeScreen(
                onNavigateToTryOn = { navController.navigate(TryOnUpload) { launchSingleTop = true } },
                onNavigateToCatalog = { navController.navigate(Catalog) { launchSingleTop = true } },
                onProductClick = { productId ->
                    handleProductClickAsTryOn(productId)
                },
                onTryOnClick = handleTryOn,
                catalogViewModel = catalogViewModel,
                tryOnViewModel = tryOnViewModel
            )
        }
        
        composable<TryOnUpload> {
            TryOnUploadScreen(
                onNavigateToProcessing = { navController.navigate(TryOnProcessing) { launchSingleTop = true } },
                onNavigateBack = { navController.popBackStack() },
                onNavigateToCatalog = { navController.navigate(Catalog) { launchSingleTop = true } },
                onNavigateToLogin = { navController.navigate(Login) { launchSingleTop = true } },
                onNavigateToPremium = { navController.navigate(Premium) { launchSingleTop = true } },
                viewModel = tryOnViewModel,
                catalogViewModel = catalogViewModel,
                authViewModel = authViewModel,
                onTargetBoundsPositioned = onTargetBoundsPositioned
            )
        }
        
        composable<TryOnCapture> {
            TryOnCaptureScreen(
                onNavigateToProcessing = { navController.navigate(TryOnProcessing) { launchSingleTop = true } },
                onNavigateBack = { navController.popBackStack() },
                viewModel = tryOnViewModel
            )
        }
        
        composable<TryOnProcessing> {
            TryOnProcessingScreen(
                onNavigateToResult = { 
                    navController.navigate(TryOnResult) {
                        popUpTo<TryOnProcessing> { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPremium = { 
                    navController.navigate(Premium) { launchSingleTop = true }
                },
                onNavigateToLogin = {
                    navController.navigate(Login) { launchSingleTop = true }
                },
                viewModel = tryOnViewModel,
                authViewModel = authViewModel
            )
        }
        
        composable<TryOnResult> {
            TryOnResultScreen(
                onNavigateBack = { 
                    val popped = navController.popBackStack()
                    if (!popped) {
                        navController.navigate(TryOnUpload) {
                            popUpTo(navController.graph.startDestinationId) { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                    tryOnViewModel.clearGarmentImage()
                    tryOnViewModel.resetProcessingState()
                },
                onTryAnotherOutfit = {
                    navController.navigate(TryOnUpload) {
                        popUpTo(navController.graph.startDestinationId) { inclusive = false }
                        launchSingleTop = true
                    }
                    tryOnViewModel.clearGarmentImage()
                    tryOnViewModel.resetProcessingState()
                },
                onSaveToWardrobe = { 
                    val result = it as com.jagtarapvtltd.tryzonai.models.TryOnResult
                    closetViewModel.saveToCloset(result.resultImage)
                },
                onBuyNow = { productId: String ->
                    handleProductClickAsTryOn(productId)
                },
                onNavigateToPremium = { navController.navigate(Premium) { launchSingleTop = true } },
                viewModel = tryOnViewModel,
                authViewModel = authViewModel,
                onTargetBoundsPositioned = onTargetBoundsPositioned
            )
        }
        
        composable<Catalog> {
            CatalogScreen(
                onProductClick = { productId ->
                    handleProductClickAsTryOn(productId)
                },
                onTryOnClick = handleTryOn,
                viewModel = catalogViewModel,
                onTargetBoundsPositioned = onTargetBoundsPositioned
            )
        }
        
        composable<ProductDetail> { backStackEntry ->
            val detail = backStackEntry.toRoute<ProductDetail>()
            ProductDetailScreen(
                productId = detail.productId,
                onNavigateBack = { navController.popBackStack() },
                onTryOn = handleTryOn,
                onBuyNow = { navController.navigate(CheckoutDestination) },
                viewModel = catalogViewModel
            )
        }
        
        composable<Inspiration> {
            InspirationFeedScreen(
                onLookClick = { lookId ->
                    navController.navigate(InspirationDetail(lookId))
                },
                onGetSimilar = { look ->
                    catalogViewModel.searchSimilar(look)
                    navController.navigate(Catalog)
                },
                viewModel = inspirationViewModel
            )
        }
        
        composable<InspirationDetail> { backStackEntry ->
            val detail = backStackEntry.toRoute<InspirationDetail>()
            InspirationDetailScreen(
                lookId = detail.lookId,
                onNavigateBack = { navController.popBackStack() },
                onProductClick = { productId ->
                    handleProductClickAsTryOn(productId)
                }
            )
        }

        composable<Wardrobe> {
            WardrobeScreen(
                authViewModel = authViewModel,
                onNavigateToLogin = { navController.navigate(Login) },
                onNavigateToPremium = { 
                    navController.navigate(Premium)
                },
                isDarkTheme = isDarkTheme,
                onThemeToggle = onThemeToggle,
                onItemClick = { item ->
                    tryOnViewModel.openHistoryResult(item)
                    navController.navigate(TryOnResult)
                },
                onTryOnAgain = { item ->
                    val user = authViewModel.user.value
                    val isUnlimited = user?.is_premium == true || (user?.subscription_tier != null && user?.subscription_tier != "free")
                    val activity = context.findActivity()
                    tryOnViewModel.retryTryOn(item, activity, isUnlimited)
                    navController.navigate(TryOnProcessing)
                },
                onSaveToCloset = { imageUrl ->
                    closetViewModel.saveToCloset(imageUrl)
                },
                viewModel = wardrobeViewModel
            )
        }
        
        composable<Closet> {
            com.jagtarapvtltd.tryzonai.ui.screens.ClosetScreen(
                viewModel = closetViewModel,
                authViewModel = authViewModel,
                onNavigateToLogin = { navController.navigate(Login) },
                onNavigateToPremium = { navController.navigate(Premium) },
                onNavigateToHistory = { navController.navigate(Wardrobe) },
                onTargetBoundsPositioned = onTargetBoundsPositioned
            )
        }
        
        composable<Premium> {
            PremiumScreen(
                authViewModel = authViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToLogin = { navController.navigate(Login) },
                onUpgradeSuccess = {
                    navController.popBackStack()
                }
            )
        }
        
        composable<CheckoutDestination> {
            CheckoutScreen(
                onNavigateBack = { navController.popBackStack() },
                onPaymentSuccess = {
                    navController.navigate(Home) {
                        popUpTo(navController.graph.startDestinationId) { inclusive = false }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable<Login> {
            LoginScreen(
                onNavigateToRegister = { navController.navigate(Register) },
                onLoginSuccess = { 
                    navController.navigate(Home) {
                        popUpTo(navController.graph.startDestinationId) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onNavigateBack = { navController.popBackStack() },
                viewModel = authViewModel
            )
        }

        composable<Register> {
            RegisterScreen(
                onNavigateToLogin = { navController.navigate(Login) },
                onRegisterSuccess = { 
                    navController.navigate(Home) {
                        popUpTo(navController.graph.startDestinationId) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onNavigateBack = { navController.popBackStack() },
                viewModel = authViewModel
            )
        }
    }
}

@Composable
fun TryZonBottomBar(
    navController: NavHostController,
    currentDestination: androidx.navigation.NavDestination?,
    isDrawerOpen: Boolean = false,
    onCloseDrawer: (() -> Unit)? = null,
    onNavigate: (() -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        shape = RoundedCornerShape(32.dp),
        border = BorderStroke(1.dp, PrimaryGold.copy(alpha = 0.2f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .navigationBarsPadding()
            .height(62.dp),
        shadowElevation = 12.dp,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val navigateTo = { route: Any ->
                val currentRouteQualified = currentDestination?.route ?: ""
                val targetRouteQualified = route::class.qualifiedName ?: ""
                val isAlreadyOnTarget = currentRouteQualified == targetRouteQualified || 
                        (targetRouteQualified.isNotEmpty() && currentRouteQualified.startsWith("$targetRouteQualified/"))
                
                if (isDrawerOpen) {
                    onCloseDrawer?.invoke()
                    if (!isAlreadyOnTarget) {
                        scope.launch {
                            kotlinx.coroutines.delay(220)
                            onNavigate?.invoke()
                            val popped = try { navController.popBackStack(route, inclusive = false) } catch (_: Exception) { false }
                            if (!popped) {
                                try {
                                    val rootId = navController.graph.findStartDestination().id
                                    navController.navigate(route) {
                                        popUpTo(rootId) {
                                            saveState = false
                                        }
                                        launchSingleTop = true
                                    }
                                } catch (_: Exception) {
                                    try {
                                        navController.navigate(route) {
                                            launchSingleTop = true
                                        }
                                    } catch (_: Exception) {}
                                }
                            }
                        }
                    } else {
                        onNavigate?.invoke()
                    }
                } else {
                    onNavigate?.invoke()
                    if (!isAlreadyOnTarget) {
                        val popped = try { navController.popBackStack(route, inclusive = false) } catch (_: Exception) { false }
                        if (!popped) {
                            try {
                                val rootId = navController.graph.findStartDestination().id
                                navController.navigate(route) {
                                    popUpTo(rootId) {
                                        saveState = false
                                    }
                                    launchSingleTop = true
                                }
                            } catch (_: Exception) {
                                try {
                                    navController.navigate(route) {
                                        launchSingleTop = true
                                    }
                                } catch (_: Exception) {}
                            }
                        }
                    }
                }
            }

            val currentRoute = currentDestination?.route

            // 1. Home
            val isHome = currentRoute == Home::class.qualifiedName
            BottomNavItem(
                icon = Icons.Default.Home,
                label = "Home",
                isSelected = isHome,
                onClick = { navigateTo(Home) }
            )

            // 2. Discover / Catalog
            val isDiscover = currentRoute == Catalog::class.qualifiedName
            BottomNavItem(
                icon = Icons.Default.Search,
                label = "Discover",
                isSelected = isDiscover,
                onClick = { navigateTo(Catalog) }
            )

            // 3. Center AI Try-On Action Button
            Box(
                modifier = Modifier
                    .bounceClick(0.90f)
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(GoldGradient))
                    .clickable { navigateTo(TryOnUpload) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PhotoCamera,
                    contentDescription = "Try-On",
                    tint = Color.Black,
                    modifier = Modifier.size(22.dp)
                )
            }

            // 4. History
            val isHistory = currentRoute == Wardrobe::class.qualifiedName
            BottomNavItem(
                icon = Icons.Default.History,
                label = "History",
                isSelected = isHistory,
                onClick = { navigateTo(Wardrobe) }
            )

            // 5. Closet
            val isCloset = currentRoute == Closet::class.qualifiedName
            BottomNavItem(
                icon = Icons.Default.Checkroom,
                label = "Closet",
                isSelected = isCloset,
                onClick = { navigateTo(Closet) }
            )
        }
    }
}

@Composable
fun BottomNavItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .bounceClick(0.92f)
            .size(44.dp)
            .clip(CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (isSelected) PrimaryGold else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.size(22.dp)
            )
            if (isSelected) {
                Spacer(modifier = Modifier.height(3.dp))
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .background(PrimaryGold, CircleShape)
                )
            }
        }
    }
}
