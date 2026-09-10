package com.jagtarapvtltd.tryzonai.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.toColorInt
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.jagtarapvtltd.tryzonai.ui.theme.*
import com.jagtarapvtltd.tryzonai.models.Product
import com.jagtarapvtltd.tryzonai.utils.UrlUtils
import com.jagtarapvtltd.tryzonai.utils.CurrencyUtils
import com.jagtarapvtltd.tryzonai.ui.components.bounceClick

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailScreen(
    productId: String,
    onNavigateBack: () -> Unit,
    onTryOn: (Product) -> Unit,
    onBuyNow: () -> Unit
) {
    // In a real app, you would fetch product details using the productId
    // For now, we'll use mock data
    val product = remember {
        Product(
            id = productId,
            name = "Premium Cotton Denim Jacket",
            brand = "Levi's",
            price = 2499,
            originalPrice = 4999,
            image = "https://www.tryzonai.com/products/denim-1.jpg",
            category = "Jackets",
            discount = 50,
            rating = 4.5f,
            colors = listOf("#0000FF", "#000000", "#FFFFFF"),
            sizes = listOf("S", "M", "L", "XL")
        )
    }

    var selectedColor by remember { mutableStateOf(product.colors.first()) }
    var selectedSize by remember { mutableStateOf(product.sizes[1]) }

    Scaffold(
        bottomBar = {
            BottomActionBar(
                onTryOn = { onTryOn(product) },
                onBuyNow = onBuyNow
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // Product Image Gallery
            ProductImageGallery(image = product.image, onBack = onNavigateBack)
            
            Column(modifier = Modifier.padding(16.dp)) {
                // Brand & Name
                Text(
                    product.brand,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    product.name,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    "Brand: ${product.brand}",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Price
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        CurrencyUtils.formatPrice(product.price),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10B981)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        CurrencyUtils.formatPrice(product.originalPrice),
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        style = androidx.compose.ui.text.TextStyle(
                            textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough
                        )
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Surface(
                        color = Color(0xFFEF4444),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            "${product.discount}% OFF",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Color Selection
                Text("Select Color", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    product.colors.forEach { colorHex ->
                        ColorOption(
                            color = Color(colorHex.toColorInt()),
                            isSelected = selectedColor == colorHex,
                            onClick = { selectedColor = colorHex }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Size Selection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Select Size", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    TextButton(onClick = { /* Size Guide */ }) {
                        Text("Size Guide")
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    product.sizes.forEach { size ->
                        SizeOption(
                            size = size,
                            isSelected = selectedSize == size,
                            onClick = { selectedSize = size }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Description
                Text("Description", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "This premium cotton denim jacket features a classic design with a modern fit. Made from high-quality sustainable cotton, it's perfect for layering and built to last.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    lineHeight = 22.sp
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Delivery Info
                DeliveryInfoCard()
                
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun ProductImageGallery(image: String, onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(400.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        // Main Image
        AsyncImage(
            model = UrlUtils.getFullUrl(image),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        
        // Back Button
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .padding(16.dp)
                .background(Color.White.copy(alpha = 0.7f), CircleShape)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        
        // Favorite Button
        IconButton(
            onClick = { /* Favorite */ },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .background(Color.White.copy(alpha = 0.7f), CircleShape)
        ) {
            Icon(Icons.Default.FavoriteBorder, contentDescription = "Favorite")
        }
    }
}

@Composable
fun ColorOption(color: Color, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .size(40.dp)
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = CircleShape
            )
            .padding(4.dp)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = color
    ) {}
}

@Composable
fun SizeOption(size: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .size(48.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        border = if (!isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)) else null
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                size,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun DeliveryInfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            DeliveryInfoRow(Icons.Default.LocalShipping, "Free delivery on orders above ₹999")
            Spacer(modifier = Modifier.height(12.dp))
            DeliveryInfoRow(Icons.AutoMirrored.Filled.AssignmentReturn, "15 days easy return policy")
            Spacer(modifier = Modifier.height(12.dp))
            DeliveryInfoRow(Icons.Default.Verified, "100% Authentic products guaranteed")
        }
    }
}

@Composable
fun DeliveryInfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(12.dp))
        Text(text, fontSize = 12.sp)
    }
}

@Composable
fun BottomActionBar(onTryOn: () -> Unit, onBuyNow: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 12.dp
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .windowInsetsPadding(WindowInsets.navigationBars),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onTryOn,
                modifier = Modifier
                    .weight(1.3f)
                    .height(52.dp)
                    .bounceClick(0.96f),
                shape = RoundedCornerShape(100.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.Black)
                Spacer(modifier = Modifier.width(6.dp))
                Text("TRY THIS ON 👕", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 13.sp)
            }
            
            Button(
                onClick = onBuyNow,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .bounceClick(0.96f),
                shape = RoundedCornerShape(100.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Text("BUY NOW", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}
