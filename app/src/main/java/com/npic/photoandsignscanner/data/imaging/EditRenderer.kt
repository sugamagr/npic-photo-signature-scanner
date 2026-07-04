package com.npic.photoandsignscanner.data.imaging

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import com.npic.photoandsignscanner.core.ui.CropQuad
import com.npic.photoandsignscanner.domain.model.CameraMode
import com.npic.photoandsignscanner.domain.model.EditState
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Composition orchestrator (PRD §4.5). Applies the edit graph to [source] in strict order:
 *
 *   1. **Rotation** — `rotation.quarterTurnsCw × 90°` via a `Bitmap.createBitmap` + [Matrix]
 *      copy. Cheap; runs before anything else so subsequent stages see upright pixels.
 *   2. **Straighten** — small `rotation.straightenDegrees` tilt via OpenCV `warpAffine`
 *      around the image center. Skipped when zero.
 *   3. **Crop** — Photo: `warpPerspective` from the [CropQuad] to an axis-aligned rectangle
 *      sized to the max side lengths of the quad (PRD §7.3). Signature: plain axis-aligned
 *      sub-rect derived from the quad's bounding box (PRD §7.2 output is already rectangular).
 *   4. **Filter** → [BitmapFilters.apply] with the routed [EditState.effectiveFilter].
 *   5. **Adjust** → [BitmapAdjustments.apply] with [EditState.adjustments].
 *
 * ### Coordinate space
 * [EditState.crop] lives in **image space** of the SOURCE bitmap (see `EditState.seedFrom`
 * KDoc). Rotation and straighten transform the working bitmap before crop reads coordinates,
 * so this class maps the quad through those same transforms as it applies them.
 *
 * ### Non-destructive contract
 * [source] is never mutated. The final rendered [Bitmap] is a fresh allocation.
 */
class EditRenderer(
    private val bridge: OpenCvBridge,
    private val filters: BitmapFilters,
    private val adjustments: BitmapAdjustments,
) {

    /**
     * Render the full edit graph. Returns a NEW bitmap; [source] is left untouched so re-edits
     * from the ViewModel don't accumulate.
     */
    fun render(source: Bitmap, state: EditState): Bitmap {
        val rotated = applyQuarterTurns(source, state.rotation.quarterTurnsCw)
        val straightened = applyStraighten(rotated, state.rotation.straightenDegrees)
        if (straightened !== rotated && rotated !== source) rotated.recycle()

        val mappedQuad = mapQuadThroughRotation(
            state.crop,
            source.width,
            source.height,
            state.rotation.quarterTurnsCw,
        )
        val cropped = applyCrop(straightened, mappedQuad, state.source.mode)
        if (straightened !== cropped && straightened !== source) straightened.recycle()

        filters.apply(cropped, state.effectiveFilter)
        adjustments.apply(cropped, state.adjustments)
        return cropped
    }

    // ------------------------------------------------------------------ rotation

    /** Multiples of 90° via [Matrix] postRotate. Zero-turn returns [source] unchanged. */
    private fun applyQuarterTurns(source: Bitmap, quarterTurns: Int): Bitmap {
        val turns = ((quarterTurns % 4) + 4) % 4
        if (turns == 0) return source
        val matrix = Matrix().apply { postRotate((turns * QUARTER_TURN_DEG).toFloat()) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    /** Small tilt via OpenCV warpAffine. Zero or OpenCV-unavailable returns [source] unchanged. */
    private fun applyStraighten(source: Bitmap, degrees: Float): Bitmap {
        if (abs(degrees) < STRAIGHTEN_EPSILON) return source
        if (!bridge.isAvailable()) {
            Log.w(TAG, "OpenCV unavailable; straighten skipped.")
            return source
        }
        val src = bridge.toMat(source) ?: return source
        val dst = Mat()
        val rotation = Imgproc.getRotationMatrix2D(
            Point(source.width / 2.0, source.height / 2.0),
            degrees.toDouble(),
            1.0,
        )
        return try {
            Imgproc.warpAffine(
                src,
                dst,
                rotation,
                Size(source.width.toDouble(), source.height.toDouble()),
                Imgproc.INTER_LINEAR,
                Core.BORDER_CONSTANT,
                Scalar(0.0, 0.0, 0.0, 0.0),
            )
            val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            if (bridge.toBitmap(dst, out)) out else source
        } catch (t: Throwable) {
            Log.e(TAG, "Straighten failed: ${t.message}", t)
            source
        } finally {
            src.release()
            dst.release()
            rotation.release()
        }
    }

    /**
     * Remap [quad] (image-space of the ORIGINAL source) through the coarse rotation so its
     * coordinates are valid on the rotated bitmap. Straighten is a small perturbation that
     * we intentionally ignore here — the quad follows the coarse orientation, and the fine
     * straighten pre-rotates the entire image so the crop still lands on upright content.
     */
    private fun mapQuadThroughRotation(
        quad: CropQuad,
        srcWidth: Int,
        srcHeight: Int,
        quarterTurns: Int,
    ): CropQuad {
        val turns = ((quarterTurns % 4) + 4) % 4
        if (turns == 0) return quad
        val w = srcWidth.toFloat()
        val h = srcHeight.toFloat()
        val tl = rotatePoint(quad.tl.x, quad.tl.y, w, h, turns)
        val tr = rotatePoint(quad.tr.x, quad.tr.y, w, h, turns)
        val br = rotatePoint(quad.br.x, quad.br.y, w, h, turns)
        val bl = rotatePoint(quad.bl.x, quad.bl.y, w, h, turns)
        return CropQuad(tl = tl, tr = tr, br = br, bl = bl)
    }

    private fun rotatePoint(
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        turns: Int,
    ): androidx.compose.ui.geometry.Offset = when (turns) {
        1 -> androidx.compose.ui.geometry.Offset(h - y, x)
        2 -> androidx.compose.ui.geometry.Offset(w - x, h - y)
        3 -> androidx.compose.ui.geometry.Offset(y, w - x)
        else -> androidx.compose.ui.geometry.Offset(x, y)
    }

    // ------------------------------------------------------------------ crop

    /**
     * Crop dispatch. Photo mode uses perspective correction (PRD §7.3). Signature mode uses
     * a plain sub-rect from the quad's bounding box since PRD §7.2 already produces an
     * axis-aligned result.
     */
    private fun applyCrop(source: Bitmap, quad: CropQuad, mode: CameraMode): Bitmap = when (mode) {
        CameraMode.Photo -> applyPerspectiveCrop(source, quad)
        CameraMode.Signature -> applyRectCrop(source, quad)
    }

    /**
     * Perspective-correct [quad] into an axis-aligned rectangle sized to the maximum side
     * lengths of the quad (PRD §7.3). Falls back to the bounding-box crop when OpenCV is
     * unavailable so Save still produces output on very old devices.
     */
    private fun applyPerspectiveCrop(source: Bitmap, quad: CropQuad): Bitmap {
        if (!bridge.isAvailable()) {
            Log.w(TAG, "OpenCV unavailable; perspective crop degrading to rect crop.")
            return applyRectCrop(source, quad)
        }
        val widthTop = hypot((quad.tr.x - quad.tl.x).toDouble(), (quad.tr.y - quad.tl.y).toDouble())
        val widthBottom = hypot((quad.br.x - quad.bl.x).toDouble(), (quad.br.y - quad.bl.y).toDouble())
        val heightLeft = hypot((quad.bl.x - quad.tl.x).toDouble(), (quad.bl.y - quad.tl.y).toDouble())
        val heightRight = hypot((quad.br.x - quad.tr.x).toDouble(), (quad.br.y - quad.tr.y).toDouble())
        val outWidth = maxOf(widthTop, widthBottom).roundToInt().coerceAtLeast(1)
        val outHeight = maxOf(heightLeft, heightRight).roundToInt().coerceAtLeast(1)

        val src = bridge.toMat(source) ?: return applyRectCrop(source, quad)
        val dst = Mat()
        val srcPts = MatOfPoint2f(
            Point(quad.tl.x.toDouble(), quad.tl.y.toDouble()),
            Point(quad.tr.x.toDouble(), quad.tr.y.toDouble()),
            Point(quad.br.x.toDouble(), quad.br.y.toDouble()),
            Point(quad.bl.x.toDouble(), quad.bl.y.toDouble()),
        )
        val dstPts = MatOfPoint2f(
            Point(0.0, 0.0),
            Point(outWidth - 1.0, 0.0),
            Point(outWidth - 1.0, outHeight - 1.0),
            Point(0.0, outHeight - 1.0),
        )
        val transform = Imgproc.getPerspectiveTransform(srcPts, dstPts)
        return try {
            Imgproc.warpPerspective(
                src,
                dst,
                transform,
                Size(outWidth.toDouble(), outHeight.toDouble()),
                Imgproc.INTER_LINEAR,
            )
            val out = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
            if (bridge.toBitmap(dst, out)) out else applyRectCrop(source, quad)
        } catch (t: Throwable) {
            Log.e(TAG, "Perspective crop failed: ${t.message}", t)
            applyRectCrop(source, quad)
        } finally {
            src.release()
            dst.release()
            srcPts.release()
            dstPts.release()
            transform.release()
        }
    }

    /** Axis-aligned sub-rect from the quad's bounding box, clamped to bitmap bounds. */
    private fun applyRectCrop(source: Bitmap, quad: CropQuad): Bitmap {
        val minX = minOf(quad.tl.x, quad.tr.x, quad.bl.x, quad.br.x)
            .coerceAtLeast(0f)
            .toInt()
        val minY = minOf(quad.tl.y, quad.tr.y, quad.bl.y, quad.br.y)
            .coerceAtLeast(0f)
            .toInt()
        val maxX = maxOf(quad.tl.x, quad.tr.x, quad.bl.x, quad.br.x)
            .coerceAtMost(source.width.toFloat())
            .toInt()
        val maxY = maxOf(quad.tl.y, quad.tr.y, quad.bl.y, quad.br.y)
            .coerceAtMost(source.height.toFloat())
            .toInt()
        val w = (maxX - minX).coerceAtLeast(1)
        val h = (maxY - minY).coerceAtLeast(1)
        return Bitmap.createBitmap(source, minX, minY, w, h)
    }

    private companion object {
        const val TAG = "EditRenderer"
        const val QUARTER_TURN_DEG = 90
        const val STRAIGHTEN_EPSILON = 0.05f
    }
}
