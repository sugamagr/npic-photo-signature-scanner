package com.npic.photoandsignscanner.features.edit

import android.graphics.Bitmap
import androidx.compose.runtime.Stable
import com.npic.photoandsignscanner.domain.model.DetectedCrop
import com.npic.photoandsignscanner.domain.model.EditState
import com.npic.photoandsignscanner.domain.model.FilterPreset

/**
 * Full view state for [EditScreen]. Wraps the immutable [EditState] (the actual edit graph)
 * with UI concerns: which tool is open, cached bitmaps for the viewport and Filter strip,
 * detection origin (drives the "Couldn't detect ink automatically" banner), and any terminal
 * error to surface as a toast.
 *
 * `@Stable` — [EditViewModel] never mutates a live [EditUiState] instance; every state
 * transition emits a fresh copy via `_state.update { }` and swaps the whole reference. That
 * means `equals` is a valid recomposition-skip signal. NOT annotated `@Immutable` because
 * the Bitmap payloads are physically mutable (pixel data can change without the wrapper
 * being replaced), and `@Immutable` promises Compose no field ever changes for equal
 * instances — a stronger contract we can't uphold. `@Stable` is the correct middle ground.
 *
 * Mutations flow through [EditViewModel]. The screen only reads.
 */
@Stable
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
     * 1920px-longest-side downsample used for live Adjust slider previews (PRD §4.5 <100 ms
     * latency budget after m1869 quality bump). Null until [sourceBitmap] is decoded.
     */
    val previewBitmap: Bitmap? = null,
    /**
     * Live-render bitmap produced by [EditRenderer] applied to [previewBitmap] whenever the
     * user mutates the edit state (filter / adjustments / rotation / straighten). Null while
     * rendering, or when no preview is available yet. Bug#3 fix — without this the viewport
     * always shows the raw source and the tabs feel like placeholders.
     */
    val livePreviewBitmap: Bitmap? = null,
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
