package com.npic.photoandsignscanner.data.imaging

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.min

/**
 * PRD §6.2 combined-format renderer. Composes a photo (top) + signature (bottom) onto a
 * fixed 800×1000 white canvas that matches the UPMSP portal template.
 *
 * ### Layout (from PRD §6.2)
 * - Canvas: 800×1000, white background, 4:5 aspect
 * - 24 px padding around all content
 * - Photo box:     752×584 at (24, 24)          — occupies top ~58% of canvas
 * - 24 px gap between photo box and signature strip
 * - Signature strip: 752×344 at (24, 632)       — occupies bottom ~34% of canvas
 * - 2 px Ink (#1A1613) border stroked around EACH box (inside the box's outer rect, so the
 *   inner drawable area is 748×580 / 748×340)
 *
 * ### Scaling
 * Both source bitmaps are drawn `fit-inside` their box preserving aspect ratio, then
 * centered inside the box. Landscape signatures are letterboxed vertically; tall photos
 * are letterboxed horizontally. This matches the portal's own render, which never crops or
 * stretches submitted assets.
 *
 * ### Bitmap ownership
 * Neither source bitmap is recycled here — the caller (usually ExportViewModel) owns them.
 * The returned bitmap is a fresh ARGB_8888 allocation and belongs to the caller.
 */
class CombinedRenderer {

    /**
     * Compose [photo] on top and [signature] on bottom onto a fresh 800×1000 white canvas
     * with 2 px Ink borders. Returns the composed bitmap.
     */
    fun render(photo: Bitmap, signature: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(CANVAS_W, CANVAS_H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
            isDither = true
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = BORDER_PX.toFloat()
            // PRD §6.2 / DESIGN §2 Ink brand color. Hardcoded here rather than pulling
            // from NpicColors because the renderer produces an image asset (not a UI
            // surface) so it never sees the Compose color pipeline. Value MUST match
            // NpicColors.Ink so exports look identical to on-device chrome.
            color = INK_ARGB
        }

        drawFitted(canvas, photo, PHOTO_BOX, fillPaint)
        drawBorderInside(canvas, PHOTO_BOX, borderPaint)

        drawFitted(canvas, signature, SIG_BOX, fillPaint)
        drawBorderInside(canvas, SIG_BOX, borderPaint)

        return out
    }

    // ------------------------------------------------------------------ helpers

    /** Draw [bitmap] centered inside [box], scaled `fit-inside` preserving aspect. */
    private fun drawFitted(canvas: Canvas, bitmap: Bitmap, box: Rect, paint: Paint) {
        val boxW = box.width().toFloat()
        val boxH = box.height().toFloat()
        val srcW = bitmap.width.toFloat()
        val srcH = bitmap.height.toFloat()
        if (srcW <= 0f || srcH <= 0f) return

        val scale = min(boxW / srcW, boxH / srcH)
        val drawW = srcW * scale
        val drawH = srcH * scale
        val left = box.left + (boxW - drawW) / 2f
        val top = box.top + (boxH - drawH) / 2f
        val dst = RectF(left, top, left + drawW, top + drawH)

        canvas.drawBitmap(bitmap, null, dst, paint)
    }

    /**
     * Stroke a 2 px border on the INSIDE edge of [box], so the border does not encroach on
     * the surrounding 24 px padding. Compensating for stroke width (half-pixel inset) keeps
     * the visible border 2 px wide without alpha-blending against the neighbor pixels.
     */
    private fun drawBorderInside(canvas: Canvas, box: Rect, paint: Paint) {
        val inset = BORDER_PX / 2f
        val rect = RectF(
            box.left + inset,
            box.top + inset,
            box.right - inset,
            box.bottom - inset,
        )
        canvas.drawRect(rect, paint)
    }

    private companion object {
        // PRD §6.2 canvas.
        const val CANVAS_W = 800
        const val CANVAS_H = 1000
        const val PAD_PX = 24
        const val GAP_PX = 24
        const val BORDER_PX = 2

        // PRD §6.2 photo box: 800 - 2×24 = 752 wide × 584 tall at (24, 24).
        val PHOTO_BOX: Rect = Rect(PAD_PX, PAD_PX, PAD_PX + 752, PAD_PX + 584)

        // PRD §6.2 signature strip: 752 wide × 344 tall directly below photo + 24 gap.
        val SIG_BOX: Rect = Rect(
            PAD_PX,
            PAD_PX + 584 + GAP_PX,
            PAD_PX + 752,
            PAD_PX + 584 + GAP_PX + 344,
        )

        /** NpicColors.Ink packed ARGB. Must stay in sync with core/theme/NpicColors. */
        const val INK_ARGB: Int = 0xFF1A1613.toInt()
    }
}
