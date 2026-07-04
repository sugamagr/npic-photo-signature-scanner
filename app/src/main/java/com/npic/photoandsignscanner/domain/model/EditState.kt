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
     * Effective filter after Auto-routing (PRD §5): Auto → SchoolId for photo, InkBoost for
     * signature. Anything else passes through untouched.
     */
    val effectiveFilter: FilterPreset
        get() = when (filter) {
            FilterPreset.Auto -> when (mode) {
                CameraMode.Photo     -> FilterPreset.SchoolId
                CameraMode.Signature -> FilterPreset.InkBoost
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
            // When guideBoxImageSpace is null (overlay hadn't laid out at capture time), we
            // seed a degenerate zero-quad — detection replaces it with the full-image
            // fallback quad as soon as the decoded bitmap is available (EditViewModel).
            val r = capture.guideBoxImageSpace ?: return CropQuad(
                tl = Offset.Zero, tr = Offset.Zero, br = Offset.Zero, bl = Offset.Zero,
            )
            return CropQuad(
                tl = Offset(r.left.toFloat(),  r.top.toFloat()),
                tr = Offset(r.right.toFloat(), r.top.toFloat()),
                br = Offset(r.right.toFloat(), r.bottom.toFloat()),
                bl = Offset(r.left.toFloat(),  r.bottom.toFloat()),
            )
        }
    }
}
