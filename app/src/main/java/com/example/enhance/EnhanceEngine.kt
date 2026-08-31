package com.example.enhance

import android.content.Context
import android.graphics.Bitmap

/**
 * Jenis mesin yang dipakai untuk men-enhance foto.
 *
 * - [ON_DEVICE]: diproses di HP, offline, 100% gratis, tanpa API key / tanpa internet.
 *   Kualitas terjamin & deterministik (upscale + sharpen + white-balance pakai referensi 1x).
 *
 * - [STABLE_HORDE]: AI generative gratis (img2img) melalui AI Horde (crowdsourced GPU).
 *   Tanpa API key (pakai key anonim), tapi butuh internet dan hasilnya bisa butuh antrean.
 */
enum class EnhanceEngineType(
    val label: String,
    val description: String,
    val requiresInternet: Boolean
) {
    ON_DEVICE(
        label = "On-device",
        description = "Offline & gratis, tanpa API key. Menaikkan resolusi, menajamkan detail, " +
            "dan mengoreksi warna menggunakan referensi foto 1x.",
        requiresInternet = false
    ),
    STABLE_HORDE(
        label = "AI Horde (online)",
        description = "AI generative gratis via AI Horde (img2img). Tanpa API key, tapi butuh " +
            "internet & bisa antre lama (pengguna anonim prioritas rendah).",
        requiresInternet = true
    )
}

/** Hasil proses enhance. [bitmap] hasil akhir, [engine] mesin yang dipakai, [note] keterangan singkat. */
data class EnhanceResult(
    val bitmap: Bitmap,
    val engine: EnhanceEngineType,
    val note: String
)

/** Kontrak untuk semua mesin enhance. */
interface EnhanceEngine {
    val type: EnhanceEngineType

    /**
     * @param source    foto utama (mis. hasil zoom) yang mau dinaikkan kualitasnya.
     * @param reference opsional: foto 1x / wide yang dipakai sebagai referensi warna & gaya.
     * @param apiKey    kunci opsional (mis. untuk pengguna AI Horde terdaftar); boleh kosong = anonim.
     * @param modelId   id model terpilih (spesifik untuk tiap engine, mis. model AI Horde). Boleh kosong.
     * @param onStatus  callback progres untuk ditampilkan ke UI.
     */
    suspend fun enhance(
        context: Context,
        source: Bitmap,
        reference: Bitmap?,
        apiKey: String,
        modelId: String = "",
        onStatus: (String) -> Unit
    ): EnhanceResult
}

/** Factory sederhana untuk membuat engine sesuai jenisnya. */
object EnhanceEngineFactory {
    fun create(type: EnhanceEngineType): EnhanceEngine = when (type) {
        EnhanceEngineType.ON_DEVICE -> OnDeviceEnhancer()
        EnhanceEngineType.STABLE_HORDE -> StableHordeEnhancer()
    }
}
