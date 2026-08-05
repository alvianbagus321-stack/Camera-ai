package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

data class CameraUiState(
    val lastCapturedUri: Uri? = null,
    val isAiEnabled: Boolean = true,
    val apiKey: String = "",
    val analysisResult: AnalysisResult? = null,
    val isAnalyzing: Boolean = false,
    val error: String? = null,
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val warmth: Float = 0f,
    val showManualControls: Boolean = false,
    val flashMode: Int = androidx.camera.core.ImageCapture.FLASH_MODE_OFF,
    val aspectRatio: Int = androidx.camera.core.AspectRatio.RATIO_4_3,
    val isVideoMode: Boolean = false,
    val timerDuration: Int = 0,
    val isGridEnabled: Boolean = false,
    val isCinematicMode: Boolean = false,
    val isStabilizerEnabled: Boolean = false,
    val isHdrEnabled: Boolean = false,
    val isSlowMoEnabled: Boolean = false,
    val videoQuality: androidx.camera.video.Quality = androidx.camera.video.Quality.HIGHEST,
    val videoFps: Int = 30,
    val tint: Float = 0f,
    val vignette: Float = 0f
)

data class AnalysisResult(
    val scene: String = "",
    val suggestedFilter: String = "",
    val detectedText: String = "",
    val objectsRemovedMessage: String = ""
)

class CameraViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("ai_camera_prefs", Context.MODE_PRIVATE)
    
    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    init {
        val savedKey = prefs.getString("API_KEY", "") ?: ""
        val initialKey = if (savedKey.isNotEmpty()) savedKey else BuildConfig.GEMINI_API_KEY
        _uiState.value = _uiState.value.copy(apiKey = initialKey)
    }

    fun saveApiKey(key: String) {
        prefs.edit().putString("API_KEY", key).apply()
        _uiState.value = _uiState.value.copy(apiKey = key)
    }

    fun toggleAi() {
        _uiState.value = _uiState.value.copy(isAiEnabled = !_uiState.value.isAiEnabled)
    }

    fun toggleManualControls() {
        _uiState.value = _uiState.value.copy(showManualControls = !_uiState.value.showManualControls)
    }

    fun toggleFlash() {
        val nextMode = when (_uiState.value.flashMode) {
            androidx.camera.core.ImageCapture.FLASH_MODE_OFF -> androidx.camera.core.ImageCapture.FLASH_MODE_ON
            androidx.camera.core.ImageCapture.FLASH_MODE_ON -> androidx.camera.core.ImageCapture.FLASH_MODE_AUTO
            else -> androidx.camera.core.ImageCapture.FLASH_MODE_OFF
        }
        _uiState.value = _uiState.value.copy(flashMode = nextMode)
    }

    fun toggleAspectRatio() {
        val nextRatio = when (_uiState.value.aspectRatio) {
            androidx.camera.core.AspectRatio.RATIO_4_3 -> androidx.camera.core.AspectRatio.RATIO_16_9
            else -> androidx.camera.core.AspectRatio.RATIO_4_3
        }
        _uiState.value = _uiState.value.copy(aspectRatio = nextRatio)
    }

    fun toggleVideoMode() {
        _uiState.value = _uiState.value.copy(isVideoMode = !_uiState.value.isVideoMode)
    }

    fun toggleGrid() {
        _uiState.value = _uiState.value.copy(isGridEnabled = !_uiState.value.isGridEnabled)
    }

    fun toggleTimer() {
        val nextTimer = when (_uiState.value.timerDuration) {
            0 -> 3
            3 -> 10
            else -> 0
        }
        _uiState.value = _uiState.value.copy(timerDuration = nextTimer)
    }

    fun toggleCinematicMode() {
        _uiState.value = _uiState.value.copy(isCinematicMode = !_uiState.value.isCinematicMode)
    }

    fun toggleStabilizer() {
        _uiState.value = _uiState.value.copy(isStabilizerEnabled = !_uiState.value.isStabilizerEnabled)
    }

    fun toggleHdr() {
        _uiState.value = _uiState.value.copy(isHdrEnabled = !_uiState.value.isHdrEnabled)
    }

    fun toggleSlowMo() {
        _uiState.value = _uiState.value.copy(isSlowMoEnabled = !_uiState.value.isSlowMoEnabled)
    }

    fun cycleVideoQuality() {
        val nextQuality = when (_uiState.value.videoQuality) {
            androidx.camera.video.Quality.HIGHEST -> androidx.camera.video.Quality.HD
            androidx.camera.video.Quality.HD -> androidx.camera.video.Quality.FHD
            androidx.camera.video.Quality.FHD -> androidx.camera.video.Quality.UHD
            else -> androidx.camera.video.Quality.HIGHEST
        }
        _uiState.value = _uiState.value.copy(videoQuality = nextQuality)
    }

    fun cycleVideoFps() {
        val nextFps = when (_uiState.value.videoFps) {
            30 -> 60
            60 -> if (_uiState.value.isSlowMoEnabled) 120 else 30
            120 -> if (_uiState.value.isSlowMoEnabled) 240 else 30
            else -> 30
        }
        _uiState.value = _uiState.value.copy(videoFps = nextFps)
    }

    fun updateAdjustments(brightness: Float, contrast: Float, saturation: Float, warmth: Float, tint: Float, vignette: Float) {
        _uiState.value = _uiState.value.copy(
            brightness = brightness,
            contrast = contrast,
            saturation = saturation,
            warmth = warmth,
            tint = tint,
            vignette = vignette
        )
    }

    fun onPhotoCaptured(uri: Uri) {
        _uiState.value = _uiState.value.copy(lastCapturedUri = uri, analysisResult = null, error = null)
        if (_uiState.value.isAiEnabled) {
            analyzeImage(uri)
        }
    }

    fun clearAnalysis() {
        _uiState.value = _uiState.value.copy(lastCapturedUri = null, analysisResult = null, error = null, isAnalyzing = false)
    }

    private fun analyzeImage(uri: Uri) {
        val apiKey = _uiState.value.apiKey
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            _uiState.value = _uiState.value.copy(error = "Please set a valid Gemini API Key in Settings.")
            return
        }

        _uiState.value = _uiState.value.copy(isAnalyzing = true, error = null)

        viewModelScope.launch {
            try {
                val bitmap = loadBitmapFromUri(uri)
                if (bitmap == null) {
                    _uiState.value = _uiState.value.copy(isAnalyzing = false, error = "Failed to load image.")
                    return@launch
                }

                val generativeModel = GenerativeModel(
                    modelName = "gemini-1.5-pro-latest",
                    apiKey = apiKey
                )

                val prompt = """
                    Analyze this photo. Return a JSON object with the following fields:
                    - "scene": Detect the scene (e.g., Landscape, Food, Portrait, Night, Document, etc).
                    - "suggestedFilter": Suggest a color grading filter based on the scene (e.g., Cinematic, Vibrant, Warm, Cool, Log).
                    - "detectedText": Extract any prominent text you see, or leave empty if none.
                    - "objectsRemovedMessage": Imagine you auto-removed an unwanted background object (like a stray wire or photobomber). Describe what you "removed" briefly, or say "No clutter removed" if the scene is clean.
                    
                    Only return the JSON object, without any markdown formatting like ```json.
                """.trimIndent()

                val response = generativeModel.generateContent(
                    content {
                        image(bitmap)
                        text(prompt)
                    }
                )

                val responseText = response.text ?: "{}"
                val cleanJson = responseText.replace("```json", "").replace("```", "").trim()
                
                val json = JSONObject(cleanJson)
                val result = AnalysisResult(
                    scene = json.optString("scene", "Unknown"),
                    suggestedFilter = json.optString("suggestedFilter", "None"),
                    detectedText = json.optString("detectedText", ""),
                    objectsRemovedMessage = json.optString("objectsRemovedMessage", "No clutter removed")
                )

                _uiState.value = _uiState.value.copy(isAnalyzing = false, analysisResult = result)

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isAnalyzing = false, error = e.localizedMessage ?: "Analysis failed.")
            }
        }
    }

    private fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            val inputStream = getApplication<Application>().contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(inputStream)
        } catch (e: Exception) {
            null
        }
    }
}
