package com.jagtarapvtltd.tryzonai.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jagtarapvtltd.tryzonai.ui.theme.PrimaryGold
import com.jagtarapvtltd.tryzonai.viewmodel.AuthViewModel
import com.jagtarapvtltd.tryzonai.viewmodel.PaymentViewModel
import com.jagtarapvtltd.tryzonai.viewmodel.PaymentUiState
import com.jagtarapvtltd.tryzonai.utils.findActivity
import kotlinx.coroutines.launch

@Composable
fun PremiumScreen(
    onNavigateBack: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onUpgradeSuccess: () -> Unit,
    viewModel: PaymentViewModel = viewModel(),
    authViewModel: AuthViewModel
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var selectedTab by remember { mutableStateOf(1) } // Default to 1: Credit Packs (Pocket Pack ₹39.00 at top)

    val user by authViewModel.user.collectAsState()
    val isUserLoggedIn = user != null

    val productDetails by viewModel.productDetails.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val activity = context.findActivity()

    androidx.activity.compose.BackHandler {
        onNavigateBack()
    }

    LaunchedEffect(Unit) {
        viewModel.fetchProductDetails()
        com.jagtarapvtltd.tryzonai.utils.AnalyticsHelper.logEvent("premium_screen_viewed")
    }

    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is PaymentUiState.Error -> {
                android.widget.Toast.makeText(context, state.message, android.widget.Toast.LENGTH_LONG).show()
            }
            is PaymentUiState.Success -> {
                android.widget.Toast.makeText(context, "Purchase Successful! Credits Added.", android.widget.Toast.LENGTH_LONG).show()
                onUpgradeSuccess()
            }
            else -> {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 12.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        // Title Header — Clear & Policy Compliant
        Text(
            text = "TRYZON AI PRO",
            style = MaterialTheme.typography.headlineMedium,
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = "Premium AI Virtual Try-On",
            style = MaterialTheme.typography.bodyMedium,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = PrimaryGold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Segmented Tab Selector with iOS Spring Sliding Pill
        AnimatedSegmentedControl(
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it }
        )

        Spacer(modifier = Modifier.height(20.dp))

        if (selectedTab == 0) {
            SubscriptionsList(
                productDetails = productDetails,
                onPlanSelect = { plan ->
                    if (!isUserLoggedIn) {
                        onNavigateToLogin()
                    } else {
                        if (activity != null) {
                            viewModel.startPayment(activity, plan.productId, isSubscription = true)
                        }
                    }
                }
            )
        } else {
            CreditPacksList(
                productDetails = productDetails,
                onPackSelect = { plan ->
                    if (!isUserLoggedIn) {
                        onNavigateToLogin()
                    } else {
                        if (activity != null) {
                            viewModel.startPayment(activity, plan.productId, isSubscription = false)
                        }
                    }
                },
                onSwitchToSubscriptions = { selectedTab = 0 },
                isFirstTimeBuyer = (user?.paid_credits ?: 0) == 0
            )
        }

        // Generous bottom spacing for navigation bar compatibility
        Spacer(modifier = Modifier.height(140.dp))
    }
}

@Composable
fun AnimatedSegmentedControl(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth(0.88f)
            .height(46.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                shape = RoundedCornerShape(100.dp)
            )
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                RoundedCornerShape(100.dp)
            )
            .padding(4.dp)
    ) {
        val totalWidth = maxWidth
        val tabWidth = (totalWidth - 8.dp) / 2

        val animatedPillOffset by animateDpAsState(
            targetValue = if (selectedTab == 0) 0.dp else tabWidth,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            ),
            label = "tab_pill_anim"
        )

        // iOS Smooth Gold Sliding Pill Indicator
        Box(
            modifier = Modifier
                .offset(x = animatedPillOffset)
                .width(tabWidth)
                .fillMaxHeight()
                .background(PrimaryGold, shape = RoundedCornerShape(100.dp))
        )

        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onTabSelected(0) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Subscriptions",
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp,
                    color = if (selectedTab == 0) Color.Black else MaterialTheme.colorScheme.onSurface
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onTabSelected(1) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Credit Packs",
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp,
                    color = if (selectedTab == 1) Color.Black else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun SubscriptionsList(
    productDetails: Map<String, com.android.billingclient.api.ProductDetails>, 
    onPlanSelect: (PricingPlan) -> Unit
) {
    val subs = listOf(
        PricingPlan(
            name = "Weekly Pro", 
            productId = "sub_weekly_pro", 
            subtitle = "Unlimited AI Try-Ons • Renews Weekly", 
            price = "₹119.00", 
            originalPrice = null, 
            tag = "", 
            features = emptyList(), 
            highlight = false, 
            billingPeriod = " / wk",
            renewalTerms = "Renews automatically every week until cancelled."
        ),
        PricingPlan(
            name = "Monthly Pro", 
            productId = "sub_monthly_pro", 
            subtitle = "Unlimited AI Try-Ons • 100% Zero Ads • VIP Queue", 
            price = "₹379.00", 
            originalPrice = null, 
            tag = "RECOMMENDED ⭐", 
            features = emptyList(), 
            highlight = true, 
            billingPeriod = " / mo",
            renewalTerms = "Renews automatically every month until cancelled."
        ),
        PricingPlan(
            name = "Yearly Legend", 
            productId = "sub_yearly_legend", 
            subtitle = "Unlimited AI Try-Ons • Save 60%", 
            price = "₹1,799.00", 
            originalPrice = null, 
            tag = "SAVE 60% 👑", 
            features = emptyList(), 
            highlight = false, 
            billingPeriod = " / yr",
            renewalTerms = "Renews automatically every year until cancelled."
        )
    ).map { plan ->
        val details = productDetails[plan.productId]
        val offerDetails = details?.subscriptionOfferDetails?.firstOrNull()
        val formattedPrice = offerDetails?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice
        if (formattedPrice != null) plan.copy(formattedPrice = formattedPrice) else plan
    }

    var selectedPlanId by remember { mutableStateOf("sub_monthly_pro") }
    val selectedPlan = subs.firstOrNull { it.productId == selectedPlanId } ?: subs.first()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Feature Header Bar
        PaywallFeaturesHeader()

        Spacer(modifier = Modifier.height(4.dp))

        // Selectable Luxury Subscription Cards Stack
        subs.forEach { plan ->
            LuxurySelectableCard(
                plan = plan.copy(price = (plan.formattedPrice ?: plan.price) + plan.billingPeriod),
                isSelected = plan.productId == selectedPlanId,
                onSelect = { 
                    selectedPlanId = plan.productId
                    onPlanSelect(plan)
                }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // iOS 26 Shimmering Pulsating Primary CTA Button
        ShimmeringCtaButton(
            text = "SUBSCRIBE — ${selectedPlan.formattedPrice ?: selectedPlan.price}${selectedPlan.billingPeriod} 👑",
            onClick = { onPlanSelect(selectedPlan) }
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = selectedPlan.renewalTerms,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        SubscriptionTermsBox()
    }
}

@Composable
fun CreditPacksList(
    productDetails: Map<String, com.android.billingclient.api.ProductDetails>, 
    onPackSelect: (PricingPlan) -> Unit, 
    onSwitchToSubscriptions: () -> Unit = {},
    isFirstTimeBuyer: Boolean = true
) {
    val packs = listOf(
        PricingPlan(
            name = if (isFirstTimeBuyer) "🎁 First Buyer Special (25 Fits)" else "⚡ Ad-Free Fast Pass (15 Fits)", 
            productId = "credits_pocket", 
            subtitle = if (isFirstTimeBuyer) "15 + 10 BONUS • ₹1.56 / fit ☕" else "15 Fast Passes • 100% Zero Ads", 
            price = "₹39.00", 
            originalPrice = null, 
            tag = if (isFirstTimeBuyer) "BEST OFFER ⭐" else "BEST VALUE", 
            features = emptyList(),
            highlight = true,
            renewalTerms = "One-time purchase. No recurring billing."
        ),
        PricingPlan(
            name = "Starter Pack (60 Credits)", 
            productId = "credits_starter", 
            subtitle = "60 AI Try-Ons • ₹1.65 / fit", 
            price = "₹99.00", 
            originalPrice = null, 
            tag = "", 
            features = emptyList(),
            renewalTerms = "One-time purchase. No recurring billing."
        ),
        PricingPlan(
            name = "Popular Pack (500 Credits)", 
            productId = "credits_value", 
            subtitle = "500 AI Try-Ons • ₹0.70 / fit", 
            price = "₹349.00", 
            originalPrice = null, 
            tag = "POPULAR 🔥", 
            features = emptyList(), 
            highlight = false,
            renewalTerms = "One-time purchase. No recurring billing."
        ),
        PricingPlan(
            name = "Pro Pack (2,000 Credits)", 
            productId = "credits_business", 
            subtitle = "2,000 AI Try-Ons • ₹0.45 / fit", 
            price = "₹899.00", 
            originalPrice = null, 
            tag = "HEAVY USERS", 
            features = emptyList(),
            renewalTerms = "One-time purchase. No recurring billing."
        ),
        PricingPlan(
            name = "Ultimate Pack (7,000 Credits)", 
            productId = "credits_enterprise", 
            subtitle = "7,000 AI Try-Ons • ₹0.38 / fit", 
            price = "₹2,699.00", 
            originalPrice = null, 
            tag = "MAX VALUE 👑", 
            features = emptyList(),
            renewalTerms = "One-time purchase. No recurring billing."
        )
    ).map { plan ->
        val details = productDetails[plan.productId]
        val formattedPrice = details?.oneTimePurchaseOfferDetails?.formattedPrice
        if (formattedPrice != null) plan.copy(formattedPrice = formattedPrice) else plan
    }

    var selectedPlanId by remember { mutableStateOf("credits_pocket") }
    val selectedPlan = packs.firstOrNull { it.productId == selectedPlanId } ?: packs.first()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Feature Header Bar
        PaywallFeaturesHeader()

        Spacer(modifier = Modifier.height(4.dp))

        // Selectable Luxury Credit Pack Cards Stack (All 5 visible at once!)
        packs.forEach { plan ->
            LuxurySelectableCard(
                plan = plan,
                isSelected = plan.productId == selectedPlanId,
                onSelect = { 
                    selectedPlanId = plan.productId
                    onPackSelect(plan)
                }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // iOS 26 Shimmering Pulsating Primary CTA Button
        ShimmeringCtaButton(
            text = "CONTINUE — ${selectedPlan.formattedPrice ?: selectedPlan.price} 🚀",
            onClick = { onPackSelect(selectedPlan) }
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "🔒 100% Secure Google Play Checkout • Credits Never Expire",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Switch to Subscriptions Link
        Surface(
            onClick = onSwitchToSubscriptions,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "👑 Want Unlimited Monthly Try-Ons?",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "VIEW PRO PASS →",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = PrimaryGold
                )
            }
        }
    }
}

@Composable
fun ShimmeringCtaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "cta_shimmer_pulse")

    // Pulsating scale (1.0f to 1.02f) for iOS luxury glow feel
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(1300, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cta_pulse_scale"
    )

    // Shimmer sweep across gold gradient
    val shimmerTranslate by infiniteTransition.animateFloat(
        initialValue = -300f,
        targetValue = 900f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "cta_shimmer_translate"
    )

    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            PrimaryGold,
            Color(0xFFFFF7C2),
            PrimaryGold,
            Color(0xFFE5A900),
            PrimaryGold
        ),
        start = Offset(shimmerTranslate, 0f),
        end = Offset(shimmerTranslate + 300f, 300f)
    )

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val finalScale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else pulseScale,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "cta_press_scale"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .graphicsLayer {
                scaleX = finalScale
                scaleY = finalScale
            }
            .background(shimmerBrush, shape = RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.Black,
            fontSize = 15.sp,
            color = Color.Black,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
fun LuxurySelectableCard(
    plan: PricingPlan,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val displayPrice = plan.formattedPrice ?: plan.price
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val targetScale = when {
        isPressed -> 0.97f
        isSelected -> 1.02f
        else -> 1.0f
    }

    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "card_scale"
    )

    val animatedBorderColor by animateColorAsState(
        targetValue = if (isSelected) PrimaryGold else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
        animationSpec = tween(durationMillis = 250),
        label = "card_border_color"
    )

    val animatedContainerColor by animateColorAsState(
        targetValue = if (isSelected) PrimaryGold.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface,
        animationSpec = tween(durationMillis = 250),
        label = "card_container_color"
    )

    val animatedElevation by animateDpAsState(
        targetValue = if (isSelected) 4.dp else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "card_elevation"
    )

    Surface(
        onClick = onSelect,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(14.dp),
        color = animatedContainerColor,
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, animatedBorderColor),
        shadowElevation = animatedElevation,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Radio Selection Circle Indicator with animated scale check
            Surface(
                shape = CircleShape,
                color = if (isSelected) PrimaryGold else Color.Transparent,
                border = BorderStroke(
                    if (isSelected) 0.dp else 2.dp,
                    if (isSelected) PrimaryGold else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                ),
                modifier = Modifier.size(20.dp)
            ) {
                if (isSelected) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Center Info Column: Title & Tag on Line 1, Subtitle gets 100% full width on Line 2!
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = plan.name,
                        fontWeight = FontWeight.Black,
                        fontSize = 12.5.sp,
                        letterSpacing = (-0.2).sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (plan.tag.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = if (isSelected) PrimaryGold else PrimaryGold.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(100.dp)
                        ) {
                            Text(
                                text = plan.tag,
                                color = if (isSelected) Color.Black else PrimaryGold,
                                fontWeight = FontWeight.Black,
                                fontSize = 8.5.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = plan.subtitle,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Right Price Text
            Text(
                text = displayPrice,
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                color = if (isSelected) PrimaryGold else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun PaywallFeaturesHeader() {
    val infiniteTransition = rememberInfiniteTransition(label = "header_glow")
    val borderAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "header_border_glow"
    )

    Surface(
        color = PrimaryGold.copy(alpha = 0.12f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, PrimaryGold.copy(alpha = borderAlpha)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            HeaderFeatureItem("🛡️", "Quality Guarantee")
            HeaderFeatureDivider()
            HeaderFeatureItem("⚡", "3s VIP Speed")
            HeaderFeatureDivider()
            HeaderFeatureItem("☕", "₹1.56 / Outfit")
        }
    }
}

@Composable
private fun HeaderFeatureItem(icon: String, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(icon, fontSize = 13.sp)
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            title,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun HeaderFeatureDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(14.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    )
}

@Composable
private fun FeatureChip(text: String) {
    Surface(
        color = PrimaryGold.copy(alpha = 0.15f),
        shape = RoundedCornerShape(100.dp),
        border = BorderStroke(1.dp, PrimaryGold.copy(alpha = 0.35f))
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            fontSize = 9.5.sp,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun SubscriptionPlanCard(plan: PricingPlan, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val scope = rememberCoroutineScope()
    val isPriceLoaded = plan.formattedPrice != null
    val displayPrice = plan.formattedPrice ?: plan.price

    Surface(
        onClick = { 
            scope.launch { onClick() }
        },
        interactionSource = interactionSource,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        shadowElevation = if (plan.highlight) 8.dp else 2.dp,
        border = BorderStroke(
            if (plan.highlight) 2.dp else 1.dp, 
            if (plan.highlight) PrimaryGold else MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
        ),
        color = if (plan.highlight) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f) else MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header: Tag Badge & Plan Name
            if (plan.tag.isNotEmpty()) {
                Surface(
                    color = PrimaryGold.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(100.dp),
                    border = BorderStroke(1.dp, PrimaryGold.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = plan.tag,
                        color = PrimaryGold,
                        fontWeight = FontWeight.Black,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Text(
                text = plan.name,
                style = MaterialTheme.typography.titleLarge,
                fontSize = 19.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 24.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Price Display — Clear Primary Price
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = displayPrice,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (plan.billingPeriod.isNotEmpty()) {
                    Text(
                        text = plan.billingPeriod,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 3.dp, start = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Bulleted Features list
            plan.features.forEach { feature ->
                Row(
                    modifier = Modifier.padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = PrimaryGold.copy(alpha = 0.2f),
                        modifier = Modifier.size(18.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Check, 
                                contentDescription = null, 
                                tint = PrimaryGold, 
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = feature, 
                        color = MaterialTheme.colorScheme.onSurface, 
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Prominent Subscribe / Buy Button
            Button(
                onClick = onClick,
                enabled = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryGold,
                    disabledContainerColor = PrimaryGold.copy(alpha = 0.5f)
                )
            ) {
                Text(
                    text = if (plan.billingPeriod.isNotEmpty()) "Subscribe — $displayPrice ${plan.billingPeriod}" else "Buy Now — $displayPrice",
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    color = Color.Black
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Explicit Prominent Auto-Renewal Disclosure (Google Play Requirement)
            Text(
                text = plan.renewalTerms,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 15.sp,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun SubscriptionTermsBox() {
    val uriHandler = LocalUriHandler.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, contentDescription = null, tint = PrimaryGold, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Subscription Terms & Policy Disclosures",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "• Subscription is OPTIONAL. Paid subscriptions are NOT required to use TryZon AI. All users receive 1 free AI Try-On every day.",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 16.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "• Auto-Renewal Notice: Subscriptions continue and renew automatically according to your plan billing period (Weekly, Monthly, or Yearly) until unsubscribed.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "• Easy Cancellation: You can cancel your subscription anytime through Google Play (Account > Payments & Subscriptions). Uninstalling the app does not cancel the subscription.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        try { uriHandler.openUri("https://tryzonai.com/privacy-policy") } catch (e: Exception) {}
                    },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        "Privacy Policy 🔗",
                        color = PrimaryGold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        textDecoration = TextDecoration.Underline
                    )
                }

                Text("|", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)

                TextButton(
                    onClick = {
                        try { uriHandler.openUri("https://tryzonai.com/terms-of-service") } catch (e: Exception) {}
                    },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        "Terms 🔗",
                        color = PrimaryGold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        textDecoration = TextDecoration.Underline
                    )
                }

                Text("|", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)

                TextButton(
                    onClick = {
                        try { uriHandler.openUri("https://play.google.com/store/account/subscriptions") } catch (e: Exception) {}
                    },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        "Manage Subscriptions ⚙️",
                        color = PrimaryGold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        textDecoration = TextDecoration.Underline
                    )
                }
            }
        }
    }
}

data class PricingPlan(
    val name: String,
    val productId: String,
    val subtitle: String,
    val price: String,
    val originalPrice: String?,
    val tag: String,
    val features: List<String>,
    val highlight: Boolean = false,
    val formattedPrice: String? = null,
    val billingPeriod: String = "",
    val renewalTerms: String = ""
)
