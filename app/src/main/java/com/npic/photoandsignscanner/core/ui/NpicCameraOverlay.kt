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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.npic.photoandsignscanner.core.theme.NpicColors
import com.npic.photoandsignscanner.core.theme.NpicTheme

/**
 * Static camera guide-box overlay. Draws a black 60% scrim across the whole preview area
 * with a rectangular cut-out matching the mode's aspect (3:4 photo, 3:1 signature).
 *
 * Per DESIGN §6.16, framing is indicated by four Saffron corner brackets (Halide/ProCam
 * style) — 24dp arms × 3dp stroke with rounded caps — NOT a full-perimeter stroke. This
 * keeps the guide unobtrusive at the edges while still telegraphing the crop.
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

            // Saffron corner brackets around the cut-out (DESIGN §6.16).
            drawCornerBrackets(boxRect)
        }
    }
}

/**
 * Draws four Saffron L-shaped brackets at the corners of [rect]. Each bracket has a 24dp
 * arm along both axes with a 3dp stroke and rounded caps. Per DESIGN §6.16. The [hDir] and
 * [vDir] fields on [Bracket] are ±1 and encode which direction each arm extends from the
 * anchor corner.
 */
private fun DrawScope.drawCornerBrackets(rect: Rect) {
    val armPx    = 24.dp.toPx()
    val strokePx = 3.dp.toPx()
    val color    = NpicColors.Saffron

    val brackets = listOf(
        Bracket(anchor = Offset(rect.left,  rect.top),    hDir = +1f, vDir = +1f),
        Bracket(anchor = Offset(rect.right, rect.top),    hDir = -1f, vDir = +1f),
        Bracket(anchor = Offset(rect.right, rect.bottom), hDir = -1f, vDir = -1f),
        Bracket(anchor = Offset(rect.left,  rect.bottom), hDir = +1f, vDir = -1f),
    )
    for (b in brackets) {
        drawLine(
            color = color,
            start = b.anchor,
            end   = Offset(b.anchor.x + b.hDir * armPx, b.anchor.y),
            strokeWidth = strokePx,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = b.anchor,
            end   = Offset(b.anchor.x, b.anchor.y + b.vDir * armPx),
            strokeWidth = strokePx,
            cap = StrokeCap.Round,
        )
    }
}

private data class Bracket(val anchor: Offset, val hDir: Float, val vDir: Float)

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
