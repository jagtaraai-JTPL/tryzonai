package com.jagtarapvtltd.tryzonai.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import com.jagtarapvtltd.tryzonai.ui.theme.*
import com.jagtarapvtltd.tryzonai.ui.components.ShimmeringButton
import com.jagtarapvtltd.tryzonai.ui.components.bounceClick
import com.jagtarapvtltd.tryzonai.utils.UrlUtils
import com.jagtarapvtltd.tryzonai.utils.findActivity
import com.jagtarapvtltd.tryzonai.viewmodel.AuthViewModel
import com.jagtarapvtltd.tryzonai.viewmodel.TryOnViewModel
import com.jagtarapvtltd.tryzonai.viewmodel.PaymentViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import java.io.File

fun createImageUri(context: Context): Uri {
    val file = File(context.cacheDir, "camera_capture_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

private val inbuiltLocalOutfits = listOf(
    com.jagtarapvtltd.tryzonai.models.Product("4", "Monaco Riviera Yacht Linen", "TryZon AI Exclusives", 2999, 4499, "res:${com.jagtarapvtltd.tryzonai.R.drawable.monaco_var}", "Riviera & Summer", store = "TryZon AI", is_featured = true, badge = "👑 OLD MONEY"),
    com.jagtarapvtltd.tryzonai.models.Product("5", "New York Fifth Ave Executive Suit", "TryZon AI Haute Couture", 5499, 8999, "res:${com.jagtarapvtltd.tryzonai.R.drawable.usa_var}", "Suits & Formal", store = "TryZon AI", is_featured = true, badge = "💼 EXECUTIVE"),
    com.jagtarapvtltd.tryzonai.models.Product("3", "Tokyo Cyberpunk Techwear", "TryZon Cyber Lab", 4999, 7999, "res:${com.jagtarapvtltd.tryzonai.R.drawable.japan_var}", "Streetwear & Cyber", store = "TryZon AI", is_featured = true, badge = "⚡ NEON AI"),
    com.jagtarapvtltd.tryzonai.models.Product("6", "Seoul Gangnam Black Suit", "TryZon AI Exclusives", 4299, 6499, "res:${com.jagtarapvtltd.tryzonai.R.drawable.korea_var}", "Suits & Formal", store = "TryZon AI", is_featured = true, badge = "🫰 K-STYLE"),
    com.jagtarapvtltd.tryzonai.models.Product("9", "Wall Street Italian Bespoke Blazer", "TryZon AI Haute Couture", 5999, 9499, "res:${com.jagtarapvtltd.tryzonai.R.drawable.sample_outfit_usa_suit}", "Suits & Formal", store = "TryZon AI", is_featured = true, badge = "👔 BESPOKE")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TryOnUploadScreen(
    onNavigateToProcessing: () -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToCatalog: (() -> Unit)? = null,
    onNavigateToLogin: () -> Unit = {},
    onNavigateToPremium: (() -> Unit)? = null,
    viewModel: TryOnViewModel,
    catalogViewModel: com.jagtarapvtltd.tryzonai.viewmodel.CatalogViewModel? = null,
    authViewModel: AuthViewModel? = null,
    paymentViewModel: PaymentViewModel = viewModel(),
    onTargetBoundsPositioned: ((Int, androidx.compose.ui.geometry.Rect) -> Unit)? = null
) {
    val userPhoto by viewModel.userPhoto.collectAsState()
    val garmentImage by viewModel.garmentImage.collectAsState()
    val selectedProduct by viewModel.selectedProduct.collectAsState()
    val catalogProducts by (catalogViewModel?.products ?: kotlinx.coroutines.flow.MutableStateFlow(emptyList())).collectAsState()

    // Auto-fetch Google Play Billing details
    LaunchedEffect(Unit) {
        paymentViewModel.fetchProductDetails()
    }

    val productDetailsMap by paymentViewModel.productDetails.collectAsState()

    val pocketDetails = productDetailsMap["credits_pocket"] ?: productDetailsMap["credits-pocket"]
    val rawPocketPrice = pocketDetails?.oneTimePurchaseOfferDetails?.formattedPrice
    val pocketPrice = rawPocketPrice ?: com.jagtarapvtltd.tryzonai.utils.CurrencyUtils.formatPrice(39)

    val proDetails = productDetailsMap["sub_monthly_pro"]
    val rawProPrice = proDetails?.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice
    val proPrice = rawProPrice ?: com.jagtarapvtltd.tryzonai.utils.CurrencyUtils.formatPrice(379)
    val displayProPrice = if (proPrice.contains("/")) proPrice else "$proPrice/mo"

    var displayedOutfits by remember { mutableStateOf(inbuiltLocalOutfits.shuffled().take(4)) }
    var showLoginRequiredDialog by remember { mutableStateOf(false) }
    var showCreditChoiceDialog by remember { mutableStateOf(false) }
    var showDailyLimitDialog by remember { mutableStateOf(false) }
    var showRewardedAdChoiceDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current

    LaunchedEffect(Unit) {
        authViewModel?.refresh()
        val masterOutfits = if (catalogProducts.isNotEmpty()) catalogProducts else inbuiltLocalOutfits
        displayedOutfits = masterOutfits.shuffled().take(4)
        if (garmentImage == null) {
            val freshRandomOutfit = displayedOutfits.random()
            viewModel.setSelectedProduct(freshRandomOutfit)
        }

        val authUser = authViewModel?.user?.value
        val isLoggedIn = authUser != null
        val userPrefs = context.getSharedPreferences("tryzon_user_prefs", Context.MODE_PRIVATE)
        val isFirstTryOnDone = userPrefs.getBoolean("is_lifetime_first_tryon_done", false)

        if (!isLoggedIn && isFirstTryOnDone) {
            showLoginRequiredDialog = true
        }
    }

    LaunchedEffect(selectedProduct, garmentImage) {
        if (selectedProduct == null && garmentImage == null) {
            val fallbackOutfit = (if (displayedOutfits.isNotEmpty()) displayedOutfits else inbuiltLocalOutfits).random()
            viewModel.setSelectedProduct(fallbackOutfit)
        }
    }
    val prefs = context.getSharedPreferences("try_on_prefs", Context.MODE_PRIVATE)
    var isDemoRunning by remember { mutableStateOf(!prefs.getBoolean("has_completed_native_hand_demo", false)) }
    var demoStep by remember { mutableStateOf(0) }

    LaunchedEffect(isDemoRunning) {
        if (isDemoRunning) {
            demoStep = 0
            kotlinx.coroutines.delay(2200)
            demoStep = 1
            kotlinx.coroutines.delay(2200)
            demoStep = 2
            kotlinx.coroutines.delay(2500)
            isDemoRunning = false
            prefs.edit().putBoolean("has_completed_native_hand_demo", true).apply()
        }
    }

    val demoTransition = rememberInfiniteTransition(label = "demo_hand_anim")
    val handOffsetY by demoTransition.animateFloat(
        initialValue = 0f,
        targetValue = -14f,
        animationSpec = infiniteRepeatable(tween(600, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "hand_offset"
    )
    val pulseAlpha by demoTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "hand_pulse"
    )

    var showTutorial by remember { mutableStateOf(false) }
    var uploadTarget by remember { mutableStateOf<String?>(null) }
    var showSafetyPolicySheet by remember { mutableStateOf(false) }
    var tempCameraUriString by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    var tempGarmentCameraUriString by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }

    val userPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { viewModel.setUserPhoto(it) }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraUriString != null) {
            viewModel.setUserPhoto(Uri.parse(tempCameraUriString))
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val uri = createImageUri(context)
            tempCameraUriString = uri.toString()
            cameraLauncher.launch(uri)
        } else {
            android.widget.Toast.makeText(context, "Camera permission denied", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    val garmentCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempGarmentCameraUriString != null) {
            viewModel.setGarmentImage(Uri.parse(tempGarmentCameraUriString))
        }
    }

    val garmentCameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val uri = createImageUri(context)
            tempGarmentCameraUriString = uri.toString()
            garmentCameraLauncher.launch(uri)
        } else {
            android.widget.Toast.makeText(context, "Camera permission denied", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    val garmentPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { viewModel.setGarmentImage(it) }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (uploadTarget != null) {
        ModalBottomSheet(
            onDismissRequest = { uploadTarget = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (uploadTarget == "user") "Upload Person Photo" else "Upload Garment Photo",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    SourceOptionButton(
                        icon = Icons.Default.PhotoLibrary,
                        label = "Gallery",
                        onClick = {
                            uploadTarget?.let { target ->
                                if (target == "garment") {
                                    garmentPhotoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                } else {
                                    userPhotoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                }
                            }
                            uploadTarget = null
                        }
                    )

                    SourceOptionButton(
                        icon = Icons.Default.CameraAlt,
                        label = "Camera",
                        onClick = {
                            uploadTarget?.let { target ->
                                if (target == "garment") {
                                    garmentCameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                                } else {
                                    cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                                }
                            }
                            uploadTarget = null
                        }
                    )
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    val authUser by (authViewModel?.user ?: kotlinx.coroutines.flow.MutableStateFlow(null)).collectAsState()
    val isAuthLoading by (authViewModel?.isLoading ?: kotlinx.coroutines.flow.MutableStateFlow(false)).collectAsState()
    val hasDailyFreeTriesLeft = (authUser?.try_ons_today ?: 0) < 1
    val scrollState = rememberScrollState()

    val activeProduct = selectedProduct ?: inbuiltLocalOutfits.first()
    val garmentModel = when {
        garmentImage != null -> garmentImage
        else -> UrlUtils.getCoilModel(activeProduct.image)
    }
    val isReady = userPhoto != null && garmentModel != null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        PrimaryGold.copy(alpha = 0.06f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(14.dp))

            // ── iOS 26 SPATIAL STUDIO HEADER BADGE WITH DEMO CHIP ──
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                StepIndicator(currentStep = if (userPhoto == null) 1 else 2)
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = PrimaryGold.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, PrimaryGold.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .height(34.dp)
                        .clickable {
                            isDemoRunning = true
                            demoStep = 0
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("💡 Demo", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PrimaryGold)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("👆", fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── AI STUDIO HERO SECTION (STEP 0 TARGET BOUNDS) ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coords ->
                        onTargetBoundsPositioned?.invoke(0, coords.boundsInRoot()) // Step 0: Entire AI Studio Hero
                    }
            ) {
                // ── OVERLAPPING LIQUID GLASS DUO VIEWFINDER ──
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. YOUR PHOTO CARD
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .onGloballyPositioned { coords ->
                                onTargetBoundsPositioned?.invoke(1, coords.boundsInRoot()) // Step 1: Photo Selection
                            }
                    ) {
                        StudioDropCard(
                            headerTitle = "YOUR PHOTO",
                            headerIcon = Icons.Default.Person,
                            imageModel = userPhoto,
                            hintText = "Upload Selfie",
                            onSelect = {
                                if (isDemoRunning) isDemoRunning = false
                                uploadTarget = "user"
                            },
                            onClear = { viewModel.clearUserPhoto() },
                            onSwap = { uploadTarget = "user" },
                            isDemoTarget = isDemoRunning && demoStep == 0,
                            demoPointerLabel = "1. Tap Photo 📸"
                        )
                    }

                    // 2. CHOOSE OUTFIT CARD
                    Column(modifier = Modifier.weight(1f)) {
                        StudioDropCard(
                            headerTitle = "CHOOSE OUTFIT",
                            headerIcon = Icons.Default.Checkroom,
                            imageModel = garmentModel,
                            hintText = "Upload Outfit",
                            onSelect = {
                                if (isDemoRunning) isDemoRunning = false
                                uploadTarget = "garment"
                            },
                            onClear = { viewModel.clearGarmentImage() },
                            onSwap = { uploadTarget = "garment" },
                            isDemoTarget = isDemoRunning && demoStep == 1,
                            demoPointerLabel = "2. Select Outfit 👗",
                            fallbackDrawable = com.jagtarapvtltd.tryzonai.R.drawable.monaco_var
                        )
                    }
                }

                // Central AI Fusion Match Pill Overlay
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.75f),
                    border = BorderStroke(1.dp, PrimaryGold.copy(alpha = 0.5f)),
                    shadowElevation = 8.dp,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = PrimaryGold,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // ── FLOATING GLASS MODEL & OUTFIT SELECTOR STRIPS ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // MODEL SELECTION STRIP
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Models",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "View all ›",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryGold,
                                modifier = Modifier.clickable { uploadTarget = "user" }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        val userModelsHistory by viewModel.userModelsHistory.collectAsState()
                        val userPhotoUriString = userPhoto?.toString()

                        fun isCustomUserPhoto(uriStr: String?): Boolean {
                            if (uriStr.isNullOrEmpty()) return false
                            return !uriStr.startsWith("android.resource://") && 
                                   (uriStr.startsWith("content://") || uriStr.startsWith("file://"))
                        }

                        val recentUserUpload = remember(userPhotoUriString, userModelsHistory) {
                            val currentCustom = if (isCustomUserPhoto(userPhotoUriString)) userPhotoUriString else null
                            val historyCustom = userModelsHistory.firstOrNull { uri ->
                                isCustomUserPhoto(uri.toString())
                            }?.toString()

                            currentCustom ?: historyCustom
                        }

                        val defaultSampleModels = remember {
                            listOf(
                                "res:${com.jagtarapvtltd.tryzonai.R.drawable.sample_model_female}",
                                "res:${com.jagtarapvtltd.tryzonai.R.drawable.sample_model_male}",
                                "res:${com.jagtarapvtltd.tryzonai.R.drawable.sample_model_asian}"
                            )
                        }

                        fun isModelSelected(photoUriStr: String?, sampleUriStr: String): Boolean {
                            if (photoUriStr.isNullOrEmpty()) return false
                            if (photoUriStr == sampleUriStr) return true
                            val id1 = photoUriStr.substringAfterLast("/").removePrefix("res:")
                            val id2 = sampleUriStr.substringAfterLast("/").removePrefix("res:")
                            return id1.isNotEmpty() && id1 == id2
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (recentUserUpload != null) {
                                val isSelected = isModelSelected(userPhotoUriString, recentUserUpload)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                ) {
                                    AsyncImage(
                                        model = recentUserUpload,
                                        contentDescription = "Your Photo",
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape)
                                            .border(
                                                width = if (isSelected) 2.dp else 1.dp,
                                                color = if (isSelected) PrimaryGold else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                                shape = CircleShape
                                            )
                                            .clickable { viewModel.setUserPhoto(Uri.parse(recentUserUpload)) },
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(CircleShape)
                                        .background(PrimaryGold.copy(alpha = 0.12f))
                                        .border(1.dp, PrimaryGold.copy(alpha = 0.4f), CircleShape)
                                        .clickable { uploadTarget = "user" },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AddAPhoto,
                                        contentDescription = "Upload Photo",
                                        tint = PrimaryGold,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }

                            defaultSampleModels.take(3).forEach { sampleUri ->
                                val isSelected = isModelSelected(userPhotoUriString, sampleUri)
                                AsyncImage(
                                    model = UrlUtils.getCoilModel(sampleUri),
                                    contentDescription = "Sample Model",
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(CircleShape)
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) PrimaryGold else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                            shape = CircleShape
                                        )
                                        .clickable { viewModel.setUserPhoto(Uri.parse(sampleUri)) },
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                }

                // OUTFIT SELECTION STRIP
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Outfits",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "View all ›",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryGold,
                                modifier = Modifier.clickable {
                                    val pool = if (catalogProducts.isNotEmpty()) catalogProducts else inbuiltLocalOutfits
                                    displayedOutfits = pool.shuffled().take(4)
                                    if (garmentImage == null) {
                                        viewModel.setSelectedProduct(displayedOutfits.random())
                                    }
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onGloballyPositioned { coords ->
                                    onTargetBoundsPositioned?.invoke(2, coords.boundsInRoot()) // Step 2: Outfit Selector
                                },
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val outfitsList = if (displayedOutfits.isNotEmpty()) displayedOutfits.take(4) else inbuiltLocalOutfits.take(4)

                            outfitsList.forEach { product ->
                                val model = UrlUtils.getCoilModel(product.image)
                                val isSelected = selectedProduct?.id == product.id
                                AsyncImage(
                                    model = model,
                                    contentDescription = product.name,
                                    error = painterResource(com.jagtarapvtltd.tryzonai.R.drawable.monaco_var),
                                    fallback = painterResource(com.jagtarapvtltd.tryzonai.R.drawable.monaco_var),
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(CircleShape)
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) PrimaryGold else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                            shape = CircleShape
                                        )
                                        .clickable { viewModel.setSelectedProduct(product) },
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

            ShimmeringButton(
                onClick = {
                    val authUser = authViewModel?.user?.value
                    val isLoggedIn = authUser != null
                    val prefs = context.getSharedPreferences("tryzon_user_prefs", Context.MODE_PRIVATE)
                    val isFirstTryOnDone = prefs.getBoolean("is_lifetime_first_tryon_done", false)

                    val hasPaidCredits = (authUser?.paid_credits ?: 0) > 0
                    val totalCredits = (authUser?.credits ?: 0)
                    val dailyAdsCount = (authUser?.daily_reward_ad_count ?: 0)
                    val isProSubscriber = authUser?.is_premium == true || listOf("pro", "premium", "vip").contains(authUser?.subscription_tier?.lowercase())
                    val hasDailyFreeTriesLeft = (authUser?.try_ons_today ?: 0) < 1

                    if (!isLoggedIn && isFirstTryOnDone) {
                        showLoginRequiredDialog = true
                    } else if (isProSubscriber) {
                        val activity = context.findActivity()
                        viewModel.submitTryOnWithAd(activity, isPremium = true)
                        onNavigateToProcessing()
                    } else if (hasPaidCredits) {
                        // Smart Choice Popup for credit holders: Use 1 credit (0 ads) vs Watch Video Ad
                        showCreditChoiceDialog = true
                    } else if (!isLoggedIn && !isFirstTryOnDone) {
                        // 1st lifetime guest trial: Prompt Choice Dialog (Watch Ad 📺 vs Go Ad-Free Pro 👑)
                        showRewardedAdChoiceDialog = true
                    } else if (hasDailyFreeTriesLeft || dailyAdsCount < 2) {
                        // Choice Popup for ad-supported try-on: Watch Ad & Generate 📺 vs Go Ad-Free Pro 👑
                        showRewardedAdChoiceDialog = true
                    } else if (totalCredits > 0) {
                        // Ad cap reached (2/2 ads used), but user has credits! Deduct 1 credit (0 ad wait)
                        val activity = context.findActivity()
                        viewModel.submitTryOnWithAd(activity, isPremium = true)
                        onNavigateToProcessing()
                    } else {
                        // Daily free tries + ad limit reached AND 0 credits! PROMPT POPUP DIALOG ON SCREEN!
                        showDailyLimitDialog = true
                    }
                },
                enabled = isReady,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .height(54.dp)
                    .onGloballyPositioned { coords ->
                        onTargetBoundsPositioned?.invoke(3, coords.boundsInRoot()) // Step 3: Generate Button
                    }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "GENERATE VIRTUAL TRY-ON",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.8.sp,
                        color = Color.Black
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                }

                if (isDemoRunning && demoStep == 2) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.35f), shape = RoundedCornerShape(50)),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.offset(y = handOffsetY.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = PrimaryGold,
                                shadowElevation = 6.dp
                            ) {
                                Text(
                                    text = "3. Tap Generate! ⚡",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.Black,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "👆",
                                fontSize = 32.sp,
                                modifier = Modifier.graphicsLayer {
                                    scaleX = 1f + (pulseAlpha * 0.15f)
                                    scaleY = 1f + (pulseAlpha * 0.15f)
                                }
                            )
                        }
                    }
                }
            }



            Spacer(modifier = Modifier.height(12.dp))

            // ── AI SAFETY POLICY FOOTER NOTE ──
            Row(
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 4.dp)
                    .clickable { showSafetyPolicySheet = true },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Shield,
                    contentDescription = null,
                    tint = PrimaryGold.copy(alpha = 0.7f),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "AI Safety Policy: Standard apparel required. Nudity restricted.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ── PRO FITTING TIPS & AI QUALITY CARD ──
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.TipsAndUpdates,
                            contentDescription = null,
                            tint = PrimaryGold,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "PRO FITTING TIPS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryGold,
                            letterSpacing = 0.5.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "For crisp 8K results, use well-lit front-facing photos. TryZon AI automatically handles lighting, texture warping, & pose alignment.",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = PrimaryGold.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, PrimaryGold.copy(alpha = 0.25f))
                        ) {
                            Text("⚡ High-Res Render", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PrimaryGold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                        ) {
                            Text("🔒 100% Private", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                        }
                    }
                }
            }
        }
    }

    // Auto-resume try-on flow when user completes login by showing Choice Popup (Watch Ad vs Pro)
    LaunchedEffect(authUser) {
        val user = authUser
        if (user != null && showLoginRequiredDialog) {
            showLoginRequiredDialog = false
            val isProSubscriber = user.is_premium || listOf("pro", "premium", "vip").contains(user.subscription_tier?.lowercase())
            val hasDailyFreeTriesLeft = user.try_ons_today < 1
            val dailyAdsCount = user.daily_reward_ad_count
            val hasPaidCredits = user.paid_credits > 0
            val totalCredits = user.credits

            if (isProSubscriber) {
                // Pro Subscribers: 100% Ad-Free instant fast pass
                val activity = context.findActivity()
                viewModel.submitTryOnWithAd(activity, isPremium = true)
                onNavigateToProcessing()
            } else if (hasPaidCredits) {
                // Prompt Smart Credit Choice Popup for credit holders
                showCreditChoiceDialog = true
            } else if (hasDailyFreeTriesLeft || dailyAdsCount < 2) {
                // Prompt explicit user choice popup ("Watch Ad & Generate 📺" vs "Go Ad-Free Pro")
                showRewardedAdChoiceDialog = true
            } else if (totalCredits > 0) {
                // Ad cap reached (2/2 ads used), but user has credits! Deduct 1 credit (0 ad wait)
                val activity = context.findActivity()
                viewModel.submitTryOnWithAd(activity, isPremium = true)
                onNavigateToProcessing()
            } else {
                // Google Login succeeded! Show clean limit reached popup dialog.
                showDailyLimitDialog = true
            }
        }
    }

    // ── MANDATORY 2ND TRY-ON LOGIN REQUIRED POPUP DIALOG ──
    if (showLoginRequiredDialog) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showLoginRequiredDialog = false }
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, PrimaryGold.copy(alpha = 0.5f)),
                tonalElevation = 12.dp,
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .wrapContentHeight()
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .background(PrimaryGold.copy(alpha = 0.15f), CircleShape)
                            .border(1.5.dp, PrimaryGold, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Login Required",
                            tint = PrimaryGold,
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "🔒 Login Required to Continue",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Your 1st free try-on is complete! Log in now to unlock unlimited AI try-ons, daily free credits, and save your magic looks.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    ShimmeringButton(
                        onClick = {
                            authViewModel?.triggerGoogleSignIn(context)
                        },
                        enabled = !isAuthLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (isAuthLoading) {
                                CircularProgressIndicator(
                                    color = Color.Black,
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Opening Google...",
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            } else {
                                Text(
                                    "🚀 Continue with Google",
                                    color = Color.Black,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 14.5.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Action 2: Email / Phone Login Screen Navigation
                    OutlinedButton(
                        onClick = {
                            showLoginRequiredDialog = false
                            onNavigateToLogin()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, PrimaryGold.copy(alpha = 0.5f))
                    ) {
                        Text(
                            "✉️ Log In with Email / Phone",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(
                        onClick = { showLoginRequiredDialog = false }
                    ) {
                        Text(
                            "Maybe Later",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    // ── SMART CREDIT CHOICE POPUP DIALOG FOR CREDIT PACK BUYERS ──
    if (showCreditChoiceDialog) {
        val userCredits = authUser?.paid_credits ?: 0
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showCreditChoiceDialog = false }
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, PrimaryGold.copy(alpha = 0.5f)),
                tonalElevation = 12.dp,
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .wrapContentHeight()
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .background(PrimaryGold.copy(alpha = 0.15f), CircleShape)
                            .border(1.5.dp, PrimaryGold, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "⚡",
                            fontSize = 28.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "⚡ Choose Generation Mode",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "You have $userCredits Paid Credits in your account balance.",
                        fontSize = 13.sp,
                        color = PrimaryGold,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Option 1: Use 1 Paid Credit (Instant Ad-Free)
                    ShimmeringButton(
                        onClick = {
                            showCreditChoiceDialog = false
                            val activity = context.findActivity()
                            viewModel.submitTryOnWithAd(activity, isPremium = true) // Skip ad & deduct credit
                            onNavigateToProcessing()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text(
                            "⚡ Use 1 Credit (Instant • 0 Ads)",
                            color = Color.Black,
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp
                        )
                        Text(
                            "Deducts 1 credit • No video ad wait",
                            color = Color.Black.copy(alpha = 0.75f),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Option 2: Watch Video Ad (Save Credit)
                    OutlinedButton(
                        onClick = {
                            showCreditChoiceDialog = false
                            val activity = context.findActivity()
                            viewModel.submitTryOnWithAd(activity, isPremium = false) // Play Rewarded Ad, save credit!
                            onNavigateToProcessing()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.5.dp, PrimaryGold.copy(alpha = 0.6f))
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "📺 Watch Video Ad (Save Credit)",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                "Keep your paid credits • Watch 30s video ad",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                fontSize = 10.5.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    TextButton(
                        onClick = { showCreditChoiceDialog = false }
                    ) {
                        Text(
                            "Cancel",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }

    // ── DAILY TRY-ON LIMIT REACHED POPUP DIALOG ──
    if (showDailyLimitDialog) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showDailyLimitDialog = false }
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, PrimaryGold.copy(alpha = 0.5f)),
                tonalElevation = 12.dp,
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .wrapContentHeight()
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .background(PrimaryGold.copy(alpha = 0.15f), CircleShape)
                            .border(1.5.dp, PrimaryGold, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "👑",
                            fontSize = 28.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Daily Limit Reached",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "You've completed all 3 free try-ons for today (1 Daily Free + 2 Video Ad Bonuses). Top up credits or upgrade to Pro for unlimited try-ons!",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    ShimmeringButton(
                        text = "⚡ GET CREDITS / UPGRADE TO PRO",
                        onClick = {
                            showDailyLimitDialog = false
                            onNavigateToPremium?.invoke()
                        },
                        fontSize = 12.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    TextButton(
                        onClick = { showDailyLimitDialog = false }
                    ) {
                        Text(
                            "Come Back Tomorrow",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }

    // ── EXPLICIT REWARDED AD CHOICE OPT-IN DIALOG (ADMOB USER CHOICE POLICY COMPLIANCE & PSYCHOLOGICAL PRO PITCH) ──
    if (showRewardedAdChoiceDialog) {
        AlertDialog(
            onDismissRequest = { showRewardedAdChoiceDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp),
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = PrimaryGold.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, PrimaryGold.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "⚡ CHOOSE GENERATION MODE",
                            color = PrimaryGold,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Generate Virtual Try-On",
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Watch a short video ad to start free generation, or upgrade to Pro for instant fast pass.",
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Option 1: Free Ad-Supported Try-On
                    ShimmeringButton(
                        onClick = {
                            showRewardedAdChoiceDialog = false
                            val activity = context.findActivity()
                            viewModel.submitTryOnWithAd(activity, isPremium = false)
                            onNavigateToProcessing()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.PlayCircle, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "WATCH AD & GENERATE 📺",
                                color = Color.Black,
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp
                            )
                        }
                    }

                    // Option 2: Direct 1-Tap Pocket Pack Quick Buy (15 Credits) -> Launches Google Play Payment Sheet
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = PrimaryGold.copy(alpha = 0.12f),
                        border = BorderStroke(1.5.dp, PrimaryGold.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clickable {
                                showRewardedAdChoiceDialog = false
                                val activity = context.findActivity()
                                if (activity != null) {
                                    paymentViewModel.startPayment(activity, "credits_pocket", isSubscription = false)
                                } else {
                                    onNavigateToPremium?.invoke()
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("⚡", fontSize = 18.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                val isFirstTimeBuyer = (authUser?.paid_credits ?: 0) == 0
                                Column {
                                    Text(
                                        if (isFirstTimeBuyer) "🎁 25 Fast Passes (15 + 10 BONUS) — $pocketPrice" else "15 Ad-Free Fast Passes — $pocketPrice",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        if (isFirstTimeBuyer) "🔥 1st Buy Special · 0 Ads · 3s Speed" else "⚡ 0 Video Ads · 3s Turbo Speed",
                                        fontSize = 10.sp,
                                        color = PrimaryGold,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = PrimaryGold,
                                modifier = Modifier.height(26.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 10.dp)
                                ) {
                                    Text(
                                        "BUY $pocketPrice",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.Black
                                    )
                                }
                            }
                        }
                    }

                    // Option 3: VIP Pro Unlimited Subscription -> Launches Google Play Payment Sheet
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(1.5.dp, PrimaryGold.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clickable {
                                showRewardedAdChoiceDialog = false
                                val activity = context.findActivity()
                                if (activity != null) {
                                    paymentViewModel.startPayment(activity, "sub_monthly_pro", isSubscription = true)
                                } else {
                                    onNavigateToPremium?.invoke()
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("👑", fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        "VIP Pro — $displayProPrice",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        "Unlimited Try-Ons · 100% Ad-Free",
                                        fontSize = 10.sp,
                                        color = PrimaryGold,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = PrimaryGold,
                                modifier = Modifier.height(26.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 10.dp)
                                ) {
                                    Text(
                                        "PRO ${proPrice.replace("/mo", "").replace("/month", "")}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.Black
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    TextButton(
                        onClick = { showRewardedAdChoiceDialog = false }
                    ) {
                        Text("Maybe Later", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                }
            }
        )
    }

    // ── AI SAFETY & FITTING GUIDELINES BOTTOM SHEET ──
    if (showSafetyPolicySheet) {
        ModalBottomSheet(
            onDismissRequest = { showSafetyPolicySheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = PrimaryGold,
                        modifier = Modifier.size(32.dp)
                    )
                    Column {
                        Text(
                            text = "AI Safety & Fitting Guidelines 🛡️",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Follow these photo rules for 100% photorealistic results",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 1. Recommended Photos
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = PrimaryGold.copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, PrimaryGold.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = PrimaryGold, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("BEST PHOTOS TO UPLOAD", fontWeight = FontWeight.Black, fontSize = 13.sp, color = PrimaryGold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("• Wear fitted clothing (e.g., T-shirt, jeans, top, or dress).", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, lineHeight = 18.sp)
                        Text("• Ensure good indoor lighting or bright natural daylight.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, lineHeight = 18.sp)
                        Text("• Stand straight, facing the camera with your body clearly visible.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, lineHeight = 18.sp)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 2. Restricted & Unsupported
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Block, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("RESTRICTED & UNSUPPORTED CONTENT", fontWeight = FontWeight.Black, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("• Nudity, semi-nudity, or underwear/lingerie.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, lineHeight = 18.sp)
                        Text("• Swimwear, bikinis, or trunks.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, lineHeight = 18.sp)
                        Text("• Extremely dark, blurry, or cropped torso images.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, lineHeight = 18.sp)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 3. Why This Policy Exists
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("WHY THIS POLICY EXISTS", fontWeight = FontWeight.Black, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("To comply with Google Play Store Safety Standards & ethical AI usage guidelines, our neural network automatically rejects non-compliant imagery.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 17.sp)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { showSafetyPolicySheet = false },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold)
                ) {
                    Text("UNDERSTOOD & GOT IT 👍", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun StudioDropCard(
    headerTitle: String,
    headerIcon: androidx.compose.ui.graphics.vector.ImageVector,
    imageModel: Any?,
    hintText: String,
    onSelect: () -> Unit,
    onClear: () -> Unit,
    onSwap: () -> Unit,
    isDemoTarget: Boolean = false,
    demoPointerLabel: String? = null,
    fallbackDrawable: Int = com.jagtarapvtltd.tryzonai.R.drawable.sample_model_female
) {
    val infiniteTransition = rememberInfiniteTransition(label = "studio_hand")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "alpha"
    )
    val handOffsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -14f,
        animationSpec = infiniteRepeatable(tween(850, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "hand"
    )

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        border = BorderStroke(
            if (isDemoTarget) 2.5.dp else 1.dp,
            if (isDemoTarget) PrimaryGold.copy(alpha = pulseAlpha) else MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)
        ),
        shadowElevation = if (isDemoTarget) 12.dp else 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(290.dp)
                .clip(RoundedCornerShape(24.dp))
                .clickable { onSelect() },
            contentAlignment = Alignment.Center
        ) {
            if (imageModel != null) {
                val coilModel = when (imageModel) {
                    is Int -> imageModel
                    is Uri -> imageModel
                    is String -> UrlUtils.getCoilModel(imageModel)
                    else -> UrlUtils.getCoilModel(imageModel.toString())
                }
                AsyncImage(
                    model = coilModel,
                    contentDescription = headerTitle,
                    error = painterResource(fallbackDrawable),
                    fallback = painterResource(fallbackDrawable),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // Top Floating Translucent Tag
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.Black.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(headerIcon, contentDescription = null, tint = PrimaryGold, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(headerTitle, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                // Top Right Remove Button
                IconButton(
                    onClick = onClear,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .background(PrimaryGold.copy(alpha = 0.12f), CircleShape)
                            .border(1.dp, PrimaryGold.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = headerIcon,
                            contentDescription = null,
                            tint = PrimaryGold,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = hintText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tap to upload",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryGold
                    )
                }
            }

            if (isDemoTarget) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.offset(y = handOffsetY.dp)
                    ) {
                        demoPointerLabel?.let { label ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = PrimaryGold,
                                border = BorderStroke(1.dp, Color.White),
                                shadowElevation = 8.dp
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.Black,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        Text(
                            text = "👆",
                            fontSize = 44.sp,
                            modifier = Modifier.graphicsLayer {
                                scaleX = 1f + (pulseAlpha * 0.18f)
                                scaleY = 1f + (pulseAlpha * 0.18f)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StepIndicator(currentStep: Int) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)),
        modifier = Modifier.height(34.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "1. MODEL",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (currentStep >= 1) PrimaryGold else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Text(
                "•",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )
            Text(
                "2. OUTFIT",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (currentStep >= 2) PrimaryGold else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Text(
                "•",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )
            Text(
                "3. RESULT",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (currentStep >= 3) PrimaryGold else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun SourceOptionButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(MaterialTheme.colorScheme.surface, CircleShape)
                .border(1.dp, PrimaryGold.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = PrimaryGold,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
