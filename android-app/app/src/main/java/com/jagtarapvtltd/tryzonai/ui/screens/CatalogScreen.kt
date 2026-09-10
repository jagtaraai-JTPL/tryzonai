package com.jagtarapvtltd.tryzonai.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jagtarapvtltd.tryzonai.ui.theme.*
import com.jagtarapvtltd.tryzonai.ui.components.shimmerEffect
import com.jagtarapvtltd.tryzonai.viewmodel.CatalogViewModel
import com.jagtarapvtltd.tryzonai.models.Product
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import coil3.compose.AsyncImage
import com.jagtarapvtltd.tryzonai.utils.UrlUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterChipDropdown(
    label: String,
    selected: String,
    options: List<String>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    Box {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(100.dp),
            color = if (selected == "All") MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f) else PrimaryGold.copy(alpha = 0.15f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    if (selected == "All") label else selected,
                    color = if (selected == "All") MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f) else PrimaryGold,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = if (selected == "All") MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f) else PrimaryGold, modifier = Modifier.size(16.dp))
            }
        }
        
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant).border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, color = if (selected == option) PrimaryGold else MaterialTheme.colorScheme.onBackground) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(
    onProductClick: (String) -> Unit,
    onTryOnClick: (Product) -> Unit,
    viewModel: CatalogViewModel,
    onTargetBoundsPositioned: ((Int, androidx.compose.ui.geometry.Rect) -> Unit)? = null
) {
    val products by viewModel.products.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isMoreLoading by viewModel.isMoreLoading.collectAsState()
    
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All AI Outfits") }
    var selectedSort by remember { mutableStateOf("Popularity") }
    var selectedGender by remember { mutableStateOf("All") }

    LaunchedEffect(Unit) {
        viewModel.randomizeOutfits()
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coords ->
                    onTargetBoundsPositioned?.invoke(2, coords.boundsInRoot()) // Step 2: Catalog
                }
        ) {
            // Single Ultra-Compact Filter & Category Bar (Saves ~70dp Vertical Height)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Compact Dropdown Chips
                FilterChipDropdown(label = "Sort", selected = selectedSort, options = listOf("Popularity", "Newest AI")) { 
                    selectedSort = it
                    viewModel.setSort(it)
                }
                FilterChipDropdown(label = "Gender", selected = selectedGender, options = listOf("All", "Men", "Women")) { 
                    selectedGender = it
                    viewModel.setGender(it)
                }

                // Vertical Separator Line
                Box(
                    modifier = Modifier
                        .height(20.dp)
                        .width(1.dp)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                )

                // Sleek Category Pill Chips
                categories.forEach { category ->
                    val isSelected = selectedCategory == category
                    Surface(
                        onClick = { 
                            selectedCategory = category
                            viewModel.setCategory(category)
                        },
                        shape = RoundedCornerShape(100.dp),
                        color = if (isSelected) PrimaryGold else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, if (isSelected) PrimaryGold else MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
                    ) {
                        Text(
                            text = category,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                if (isLoading) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 110.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(6) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(0.68f)
                                    .clip(RoundedCornerShape(20.dp))
                                    .shimmerEffect()
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 110.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Featured AI Spotlight Hero Banner
                        if (selectedCategory == "All AI Outfits" && products.isNotEmpty()) {
                            item(span = { GridItemSpan(2) }) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .clickable { onTryOnClick(products.first()) },
                                    shape = RoundedCornerShape(24.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    border = BorderStroke(1.dp, PrimaryGold.copy(alpha = 0.4f))
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        AsyncImage(
                                            model = UrlUtils.getCoilModel(products.first().image),
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    Brush.verticalGradient(
                                                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                                                    )
                                                )
                                        )
                                        Column(
                                            modifier = Modifier
                                                .align(Alignment.BottomStart)
                                                .padding(16.dp)
                                        ) {
                                            Surface(
                                                color = PrimaryGold,
                                                shape = RoundedCornerShape(100.dp)
                                            ) {
                                                Text(
                                                    "🔥 #1 VIRAL AI TRY-ON OUTFIT",
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                    color = Color.Black,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                products.first().name,
                                                color = Color.White,
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                "Photorealistic AI Try-On ready in 2s",
                                                color = Color.White.copy(alpha = 0.8f),
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        items(products) { product ->
                            ProductReplicaCard(
                                product = product,
                                onClick = onProductClick,
                                onTryOnClick = onTryOnClick,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        
                        // Load More Button
                        if (viewModel.hasMore()) {
                            item(span = { GridItemSpan(2) }) {
                                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                                    Button(
                                        onClick = { viewModel.loadCatalog(reset = false) },
                                        enabled = !isMoreLoading,
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                                        border = BorderStroke(1.dp, PrimaryGold),
                                        shape = RoundedCornerShape(100.dp)
                                    ) {
                                        if (isMoreLoading) {
                                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = PrimaryGold, strokeWidth = 2.dp)
                                        } else {
                                            Text("LOAD MORE AI STYLES", color = PrimaryGold, style = MaterialTheme.typography.labelSmall)
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
