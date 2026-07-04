package com.npic.photoandsignscanner.domain.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset

/**
 * The whole non-destructive edit graph for a single [CameraCapture] (PRD §4.5). Every knob
 * the user turns in the Edit screen lives here; the source JPEG on disk is never mutated
 * until Save re-renders it (PRD §5.5).
 *
 * Composition order (PRD §4.5):
 *   1. [rotation.quarterTurnsCw] — coarse orientation
 *   2. [rotation.straightenDegrees] — fine tilt
 *   3. [crop] quad — perspective-corrected rectangle
 *   4. [filter] preset — LUT/tone curve
 *   5. [adjustments] — brightness/contrast/sharpness/saturation/warmth deltas
 *
 * [hasChanges] drives the discard-confirm dialog when the user hits Back.
 */
@Immutable
data class EditState(
    val source: CameraCapture,
    val crop: CropQuad,
    val filter: FilterPreset = FilterPreset.Auto,
    val adjustments: Adjustments = Adjustments.NONE,
    val rotation: RotationSpec = RotationSpec.NONE,
    val aspectLock: AspectLock,
) {
    val mode: CameraMode
        get() = source.mode

    val hasChanges: Boolean
        get() = filter != FilterPreset.Auto ||
                adjustments.hasChanges ||
                rotation.hasChanges ||
                crop != initialCropFor(source, aspectLock)

    /**
     * Effective filter after Auto-routing (PRD §5): Auto → SchoolId for photo, Original for
     * signature (m1682: teachers reported InkBoost darkened faint ink to unreadable —
     * Original is the safer default; InkBoost stays available as an explicit pick).
     * Anything else passes through untouched.
     */
    val effectiveFilter: FilterPreset
        get() = when (filter) {
            FilterPreset.Auto -> when (mode) {
                CameraMode.Photo     -> FilterPreset.SchoolId
                CameraMode.Signature -> FilterPreset.Original
            }
            else -> filter
        }

    companion object {
        /**
         * Seed a fresh EditState from a [CameraCapture]. The initial crop is the guide-box
         * rectangle in image-space (PRD §7.1 step 9 fallback until OpenCV lands in 7b).
         */
        fun seedFrom(capture: CameraCapture): EditState {
            val lock = capture.mode.defaultAspectLock()
            return EditState(
                source     = capture,
                crop       = initialCropFor(capture, lock),
                aspectLock = lock,
            )
        }

        @Suppress("UNUSED_PARAMETER")
        private fun initialCropFor(capture: CameraCapture, lock: AspectLock): CropQuad {
            // Seed from the guide box in image-space coordinates. The overlay's onLayoutSize
            // callback later re-projects into view-space; the shape only depends on the
            // guide-box aspect, which the lock already carries. `lock` is reserved for
            // future aspect-constrained reseeding (Layer 7b), hence the suppress.
            //
            // When guideBoxImageSpace is null we DO NOT know the source dimensions here —
            // CameraCapture only carries the raw path. Fall back to a unit-square quad in
            // NORMALIZED space (0..1) that EditRenderer detects and remaps to full source
            // bounds on first render. This keeps every stage of the render pipeline valid
            // (widths and heights are non-zero → applyPerspectiveCrop produces a real
            // output bitmap → Filter/Adjust/Rotate show the actual photo instead of a
            // 1×1 gray blob resampled up to viewport).
            val r = capture.guideBoxImageSpace ?: return CropQuad(
                tl = Offset(0f,      0f),
                tr = Offset(NORMALIZED, 0f),
                br = Offset(NORMALIZED, NORMALIZED),
                bl = Offset(0f,      NORMALIZED),
            )
            return CropQuad(
                tl = Offset(r.left.toFloat(),  r.top.toFloat()),
                tr = Offset(r.right.toFloat(), r.top.toFloat()),
                br = Offset(r.right.toFloat(), r.bottom.toFloat()),
                bl = Offset(r.left.toFloat(),  r.bottom.toFloat()),
            )
        }

        /**
         * Sentinel value flagging a normalized 0..1 quad. EditRenderer detects this by
         * checking the quad's max ordinate against [NORMALIZED_SENTINEL_THRESHOLD] and
         * remaps to full source bounds before applyCrop runs. Kept as a float rather than
         * a Boolean on CropQuad because CropQuad is a domain-layer value object shared
         * with core.ui and adding a "normalized" flag would leak the render concern.
         */
        const val NORMALIZED = 1f
        const val NORMALIZED_SENTINEL_THRESHOLD = 1.5f
    }
}
