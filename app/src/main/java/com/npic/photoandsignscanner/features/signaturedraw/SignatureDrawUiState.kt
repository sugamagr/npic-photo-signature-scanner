package com.npic.photoandsignscanner.features.signaturedraw

import androidx.compose.runtime.Stable
import com.npic.photoandsignscanner.domain.model.DrawStroke

/**
 * View state for [SignatureDrawScreen] (PRD §4.4).
 *
 * [strokes] is the committed stroke history rendered on the canvas. [inFlightStroke] is the
 * stroke currently being drawn (populated during pointerInput onDrag); it's kept separate
 * so undo/redo doesn't fight with the live drag.
 *
 * [redoStack] holds strokes that were undone; a new stroke clears it (standard undo/redo
 * semantics — you can't redo past a divergence).
 *
 * [thicknessPx] is the pen width applied to the NEXT stroke, in canvas pixels. Slider
 * range is 2..12 px per PRD §4.4, default 4 px.
 *
 * [showClearConfirm] drives the "Clear all strokes?" confirmation dialog.
 *
 * qc-round-12 Oracle #6 MAJOR #1: annotated `@Stable` (not `@Immutable`) because Kotlin's
 * read-only `List<DrawStroke>` type can be backed by a mutable implementation — the
 * `@Immutable` contract requires DEEP unchangeability, which we can't guarantee at the
 * type level. Runtime is correct: SignatureDrawViewModel publishes fresh instances via
 * `_state.update { ... }`, so equals-based skip-recomp (which `@Stable` enables) is sound.
 * Matches EditUiState exemplary pattern. m1597 industry standard.
 */
@Stable
data class SignatureDrawUiState(
    val strokes: List<DrawStroke> = emptyList(),
    val inFlightStroke: DrawStroke? = null,
    val redoStack: List<DrawStroke> = emptyList(),
    val thicknessPx: Float = DEFAULT_THICKNESS,
    val showClearConfirm: Boolean = false,
) {
    val canUndo: Boolean get() = strokes.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /** PRD §4.4: Done button is disabled until any stroke exists. */
    val canFinish: Boolean get() = strokes.isNotEmpty()

    companion object {
        const val MIN_THICKNESS = 2f
        const val MAX_THICKNESS = 12f
        const val DEFAULT_THICKNESS = 4f
    }
}
