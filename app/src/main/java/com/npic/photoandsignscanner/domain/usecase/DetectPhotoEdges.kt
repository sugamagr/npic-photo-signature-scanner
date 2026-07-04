package com.npic.photoandsignscanner.domain.usecase

import android.graphics.Bitmap
import com.npic.photoandsignscanner.domain.model.DetectedCrop
import com.npic.photoandsignscanner.domain.model.RectI

/**
 * Photo edge-detection use case. Given a captured photo bitmap and the on-screen guide-box
 * mapped to image space, produce a 4-corner quad ready for perspective correction.
 *
 * The exact algorithm is specified in PRD §7.1 (grayscale → bilateral filter → auto-thresholded
 * Canny → morphological close → find contours → filter by area → approxPolyDP → score →
 * fallback to guide-box if no quad scores above 0.6). Implementations live in `data/imaging/`.
 *
 * All calls MUST be safe to invoke from [kotlinx.coroutines.Dispatchers.Default] and MUST NOT
 * throw on OpenCV-not-initialized — return `DetectedCrop.Quad(..., origin = Skipped, confidence = 0f)`
 * built from the guide-box coordinates so the caller can proceed without a null branch.
 */
fun interface DetectPhotoEdges {
    suspend operator fun invoke(source: Bitmap, guideBoxImageSpace: RectI): DetectedCrop.Quad
}
