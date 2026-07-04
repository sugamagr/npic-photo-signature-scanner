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
    onBegin: (Offset) -> Unit,
    onExtend: (Offset) -> Unit,
    onEnd: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Committed-stroke Paths are stable — cache one Path per DrawStroke identity so the
    // draw scope doesn't re-allocate on every frame. The in-flight stroke is grown per
    // frame and must rebuild; that Path is small (recent points only).
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
        committedPaths.forEach { (stroke, path) -> drawStroke(stroke, path) }
        inFlightStroke?.let { drawStroke(it, it.buildPath()) }
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
) {
    val pts = stroke.points
    if (pts.isEmpty()) return
    if (pts.size == 1) {
        drawCircle(
            color = NpicColors.Ink,
            radius = stroke.widthPx / 2f,
            center = pts.first(),
        )
        return
    }
    drawPath(
        path = path,
        color = NpicColors.Ink,
        style = Stroke(
            width = stroke.widthPx,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        ),
    )
}

private const val SIGNATURE_ASPECT = 3f
