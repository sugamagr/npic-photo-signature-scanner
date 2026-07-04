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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.npic.photoandsignscanner.core.theme.NpicColors
import com.npic.photoandsignscanner.core.theme.NpicTheme
import com.npic.photoandsignscanner.domain.model.CropQuad

/**
 * Draggable crop quadrilateral. Per DESIGN §6.15:
 *   • Four corner handles: 16dp Saffron dot inside a 3dp white ring; hit area is the
 *     nearest-handle Voronoi cell around each corner, unbounded within the overlay.
 *   • Four edge-midpoint handles: 12dp Saffron dot inside a 2dp white ring; same
 *     nearest-handle routing so effective hit slop always meets 44dp WCAG target size
 *     regardless of how tightly the user zooms in on the crop rectangle.
 *   • Rule-of-thirds grid: 1dp Saffron 30% alpha, visible ONLY while dragging.
 * Magnifier bubble is intentionally deferred to the Edit feature (it needs the bitmap).
 *
 * Edge midpoints translate along their perpendicular axis only — top/bottom moves in Y,
 * left/right moves in X. Corners translate freely in both axes so users can reshape into
 * a non-rectangular quad after edge detection.
 *
 * Coordinates are stored in the overlay's own layout box in [Offset]s (px). Callers translate
 * to source-image coordinates via [onQuadChange] and [onLayoutSize].
 */
@Composable
fun NpicCropOverlay(
    quad: CropQuad,
    onQuadChange: (CropQuad) -> Unit,
    onLayoutSize: (IntSize) -> Unit,
    modifier: Modifier = Modifier,
) {
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var dragging by remember { mutableStateOf(false) }
    var activeHandle by remember { mutableStateOf<CropHandle?>(null) }

    // Latest quad + callback snapshots so the pointerInput block can key on Unit
    // (avoiding gesture-detector restarts on every drag frame) while still reading fresh values.
    val currentQuad = rememberUpdatedState(quad)
    val currentOnQuadChange = rememberUpdatedState(onQuadChange)

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { pos ->
                        activeHandle = currentQuad.value.nearestHandle(pos)
                        dragging = true
                    },
                    onDragEnd = {
                        dragging = false
                        activeHandle = null
                    },
                    onDragCancel = {
                        dragging = false
                        activeHandle = null
                    },
                    onDrag = { change, drag ->
                        change.consume()
                        val q = currentQuad.value
                        val handle = activeHandle ?: q.nearestHandle(change.position)
                        val moved = q.moveHandle(handle, drag)
                        currentOnQuadChange.value(
                            moved.clampedTo(size.width.toFloat(), size.height.toFloat())
                        )
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

            if (dragging) {
                val minX = minOf(quad.tl.x, quad.bl.x)
                val maxX = maxOf(quad.tr.x, quad.br.x)
                val minY = minOf(quad.tl.y, quad.tr.y)
                val maxY = maxOf(quad.bl.y, quad.br.y)
                val w = maxX - minX
                val h = maxY - minY
                val gridColor = NpicColors.Saffron.copy(alpha = 0.30f)
                val gridStrokePx = 1.dp.toPx()
                for (i in 1..2) {
                    val x = minX + w * i / 3f
                    drawLine(gridColor, Offset(x, minY), Offset(x, maxY), strokeWidth = gridStrokePx)
                    val y = minY + h * i / 3f
                    drawLine(gridColor, Offset(minX, y), Offset(maxX, y), strokeWidth = gridStrokePx)
                }
            }

            val edgeColor = NpicColors.Saffron
            val edgeStrokePx = 2.dp.toPx()
            drawLine(edgeColor, quad.tl, quad.tr, strokeWidth = edgeStrokePx)
            drawLine(edgeColor, quad.tr, quad.br, strokeWidth = edgeStrokePx)
            drawLine(edgeColor, quad.br, quad.bl, strokeWidth = edgeStrokePx)
            drawLine(edgeColor, quad.bl, quad.tl, strokeWidth = edgeStrokePx)

            val cornerDotR  = 8.dp.toPx()
            val cornerRingR = cornerDotR + 3.dp.toPx()
            drawHandle(quad.tl, cornerDotR, cornerRingR)
            drawHandle(quad.tr, cornerDotR, cornerRingR)
            drawHandle(quad.br, cornerDotR, cornerRingR)
            drawHandle(quad.bl, cornerDotR, cornerRingR)

            val edgeDotR  = 6.dp.toPx()
            val edgeRingR = edgeDotR + 2.dp.toPx()
            drawHandle(quad.topMid,    edgeDotR, edgeRingR)
            drawHandle(quad.rightMid,  edgeDotR, edgeRingR)
            drawHandle(quad.bottomMid, edgeDotR, edgeRingR)
            drawHandle(quad.leftMid,   edgeDotR, edgeRingR)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHandle(
    center: Offset,
    dotR: Float,
    ringR: Float,
) {
    drawCircle(Color.White,        radius = ringR, center = center)
    drawCircle(NpicColors.Saffron, radius = dotR,  center = center)
}

/**
 * A grabbable point on the crop overlay: one of the four corners or one of the four edge
 * midpoints. Corners translate freely; edges translate along their perpendicular axis only.
 *
 * View-layer only — kept out of `domain/model/CropQuad` because it exists solely to route
 * pointer events on the overlay Canvas.
 */
sealed interface CropHandle {
    enum class Corner : CropHandle { TL, TR, BR, BL }
    enum class Edge   : CropHandle { Top, Right, Bottom, Left }
}

/**
 * Nearest-handle Voronoi routing for a pointer position (DESIGN §6.15). Extension on the
 * domain [CropQuad] so the domain model stays free of view-layer sealed interfaces.
 */
fun CropQuad.nearestHandle(p: Offset): CropHandle {
    var best: CropHandle = CropHandle.Corner.TL
    var bestD = (tl - p).getDistance()
    fun tryHandle(h: CropHandle, d: Float) {
        if (d < bestD) { best = h; bestD = d }
    }
    tryHandle(CropHandle.Corner.TR,     (tr        - p).getDistance())
    tryHandle(CropHandle.Corner.BR,     (br        - p).getDistance())
    tryHandle(CropHandle.Corner.BL,     (bl        - p).getDistance())
    tryHandle(CropHandle.Edge.Top,      (topMid    - p).getDistance())
    tryHandle(CropHandle.Edge.Right,    (rightMid  - p).getDistance())
    tryHandle(CropHandle.Edge.Bottom,   (bottomMid - p).getDistance())
    tryHandle(CropHandle.Edge.Left,     (leftMid   - p).getDistance())
    return best
}

/** Translate a grabbed handle by [delta]. Corner=free, Edge=perpendicular-only. */
fun CropQuad.moveHandle(handle: CropHandle, delta: Offset): CropQuad {
    val dx = Offset(delta.x, 0f)
    val dy = Offset(0f, delta.y)
    return when (handle) {
        CropHandle.Corner.TL   -> copy(tl = tl + delta)
        CropHandle.Corner.TR   -> copy(tr = tr + delta)
        CropHandle.Corner.BR   -> copy(br = br + delta)
        CropHandle.Corner.BL   -> copy(bl = bl + delta)
        CropHandle.Edge.Top    -> copy(tl = tl + dy, tr = tr + dy)
        CropHandle.Edge.Bottom -> copy(br = br + dy, bl = bl + dy)
        CropHandle.Edge.Left   -> copy(bl = bl + dx, tl = tl + dx)
        CropHandle.Edge.Right  -> copy(tr = tr + dx, br = br + dx)
    }
}

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
