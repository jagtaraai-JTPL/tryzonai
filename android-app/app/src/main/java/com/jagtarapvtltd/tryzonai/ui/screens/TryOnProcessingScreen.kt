// app/src/main/java/com.jagtarapvtltd.tryzonai/ui/screens/TryOnProcessingScreen.kt
package com.jagtarapvtltd.tryzonai.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.jagtarapvtltd.tryzonai.viewmodel.TryOnViewModel
import com.jagtarapvtltd.tryzonai.viewmodel.AuthViewModel
import com.jagtarapvtltd.tryzonai.viewmodel.ProcessingState
import com.jagtarapvtltd.tryzonai.ui.theme.PrimaryGold
import com.jagtarapvtltd.tryzonai.ui.components.ShimmeringButton
import com.jagtarapvtltd.tryzonai.utils.findActivity
import kotlinx.coroutines.delay

/**
 * TryZon AI Processing Screen
 * Premium fashion-tech visual experience inspired by luxury AI wardrobing
 */
@Composable
fun TryOnProcessingScreen(
    onNavigateToResult: () -> Unit,
    onNavigateBack: () -> Unit = {},
    onNavigateToPremium: () -> Unit = {},
    onNavigateToLogin: () -> Unit = {},
    viewModel: TryOnViewModel,
    authViewModel: AuthViewModel? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val adManager = remember { com.jagtarapvtltd.tryzonai.utils.AdManager.getInstance(context) }
    val processingState by viewModel.processingState.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val limitReached by viewModel.limitReachedEvent.collectAsState()
    val authUser by (authViewModel?.user ?: kotlinx.coroutines.flow.MutableStateFlow(null)).collectAsState()

    val userPhoto by viewModel.userPhoto.collectAsState()
    val garmentImage by viewModel.garmentImage.collectAsState()
    val selectedProduct by viewModel.selectedProduct.collectAsState()

    var hasNavigatedToResult by remember { mutableStateOf(false) }
    var isBlasting by remember { mutableStateOf(false) }

    if (limitReached) {
        val isGuestUser = authUser == null

        if (isGuestUser) {
            AlertDialog(
                onDismissRequest = {
                    viewModel.resetLimitEvent()
                    onNavigateBack()
                },
                containerColor = MaterialTheme.colorScheme.surface,
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = PrimaryGold.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, PrimaryGold.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "🎁 GIFT UNLOCKED",
                                color = PrimaryGold,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "✨ Get 2 Free Welcome Credits",
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Create your account to claim 2 free welcome credits and keep exploring new outfits!",
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = PrimaryGold.copy(alpha = 0.08f),
                            border = BorderStroke(1.dp, PrimaryGold.copy(alpha = 0.25f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = PrimaryGold, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("1 Free Daily Try-On Pass", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = PrimaryGold, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Cloud Wardrobe Backup", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = PrimaryGold, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Priority GPU Fast Queue", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "⚠️ Unsaved try-ons & body pose data will be cleared",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.9f),
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }
                },
                confirmButton = {
                    ShimmeringButton(
                        text = "CLAIM 2 CREDITS WITH GOOGLE 🚀",
                        onClick = {
                            viewModel.resetLimitEvent()
                            onNavigateToLogin()
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    )
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            viewModel.resetLimitEvent()
                            onNavigateBack()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("MAYBE LATER", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                }
            )
        } else {
            AlertDialog(
                onDismissRequest = {
                    viewModel.resetLimitEvent()
                    onNavigateBack()
                },
                title = { Text("🎬 Watch a short ad to continue", fontWeight = FontWeight.Black) },
                text = { Text("Create your AI Try-On right after the ad, or upgrade for an ad-free experience.") },
                icon = { Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = PrimaryGold, modifier = Modifier.size(48.dp)) },
                confirmButton = {
                    ShimmeringButton(
                        text = "WATCH AD & CONTINUE 🎬",
                        onClick = {
                            viewModel.resetLimitEvent()
                            val activity = context.findActivity()
                            if (activity != null) {
                                adManager.showRewardedAd(
                                    activity = activity,
                                    onAdDismissed = { },
                                    onRewardEarned = {
                                        viewModel.claimRewardedAdCredit {
                                            authViewModel?.refresh()
                                        }
                                    }
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    )
                },
                dismissButton = {
                    TextButton(onClick = {
                        viewModel.resetLimitEvent()
                        onNavigateBack()
                    }) {
                        Text("CLOSE", color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            )
        }
    }

    if (processingState == ProcessingState.WAITING_FOR_AD) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelAd() },
            title = { Text("Watch 1 Short Ad to Continue", fontWeight = FontWeight.Black) },
            text = { Text("Watching a short video ad supports our AI GPU servers and keeps TryZon AI free for everyone! Click WATCH AD below to play the ad, and your Try-On will begin processing immediately after.", fontSize = 14.sp, lineHeight = 21.sp) },
            icon = { Icon(Icons.Default.PlayCircleOutline, contentDescription = null, tint = PrimaryGold, modifier = Modifier.size(48.dp)) },
            confirmButton = {
                ShimmeringButton(
                    text = "WATCH AD 📺",
                    onClick = { viewModel.proceedWithAd(context.findActivity()) },
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                )
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelAd() }) {
                    Text("CANCEL", color = MaterialTheme.colorScheme.onSurface)
                }
            }
        )
    }

    // Dynamic background aura transition
    val infiniteTransition = rememberInfiniteTransition(label = "bg_aura")
    val auraScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(3500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "aura_scale"
    )

    androidx.activity.compose.BackHandler {
        viewModel.resetProcessingState()
        onNavigateBack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Soft golden radial aura behind central visualizer
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        if (processingState == ProcessingState.ERROR) Color(0x22EF4444)
                        else PrimaryGold.copy(alpha = 0.16f),
                        PrimaryGold.copy(alpha = 0.04f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.5f, size.height * 0.26f),
                    radius = size.width * 0.65f * auraScale
                )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            // Top Cancel & Navigation Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        viewModel.resetProcessingState()
                        onNavigateBack()
                    }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Text(
                    text = "AI Virtual Fitting",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                TextButton(
                    onClick = {
                        viewModel.resetProcessingState()
                        onNavigateBack()
                    }
                ) {
                    Text("Cancel", color = PrimaryGold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            if (processingState == ProcessingState.ERROR) {
                // Error State Card
                Spacer(modifier = Modifier.height(24.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.3f)),
                    shadowElevation = 6.dp
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = Color.Red,
                            modifier = Modifier.size(64.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Processing Update",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        val isAdRequiredError = errorMessage?.contains("ad", ignoreCase = true) == true ||
                                errorMessage?.contains("video", ignoreCase = true) == true ||
                                errorMessage?.contains("watch", ignoreCase = true) == true

                        val isLimitOrCreditError = errorMessage?.contains("limit", ignoreCase = true) == true ||
                                errorMessage?.contains("credit", ignoreCase = true) == true ||
                                errorMessage?.contains("premium", ignoreCase = true) == true ||
                                errorMessage?.contains("time slot", ignoreCase = true) == true ||
                                errorMessage?.contains("slot", ignoreCase = true) == true

                        Text(
                            text = errorMessage ?: "An unexpected issue occurred during virtual fitting.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 10.dp, bottom = 24.dp),
                            lineHeight = 20.sp
                        )

                        if (isAdRequiredError) {
                            val act = context.findActivity()
                            Button(
                                onClick = {
                                    if (act != null) {
                                        viewModel.proceedWithAd(act)
                                    } else {
                                        viewModel.submitTryOn()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold),
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("WATCH VIDEO AD & GENERATE 📺", color = Color.Black, fontWeight = FontWeight.Black)
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedButton(
                                onClick = onNavigateToPremium,
                                border = BorderStroke(1.5.dp, PrimaryGold.copy(alpha = 0.6f)),
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("GO AD-FREE PRO 👑", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                            }
                        } else if (isLimitOrCreditError) {
                            Button(
                                onClick = onNavigateToPremium,
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold),
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("UPGRADE TO PRO / GET CREDITS 👑", color = Color.Black, fontWeight = FontWeight.Black)
                            }
                        } else {
                            val act = context.findActivity()
                            Button(
                                onClick = {
                                    if (act != null) {
                                        viewModel.submitTryOnWithAd(act, viewModel.lastTryOnWasPremium)
                                    } else {
                                        viewModel.submitTryOn()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold),
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("RETRY PROCESSING", color = Color.Black, fontWeight = FontWeight.Black)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        TextButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("GO BACK", color = PrimaryGold, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                // 20-Second Ticker & Supernova Blast Navigation
                var percentage by remember { mutableIntStateOf(0) }

                LaunchedEffect(processingState) {
                    if (processingState != ProcessingState.ERROR) {
                        while (percentage < 99) {
                            delay(140) // Balanced timeline (~14s total: na jyada fast, na jyada slow)
                            percentage += 1
                        }
                    }
                }

                LaunchedEffect(processingState) {
                    if (processingState == ProcessingState.COMPLETED && !hasNavigatedToResult) {
                        while (percentage < 95) {
                            delay(50)
                        }
                        percentage = 100
                        isBlasting = true
                        delay(650) // Golden Supernova Explosion Blast duration
                        if (!hasNavigatedToResult) {
                            hasNavigatedToResult = true
                            authViewModel?.refresh()
                            onNavigateToResult()
                        }
                    }
                }

                val currentCalculatedStep = when {
                    processingState == ProcessingState.COMPLETED -> 5
                    processingState.step > 0 -> processingState.step
                    else -> (percentage / 20 + 1).coerceIn(1, 5)
                }

                // 1. TOP HEADER
                Text(
                    text = buildAnnotatedString {
                        append("Processing your ")
                        withStyle(style = SpanStyle(color = PrimaryGold)) {
                            append("Try-On...")
                        }
                    },
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    letterSpacing = (-0.5).sp
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "Almost ready! Our AI is creating your perfect look.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Time confidence pill
                Surface(
                    color = PrimaryGold.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, PrimaryGold.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = null,
                            tint = PrimaryGold,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Usually takes 15–20 seconds • Please wait",
                            fontSize = 10.5.sp,
                            color = PrimaryGold,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.1.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 2. MAIN AI VISUALIZATION & CARDS
                val outfitModel: Any? = garmentImage
                    ?: selectedProduct?.image
                    ?: selectedProduct?.url

                MainVisualizerSection(
                    percentage = percentage,
                    userPhoto = userPhoto,
                    outfitModel = outfitModel,
                    isBlasting = isBlasting
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 3. AI FASHION PROCESSING CAPABILITY CARD
                ProcessingBenefitsRow()

                Spacer(modifier = Modifier.height(8.dp))

                // 4. PROCESSING STEPS TIMELINE
                ProcessingTimelineCard(currentStep = currentCalculatedStep)

                Spacer(modifier = Modifier.height(6.dp))

                // 5. BOTTOM REASSURANCE CARD
                ReassuranceCard()
            }
        }
    }
}

/**
 * Helper to resolve Coil Image model from Uri, String (with res: or http), or Int
 */
private fun resolveCoilModel(input: Any?, fallbackRes: Int): Any {
    if (input == null) return fallbackRes
    if (input is Int) return input
    val str = input.toString()
    if (str.isEmpty() || str == "null") return fallbackRes
    if (str.startsWith("res:")) {
        val resId = str.removePrefix("res:").toIntOrNull()
        if (resId != null) return resId
        return fallbackRes
    }
    if (str.startsWith("content://") || str.startsWith("file://")) {
        return try {
            android.net.Uri.parse(str)
        } catch (e: Exception) {
            fallbackRes
        }
    }
    val coil = com.jagtarapvtltd.tryzonai.utils.UrlUtils.getCoilModel(str)
    if (coil != null) return coil
    return fallbackRes
}

/**
 * Main Central AI Visualization:
 * Card Inward Attraction + Collision Merger over Center Ring + Gold Explosion Blast
 */
@Composable
fun MainVisualizerSection(
    percentage: Int,
    userPhoto: Any?,
    outfitModel: Any?,
    isBlasting: Boolean = false
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val progress = (percentage / 100f).coerceIn(0f, 1f)

    // Calculate Inward Card Attraction Shift
    // 0% -> 0dp, 75% -> 44dp, 95% -> 72dp (Full Collision Overlap!)
    val shiftDp = when {
        progress <= 0.75f -> (progress / 0.75f) * 44f
        else -> 44f + ((progress - 0.75f) / 0.20f) * 28f
    }.dp

    val leftShiftPx = with(density) { shiftDp.toPx() }
    val rightShiftPx = with(density) { -shiftDp.toPx() }

    // Card Rotation (straightens as cards approach center)
    val leftRotation = (-4f * (1f - (progress / 0.75f).coerceIn(0f, 1f)))
    val rightRotation = (4f * (1f - (progress / 0.75f).coerceIn(0f, 1f)))

    // Fusion vibration & merger state (75%+ progress)
    val isMerging = progress >= 0.75f

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. TOP ROW: Photo Cards Inward Attraction & Merger Collision
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Card: YOUR PHOTO
                PhotoPreviewCard(
                    title = "YOUR PHOTO",
                    model = userPhoto,
                    fallbackRes = com.jagtarapvtltd.tryzonai.R.drawable.sample_model_female,
                    rotation = leftRotation,
                    translationX = leftShiftPx,
                    isScanning = true,
                    isDraping = false,
                    isMerging = isMerging
                )

                // Flow connection chevrons (fades during merger)
                if (!isMerging) {
                    FlowConnectionIndicator()
                }

                // Right Card: AI TRY-ON
                PhotoPreviewCard(
                    title = "AI TRY-ON",
                    model = outfitModel,
                    fallbackRes = com.jagtarapvtltd.tryzonai.R.drawable.monaco_var,
                    rotation = rightRotation,
                    translationX = rightShiftPx,
                    isScanning = false,
                    isDraping = true,
                    isMerging = isMerging
                )
            }

            // 💥 SUPERNOVA GOLD BLAST EXPLOSION OVERLAY ON COLLISION
            SupernovaBlastOverlay(isBlasting = isBlasting)
        }

        // 2. BOTTOM: Central AI Progress Ring showing 0-100% Ticker
        AICentralRing(percentage = percentage)
    }
}

@Composable
fun FlowConnectionIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "flow_arrow")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "arrow_alpha"
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy((-3).dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = PrimaryGold.copy(alpha = alpha * 0.5f),
            modifier = Modifier.size(14.dp)
        )
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = PrimaryGold.copy(alpha = alpha),
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
fun SupernovaBlastOverlay(
    isBlasting: Boolean
) {
    if (!isBlasting) return

    val blastProgress = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(isBlasting) {
        if (isBlasting) {
            blastProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(600, easing = FastOutSlowInEasing)
            )
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val p = blastProgress.value
        val maxRadius = (size.width.coerceAtLeast(size.height)) * 0.9f
        val currentRadius = p * maxRadius
        val alpha = (1f - p).coerceIn(0f, 1f)

        // 1. Central Gold Flash Aura
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White,
                    PrimaryGold,
                    PrimaryGold.copy(alpha = 0.5f),
                    Color.Transparent
                ),
                center = center,
                radius = (currentRadius * 1.2f).coerceAtLeast(1f)
            ),
            center = center,
            radius = (currentRadius * 1.2f).coerceAtLeast(1f),
            alpha = alpha
        )

        // 2. Shockwave Radial Ring
        drawCircle(
            color = PrimaryGold,
            center = center,
            radius = currentRadius,
            style = Stroke(width = 8.dp.toPx() * (1f - p)),
            alpha = alpha
        )

        // 3. Explosive Sparkle Particles
        val numParticles = 24
        for (i in 0 until numParticles) {
            val angle = (i * 360f / numParticles) * (Math.PI / 180f)
            val dist = currentRadius * (0.75f + (i % 3) * 0.15f)
            val px = center.x + dist * Math.cos(angle).toFloat()
            val py = center.y + dist * Math.sin(angle).toFloat()
            val particleRadius = (6.dp.toPx() * (1f - p)).coerceAtLeast(1f)

            drawCircle(
                color = if (i % 2 == 0) Color.White else PrimaryGold,
                center = Offset(px, py),
                radius = particleRadius,
                alpha = alpha
            )
        }
    }
}

@Composable
fun PhotoPreviewCard(
    title: String,
    model: Any?,
    fallbackRes: Int,
    rotation: Float,
    translationX: Float = 0f,
    isScanning: Boolean = false,
    isDraping: Boolean = false,
    isMerging: Boolean = false
) {
    val resolvedModel = remember(model) {
        resolveCoilModel(model, fallbackRes)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "card_anim_$title")

    val floatOffset by infiniteTransition.animateFloat(
        initialValue = if (isScanning) -2f else 2f,
        targetValue = if (isScanning) 2f else -2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float_offset"
    )

    val mergerShake by infiniteTransition.animateFloat(
        initialValue = if (isMerging) -2.5f else 0f,
        targetValue = if (isMerging) 2.5f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(90, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "merger_shake"
    )

    val borderAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "border_glow"
    )

    val scanYProgress by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scan_line"
    )

    val shimmerProgress by infiniteTransition.animateFloat(
        initialValue = -0.4f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_progress"
    )

    Surface(
        modifier = Modifier
            .width(88.dp)
            .height(124.dp)
            .graphicsLayer {
                rotationZ = rotation
                this.translationX = translationX + mergerShake.dp.toPx()
                this.translationY = floatOffset.dp.toPx()
            }
            .shadow(10.dp, RoundedCornerShape(18.dp), spotColor = PrimaryGold.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.5.dp, PrimaryGold.copy(alpha = borderAlpha))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = resolvedModel,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            if (isScanning) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val y = size.height * scanYProgress
                    drawLine(
                        brush = Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                PrimaryGold.copy(alpha = 0.95f),
                                Color.White,
                                PrimaryGold.copy(alpha = 0.95f),
                                Color.Transparent
                            )
                        ),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 2.5.dp.toPx()
                    )
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                PrimaryGold.copy(alpha = 0.35f),
                                Color.Transparent
                            ),
                            startY = y,
                            endY = (y - 20.dp.toPx()).coerceAtLeast(0f)
                        ),
                        topLeft = Offset(0f, (y - 20.dp.toPx()).coerceAtLeast(0f)),
                        size = androidx.compose.ui.geometry.Size(size.width, 20.dp.toPx())
                    )
                }
            }

            if (isDraping) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val startX = shimmerProgress * w
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                PrimaryGold.copy(alpha = 0.35f),
                                Color.White.copy(alpha = 0.65f),
                                PrimaryGold.copy(alpha = 0.35f),
                                Color.Transparent
                            ),
                            start = Offset(startX - w * 0.5f, 0f),
                            end = Offset(startX + w * 0.5f, h)
                        )
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f))
                        )
                    )
            )

            Surface(
                shape = RoundedCornerShape(50),
                color = Color.Black.copy(alpha = 0.75f),
                border = BorderStroke(0.8.dp, PrimaryGold.copy(alpha = 0.7f)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = PrimaryGold,
                        modifier = Modifier.size(8.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = title,
                        fontSize = 7.5.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        letterSpacing = 0.3.sp
                    )
                }
            }
        }
    }
}

@Composable
fun AICentralRing(percentage: Int) {
    val infiniteTransition = rememberInfiniteTransition(label = "ai_ring")

    val outerRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(6500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "outer_rot"
    )

    val innerRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(4500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "inner_rot"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Box(
        modifier = Modifier.size(116.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer rotating ring
        Box(
            modifier = Modifier
                .size(114.dp)
                .rotate(outerRotation)
                .border(
                    width = 2.5.dp,
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            PrimaryGold,
                            Color.Transparent,
                            PrimaryGold.copy(alpha = 0.6f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        // Inner counter-rotating ring
        Box(
            modifier = Modifier
                .size(98.dp)
                .rotate(innerRotation)
                .border(
                    width = 1.5.dp,
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xFFFDE047),
                            Color.Transparent,
                            PrimaryGold
                        )
                    ),
                    shape = CircleShape
                )
        )

        // Glowing Core with Live Percentage Counter
        Surface(
            modifier = Modifier.size(80.dp * pulseScale),
            shape = CircleShape,
            color = PrimaryGold.copy(alpha = 0.12f),
            border = BorderStroke(1.5.dp, PrimaryGold.copy(alpha = 0.75f))
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "$percentage%",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        color = PrimaryGold,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        text = "AI RENDERING",
                        fontSize = 7.5.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onBackground,
                        letterSpacing = 1.2.sp
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = PrimaryGold,
                        modifier = Modifier.size(10.dp)
                    )
                }
            }
        }
    }
}

/**
 * Single Compact AI Capability Card (Replaces cramped 4 columns)
 */
@Composable
fun ProcessingBenefitsRow() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, PrimaryGold.copy(alpha = 0.22f)),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = PrimaryGold,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "AI Fashion Processing",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Realistic fit • Natural lighting • Personalized try-on",
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                )
            }
        }
    }
}

/**
 * Vertical Timeline Processing Steps
 */
@Composable
fun ProcessingTimelineCard(currentStep: Int) {
    val steps = listOf(
        TimelineStepData(1, "Analyzing photo fidelity", "Checking image quality and pose..."),
        TimelineStepData(2, "Extracting garment mesh", "Understanding fabric and texture..."),
        TimelineStepData(3, "AI virtual draping", "Fitting outfit to your body shape..."),
        TimelineStepData(4, "Lighting adjustment", "Adding realistic shadows and lights..."),
        TimelineStepData(5, "Final render generation", "Putting the final touches...")
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, PrimaryGold.copy(alpha = 0.22f)),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            steps.forEach { step ->
                TimelineStepRow(
                    step = step,
                    isActive = step.id == currentStep,
                    isCompleted = step.id < currentStep
                )
            }
        }
    }
}

@Composable
fun TimelineStepRow(
    step: TimelineStepData,
    isActive: Boolean,
    isCompleted: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Circular Icon Indicator
        Box(
            modifier = Modifier.size(34.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.size(34.dp),
                shape = CircleShape,
                color = when {
                    isCompleted -> PrimaryGold
                    isActive -> PrimaryGold.copy(alpha = 0.15f)
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                },
                border = when {
                    isCompleted -> null
                    isActive -> BorderStroke(2.dp, PrimaryGold)
                    else -> BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                }
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (isCompleted) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp)
                        )
                    } else if (isActive) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = PrimaryGold,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "${step.id}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Step Title & Context Subtitle
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = step.title,
                fontSize = 13.5.sp,
                fontWeight = if (isActive || isCompleted) FontWeight.Bold else FontWeight.Medium,
                color = when {
                    isCompleted -> MaterialTheme.colorScheme.onSurface
                    isActive -> PrimaryGold
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                }
            )
            Text(
                text = step.subtitle,
                fontSize = 10.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Status Badge
        if (isCompleted) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Complete ",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryGold
                )
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = PrimaryGold,
                    modifier = Modifier.size(13.dp)
                )
            }
        } else if (isActive) {
            Text(
                text = "Processing...",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryGold
            )
        }
    }
}

/**
 * Reassurance Card at bottom
 */
@Composable
fun ReassuranceCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = PrimaryGold.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, PrimaryGold.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = PrimaryGold,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Great style is a few seconds away!",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Thanks for your patience",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                )
            }
        }
    }
}

data class TimelineStepData(
    val id: Int,
    val title: String,
    val subtitle: String
)
