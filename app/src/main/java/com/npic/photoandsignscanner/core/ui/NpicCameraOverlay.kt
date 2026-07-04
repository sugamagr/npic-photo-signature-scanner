package com.npic.photoandsignscanner.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.npic.photoandsignscanner.core.theme.NpicColors
import com.npic.photoandsignscanner.core.theme.NpicTheme

/**
 * Static camera guide-box overlay. Draws a black 60% scrim across the whole preview area
 * with a rectangular cut-out matching the mode's aspect (3:4 photo, 3:1 signature). A 2dp
 * Saffron stroke traces the cut-out edge (DESIGN §7).
 *
 * The [aspect] is the ratio `width / height`. The guide box takes 88% of the shorter axis.
 * Center is fixed.
 */
@Composable
fun NpicCameraOverlay(
    aspect: Float,
    modifier: Modifier = Modifier,
    fillFraction: Float = 0.88f,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val s = this.size
            val boxRect = computeGuideBox(canvas = s, aspect = aspect, fill = fillFraction)

            // Scrim outside the cut-out — build a path = full rect − cut-out.
            val outer = Path().apply { addRect(Rect(Offset.Zero, s)) }
            val cut   = Path().apply { addRect(boxRect) }
            val scrim = Path().apply { op(outer, cut, PathOperation.Difference) }
            drawPath(scrim, color = Color.Black.copy(alpha = 0.60f))

            // Saffron stroke around the cut-out.
            drawRect(
                color = NpicColors.Saffron,
                topLeft = boxRect.topLeft,
                size    = boxRect.size,
                style   = Stroke(width = 2.dp.toPx()),
            )
        }
    }
}

private fun computeGuideBox(canvas: Size, aspect: Float, fill: Float): Rect {
    // Try width-limited first
    val wIfWidth  = canvas.width * fill
    val hIfWidth  = wIfWidth / aspect
    return if (hIfWidth <= canvas.height * fill) {
        val left = (canvas.width - wIfWidth) / 2f
        val top  = (canvas.height - hIfWidth) / 2f
        Rect(offset = Offset(left, top), size = Size(wIfWidth, hIfWidth))
    } else {
        val hIfHeight = canvas.height * fill
        val wIfHeight = hIfHeight * aspect
        val left = (canvas.width - wIfHeight) / 2f
        val top  = (canvas.height - hIfHeight) / 2f
        Rect(offset = Offset(left, top), size = Size(wIfHeight, hIfHeight))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Previews
// ─────────────────────────────────────────────────────────────────────────────

@Preview(name = "CameraOverlay — 3:4 photo")
@Composable
private fun CameraOverlayPhotoPreview() {
    NpicTheme {
        Box(Modifier.background(NpicColors.Ink).size(320.dp, 480.dp)) {
            NpicCameraOverlay(aspect = 3f / 4f)
        }
    }
}

@Preview(name = "CameraOverlay — 3:1 signature")
@Composable
private fun CameraOverlaySignaturePreview() {
    NpicTheme {
        Box(Modifier.background(NpicColors.Ink).size(320.dp, 480.dp)) {
            NpicCameraOverlay(aspect = 3f / 1f)
        }
    }
}
