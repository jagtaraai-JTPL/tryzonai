package com.jagtarapvtltd.tryzonai.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.jagtarapvtltd.tryzonai.ui.theme.*
import com.jagtarapvtltd.tryzonai.ui.components.shimmerEffect
import com.jagtarapvtltd.tryzonai.viewmodel.WardrobeViewModel
import com.jagtarapvtltd.tryzonai.utils.UrlUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WardrobeScreen(
    viewModel: WardrobeViewModel,
    authViewModel: com.jagtarapvtltd.tryzonai.viewmodel.AuthViewModel,
    onNavigateToLogin: () -> Unit,
    onNavigateToPremium: () -> Unit,
    isDarkTheme: Boolean,
    onThemeToggle: () -> Unit,
    onItemClick: (com.jagtarapvtltd.tryzonai.network.TryOnHistoryItem) -> Unit,
    onTryOnAgain: (com.jagtarapvtltd.tryzonai.network.TryOnHistoryItem) -> Unit,
    onSaveToCloset: (String) -> Unit
) {
    val user by authViewModel.user.collectAsState()
    val isLoggedIn = user != null
    val savedOutfits by viewModel.savedTryOns.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Section Title
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Try-On History",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        "Your recent AI outfit transformations",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            var showWarningBanner by remember { mutableStateOf(true) }
            
            if (showWarningBanner) {
                // Local Storage Warning Banner
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 6.dp)
                        .clickable { showWarningBanner = false },
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.SaveAlt, contentDescription = null, tint = PrimaryGold, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Local History", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("History is saved locally on your device. Save favorites to Closet to keep permanently.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                    }
                }
            }

            if (isLoading && savedOutfits.isEmpty()) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(6) {
                        Box(
                            modifier = Modifier
                                .aspectRatio(3f / 4f)
                                .clip(RoundedCornerShape(18.dp))
                                .shimmerEffect()
                        )
                    }
                }
            } else if (savedOutfits.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        Surface(
                            shape = CircleShape,
                            color = PrimaryGold.copy(alpha = 0.12f),
                            modifier = Modifier.size(72.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.History, contentDescription = null, tint = PrimaryGold, modifier = Modifier.size(36.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("No Try-On History Yet", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Start trying on outfits to see your past transformations here!", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, textAlign = TextAlign.Center)
                        if (!isLoggedIn) {
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = onNavigateToLogin,
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold),
                                shape = RoundedCornerShape(100.dp)
                            ) {
                                Text("SIGN IN TO SYNC", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(savedOutfits) { item ->
                        Box(
                            modifier = Modifier
                                .aspectRatio(3f / 4f)
                                .clip(RoundedCornerShape(18.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), RoundedCornerShape(18.dp))
                                .clickable { onItemClick(item) }
                        ) {
                            // Full Hero Image (Unobscured center)
                            AsyncImage(
                                model = UrlUtils.getFullUrl(item.result_url),
                                contentDescription = "Try-On Result",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            
                            // Top Bar Actions
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.TopCenter)
                                    .background(
                                        androidx.compose.ui.graphics.Brush.verticalGradient(
                                            listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)
                                        )
                                    )
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Delete Button
                                Surface(
                                    shape = CircleShape,
                                    color = Color.Black.copy(alpha = 0.45f),
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clickable { viewModel.deleteItem(item.id) }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.85f), modifier = Modifier.size(14.dp))
                                    }
                                }

                                // Favorite / Save to Closet Button
                                Surface(
                                    shape = CircleShape,
                                    color = Color.Black.copy(alpha = 0.45f),
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clickable { 
                                            onSaveToCloset(item.result_url)
                                            android.widget.Toast.makeText(context, "Saved to Closet ❤️", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.FavoriteBorder, contentDescription = "Save to Closet", tint = PrimaryGold, modifier = Modifier.size(15.dp))
                                    }
                                }
                            }

                            // Bottom Bar Details & Quick Actions
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter)
                                    .background(
                                        androidx.compose.ui.graphics.Brush.verticalGradient(
                                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                                        )
                                    )
                                    .padding(10.dp)
                            ) {
                                Text(
                                    text = item.timestamp.take(10),
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Try Again Button
                                    Surface(
                                        shape = RoundedCornerShape(100.dp),
                                        color = PrimaryGold,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { onTryOnAgain(item) }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(vertical = 5.dp, horizontal = 8.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.Black, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Try Again", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    // Share Button
                                    Surface(
                                        shape = CircleShape,
                                        color = Color.White.copy(alpha = 0.2f),
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clickable {
                                                coroutineScope.launch {
                                                    com.jagtarapvtltd.tryzonai.utils.ImageExportHelper.shareImage(context, item.result_url)
                                                }
                                            }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White, modifier = Modifier.size(12.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ClosetStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = PrimaryGold, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}
