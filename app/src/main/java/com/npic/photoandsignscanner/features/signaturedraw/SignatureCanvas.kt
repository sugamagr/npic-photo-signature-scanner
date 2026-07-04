package com.npic.photoandsignscanner.features.signaturedraw

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(SIGNATURE_ASPECT)
            .background(Color.White)
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
        strokes.forEach { drawStroke(it) }
        inFlightStroke?.let { drawStroke(it) }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStroke(stroke: DrawStroke) {
    val pts = stroke.points
    if (pts.isEmpty()) return
    if (pts.size == 1) {
        // Single tap — render as a filled dot the width of the stroke so it doesn't
        // vanish. Otherwise a lone tap would produce an empty Path.
        drawCircle(
            color = NpicColors.Ink,
            radius = stroke.widthPx / 2f,
            center = pts.first(),
        )
        return
    }
    val path = Path().apply {
        moveTo(pts.first().x, pts.first().y)
        for (i in 1 until pts.size) {
            lineTo(pts[i].x, pts[i].y)
        }
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
