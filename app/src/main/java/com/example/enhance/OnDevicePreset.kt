package com.example.enhance

/**
 * Preset kualitas untuk engine on-device. Mengontrol seberapa kuat proses
 * upscale/sharpen/saturasi. Dipakai saat engine [EnhanceEngineType.ON_DEVICE].
 */
enum class OnDevicePreset(
    val label: String,
    val description: String,
    // targetLongSide untuk upscale
    val targetLongSide: Int,
    // radius + amount unsharp mask
    val sharpenRadius: Int,
    val sharpenAmount: Float,
    // boost saturasi
    val saturation: Float,
    // kontras tone curve
    val contrast: Float
) {
    STANDARD(
        label = "Standard",
        description = "Seimbang: upscale 2048, sharpen ringan.",
        targetLongSide = 2048,
        sharpenRadius = 3,
        sharpenAmount = 0.55f,
        saturation = 1.12f,
        contrast = 1.08f
    ),
    DETAIL(
        label = "Detail",
        description = "Sharpen lebih kuat untuk memperjelas tekstur.",
        targetLongSide = 2560,
        sharpenRadius = 3,
        sharpenAmount = 0.85f,
        saturation = 1.12f,
        contrast = 1.08f
    ),
    NATURAL(
        label = "Natural",
        description = "Minimal: hanya upscale + koreksi ringan, warna tetap asli.",
        targetLongSide = 2048,
        sharpenRadius = 2,
        sharpenAmount = 0.3f,
        saturation = 1.0f,
        contrast = 1.0f
    )
}
