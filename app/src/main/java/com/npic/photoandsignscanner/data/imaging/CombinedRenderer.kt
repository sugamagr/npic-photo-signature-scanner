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
 * ### Layout (updated per m1814)
 * - Canvas: 800×1000, white background, 4:5 aspect
 * - Photo box:     540×720 at (130, 24)         — aspect 3:4, matches Camera Photo guide
 * - 24 px gap between photo and signature
 * - Signature strip: 540×180 at (130, 768)      — aspect 3:1, matches Camera Signature
 *   guide (which m1817 narrowed to width-match the photo box)
 * - 130 px side margin (matched left/right) — uniform border replaces the ragged inside
 *   letterbox that the old 752-wide near-square photo slot produced
 * - 2 px Ink (#1A1613) border stroked INSIDE each box's outer rect
 *
 * ### m1814 fix rationale
 * The old 752×584 photo box (aspect 1.29) forced huge horizontal white bars on every 3:4
 * capture, and the old 752×344 signature strip (aspect 2.19) forced vertical dead space
 * above/below every 3:1 signature. Matching each slot to its capture aspect kills the
 * whitespace bug at the source without any auto-tightening heuristics that could fail on
 * captured-signature edge cases (off-white paper, warm light, stray ink dots). The tighter
 * photo slot (540 vs 752 wide) trades absolute pixel size for consistent uniform margins —
 * a portal-quality look.
 *
 * ### Scaling
 * Both source bitmaps are drawn `fit-inside` their box preserving aspect ratio, then
 * centered inside the box. When source aspect matches slot aspect (the common case now),
 * `drawFitted` produces zero letterbox — the bitmap fills the slot edge-to-edge. Off-aspect
 * imports still get letterboxing but inside the correctly-shaped slot, so the bars are
 * small and centered rather than the old huge asymmetric ones.
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
        // PRD §6.2 canvas stays 800×1000 to preserve the UPMSP portal contract.
        const val CANVAS_W = 800
        const val CANVAS_H = 1000
        const val BORDER_PX = 2

        // m1814 layout — matched-aspect slots centered horizontally on the canvas.
        //
        // Photo box: 540 wide × 720 tall = aspect 3:4 EXACTLY. Matches Camera Photo mode
        // guideAspect (3:4). When the SourceStore-scaled photo (long-side 1600, aspect 3:4)
        // is drawn fit-inside this box, drawFitted produces zero letterbox — the bitmap
        // fills the slot edge-to-edge. That is the whole point of this rewrite.
        //
        // Signature box: 540 wide × 180 tall = aspect 3:1 EXACTLY. Matches Camera Signature
        // mode guideAspect (3:1) AND the SignatureDraw canvas (1500×500, aspect 3:1). Same
        // zero-letterbox story.
        //
        // Both slots have width 540, giving 130 px left/right margin — matched, intentional,
        // portal-friendly. Vertical margins: 24 top, 24 gap, 76 bottom. The slightly larger
        // bottom margin reads visually balanced because the eye reads top-heavy layouts.
        //
        // DO NOT restore the old 752-wide slots without matching the aspects first — see
        // the class KDoc for why the old geometry produced huge whitespace bars.
        val PHOTO_BOX: Rect = Rect(130, 24, 130 + 540, 24 + 720)   // (130, 24) → (670, 744)
        val SIG_BOX:   Rect = Rect(130, 768, 130 + 540, 768 + 180) // (130, 768) → (670, 948)

        /** NpicColors.Ink packed ARGB. Must stay in sync with core/theme/NpicColors. */
        const val INK_ARGB: Int = 0xFF1A1613.toInt()
    }
}
