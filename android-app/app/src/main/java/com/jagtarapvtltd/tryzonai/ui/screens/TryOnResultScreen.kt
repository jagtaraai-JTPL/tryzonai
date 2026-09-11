package com.jagtarapvtltd.tryzonai.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import com.jagtarapvtltd.tryzonai.R
import com.jagtarapvtltd.tryzonai.ui.components.bounceClick
import com.jagtarapvtltd.tryzonai.ui.components.ShimmeringButton
import com.jagtarapvtltd.tryzonai.utils.findActivity
import com.jagtarapvtltd.tryzonai.ui.theme.*
import com.jagtarapvtltd.tryzonai.utils.CurrencyUtils
import com.jagtarapvtltd.tryzonai.viewmodel.TryOnViewModel
import com.jagtarapvtltd.tryzonai.models.*
import com.jagtarapvtltd.tryzonai.utils.UrlUtils
import com.jagtarapvtltd.tryzonai.utils.findActivity
import com.jagtarapvtltd.tryzonai.ui.components.bounceClick
import kotlin.math.roundToInt

fun getRandom5StarReviewText(): String {
    val sampleReviews = listOf(
        "Mind-blowing AI try-on app! The outfit fitting and photo realism are unbelievable. 5/5 stars! 🔥✨",
        "TryZon AI is super quick and accurate. Love how every outfit fits so naturally on me! 👕⭐",
        "Best virtual fitting room app ever! Saves so much time choosing clothes before buying. 👌💯",
        "Super realistic virtual try-on! Clothes texture and lighting look totally real. Highly recommend! 🌟",
        "Absolutely in love with this app! Instant outfit matching with zero hassle. Must have fashion app! 👗✨"
    )
    return sampleReviews.random()
}

fun copyReviewTextToClipboard(context: android.content.Context, reviewText: String) {
    try {
        val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("TryZon Review", reviewText)
        clipboardManager?.setPrimaryClip(clip)
        android.widget.Toast.makeText(context, "📋 Review draft copied! Opening Play Store...", android.widget.Toast.LENGTH_LONG).show()
    } catch (_: Exception) {}
}

fun openPlayStorePage(context: android.content.Context) {
    val packageName = context.packageName
    val playStoreUri = android.net.Uri.parse("market://details?id=$packageName")
    val webUri = android.net.Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
    try {
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, playStoreUri).apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        try {
            val webIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, webUri).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
        } catch (_: Exception) {}
    }
}

fun launchGoogleInAppReview(context: android.content.Context, fallbackToStore: Boolean = true) {
    val activity = context.findActivity() ?: run {
        if (fallbackToStore) openPlayStorePage(context)
        return
    }
    try {
        val reviewManager = if (com.jagtarapvtltd.tryzonai.BuildConfig.DEBUG) {
            com.google.android.play.core.review.testing.FakeReviewManager(context)
        } else {
            com.google.android.play.core.review.ReviewManagerFactory.create(context)
        }
        val request = reviewManager.requestReviewFlow()
        request.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val reviewInfo = task.result
                val flow = reviewManager.launchReviewFlow(activity, reviewInfo)
                flow.addOnCompleteListener {
                    // Google Play Review Flow completed cleanly
                }
            } else {
                if (fallbackToStore) openPlayStorePage(context)
            }
        }
    } catch (_: Exception) {
        if (fallbackToStore) openPlayStorePage(context)
    }
}

fun downloadWithAdCheck(context: android.content.Context, currentUser: com.jagtarapvtltd.tryzonai.network.UserResponse?, imageUrl: String) {
    if (imageUrl.isEmpty()) return
    val isAdFree = (currentUser?.is_premium == true) || ((currentUser?.paid_credits ?: 0) > 0)
    if (isAdFree) {
        com.jagtarapvtltd.tryzonai.utils.AnalyticsHelper.logEvent("image_saved_adfree")
        com.jagtarapvtltd.tryzonai.utils.ImageExportHelper.downloadImageToGallery(context, imageUrl)
    } else {
        com.jagtarapvtltd.tryzonai.utils.AnalyticsHelper.logEvent("image_saved_ad_triggered")
        val activity = context.findActivity()
        if (activity != null) {
            com.jagtarapvtltd.tryzonai.utils.AdManager.getInstance(context).showRewardedAd(
                activity = activity,
                onAdDismissed = {
                    com.jagtarapvtltd.tryzonai.utils.ImageExportHelper.downloadImageToGallery(context, imageUrl)
                },
                onRewardEarned = {
                    // Reward earned for watching ad
                },
                onAdFailedToLoad = {
                    com.jagtarapvtltd.tryzonai.utils.ImageExportHelper.downloadImageToGallery(context, imageUrl)
                }
            )
        } else {
            com.jagtarapvtltd.tryzonai.utils.ImageExportHelper.downloadImageToGallery(context, imageUrl)
        }
    }
}

fun getStoreUrlForProduct(product: com.jagtarapvtltd.tryzonai.models.Product?, fallbackName: String? = null, targetStore: String): String {
    val productName = product?.name.takeIf { !it.isNullOrEmpty() } 
        ?: fallbackName.takeIf { !it.isNullOrEmpty() } 
        ?: "Fashion Outfit"
    val encodedName = try { java.net.URLEncoder.encode(productName, "UTF-8") } catch (_: Exception) { "Fashion" }
    val rawUrl = product?.url ?: ""
    val lowerUrl = rawUrl.lowercase()
    
    return when (targetStore.lowercase()) {
        "myntra" -> {
            if (lowerUrl.contains("myntra.com")) {
                if (rawUrl.contains("subid=") || rawUrl.contains("tag=")) rawUrl else if (rawUrl.contains("?")) "$rawUrl&subid=tryzonai" else "$rawUrl?subid=tryzonai"
            } else {
                "https://www.myntra.com/search?rawQuery=$encodedName&subid=tryzonai"
            }
        }
        "ajio" -> {
            if (lowerUrl.contains("ajio.com")) {
                if (rawUrl.contains("subid=") || rawUrl.contains("tag=")) rawUrl else if (rawUrl.contains("?")) "$rawUrl&subid=tryzonai" else "$rawUrl?subid=tryzonai"
            } else {
                "https://www.ajio.com/search/?text=$encodedName&subid=tryzonai"
            }
        }
        "flipkart" -> {
            if (lowerUrl.contains("flipkart.com")) {
                if (rawUrl.contains("affid=") || rawUrl.contains("subid=")) rawUrl else if (rawUrl.contains("?")) "$rawUrl&affid=tryzonai" else "$rawUrl?affid=tryzonai"
            } else {
                "https://www.flipkart.com/search?q=$encodedName&affid=tryzonai"
            }
        }
        "amazon" -> {
            if (lowerUrl.contains("amazon.")) {
                if (rawUrl.contains("tag=") || rawUrl.contains("affid=")) rawUrl else if (rawUrl.contains("?")) "$rawUrl&tag=tryzonai-21" else "$rawUrl?tag=tryzonai-21"
            } else {
                "https://www.amazon.in/s?k=$encodedName&tag=tryzonai-21"
            }
        }
        else -> {
            if (rawUrl.isNotEmpty()) rawUrl else "https://www.google.com/search?q=$encodedName"
        }
    }
}

fun safeOpenUrl(context: android.content.Context, url: String) {
    if (url.isEmpty()) return
    try {
        val intent = android.content.Intent(
            android.content.Intent.ACTION_VIEW,
            android.net.Uri.parse(url)
        ).apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: android.content.ActivityNotFoundException) {
        android.widget.Toast.makeText(context, "No web browser found to open link.", android.widget.Toast.LENGTH_SHORT).show()
    } catch (_: Exception) {
        android.widget.Toast.makeText(context, "Could not open link.", android.widget.Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun ResultImageSection(
    originalPhoto: String, 
    resultImage: String,
    modifier: Modifier = Modifier.padding(horizontal = 20.dp),
    cardHeight: Dp? = 255.dp,
    alignment: Alignment = Alignment.TopCenter,
    contentScale: ContentScale = ContentScale.Crop,
    demoTriggerCount: Int = 0,
    onOpenFullscreen: (() -> Unit)? = null
) {
    var userOffsetX by remember { mutableStateOf(0.5f) }
    var showHeart by remember { mutableStateOf(false) }
    val animatableOffsetX = remember { Animatable(0.5f) }
    var isHandShowing by remember { mutableStateOf(false) }

    LaunchedEffect(demoTriggerCount) {
        if (demoTriggerCount > 0) {
            isHandShowing = true
            animatableOffsetX.snapTo(userOffsetX)
            animatableOffsetX.animateTo(
                targetValue = 0.15f,
                animationSpec = tween(650, easing = LinearOutSlowInEasing)
            )
            animatableOffsetX.animateTo(
                targetValue = 0.85f,
                animationSpec = tween(900, easing = FastOutSlowInEasing)
            )
            animatableOffsetX.animateTo(
                targetValue = 0.5f,
                animationSpec = tween(650, easing = FastOutSlowInEasing)
            )
            userOffsetX = 0.5f
            isHandShowing = false
        }
    }

    val activeOffsetX = if (animatableOffsetX.isRunning) animatableOffsetX.value else userOffsetX

    if (showHeart) {
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(800)
            showHeart = false
        }
    }
    
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (cardHeight != null) Modifier.height(cardHeight) else Modifier.fillMaxHeight())
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
                .pointerInput(resultImage) {
                    detectTapGestures(
                        onDoubleTap = { showHeart = true }
                    )
                }
                .pointerInput(originalPhoto, resultImage) {
                    if (originalPhoto.isNotEmpty()) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            if (size.width > 0) {
                                val delta = dragAmount.x / size.width
                                if (!delta.isNaN() && !delta.isInfinite()) {
                                    val current = if (userOffsetX.isNaN()) 0.5f else userOffsetX
                                    userOffsetX = (current + delta).coerceIn(0f, 1f)
                                }
                            }
                        }
                    }
                }
        ) {
            if (originalPhoto.isEmpty()) {
                AsyncImage(
                    model = UrlUtils.getCoilModel(resultImage),
                    contentDescription = "Try-On Result",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = contentScale,
                    alignment = alignment
                )
            } else {
                AsyncImage(
                    model = UrlUtils.getCoilModel(resultImage),
                    contentDescription = "Result Image",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = contentScale,
                    alignment = alignment
                )
                
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            clipRect(right = size.width * activeOffsetX) {
                                this@drawWithContent.drawContent()
                            }
                        }
                ) {
                    AsyncImage(
                        model = UrlUtils.getCoilModel(originalPhoto),
                        contentDescription = "Original Photo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = contentScale,
                        alignment = alignment
                    )
                }
                
                // Slider Handle
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val handlePosDp = maxWidth * activeOffsetX
                    
                    // Comparison Divider Line
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(3.dp)
                            .offset(x = handlePosDp - 1.5.dp)
                            .background(PrimaryGold)
                    )
                    
                    // Centered Compare Arrow Handle Badge
                    Surface(
                        shape = CircleShape,
                        color = PrimaryGold,
                        shadowElevation = 6.dp,
                        border = BorderStroke(1.5.dp, Color.White),
                        modifier = Modifier
                            .size(36.dp)
                            .offset(x = handlePosDp - 18.dp)
                            .align(Alignment.CenterStart)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.CompareArrows,
                                contentDescription = "Compare",
                                tint = Color.Black,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Animated Hand Gesture Demo Badge Overlay
                    if (isHandShowing || animatableOffsetX.isRunning) {
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = Color.Black.copy(alpha = 0.85f),
                            border = BorderStroke(1.dp, PrimaryGold),
                            shadowElevation = 8.dp,
                            modifier = Modifier
                                .offset(x = (handlePosDp - 50.dp).coerceAtLeast(10.dp), y = 60.dp)
                                .align(Alignment.TopStart)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("👆", fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Swipe to Compare", color = PrimaryGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                
                // Labels
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "ORIGINAL",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp),
                    color = PrimaryGold.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "RESULT",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = Color.Black,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
            
            // Double-Tap Heart Animation
            androidx.compose.animation.AnimatedVisibility(
                visible = showHeart,
                enter = scaleIn(tween(300)) + fadeIn(tween(300)),
                exit = scaleOut(tween(300)) + fadeOut(tween(300)),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Icon(
                    Icons.Default.Favorite,
                    contentDescription = null,
                    tint = Color.Red.copy(alpha = 0.85f),
                    modifier = Modifier.size(110.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            if (originalPhoto.isNotEmpty()) "👆 Swipe handle to compare • Double-tap to heart" else "❤️ Double-tap to heart your AI Try-On Result",
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun StylingComplements(complements: List<ComplementProduct>, onTryOn: (ComplementProduct) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(complements) { product ->
            ComplementCard(product = product, onTryOn = onTryOn)
        }
    }
}

@Composable
fun ComplementCard(product: ComplementProduct, onTryOn: (ComplementProduct) -> Unit) {
    Card(
        modifier = Modifier
            .width(115.dp)
            .clickable { onTryOn(product) },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(
                    model = UrlUtils.getFullUrl(product.imageUrl),
                    contentDescription = product.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp),
                    color = PrimaryGold.copy(alpha = 0.18f),
                    border = BorderStroke(0.5.dp, PrimaryGold),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "${product.matchScore}% AI MATCH",
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        color = PrimaryGold,
                        fontSize = 7.5.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
            
            Column(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = product.name,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

data class ConfettiParticle(
    val id: Int,
    val vx: Float,
    val vy: Float,
    val initialRot: Float,
    val rotSpeed: Float,
    val color: Color,
    val size: Float,
    val isCircle: Boolean
)

@Composable
fun CelebrationConfettiEffect(
    modifier: Modifier = Modifier,
    triggerKey: Any? = Unit
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val particles = remember(triggerKey) {
        val colors = listOf(
            PrimaryGold,
            Color(0xFFFF3F6C),
            Color(0xFF00E5FF),
            Color(0xFF10B981),
            Color(0xFFFF9800),
            Color(0xFFA855F7),
            Color(0xFFFC2779),
            Color.White
        )
        List(60) { index ->
            val angle = kotlin.random.Random.nextDouble(-Math.PI * 0.85, -Math.PI * 0.15)
            val speed = kotlin.random.Random.nextFloat() * 20f + 8f
            ConfettiParticle(
                id = index,
                vx = (kotlin.math.cos(angle) * speed).toFloat(),
                vy = (kotlin.math.sin(angle) * speed).toFloat(),
                initialRot = kotlin.random.Random.nextFloat() * 360f,
                rotSpeed = (kotlin.random.Random.nextFloat() - 0.5f) * 24f,
                color = colors[index % colors.size],
                size = kotlin.random.Random.nextFloat() * 10f + 6f,
                isCircle = index % 3 == 0
            )
        }
    }

    val progress = remember(triggerKey) { Animatable(0f) }

    LaunchedEffect(triggerKey) {
        try {
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        } catch (_: Exception) {}

        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 2300, easing = LinearEasing)
        )
    }

    val currentProgress = progress.value
    if (currentProgress < 1f) {
        val alpha = (1f - currentProgress).coerceIn(0f, 1f)
        Canvas(
            modifier = modifier
                .fillMaxSize()
                .graphicsLayer { this.alpha = alpha }
        ) {
            val centerX = size.width / 2f
            val centerY = size.height * 0.25f

            particles.forEach { p ->
                val t = currentProgress * 44f
                val gravity = 0.5f * t * t
                val posX = centerX + p.vx * t
                val posY = centerY + p.vy * t + gravity
                val rot = p.initialRot + p.rotSpeed * t

                drawContext.canvas.save()
                drawContext.canvas.translate(posX, posY)
                drawContext.canvas.rotate(rot)

                if (p.isCircle) {
                    drawCircle(color = p.color, radius = p.size / 2f)
                } else {
                    drawRect(
                        color = p.color,
                        topLeft = androidx.compose.ui.geometry.Offset(-p.size / 2f, -p.size / 4f),
                        size = androidx.compose.ui.geometry.Size(p.size, p.size * 0.65f)
                    )
                }
                drawContext.canvas.restore()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TryOnResultScreen(
    onNavigateBack: () -> Unit,
    onTryAnotherOutfit: () -> Unit = onNavigateBack,
    onSaveToWardrobe: (Any) -> Unit,
    onBuyNow: (String) -> Unit,
    onNavigateToPremium: () -> Unit = {},
    viewModel: TryOnViewModel,
    authViewModel: com.jagtarapvtltd.tryzonai.viewmodel.AuthViewModel? = null,
    onTargetBoundsPositioned: ((Int, androidx.compose.ui.geometry.Rect) -> Unit)? = null
) {
    val result by viewModel.tryOnResult.collectAsState()
    val product by viewModel.selectedProduct.collectAsState()
    val state by viewModel.processingState.collectAsState()
    val errorMsg by viewModel.errorMessage.collectAsState()
    val scrollState = rememberScrollState()
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    var showReportDialog by remember { mutableStateOf(false) }
    var showFullscreenViewer by remember { mutableStateOf(false) }

    val prefs = remember { context.getSharedPreferences("try_on_prefs", android.content.Context.MODE_PRIVATE) }
    var isResultDemoRunning by remember { mutableStateOf(!prefs.getBoolean("has_completed_result_hand_demo", false)) }
    var resultDemoStep by remember { mutableStateOf(0) }
    var demoTriggerCount by remember { mutableStateOf(0) }

    val currentUser by authViewModel?.user?.collectAsState() ?: remember { mutableStateOf(null) }
    val lastPromptDate = remember { prefs.getString("last_rating_prompt_date", "") ?: "" }
    val todayDate = remember { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date()) }
    
    var showFeedbackDialog by remember { mutableStateOf(false) }
    var selectedStars by remember { mutableStateOf(5) }

    androidx.activity.compose.BackHandler {
        if (showFullscreenViewer) {
            showFullscreenViewer = false
        } else {
            onNavigateBack()
        }
    }

    LaunchedEffect(result) {
        if (result != null && lastPromptDate != todayDate) {
            kotlinx.coroutines.delay(2000)
            prefs.edit().putString("last_rating_prompt_date", todayDate).apply()
            showFeedbackDialog = true
        }
    }

    if (showFullscreenViewer && result != null) {
        val fullRes = result!!
        val coroutineScope = rememberCoroutineScope()
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showFullscreenViewer = false },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { showFullscreenViewer = false },
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color.White.copy(alpha = 0.15f), CircleShape)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White
                            )
                        }

                        Text(
                            "HD VIRTUAL FITTING ROOM ✨",
                            color = PrimaryGold,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(
                                onClick = {
                                    downloadWithAdCheck(context, currentUser, fullRes.resultImage)
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color.White.copy(alpha = 0.15f), CircleShape)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = "Save", tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                            IconButton(
                                onClick = {
                                    coroutineScope.launch {
                                        com.jagtarapvtltd.tryzonai.utils.ImageExportHelper.shareImage(context, fullRes.resultImage)
                                    }
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color.White.copy(alpha = 0.15f), CircleShape)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        ResultImageSection(
                            originalPhoto = fullRes.originalPhoto,
                            resultImage = fullRes.resultImage,
                            modifier = Modifier.fillMaxSize(),
                            cardHeight = null,
                            demoTriggerCount = 0,
                            onOpenFullscreen = null
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(bottom = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            color = Color.White.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(100.dp)
                        ) {
                            Text(
                                "👈 Swipe handle to compare • Double tap to ❤️",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showFeedbackDialog) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showFeedbackDialog = false }
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, PrimaryGold.copy(alpha = 0.5f)),
                tonalElevation = 12.dp,
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .wrapContentHeight()
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .background(PrimaryGold.copy(alpha = 0.15f), CircleShape)
                            .border(1.5.dp, PrimaryGold, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("⭐", fontSize = 28.sp)
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "⭐ Enjoying TryZon AI?",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "How was your AI Try-On result? Tap stars below to rate us on Google Play!",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        (1..5).forEach { star ->
                            Icon(
                                imageVector = if (star <= selectedStars) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "Star $star",
                                tint = if (star <= selectedStars) PrimaryGold else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier
                                    .size(36.dp)
                                    .clickable {
                                        selectedStars = star
                                        showFeedbackDialog = false
                                        launchGoogleInAppReview(context)
                                    }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    ShimmeringButton(
                        text = "RATE 5★ ON GOOGLE PLAY ⭐",
                        onClick = {
                            showFeedbackDialog = false
                            launchGoogleInAppReview(context)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    TextButton(
                        onClick = { showFeedbackDialog = false }
                    ) {
                        Text(
                            "Maybe Later",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    if (showReportDialog) {
        com.jagtarapvtltd.tryzonai.ui.components.ReportDialog(
            contentType = "tryon_result",
            contentId = result?.resultImage,
            imageUrl = result?.resultImage,
            onDismiss = { showReportDialog = false },
            onReportSubmitted = { showReportDialog = false }
        )
    }

    val res = result

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (res == null) {
            LaunchedEffect(res, state) {
                if (state != com.jagtarapvtltd.tryzonai.viewmodel.ProcessingState.ANALYZING &&
                    state != com.jagtarapvtltd.tryzonai.viewmodel.ProcessingState.EXTRACTING &&
                    state != com.jagtarapvtltd.tryzonai.viewmodel.ProcessingState.FITTING &&
                    state != com.jagtarapvtltd.tryzonai.viewmodel.ProcessingState.ENHANCING &&
                    state != com.jagtarapvtltd.tryzonai.viewmodel.ProcessingState.FINALIZING &&
                    state != com.jagtarapvtltd.tryzonai.viewmodel.ProcessingState.ERROR) {
                    onNavigateBack()
                }
            }
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (state == com.jagtarapvtltd.tryzonai.viewmodel.ProcessingState.ERROR) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = "Error", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Failed to load result", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                        Text(errorMsg ?: "An unknown error occurred.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f), textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 8.dp))
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = onNavigateBack, colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold)) {
                            Text("Go Back", color = Color.Black)
                        }
                    }
                } else {
                    CircularProgressIndicator(color = PrimaryGold)
                }
            }
        } else {
            CelebrationConfettiEffect(triggerKey = res.resultImage)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                // 1. SECTION TITLE: Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "YOUR NEW LOOK ✨",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            "AI-generated virtual try-on",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = PrimaryGold.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, PrimaryGold.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .height(32.dp)
                                .clickable {
                                    isResultDemoRunning = true
                                    resultDemoStep = 0
                                    demoTriggerCount++
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("💡 Demo", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PrimaryGold)
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("👆", fontSize = 11.sp)
                            }
                        }

                        IconButton(
                            onClick = { showFullscreenViewer = true },
                            modifier = Modifier
                                .size(32.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        ) {
                            Icon(
                                Icons.Default.Fullscreen,
                                contentDescription = "Fullscreen",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 2. HERO RESULT IMAGE SECTION
                ResultImageSection(
                    originalPhoto = res.originalPhoto,
                    resultImage = res.resultImage,
                    demoTriggerCount = demoTriggerCount,
                    onOpenFullscreen = { showFullscreenViewer = true }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 3. ACTION BUTTONS ROW: (Share, Save, Lens, Closet)
                val coroutineScope = rememberCoroutineScope()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Card(
                        onClick = { 
                            com.jagtarapvtltd.tryzonai.utils.AnalyticsHelper.logEvent("image_shared")
                            coroutineScope.launch {
                                com.jagtarapvtltd.tryzonai.utils.ImageExportHelper.shareImage(context, res.resultImage)
                            }
                        },
                        modifier = Modifier.weight(1f).height(38.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = PrimaryGold, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Share", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        }
                    }

                    Card(
                        onClick = { 
                            com.jagtarapvtltd.tryzonai.utils.AnalyticsHelper.logEvent("image_saved")
                            downloadWithAdCheck(context, currentUser, res.resultImage)
                        },
                        modifier = Modifier.weight(1f).height(38.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, tint = PrimaryGold, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Save", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        }
                    }

                    Card(
                        onClick = { 
                            val imgUrl = UrlUtils.getFullUrl(res.resultImage)
                            if (imgUrl.startsWith("http")) {
                                val encodedUrl = try { java.net.URLEncoder.encode(imgUrl, "UTF-8") } catch (_: Exception) { imgUrl }
                                val lensUrl = "https://lens.google.com/uploadbyurl?url=$encodedUrl"
                                safeOpenUrl(context, lensUrl)
                            } else {
                                android.widget.Toast.makeText(context, "Search unavailable for local image", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f).height(38.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Lens 🔍", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        }
                    }

                    Card(
                        onClick = { 
                            onSaveToWardrobe(res) 
                            android.widget.Toast.makeText(context, "Saved to Closet ❤️", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f).height(38.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.FavoriteBorder, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Closet", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 4. E-COMMERCE AFFILIATE SHOPPING CARD WITH OFFICIAL STORE VECTOR ICONS
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, PrimaryGold.copy(alpha = 0.6f)),
                    shadowElevation = 3.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = PrimaryGold.copy(alpha = 0.18f)
                            ) {
                                Text(
                                    text = "✨ 98% AI FIT SCORE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    color = PrimaryGold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF10B981).copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "PERFECT MATCH",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF10B981),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        
                        Text(
                            text = "Get This Look Real-Life Ready 🛍️",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "AI Stylist: Select a store to check live stock & delivery",
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // STORE CHOICE CHIPS WITH OFFICIAL VECTOR DRAWABLE ICONS & AFFILIATE TAGS
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Myntra Button
                            Button(
                                onClick = {
                                    val targetUrl = getStoreUrlForProduct(product, product?.name, "myntra")
                                    safeOpenUrl(context, targetUrl)
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3F6C)),
                                modifier = Modifier.weight(1f).height(38.dp),
                                contentPadding = PaddingValues(horizontal = 2.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_myntra),
                                        contentDescription = "Myntra",
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text("Myntra", color = Color.White, fontWeight = FontWeight.Black, fontSize = 10.sp)
                                }
                            }

                            // Ajio Button
                            Button(
                                onClick = {
                                    val targetUrl = getStoreUrlForProduct(product, product?.name, "ajio")
                                    safeOpenUrl(context, targetUrl)
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C4152)),
                                modifier = Modifier.weight(1f).height(38.dp),
                                contentPadding = PaddingValues(horizontal = 2.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_ajio),
                                        contentDescription = "AJIO",
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text("AJIO", color = Color.White, fontWeight = FontWeight.Black, fontSize = 10.sp)
                                }
                            }

                            // Flipkart Button
                            Button(
                                onClick = {
                                    val targetUrl = getStoreUrlForProduct(product, product?.name, "flipkart")
                                    safeOpenUrl(context, targetUrl)
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2874F0)),
                                modifier = Modifier.weight(1f).height(38.dp),
                                contentPadding = PaddingValues(horizontal = 2.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_flipkart),
                                        contentDescription = "Flipkart",
                                        tint = Color.Unspecified,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text("Flipkart", color = Color.White, fontWeight = FontWeight.Black, fontSize = 10.sp)
                                }
                            }

                            // Amazon Button (tag=tryzonai-21)
                            Button(
                                onClick = {
                                    val targetUrl = getStoreUrlForProduct(product, product?.name, "amazon")
                                    safeOpenUrl(context, targetUrl)
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9900)),
                                modifier = Modifier.weight(1f).height(38.dp),
                                contentPadding = PaddingValues(horizontal = 2.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_amazon),
                                        contentDescription = "Amazon",
                                        tint = Color.Unspecified,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text("Amazon", color = Color.White, fontWeight = FontWeight.Black, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 5. PRIMARY CTA BUTTONS: TRY ANOTHER OUTFIT & REPORT
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = { onTryAnotherOutfit() },
                        modifier = Modifier
                            .bounceClick(scaleDown = 0.95f)
                            .weight(2.3f)
                            .height(44.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = PrimaryGold,
                        shadowElevation = 3.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                "TRY ANOTHER OUTFIT 👕✨",
                                color = Color.Black,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }
                    }

                    Surface(
                        onClick = { showReportDialog = true },
                        modifier = Modifier
                            .bounceClick(scaleDown = 0.95f)
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
                        shadowElevation = 1.5.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 6.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFDC2626),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                "Report 🚩",
                                color = Color(0xFFDC2626),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 5. QUICK BUY MONETIZATION BANNER
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = PrimaryGold.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, PrimaryGold.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .clickable { onNavigateToPremium() }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("⚡", fontSize = 18.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    "Want Instant Ad-Free Fits?",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                val isFirstTimeBuyer = (currentUser?.paid_credits ?: 0) == 0
                                Text(
                                    if (isFirstTimeBuyer) "🎁 25 Fast Passes (15 + 10 BONUS) for ${CurrencyUtils.formatPrice(39)}" else "15 Fast Passes for ${CurrencyUtils.formatPrice(39)} · Zero Ads",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryGold
                                )
                            }
                        }

                        Button(
                            onClick = { onNavigateToPremium() },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold),
                            shape = RoundedCornerShape(50),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("BUY ${CurrencyUtils.formatPrice(39)}", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 10.5.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // 6. COMPLETE THE LOOK SECTION
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "COMPLETE THE LOOK",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .width(24.dp)
                                    .height(2.dp)
                                    .background(PrimaryGold, CircleShape)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "AI-picked accessories for your outfit",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                val sampleComplements = if (res.complements.isNotEmpty()) res.complements else listOf(
                    ComplementProduct("1", "Sunglasses", "Accessories", 0, "https://images.unsplash.com/photo-1511499767150-a48a237f0083?w=300", "https://myntr.it/sQ4bpfb", 95),
                    ComplementProduct("2", "Watch", "Accessories", 0, "https://images.unsplash.com/photo-1524805444758-089113d48a6d?w=300", "https://myntr.it/sQ4bpfb", 96),
                    ComplementProduct("3", "Shoes", "Footwear", 0, "https://images.unsplash.com/photo-1549298916-b41d501d3772?w=300", "https://myntr.it/sQ4bpfb", 94),
                    ComplementProduct("4", "Bag", "Accessories", 0, "https://images.unsplash.com/photo-1553062407-98eeb64c6a62?w=300", "https://myntr.it/sQ4bpfb", 95)
                )

                StylingComplements(
                    complements = sampleComplements,
                    onTryOn = { comp ->
                        viewModel.setSelectedProduct(Product(
                            id = comp.id,
                            name = comp.name,
                            category = comp.category,
                            price = comp.price,
                            image = comp.imageUrl,
                            url = comp.url
                        ))
                        android.widget.Toast.makeText(context, "Selected ${comp.name} for AI Try-On ✨", android.widget.Toast.LENGTH_SHORT).show()
                        onTryAnotherOutfit()
                    }
                )
                
                Spacer(modifier = Modifier.height(22.dp))
                
                // 7. TOP SHOPPING SITES SECTION
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        "TOP SHOPPING SITES 🏬",
                        fontSize = 13.sp,
                        color = PrimaryGold,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.5.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "Browse Real Prices on Verified Partner Stores",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(modifier = Modifier.height(10.dp))
                
                val countryCode = LocalContext.current.resources.configuration.locales[0].country
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val sites = when(countryCode) {
                        "IN" -> listOf(
                            "Myntra 🛍️" to "https://myntr.it/sQ4bpfb",
                            "Ajio ✨" to "https://ajiio.in/86irPln",
                            "Flipkart 📦" to "https://fktr.in/JBqDhWg",
                            "Zara 👔" to "https://www.zara.com/in/"
                        )
                        "US" -> listOf(
                            "Amazon 🛒" to "https://www.amazon.com/fashion",
                            "Target 🎯" to "https://www.target.com/c/clothing/-/N-5xtc0",
                            "Walmart 🛍️" to "https://www.walmart.com/cp/clothing/5438",
                            "Zara 👔" to "https://www.zara.com/us/"
                        )
                        else -> listOf(
                            "Amazon 🛒" to "https://www.amazon.com/fashion",
                            "ASOS 🛍️" to "https://www.asos.com/",
                            "Zara 👔" to "https://www.zara.com/",
                            "H&M ✨" to "https://www.hm.com/"
                        )
                    }
                    
                    items(sites.size) { index ->
                        val site = sites[index]
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                            onClick = { uriHandler.openUri(site.second) }
                        ) {
                            Text(
                                site.first,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(140.dp))
            }
        }
    }
}
