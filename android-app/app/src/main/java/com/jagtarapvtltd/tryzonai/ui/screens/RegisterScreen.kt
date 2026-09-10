package com.jagtarapvtltd.tryzonai.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import androidx.compose.ui.res.painterResource
import com.jagtarapvtltd.tryzonai.R
import com.jagtarapvtltd.tryzonai.ui.theme.*
import com.jagtarapvtltd.tryzonai.viewmodel.AuthViewModel
import com.jagtarapvtltd.tryzonai.utils.UrlUtils
import com.jagtarapvtltd.tryzonai.ui.components.bounceClick

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    onNavigateToLogin: () -> Unit,
    onRegisterSuccess: () -> Unit,
    viewModel: AuthViewModel
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val user by viewModel.user.collectAsState()

    // ── LIVE AI TRY-ON SCANNING ANIMATION STATE ──
    val infiniteTransition = rememberInfiniteTransition(label = "scanLine")
    val scanProgress by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scanProgress"
    )

    var currentFrame by remember { mutableStateOf(1) }
    val tryOnShowcasePairs = listOf(
        Pair("android.resource://${androidx.compose.ui.platform.LocalContext.current.packageName}/${R.drawable.sample_model_female}", "High-Fashion Evening Gown"),
        Pair("android.resource://${androidx.compose.ui.platform.LocalContext.current.packageName}/${R.drawable.sample_model_male}", "Minimalist Tailored Suit"),
        Pair("android.resource://${androidx.compose.ui.platform.LocalContext.current.packageName}/${R.drawable.sample_model_asian}", "Urban Luxe Streetwear")
    )

    LaunchedEffect(Unit) {
        while(true) {
            kotlinx.coroutines.delay(4000)
            currentFrame = (currentFrame + 1) % tryOnShowcasePairs.size
        }
    }

    LaunchedEffect(user) {
        if (user != null) {
            onRegisterSuccess()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFFFBFBF9),
                        Color(0xFFF5EFDF),
                        Color(0xFFFBFBF9)
                    )
                )
            )
    ) {
        // 1. Soft Ambient Background Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.4f),
                            Color(0xFFFBFBF9).copy(alpha = 0.9f)
                        )
                    )
                )
        )

        // 2. Content Layout Container
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(14.dp))

            // Branding Section with Transparent Brand Logo
            Image(
                painter = painterResource(id = R.drawable.ic_tryzon_transparent_logo),
                contentDescription = "TryZon AI Logo",
                modifier = Modifier.height(50.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "VIRTUAL TRY-ON STUDIO",
                color = PrimaryGold,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(18.dp))

            // ── HERO LIVE AI TRY-ON ANIMATED VIEWFINDER ──
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = Color.Black,
                border = BorderStroke(1.5.dp, PrimaryGold.copy(alpha = 0.4f)),
                shadowElevation = 18.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(270.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Live Model Try-On Image Crossfade
                    Crossfade(targetState = currentFrame, animationSpec = tween(1200)) { index ->
                        AsyncImage(
                            model = tryOnShowcasePairs[index].first,
                            contentDescription = tryOnShowcasePairs[index].second,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    // Dark Vignette Shadow Overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Black.copy(alpha = 0.5f),
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.7f)
                                    )
                                )
                            )
                    )

                    // ⚡ LIVE LASER GOLD AI SCANNING BEAM
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.08f)
                            .align(Alignment.TopStart)
                            .graphicsLayer {
                                translationY = scanProgress * 270.dp.toPx()
                            }
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Transparent,
                                        PrimaryGold.copy(alpha = 0.85f),
                                        Color.White,
                                        PrimaryGold.copy(alpha = 0.85f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )

                    // Top Floating Badge: LIVE AI SIMULATION
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color.Black.copy(alpha = 0.7f),
                        border = BorderStroke(1.dp, PrimaryGold.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF4CAF50))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "LIVE AI SIMULATION",
                                color = Color.White,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )
                        }
                    }

                    // Bottom Floating Badge: Outfit Title & Quality
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(14.dp)
                    ) {
                        Text(
                            tryOnShowcasePairs[currentFrame].second,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = PrimaryGold,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Instant Pose & Texture Alignment Active",
                                color = PrimaryGold,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── 1-TAP GOOGLE AUTHENTICATION CARD ──
            Surface(
                color = Color.White.copy(alpha = 0.85f),
                shape = RoundedCornerShape(30.dp),
                border = BorderStroke(1.5.dp, PrimaryGold.copy(alpha = 0.35f)),
                shadowElevation = 14.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Join the Atelier",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1A1A1A)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Create your global AI virtual fashion identity",
                        fontSize = 12.sp,
                        color = Color(0xFF666666),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    val err = error
                    if (!err.isNullOrEmpty()) {
                        Text(
                            err,
                            color = Color(0xFFD32F2F),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 12.dp),
                            textAlign = TextAlign.Center
                        )
                    }

                    // 1-TAP CONTINUE WITH GOOGLE BUTTON
                    val context = androidx.compose.ui.platform.LocalContext.current
                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            viewModel.triggerGoogleSignIn(context)
                        },
                        shape = RoundedCornerShape(50),
                        color = Color.White,
                        border = BorderStroke(1.5.dp, PrimaryGold),
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .bounceClick(0.96f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(22.dp), color = PrimaryGold)
                            } else {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_google),
                                    contentDescription = "Google Logo",
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    "Continue with Google",
                                    color = Color(0xFF1A1A1A),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = PrimaryGold,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 3 Free Daily Credits Pill Badge
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = PrimaryGold.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, PrimaryGold.copy(alpha = 0.3f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        ) {
                            Text(
                                "🎁 1 FREE DAILY TRY-ON INCLUDED",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryGold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        "By creating an account, you agree to our Terms of Service and Privacy Policy regarding AI data processing.",
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
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                "The Intersection of Fashion & Intelligence.\n© 2026 TryZon AI. All Rights Reserved.",
                color = Color(0xFF888888),
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                lineHeight = 16.sp
            )
            
            Spacer(modifier = Modifier.navigationBarsPadding())
        }
    }
}
