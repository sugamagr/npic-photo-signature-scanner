package com.npic.photoandsignscanner.data.imaging

import android.graphics.Bitmap
import android.util.Log
import com.npic.photoandsignscanner.domain.model.Adjustments
import com.npic.photoandsignscanner.domain.model.FilterPreset
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * Baked filter presets from PRD §5. Each preset is a deterministic image transform applied
 * BEFORE the Adjust sliders (composition order: rotation → straighten → crop → **filter** →
 * adjust; see PRD §4.5).
 *
 * ### Preset routing
 * - [FilterPreset.Auto] and [FilterPreset.Original] are pass-through here. `Auto` resolves
 *   upstream via [com.npic.photoandsignscanner.domain.model.EditState.effectiveFilter] so
 *   the concrete preset (SchoolId for photo, InkBoost for signature) is what actually reaches
 *   this class. The defensive pass-through covers preview strips that render literal `Auto`.
 * - Every other preset chains through [BitmapAdjustments] for the linear channels (Brightness /
 *   Contrast / Saturation / Warmth) and adds preset-specific sharpen or Mat-based work on top.
 *
 * ### Sharpen
 * The Adjust slider's Sharpness routes through `BitmapAdjustments.applySharpness` with
 * `UNSHARP_SIGMA = 1.5`. Filter presets that specify a different radius (`r`) or amount (`a`)
 * per PRD §5 use [applyUnsharp] directly with those parameters.
 */
class BitmapFilters(
    private val bridge: OpenCvBridge,
    private val adjustments: BitmapAdjustments,
) {

    /**
     * Apply the baked transform for [preset] to [bitmap] in place. Returns [bitmap] for
     * call-chaining with [BitmapAdjustments].
     */
    fun apply(bitmap: Bitmap, preset: FilterPreset): Bitmap = when (preset) {
        FilterPreset.Auto,
        FilterPreset.Original -> bitmap
        FilterPreset.ColorBoost -> applyColorBoost(bitmap)
        FilterPreset.DocumentBw -> applyDocumentBw(bitmap)
        FilterPreset.Passport -> applyPassport(bitmap)
        FilterPreset.SchoolId -> applySchoolId(bitmap)
        FilterPreset.FadedRescue -> applyFadedRescue(bitmap)
        FilterPreset.InkBoost -> applyInkBoost(bitmap)
    }

    // ------------------------------------------------------------------ presets

    /** Color Boost — Sat +15, Contrast +10 (PRD §5). Skin-tone preserve is a Layer 7c polish. */
    private fun applyColorBoost(bitmap: Bitmap): Bitmap =
        adjustments.apply(bitmap, Adjustments(contrast = 10, saturation = 15))

    /**
     * Document B&W — grayscale + adaptive threshold + background whitening (PRD §5).
     *
     * Adaptive threshold with a large block size (`BW_BLOCK_SIZE`) hugs local contrast so
     * folded paper and shadow gradients don't wash pixels black; the resulting binary is
     * blended back against white to produce a clean scan look while keeping text crisp.
     */
    private fun applyDocumentBw(bitmap: Bitmap): Bitmap {
        if (!bridge.isAvailable()) {
            Log.w(TAG, "OpenCV unavailable; Document B&W pass-through.")
            return bitmap
        }
        val src = bridge.toMat(bitmap) ?: return bitmap
        val gray = Mat()
        val binary = Mat()
        val whitened = Mat()
        try {
            Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.adaptiveThreshold(
                gray,
                binary,
                255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY,
                BW_BLOCK_SIZE,
                BW_CONSTANT,
            )
            // Background whitening: blend binary (dominant on paper) with grayscale detail
            // so glyph edges keep antialiasing rather than looking like a fax.
            Core.addWeighted(binary, WHITEN_BIN_WEIGHT, gray, WHITEN_GRAY_WEIGHT, 0.0, whitened)
            Imgproc.cvtColor(whitened, src, Imgproc.COLOR_GRAY2RGBA)
            bridge.toBitmap(src, bitmap)
        } catch (t: Throwable) {
            Log.e(TAG, "Document B&W pipeline failed: ${t.message}", t)
        } finally {
            src.release(); gray.release(); binary.release(); whitened.release()
        }
        return bitmap
    }

    /**
     * Passport — Warmth +5, Contrast +10, Sat −5, unsharp mask r=1.2 a=0.4 (PRD §5).
     * Unsharp is applied FIRST so subsequent contrast/saturation don't over-crisp edges.
     */
    private fun applyPassport(bitmap: Bitmap): Bitmap {
        applyUnsharp(bitmap, sigma = PASSPORT_SIGMA, amount = PASSPORT_AMOUNT)
        return adjustments.apply(
            bitmap,
            Adjustments(contrast = 10, saturation = -5, warmth = 5),
        )
    }

    /**
     * School ID (photo default) — neutral WB (identity — auto-WB deferred), Contrast +15,
     * Sharpen +20 (unsharp r=1.0 a=0.6), Sat +5 (PRD §5).
     */
    private fun applySchoolId(bitmap: Bitmap): Bitmap {
        applyUnsharp(bitmap, sigma = SCHOOL_ID_SIGMA, amount = SCHOOL_ID_AMOUNT)
        return adjustments.apply(
            bitmap,
            Adjustments(contrast = 15, saturation = 5),
        )
    }

    /**
     * Faded Print Rescue — Contrast +25, shadow lift +10, Sharpen +15 (PRD §5).
     *
     * Shadow lift = per-pixel LUT lifting the low quartile while leaving highlights untouched,
     * applied BEFORE contrast so the lifted mid-tones don't get crushed.
     */
    private fun applyFadedRescue(bitmap: Bitmap): Bitmap {
        applyShadowLift(bitmap, amount = FADED_SHADOW_LIFT)
        applyUnsharp(bitmap, sigma = FADED_SIGMA, amount = FADED_AMOUNT)
        return adjustments.apply(bitmap, Adjustments(contrast = 25))
    }

    /**
     * Ink Boost (signature default) — grayscale + adaptive threshold + Contrast +40 +
     * Sharpen +25 (PRD §5). Aggressive on purpose: dry/faded ballpoint recovers.
     */
    private fun applyInkBoost(bitmap: Bitmap): Bitmap {
        if (!bridge.isAvailable()) {
            Log.w(TAG, "OpenCV unavailable; Ink Boost degrading to Contrast+Sharpen only.")
            applyUnsharp(bitmap, sigma = INK_SIGMA, amount = INK_AMOUNT)
            return adjustments.apply(bitmap, Adjustments(contrast = 40))
        }
        val src = bridge.toMat(bitmap) ?: return bitmap
        val gray = Mat()
        val binary = Mat()
        try {
            Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.adaptiveThreshold(
                gray,
                binary,
                255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY,
                INK_BLOCK_SIZE,
                INK_CONSTANT,
            )
            Imgproc.cvtColor(binary, src, Imgproc.COLOR_GRAY2RGBA)
            bridge.toBitmap(src, bitmap)
        } catch (t: Throwable) {
            Log.e(TAG, "Ink Boost pipeline failed: ${t.message}", t)
        } finally {
            src.release(); gray.release(); binary.release()
        }
        applyUnsharp(bitmap, sigma = INK_SIGMA, amount = INK_AMOUNT)
        return adjustments.apply(bitmap, Adjustments(contrast = 40))
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Unsharp mask with caller-supplied [sigma] (Gaussian radius proxy) and [amount] (blend
     * weight). Formula matches [BitmapAdjustments.applySharpness]:
     * `out = src * (1 + amount) + blur(src) * (-amount)`. No-op when OpenCV unavailable.
     */
    private fun applyUnsharp(bitmap: Bitmap, sigma: Double, amount: Float) {
        if (amount == 0f) return
        if (!bridge.isAvailable()) {
            Log.w(TAG, "OpenCV unavailable; unsharp mask skipped.")
            return
        }
        val src = bridge.toMat(bitmap) ?: return
        val blurred = Mat()
        val sharpened = Mat()
        try {
            Imgproc.GaussianBlur(src, blurred, Size(0.0, 0.0), sigma)
            Core.addWeighted(src, (1f + amount).toDouble(), blurred, (-amount).toDouble(), 0.0, sharpened)
            bridge.toBitmap(sharpened, bitmap)
        } catch (t: Throwable) {
            Log.e(TAG, "Unsharp mask failed: ${t.message}", t)
        } finally {
            src.release(); blurred.release(); sharpened.release()
        }
    }

    /**
     * Shadow lift via a piecewise LUT: pixels below [SHADOW_KNEE] are boosted by [amount]
     * scaled to the 0..255 range, tapering to zero at the knee so highlights stay untouched.
     *
     * Done in-place on the caller's bitmap through a single `getPixels/setPixels` pass so it
     * composes cheaply with Faded Print Rescue's downstream contrast bump.
     */
    private fun applyShadowLift(bitmap: Bitmap, amount: Int) {
        if (amount == 0) return
        val boost = (amount / Adjustments.MAX.toFloat() * SHADOW_LIFT_MAX).coerceAtLeast(0f)
        val lut = IntArray(256) { v ->
            val lift = if (v < SHADOW_KNEE) {
                (boost * (1f - v / SHADOW_KNEE.toFloat())).toInt()
            } else {
                0
            }
            (v + lift).coerceIn(0, 255)
        }
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        var i = 0
        val n = pixels.size
        while (i < n) {
            val argb = pixels[i]
            val a = argb ushr ALPHA_SHIFT and CHANNEL_MASK
            val r = lut[argb ushr RED_SHIFT and CHANNEL_MASK]
            val g = lut[argb ushr GREEN_SHIFT and CHANNEL_MASK]
            val b = lut[argb and CHANNEL_MASK]
            pixels[i] =
                (a shl ALPHA_SHIFT) or (r shl RED_SHIFT) or (g shl GREEN_SHIFT) or b
            i++
        }
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
    }

    private companion object {
        const val TAG = "BitmapFilters"

        // Document B&W
        const val BW_BLOCK_SIZE = 31
        const val BW_CONSTANT = 10.0
        const val WHITEN_BIN_WEIGHT = 0.6
        const val WHITEN_GRAY_WEIGHT = 0.4

        // Passport (PRD §5: unsharp r=1.2 a=0.4)
        const val PASSPORT_SIGMA = 1.2
        const val PASSPORT_AMOUNT = 0.4f

        // School ID (PRD §5: unsharp r=1.0 a=0.6)
        const val SCHOOL_ID_SIGMA = 1.0
        const val SCHOOL_ID_AMOUNT = 0.6f

        // Faded Print Rescue (PRD §5: Sharpen +15 → 15/50 × 1.5 amount scale = 0.45)
        const val FADED_SHADOW_LIFT = 10
        const val FADED_SIGMA = 1.2
        const val FADED_AMOUNT = 0.45f

        // Ink Boost (PRD §5: Sharpen +25 → 25/50 × 1.5 amount scale = 0.75)
        const val INK_BLOCK_SIZE = 41
        const val INK_CONSTANT = 12.0
        const val INK_SIGMA = 1.0
        const val INK_AMOUNT = 0.75f

        // Shadow lift knee — pixels darker than this get lifted; brighter pixels untouched.
        const val SHADOW_KNEE = 96
        const val SHADOW_LIFT_MAX = 40f

        // ARGB channel arithmetic
        const val ALPHA_SHIFT = 24
        const val RED_SHIFT = 16
        const val GREEN_SHIFT = 8
        const val CHANNEL_MASK = 0xFF
    }
}
