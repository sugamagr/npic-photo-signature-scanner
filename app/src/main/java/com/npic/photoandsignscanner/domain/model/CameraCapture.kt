package com.npic.photoandsignscanner.domain.model

import androidx.compose.runtime.Immutable
import kotlinx.datetime.Instant

/**
 * The output of a successful shutter press: a raw JPEG on disk plus the metadata Edit needs
 * to seed its initial crop.
 *
 * • [rawPath] is a `cache/drafts/{id}.jpg` path — the full-resolution CameraX JPEG. Not the
 *   source-quality file (§5.5) — that's produced on Save.
 * • [mode] tells Edit which framing (Photo vs Signature) the capture used.
 * • [guideBoxImageSpace] is the guide-box rectangle expressed in the RAW-JPEG pixel space
 *   (not preview space), or `null` when the guide-box overlay had not laid out yet at
 *   capture time (rare — user tapped shutter within the first frame), or when the source
 *   arrived via Photo Picker / Detail add-media (no camera frame at all). Callers MUST treat
 *   null as "no seed available; use full-image bounds" — never as an empty rect. Edit uses
 *   it as the seed for the initial crop quad on the Crop tab; when null, Edit opens at
 *   full-image bounds via the [EditState] `NORMALIZED_SENTINEL_THRESHOLD` sentinel path.
 *   Auto edge / ink detection was removed per m2154 — the guide box is now purely a
 *   framing hint, no pipeline runs on it.
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
