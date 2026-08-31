package com.example.enhance

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Mesin enhance generative via [AI Horde](https://aihorde.net) — crowdsourced GPU, gratis,
 * tanpa API key (pakai key anonim). Foto dikirim sebagai base64 (img2img, denoise rendah)
 * supaya hasil tetap mempertahankan konten/subjek asli sambil menajamkan & memperkaya detail.
 *
 * Catatan: pengguna anonim prioritasnya paling rendah, jadi bisa antre lama atau gagal jika
 * tidak ada worker untuk model yang diminta. [EnhanceEngineFactory] selalu bisa di-fallback
 * ke [OnDeviceEnhancer] bila jaringan/antrean gagal.
 */
class StableHordeEnhancer(
    private val client: OkHttpClient = defaultClient()
) : EnhanceEngine {

    override val type: EnhanceEngineType = EnhanceEngineType.STABLE_HORDE

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val baseUrl = "https://aihorde.net/api/v2"

    // Key anonim resmi AI Horde untuk pengguna tanpa akun.
    private val anonymousKey = "0000000000"

    // Total waktu menunggu (detik) sebelum dianggap gagal.
    private val pollBudgetMs = 200_000L
    private val pollIntervalMs = 4_000L

    override suspend fun enhance(
        context: Context,
        source: Bitmap,
        reference: Bitmap?,
        apiKey: String,
        onStatus: (String) -> Unit
    ): EnhanceResult = withContext(Dispatchers.IO) {
        val key = apiKey.ifBlank { anonymousKey }

        onStatus("Mengompres foto…")
        val b64 = encodeJpeg(source, quality = 90)

        val payload = JSONObject().apply {
            put("prompt", buildPrompt())
            put("models", JSONArray().put("flux").put("stable_diffusion_xl"))
            put("source_image", b64)
            put("source_processing", "img2img")
            put("nsfw", false)
            put("params", JSONObject().apply {
                put("denoising_strength", 0.35)
                put("cfg_scale", 4.0)
                put("steps", 20)
                put("sampler_name", "k_euler")
                put("width", source.width)
                put("height", source.height)
            })
        }

        onStatus("Mengirim foto ke AI Horde…")
        val jobId = submit(payload.toString(), key)

        onStatus("Menunggu hasil AI (anonim bisa antre lama)…")
        val bmp = poll(jobId, key, onStatus)
            ?: throw RuntimeException(
                "AI Horde belum selesai (tidak ada worker / antrean anonim terlalu panjang). " +
                    "Coba lagi, atau pakai mode On-device."
            )

        EnhanceResult(
            bitmap = bmp,
            engine = type,
            note = "Dinaikkan kualitasnya oleh AI Horde (img2img, gratis, tanpa key). " +
                "Konten asli dipertahankan, detail diperkaya."
        )
    }

    private fun buildPrompt(): String =
        "Enhance and sharpen this photo, increase fine detail and clarity, " +
            "correct color and exposure naturally, keep the exact same subject, " +
            "composition, and scene unchanged. Photorealistic, high quality."

    private fun encodeJpeg(bmp: Bitmap, quality: Int): String {
        val stream = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    private fun submit(body: String, key: String): String {
        val req = Request.Builder()
            .url("$baseUrl/generate/async")
            .header("apikey", key)
            .header("Client-Agent", "AI-Enhance-Camera:1.0:com.example")
            .post(body.toRequestBody(jsonMediaType))
            .build()

        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                throw RuntimeException("AI Horde submit gagal (${resp.code}): ${trim(text)}")
            }
            val obj = JSONObject(text)
            val id = obj.optString("id")
            if (id.isBlank()) {
                val msg = obj.optString("message", text)
                throw RuntimeException("AI Horde menolak request: ${trim(msg)}")
            }
            return id
        }
    }

    private fun poll(jobId: String, key: String, onStatus: (String) -> Unit): Bitmap? {
        val deadline = System.currentTimeMillis() + pollBudgetMs
        while (System.currentTimeMillis() < deadline) {
            val status = getStatus(jobId, key)
            if (status == null) {
                // request dibatalkan / tidak ditemukan
                return null
            }
            val done = status.optBoolean("done", false)
            val generations = status.optJSONArray("generations")
            if (done && generations != null && generations.length() > 0) {
                val imgB64 = generations.getJSONObject(0).optString("img")
                if (imgB64.isNotBlank()) {
                    val bytes = Base64.decode(imgB64, Base64.DEFAULT)
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bmp != null) return bmp
                }
                // done tapi tak ada gambar valid
                return null
            }
            if (done) return null

            val processing = status.optInt("processing", 0)
            val waiting = status.optInt("waiting", 0)
            val position = status.optInt("queue_position", 0)
            onStatus("Antrean AI: $position (worker $processing). Menunggu…")

            // Sinkron/blokir ringan di IO — sleep kecil agar tidak spam.
            runCatching { Thread.sleep(pollIntervalMs) }
        }
        return null
    }

    private fun getStatus(jobId: String, key: String): JSONObject? {
        val req = Request.Builder()
            .url("$baseUrl/generate/status/$jobId")
            .header("apikey", key)
            .header("Client-Agent", "AI-Enhance-Camera:1.0:com.example")
            .get()
            .build()
        return try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) null else JSONObject(resp.body?.string() ?: "")
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun trim(s: String): String = s.take(300)

    private companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}
