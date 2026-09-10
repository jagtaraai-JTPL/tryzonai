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
import com.jagtarapvtltd.tryzonai.ui.components.ShimmeringButton
import com.jagtarapvtltd.tryzonai.viewmodel.ClosetViewModel
import com.jagtarapvtltd.tryzonai.utils.UrlUtils
import kotlinx.coroutines.launch
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClosetScreen(
    viewModel: ClosetViewModel,
    authViewModel: com.jagtarapvtltd.tryzonai.viewmodel.AuthViewModel,
    onNavigateToLogin: () -> Unit,
    onNavigateToPremium: () -> Unit = {},
    onNavigateToHistory: () -> Unit = {},
    onTargetBoundsPositioned: ((Int, androidx.compose.ui.geometry.Rect) -> Unit)? = null
) {
    val user by authViewModel.user.collectAsState()
    val isLoggedIn = user != null
    val savedItems by viewModel.closetItems.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scrollState = rememberScrollState()

    var showCompareModal by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        authViewModel.refresh()
        if (isLoggedIn) {
            viewModel.loadCloset()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                onTargetBoundsPositioned?.invoke(5, coords.boundsInRoot()) // Step 5: Closet
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            // ── 1. HEADER TITLE ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "My Account & Closet",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        "Personal dashboard & saved fashion looks",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = PrimaryGold.copy(alpha = 0.15f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Checkroom, contentDescription = "Closet", tint = PrimaryGold, modifier = Modifier.size(20.dp))
                    }
                }
            }

            if (!isLoggedIn) {
                // Logged Out State
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(28.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = PrimaryGold.copy(alpha = 0.15f),
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = PrimaryGold, modifier = Modifier.size(32.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Sign In to Access Your Dashboard", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Sync your credits, subscription status, and saved outfits across all devices.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = onNavigateToLogin,
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold),
                            shape = RoundedCornerShape(100.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("SIGN IN / REGISTER", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                // ── 2. PROFILE SUMMARY CARD ──
                val isPro = user != null && (user?.is_premium == true || user?.subscription_tier?.lowercase()?.contains("pro") == true)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, PrimaryGold.copy(alpha = 0.25f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                modifier = Modifier.size(48.dp),
                                shape = CircleShape,
                                color = PrimaryGold.copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, PrimaryGold)
                            ) {
                                val photoUrl = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.photoUrl?.toString()
                                if (photoUrl != null) {
                                    AsyncImage(
                                        model = photoUrl,
                                        contentDescription = "Profile",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Person, contentDescription = null, tint = PrimaryGold, modifier = Modifier.size(24.dp))
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = user?.name ?: user?.username ?: "Account",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                val planText = if (isPro) "Pro Subscriber 👑" else "Free Plan Account"
                                Text(
                                    text = planText,
                                    color = PrimaryGold,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                        Spacer(modifier = Modifier.height(14.dp))
                        
                        // Usage & Credits Summary Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Available Balance", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                                Text("${user?.credits ?: 0} Credits", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Today's Free Usage", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                                val todayCount = user?.try_ons_today ?: 0
                                val usageText = if (todayCount > 1) {
                                    "1 / 1 Free (+${todayCount - 1} Ad Bonus)"
                                } else {
                                    "$todayCount / 1 Free Used"
                                }
                                Text(usageText, color = PrimaryGold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(14.dp))
                        
                        Button(
                            onClick = onNavigateToPremium,
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold),
                            shape = RoundedCornerShape(100.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("VIEW PLANS & CREDITS", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── 3. SAVED CLOSET LOOKS SECTION ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "My Saved Closet",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            "${savedItems.size} favorite outfits saved",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                    
                    if (savedItems.size >= 2) {
                        OutlinedButton(
                            onClick = { showCompareModal = true },
                            border = BorderStroke(1.dp, PrimaryGold),
                            shape = RoundedCornerShape(100.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.Compare, contentDescription = null, tint = PrimaryGold, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("VERSUS ⚔️", color = PrimaryGold, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (savedItems.isEmpty()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Icon(Icons.Default.FavoriteBorder, contentDescription = null, tint = PrimaryGold.copy(alpha = 0.6f), modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No Saved Outfits Yet", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Tap the ❤️ heart icon on any Try-On result to save your favorite transformations here!", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, textAlign = TextAlign.Center)
                        }
                    }
                } else {
                    // Saved Items Grid (embedded in scroll)
                    val chunkedItems = savedItems.chunked(2)
                    Column(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        chunkedItems.forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                rowItems.forEach { item ->
                                    var isDeleting by remember { mutableStateOf(false) }
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(0.7f)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                                    ) {
                                        AsyncImage(
                                            model = UrlUtils.getCoilModel(item.image_url),
                                            contentDescription = "Saved Closet Look",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        // Delete from Closet
                                        Surface(
                                            shape = CircleShape,
                                            color = Color.Black.copy(alpha = 0.5f),
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(8.dp)
                                                .size(28.dp)
                                                .clickable {
                                                    isDeleting = true
                                                    viewModel.removeFromCloset(item.id)
                                                }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                if (isDeleting) {
                                                    CircularProgressIndicator(color = PrimaryGold, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                                } else {
                                                    Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Color.Red, modifier = Modifier.size(14.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                                if (rowItems.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ── 4. QUICK ACCESS LINKS SECTION ──
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateToHistory() }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.History, contentDescription = null, tint = PrimaryGold, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Full Try-On History", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("View all past AI outfit transformations", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 14.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://play.google.com/store/account/orderhistory"))
                                    context.startActivity(intent)
                                }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = PrimaryGold, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Purchase Receipts & History", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("View past Google Play transactions", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(100.dp)) // Bottom padding for navigation bar
            }
        }

        if (showCompareModal && savedItems.size >= 2) {
            CompareOutfitsModal(
                items = savedItems,
                onDismiss = { showCompareModal = false }
            )
        }
    }
}

@Composable
fun CompareOutfitsModal(
    items: List<com.jagtarapvtltd.tryzonai.network.WardrobeItem>,
    onDismiss: () -> Unit
) {
    if (items.size < 2) return
    var itemA by remember { mutableStateOf(items[0]) }
    var itemB by remember { mutableStateOf(items[1]) }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, PrimaryGold.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth().padding(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "OUTFIT VERSUS ⚔️",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }

                Text(
                    "Compare 2 outfits side-by-side to pick your best look!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Duo Side-by-Side Comparison Box
                Row(
                    modifier = Modifier.fillMaxWidth().height(260.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Item A
                    Box(
                        modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(18.dp)).border(2.dp, PrimaryGold, RoundedCornerShape(18.dp))
                    ) {
                        AsyncImage(
                            model = UrlUtils.getCoilModel(itemA.image_url),
                            contentDescription = "Outfit A",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color.Black.copy(alpha = 0.7f),
                            modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                        ) {
                            Text("STYLE A", color = PrimaryGold, fontWeight = FontWeight.Black, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                    }

                    // VS Divider Badge
                    Box(
                        modifier = Modifier.align(Alignment.CenterVertically).size(32.dp).clip(CircleShape).background(PrimaryGold),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("VS", fontWeight = FontWeight.Black, fontSize = 12.sp, color = Color.Black)
                    }

                    // Item B
                    Box(
                        modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(18.dp)).border(2.dp, Color(0xFF38EF7D), RoundedCornerShape(18.dp))
                    ) {
                        AsyncImage(
                            model = UrlUtils.getCoilModel(itemB.image_url),
                            contentDescription = "Outfit B",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color.Black.copy(alpha = 0.7f),
                            modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                        ) {
                            Text("STYLE B", color = Color(0xFF38EF7D), fontWeight = FontWeight.Black, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Selector Buttons
                Text("Tap an outfit below to change comparison:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items.forEach { item ->
                        val isA = item.id == itemA.id
                        val isB = item.id == itemB.id
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .border(
                                    width = if (isA || isB) 2.dp else 1.dp,
                                    color = when {
                                        isA -> PrimaryGold
                                        isB -> Color(0xFF38EF7D)
                                        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                    },
                                    shape = CircleShape
                                )
                                .clickable {
                                    if (!isA && !isB) {
                                        itemB = item
                                    }
                                }
                        ) {
                            AsyncImage(
                                model = UrlUtils.getCoilModel(item.image_url),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                ShimmeringButton(
                    text = "DONE COMPARING ✨",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                )
            }
        }
    }
}
