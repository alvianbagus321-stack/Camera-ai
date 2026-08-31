package com.example.enhance

/**
 * Daftar model Gemini yang dipakai untuk analisis scene/OCR (bukan untuk generate gambar).
 * Nilai [modelName] dikirim ke SDK `GenerativeModel`.
 */
enum class GeminiAnalysisModel(
    val modelName: String,
    val label: String,
    val description: String
) {
    GEMINI_1_5_PRO(
        modelName = "gemini-1.5-pro-latest",
        label = "Gemini 1.5 Pro",
        description = "Analisis scene akurat & detail (default)."
    ),
    GEMINI_2_0_FLASH(
        modelName = "gemini-2.0-flash",
        label = "Gemini 2.0 Flash",
        description = "Cepat & hemat, cocok untuk analisis real-time."
    ),
    GEMINI_2_5_FLASH(
        modelName = "gemini-2.5-flash",
        label = "Gemini 2.5 Flash",
        description = "Terbaru, cepat & akurat (jika akun kamu mendukung)."
    )
}
