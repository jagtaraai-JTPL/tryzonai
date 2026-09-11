package com.jagtarapvtltd.tryzonai.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.jagtarapvtltd.tryzonai.ui.theme.*
import com.jagtarapvtltd.tryzonai.ui.components.bounceClick
import com.jagtarapvtltd.tryzonai.ui.components.ShimmeringButton
import com.jagtarapvtltd.tryzonai.viewmodel.CatalogViewModel
import com.jagtarapvtltd.tryzonai.utils.UrlUtils
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.*
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.runtime.snapshots.SnapshotStateMap


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToTryOn: () -> Unit,
    onNavigateToCatalog: () -> Unit,
    onProductClick: (String) -> Unit,
    onTryOnClick: (com.jagtarapvtltd.tryzonai.models.Product) -> Unit = {},
    catalogViewModel: CatalogViewModel,
    tryOnViewModel: com.jagtarapvtltd.tryzonai.viewmodel.TryOnViewModel
) {
    val scrollState = rememberScrollState()
    val trendingProducts by catalogViewModel.products.collectAsState()
    val processingState by tryOnViewModel.processingState.collectAsState()
    val tryOnResult by tryOnViewModel.tryOnResult.collectAsState()
    val selectedProduct by tryOnViewModel.selectedProduct.collectAsState()
    
    val outfits = remember {
        listOf(
            Pair(com.jagtarapvtltd.tryzonai.R.drawable.monaco_base, com.jagtarapvtltd.tryzonai.R.drawable.monaco_var),
            Pair(com.jagtarapvtltd.tryzonai.R.drawable.usa_base, com.jagtarapvtltd.tryzonai.R.drawable.usa_var),
            Pair(com.jagtarapvtltd.tryzonai.R.drawable.japan_base, com.jagtarapvtltd.tryzonai.R.drawable.japan_var),
            Pair(com.jagtarapvtltd.tryzonai.R.drawable.korea_base, com.jagtarapvtltd.tryzonai.R.drawable.korea_var)
        )
    }
    val currentHeroFrame by catalogViewModel.currentHeroFrame.collectAsState()
    
    val currentContext = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        val prefs = currentContext.getSharedPreferences("tryzon_user_prefs", Context.MODE_PRIVATE)
        val tryOnPrefs = currentContext.getSharedPreferences("try_on_prefs", Context.MODE_PRIVATE)
        if (!prefs.contains("user_gender") && !tryOnPrefs.contains("user_gender")) {
            prefs.edit().putString("user_gender", "Women").apply()
            tryOnPrefs.edit().putString("user_gender", "Women").apply()
        }
    }
    val tourBounds = remember { mutableStateMapOf<Int, androidx.compose.ui.geometry.Rect>() }

    // Spin the Wheel Dialog State
    var showSpinWheelDialog by remember { mutableStateOf(false) }

    if (showSpinWheelDialog) {
        com.jagtarapvtltd.tryzonai.ui.components.SpinWheelDialog(
            onDismiss = { showSpinWheelDialog = false },
            viewModel = tryOnViewModel
        )
    }
    
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    var isShaking by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    
    ShakeDetector(
        onShake = {
            if (!isShaking && trendingProducts.isNotEmpty()) {
                isShaking = true
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                val randomProduct = trendingProducts.random()
                onTryOnClick(randomProduct)
                
                // Reset state after a delay
                coroutineScope.launch {
                    kotlinx.coroutines.delay(2000)
                    isShaking = false
                }
            }
        }
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            // 1. HERO SECTION (Compact & Premium)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .height(480.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .onGloballyPositioned { coords ->
                        val rect = coords.boundsInRoot()
                        tourBounds[0] = rect // Step 0: AI Studio Hero
                    }
            ) {
                // Stunning Hero Background Image (Animated Try-On)
                Box(modifier = Modifier.fillMaxSize()) {
                    outfits.forEachIndexed { index, outfit ->
                        val alpha by animateFloatAsState(
                            targetValue = if (index == currentHeroFrame) 0.85f else 0f,
                            animationSpec = tween(800),
                            label = "hero_alpha"
                        )
                        if (alpha > 0.01f) {
                            Box(modifier = Modifier.matchParentSize().graphicsLayer { this.alpha = alpha }) {
                                HeroTryOnVisual(
                                    baseImageRes = outfit.first,
                                    varImageRes = outfit.second,
                                    isCurrent = index == currentHeroFrame
                                )
                            }
                        }
                    }
                }
                
                // Gradient Overlay to ensure text readability
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.4f),
                                    Color.Black.copy(alpha = 0.8f)
                                ),
                                startY = 0f,
                                endY = 900f
                            )
                        )
                )

                Column(
                    horizontalAlignment = Alignment.Start,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Surface(
                        color = PrimaryGold.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, PrimaryGold.copy(alpha = 0.3f))
                    ) {
                        Text(
                            "AI FASHION HUB",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = PrimaryGold,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Try Any Outfit\nVirtually ✨",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Black,
                            lineHeight = 44.sp,
                            shadow = androidx.compose.ui.graphics.Shadow(
                                color = Color.Black.copy(alpha = 0.5f),
                                offset = androidx.compose.ui.geometry.Offset(2f, 2f),
                                blurRadius = 8f
                            )
                        ),
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Photorealistic AI Try-On at the speed of thought.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f)
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
                        Button(
                            onClick = { 
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                onNavigateToTryOn() 
                            },
                            modifier = Modifier
                                .bounceClick(scaleDown = 0.95f, performHaptic = true)
                                .weight(1f)
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold)
                        ) {
                            Text("START NOW", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 16.sp, letterSpacing = 1.sp)
                        }
                    }
                }
            }


            // 2. STATS BAR
            StatsMarquee(
                modifier = Modifier.onGloballyPositioned { coords ->
                    tourBounds[1] = coords.boundsInRoot() // Step 1: Stats bar
                }
            )

            Spacer(modifier = Modifier.height(24.dp))
            
            // GAMIFICATION & REFERRAL
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StreakWidget(modifier = Modifier.weight(0.4f))
                ReferAndEarnBanner(modifier = Modifier.weight(0.6f))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // SPIN THE WHEEL BANNER
            com.jagtarapvtltd.tryzonai.ui.components.SpinWheelBanner(
                modifier = Modifier.padding(horizontal = 24.dp),
                onClick = { showSpinWheelDialog = true }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // RANDOM TRIAL SHOWCASE BANNER
            RandomTrialBanner(
                products = trendingProducts,
                onTryOnRandom = { randomProduct ->
                    onTryOnClick(randomProduct)
                }
            )

            Spacer(modifier = Modifier.height(40.dp))

            // 3. FASHION QUOTE OF THE DAY
            Box(
                modifier = Modifier.onGloballyPositioned { coords ->
                    tourBounds[3] = coords.boundsInRoot() // Step 3: Daily style drops
                }
            ) {
                DailyFashionQuote()
            }
            
            Spacer(modifier = Modifier.height(40.dp))

            // 4. DISCOVER SECTION
            Text(
                "DISCOVER",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                color = PrimaryGold
            )
            Text(
                "Trending Global Fashion",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Horizontal Trending Row
            LazyRow(
                contentPadding = PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.onGloballyPositioned { coords ->
                    tourBounds[2] = coords.boundsInRoot() // Step 2: Trending catalog
                }
            ) {
                items(trendingProducts.take(6)) { product ->
                    ProductReplicaCard(product, onProductClick, onTryOnClick)
                }
            }

            Spacer(modifier = Modifier.height(60.dp))



            // 5. STEPS SECTION
            Text(
                "HOW IT WORKS",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                color = PrimaryGold
            )
            
            Spacer(modifier = Modifier.height(32.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .onGloballyPositioned { coords ->
                        tourBounds[4] = coords.boundsInRoot() // Step 4: How it works steps
                    }
            ) {
                Column {
                    StepReplicaCard("01", "📸", "Upload Photo", "Our AI understands your body shape instantly.")
                    StepReplicaCard("02", "👗", "Pick Outfit", "TryZon Vision Engine maps any style to your body.")
                    StepReplicaCard("03", "✨", "See Result", "Get photorealistic results in seconds.")
                }
            }

        }
    }
}

@Composable
fun ActiveTryOnCard(
    state: com.jagtarapvtltd.tryzonai.viewmodel.ProcessingState,
    result: com.jagtarapvtltd.tryzonai.models.TryOnResult?,
    product: com.jagtarapvtltd.tryzonai.models.Product?,
    onReset: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, PrimaryGold.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = PrimaryGold,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    if (result != null) "TRY-ON COMPLETE" else "AI PROCESSING...",
                    style = MaterialTheme.typography.labelLarge,
                    color = PrimaryGold,
                    fontWeight = FontWeight.Black
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onReset, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(16.dp))
                        
                ) {
                    AsyncImage(
                        model = UrlUtils.getCoilModel(result?.resultImage ?: product?.image ?: ""),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    if (result == null) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center).size(24.dp),
                            color = PrimaryGold,
                            strokeWidth = 2.dp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        product?.name ?: "Virtual Outfit",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1
                    )
                    Text(
                        if (result != null) "View your high-fidelity render" else "Step ${state.step}/5: ${state.name.lowercase().replaceFirstChar { it.uppercase() }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun StatsMarquee(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatItem("50K+", "GLOBAL STYLES")
            Box(modifier = Modifier.width(1.dp).height(20.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)))
            StatItem("98.5%", "ACCURACY")
            Box(modifier = Modifier.width(1.dp).height(20.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)))
            StatItem("2M+", "TRY-ONS")
        }
    }
}

@Composable
fun StatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 9.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.3.sp
        )
    }
}

@Composable
fun ProductReplicaCard(
    product: com.jagtarapvtltd.tryzonai.models.Product,
    onClick: (String) -> Unit,
    onTryOnClick: (com.jagtarapvtltd.tryzonai.models.Product) -> Unit = {},
    modifier: Modifier = Modifier.width(220.dp)
) {
    Card(
        modifier = modifier
            .clickable { onTryOnClick(product) },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.85f)
            ) {
                AsyncImage(
                    model = UrlUtils.getCoilModel(product.image),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                if (product.badge != null) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp),
                        color = MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(100.dp),
                        border = BorderStroke(1.dp, PrimaryGold.copy(alpha = 0.4f))
                    ) {
                        Text(
                            product.badge.orEmpty(),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            color = PrimaryGold,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Text(
                    product.brand,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    product.name,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(10.dp))
                
                Button(
                    onClick = { onTryOnClick(product) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp),
                    contentPadding = PaddingValues(0.dp),
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold)
                ) {
                    Text("TRY ON", color = Color.Black, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun StepReplicaCard(num: String, icon: String, title: String, desc: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(modifier = Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(num, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                Spacer(modifier = Modifier.height(4.dp))
                Text(title, style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp), color = MaterialTheme.colorScheme.onBackground)
                Text(desc, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
            Text(icon, fontSize = 32.sp)
        }
    }
}

@Composable
fun StreakWidget(modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var currentStreak by remember { mutableStateOf(1) }
    
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("gamification_prefs", Context.MODE_PRIVATE)
        val lastLogin = prefs.getLong("last_login_time", 0L)
        var streak = prefs.getInt("current_streak", 1)
        
        val now = java.util.Calendar.getInstance()
        val last = java.util.Calendar.getInstance().apply { timeInMillis = lastLogin }
        
        val nowDay = now.get(java.util.Calendar.DAY_OF_YEAR)
        val nowYear = now.get(java.util.Calendar.YEAR)
        val lastDay = last.get(java.util.Calendar.DAY_OF_YEAR)
        val lastYear = last.get(java.util.Calendar.YEAR)
        
        if (lastLogin == 0L) {
            streak = 1
        } else if (nowYear == lastYear && nowDay - lastDay == 1) {
            streak += 1
        } else if (nowYear != lastYear || nowDay - lastDay > 1) {
            streak = 1 // Reset if missed a day
        }
        
        prefs.edit()
            .putLong("last_login_time", System.currentTimeMillis())
            .putInt("current_streak", streak)
            .apply()
            
        currentStreak = streak
    }

    Card(
        modifier = modifier
            .height(84.dp)
            .bounceClick(0.95f)
            .clickable {
                android.widget.Toast.makeText(context, "Keep coming back to build your streak and unlock rewards!", android.widget.Toast.LENGTH_SHORT).show()
            },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🔥 $currentStreak", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(2.dp))
            Text("Day Streak", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), fontSize = 10.sp)
        }
    }
}

@Composable
fun ReferAndEarnBanner(modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Card(
        modifier = modifier
            .height(84.dp)
            .bounceClick(0.95f)
            .clickable {
                val sendIntent: android.content.Intent = android.content.Intent().apply {
                    action = android.content.Intent.ACTION_SEND
                    putExtra(android.content.Intent.EXTRA_TEXT, "I've been styling my outfits with TryZon AI! Download the app and get 10 Free Try-Ons! ✨\n\nhttps://play.google.com/store/apps/details?id=${context.packageName}")
                    type = "text/plain"
                }
                val shareIntent = android.content.Intent.createChooser(sendIntent, "Invite Friends via")
                context.startActivity(shareIntent)
            },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Invite Friends", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp), color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(2.dp))
                Text("Get 10 Free Tries", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), fontSize = 10.sp)
            }
            Icon(Icons.Default.CardGiftcard, contentDescription = null, tint = PrimaryGold, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
fun HeroTryOnVisual(baseImageRes: Int, varImageRes: Int, isCurrent: Boolean) {
    val scanlineY = remember { androidx.compose.animation.core.Animatable(0f) }

    LaunchedEffect(isCurrent) {
        if (isCurrent) {
            scanlineY.snapTo(0f)
            kotlinx.coroutines.delay(600) // Wait for Crossfade/Alpha to finish
            scanlineY.animateTo(1f, tween(2500, easing = androidx.compose.animation.core.LinearEasing))
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        // Base Image
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(id = baseImageRes),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Revealed Image & Scanline
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(id = varImageRes),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    val y = size.height * scanlineY.value
                    
                    clipRect(bottom = y) {
                        this@drawWithContent.drawContent()
                    }
                    
                    // Draw glowing laser scanline
                    drawLine(
                        color = Color.White,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 6f
                    )
                    
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Transparent, PrimaryGold.copy(alpha = 0.8f), Color.Transparent),
                            startY = y - 50f,
                            endY = y + 50f
                        ),
                        topLeft = Offset(0f, y - 50f),
                        size = Size(size.width, 100f)
                    )
                },
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
fun DailyFashionQuote() {
    val quotes = remember {
        listOf(
            "\"Style is a way to say who you are without having to speak.\" — Rachel Zoe",
            "\"Fashion is what you're offered. Style is what you choose.\" — Lauren Hutton",
            "\"You can have anything you want in life if you dress for it.\" — Edith Head",
            "\"Elegance is the only beauty that never fades.\" — Audrey Hepburn",
            "\"Clothes mean nothing until someone lives in them.\" — Marc Jacobs"
        )
    }
    
    val todaysQuote = remember { quotes.random() }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "“",
            fontSize = 36.sp,
            color = PrimaryGold.copy(alpha = 0.6f),
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = todaysQuote,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontStyle = FontStyle.Italic,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Serif
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
    }
}

@Composable
fun ShakeDetector(onShake: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val currentOnShake by rememberUpdatedState(onShake)
    
    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        
        var lastUpdate: Long = 0
        val SHAKE_THRESHOLD_GRAVITY = 2.5f
        
        val sensorEventListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                
                val gX = x / android.hardware.SensorManager.GRAVITY_EARTH
                val gY = y / android.hardware.SensorManager.GRAVITY_EARTH
                val gZ = z / android.hardware.SensorManager.GRAVITY_EARTH
                
                val gForce = Math.sqrt((gX * gX + gY * gY + gZ * gZ).toDouble()).toFloat()
                
                if (gForce > SHAKE_THRESHOLD_GRAVITY) {
                    val now = System.currentTimeMillis()
                    // Ignore shake events too close to each other (1000ms)
                    if (lastUpdate + 1000 > now) {
                        return
                    }
                    lastUpdate = now
                    currentOnShake()
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        
        if (accelerometer != null) {
            sensorManager.registerListener(sensorEventListener, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
        }
        
        onDispose {
            sensorManager.unregisterListener(sensorEventListener)
        }
    }
}

@Composable
fun FeatureDiscoveryDialog(onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("✨ What's New in TryZon!", fontWeight = FontWeight.Black, color = PrimaryGold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                TipRow("📳", "Shake to Shuffle", "Shake your phone right now to instantly discover and try on a random trending outfit!")
                TipRow("❤️", "Double-Tap to Closet", "Double-tap your generated Try-On result to instantly save it with a cool animation.")
                TipRow("🔍", "Shop with Google Lens", "Use the new Lens button on your results to find and buy similar clothes online.")
                TipRow("🎁", "Refer & Earn", "Tap the 'Invite Friends' banner to share the app and unlock rewards!")
            }
        },
        confirmButton = {
            ShimmeringButton(
                text = "Got it! Let's Go 🚀",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
fun TipRow(icon: String, title: String, desc: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(icon, fontSize = 20.sp)
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp)
            Text(desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f), lineHeight = 16.sp)
        }
    }
}

@Composable
fun RandomTrialBanner(
    products: List<com.jagtarapvtltd.tryzonai.models.Product>,
    onTryOnRandom: (com.jagtarapvtltd.tryzonai.models.Product) -> Unit
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text("🎲", fontSize = 20.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "INSTANT RANDOM TRIAL",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                "Can't decide? Tap to let AI pick a surprise outfit for your virtual try-on!",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    if (products.isNotEmpty()) {
                        val randomItem = products.random()
                        onTryOnRandom(randomItem)
                    }
                },
                modifier = Modifier
                    .bounceClick(0.95f)
                    .fillMaxWidth()
                    .height(46.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Try Random Outfit 🎲✨",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp
                    )
                }
            }
        }
    }
}

@Composable
fun GenderSelectionModal(
    currentGender: String,
    onGenderSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember { mutableStateOf(currentGender) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("👗 Select Your Fashion Preference", fontWeight = FontWeight.Black, fontSize = 18.sp, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Choose fashion category for tailored AI Try-On & Daily Style Drops!",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val options = listOf(
                    Triple("Women", "👩 Women's Fashion", "Dresses, Tops, Sarees & Ethnic Wear"),
                    Triple("Men", "👨 Men's Fashion", "Shirts, Jackets, Suits & Casuals"),
                    Triple("Unisex", "👫 All & Unisex Fashion", "Explore both Men's & Women's styles")
                )

                options.forEach { (genderKey, title, subtitle) ->
                    val isSelected = selected.equals(genderKey, ignoreCase = true)
                    Card(
                        onClick = { selected = genderKey },
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) PrimaryGold else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(16.dp)
                            ),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) PrimaryGold.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { selected = genderKey },
                                colors = RadioButtonDefaults.colors(selectedColor = PrimaryGold)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            ShimmeringButton(
                text = "Save Preference ✨",
                onClick = { onGenderSelected(selected) },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            )
        }
    )
}
