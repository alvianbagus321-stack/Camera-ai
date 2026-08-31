package com.example.enhance

/**
 * Daftar model generative (image) yang bisa dipakai lewat AI Horde.
 * [apiIds] adalah id model persis yang dikirim ke API AI Horde; boleh lebih dari satu
 * sebagai fallback bila worker untuk model pertama tidak tersedia.
 *
 * Catatan: ketersediaan model untuk pengguna anonim AI Horde bisa berubah-ubah.
 * Jika model yang dipilih tidak punya worker, app akan menampilkan pesan gagal —
 * pengguna bisa beralih ke model lain atau ke engine On-device.
 */
enum class HordeImageModel(
    val apiIds: List<String>,
    val label: String,
    val description: String
) {
    FLUX(
        apiIds = listOf("flux"),
        label = "FLUX",
        description = "FLUX.1 — model gratis populer di AI Horde, cepat & berkualitas."
    ),
    SDXL(
        apiIds = listOf("stable_diffusion_xl"),
        label = "Stable Diffusion XL",
        description = "SDXL — resolusi tinggi dan tersedia luas di AI Horde."
    ),
    JUGGERNAUT_XL(
        apiIds = listOf("Juggernaut XL"),
        label = "Juggernaut XL",
        description = "Model SDXL yang kuat untuk foto realistis."
    ),
    REALISTIC_VISION(
        apiIds = listOf("Realistic_Vision_V6.0 B1"),
        label = "Realistic Vision",
        description = "Model SD1.5 khusus foto realistis."
    ),
    DELIBERATE(
        apiIds = listOf("Deliberate"),
        label = "Deliberate",
        description = "Model SD1.5 serbaguna & realistis."
    )
}
