package com.npic.photoandsignscanner.features.signaturedraw

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.npic.photoandsignscanner.core.theme.NpicColors
import com.npic.photoandsignscanner.domain.model.DrawStroke

/**
 * The pen-drawing surface (PRD §4.4). Fixed 3:1 landscape aspect ratio; the parent should
 * hand it a width and let the aspectRatio modifier derive the height. White fill, black
 * strokes at the caller's chosen thickness.
 *
 * Gestures wire through [onBegin] / [onExtend] / [onEnd] / [onCancel] so the ViewModel owns
 * the stroke history. The canvas only renders — never mutates state directly.
 *
 * Strokes render as [Path]s with round caps + round joins so pen ends look natural rather
 * than square. The in-flight stroke is drawn on top of the committed history so the user
 * sees their current pen path immediately.
 */
@Composable
fun SignatureCanvas(
    strokes: List<DrawStroke>,
    inFlightStroke: DrawStroke?,
    liveWidthPx: Float,
    onBegin: (Offset) -> Unit,
    onExtend: (Offset) -> Unit,
    onEnd: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Path allocation is per-stroke and stable; keeping them cached avoids per-frame
    // allocation. The in-flight stroke must rebuild each frame — it grows point-by-point.
    val committedPaths = remember(strokes) {
        strokes.map { it to it.buildPath() }
    }
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(SIGNATURE_ASPECT)
            .background(NpicColors.Surface)
            .semantics { contentDescription = "Signature drawing canvas" }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { onBegin(it) },
                    onDragEnd = { onEnd() },
                    onDragCancel = { onCancel() },
                    onDrag = { change, _ ->
                        change.consume()
                        onExtend(change.position)
                    },
                )
            },
    ) {
        // Every stroke — committed and in-flight — renders at the live slider width.
        // User feedback (2024-12): thickness must feel like a canvas-global pen, not a
        // per-stroke commit. Contradicts PRD §4.4's "thickness fixed per-stroke" note; the
        // per-stroke widthPx on DrawStroke is preserved only for rasterization backwards
        // compat (SignatureRasterizer still reads it if a caller uses the old code path).
        committedPaths.forEach { (stroke, path) -> drawStroke(stroke, path, liveWidthPx) }
        inFlightStroke?.let { drawStroke(it, it.buildPath(), liveWidthPx) }
    }
}

private fun DrawStroke.buildPath(): Path {
    val pts = points
    val path = Path()
    if (pts.isEmpty()) return path
    path.moveTo(pts.first().x, pts.first().y)
    for (i in 1 until pts.size) {
        path.lineTo(pts[i].x, pts[i].y)
    }
    return path
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStroke(
    stroke: DrawStroke,
    path: Path,
    widthPx: Float,
) {
    val pts = stroke.points
    if (pts.isEmpty()) return
    if (pts.size == 1) {
        drawCircle(
            color = NpicColors.Ink,
            radius = widthPx / 2f,
            center = pts.first(),
        )
        return
    }
    drawPath(
        path = path,
        color = NpicColors.Ink,
        style = Stroke(
            width = widthPx,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        ),
    )
}

private const val SIGNATURE_ASPECT = 3f
