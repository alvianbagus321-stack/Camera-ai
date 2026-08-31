package com.example.enhance

import android.content.Context
import android.graphics.Bitmap
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Mesin enhance yang berjalan sepenuhnya di perangkat (offline, gratis, tanpa API key).
 *
 * Pipeline kualitas:
 *  1. White-balance (gray world) — memakai rata-rata warna foto referensi 1x bila tersedia,
 *     agar tone hasil zoom konsisten dengan foto wide.
 *  2. Upscale bilinear ke sisi panjang target (membantu saat foto zoom tampil besar).
 *  3. Unsharp mask untuk menajamkan detail.
 *  4. Boosting saturasi + kurva tone kontras.
 */
class OnDeviceEnhancer(
    private val preset: OnDevicePreset = OnDevicePreset.STANDARD
) : EnhanceEngine {

    override val type: EnhanceEngineType = EnhanceEngineType.ON_DEVICE

    override suspend fun enhance(
        context: Context,
        source: Bitmap,
        reference: Bitmap?,
        apiKey: String,
        modelId: String,
        onStatus: (String) -> Unit
    ): EnhanceResult = withContext(Dispatchers.Default) {
        onStatus("Membaca warna referensi 1x…")
        val gains = computeWhiteBalanceGains(source, reference)

        onStatus("Menaikkan resolusi…")
        var bmp = resizeBilinear(source, targetSize(source.width, source.height, preset.targetLongSide))

        onStatus("Menajamkan detail…")
        bmp = unsharpMask(bmp, radius = preset.sharpenRadius, amount = preset.sharpenAmount)

        onStatus("Mengoreksi warna & tone…")
        bmp = applyWhiteBalance(bmp, gains)
        bmp = applySaturation(bmp, preset.saturation)
        bmp = applyToneCurve(bmp, preset.contrast)

        val note = if (reference != null) {
            "Dinaikkan kualitasnya on-device (${preset.label}): resolusi + detail, warna disesuaikan dengan referensi 1x."
        } else {
            "Dinaikkan kualitasnya on-device (${preset.label}): resolusi + detail + koreksi warna (tanpa referensi 1x)."
        }
        EnhanceResult(bitmap = bmp, engine = type, note = note)
    }

    // ---- Target size -----------------------------------------------------

    private fun targetSize(w: Int, h: Int, targetLongSide: Int): Pair<Int, Int> {
        val longSide = max(w, h)
        if (longSide >= targetLongSide) return w to h
        val scale = targetLongSide.toFloat() / longSide
        val nw = (w * scale).toInt().coerceAtLeast(1)
        val nh = (h * scale).toInt().coerceAtLeast(1)
        return nw to nh
    }

    // ---- White balance (gray world) ---------------------------------------

    private fun computeWhiteBalanceGains(source: Bitmap, reference: Bitmap?): FloatArray {
        val bmp = reference ?: source
        val nw = bmp.width
        val nh = bmp.height
        val pixels = IntArray(nw * nh)
        bmp.getPixels(pixels, 0, nw, 0, 0, nw, nh)

        // Sub-sampling agar cepat: ambil maks ~500k sampel merata.
        val total = pixels.size
        val step = max(1, total / 500_000)
        var rSum = 0.0
        var gSum = 0.0
        var bSum = 0.0
        var count = 0
        var i = 0
        while (i < total) {
            val c = pixels[i]
            rSum += (c shr 16) and 0xff
            gSum += (c shr 8) and 0xff
            bSum += c and 0xff
            count++
            i += step
        }
        if (count == 0) return floatArrayOf(1f, 1f, 1f)

        val rAvg = rSum / count
        val gAvg = gSum / count
        val bAvg = bSum / count
        val target = (rAvg + gAvg + bAvg) / 3.0

        // Clamp supaya tidak over-boost.
        fun gain(avg: Double): Float =
            if (avg <= 0.0) 1f else (target / avg).toFloat().coerceIn(0.85f, 1.2f)

        return floatArrayOf(gain(rAvg), gain(gAvg), gain(bAvg))
    }

    private fun applyWhiteBalance(bmp: Bitmap, gains: FloatArray): Bitmap {
        if (gains[0] == 1f && gains[1] == 1f && gains[2] == 1f) return bmp
        val w = bmp.width
        val h = bmp.height
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)
        val gr = gains[0]; val gg = gains[1]; val gb = gains[2]
        for (idx in pixels.indices) {
            val c = pixels[idx]
            val r = ((c shr 16) and 0xff)
            val g = ((c shr 8) and 0xff)
            val b = (c and 0xff)
            val nr = (r * gr).toInt().coerceIn(0, 255)
            val ng = (g * gg).toInt().coerceIn(0, 255)
            val nb = (b * gb).toInt().coerceIn(0, 255)
            pixels[idx] = (0xff shl 24) or (nr shl 16) or (ng shl 8) or nb
        }
        return bmp.apply { setPixels(pixels, 0, w, 0, 0, w, h) }
    }

    // ---- Bilinear resize ---------------------------------------------------

    private fun resizeBilinear(src: Bitmap, target: Pair<Int, Int>): Bitmap {
        val sw = src.width
        val sh = src.height
        val dw = target.first
        val dh = target.second
        if (sw == dw && sh == dh) return src.copy(Bitmap.Config.ARGB_8888, false)

        val srcPixels = IntArray(sw * sh)
        src.getPixels(srcPixels, 0, sw, 0, 0, sw, sh)

        val out = Bitmap.createBitmap(dw, dh, Bitmap.Config.ARGB_8888)
        val dstPixels = IntArray(dw * dh)

        val xRatio = sw.toDouble() / dw
        val yRatio = sh.toDouble() / dh

        var dst = 0
        for (y in 0 until dh) {
            val sy = y * yRatio
            val y0 = sy.toInt().coerceIn(0, sh - 1)
            val y1 = (y0 + 1).coerceIn(0, sh - 1)
            val fy = (sy - y0).toFloat()
            val yOff0 = y0 * sw
            val yOff1 = y1 * sw
            for (x in 0 until dw) {
                val sx = x * xRatio
                val x0 = sx.toInt().coerceIn(0, sw - 1)
                val x1 = (x0 + 1).coerceIn(0, sw - 1)
                val fx = (sx - x0).toFloat()

                val p00 = srcPixels[yOff0 + x0]
                val p01 = srcPixels[yOff0 + x1]
                val p10 = srcPixels[yOff1 + x0]
                val p11 = srcPixels[yOff1 + x1]

                val r = bilinearChannel((p00 shr 16) and 0xff, (p01 shr 16) and 0xff,
                    (p10 shr 16) and 0xff, (p11 shr 16) and 0xff, fx, fy)
                val g = bilinearChannel((p00 shr 8) and 0xff, (p01 shr 8) and 0xff,
                    (p10 shr 8) and 0xff, (p11 shr 8) and 0xff, fx, fy)
                val b = bilinearChannel(p00 and 0xff, p01 and 0xff,
                    p10 and 0xff, p11 and 0xff, fx, fy)

                dstPixels[dst++] = (0xff shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        out.setPixels(dstPixels, 0, dw, 0, 0, dw, dh)
        return out
    }

    private fun bilinearChannel(a: Int, b: Int, c: Int, d: Int, fx: Float, fy: Float): Int {
        val top = a + (b - a) * fx
        val bottom = c + (d - c) * fx
        val v = top + (bottom - top) * fy
        return v.toInt().coerceIn(0, 255)
    }

    // ---- Unsharp mask -------------------------------------------------------

    private fun unsharpMask(src: Bitmap, radius: Int, amount: Float): Bitmap {
        val w = src.width
        val h = src.height
        val srcPixels = IntArray(w * h)
        src.getPixels(srcPixels, 0, w, 0, 0, w, h)

        val blur = boxBlur(srcPixels, w, h, radius)

        val out = src.copy(Bitmap.Config.ARGB_8888, false)
        val dstPixels = IntArray(w * h)
        for (i in srcPixels.indices) {
            val c = srcPixels[i]
            val cB = blur[i]
            val r = ((c shr 16) and 0xff)
            val g = ((c shr 8) and 0xff)
            val b = (c and 0xff)
            val rb = (cB shr 16) and 0xff
            val gb = (cB shr 8) and 0xff
            val bb = cB and 0xff
            val nr = (r + amount * (r - rb)).toInt().coerceIn(0, 255)
            val ng = (g + amount * (g - gb)).toInt().coerceIn(0, 255)
            val nb = (b + amount * (b - bb)).toInt().coerceIn(0, 255)
            dstPixels[i] = (0xff shl 24) or (nr shl 16) or (ng shl 8) or nb
        }
        out.setPixels(dstPixels, 0, w, 0, 0, w, h)
        return out
    }

    /**
     * Box blur dua-pass (horizontal lalu vertikal) dengan sliding window.
     * Window diinisialisasi penuh untuk piksel pertama, lalu digeser per kolom/baris.
     */
    private fun boxBlur(pixels: IntArray, w: Int, h: Int, radius: Int): IntArray {
        val r = radius.coerceAtLeast(1)
        val size = 2 * r + 1
        val sizeL = size.toLong()
        val tmp = IntArray(w * h)
        val out = IntArray(w * h)

        fun pack(rSum: Long, gSum: Long, bSum: Long): Int =
            (0xff shl 24) or
                ((((rSum / sizeL).toInt()) shl 16) and 0x00ff0000) or
                ((((gSum / sizeL).toInt()) shl 8) and 0x0000ff00) or
                ((((bSum / sizeL).toInt())) and 0xff)

        // Horizontal pass
        for (y in 0 until h) {
            val row = y * w
            var rSum = 0L; var gSum = 0L; var bSum = 0L
            // isi window awal berpusat di x=0 (dari -r..r, di-clamp)
            for (x in -r..r) {
                val c = pixels[row + x.coerceIn(0, w - 1)]
                rSum += (c shr 16) and 0xff
                gSum += (c shr 8) and 0xff
                bSum += c and 0xff
            }
            for (x in 0 until w) {
                tmp[row + x] = pack(rSum, gSum, bSum)
                // geser window ke x+1: buang piksel x-r, tambah piksel x+r+1
                val rc = pixels[row + (x - r).coerceIn(0, w - 1)]
                rSum -= (rc shr 16) and 0xff
                gSum -= (rc shr 8) and 0xff
                bSum -= rc and 0xff
                val ac = pixels[row + (x + r + 1).coerceIn(0, w - 1)]
                rSum += (ac shr 16) and 0xff
                gSum += (ac shr 8) and 0xff
                bSum += ac and 0xff
            }
        }

        // Vertical pass
        for (x in 0 until w) {
            var rSum = 0L; var gSum = 0L; var bSum = 0L
            for (y in -r..r) {
                val c = tmp[y.coerceIn(0, h - 1) * w + x]
                rSum += (c shr 16) and 0xff
                gSum += (c shr 8) and 0xff
                bSum += c and 0xff
            }
            for (y in 0 until h) {
                out[y * w + x] = pack(rSum, gSum, bSum)
                val rc = tmp[(y - r).coerceIn(0, h - 1) * w + x]
                rSum -= (rc shr 16) and 0xff
                gSum -= (rc shr 8) and 0xff
                bSum -= rc and 0xff
                val ac = tmp[(y + r + 1).coerceIn(0, h - 1) * w + x]
                rSum += (ac shr 16) and 0xff
                gSum += (ac shr 8) and 0xff
                bSum += ac and 0xff
            }
        }
        return out
    }

    // ---- Saturation & tone --------------------------------------------------

    private fun applySaturation(src: Bitmap, sat: Float): Bitmap {
        if (sat == 1f) return src
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xff
            val g = (c shr 8) and 0xff
            val b = c and 0xff
            val luma = 0.299f * r + 0.587f * g + 0.114f * b
            val nr = (luma + (r - luma) * sat).toInt().coerceIn(0, 255)
            val ng = (luma + (g - luma) * sat).toInt().coerceIn(0, 255)
            val nb = (luma + (b - luma) * sat).toInt().coerceIn(0, 255)
            pixels[i] = (0xff shl 24) or (nr shl 16) or (ng shl 8) or nb
        }
        return src.apply { setPixels(pixels, 0, w, 0, 0, w, h) }
    }

    /** Kurva tone S sederhana: kontras halus di sekitar midtone + soft rolloff highlight/shadow. */
    private fun applyToneCurve(src: Bitmap, contrast: Float): Bitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        // lookup table
        val lut = IntArray(256)
        for (v in 0..255) {
            var x = v / 255f
            x = (0.5f + (x - 0.5f) * contrast).coerceIn(0f, 1f)
            // smoothstep (S-curve) untuk rolloff highlight/shadow
            val y = x * x * (3f - 2f * x)
            lut[v] = (y * 255f).toInt().coerceIn(0, 255)
        }

        for (i in pixels.indices) {
            val c = pixels[i]
            val r = lut[(c shr 16) and 0xff]
            val g = lut[(c shr 8) and 0xff]
            val b = lut[c and 0xff]
            pixels[i] = (0xff shl 24) or (r shl 16) or (g shl 8) or b
        }
        return src.apply { setPixels(pixels, 0, w, 0, 0, w, h) }
    }
}
