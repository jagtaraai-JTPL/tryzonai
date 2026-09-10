package com.jagtarapvtltd.tryzonai.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jagtarapvtltd.tryzonai.R
import com.jagtarapvtltd.tryzonai.ui.theme.*
import com.jagtarapvtltd.tryzonai.viewmodel.AuthViewModel
import com.jagtarapvtltd.tryzonai.ui.components.bounceClick

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onNavigateToRegister: () -> Unit,
    onLoginSuccess: () -> Unit,
    viewModel: AuthViewModel
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val user by viewModel.user.collectAsState()

    // ── HIGH FASHION EDITORIAL CATALOG PAIRS ──
    val outfits = remember {
        listOf(
            Triple(R.drawable.korea_base, R.drawable.korea_var, "Korean Minimalist Wool Suit"),
            Triple(R.drawable.usa_base, R.drawable.usa_var, "New York Tailored Tuxedo"),
            Triple(R.drawable.swiss_base, R.drawable.swiss_var, "Swiss Alpine Cashmere Coat"),
            Triple(R.drawable.monaco_base, R.drawable.monaco_var, "Monaco Atelier Evening Gown"),
            Triple(R.drawable.uae_base, R.drawable.uae_var, "Dubai Royal Velvet Couture"),
            Triple(R.drawable.japan_base, R.drawable.japan_var, "Tokyo Cyberpunk Streetwear")
        )
    }

    var currentHeroFrame by remember { mutableStateOf(0) }

    // ⚡ FLUID 3.0s CYCLE (2.2s Scan + 0.8s Transition = Zero Lag)
    LaunchedEffect(Unit) {
        while(true) {
            kotlinx.coroutines.delay(3000)
            currentHeroFrame = (currentHeroFrame + 1) % outfits.size
        }
    }

    LaunchedEffect(user) {
        if (user != null) {
            onLoginSuccess()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFAF9F6)) // Warm Pearl Ivory Canvas
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // ── 2. DOMINANT HERO FASHION IMAGE CANVAS ──
            Surface(
                shape = RoundedCornerShape(30.dp),
                color = Color.Black,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    outfits.forEachIndexed { index, outfit ->
                        val alpha by animateFloatAsState(
                            targetValue = if (index == currentHeroFrame) 1f else 0f,
                            animationSpec = tween(500),
                            label = "hero_alpha"
                        )
                        if (alpha > 0.01f) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .graphicsLayer { this.alpha = alpha }
                            ) {
                                AuthHeroTryOnVisual(
                                    baseImageRes = outfit.first,
                                    varImageRes = outfit.second,
                                    isCurrent = index == currentHeroFrame
                                )
                            }
                        }
                    }

                    // Bottom Cinematic Gradient Overlay for Editorial Caption
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.35f)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.70f)
                                    )
                                )
                            )
                    )

                    // Integrated Editorial Caption
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(20.dp)
                    ) {
                        Text(
                            outfits[currentHeroFrame].third,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "AI Virtual Outfit Fitting",
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── 3. NATURAL LOGIN ENTRY SECTION ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Enter your Virtual Studio",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF1A1A1A),
                    letterSpacing = (-0.3).sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Your private wardrobe, one tap away.",
                    fontSize = 13.sp,
                    color = Color(0xFF666666),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(18.dp))

                val err = error
                if (!err.isNullOrEmpty() && !err.contains("cancelled", ignoreCase = true) && !err.contains("account not selected", ignoreCase = true)) {
                    Text(
                        err,
                        color = Color(0xFFD32F2F),
                        fontSize = 11.5.sp,
                        modifier = Modifier.padding(bottom = 8.dp),
                        textAlign = TextAlign.Center
                    )
                }

                // 4. FLOATING GOOGLE AUTH CONTROL (92% WIDTH)
                val context = androidx.compose.ui.platform.LocalContext.current
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        viewModel.triggerGoogleSignIn(context)
                    },
                    shape = RoundedCornerShape(50),
                    color = Color.White,
                    shadowElevation = 3.dp,
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .height(50.dp)
                        .bounceClick(0.97f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color(0xFF1A1A1A))
                        } else {
                            Image(
                                painter = painterResource(id = R.drawable.ic_google),
                                contentDescription = "Google Logo",
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                "Continue with Google",
                                color = Color(0xFF1A1A1A),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.5.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 5. RESTRAINED FREE TRIAL MESSAGING
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text("✦ ", color = PrimaryGold, fontSize = 11.sp)
                    Text(
                        "1 free try-on every day · No card required",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF555555)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    "By signing in, you agree to our Terms & Privacy Policy",
                    color = Color(0xFF888888),
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            try {
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://tryzonai.com/privacy-policy"))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(context, "Please visit https://tryzonai.com/privacy-policy in your browser", android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                )
            }
        }
    }
}

// ── FAST FLUID OUTFIT MORPH VISUAL ENGINE (2.2s SCAN, NO LAG) ──
@Composable
fun AuthHeroTryOnVisual(baseImageRes: Int, varImageRes: Int, isCurrent: Boolean) {
    val scanlineY = remember { Animatable(0f) }

    LaunchedEffect(isCurrent) {
        if (isCurrent) {
            scanlineY.snapTo(0f)
            kotlinx.coroutines.delay(150)
            scanlineY.animateTo(1f, tween(2200, easing = LinearEasing))
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        // Base Image
        Image(
            painter = painterResource(id = baseImageRes),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Revealed Outfit Image & Paper-thin Hairline Scan (Zero Lag)
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
                    
                    // Paper-thin hairline scan line (Subtle translucent white line)
                    drawLine(
                        color = Color.White.copy(alpha = 0.5f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 2f
                    )
                },
            contentScale = ContentScale.Crop
        )
    }
}
