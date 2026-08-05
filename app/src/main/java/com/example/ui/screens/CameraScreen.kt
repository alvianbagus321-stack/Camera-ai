package com.example.ui.screens

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.GridOff
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Timer3
import androidx.compose.material.icons.filled.Timer10
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.viewmodel.CameraViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Composable
fun CameraScreen(
    viewModel: CameraViewModel,
    onNavigateToPreview: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()
    
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var videoCapture by remember { mutableStateOf<androidx.camera.video.VideoCapture<androidx.camera.video.Recorder>?>(null) }
    var recording by remember { mutableStateOf<androidx.camera.video.Recording?>(null) }
    val previewView = remember { PreviewView(context) }
    
    var zoomRatio by remember { mutableFloatStateOf(1f) }
    var maxZoomRatio by remember { mutableFloatStateOf(10f) }
    var cameraControl by remember { mutableStateOf<androidx.camera.core.CameraControl?>(null) }
    var isCapturing by remember { mutableStateOf(false) }

    LaunchedEffect(lensFacing, uiState.aspectRatio, uiState.isVideoMode, uiState.videoQuality) {
        val cameraProvider = context.getCameraProvider()
        cameraProvider.unbindAll()

        val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        
        val preview = Preview.Builder()
            .setTargetAspectRatio(uiState.aspectRatio)
            .build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
        
        try {
            if (uiState.isVideoMode) {
                val recorder = androidx.camera.video.Recorder.Builder()
                    .setQualitySelector(androidx.camera.video.QualitySelector.from(uiState.videoQuality))
                    .build()
                val newVideoCapture = androidx.camera.video.VideoCapture.withOutput(recorder)
                videoCapture = newVideoCapture
                imageCapture = null
                
                val camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    newVideoCapture
                )
                cameraControl = camera.cameraControl
                maxZoomRatio = camera.cameraInfo.zoomState.value?.maxZoomRatio ?: 10f
            } else {
                val newImageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setTargetAspectRatio(uiState.aspectRatio)
                    .build()
                imageCapture = newImageCapture
                videoCapture = null
                
                val camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    newImageCapture
                )
                cameraControl = camera.cameraControl
                maxZoomRatio = camera.cameraInfo.zoomState.value?.maxZoomRatio ?: 10f
            }
        } catch (e: Exception) {
            Log.e("CameraScreen", "Use case binding failed", e)
        }
    }

    LaunchedEffect(uiState.flashMode) {
        imageCapture?.flashMode = uiState.flashMode
    }

    LaunchedEffect(uiState.isStabilizerEnabled) {
        if (uiState.isStabilizerEnabled) {
            cameraControl?.setZoomRatio(1.2f)
            zoomRatio = 1.2f
        } else {
            cameraControl?.setZoomRatio(1.0f)
            zoomRatio = 1.0f
        }
    }

    val manualColorMatrix = remember(uiState.brightness, uiState.contrast, uiState.saturation, uiState.tint, uiState.isHdrEnabled) {
        val matrix = ColorMatrix()
        matrix.setToSaturation(uiState.saturation)
        
        // Simulated HDR: lower contrast slightly to recover highlights/shadows, and boost brightness a tiny bit
        val baseContrast = if (uiState.isHdrEnabled) 0.9f else 1.0f
        val baseBrightness = if (uiState.isHdrEnabled) 0.05f else 0.0f
        
        val scale = uiState.contrast * baseContrast
        val translate = (-.5f * scale + .5f) * 255f + ((uiState.brightness + baseBrightness) * 255f)
        val cbMatrix = ColorMatrix(floatArrayOf(
            scale + (uiState.tint * 0.2f), 0f, 0f, 0f, translate,
            0f, scale, 0f, 0f, translate,
            0f, 0f, scale - (uiState.tint * 0.2f), 0f, translate,
            0f, 0f, 0f, 1f, 0f
        ))
        matrix.timesAssign(cbMatrix)
        matrix
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(cameraControl) {
                    detectTapGestures { offset ->
                        cameraControl?.let { control ->
                            val factory = previewView.meteringPointFactory
                            val point = factory.createPoint(offset.x, offset.y)
                            val action = androidx.camera.core.FocusMeteringAction.Builder(point).build()
                            control.startFocusAndMetering(action)
                        }
                    }
                }
                .graphicsLayer {
                    if (uiState.showManualControls || uiState.brightness != 0f || uiState.contrast != 1f || uiState.saturation != 1f || uiState.tint != 0f) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            renderEffect = android.graphics.RenderEffect.createColorFilterEffect(
                                android.graphics.ColorMatrixColorFilter(manualColorMatrix.values)
                            ).asComposeRenderEffect()
                        }
                    }
                }
        )

        // Vignette Overlay
        if (uiState.vignette > 0f) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val radius = size.minDimension / 2f
                drawRect(
                    brush = androidx.compose.ui.graphics.Brush.radialGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = uiState.vignette * 0.8f)),
                        center = center,
                        radius = radius * 1.5f
                    )
                )
            }
        }

        // Cinematic Letterbox
        if (uiState.isCinematicMode) {
            Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.15f).background(Color.Black).align(Alignment.TopCenter))
            Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.15f).background(Color.Black).align(Alignment.BottomCenter))
        }

        if (uiState.isGridEnabled) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val thirdWidth = size.width / 3
                val thirdHeight = size.height / 3
                
                drawLine(
                    color = Color.White.copy(alpha = 0.5f),
                    start = Offset(thirdWidth, 0f),
                    end = Offset(thirdWidth, size.height),
                    strokeWidth = 1.dp.toPx()
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.5f),
                    start = Offset(thirdWidth * 2, 0f),
                    end = Offset(thirdWidth * 2, size.height),
                    strokeWidth = 1.dp.toPx()
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.5f),
                    start = Offset(0f, thirdHeight),
                    end = Offset(size.width, thirdHeight),
                    strokeWidth = 1.dp.toPx()
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.5f),
                    start = Offset(0f, thirdHeight * 2),
                    end = Offset(size.width, thirdHeight * 2),
                    strokeWidth = 1.dp.toPx()
                )
            }
        }

        var countdown by remember { mutableIntStateOf(0) }

        LaunchedEffect(countdown) {
            if (countdown > 0) {
                kotlinx.coroutines.delay(1000)
                countdown--
                if (countdown == 0) {
                    if (uiState.isVideoMode) {
                        videoCapture?.let { vc ->
                            recording = recordVideo(
                                videoCapture = vc,
                                context = context,
                                onVideoCaptured = { uri ->
                                    viewModel.onPhotoCaptured(uri)
                                    onNavigateToPreview()
                                },
                                onError = { e ->
                                    Log.e("CameraScreen", "Video capture failed", e)
                                    recording = null
                                }
                            )
                        }
                    } else {
                        imageCapture?.let { ic ->
                            isCapturing = true
                            takePhoto(
                                imageCapture = ic,
                                context = context,
                                onImageCaptured = { uri ->
                                    isCapturing = false
                                    viewModel.onPhotoCaptured(uri)
                                    onNavigateToPreview()
                                },
                                onError = {
                                    isCapturing = false
                                    Log.e("CameraScreen", "Capture failed", it)
                                }
                            )
                        }
                    }
                }
            }
        }

        if (countdown > 0) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = countdown.toString(), color = Color.White, style = MaterialTheme.typography.displayLarge)
            }
        }

        // Top Controls
        Column(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp, start = 8.dp, end = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateToSettings,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape).size(40.dp)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                }

                // Flash Toggle
                IconButton(
                    onClick = { viewModel.toggleFlash() },
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape).size(40.dp)
                ) {
                    val flashIcon = when(uiState.flashMode) {
                        androidx.camera.core.ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
                        androidx.camera.core.ImageCapture.FLASH_MODE_AUTO -> Icons.Default.FlashAuto
                        else -> Icons.Default.FlashOff
                    }
                    Icon(flashIcon, contentDescription = "Flash", tint = Color.White)
                }

                // Grid Toggle
                IconButton(
                    onClick = { viewModel.toggleGrid() },
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape).size(40.dp)
                ) {
                    Icon(if (uiState.isGridEnabled) Icons.Default.GridOn else Icons.Default.GridOff, contentDescription = "Grid", tint = Color.White)
                }

                // Timer Toggle
                IconButton(
                    onClick = { viewModel.toggleTimer() },
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape).size(40.dp)
                ) {
                    val timerIcon = when(uiState.timerDuration) {
                        3 -> Icons.Default.Timer3
                        10 -> Icons.Default.Timer10
                        else -> Icons.Default.Timer
                    }
                    Icon(timerIcon, contentDescription = "Timer", tint = if (uiState.timerDuration > 0) MaterialTheme.colorScheme.primaryContainer else Color.White)
                }

                // HDR Toggle
                IconButton(
                    onClick = { viewModel.toggleHdr() },
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape).size(40.dp)
                ) {
                    Text(
                        text = "HDR",
                        color = if (uiState.isHdrEnabled) MaterialTheme.colorScheme.primaryContainer else Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                // Ratio Toggle
                TextButton(
                    onClick = { viewModel.toggleAspectRatio() },
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(16.dp)).height(40.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = if(uiState.aspectRatio == androidx.camera.core.AspectRatio.RATIO_16_9) "16:9" else "4:3",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                // Manual Controls Toggle
                FilledTonalIconToggleButton(
                    checked = uiState.showManualControls,
                    onCheckedChange = { viewModel.toggleManualControls() },
                    colors = IconButtonDefaults.filledTonalIconToggleButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.5f),
                        checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        if (uiState.showManualControls) Icons.Filled.Tune else Icons.Outlined.Tune,
                        contentDescription = "Manual Controls",
                        tint = if (uiState.showManualControls) MaterialTheme.colorScheme.onSecondaryContainer else Color.White
                    )
                }

                // AI Toggle
                FilledTonalIconToggleButton(
                    checked = uiState.isAiEnabled,
                    onCheckedChange = { viewModel.toggleAi() },
                    colors = IconButtonDefaults.filledTonalIconToggleButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.5f),
                        checkedContainerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        if (uiState.isAiEnabled) Icons.Filled.AutoAwesome else Icons.Outlined.AutoAwesome,
                        contentDescription = "AI Enhance",
                        tint = if (uiState.isAiEnabled) MaterialTheme.colorScheme.onPrimaryContainer else Color.White
                    )
                }
            }
            
            // Video Specific Controls
            if (uiState.isVideoMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, start = 8.dp, end = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Quality Toggle
                    TextButton(
                        onClick = { viewModel.cycleVideoQuality() },
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(16.dp)).height(32.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                    ) {
                        val qualityText = when (uiState.videoQuality) {
                            androidx.camera.video.Quality.HIGHEST -> "4K"
                            androidx.camera.video.Quality.UHD -> "4K"
                            androidx.camera.video.Quality.FHD -> "1080p"
                            androidx.camera.video.Quality.HD -> "720p"
                            else -> "AUTO"
                        }
                        Text(text = qualityText, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // FPS Toggle
                    TextButton(
                        onClick = { viewModel.cycleVideoFps() },
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(16.dp)).height(32.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                    ) {
                        Text(text = "${uiState.videoFps} FPS", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // SlowMo Toggle
                    FilterChip(
                        selected = uiState.isSlowMoEnabled,
                        onClick = { viewModel.toggleSlowMo() },
                        label = { Text("SlowMo") },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Color.Black.copy(alpha = 0.5f),
                            labelColor = Color.White,
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        }

        // Manual Controls Panel
        if (uiState.showManualControls) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp, top = 80.dp, bottom = 180.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("BRIGHT", color = Color.White, style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = uiState.brightness,
                    onValueChange = { viewModel.updateAdjustments(it, uiState.contrast, uiState.saturation, uiState.warmth, uiState.tint, uiState.vignette) },
                    valueRange = -1f..1f,
                    modifier = Modifier.width(120.dp)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text("CONTRAST", color = Color.White, style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = uiState.contrast,
                    onValueChange = { viewModel.updateAdjustments(uiState.brightness, it, uiState.saturation, uiState.warmth, uiState.tint, uiState.vignette) },
                    valueRange = 0f..2f,
                    modifier = Modifier.width(120.dp)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text("SATURATION", color = Color.White, style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = uiState.saturation,
                    onValueChange = { viewModel.updateAdjustments(uiState.brightness, uiState.contrast, it, uiState.warmth, uiState.tint, uiState.vignette) },
                    valueRange = 0f..2f,
                    modifier = Modifier.width(120.dp)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text("WARMTH", color = Color.White, style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = uiState.warmth,
                    onValueChange = { viewModel.updateAdjustments(uiState.brightness, uiState.contrast, uiState.saturation, it, uiState.tint, uiState.vignette) },
                    valueRange = -1f..1f,
                    modifier = Modifier.width(120.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text("TINT", color = Color.White, style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = uiState.tint,
                    onValueChange = { viewModel.updateAdjustments(uiState.brightness, uiState.contrast, uiState.saturation, uiState.warmth, it, uiState.vignette) },
                    valueRange = -1f..1f,
                    modifier = Modifier.width(120.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text("VIGNETTE", color = Color.White, style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = uiState.vignette,
                    onValueChange = { viewModel.updateAdjustments(uiState.brightness, uiState.contrast, uiState.saturation, uiState.warmth, uiState.tint, it) },
                    valueRange = 0f..1f,
                    modifier = Modifier.width(120.dp)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                TextButton(onClick = { viewModel.updateAdjustments(0f, 1f, 1f, 0f, 0f, 0f) }) {
                    Text("RESET", color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        // Bottom Controls
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.4f))
                .padding(bottom = 32.dp, top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = uiState.isCinematicMode,
                    onClick = { viewModel.toggleCinematicMode() },
                    label = { Text("Cinematic") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
                Spacer(modifier = Modifier.width(16.dp))
                FilterChip(
                    selected = uiState.isStabilizerEnabled,
                    onClick = { viewModel.toggleStabilizer() },
                    label = { Text("Stabilizer") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))

            // Zoom Slider
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("1x", color = Color.White, fontWeight = FontWeight.Bold)
                Slider(
                    value = zoomRatio,
                    onValueChange = { 
                        zoomRatio = it
                        // Coerce to physical max but allow UI to pretend it goes higher if needed
                        cameraControl?.setZoomRatio(it.coerceAtMost(maxZoomRatio))
                    },
                    valueRange = 1f..100f,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                    )
                )
                Text("100x", color = Color.White, fontWeight = FontWeight.Bold)
            }
            
            Text(
                text = "${String.format(Locale.US, "%.1f", zoomRatio)}x AI Zoom", 
                color = Color.White, 
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Camera Actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Gallery Thumbnail
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color.DarkGray)
                        .border(2.dp, Color.White, CircleShape)
                        .clickable {
                            if (uiState.lastCapturedUri != null) {
                                onNavigateToPreview()
                            }
                        }
                ) {
                    if (uiState.lastCapturedUri != null) {
                        AsyncImage(
                            model = uiState.lastCapturedUri,
                            contentDescription = "Gallery",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                // Shutter Button
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(if (uiState.isVideoMode) (if (recording != null) Color.Red else Color.White) else Color.White)
                        .border(4.dp, if (recording != null) Color.DarkGray else Color.LightGray, CircleShape)
                        .clickable(enabled = !isCapturing && countdown == 0) {
                            if (uiState.isVideoMode) {
                                if (recording != null) {
                                    recording?.stop()
                                    recording = null
                                } else {
                                    videoCapture?.let { vc ->
                                        recording = recordVideo(
                                            videoCapture = vc,
                                            context = context,
                                            onVideoCaptured = { uri ->
                                                viewModel.onPhotoCaptured(uri) // Wait, we can reuse this for video for now
                                                onNavigateToPreview()
                                            },
                                            onError = { e ->
                                                Log.e("CameraScreen", "Video capture failed", e)
                                                recording = null
                                            }
                                        )
                                    }
                                }
                            } else {
                                if (uiState.timerDuration > 0) {
                                    countdown = uiState.timerDuration
                                } else {
                                    imageCapture?.let { ic ->
                                        isCapturing = true
                                        takePhoto(
                                            imageCapture = ic,
                                            context = context,
                                            onImageCaptured = { uri ->
                                                isCapturing = false
                                                viewModel.onPhotoCaptured(uri)
                                                onNavigateToPreview()
                                            },
                                            onError = {
                                                isCapturing = false
                                                Log.e("CameraScreen", "Capture failed", it)
                                            }
                                        )
                                    }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isCapturing) {
                        CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(32.dp))
                    }
                }

                Row {
                    // Photo / Video Switch
                    IconButton(
                        onClick = { viewModel.toggleVideoMode() },
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color.DarkGray.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(
                            if (uiState.isVideoMode) Icons.Default.PhotoCamera else Icons.Default.Videocam, 
                            contentDescription = "Switch Mode", 
                            tint = Color.White
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))

                    // Flip Camera
                    IconButton(
                        onClick = {
                            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                CameraSelector.LENS_FACING_FRONT
                            } else {
                                CameraSelector.LENS_FACING_BACK
                            }
                        },
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color.DarkGray.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(Icons.Default.Cameraswitch, contentDescription = "Flip Camera", tint = Color.White)
                    }
                }
            }
        }
    }
}

suspend fun Context.getCameraProvider(): ProcessCameraProvider = suspendCoroutine { continuation ->
    ProcessCameraProvider.getInstance(this).also { cameraProvider ->
        cameraProvider.addListener({
            continuation.resume(cameraProvider.get())
        }, ContextCompat.getMainExecutor(this))
    }
}

private fun takePhoto(
    imageCapture: ImageCapture,
    context: Context,
    onImageCaptured: (Uri) -> Unit,
    onError: (ImageCaptureException) -> Unit
) {
    val photoFile = File(
        context.cacheDir,
        SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US).format(System.currentTimeMillis()) + ".jpg"
    )

    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                val savedUri = Uri.fromFile(photoFile)
                onImageCaptured(savedUri)
            }

            override fun onError(exc: ImageCaptureException) {
                onError(exc)
            }
        }
    )
}

@android.annotation.SuppressLint("MissingPermission")
private fun recordVideo(
    videoCapture: androidx.camera.video.VideoCapture<androidx.camera.video.Recorder>,
    context: Context,
    onVideoCaptured: (Uri) -> Unit,
    onError: (Exception) -> Unit
): androidx.camera.video.Recording {
    val videoFile = File(
        context.cacheDir,
        SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US).format(System.currentTimeMillis()) + ".mp4"
    )

    val outputOptions = androidx.camera.video.FileOutputOptions.Builder(videoFile).build()

    return videoCapture.output
        .prepareRecording(context, outputOptions)
        .start(ContextCompat.getMainExecutor(context)) { recordEvent ->
            when (recordEvent) {
                is androidx.camera.video.VideoRecordEvent.Finalize -> {
                    if (!recordEvent.hasError()) {
                        onVideoCaptured(Uri.fromFile(videoFile))
                    } else {
                        val cause = recordEvent.cause
                        if (cause is Exception) {
                            onError(cause)
                        } else {
                            onError(Exception(cause))
                        }
                    }
                }
            }
        }
}
