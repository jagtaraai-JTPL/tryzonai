package com.jagtarapvtltd.tryzonai.ui.components

import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.jagtarapvtltd.tryzonai.ui.theme.PrimaryGold
import kotlinx.coroutines.launch
import java.util.Calendar

@Composable
fun SpinWheelBanner(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(76.dp)
            .bounceClick(0.95f)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Daily Spin to Win! 🎡", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp), color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(2.dp))
                Text("Win up to 5 Free Try-Ons", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), fontSize = 11.sp)
            }
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold),
                shape = RoundedCornerShape(50),
                modifier = Modifier.height(36.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
            ) {
                Text("SPIN", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun SpinWheelDialog(
    onDismiss: () -> Unit,
    viewModel: com.jagtarapvtltd.tryzonai.viewmodel.TryOnViewModel? = null
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("gamification_prefs", Context.MODE_PRIVATE)
    
    val lastSpinTime = prefs.getLong("last_spin_time", 0L)
    val now = Calendar.getInstance()
    val lastSpin = Calendar.getInstance().apply { timeInMillis = lastSpinTime }
    
    val canSpin = lastSpinTime == 0L || 
                  now.get(Calendar.DAY_OF_YEAR) != lastSpin.get(Calendar.DAY_OF_YEAR) || 
                  now.get(Calendar.YEAR) != lastSpin.get(Calendar.YEAR)

    var isSpinning by remember { mutableStateOf(false) }
    var showReward by remember { mutableStateOf<String?>(null) }
    
    val rotation = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    
    val segments = listOf(
        "1 Free Try", "Better Luck", "2 Free Tries", "Better Luck", "5 Free Tries", "Better Luck"
    )
    val segmentColors = listOf(
        Color(0xFFFFD700), Color(0xFF333333), Color(0xFFFFD700), Color(0xFF333333), Color(0xFFFFD700), Color(0xFF333333)
    )

    Dialog(onDismissRequest = { if (!isSpinning) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, PrimaryGold.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = { if (!isSpinning) onDismiss() }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
                
                Text(
                    text = "Daily Wheel of Fortune",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
                    color = PrimaryGold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (canSpin) "Spin the wheel for a chance to win free Try-Ons!" else "You already spun today! Come back tomorrow.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // The Wheel Pointer
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = PrimaryGold,
                    modifier = Modifier.size(48.dp).offset(y = 12.dp)
                )
                
                // The Wheel
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .clip(CircleShape)
                        .border(4.dp, PrimaryGold, CircleShape)
                        .graphicsLayer { rotationZ = rotation.value },
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val anglePerSegment = 360f / segments.size
                        // Draw segments so that 0 degrees is at the top (subtract 90)
                        for (i in segments.indices) {
                            drawArc(
                                color = segmentColors[i],
                                startAngle = -90f + (i * anglePerSegment),
                                sweepAngle = anglePerSegment,
                                useCenter = true,
                                style = Fill
                            )
                        }
                    }
                    // Center dot
                    Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(Color.White))
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                if (showReward != null) {
                    Text(
                        text = "\uD83C\uDF89 You won: ${showReward}!",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.Green
                    )
                } else {
                    ShimmeringButton(
                        onClick = {
                            if (canSpin && !isSpinning) {
                                isSpinning = true
                                scope.launch {
                                    val randomSegment = (0 until segments.size).random()
                                    val spins = 5
                                    // Pointer is at -90 degrees (top).
                                    // The segment drawn at top is index 0.
                                    val targetRotation = rotation.value + (spins * 360f) + (randomSegment * (360f / segments.size))
                                    
                                    rotation.animateTo(
                                        targetValue = targetRotation,
                                        animationSpec = tween(durationMillis = 4000, easing = FastOutSlowInEasing)
                                    )
                                    
                                    // Because we rotated the wheel positively, the segment at the top is (segments.size - randomSegment) % segments.size
                                    val winningIndex = (segments.size - randomSegment) % segments.size
                                    val winningReward = segments[winningIndex]
                                    showReward = winningReward
                                    prefs.edit().putLong("last_spin_time", System.currentTimeMillis()).apply()
                                    isSpinning = false

                                    if (viewModel != null && winningReward != "Better Luck") {
                                        viewModel.grantSpinWheelReward(winningReward) { msg ->
                                            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            }
                        },
                        enabled = canSpin && !isSpinning,
                        text = if (canSpin) "SPIN NOW 🎰" else "COME BACK TOMORROW",
                        modifier = Modifier.fillMaxWidth().height(54.dp)
                    )
                }
            }
        }
    }
}
