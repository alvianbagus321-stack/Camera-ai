package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import com.example.viewmodel.CameraViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(
    viewModel: CameraViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val uri = uiState.lastCapturedUri
    
    val manualColorMatrix = remember(uiState.brightness, uiState.contrast, uiState.saturation, uiState.tint, uiState.isHdrEnabled) {
        val matrix = ColorMatrix()
        matrix.setToSaturation(uiState.saturation)
        
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
        
        // If AI is enabled, add a slight cinematic log boost on top of manual settings
        if (uiState.isAiEnabled) {
            val aiMatrix = ColorMatrix().apply {
                setToSaturation(1.1f)
                val aiContrast = 1.05f
                val aiTranslate = (-.5f * aiContrast + .5f) * 255f
                val aiCbMatrix = ColorMatrix(floatArrayOf(
                    aiContrast, 0f, 0f, 0f, aiTranslate,
                    0f, aiContrast, 0f, 0f, aiTranslate,
                    0f, 0f, aiContrast, 0f, aiTranslate,
                    0f, 0f, 0f, 1f, 0f
                ))
                timesAssign(aiCbMatrix)
            }
            matrix.timesAssign(aiMatrix)
        }
        
        matrix
    }

    var sliderPosition by remember { mutableFloatStateOf(0.5f) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Enhance Preview") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { /* TODO Save to Gallery */ }) {
                        Icon(Icons.Default.Save, contentDescription = "Save")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (uri == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No image found.", color = Color.White)
                }
                return@Scaffold
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            sliderPosition = (change.position.x / size.width).coerceIn(0f, 1f)
                        }
                    }
            ) {
                // Original Image
                Image(
                    painter = rememberAsyncImagePainter(model = uri),
                    contentDescription = "Original",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )

                val hasAdjustments = uiState.isAiEnabled || uiState.brightness != 0f || uiState.contrast != 1f || uiState.saturation != 1f || uiState.tint != 0f || uiState.vignette != 0f || uiState.isHdrEnabled
                // Enhanced Image (clipped by slider)
                if (hasAdjustments) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .drawWithContent {
                                clipRect(right = size.width * sliderPosition) {
                                    this@drawWithContent.drawContent()
                                }
                            }
                    ) {
                        Image(
                            painter = rememberAsyncImagePainter(model = uri),
                            contentDescription = "Enhanced",
                            contentScale = ContentScale.Fit,
                            colorFilter = ColorFilter.colorMatrix(manualColorMatrix),
                            modifier = Modifier.fillMaxSize()
                        )
                        
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
                        
                        if (uiState.isCinematicMode) {
                            Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.15f).background(Color.Black).align(Alignment.TopCenter))
                            Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.15f).background(Color.Black).align(Alignment.BottomCenter))
                        }
                    }

                    // Slider Line
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(2.dp)
                            .background(Color.White)
                            .align(Alignment.CenterStart)
                            .offset(x = (sliderPosition * 360).dp) // This is a rough offset, let's just use canvas for the line
                    )
                }
            }

            // AI Analysis Results Panel
            if (uiState.isAiEnabled) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = "AI", tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("AI Scene Analysis", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        }
                        
                        Spacer(Modifier.height(16.dp))

                        if (uiState.isAnalyzing) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                            Text("Analyzing pixels...", modifier = Modifier.align(Alignment.CenterHorizontally))
                        } else if (uiState.error != null) {
                            Text("Error: ${uiState.error}", color = MaterialTheme.colorScheme.error)
                        } else if (uiState.analysisResult != null) {
                            val res = uiState.analysisResult!!
                            Text("Detected Scene: ${res.scene}")
                            Text("Smart Filter: ${res.suggestedFilter} (Applied)")
                            if (res.detectedText.isNotEmpty()) {
                                Text("OCR Text: ${res.detectedText}")
                            }
                            Text("AI Touchup: ${res.objectsRemovedMessage}")
                        }
                    }
                }
            }
        }
    }
}
