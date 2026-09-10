package com.jagtarapvtltd.tryzonai.ui.screens

import android.Manifest
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.jagtarapvtltd.tryzonai.utils.findActivity
import com.jagtarapvtltd.tryzonai.viewmodel.TryOnViewModel
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TryOnCaptureScreen(
    onNavigateToProcessing: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: TryOnViewModel
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    
    val previewView = remember { PreviewView(context) }
    val imageCapture = remember { ImageCapture.Builder().build() }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    
    // Proper cleanup of executor
    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    var showTips by remember { mutableStateOf(true) }

    LaunchedEffect(cameraPermissionState.status) {
        if (cameraPermissionState.status.isGranted) {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_FRONT_CAMERA,
                        preview,
                        imageCapture
                    )
                } catch (e: Exception) {
                    // Handle error
                }
            }, ContextCompat.getMainExecutor(context))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Take Your Photo") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.5f),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (cameraPermissionState.status.isGranted) {
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize()
                )
                
                // Pose Guide Overlay
                PoseGuideOverlay()
                
                // Step Indicator
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = paddingValues.calculateTopPadding() + 16.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    StepIndicator(currentStep = 2)
                }
                
                // Bottom Controls
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (showTips) {
                        Surface(
                            modifier = Modifier
                                .padding(horizontal = 24.dp)
                                .padding(bottom = 24.dp),
                            color = Color.Black.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Lightbulb, contentDescription = null, tint = Color.Yellow)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    "Stand 5-7 feet away and keep your body straight within the guide.",
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontSize = 12.sp
                                )
                                IconButton(onClick = { showTips = false }) {
                                    Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Switch Camera
                        IconButton(onClick = { /* Implement switch camera */ }) {
                            Icon(Icons.Default.FlipCameraAndroid, contentDescription = "Switch Camera", tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(32.dp))
                        }
                        
                        // Capture Button
                        Surface(
                            modifier = Modifier
                                .size(80.dp)
                                .border(4.dp, Color.White, CircleShape)
                                .padding(6.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.onBackground,
                            onClick = {
                                if (!com.jagtarapvtltd.tryzonai.utils.NetworkMonitor.isInternetAvailable(context)) {
                                    android.widget.Toast.makeText(context, "Internet connection required for AI Try-On", android.widget.Toast.LENGTH_LONG).show()
                                } else {
                                    takePhoto(
                                        imageCapture,
                                        context,
                                        cameraExecutor,
                                        onPhotoCaptured = { uri ->
                                            viewModel.setUserPhoto(uri)
                                            val activity = context.findActivity()
                                            viewModel.submitTryOnWithAd(activity, false)
                                            onNavigateToProcessing()
                                        }
                                    )
                                }
                            }
                        ) {
                            Box(modifier = Modifier.fillMaxSize())
                        }
                        
                        // Gallery Upload Alternative
                        IconButton(onClick = { /* Implement gallery upload */ }) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = "Upload from Gallery", tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(32.dp))
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "TryZon AI requires Camera access so you can take a full-body photo for the Virtual Try-On.", 
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { cameraPermissionState.launchPermissionRequest() }) {
                        Text("Grant Permission")
                    }
                }
            }
        }
    }
}

@Composable
fun PoseGuideOverlay() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Outline of a person (Simplified as a dashed box for now)
        Box(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .fillMaxHeight(0.8f)
                .border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(20.dp)
                )
        )
        
        Text(
            "Align your body here",
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

private fun takePhoto(
    imageCapture: ImageCapture,
    context: android.content.Context,
    executor: ExecutorService,
    onPhotoCaptured: (Uri) -> Unit
) {
    val outputDirectory = context.externalCacheDir ?: context.cacheDir
    val photoFile = File(
        outputDirectory,
        "tryon_${System.currentTimeMillis()}.jpg"
    )

    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

    imageCapture.takePicture(
        outputOptions,
        executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                val savedUri = Uri.fromFile(photoFile)
                // Ensure callback is on main thread as it triggers UI/Navigation
                Handler(Looper.getMainLooper()).post {
                    onPhotoCaptured(savedUri)
                }
            }

            override fun onError(exception: ImageCaptureException) {
                exception.printStackTrace()
                Handler(Looper.getMainLooper()).post {
                    android.widget.Toast.makeText(context, "Failed to capture photo: ${exception.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    )
}
