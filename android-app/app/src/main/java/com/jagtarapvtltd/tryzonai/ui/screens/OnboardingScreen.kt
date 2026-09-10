package com.jagtarapvtltd.tryzonai.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jagtarapvtltd.tryzonai.R
import com.jagtarapvtltd.tryzonai.ui.components.bounceClick
import com.jagtarapvtltd.tryzonai.ui.theme.PrimaryGold
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jagtarapvtltd.tryzonai.utils.findActivity
import com.jagtarapvtltd.tryzonai.viewmodel.PaymentViewModel

// ── LUXURY SHIMMER BRUSH EXTENSION ──
@Composable
fun rememberLuxuryShimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer_transition")
    val translateAnim by transition.animateFloat(
        initialValue = -500f,
        targetValue = 1500f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )
    return Brush.linearGradient(
        colors = listOf(
            PrimaryGold,
            Color(0xFFFFF7C2),
            PrimaryGold,
            PrimaryGold.copy(alpha = 0.8f)
        ),
        start = Offset(translateAnim - 300f, translateAnim - 300f),
        end = Offset(translateAnim, translateAnim)
    )
}

// ── HIGH-CONVERTING PSYCHOLOGICAL ONBOARDING FUNNEL ──
@Composable
fun OnboardingScreen(
    authViewModel: com.jagtarapvtltd.tryzonai.viewmodel.AuthViewModel? = null,
    paymentViewModel: PaymentViewModel = viewModel(),
    onFinish: () -> Unit
) {
    var currentPage by remember { mutableIntStateOf(0) }
    val totalPages = 4
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("try_on_prefs", android.content.Context.MODE_PRIVATE) }
    val userPrefs = remember { context.getSharedPreferences("tryzon_user_prefs", android.content.Context.MODE_PRIVATE) }

    // User Selection States for Micro-Commitment Personalization
    var selectedGender by remember { mutableStateOf(prefs.getString("user_gender", "Women") ?: "Women") }
    var selectedGoalIndex by remember { mutableIntStateOf(0) }
    var selectedVibeIndex by remember { mutableIntStateOf(0) }
    var selectedPlanIndex by remember { mutableIntStateOf(0) }

    // Auto-fetch Google Play Billing details for accurate international pricing
    LaunchedEffect(Unit) {
        paymentViewModel.fetchProductDetails()
    }

    val productDetailsMap by paymentViewModel.productDetails.collectAsState()

    val pocketDetails = productDetailsMap["credits_pocket"] ?: productDetailsMap["credits-pocket"]
    val rawPocketPrice = pocketDetails?.oneTimePurchaseOfferDetails?.formattedPrice
    val pocketPrice = rawPocketPrice ?: com.jagtarapvtltd.tryzonai.utils.CurrencyUtils.formatPrice(39)

    val proDetails = productDetailsMap["sub_monthly_pro"]
    val rawProPrice = proDetails?.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice
    val proPrice = rawProPrice ?: com.jagtarapvtltd.tryzonai.utils.CurrencyUtils.formatPrice(379)
    val displayProPrice = if (proPrice.contains("/")) proPrice else "$proPrice/mo"

    val shimmerBrush = rememberLuxuryShimmerBrush()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Ambient Light Background Glow
        val ambientPulse = rememberInfiniteTransition(label = "ambient_glow")
        val alphaGlow by ambientPulse.animateFloat(
            initialValue = 0.12f,
            targetValue = 0.22f,
            animationSpec = infiniteRepeatable(tween(2500, easing = EaseInOutSine), RepeatMode.Reverse),
            label = "ambientGlowAlpha"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            PrimaryGold.copy(alpha = alphaGlow),
                            Color.Transparent
                        ),
                        radius = 2000f
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // ── TOP HEADER: BRAND LOGO & SKIP LINK ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "◈ TRYZON AI",
                    color = PrimaryGold,
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    letterSpacing = 2.5.sp
                )

                if (currentPage < totalPages - 1) {
                    TextButton(
                        onClick = { onFinish() }
                    ) {
                        Text(
                            "Skip",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── MAIN ANIMATED FUNNEL VIEWPORT ──
            AnimatedContent(
                targetState = currentPage,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { it } +
                         fadeIn(tween(400)) + scaleIn(initialScale = 0.90f)) togetherWith
                        (slideOutHorizontally(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { -it / 2 } +
                         fadeOut(tween(300)) + scaleOut(targetScale = 1.06f))
                    } else {
                        (slideInHorizontally(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { -it } +
                         fadeIn(tween(400)) + scaleIn(initialScale = 1.06f)) togetherWith
                        (slideOutHorizontally(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { it / 2 } +
                         fadeOut(tween(300)) + scaleOut(targetScale = 0.90f))
                    }
                },
                label = "psychological_funnel_page",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                when (page) {
                    0 -> FunnelStep1_AttentionAndDesire()
                    1 -> FunnelStep2_PersonalizationAndCommitment(
                        selectedGender = selectedGender,
                        onSelectGender = { gender ->
                            selectedGender = gender
                            prefs.edit().putString("user_gender", gender).apply()
                            userPrefs.edit().putString("user_gender", gender).apply()
                        },
                        selectedGoal = selectedGoalIndex,
                        onSelectGoal = { 
                            selectedGoalIndex = it 
                            prefs.edit().putInt("user_fashion_goal", it).apply()
                        },
                        selectedVibe = selectedVibeIndex,
                        onSelectVibe = { 
                            selectedVibeIndex = it 
                            prefs.edit().putInt("user_style_vibe", it).apply()
                        }
                    )
                    2 -> FunnelStep3_CognitiveSimplicity()
                    else -> FunnelStep4_LossAversionAndGiftClaim(
                        selectedPlan = selectedPlanIndex,
                        onSelectPlan = { selectedPlanIndex = it },
                        pocketPrice = pocketPrice,
                        proPrice = proPrice
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── PROGRESS INDICATOR BAR ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                (0 until totalPages).forEach { index ->
                    val isSelected = index == currentPage
                    val width by animateDpAsState(if (isSelected) 36.dp else 8.dp, animationSpec = spring(stiffness = Spring.StiffnessMedium), label = "funnel_dot_w")
                    Box(
                        modifier = Modifier
                            .height(6.dp)
                            .width(width)
                            .clip(RoundedCornerShape(100.dp))
                            .background(
                                if (isSelected) PrimaryGold
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                            )
                    )
                    if (index < totalPages - 1) Spacer(modifier = Modifier.width(6.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── PRIMARY ACTION CTA (HIGH PSYCHOLOGICAL IMPACT & CONTINUOUS SHIMMER) ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(100.dp))
                    .background(shimmerBrush)
                    .bounceClick(0.96f)
                    .shadow(16.dp, RoundedCornerShape(100.dp), spotColor = PrimaryGold.copy(alpha = 0.6f))
                    .clickable {
                        val goalsList = listOf("Verify Fit Online", "Social Media Fashion", "Special Event Wardrobe")
                        val vibesList = listOf(
                            "All Styles & Mix",
                            "Old Money & Quiet Luxury",
                            "Oversized Streetwear",
                            "Party & Date Night",
                            "Royal Ethnic & Festive",
                            "Minimal & Airport Chic"
                        )
                        val goalStr = goalsList.getOrNull(selectedGoalIndex) ?: "Verify Fit Online"
                        val vibeStr = vibesList.getOrNull(selectedVibeIndex) ?: "All Styles & Mix"
                        authViewModel?.updateProfile(prefGender = selectedGender, prefFashionGoal = goalStr, prefStyleVibe = vibeStr)

                        if (currentPage < totalPages - 1) {
                            currentPage++
                        } else {
                            prefs.edit().putInt("selected_onboarding_plan", selectedPlanIndex).apply()
                            val activity = context.findActivity()
                            when (selectedPlanIndex) {
                                1 -> {
                                    if (activity != null) {
                                        paymentViewModel.startPayment(activity, "credits_pocket", isSubscription = false)
                                    }
                                    onFinish()
                                }
                                2 -> {
                                    if (activity != null) {
                                        paymentViewModel.startPayment(activity, "sub_monthly_pro", isSubscription = true)
                                    }
                                    onFinish()
                                }
                                else -> {
                                    onFinish()
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                val buttonText = when (currentPage) {
                    0 -> "Start My Transformation →"
                    1 -> "Personalize My AI Studio →"
                    2 -> "Unlock My Free Daily Pass →"
                    else -> when (selectedPlanIndex) {
                        1 -> "Unlock 15 Credits ($pocketPrice) ⚡"
                        2 -> "Upgrade to VIP Pro ($displayProPrice) 👑"
                        else -> "Claim Free Pass & Try First Outfit 🚀"
                    }
                }
                Text(
                    text = buttonText,
                    color = Color.Black,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── PSYCHOLOGICAL REASSURANCE FOOTNOTE ──
            val footnoteText = when (currentPage) {
                0 -> "🔒 100% Private · Preserves your face & identity"
                1 -> "✨ Tailoring 8K neural engine to your fashion preferences"
                2 -> "⚡ Takes less than 5 seconds · Powered by ComfyUI GPU cluster"
                else -> when (selectedPlanIndex) {
                    1 -> "⚡ 15 Paid Credits added instantly · 100% Ad-Free Pass"
                    2 -> "👑 VIP Turbo Speed Queue · Unlimited 8K AI Fitting"
                    else -> "🎁 Zero credit card required · Instant free fitting access"
                }
            }
            Text(
                text = footnoteText,
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ── FUNNEL STEP 1: ATTENTION & IMMEDIATE DESIRE HOOK ──
@Composable
private fun FunnelStep1_AttentionAndDesire() {
    val outfits = remember {
        listOf(
            Triple(R.drawable.uae_base, R.drawable.uae_var, "Dubai Royal Velvet Couture 🇦🇪"),
            Triple(R.drawable.swiss_base, R.drawable.swiss_var, "Swiss Alpine Cashmere Coat 🇨🇭"),
            Triple(R.drawable.monaco_base, R.drawable.monaco_var, "Monaco Atelier Evening Gown 🇲🇨"),
            Triple(R.drawable.usa_base, R.drawable.usa_var, "New York Executive Tuxedo 🇺🇸"),
            Triple(R.drawable.japan_base, R.drawable.japan_var, "Tokyo Cyberpunk Techwear 🇯🇵"),
            Triple(R.drawable.korea_base, R.drawable.korea_var, "Seoul Gangnam K-Style Minimalist 🇰🇷")
        )
    }
    var currentOutfitIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(3000)
            currentOutfitIndex = (currentOutfitIndex + 1) % outfits.size
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Hero Visual Card Frame
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(26.dp),
            border = BorderStroke(1.dp, PrimaryGold.copy(alpha = 0.35f)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                outfits.forEachIndexed { index, outfit ->
                    val isCurrent = index == currentOutfitIndex
                    val alpha by animateFloatAsState(
                        targetValue = if (isCurrent) 1f else 0f,
                        animationSpec = tween(500),
                        label = "outfit_alpha"
                    )
                    if (alpha > 0.01f) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .graphicsLayer { this.alpha = alpha }
                        ) {
                            OnboardingHeroTryOnVisual(
                                baseImageRes = outfit.first,
                                varImageRes = outfit.second,
                                isCurrent = isCurrent
                            )

                            // Bottom Gradient Overlay for text contrast
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                Color.Black.copy(alpha = 0.75f)
                                            ),
                                            startY = 250f
                                        )
                                    )
                            )

                            // Bottom Outfit Name Indicator
                            Surface(
                                shape = RoundedCornerShape(100.dp),
                                color = Color.Black.copy(alpha = 0.75f),
                                border = BorderStroke(1.dp, PrimaryGold.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 14.dp)
                            ) {
                                Text(
                                    text = "✨ " + outfit.third,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }

                // Top Social Proof & Live AI Room Badge
                val infiniteDotTransition = rememberInfiniteTransition(label = "pulse_live_dot")
                val dotAlpha by infiniteDotTransition.animateFloat(
                    initialValue = 0.25f,
                    targetValue = 1.0f,
                    animationSpec = infiniteRepeatable(tween(750, easing = EaseInOutSine), RepeatMode.Reverse),
                    label = "dotAlpha"
                )

                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = Color.Black.copy(alpha = 0.85f),
                        border = BorderStroke(1.dp, PrimaryGold.copy(alpha = 0.6f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00FF66).copy(alpha = dotAlpha))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "LIVE 8K VIRTUAL FITTING STUDIO",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Social Proof Badge
        Surface(
            shape = RoundedCornerShape(50),
            color = PrimaryGold.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, PrimaryGold.copy(alpha = 0.35f))
        ) {
            Text(
                text = "⭐ 4.9/5 · Loved by 50,000+ Fashion Enthusiasts",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryGold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Headline & Subtext
        Text(
            text = "Stop Buying Clothes\nThat Don't Fit You ✨",
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            lineHeight = 28.sp,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "See any outfit on your photo before buying.\nPowered by 8K Ultra-Real AI Drape.",
            fontSize = 13.sp,
            lineHeight = 18.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

// ── FAST FLUID OUTFIT MORPH VISUAL ENGINE ──
@Composable
private fun OnboardingHeroTryOnVisual(baseImageRes: Int, varImageRes: Int, isCurrent: Boolean) {
    val scanlineY = remember { Animatable(0f) }

    LaunchedEffect(isCurrent) {
        if (isCurrent) {
            scanlineY.snapTo(0f)
            kotlinx.coroutines.delay(150)
            scanlineY.animateTo(1f, tween(2200, easing = LinearEasing))
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = baseImageRes),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Image(
            painter = painterResource(id = varImageRes),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    val y = size.height * scanlineY.value

                    clipRect(bottom = y) {
                        this@drawWithContent.drawContent()
                    }

                    // 8K Holographic Gold Laser Beam
                    drawLine(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                PrimaryGold.copy(alpha = 0.95f),
                                Color.White,
                                PrimaryGold.copy(alpha = 0.95f),
                                Color.Transparent
                            )
                        ),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 3.5.dp.toPx()
                    )

                    // Laser Glow Aura Sweep
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                PrimaryGold.copy(alpha = 0.38f),
                                Color.Transparent
                            ),
                            startY = y,
                            endY = y + 32.dp.toPx()
                        ),
                        topLeft = Offset(0f, y),
                        size = androidx.compose.ui.geometry.Size(size.width, 32.dp.toPx())
                    )
                },
            contentScale = ContentScale.Crop
        )
    }
}

// ── FUNNEL STEP 2: PERSONALIZATION & MICRO-COMMITMENT CARDS ──
@Composable
private fun FunnelStep2_PersonalizationAndCommitment(
    selectedGender: String,
    onSelectGender: (String) -> Unit,
    selectedGoal: Int,
    onSelectGoal: (Int) -> Unit,
    selectedVibe: Int,
    onSelectVibe: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1. GENDER PREFERENCE SELECTION
            Text(
                text = "✨ Who are we styling today?",
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )

            val genders = listOf(
                Triple("Women", "👩 Female", "Women's Fits"),
                Triple("Men", "👨 Male", "Men's Fits"),
                Triple("All", "✨ Unisex", "Both / All")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                genders.forEach { (genderKey, label, sub) ->
                    val isSelected = selectedGender.equals(genderKey, ignoreCase = true)
                    Surface(
                        onClick = { onSelectGender(genderKey) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) PrimaryGold else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(
                            if (isSelected) 1.5.dp else 1.dp,
                            if (isSelected) PrimaryGold else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                        )
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(1.dp))
                            Text(
                                text = sub,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSelected) Color.Black.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // 2. MAIN GOAL SELECTION
            Text(
                text = "What is your main goal today?",
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            val goals = listOf(
                Triple("🛍️", "Verify Fit Before Buying Online", "Amazon, Zara, ASOS, SHEIN, Myntra"),
                Triple("📸", "Create Social Media Fashion Looks", "High-definition 8K photoshoot quality"),
                Triple("💼", "Formal & Special Event Wardrobe", "Suits, gowns, tuxedos & festival wear")
            )

            goals.forEachIndexed { index, item ->
                val isSelected = selectedGoal == index
                Card(
                    onClick = { onSelectGoal(index) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) PrimaryGold.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(
                        if (isSelected) 1.5.dp else 1.dp,
                        if (isSelected) PrimaryGold else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 6.dp, horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(item.first, fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.second, fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurface)
                            Text(item.third, fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (isSelected) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = PrimaryGold, modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // 3. PREFERRED STYLE VIBE
            Text(
                text = "Preferred Style Vibe:",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            val vibeRows = listOf(
                listOf(0 to "🌟 All Styles & Mix", 1 to "✨ Old Money & Luxury"),
                listOf(2 to "🔥 Oversized Streetwear", 3 to "💃 Party & Date Night"),
                listOf(4 to "🪔 Royal Ethnic & Festive", 5 to "🌿 Minimal Airport Chic")
            )

            vibeRows.forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    rowItems.forEach { (index, vibeName) ->
                        val isSelected = selectedVibe == index
                        Surface(
                            onClick = { onSelectVibe(index) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) PrimaryGold else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, if (isSelected) PrimaryGold else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                        ) {
                            Text(
                                text = vibeName,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── FUNNEL STEP 3: COGNITIVE SIMPLICITY (3 STEPS, ZERO FRICTION) ──
@Composable
private fun FunnelStep3_CognitiveSimplicity() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FunnelStepCard(
                stepNumber = "①",
                icon = "📸",
                title = "Snap or Upload Photo",
                description = "Add one selfie or full-body photo. Your face and body pose are 100% preserved."
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(
                            Brush.verticalGradient(
                                listOf(PrimaryGold.copy(alpha = 0.6f), PrimaryGold.copy(alpha = 0.2f))
                            )
                        )
                )
            }

            FunnelStepCard(
                stepNumber = "②",
                icon = "👗",
                title = "Select Style or Paste Link",
                description = "Pick catalog looks or paste any shopping link from Amazon, Zara, ASOS, SHEIN, Myntra, etc."
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(
                            Brush.verticalGradient(
                                listOf(PrimaryGold.copy(alpha = 0.6f), PrimaryGold.copy(alpha = 0.2f))
                            )
                        )
                )
            }

            FunnelStepCard(
                stepNumber = "③",
                icon = "✨",
                title = "Instant 8K AI Fitting",
                description = "See yourself wearing the outfit in 5 seconds. Compare before/after with 98% accuracy!"
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "3 Simple Steps. Zero Friction. ⚡",
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "No fashion expertise needed. Get instant results.",
            fontSize = 13.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun FunnelStepCard(
    stepNumber: String,
    icon: String,
    title: String,
    description: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, PrimaryGold.copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(46.dp),
                shape = CircleShape,
                color = PrimaryGold.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, PrimaryGold.copy(alpha = 0.5f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(icon, fontSize = 20.sp)
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stepNumber,
                        color = PrimaryGold,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        title,
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    description,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── FUNNEL STEP 4: LOSS AVERSION & WELCOME GIFT CLAIM ──
@Composable
private fun FunnelStep4_LossAversionAndGiftClaim(
    selectedPlan: Int,
    onSelectPlan: (Int) -> Unit,
    pocketPrice: String,
    proPrice: String
) {
    val giftPulse = rememberInfiniteTransition(label = "gift_pulse")
    val giftScale by giftPulse.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(1400, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "giftScale"
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.5.dp, Brush.linearGradient(
                listOf(PrimaryGold, PrimaryGold.copy(alpha = 0.3f), PrimaryGold)
            )),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Header Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Surface(
                        modifier = Modifier
                            .size(40.dp)
                            .graphicsLayer {
                                scaleX = giftScale
                                scaleY = giftScale
                            },
                        shape = CircleShape,
                        color = PrimaryGold.copy(alpha = 0.18f),
                        border = BorderStroke(1.5.dp, PrimaryGold)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("🎁", fontSize = 20.sp)
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            "CHOOSE YOUR FITTING PASS",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = PrimaryGold,
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            "1st lifetime try-on is 100% Free · No card needed",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 3 INTERACTIVE MEMBERSHIP PLAN CARDS
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Card 0: Free Daily Pass
                    OnboardingSelectablePlanCard(
                        index = 0,
                        title = "⚡ Daily Free Pass",
                        subtitle = "1 Free Try/Day + 2 Welcome Bonus Credits",
                        price = "FREE TODAY",
                        badge = "DEFAULT FREE ✅",
                        isSelected = selectedPlan == 0,
                        onSelect = onSelectPlan
                    )

                    // Card 1: Pocket Credit Pack
                    OnboardingSelectablePlanCard(
                        index = 1,
                        title = "⚡ Pocket Credit Pack",
                        subtitle = "15 Paid Credits · 100% Zero Ads · Instant Fast Pass",
                        price = pocketPrice,
                        badge = "POPULAR ⚡",
                        isSelected = selectedPlan == 1,
                        onSelect = onSelectPlan
                    )

                    // Card 2: Pro Pass
                    OnboardingSelectablePlanCard(
                        index = 2,
                        title = "👑 VIP Pro Unlimited",
                        subtitle = "Unlimited Try-Ons · 100% Zero Ads · 8K HD Exports",
                        price = if (proPrice.contains("/")) proPrice else "$proPrice/mo",
                        badge = "BEST VALUE 👑",
                        isSelected = selectedPlan == 2,
                        onSelect = onSelectPlan
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = PrimaryGold.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, PrimaryGold.copy(alpha = 0.4f))
                ) {
                    Text(
                        "🛡️ 100% Secure · Cancel Anytime · Instant Access",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = PrimaryGold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = when (selectedPlan) {
                1 -> "Unlock 15 Credits Now ⚡"
                2 -> "Get VIP Pro Access Now 👑"
                else -> "Claim Your Free Pass Now 🚀"
            },
            fontSize = 19.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(3.dp))

        Text(
            text = when (selectedPlan) {
                1 -> "15 Paid credits added to your account instantly.\n100% Ad-Free pass included."
                2 -> "Unlimited AI Try-Ons & 8K Exports.\nVIP Turbo processing speed."
                else -> "Start with 1 free AI Try-On today.\nNo credit card or subscription required."
            },
            fontSize = 12.sp,
            lineHeight = 16.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun OnboardingSelectablePlanCard(
    index: Int,
    title: String,
    subtitle: String,
    price: String,
    badge: String,
    isSelected: Boolean,
    onSelect: (Int) -> Unit
) {
    Surface(
        onClick = { onSelect(index) },
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) PrimaryGold.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(
            if (isSelected) 1.8.dp else 1.dp,
            if (isSelected) PrimaryGold else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, if (isSelected) PrimaryGold else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), CircleShape)
                        .background(if (isSelected) PrimaryGold else Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Text("✓", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color.Black)
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (isSelected) PrimaryGold else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        ) {
                            Text(
                                text = badge,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                                color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = subtitle,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = price,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                color = if (isSelected) PrimaryGold else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun GiftBenefitItem(text: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
