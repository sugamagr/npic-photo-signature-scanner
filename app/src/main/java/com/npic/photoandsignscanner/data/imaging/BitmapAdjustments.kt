package com.npic.photoandsignscanner.data.imaging

import android.graphics.Bitmap
import android.util.Log
import com.npic.photoandsignscanner.domain.model.Adjustments
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.roundToInt

/**
 * Pixel-level implementation of the five Adjust sliders (PRD §4.5, §5). All operations run
 * on the caller's thread and mutate a single working [Bitmap] in place.
 *
 * Channels split by cost:
 *   - **Brightness, Contrast, Saturation, Warmth** — pure linear transforms on RGB; done via
 *     one shared [IntArray] round-trip. Sub-millisecond on the 384px preview downsample the
 *     Adjust tool uses during drag.
 *   - **Sharpness** — unsharp mask (blur + weighted subtract). Inherently Mat-based; only
 *     enters that path when `sharpness != 0`.
 *
 * Slider `-50..+50` values are mapped to physical ranges inside this file so the domain
 * layer stays free of pixel semantics (§5 wording is authoritative; see per-channel KDoc).
 */
class BitmapAdjustments(private val bridge: OpenCvBridge) {

    /**
     * Apply all five channels to [bitmap] in place. Fast-path when [adjustments] equals
     * [Adjustments.NONE]. Returns [bitmap] for call-chaining with [BitmapFilters] and
     * [EditRenderer].
     */
    fun apply(bitmap: Bitmap, adjustments: Adjustments): Bitmap {
        if (!adjustments.hasChanges) return bitmap
        applyLinearChannels(bitmap, adjustments)
        if (adjustments.sharpness != 0) applySharpness(bitmap, adjustments.sharpness)
        return bitmap
    }

    /**
     * One IntArray round-trip that folds Brightness → Contrast → Saturation → Warmth. Order
     * matches the visual model (multiplicative first around mid-gray, then chroma, then
     * per-channel bias). Skipped entirely when all four are zero.
     */
    private fun applyLinearChannels(bitmap: Bitmap, adjustments: Adjustments) {
        val brightness = adjustments.brightness
        val contrast   = adjustments.contrast
        val saturation = adjustments.saturation
        val warmth     = adjustments.warmth
        if (brightness == 0 && contrast == 0 && saturation == 0 && warmth == 0) return

        val brightnessOffset = brightnessOffset(brightness)   // -127..+127
        val contrastScale    = contrastScale(contrast)        // 0.5..1.5, 1.0 at 0
        val saturationScale  = saturationScale(saturation)    // 0.0..2.0, 1.0 at 0
        val (redBias, blueBias) = warmthBias(warmth)          // (-25..+25, +25..-25)

        val width  = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        var i = 0
        val n = pixels.size
        while (i < n) {
            val argb = pixels[i]
            val a = argb ushr 24 and 0xFF
            var r = argb ushr 16 and 0xFF
            var g = argb ushr  8 and 0xFF
            var b = argb         and 0xFF

            if (brightnessOffset != 0) {
                r += brightnessOffset
                g += brightnessOffset
                b += brightnessOffset
            }
            if (contrastScale != 1f) {
                r = ((r - 128) * contrastScale + 128f).toInt()
                g = ((g - 128) * contrastScale + 128f).toInt()
                b = ((b - 128) * contrastScale + 128f).toInt()
            }
            if (saturationScale != 1f) {
                // BT.601 luma — cheap, matches how most consumer cameras derive Y for saturation UIs.
                val luma = (LUMA_R * r + LUMA_G * g + LUMA_B * b).toInt()
                r = (luma + saturationScale * (r - luma)).toInt()
                g = (luma + saturationScale * (g - luma)).toInt()
                b = (luma + saturationScale * (b - luma)).toInt()
            }
            if (redBias != 0)  r += redBias
            if (blueBias != 0) b += blueBias

            pixels[i] = (a shl 24) or
                (r.coerceIn(0, 255) shl 16) or
                (g.coerceIn(0, 255) shl 8) or
                b.coerceIn(0, 255)
            i++
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }

    /**
     * Unsharp mask via OpenCV: `sharpened = bitmap + amount × (bitmap − blur(bitmap))`.
     *
     * Kernel radius fixed at 1.5px (Gaussian σ) — the smallest kernel that meaningfully
     * accentuates edges on the 1600px source without haloing. Amount scales `0..1.5` over
     * the positive half of the slider; negative sharpness clamps to zero (no blur), matching
     * PRD §5 filter behaviors (School ID, Ink Boost, Passport all specify positive amount
     * only). Slider going into `-50..0` is treated as identity so users can dial *back* a
     * filter-baked sharpen without introducing softness.
     */
    private fun applySharpness(bitmap: Bitmap, sharpness: Int) {
        val amount = sharpnessAmount(sharpness)
        if (amount == 0f) return
        if (!bridge.isAvailable()) {
            Log.w(TAG, "sharpness requested but OpenCV unavailable; skipping")
            return
        }

        val src = bridge.toMat(bitmap) ?: return
        val blurred = Mat()
        val sharpened = Mat()
        try {
            Imgproc.GaussianBlur(src, blurred, Size(0.0, 0.0), UNSHARP_SIGMA)
            // dst = src * (1 + amount) + blurred * (−amount) — the closed-form unsharp mask.
            Core.addWeighted(src, 1.0 + amount, blurred, -amount.toDouble(), 0.0, sharpened)
            if (!bridge.toBitmap(sharpened, bitmap)) {
                Log.e(TAG, "sharpness: matToBitmap failed")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "sharpness threw; keeping unsharpened pixels", t)
        } finally {
            src.release()
            blurred.release()
            sharpened.release()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Slider → physical-range mappings (kept here so the domain layer stays clean)
    // ─────────────────────────────────────────────────────────────────────────

    private fun brightnessOffset(v: Int): Int =
        (v.coerceIn(Adjustments.MIN, Adjustments.MAX) * BRIGHTNESS_SCALE).roundToInt()

    private fun contrastScale(v: Int): Float =
        1f + (v.coerceIn(Adjustments.MIN, Adjustments.MAX) / Adjustments.MAX.toFloat()) * CONTRAST_HALF_RANGE

    private fun saturationScale(v: Int): Float =
        1f + (v.coerceIn(Adjustments.MIN, Adjustments.MAX) / Adjustments.MAX.toFloat()) * SATURATION_HALF_RANGE

    private fun warmthBias(v: Int): Pair<Int, Int> {
        val clamped = v.coerceIn(Adjustments.MIN, Adjustments.MAX)
        val red = (clamped / Adjustments.MAX.toFloat() * WARMTH_HALF_RANGE).roundToInt()
        return red to -red
    }

    private fun sharpnessAmount(v: Int): Float =
        if (v <= 0) 0f
        else (v / Adjustments.MAX.toFloat()) * SHARPNESS_MAX_AMOUNT

    private companion object {
        const val TAG = "BitmapAdjustments"

        // Slider mapping constants. Half-range means the value at ±MAX.
        const val BRIGHTNESS_SCALE     = 127f / 50f    // ±50 → ±127
        const val CONTRAST_HALF_RANGE  = 0.5f          // scale 0.5..1.5
        const val SATURATION_HALF_RANGE = 1.0f         // scale 0.0..2.0
        const val WARMTH_HALF_RANGE    = 25f           // ±25 R bias
        const val SHARPNESS_MAX_AMOUNT = 1.5f          // unsharp amount at +50

        // BT.601 luma coefficients — used by the saturation transform above.
        const val LUMA_R = 0.299f
        const val LUMA_G = 0.587f
        const val LUMA_B = 0.114f

        // Unsharp mask Gaussian σ in pixels. 1.5 covers a 3-tap kernel effectively.
        const val UNSHARP_SIGMA = 1.5
    }
}
