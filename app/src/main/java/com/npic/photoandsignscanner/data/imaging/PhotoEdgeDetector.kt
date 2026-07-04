package com.npic.photoandsignscanner.data.imaging

import android.graphics.Bitmap
import android.util.Log
import com.npic.photoandsignscanner.domain.model.DetectedCrop
import com.npic.photoandsignscanner.domain.model.ImagePoint
import com.npic.photoandsignscanner.domain.model.RectI
import com.npic.photoandsignscanner.domain.usecase.DetectPhotoEdges
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * Photo pipeline — edge detection. Implements PRD §7.1 verbatim.
 *
 * Given the raw camera bitmap and the on-screen guide-box mapped to image space, produces a
 * 4-corner quad ready for perspective correction (which runs later, at Save time — PRD §7.3).
 *
 * All computation runs on [Dispatchers.Default]. Never throws — returns a
 * `DetectedCrop.Quad(origin = Skipped)` built from the guide box when OpenCV is unavailable,
 * and `origin = GuideBoxFallback` when the pipeline runs but no candidate scores above the
 * PRD §7.1 step-9 threshold.
 */
class PhotoEdgeDetector(private val bridge: OpenCvBridge) : DetectPhotoEdges {

    override suspend fun invoke(source: Bitmap, guideBoxImageSpace: RectI): DetectedCrop.Quad =
        withContext(Dispatchers.Default) {
            val fallback = guideBoxImageSpace.toQuad(DetectedCrop.Origin.Skipped, 0f)
            val rgba = bridge.toMat(source) ?: return@withContext fallback

            try {
                val best = runPipeline(rgba, guideBoxImageSpace)
                    ?: return@withContext guideBoxImageSpace.toQuad(
                        origin = DetectedCrop.Origin.GuideBoxFallback,
                        confidence = 0f,
                    )
                best
            } catch (t: Throwable) {
                Log.e(TAG, "photo edge pipeline failed; using guide-box fallback", t)
                guideBoxImageSpace.toQuad(DetectedCrop.Origin.GuideBoxFallback, 0f)
            } finally {
                rgba.release()
            }
        }

    /**
     * PRD §7.1 steps 1–9. Returns null when no candidate scores above [SCORE_THRESHOLD].
     */
    private fun runPipeline(rgba: Mat, guideBox: RectI): DetectedCrop.Quad? {
        val gray = Mat()
        Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)

        val denoised = Mat()
        Imgproc.bilateralFilter(gray, denoised, 9, 75.0, 75.0)
        gray.release()

        val median = medianGray(denoised)
        val lo = (0.66 * median).coerceIn(0.0, 255.0)
        val hi = (1.33 * median).coerceIn(0.0, 255.0)
        val edges = Mat()
        Imgproc.Canny(denoised, edges, lo, hi)
        denoised.release()

        val closed = Mat()
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
        Imgproc.morphologyEx(edges, closed, Imgproc.MORPH_CLOSE, kernel)
        edges.release()
        kernel.release()

        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(
            closed,
            contours,
            hierarchy,
            Imgproc.RETR_EXTERNAL,
            Imgproc.CHAIN_APPROX_SIMPLE,
        )
        hierarchy.release()
        closed.release()

        val imageArea = rgba.width().toDouble() * rgba.height().toDouble()
        val minArea = imageArea * 0.05
        val maxArea = imageArea * 0.95

        val candidates = collectCandidates(contours, minArea, maxArea)
        val maxCandidateArea = candidates.maxOfOrNull { it.area } ?: 0.0

        var bestScore = 0f
        var bestQuad: DetectedCrop.Quad? = null
        for (c in candidates) {
            val ordered = orderCorners(c.corners)
            val score = scoreQuad(
                ordered = ordered,
                area = c.area,
                maxArea = maxCandidateArea,
                convex = c.convex,
                guideBox = guideBox,
            )
            if (score > bestScore) {
                bestScore = score
                bestQuad = DetectedCrop.Quad(
                    topLeft     = ordered[0].toImagePoint(),
                    topRight    = ordered[1].toImagePoint(),
                    bottomRight = ordered[2].toImagePoint(),
                    bottomLeft  = ordered[3].toImagePoint(),
                    confidence  = score,
                    origin      = DetectedCrop.Origin.Detected,
                )
            }
        }

        return if (bestScore >= SCORE_THRESHOLD) bestQuad else null
    }

    /**
     * PRD §7.1 steps 6–7. Filters contours by area, approximates each to a polygon at
     * ε=0.02·arcLength, and keeps only 4-vertex results. Returns a list of quad candidates
     * with their contour area and convexity flag pre-computed so [scoreQuad] doesn't have to
     * re-touch the raw contour data.
     */
    private fun collectCandidates(
        contours: List<MatOfPoint>,
        minArea: Double,
        maxArea: Double,
    ): List<Candidate> {
        val out = mutableListOf<Candidate>()
        for (contour in contours) {
            val area = Imgproc.contourArea(contour)
            if (area < minArea || area > maxArea) {
                contour.release()
                continue
            }
            val curve = MatOfPoint2f(*contour.toArray())
            val approx = MatOfPoint2f()
            val epsilon = 0.02 * Imgproc.arcLength(curve, true)
            Imgproc.approxPolyDP(curve, approx, epsilon, true)
            curve.release()
            contour.release()

            val points = approx.toArray()
            approx.release()
            if (points.size != 4) continue

            // Wrap points in a Mat only long enough to check convexity, then release —
            // MatOfPoint owns a native buffer that GC won't reclaim on its own.
            val convexProbe = MatOfPoint(*points)
            val convex = try {
                Imgproc.isContourConvex(convexProbe)
            } finally {
                convexProbe.release()
            }
            out += Candidate(points, area, convex)
        }
        return out
    }

    /**
     * PRD §7.1 step 8 — score in `[0.0, 1.0]`:
     *   • 0.5 × guide-box overlap ratio  (Intersection-over-Union with the guide box)
     *   • 0.3 × aspect proximity to 3:4  (1.0 when aspect exactly 0.75, decays linearly)
     *   • 0.2 × convex-and-area-rank     (convex × normalized area rank)
     */
    private fun scoreQuad(
        ordered: Array<Point>,
        area: Double,
        maxArea: Double,
        convex: Boolean,
        guideBox: RectI,
    ): Float {
        val quadBoxLeft   = ordered.minOf { it.x }.toInt()
        val quadBoxTop    = ordered.minOf { it.y }.toInt()
        val quadBoxRight  = ordered.maxOf { it.x }.toInt()
        val quadBoxBottom = ordered.maxOf { it.y }.toInt()
        val quadRect = RectI(quadBoxLeft, quadBoxTop, quadBoxRight, quadBoxBottom)
        val overlap = iou(quadRect, guideBox).toFloat()

        val quadWidth  = max(1, quadRect.width).toFloat()
        val quadHeight = max(1, quadRect.height).toFloat()
        // Rotation-invariant aspect: fold landscape quads into portrait space (min of
        // w/h and h/w) before comparing to the 3:4 target. Without this, a landscape-
        // oriented photo scores ~0.07 lower purely because its raw aspect is >1.
        val rawAspect = quadWidth / quadHeight
        val aspect = min(rawAspect, 1f / rawAspect)
        val aspectDelta = abs(aspect - 0.75f)
        val aspectScore = 1f - (aspectDelta / 0.75f).coerceAtMost(1f)

        val areaRank = if (maxArea > 0.0) (area / maxArea).toFloat() else 0f
        val rankScore = if (convex) areaRank else areaRank * 0.5f

        return 0.5f * overlap + 0.3f * aspectScore + 0.2f * rankScore
    }

    /**
     * Order corners as TL, TR, BR, BL by sum/difference-of-coordinates trick:
     *   • TL = point with smallest x+y
     *   • BR = point with largest  x+y
     *   • TR = point with largest  x-y
     *   • BL = point with smallest x-y
     * Robust to arbitrary contour vertex order.
     */
    private fun orderCorners(pts: Array<Point>): Array<Point> {
        require(pts.size == 4) { "orderCorners requires 4 points" }
        val sums  = pts.map { it.x + it.y }
        val diffs = pts.map { it.x - it.y }
        val tl = pts[sums.indexOf(sums.min())]
        val br = pts[sums.indexOf(sums.max())]
        val tr = pts[diffs.indexOf(diffs.max())]
        val bl = pts[diffs.indexOf(diffs.min())]
        return arrayOf(tl, tr, br, bl)
    }

    /** Rect-vs-rect intersection over union. Both rects in image-space pixels. */
    private fun iou(a: RectI, b: RectI): Double {
        val interLeft   = max(a.left,   b.left)
        val interTop    = max(a.top,    b.top)
        val interRight  = min(a.right,  b.right)
        val interBottom = min(a.bottom, b.bottom)
        val interW = max(0, interRight - interLeft)
        val interH = max(0, interBottom - interTop)
        val interArea = interW.toDouble() * interH.toDouble()
        val aArea = a.width.toDouble() * a.height.toDouble()
        val bArea = b.width.toDouble() * b.height.toDouble()
        val union = aArea + bArea - interArea
        return if (union <= 0.0) 0.0 else interArea / union
    }

    /**
     * Median of a single-channel grayscale Mat. Computed via 256-bin histogram — O(pixels)
     * with no allocations per pixel. Called once per detection pass.
     */
    private fun medianGray(gray: Mat): Double {
        val hist = IntArray(256)
        val total = gray.rows() * gray.cols()
        val buf = ByteArray(total)
        gray.get(0, 0, buf)
        for (b in buf) {
            hist[b.toInt() and 0xFF]++
        }
        val target = total / 2
        var running = 0
        for (i in 0 until 256) {
            running += hist[i]
            if (running >= target) return i.toDouble()
        }
        return 127.0
    }

    private fun Point.toImagePoint() = ImagePoint(x.toInt(), y.toInt())

    private fun RectI.toQuad(origin: DetectedCrop.Origin, confidence: Float) =
        DetectedCrop.Quad(
            topLeft     = ImagePoint(left,  top),
            topRight    = ImagePoint(right, top),
            bottomRight = ImagePoint(right, bottom),
            bottomLeft  = ImagePoint(left,  bottom),
            confidence  = confidence,
            origin      = origin,
        )

    /**
     * Pipeline-internal candidate. Deliberately NOT a `data class` — Kotlin's synthetic
     * equals on `Array<Point>` would use identity, not value equality, which triggers the
     * kotlinc warning and is meaningless for this type (we never compare candidates).
     */
    private class Candidate(
        val corners: Array<Point>,
        val area: Double,
        val convex: Boolean,
    )

    private companion object {
        const val TAG = "PhotoEdgeDetector"
        /** PRD §7.1 step 9 — "if no quad scores > 0.6, fall back to guide-box coordinates". */
        const val SCORE_THRESHOLD = 0.6f
    }
}
