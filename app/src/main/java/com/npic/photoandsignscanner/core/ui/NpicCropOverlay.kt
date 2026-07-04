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
import com.npic.photoandsignscanner.domain.model.AspectLock
import com.npic.photoandsignscanner.domain.model.CropQuad

/**
 * Draggable crop rectangle. Per user feedback (2024-12), simplified from the eight-handle
 * design to just four corner handles — edge midpoints were more noise than signal for the
 * portrait-photo use case, and aspect-lock enforcement is trivial with corners alone.
 *
 * Per DESIGN §6.15:
 *   • Four corner handles: 16dp Saffron dot inside a 3dp white ring; hit routing uses
 *     nearest-corner Voronoi cells so effective slop always meets 44dp WCAG target.
 *   • Rule-of-thirds grid: 1dp Saffron 30% alpha, visible ONLY while dragging.
 *
 * Aspect lock is honoured on every drag: if [aspectLock] is a [AspectLock.Ratio], the moved
 * corner is projected so the resulting quad matches the ratio, anchored to the diagonally
 * opposite corner. [AspectLock.Free] leaves each corner independent.
 *
 * Magnifier bubble is intentionally deferred to the Edit feature (it needs the bitmap).
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
    aspectLock: AspectLock = AspectLock.Free,
) {
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var dragging by remember { mutableStateOf(false) }
    var activeHandle by remember { mutableStateOf<CropHandle?>(null) }
    var dragMode by remember { mutableStateOf(DragMode.None) }

    val currentQuad = rememberUpdatedState(quad)
    val currentAspectLock = rememberUpdatedState(aspectLock)
    val currentOnQuadChange = rememberUpdatedState(onQuadChange)

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                val cornerHitThresholdPx = 44.dp.toPx()
                detectDragGestures(
                    onDragStart = { pos ->
                        val q = currentQuad.value
                        val nearest = q.nearestCorner(pos)
                        val nearestDist = (q.cornerFor(nearest) - pos).getDistance()
                        dragMode = when {
                            nearestDist <= cornerHitThresholdPx -> DragMode.Corner.also {
                                activeHandle = nearest
                            }
                            q.contains(pos) -> DragMode.Box.also { activeHandle = null }
                            else -> DragMode.None.also { activeHandle = null }
                        }
                        dragging = dragMode != DragMode.None
                    },
                    onDragEnd = {
                        dragging = false
                        activeHandle = null
                        dragMode = DragMode.None
                    },
                    onDragCancel = {
                        dragging = false
                        activeHandle = null
                        dragMode = DragMode.None
                    },
                    onDrag = { change, drag ->
                        if (dragMode == DragMode.None) return@detectDragGestures
                        change.consume()
                        val q = currentQuad.value
                        val moved = when (dragMode) {
                            DragMode.Corner -> {
                                val handle = activeHandle ?: q.nearestCorner(change.position)
                                q.moveCorner(handle, drag, currentAspectLock.value, boxSize)
                            }
                            DragMode.Box -> q.translated(
                                drag,
                                size.width.toFloat(),
                                size.height.toFloat(),
                            )
                            DragMode.None -> q
                        }
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
 * A grabbable corner on the crop overlay. Kept out of `domain/model/CropQuad` because it
 * exists solely to route pointer events on the overlay Canvas.
 */
enum class CropHandle { TL, TR, BR, BL }

/**
 * Classifies what an in-flight drag is manipulating. Set in `onDragStart` and held for the
 * lifetime of the drag so `onDrag` can route each pointer event to the correct math path
 * without recomputing hit tests every frame.
 *
 * Per user m1679: dragging INSIDE the quad (but outside the 44dp corner hit region)
 * translates the whole box; dragging near a corner resizes it. `None` means the drag started
 * outside the quad — swallowed rather than routed to some fallback because pointer input on
 * the surrounding overlay canvas should be inert.
 */
enum class DragMode { Corner, Box, None }

/**
 * Fast projection from a corner enum to the [Offset] it names. Kept as an extension so the
 * routing code in the overlay stays declarative (`q.cornerFor(handle)`) without a temporary
 * when-block at each callsite.
 */
fun CropQuad.cornerFor(handle: CropHandle): Offset = when (handle) {
    CropHandle.TL -> tl
    CropHandle.TR -> tr
    CropHandle.BR -> br
    CropHandle.BL -> bl
}

/**
 * Convex point-in-quad test using the cross-product sign method.
 *
 * For each edge `(v_i → v_{i+1})` we compute `cross((v_{i+1} - v_i), (p - v_i))`. A point is
 * strictly inside a convex polygon iff all four cross products carry the same sign; a point
 * on an edge produces a zero cross. Because [CropQuad] is convex by construction (both the
 * initial full-image quad and every `moveCorner` result preserve convexity), this test is
 * sufficient and avoids the winding-number counter-loop overhead.
 *
 * Boundary points (cross == 0) count as inside — a user who taps exactly on an edge should
 * still trigger box-drag mode, not fall through to the outer overlay canvas.
 */
fun CropQuad.contains(p: Offset): Boolean {
    fun sideSign(a: Offset, b: Offset, q: Offset): Float {
        // cross of (b - a) × (q - a); positive on left of a→b, negative on right, 0 on the line.
        return (b.x - a.x) * (q.y - a.y) - (b.y - a.y) * (q.x - a.x)
    }
    val s1 = sideSign(tl, tr, p)
    val s2 = sideSign(tr, br, p)
    val s3 = sideSign(br, bl, p)
    val s4 = sideSign(bl, tl, p)
    val allNonNeg = s1 >= 0f && s2 >= 0f && s3 >= 0f && s4 >= 0f
    val allNonPos = s1 <= 0f && s2 <= 0f && s3 <= 0f && s4 <= 0f
    return allNonNeg || allNonPos
}

/**
 * Translate every corner of the quad by [delta], but clamp the translation so the resulting
 * quad's axis-aligned bounding box stays inside `[0, boxW] × [0, boxH]`.
 *
 * The AABB approach (not per-corner clamping) is deliberate: per-corner clamping would let
 * one corner stop while others move, deforming the quad. AABB clamping preserves shape — the
 * quad slides as a rigid body until its bounding box kisses a wall, then stops.
 *
 * Per m1679: user expects the whole crop box to move as one; deforming it on a wall hit
 * would feel like a bug even if the corners stay inside.
 */
fun CropQuad.translated(delta: Offset, boxW: Float, boxH: Float): CropQuad {
    val minX = minOf(tl.x, tr.x, br.x, bl.x)
    val maxX = maxOf(tl.x, tr.x, br.x, bl.x)
    val minY = minOf(tl.y, tr.y, br.y, bl.y)
    val maxY = maxOf(tl.y, tr.y, br.y, bl.y)
    // Clamp translation so [minX+dx, maxX+dx] stays in [0, boxW] and same for Y.
    val dx = delta.x.coerceIn(-minX, boxW - maxX)
    val dy = delta.y.coerceIn(-minY, boxH - maxY)
    val d = Offset(dx, dy)
    return CropQuad(
        tl = tl + d,
        tr = tr + d,
        br = br + d,
        bl = bl + d,
    )
}

/**
 * Nearest-corner Voronoi routing for a pointer position (DESIGN §6.15). Extension on the
 * domain [CropQuad] so the domain model stays free of view-layer types.
 */
fun CropQuad.nearestCorner(p: Offset): CropHandle {
    var best = CropHandle.TL
    var bestD = (tl - p).getDistance()
    fun tryHandle(h: CropHandle, d: Float) {
        if (d < bestD) { best = h; bestD = d }
    }
    tryHandle(CropHandle.TR, (tr - p).getDistance())
    tryHandle(CropHandle.BR, (br - p).getDistance())
    tryHandle(CropHandle.BL, (bl - p).getDistance())
    return best
}

/**
 * Translate a grabbed corner by [delta], enforcing [aspectLock].
 *
 * When [aspectLock] is [AspectLock.Ratio], the moved corner is projected onto the
 * diagonal from the anchor (opposite) corner so the resulting axis-aligned bounding box
 * matches `ratio.width / ratio.height`. Signed dx/dy from the anchor is preserved so the
 * user's drag direction still feels responsive.
 *
 * When [aspectLock] is [AspectLock.Free], each corner moves independently — historically
 * the "trapezoidal" crop mode useful for hand-perspective-fix on already-detected edges.
 *
 * [box] bounds are used only to clamp the projected corner; if the projected point falls
 * outside the overlay, the shorter axis wins so the ratio survives.
 */
fun CropQuad.moveCorner(
    handle: CropHandle,
    delta: Offset,
    aspectLock: AspectLock,
    box: IntSize,
): CropQuad {
    if (aspectLock is AspectLock.Free) {
        return when (handle) {
            CropHandle.TL -> copy(tl = tl + delta)
            CropHandle.TR -> copy(tr = tr + delta)
            CropHandle.BR -> copy(br = br + delta)
            CropHandle.BL -> copy(bl = bl + delta)
        }
    }
    val ratio = (aspectLock as AspectLock.Ratio).aspect

    // Anchor is the corner diagonally opposite the one being moved; it holds still and
    // defines the axis-aligned bounding box together with the projected moved corner.
    val anchor: Offset = when (handle) {
        CropHandle.TL -> br
        CropHandle.TR -> bl
        CropHandle.BR -> tl
        CropHandle.BL -> tr
    }
    val movedRaw = when (handle) {
        CropHandle.TL -> tl + delta
        CropHandle.TR -> tr + delta
        CropHandle.BR -> br + delta
        CropHandle.BL -> bl + delta
    }
    // Signed offset from anchor. Absolute values drive the axis-aligned box; signs preserve
    // which quadrant the moved corner lives in relative to the anchor.
    val dx = movedRaw.x - anchor.x
    val dy = movedRaw.y - anchor.y
    val sx = if (dx >= 0) 1f else -1f
    val sy = if (dy >= 0) 1f else -1f
    var w = kotlin.math.abs(dx)
    var h = kotlin.math.abs(dy)

    // Project onto ratio: pick the larger axis as the driver so the user's drag stays
    // visible; the smaller axis follows. This mirrors the standard Photoshop/Figma
    // aspect-locked resize behaviour.
    if (w / h > ratio) h = w / ratio else w = h * ratio

    // Clamp to the overlay so a runaway drag doesn't punch a corner out of the box; clamp
    // by whichever axis hit its edge first so the ratio is preserved.
    val maxW = if (sx >= 0) box.width - anchor.x else anchor.x
    val maxH = if (sy >= 0) box.height - anchor.y else anchor.y
    if (maxW > 0f && w > maxW) { w = maxW; h = w / ratio }
    if (maxH > 0f && h > maxH) { h = maxH; w = h * ratio }

    val nx = anchor.x + sx * w
    val ny = anchor.y + sy * h
    val newMoved = Offset(nx, ny)

    // Build a fresh axis-aligned quad. Aspect lock implies rectangular (not trapezoidal)
    // crops — the mid-drag CropQuad is always a rect when a ratio is locked.
    return when (handle) {
        CropHandle.TL -> rectFrom(newMoved, anchor)
        CropHandle.TR -> rectFrom(Offset(anchor.x, newMoved.y), Offset(newMoved.x, anchor.y))
        CropHandle.BR -> rectFrom(anchor, newMoved)
        CropHandle.BL -> rectFrom(Offset(newMoved.x, anchor.y), Offset(anchor.x, newMoved.y))
    }
}

/**
 * Assemble an axis-aligned [CropQuad] from any two diagonal corners. Normalises so that
 * [CropQuad.tl] is the top-left regardless of which corner the caller passed as "min".
 */
private fun rectFrom(a: Offset, b: Offset): CropQuad {
    val left   = minOf(a.x, b.x)
    val right  = maxOf(a.x, b.x)
    val top    = minOf(a.y, b.y)
    val bottom = maxOf(a.y, b.y)
    return CropQuad(
        tl = Offset(left,  top),
        tr = Offset(right, top),
        br = Offset(right, bottom),
        bl = Offset(left,  bottom),
    )
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
