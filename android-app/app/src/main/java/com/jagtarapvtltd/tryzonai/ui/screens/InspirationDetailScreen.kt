package com.jagtarapvtltd.tryzonai.ui.screens

import androidx.compose.foundation.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InspirationDetailScreen(
    lookId: String,
    onNavigateBack: () -> Unit,
    onProductClick: (String) -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
            // Full Look Image
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(500.dp)
                    .background(Color.LightGray)
            ) {
                // AsyncImage
            }
            
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    "Summer Weekend Chic",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("S", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Styled by TryZon AI", fontWeight = FontWeight.Medium)
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    "Get This Look",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Products in this look
                LookProductItem(
                    name = "Light Blue Denim Jacket",
                    price = "Check Store Price",
                    onClick = { onProductClick("1") }
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                LookProductItem(
                    name = "White Cotton T-Shirt",
                    price = "Check Store Price",
                    onClick = { onProductClick("2") }
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                LookProductItem(
                    name = "Beige Chino Pants",
                    price = "Check Store Price",
                    onClick = { onProductClick("3") }
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Button(
                    onClick = { /* Shop all */ },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Shop All Items")
                }
                
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
}

@Composable
fun LookProductItem(name: String, price: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.LightGray)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(price, color = Color(0xFF10B981), fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}
