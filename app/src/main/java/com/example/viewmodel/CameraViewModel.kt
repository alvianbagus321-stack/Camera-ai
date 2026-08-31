package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.enhance.EnhanceEngineFactory
import com.example.enhance.EnhanceEngineType
import com.example.enhance.GeminiAnalysisModel
import com.example.enhance.HordeImageModel
import com.example.enhance.OnDevicePreset
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
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
    val vignette: Float = 0f,
    val enhanceEngineType: EnhanceEngineType = EnhanceEngineType.ON_DEVICE,
    val hordeApiKey: String = "",
    val hordeModel: HordeImageModel = HordeImageModel.FLUX,
    val geminiModel: GeminiAnalysisModel = GeminiAnalysisModel.GEMINI_1_5_PRO,
    val onDevicePreset: OnDevicePreset = OnDevicePreset.STANDARD,
    val isEnhancing: Boolean = false,
    val enhanceStatus: String? = null,
    val enhanceError: String? = null,
    val enhancedUri: Uri? = null,
    val enhanceNote: String? = null,
    val referenceUri: Uri? = null,
    val zoomRatio: Float = 1f
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
        val hordeKey = prefs.getString("HORDE_API_KEY", "") ?: ""
        val hordeModel = prefs.getString("HORDE_MODEL", null)
            ?.let { saved -> HordeImageModel.entries.firstOrNull { it.name == saved } }
            ?: HordeImageModel.FLUX
        val geminiModel = prefs.getString("GEMINI_MODEL", null)
            ?.let { saved -> GeminiAnalysisModel.entries.firstOrNull { it.name == saved } }
            ?: GeminiAnalysisModel.GEMINI_1_5_PRO
        val onDevicePreset = prefs.getString("ON_DEVICE_PRESET", null)
            ?.let { saved -> OnDevicePreset.entries.firstOrNull { it.name == saved } }
            ?: OnDevicePreset.STANDARD
        _uiState.value = _uiState.value.copy(
            apiKey = initialKey,
            hordeApiKey = hordeKey,
            hordeModel = hordeModel,
            geminiModel = geminiModel,
            onDevicePreset = onDevicePreset
        )
    }

    fun saveApiKey(key: String) {
        prefs.edit().putString("API_KEY", key).apply()
        _uiState.value = _uiState.value.copy(apiKey = key)
    }

    fun setEnhanceEngine(type: EnhanceEngineType) {
        _uiState.value = _uiState.value.copy(enhanceEngineType = type, enhanceError = null)
    }

    fun saveHordeApiKey(key: String) {
        prefs.edit().putString("HORDE_API_KEY", key).apply()
        _uiState.value = _uiState.value.copy(hordeApiKey = key)
    }

    fun setHordeModel(model: HordeImageModel) {
        prefs.edit().putString("HORDE_MODEL", model.name).apply()
        _uiState.value = _uiState.value.copy(hordeModel = model, enhanceError = null)
    }

    fun setGeminiModel(model: GeminiAnalysisModel) {
        prefs.edit().putString("GEMINI_MODEL", model.name).apply()
        _uiState.value = _uiState.value.copy(geminiModel = model)
    }

    fun setOnDevicePreset(preset: OnDevicePreset) {
        prefs.edit().putString("ON_DEVICE_PRESET", preset.name).apply()
        _uiState.value = _uiState.value.copy(onDevicePreset = preset, enhanceError = null)
    }

    fun setReferenceUri(uri: Uri?) {
        _uiState.value = _uiState.value.copy(referenceUri = uri)
    }

    /**
     * Menjalankan engine enhance. Alur mengikuti konsep aplikasi:
     *  1. Foto wide/1x adalah DATA awal (sumber).
     *  2. Foto "zoom" hanyalah crop dari foto wide.
     *  3. Enhance diproses pada foto wide, lalu bagian zoom di-crop dari hasilnya.
     *     Jadi hasil zoom memakai semua data foto wide (resolusi & konteks penuh).
     */
    fun enhanceImage() {
        if (_uiState.value.isEnhancing) return
        if (!_uiState.value.isAiEnabled) {
            _uiState.value = _uiState.value.copy(
                enhanceError = "Mode AI dinonaktifkan. Aktifkan tombol AI di layar kamera untuk enhance.",
                enhanceStatus = null
            )
            return
        }
        val zoomRatio = _uiState.value.zoomRatio
        val wideUri = _uiState.value.referenceUri ?: _uiState.value.lastCapturedUri ?: return

        _uiState.value = _uiState.value.copy(
            isEnhancing = true,
            enhanceError = null,
            enhanceStatus = "Menyiapkan…",
            enhancedUri = null,
            enhanceNote = null
        )

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val engine = EnhanceEngineFactory.create(
                    type = _uiState.value.enhanceEngineType,
                    onDevicePreset = _uiState.value.onDevicePreset
                )
                // Data awal: foto wide.
                val source = loadBitmapFromUri(wideUri)
                    ?: throw RuntimeException("Gagal membaca foto wide (data awal).")

                val result = engine.enhance(
                    context = getApplication(),
                    source = source,
                    reference = null,
                    apiKey = _uiState.value.hordeApiKey,
                    modelId = _uiState.value.hordeModel.apiIds.joinToString(",")
                ) { status ->
                    _uiState.value = _uiState.value.copy(enhanceStatus = status)
                }

                // Ambil wilayah "zoom" dari hasil enhance wide, lalu upscale.
                val enhanced = if (zoomRatio > 1.05f) {
                    cropZoomRegion(result.bitmap, zoomRatio)
                } else {
                    result.bitmap
                }

                val savedUri = saveBitmapToCache(enhanced)
                _uiState.value = _uiState.value.copy(
                    isEnhancing = false,
                    enhancedUri = savedUri,
                    enhanceStatus = "Selesai.",
                    enhanceNote = result.note
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isEnhancing = false,
                    enhanceError = e.localizedMessage ?: "Enhance gagal.",
                    enhanceStatus = null
                )
            }
        }
    }

    /** Crop bagian tengah [bmp] sesuai [zoomRatio] lalu upscale ke sisi panjang 2048. */
    private fun cropZoomRegion(bmp: Bitmap, zoomRatio: Float): Bitmap {
        val w = bmp.width
        val h = bmp.height
        val factor = zoomRatio.coerceIn(1.001f, 10f)
        val cropSide = 1f / factor
        val cw = (w * cropSide).toInt().coerceAtLeast(1)
        val ch = (h * cropSide).toInt().coerceAtLeast(1)
        val left = (w - cw) / 2
        val top = (h - ch) / 2
        val crop = Bitmap.createBitmap(bmp, left, top, cw, ch)

        val targetLong = 2048
        val longSide = max(cw, ch)
        return if (longSide < targetLong) {
            val scale = targetLong.toFloat() / longSide
            val nw = (cw * scale).toInt().coerceAtLeast(1)
            val nh = (ch * scale).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(crop, nw, nh, true)
        } else {
            crop
        }
    }

    fun clearEnhancement() {
        _uiState.value = _uiState.value.copy(
            enhancedUri = null,
            enhanceNote = null,
            enhanceStatus = null,
            enhanceError = null
        )
    }

    /** Menyimpan hasil enhance (atau foto asli bila belum di-enhance) ke galeri. */
    fun saveToGallery() {
        val uri = _uiState.value.enhancedUri ?: _uiState.value.lastCapturedUri ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bmp = loadBitmapFromUri(uri)
                    ?: throw RuntimeException("Gagal membaca foto untuk disimpan.")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    saveBitmapToMediaStore(getApplication(), bmp)
                } else {
                    throw RuntimeException("Simpan ke galeri perlu Android 10+.")
                }
                _uiState.value = _uiState.value.copy(enhanceStatus = "Tersimpan ke Galeri.")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    enhanceError = e.localizedMessage ?: "Gagal menyimpan."
                )
            }
        }
    }

    private fun saveBitmapToCache(bmp: Bitmap): Uri {
        val file = File(
            getApplication<Application>().cacheDir,
            "enhanced_${System.currentTimeMillis()}.jpg"
        )
        FileOutputStream(file).use { out ->
            bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        return Uri.fromFile(file)
    }

    private fun saveBitmapToMediaStore(context: Context, bmp: Bitmap) {
        val resolver = context.contentResolver
        val values = android.content.ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "AI_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/AI Camera"
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val item = resolver.insert(collection, values)
            ?: throw RuntimeException("Gagal membuat file di galeri.")
        resolver.openOutputStream(item).use { out ->
            if (out == null) throw RuntimeException("Gagal membuka output.")
            bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(item, values, null, null)
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

    fun onPhotoCaptured(uri: Uri, zoomRatio: Float = 1f) {
        // Foto yang benar-benar diambil selalu foto WIDE / 1x (data awal).
        // "Zoom" pada dasarnya = crop + upscale dari foto wide ini.
        if (zoomRatio > 1.05f) {
            val wideUri = uri
            val zoomUri = createZoomPhoto(wideUri, zoomRatio)
            _uiState.value = _uiState.value.copy(
                lastCapturedUri = zoomUri,
                referenceUri = wideUri,
                zoomRatio = zoomRatio,
                analysisResult = null,
                error = null
            )
        } else {
            _uiState.value = _uiState.value.copy(
                lastCapturedUri = uri,
                referenceUri = null,
                zoomRatio = 1f,
                analysisResult = null,
                error = null
            )
        }
        if (_uiState.value.isAiEnabled) {
            analyzeImage(_uiState.value.lastCapturedUri!!)
        }
    }

    /**
     * Membuat gambar "zoom" = crop bagian tengah foto wide sesuai [zoomRatio], lalu
     * di-upscale agar resolusi target tetap layak. Foto wide asli disimpan sebagai
     * [referenceUri] (data awal) untuk dipakai enhance.
     */
    private fun createZoomPhoto(uri: Uri, zoomRatio: Float): Uri {
        val bmp = loadBitmapFromUri(uri) ?: return uri
        val w = bmp.width
        val h = bmp.height
        val factor = zoomRatio.coerceIn(1.001f, 10f)
        val cropSide = 1f / factor
        val cw = (w * cropSide).toInt().coerceAtLeast(1)
        val ch = (h * cropSide).toInt().coerceAtLeast(1)
        val left = (w - cw) / 2
        val top = (h - ch) / 2
        val crop = Bitmap.createBitmap(bmp, left, top, cw, ch)

        // Upscale hasil crop ke sisi panjang target agar tetap tampak tajam
        val targetLong = 2048
        val longSide = max(cw, ch)
        val result = if (longSide < targetLong) {
            val scale = targetLong.toFloat() / longSide
            val nw = (cw * scale).toInt().coerceAtLeast(1)
            val nh = (ch * scale).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(crop, nw, nh, true)
        } else {
            crop
        }
        return saveBitmapToCache(result)
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
                    modelName = _uiState.value.geminiModel.modelName,
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
