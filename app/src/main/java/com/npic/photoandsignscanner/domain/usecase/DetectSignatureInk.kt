package com.npic.photoandsignscanner.domain.usecase

import android.graphics.Bitmap
import com.npic.photoandsignscanner.domain.model.DetectedCrop
import com.npic.photoandsignscanner.domain.model.RectI

/**
 * Signature ink-isolation use case. Given a captured signature bitmap and the on-screen guide
 * box mapped to image space, produce an axis-aligned rectangle tightly wrapping the ink.
 *
 * The exact algorithm is specified in PRD §7.2 (crop to guide-box → grayscale → Gaussian blur
 * → adaptive threshold → morphological dilate → find connected components → union bbox with
 * 8% padding → fallback to full guide-box if no components survive). Implementations live in
 * `data/imaging/`.
 *
 * All calls MUST be safe to invoke from [kotlinx.coroutines.Dispatchers.Default] and MUST NOT
 * throw on OpenCV-not-initialized — return `DetectedCrop.Rect(..., origin = Skipped, confidence
 * = 0f)` built from the guide-box coordinates so the caller can proceed without a null branch.
 * When the pipeline runs but yields zero components, return `origin = GuideBoxFallback` so the
 * Edit screen can raise the "Couldn't detect ink automatically" banner (PRD §7.2).
 */
fun interface DetectSignatureInk {
    suspend operator fun invoke(source: Bitmap, guideBoxImageSpace: RectI): DetectedCrop.Rect
}
