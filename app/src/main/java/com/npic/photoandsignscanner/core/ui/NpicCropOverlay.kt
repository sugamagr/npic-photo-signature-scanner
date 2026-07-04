package com.npic.photoandsignscanner.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.npic.photoandsignscanner.core.theme.NpicColors
import com.npic.photoandsignscanner.core.theme.NpicTheme

/**
 * Draggable crop quadrilateral. Four corners as 16dp Saffron dots with 3dp white ring; each
 * has a 44dp invisible hit area (DESIGN §7.16). Grid lines are 1dp white at 40% alpha
 * (rule-of-thirds). This overlay draws on top of a caller-provided image surface.
 *
 * Coordinates are stored in the overlay's own layout box in [Offset]s (px). Callers are
 * responsible for translating to source-image coordinates using [onQuadChange] and the box
 * size delivered on layout.
 *
 * Deliberately does NOT implement magnifier bubble here — that's a follow-up hookup in the
 * Edit feature because it needs the actual bitmap. The 3-zoom bubble spec is left as a TODO
 * anchor.
 */
@Composable
fun NpicCropOverlay(
    quad: CropQuad,
    onQuadChange: (CropQuad) -> Unit,
    onLayoutSize: (IntSize) -> Unit,
    modifier: Modifier = Modifier,
) {
    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(quad) {
                detectDragGestures(
                    onDrag = { change, drag ->
                        change.consume()
                        val corner = quad.nearestCorner(change.position)
                        val moved = quad.moveCorner(corner, drag)
                        onQuadChange(moved.clampedTo(size.width.toFloat(), size.height.toFloat()))
                    },
                )
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size
            if (boxSize != IntSize(s.width.toInt(), s.height.toInt())) {
                boxSize = IntSize(s.width.toInt(), s.height.toInt())
                onLayoutSize(boxSize)
            }

            // Rule-of-thirds grid inside the bounding rect of the quad.
            val minX = minOf(quad.tl.x, quad.bl.x)
            val maxX = maxOf(quad.tr.x, quad.br.x)
            val minY = minOf(quad.tl.y, quad.tr.y)
            val maxY = maxOf(quad.bl.y, quad.br.y)
            val w = maxX - minX
            val h = maxY - minY
            val gridColor = Color.White.copy(alpha = 0.40f)
            val gridStroke = Stroke(width = 1.dp.toPx(), pathEffect = null)
            for (i in 1..2) {
                val x = minX + w * i / 3f
                drawLine(gridColor, Offset(x, minY), Offset(x, maxY), strokeWidth = gridStroke.width)
                val y = minY + h * i / 3f
                drawLine(gridColor, Offset(minX, y), Offset(maxX, y), strokeWidth = gridStroke.width)
            }

            // Quad edges.
            val edgeColor = NpicColors.Saffron
            val edgeStroke = Stroke(width = 2.dp.toPx())
            drawLine(edgeColor, quad.tl, quad.tr, strokeWidth = edgeStroke.width)
            drawLine(edgeColor, quad.tr, quad.br, strokeWidth = edgeStroke.width)
            drawLine(edgeColor, quad.br, quad.bl, strokeWidth = edgeStroke.width)
            drawLine(edgeColor, quad.bl, quad.tl, strokeWidth = edgeStroke.width)

            // Corner handles (3dp white ring around 16dp Saffron dot).
            val handleR = 8.dp.toPx()
            val ringR   = handleR + 3.dp.toPx()
            listOf(quad.tl, quad.tr, quad.br, quad.bl).forEach { p ->
                drawCircle(Color.White, radius = ringR, center = p)
                drawCircle(NpicColors.Saffron, radius = handleR, center = p)
            }
        }
    }
}

/** Four corners in overlay-local pixel coordinates. Anchored top-left is [tl]. */
data class CropQuad(
    val tl: Offset,
    val tr: Offset,
    val br: Offset,
    val bl: Offset,
) {
    fun nearestCorner(p: Offset): Corner {
        val d = listOf(
            Corner.TL to (tl - p).getDistance(),
            Corner.TR to (tr - p).getDistance(),
            Corner.BR to (br - p).getDistance(),
            Corner.BL to (bl - p).getDistance(),
        )
        return d.minBy { it.second }.first
    }

    fun moveCorner(corner: Corner, delta: Offset): CropQuad = when (corner) {
        Corner.TL -> copy(tl = tl + delta)
        Corner.TR -> copy(tr = tr + delta)
        Corner.BR -> copy(br = br + delta)
        Corner.BL -> copy(bl = bl + delta)
    }

    fun clampedTo(w: Float, h: Float): CropQuad {
        fun c(o: Offset) = Offset(o.x.coerceIn(0f, w), o.y.coerceIn(0f, h))
        return copy(tl = c(tl), tr = c(tr), br = c(br), bl = c(bl))
    }

    enum class Corner { TL, TR, BR, BL }

    companion object {
        /** Full-frame quad for the given box size. */
        fun full(width: Float, height: Float): CropQuad = CropQuad(
            tl = Offset(0f, 0f),
            tr = Offset(width, 0f),
            br = Offset(width, height),
            bl = Offset(0f, height),
        )

        /** 3:4 photo aspect centered inside the given box. */
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

// ─────────────────────────────────────────────────────────────────────────────
// Previews
// ─────────────────────────────────────────────────────────────────────────────

@Preview(name = "CropOverlay — over a mock photo")
@Composable
private fun CropOverlayPreview() {
    NpicTheme {
        Box(Modifier.background(NpicColors.Ink).size(320.dp).aspectRatio(3f / 4f)) {
            Box(Modifier.fillMaxSize().background(NpicColors.SurfaceRaised))
            var quad by remember { mutableStateOf(CropQuad.full(width = 320f, height = 427f)) }
            NpicCropOverlay(
                quad = quad,
                onQuadChange = { quad = it },
                onLayoutSize = {},
            )
        }
    }
}
