package com.npic.photoandsignscanner.features.edit

import android.graphics.Bitmap
import com.npic.photoandsignscanner.domain.model.DetectedCrop
import com.npic.photoandsignscanner.domain.model.EditState
import com.npic.photoandsignscanner.domain.model.FilterPreset

/**
 * Full view state for [EditScreen]. Wraps the immutable [EditState] (the actual edit graph)
 * with UI concerns: which tool is open, cached bitmaps for the viewport and Filter strip,
 * detection origin (drives the "Couldn't detect ink automatically" banner), and any terminal
 * error to surface as a toast.
 *
 * Not annotated `@Immutable` because [sourceBitmap] / [previewBitmap] / [filterThumbnails]
 * are mutable Bitmap references (contents can change without the wrapper being replaced).
 * The `@Immutable` promise would let Compose skip recompositions the state actually needs.
 * Compose infers Bitmap-holding data classes as Unstable by default, which is correct here.
 *
 * Mutations flow through [EditViewModel]. The screen only reads.
 */
data class EditUiState(
    val edit: EditState,
    val activeTool: EditTool = EditTool.Crop,
    val isRendering: Boolean = false,
    val showDiscardConfirm: Boolean = false,
    val committing: Boolean = false,
    val committedSourcePath: String? = null,
    val lastError: String? = null,
    /**
     * Full-resolution source bitmap loaded from `edit.source.rawPath`. Null while decoding.
     * The Edit viewport renders this letterboxed on CameraSurface.
     */
    val sourceBitmap: Bitmap? = null,
    /**
     * 384px-longest-side downsample used for live Adjust slider previews (PRD §4.5 <100 ms
     * latency budget). Null until [sourceBitmap] is decoded.
     */
    val previewBitmap: Bitmap? = null,
    /**
     * 192×192 cached preview per FilterPreset (DESIGN §6.18 max working res). Populated on
     * demand when the Filter tool is first opened; keys map 1:1 to [FilterPreset.entries].
     */
    val filterThumbnails: Map<FilterPreset, Bitmap> = emptyMap(),
    /**
     * Origin of the current crop quad. `Detected` = OpenCV succeeded and crossfaded in;
     * `GuideBoxFallback` = detector returned null (photo below threshold, or signature had
     * no ink components); `Skipped` = OpenCV not initialized. Drives the fallback banner
     * for Signature mode per PRD §7.2.
     */
    val detectionOrigin: DetectedCrop.Origin = DetectedCrop.Origin.GuideBoxFallback,
) {
    val dirty: Boolean get() = edit.hasChanges

    /**
     * True when the signature ink detector failed to find components AND the mode is
     * Signature. Photo mode's fallback surfaces as manual-crop-required implicitly (the
     * quad is still the guide box), no banner needed.
     */
    val showInkFallbackBanner: Boolean
        get() = detectionOrigin == DetectedCrop.Origin.GuideBoxFallback &&
                edit.mode == com.npic.photoandsignscanner.domain.model.CameraMode.Signature
}
