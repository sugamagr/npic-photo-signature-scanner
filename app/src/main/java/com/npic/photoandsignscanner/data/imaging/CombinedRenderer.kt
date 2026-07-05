package com.npic.photoandsignscanner.data.imaging

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.min

/**
 * Combined-format renderer. Composes a photo (top) + signature (bottom) onto a
 * content-hugging white canvas per m1966 user directive.
 *
 * ### Layout
 * - Photo box:     540 × 720 (aspect 3:4, matches Camera Photo guide)
 * - Gap: 24 px between photo bottom border and signature top border
 * - Signature box: 540 × 180 (aspect 3:1, matches Camera Signature guide + SignatureDraw
 *   1500×500 canvas)
 * - Outer padding: 16 px on all sides
 * - Total canvas: (540 + 32) × (720 + 24 + 180 + 32) = 572 × 956, white background
 * - 2 px Ink (#1A1613) border stroked INSIDE each box's outer rect
 *
 * ### m1966 rationale — content-hugging canvas
 * m1814 fixed the whitespace INSIDE each slot by aspect-matching the boxes to the capture
 * guides. That left ~130 px side margins around the content stack because the canvas
 * was locked at 800×1000 for the UPMSP portal template. User m1966 verified the portal
 * accepts non-standard canvas dimensions and asked for a content-only export with a
 * small uniform padding. This shrinks the payload ~30% by pixel count (572×956 vs
 * 800×1000) and gives compressors more room to preserve detail at the 28 KB Combined
 * ceiling.
 *
 * ### Scaling
 * Both source bitmaps are drawn `fit-inside` their box preserving aspect ratio, then
 * centered inside the box. When source aspect matches slot aspect (the common case now),
 * `drawFitted` produces zero letterbox — the bitmap fills the slot edge-to-edge.
 * Off-aspect imports still letterbox inside the correctly-shaped slot, so the bars are
 * small and centered rather than the ragged asymmetric ones from the pre-m1814 layout.
 *
 * ### Bitmap ownership
 * Neither source bitmap is recycled here — the caller (usually ExportViewModel) owns them.
 * The returned bitmap is a fresh ARGB_8888 allocation and belongs to the caller.
 */
class CombinedRenderer {

    /**
     * Compose [photo] on top and [signature] on bottom onto a fresh 572×956 white canvas
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
            // DESIGN §2 Ink brand color. Hardcoded here rather than pulling from
            // NpicColors because the renderer produces an image asset (not a UI surface)
            // so it never sees the Compose color pipeline. Value MUST match
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
        // Content-hugging layout — see class KDoc for the m1966 rewrite rationale.
        //
        // Photo box: 540 × 720 = aspect 3:4 EXACTLY. Matches Camera Photo mode guideAspect
        // (3:4). SourceStore-scaled photo (long-side 1600, aspect 3:4) draws with zero
        // letterbox inside this slot.
        //
        // Signature box: 540 × 180 = aspect 3:1 EXACTLY. Matches Camera Signature mode
        // guideAspect (3:1) AND the SignatureDraw canvas (1500×500). Same zero-letterbox
        // story.
        //
        // Canvas = content stack + uniform 16 px outer padding. Total 572 × 956.
        //
        // DO NOT restore the pre-m1966 800×1000 canvas or the pre-m1814 near-square slots
        // — the KDoc names the exact whitespace bugs each rewrite fixed.
        const val OUTER_PAD_PX = 16
        const val GAP_PX = 24
        const val PHOTO_W = 540
        const val PHOTO_H = 720
        const val SIG_W = 540
        const val SIG_H = 180
        const val BORDER_PX = 2

        const val CANVAS_W = PHOTO_W + 2 * OUTER_PAD_PX                    // 572
        const val CANVAS_H = PHOTO_H + GAP_PX + SIG_H + 2 * OUTER_PAD_PX   // 956

        val PHOTO_BOX: Rect = Rect(
            OUTER_PAD_PX,
            OUTER_PAD_PX,
            OUTER_PAD_PX + PHOTO_W,
            OUTER_PAD_PX + PHOTO_H,
        )
        val SIG_BOX: Rect = Rect(
            OUTER_PAD_PX,
            OUTER_PAD_PX + PHOTO_H + GAP_PX,
            OUTER_PAD_PX + SIG_W,
            OUTER_PAD_PX + PHOTO_H + GAP_PX + SIG_H,
        )

        /** NpicColors.Ink packed ARGB. Must stay in sync with core/theme/NpicColors. */
        const val INK_ARGB: Int = 0xFF1A1613.toInt()
    }
}
