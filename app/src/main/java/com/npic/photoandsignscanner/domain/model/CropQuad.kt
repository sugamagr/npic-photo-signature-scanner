package com.npic.photoandsignscanner.domain.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset

/**
 * Four corners of an editable crop rectangle, in overlay-local pixel coordinates.
 *
 * Lives in `domain` because [com.npic.photoandsignscanner.domain.model.EditState] carries
 * it as the user's non-destructive crop selection (PRD §4.5) — the shape must survive
 * process death, Room persistence, and Save-time full-resolution re-render without
 * dragging Compose UI into the state graph. The overlay's `CropHandle` sealed interface
 * and drawing logic stay in `core.ui.NpicCropOverlay` because they're view-layer concerns.
 *
 * [Offset] is used as the coordinate type because it's a lightweight geometry primitive
 * (two floats) with no Compose-runtime dependency — same rationale as
 * `kotlinx.datetime.Instant` on [CameraCapture].
 */
@Immutable
data class CropQuad(
    val tl: Offset,
    val tr: Offset,
    val br: Offset,
    val bl: Offset,
) {
    val topMid:    Offset get() = (tl + tr) / 2f
    val rightMid:  Offset get() = (tr + br) / 2f
    val bottomMid: Offset get() = (br + bl) / 2f
    val leftMid:   Offset get() = (bl + tl) / 2f

    fun clampedTo(w: Float, h: Float): CropQuad {
        fun c(o: Offset) = Offset(o.x.coerceIn(0f, w), o.y.coerceIn(0f, h))
        return copy(tl = c(tl), tr = c(tr), br = c(br), bl = c(bl))
    }

    /**
     * Uniformly scale every corner by [factor]. Used to translate a crop selection from
     * one coordinate space to another when the same [EditState] is rendered against a
     * downsampled preview bitmap (`Dispatchers.Default` live preview at 384px long-side)
     * as well as the full-resolution source at commit time. Without this, an OpenCV
     * warpPerspective seeded with source-space quad coordinates on a preview-sized bitmap
     * samples entirely out-of-bounds and fills the output with the border constant — the
     * flat gray blob observed in m1653/m1780.
     */
    fun scaledBy(factor: Float): CropQuad = copy(
        tl = Offset(tl.x * factor, tl.y * factor),
        tr = Offset(tr.x * factor, tr.y * factor),
        br = Offset(br.x * factor, br.y * factor),
        bl = Offset(bl.x * factor, bl.y * factor),
    )

    companion object {
        fun full(width: Float, height: Float): CropQuad = CropQuad(
            tl = Offset(0f, 0f),
            tr = Offset(width, 0f),
            br = Offset(width, height),
            bl = Offset(0f, height),
        )

        fun photo34(width: Float, height: Float): CropQuad {
            val targetAspect = 3f / 4f
            val (w, h) = if (width / height > targetAspect) {
                height * targetAspect to height
            } else {
                width to width / targetAspect
            }
            val x0 = (width - w) / 2f
            val y0 = (height - h) / 2f
            return CropQuad(
                tl = Offset(x0,       y0),
                tr = Offset(x0 + w,   y0),
                br = Offset(x0 + w,   y0 + h),
                bl = Offset(x0,       y0 + h),
            )
        }
    }
}
