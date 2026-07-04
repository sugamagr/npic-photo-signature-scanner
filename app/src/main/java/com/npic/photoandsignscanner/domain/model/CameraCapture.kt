package com.npic.photoandsignscanner.domain.model

import androidx.compose.runtime.Immutable
import kotlinx.datetime.Instant

/**
 * The output of a successful shutter press: a raw JPEG on disk plus the metadata Edit needs
 * to seed its initial crop.
 *
 * • [rawPath] is a `cache/drafts/{id}.jpg` path — the full-resolution CameraX JPEG. Not the
 *   source-quality file (§5.5) — that's produced on Save.
 * • [mode] tells Edit which pipeline to run (edge detection vs. ink isolation).
 * • [guideBoxImageSpace] is the guide-box rectangle expressed in the RAW-JPEG pixel space
 *   (not preview space), or `null` when the guide-box overlay had not laid out yet at
 *   capture time (rare — user tapped shutter within the first frame). Callers MUST treat
 *   null as "no seed available; use full-image bounds" — never as an empty rect. Edit uses
 *   this as:
 *     - Photo mode: the seed area for OpenCV edge detection, and the fallback quad when no
 *       quad scores above 0.6 (PRD §7.1 step 9).
 *     - Signature mode: the crop applied before the ink-isolation pipeline runs (PRD §7.2
 *       step 1).
 * • [capturedAt] is used later for draft resume prompts and file naming.
 */
@Immutable
data class CameraCapture(
    val rawPath: String,
    val mode: CameraMode,
    val guideBoxImageSpace: RectI?,
    val capturedAt: Instant,
)

/**
 * Integer rectangle in image-pixel space. Kept separate from android.graphics.Rect so the
 * domain layer stays framework-free (unit-testable on the JVM).
 */
@Immutable
data class RectI(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int  get() = right - left
    val height: Int get() = bottom - top
}
