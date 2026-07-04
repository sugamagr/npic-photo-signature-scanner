package com.npic.photoandsignscanner.features.signaturedraw

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.Size
import androidx.compose.ui.graphics.toArgb
import com.npic.photoandsignscanner.core.theme.NpicColors
import com.npic.photoandsignscanner.domain.model.DrawStroke

/**
 * Flattens a list of committed [DrawStroke]s from the on-screen canvas into the export
 * bitmap (PRD §4.4: 1500×500 white background, black ink). The caller provides the
 * on-screen canvas size so stroke coordinates can be projected into the export size.
 *
 * Runs on Dispatchers.Default from [SignatureRasterizer.rasterize] — this class holds
 * no state, no OpenCV dependency, and is JVM-unit-testable.
 */
object SignatureRasterizer {

    const val EXPORT_WIDTH = 1500
    const val EXPORT_HEIGHT = 500

    /**
     * Rasterize [strokes] captured in a canvas of size [canvasSize] onto a fresh
     * [EXPORT_WIDTH]×[EXPORT_HEIGHT] white bitmap. Returns null when [strokes] is empty
     * or [canvasSize] has zero area (caller should short-circuit Done).
     */
    fun rasterize(strokes: List<DrawStroke>, canvasSize: Size): Bitmap? {
        if (strokes.isEmpty()) return null
        if (canvasSize.width <= 0 || canvasSize.height <= 0) return null

        val bitmap = Bitmap.createBitmap(EXPORT_WIDTH, EXPORT_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(SURFACE_COLOR)

        val scaleX = EXPORT_WIDTH.toFloat() / canvasSize.width.toFloat()
        val scaleY = EXPORT_HEIGHT.toFloat() / canvasSize.height.toFloat()

        val paint = Paint().apply {
            isAntiAlias = true
            color = INK_COLOR
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val dotPaint = Paint().apply {
            isAntiAlias = true
            color = INK_COLOR
            style = Paint.Style.FILL
        }

        strokes.forEach { stroke ->
            val projected = stroke.points.map { p ->
                android.graphics.PointF(p.x * scaleX, p.y * scaleY)
            }
            paint.strokeWidth = stroke.widthPx * ((scaleX + scaleY) / 2f)
            when {
                projected.isEmpty() -> Unit
                projected.size == 1 -> canvas.drawCircle(
                    projected.first().x,
                    projected.first().y,
                    paint.strokeWidth / 2f,
                    dotPaint,
                )
                else -> {
                    val path = Path().apply {
                        moveTo(projected.first().x, projected.first().y)
                        for (i in 1 until projected.size) {
                            lineTo(projected[i].x, projected[i].y)
                        }
                    }
                    canvas.drawPath(path, paint)
                }
            }
        }
        return bitmap
    }

    private val INK_COLOR = NpicColors.Ink.toArgb()
    private val SURFACE_COLOR = NpicColors.Surface.toArgb()
}
