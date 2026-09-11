package com.jagtarapvtltd.tryzonai.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jagtarapvtltd.tryzonai.ui.theme.PrimaryGold
import kotlinx.coroutines.delay

data class TourStepData(
    val bounds: Rect? = null,
    val icon: String,
    val title: String,
    val description: String,
    val explanation: String? = null,
    val ctaText: String = "Next Step →",
    val tooltipBelow: Boolean = true
)

@Composable
fun GuidedTourOverlay(
    steps: List<TourStepData>,
    currentStep: Int,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit
) {
    if (steps.isEmpty()) return
    val step = steps.getOrNull(currentStep) ?: steps.first()

    var isAutoPlaying by remember { mutableStateOf(true) }

    // Auto-advance story timer (3.8 seconds per step)
    LaunchedEffect(currentStep, isAutoPlaying) {
        if (isAutoPlaying) {
            delay(3800)
            if (currentStep < steps.size - 1) {
                onNext()
            } else {
                isAutoPlaying = false
            }
        }
    }

    // Story progress bar animation
    val progressAnim = remember { Animatable(0f) }
    LaunchedEffect(currentStep, isAutoPlaying) {
        progressAnim.snapTo(0f)
        if (isAutoPlaying) {
            progressAnim.animateTo(1f, tween(3800, easing = LinearEasing))
        }
    }

    Dialog(
        onDismissRequest = onSkip,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable { onSkip() },
            contentAlignment = Alignment.Center
        ) {
            // Main Modal Card
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .padding(vertical = 12.dp, horizontal = 14.dp)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        onClick = {} // Intercept click inside card body
                    ),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.5.dp, Brush.linearGradient(
                    colors = listOf(PrimaryGold, PrimaryGold.copy(alpha = 0.3f), PrimaryGold)
                )),
                elevation = CardDefaults.cardElevation(defaultElevation = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Top Progress Bars (Story Style)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        steps.indices.forEach { idx ->
                            val progress = when {
                                idx < currentStep -> 1f
                                idx == currentStep -> progressAnim.value
                                else -> 0f
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(3.5.dp)
                                    .clip(RoundedCornerShape(100.dp))
                                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(progress)
                                        .background(PrimaryGold)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Top Header Row: Brand Badge & Play/Pause & Close Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = PrimaryGold.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, PrimaryGold.copy(alpha = 0.4f))
                        ) {
                            Text(
                                "✨ HOW TRYZON AI WORKS",
                                color = PrimaryGold,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .size(30.dp)
                                    .clickable { isAutoPlaying = !isAutoPlaying }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isAutoPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = "Toggle Demo",
                                        tint = PrimaryGold,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            IconButton(
                                onClick = onSkip,
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Close Guide",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Animated Visual Demo Canvas (Live Interactive Showcase Window)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .border(1.dp, PrimaryGold.copy(alpha = 0.25f), RoundedCornerShape(20.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedContent(
                            targetState = currentStep,
                            transitionSpec = {
                                (fadeIn(tween(300)) + scaleIn(initialScale = 0.9f)) togetherWith
                                (fadeOut(tween(200)) + scaleOut(targetScale = 0.9f))
                            },
                            label = "demo_visual"
                        ) { stepIdx ->
                            when (stepIdx) {
                                0 -> Step0VisualDemo()
                                1 -> Step1VisualDemo()
                                else -> Step2VisualDemo()
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Animated Step Text Content
                    AnimatedContent(
                        targetState = currentStep,
                        transitionSpec = {
                            (fadeIn(tween(200)) + slideInHorizontally { it / 4 }) togetherWith
                            (fadeOut(tween(150)) + slideOutHorizontally { -it / 4 })
                        },
                        label = "content_anim"
                    ) { stepIdx ->
                        val s = steps.getOrNull(stepIdx) ?: step
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = s.title,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = s.description,
                                fontSize = 12.5.sp,
                                lineHeight = 17.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )

                            if (!s.explanation.isNullOrEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = PrimaryGold.copy(alpha = 0.08f),
                                    border = BorderStroke(1.dp, PrimaryGold.copy(alpha = 0.25f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = s.explanation,
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.90f),
                                        modifier = Modifier.padding(8.dp),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Action Buttons Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (currentStep > 0) {
                            TextButton(
                                onClick = {
                                    isAutoPlaying = false
                                    onBack()
                                },
                                modifier = Modifier.height(44.dp)
                            ) {
                                Text(
                                    "← Back",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            TextButton(
                                onClick = onSkip,
                                modifier = Modifier.height(44.dp)
                            ) {
                                Text(
                                    "Skip Guide",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        }

                        val buttonText = if (currentStep == steps.size - 1) "Try It Yourself 🚀" else step.ctaText
                        ShimmeringButton(
                            text = buttonText,
                            onClick = {
                                isAutoPlaying = false
                                if (currentStep < steps.size - 1) onNext() else onSkip()
                            },
                            modifier = Modifier.height(44.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Step0VisualDemo() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_hand")
    val handOffsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -10f,
        animationSpec = infiniteRepeatable(tween(600, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "hand"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.TopEnd) {
            Surface(
                modifier = Modifier.size(72.dp),
                shape = CircleShape,
                color = PrimaryGold.copy(alpha = 0.15f),
                border = BorderStroke(2.dp, PrimaryGold)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("📸", fontSize = 34.sp)
                }
            }
            Surface(
                shape = CircleShape,
                color = Color(0xFF10B981),
                modifier = Modifier.size(22.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("✓", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black)
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Surface(
            shape = RoundedCornerShape(100.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, PrimaryGold.copy(alpha = 0.5f))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text("👆", fontSize = 16.sp, modifier = Modifier.offset(y = handOffsetY.dp))
                Text("1. Upload Selfie / Full Body Photo", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = PrimaryGold)
            }
        }
    }
}

@Composable
private fun Step1VisualDemo() {
    val infiniteTransition = rememberInfiniteTransition(label = "slide_outfit")
    val slideOffset by infiniteTransition.animateFloat(
        initialValue = -15f,
        targetValue = 15f,
        animationSpec = infiniteRepeatable(tween(1200, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "slide"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.offset(x = slideOffset.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                modifier = Modifier.size(54.dp)
            ) {
                Box(contentAlignment = Alignment.Center) { Text("👔", fontSize = 24.sp) }
            }
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = PrimaryGold.copy(alpha = 0.15f),
                border = BorderStroke(2.dp, PrimaryGold),
                modifier = Modifier.size(68.dp)
            ) {
                Box(contentAlignment = Alignment.Center) { Text("👗", fontSize = 32.sp) }
            }
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                modifier = Modifier.size(54.dp)
            ) {
                Box(contentAlignment = Alignment.Center) { Text("🧥", fontSize = 24.sp) }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Surface(
            shape = RoundedCornerShape(100.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, PrimaryGold.copy(alpha = 0.5f))
        ) {
            Text("2. Pick Outfit or Paste Store Link", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = PrimaryGold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
        }
    }
}

@Composable
private fun Step2VisualDemo() {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer_ai")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "shimmer"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, PrimaryGold.copy(alpha = 0.5f)),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) { Text("👤", fontSize = 22.sp) }
            }

            Text("✨", fontSize = 24.sp, modifier = Modifier.graphicsLayer { alpha = alphaAnim })

            Surface(
                shape = CircleShape,
                color = PrimaryGold.copy(alpha = 0.2f),
                border = BorderStroke(2.dp, PrimaryGold),
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) { Text("✨👗", fontSize = 24.sp) }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Surface(
            shape = RoundedCornerShape(100.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, PrimaryGold.copy(alpha = 0.5f))
        ) {
            Text("3. AI Realistic fitting transformation (98% Match)", fontSize = 11.sp, fontWeight = FontWeight.Black, color = PrimaryGold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp))
        }
    }
}
