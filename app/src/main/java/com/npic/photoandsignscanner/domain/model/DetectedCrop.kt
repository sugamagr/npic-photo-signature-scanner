package com.npic.photoandsignscanner.domain.model

import androidx.compose.runtime.Immutable

/**
 * Result of an image-space auto-crop detection.
 *
 * Two shapes because the two pipelines produce different geometry (PRD §7):
 *
 *  • [Quad] — output of the photo edge-detection pipeline (PRD §7.1). Four corners in
 *    IMAGE-space pixel coordinates ordered TL, TR, BR, BL. Downstream perspective correction
 *    uses these to warp the photo into an upright rectangle (PRD §7.3).
 *  • [Rect] — output of the signature ink-isolation pipeline (PRD §7.2). A single axis-aligned
 *    rectangle in image-space pixel coordinates. Signatures don't need perspective correction
 *    because the paper strip is essentially flat.
 *
 * Every result carries a [confidence] score in `[0.0, 1.0]` and an [origin] tag telling the
 * caller how the geometry was derived. The Photo pipeline's step-9 fallback ("no quad scored
 * above 0.6, use guide box coordinates") is expressed as `origin = GuideBoxFallback` with a
 * confidence of `0.0`. The signature pipeline's "no ink components found" fallback (PRD §7.2
 * paragraph "Fallback") is likewise expressed as `origin = GuideBoxFallback` on a [Rect].
 *
 * The Edit screen watches for `origin = GuideBoxFallback` on a signature capture and shows the
 * "Couldn't detect ink automatically. Adjust the crop manually." banner (PRD §7.2).
 */
@Immutable
sealed interface DetectedCrop {

    /** How this crop was derived. Drives UI messaging and downstream retry decisions. */
    val origin: Origin

    /** `[0.0, 1.0]` — 0 for fallbacks, otherwise the pipeline's own score. */
    val confidence: Float

    /**
     * Photo pipeline output. Four corners in image-space pixel coordinates, ordered
     * TL, TR, BR, BL. Perspective correction is applied at Save time (PRD §7.3), not here.
     */
    @Immutable
    data class Quad(
        val topLeft:     ImagePoint,
        val topRight:    ImagePoint,
        val bottomRight: ImagePoint,
        val bottomLeft:  ImagePoint,
        override val confidence: Float,
        override val origin:     Origin,
    ) : DetectedCrop

    /**
     * Signature pipeline output. Axis-aligned rectangle in image-space pixel coordinates.
     * `[left, top]` is inclusive; `[right, bottom]` is exclusive per common CV convention.
     */
    @Immutable
    data class Rect(
        val left:   Int,
        val top:    Int,
        val right:  Int,
        val bottom: Int,
        override val confidence: Float,
        override val origin:     Origin,
    ) : DetectedCrop {
        val width:  Int get() = right - left
        val height: Int get() = bottom - top
    }

    /** Provenance of a detection result. */
    enum class Origin {
        /** Detector produced a real geometry that scored above its threshold. */
        Detected,

        /** Detector ran but couldn't beat the confidence threshold — using guide-box as fallback. */
        GuideBoxFallback,

        /** Detector was skipped (e.g. OpenCV not initialized). Guide-box used directly. */
        Skipped,
    }
}

/** Integer point in image-space pixel coordinates. Kept framework-free (no android.graphics). */
@Immutable
data class ImagePoint(val x: Int, val y: Int)
