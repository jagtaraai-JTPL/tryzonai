package com.jagtarapvtltd.tryzonai.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jagtarapvtltd.tryzonai.ui.theme.PrimaryGold
import com.jagtarapvtltd.tryzonai.viewmodel.InspirationViewModel
import com.jagtarapvtltd.tryzonai.network.FeedItem
import com.jagtarapvtltd.tryzonai.utils.UrlUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InspirationFeedScreen(
    onLookClick: (String) -> Unit,
    onGetSimilar: (FeedItem) -> Unit,
    viewModel: InspirationViewModel
) {
    var selectedCategory by remember { mutableStateOf("All") }
    val categories = listOf("All", "Casual", "Formal", "Streetwear", "Party", "Sporty")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
            // Screen Title
            Text(
                "INSPIRATION FEED ✨",
                modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 4.dp),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                "Discover trending AI-styled looks",
                modifier = Modifier.padding(start = 16.dp, bottom = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Category Chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                items(categories) { category ->
                    val isSelected = selectedCategory == category
                    Surface(
                        onClick = { selectedCategory = category },
                        color = if (isSelected) PrimaryGold else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(100.dp),
                        modifier = Modifier.height(40.dp)
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                category,
                                color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
            
            // Staggered Grid
            val looks by viewModel.looks.collectAsState()

            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalItemSpacing = 12.dp,
                modifier = Modifier.fillMaxSize()
            ) {
                items(looks) { look ->
                    InspirationCard(
                        id = look.id,
                        name = look.name,
                        image = look.image,
                        onClick = { onLookClick(look.id) },
                        onLike = { /* Like */ },
                        onGetSimilar = { onGetSimilar(look) }
                    )
                }
            }
        }
}

@Composable
fun InspirationCard(
    id: String,
    name: String,
    image: String,
    onClick: () -> Unit,
    onLike: () -> Unit,
    onGetSimilar: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box {
            // Mock dynamic height based on id
            val height = if (id.toInt() % 2 == 0) 280.dp else 220.dp
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height)
                    .background(Color(0xFFF1F5F9))
            ) {
                coil3.compose.AsyncImage(
                    model = UrlUtils.getFullUrl(image),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            }
            
            // Like Button
            IconButton(
                onClick = onLike,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .background(Color.White.copy(alpha = 0.9f), CircleShape)
                    .size(36.dp)
            ) {
                Icon(
                    Icons.Default.FavoriteBorder, 
                    contentDescription = "Like", 
                    modifier = Modifier.size(20.dp),
                    tint = Color(0xFF64748B)
                )
            }
            
            // Look Info Overlay
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                        )
                    )
                    .padding(16.dp)
            ) {
                Column {
                    Text(
                        name,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                modifier = Modifier.size(16.dp),
                                shape = CircleShape,
                                color = Color(0xFF6366F1)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Verified, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(10.dp))
                                }
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("TryZon AI", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }

                        IconButton(
                            onClick = onGetSimilar,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "Similar", tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}
