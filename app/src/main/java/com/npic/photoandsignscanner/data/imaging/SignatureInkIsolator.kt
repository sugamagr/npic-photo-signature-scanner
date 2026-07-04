package com.npic.photoandsignscanner.data.imaging

import android.graphics.Bitmap
import android.util.Log
import com.npic.photoandsignscanner.domain.model.DetectedCrop
import com.npic.photoandsignscanner.domain.model.RectI
import com.npic.photoandsignscanner.domain.usecase.DetectSignatureInk
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * Signature pipeline — ink isolation. Implements PRD §7.2 verbatim.
 *
 * Signatures don't have clean rectangular borders, so we skip edge detection entirely and
 * isolate the ink. Input is the raw camera bitmap; [guideBoxImageSpace] is the on-screen
 * signature guide (3:1 landscape) mapped to image space. Output is a bounding box in the
 * ORIGINAL image's coordinates, not the working-area coordinates.
 *
 * All computation runs on [Dispatchers.Default]. Never throws — returns a
 * `DetectedCrop.Rect(origin = Skipped)` set to the guide box when OpenCV is unavailable,
 * and `origin = GuideBoxFallback` when the pipeline runs but no ink survives step 6.
 * `GuideBoxFallback` drives the "Couldn't detect ink automatically" banner (PRD §7.2).
 */
class SignatureInkIsolator(private val bridge: OpenCvBridge) : DetectSignatureInk {

    override suspend fun invoke(source: Bitmap, guideBoxImageSpace: RectI): DetectedCrop.Rect =
        withContext(Dispatchers.Default) {
            val fallbackSkipped = guideBoxImageSpace.toRect(DetectedCrop.Origin.Skipped, 0f)
            val rgba = bridge.toMat(source) ?: return@withContext fallbackSkipped

            try {
                runPipeline(rgba, guideBoxImageSpace)
                    ?: guideBoxImageSpace.toRect(DetectedCrop.Origin.GuideBoxFallback, 0f)
            } catch (t: Throwable) {
                Log.e(TAG, "signature ink pipeline failed; using guide-box fallback", t)
                guideBoxImageSpace.toRect(DetectedCrop.Origin.GuideBoxFallback, 0f)
            } finally {
                rgba.release()
            }
        }

    /**
     * PRD §7.2 steps 1–9. Returns null when no ink components survive step 6.
     */
    private fun runPipeline(rgba: Mat, guideBox: RectI): DetectedCrop.Rect? {
        val clamped = guideBox.clampedTo(imageWidth = rgba.width(), imageHeight = rgba.height())
        if (clamped.width <= 0 || clamped.height <= 0) return null

        // Step 1 — crop to guide-box. This is the "working area" for the rest of the pipeline.
        val working = rgba.submat(clamped.top, clamped.bottom, clamped.left, clamped.right)

        val gray = Mat()
        Imgproc.cvtColor(working, gray, Imgproc.COLOR_RGBA2GRAY)
        working.release()

        val blurred = Mat()
        Imgproc.GaussianBlur(gray, blurred, Size(3.0, 3.0), 0.0)
        gray.release()

        // Step 4 — adaptive threshold. INV so ink becomes non-zero for CC labeling.
        val binary = Mat()
        Imgproc.adaptiveThreshold(
            blurred,
            binary,
            255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
            Imgproc.THRESH_BINARY_INV,
            51,
            10.0,
        )
        blurred.release()

        val dilated = Mat()
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
        Imgproc.dilate(dilated.also { binary.copyTo(it) }, dilated, kernel, Point(-1.0, -1.0), 1)
        kernel.release()
        binary.release()

        // Step 6 — connected components with per-component stats.
        val labels = Mat()
        val stats = Mat()
        val centroids = Mat()
        val n = Imgproc.connectedComponentsWithStats(dilated, labels, stats, centroids)
        dilated.release()
        labels.release()
        centroids.release()

        val workingArea = clamped.width.toDouble() * clamped.height.toDouble()
        val minComponentArea = workingArea * 0.001

        // Union bbox in WORKING-area coords. Skip label 0 (background).
        var unionLeft   = Int.MAX_VALUE
        var unionTop    = Int.MAX_VALUE
        var unionRight  = Int.MIN_VALUE
        var unionBottom = Int.MIN_VALUE
        var surviving = 0

        for (label in 1 until n) {
            val area = stats.get(label, Imgproc.CC_STAT_AREA)[0]
            if (area < minComponentArea) continue

            val x  = stats.get(label, Imgproc.CC_STAT_LEFT)[0].toInt()
            val y  = stats.get(label, Imgproc.CC_STAT_TOP)[0].toInt()
            val w  = stats.get(label, Imgproc.CC_STAT_WIDTH)[0].toInt()
            val h  = stats.get(label, Imgproc.CC_STAT_HEIGHT)[0].toInt()

            unionLeft   = min(unionLeft,   x)
            unionTop    = min(unionTop,    y)
            unionRight  = max(unionRight,  x + w)
            unionBottom = max(unionBottom, y + h)
            surviving++
        }
        stats.release()

        if (surviving == 0) return null

        // Step 7 — 8% padding proportional to bbox dimensions.
        val bboxW = unionRight  - unionLeft
        val bboxH = unionBottom - unionTop
        val padX  = (bboxW * PADDING_FRACTION).roundToInt()
        val padY  = (bboxH * PADDING_FRACTION).roundToInt()

        // Step 8 — clamp to working area.
        val paddedLeft   = max(0,             unionLeft   - padX)
        val paddedTop    = max(0,             unionTop    - padY)
        val paddedRight  = min(clamped.width,  unionRight  + padX)
        val paddedBottom = min(clamped.height, unionBottom + padY)

        // Step 9 — convert back to FULL-image coordinates.
        val confidence = (surviving.toFloat() / MAX_CONFIDENCE_COMPONENTS).coerceIn(0f, 1f)
        return DetectedCrop.Rect(
            left       = clamped.left + paddedLeft,
            top        = clamped.top  + paddedTop,
            right      = clamped.left + paddedRight,
            bottom     = clamped.top  + paddedBottom,
            confidence = confidence,
            origin     = DetectedCrop.Origin.Detected,
        )
    }

    private fun RectI.clampedTo(imageWidth: Int, imageHeight: Int): RectI = RectI(
        left   = left.coerceIn(0, imageWidth),
        top    = top.coerceIn(0, imageHeight),
        right  = right.coerceIn(0, imageWidth),
        bottom = bottom.coerceIn(0, imageHeight),
    )

    private fun RectI.toRect(origin: DetectedCrop.Origin, confidence: Float) =
        DetectedCrop.Rect(
            left       = left,
            top        = top,
            right      = right,
            bottom     = bottom,
            confidence = confidence,
            origin     = origin,
        )

    private companion object {
        const val TAG = "SignatureInkIsolator"
        /** PRD §7.2 step 7 — "Add 8% padding on all sides (proportional to bbox dimensions)". */
        const val PADDING_FRACTION = 0.08
        /** Confidence saturates when ≥ N ink components survive. Chosen empirically — a
         *  full cursive signature typically produces 3–5 components after dilation. */
        const val MAX_CONFIDENCE_COMPONENTS = 5f
    }
}
