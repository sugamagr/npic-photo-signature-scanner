package com.npic.photoandsignscanner.features.camera

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.npic.photoandsignscanner.domain.model.RectI

/**
 * The overlay guide box measured in PREVIEW-space pixels together with the size of the
 * preview canvas that produced it. Passed from [CameraScreen] through to
 * [CameraViewModel.capture] so the VM can compute the correct FILL_CENTER inverse against
 * the actual captured-JPEG dimensions.
 *
 * ### Coordinate space
 *
 * m2475 Bug U: [rect] is measured in the OVERLAY's local canvas coordinates (weighted
 * middle cell of CameraScreen — excludes top + bottom bars), while CameraX's PreviewView
 * fills the ENTIRE screen. Passing overlay-local rect + overlay-local canvas size to the
 * FILL_CENTER inverse projects onto the wrong image pixels — the captured photo appears
 * shrunk and vertically shifted vs. what the user framed. Fix: also carry
 *
 *   - [previewSize]: the FULL PreviewView pixel size (screen-space)
 *   - [previewOffset]: the overlay canvas's top-left position expressed in PreviewView
 *     coordinates (positive Y = below the top bar).
 *
 * [toImageSpace] adds [previewOffset] to every rect corner before running the inverse.
 *
 * Lives in the `features/camera/` module because [Rect] and [Size] are Compose types; the
 * domain layer (which owns [RectI]) stays framework-free.
 */
@Immutable
data class PreviewGuide(
    val rect: Rect,
    val previewSize: Size,
    val previewOffset: Offset = Offset.Zero,
)

/**
 * Map a preview-space [PreviewGuide] onto a raw image-space [RectI] given the captured
 * JPEG's dimensions. Uses [androidx.camera.view.PreviewView.ScaleType.FILL_CENTER]'s
 * inverse: the image is uniformly scaled by `max(previewW/imageW, previewH/imageH)` until
 * both preview axes are covered, then centered — so the excess along the longer axis is
 * cropped off-screen. The inverse un-crops and un-scales.
 *
 * Returns `null` for degenerate inputs (zero-sized preview / zero-sized image) so
 * downstream stays honest about "no seed available; use full-image bounds".
 */
fun PreviewGuide.toImageSpace(imageWidth: Int, imageHeight: Int): RectI? {
    if (previewSize.width <= 0f || previewSize.height <= 0f) return null
    if (imageWidth <= 0 || imageHeight <= 0) return null

    // Scale factor applied to the IMAGE so the preview canvas is fully covered.
    val scale = maxOf(
        previewSize.width / imageWidth.toFloat(),
        previewSize.height / imageHeight.toFloat(),
    )
    // How much of the scaled image is off-screen on each side (centered crop).
    val scaledImageW = imageWidth * scale
    val scaledImageH = imageHeight * scale
    val offsetX = (scaledImageW - previewSize.width) / 2f
    val offsetY = (scaledImageH - previewSize.height) / 2f

    // Inverse mapping: preview point (px,py) → image point ((px + offsetX)/scale, (py + offsetY)/scale).
    // m2475 Bug U: rect corners are OVERLAY-local so we add previewOffset to lift them
    // into PreviewView-local coordinates before inverting the FILL_CENTER scale.
    fun px(x: Float) = ((x + previewOffset.x + offsetX) / scale).toInt().coerceIn(0, imageWidth)
    fun py(y: Float) = ((y + previewOffset.y + offsetY) / scale).toInt().coerceIn(0, imageHeight)

    val left   = px(rect.left)
    val top    = py(rect.top)
    val right  = px(rect.right)
    val bottom = py(rect.bottom)
    // Guard against numerical collapse (e.g. hugely off-screen overlay). Detector wants a
    // non-empty rect or nothing.
    if (right <= left || bottom <= top) return null
    return RectI(left = left, top = top, right = right, bottom = bottom)
}
