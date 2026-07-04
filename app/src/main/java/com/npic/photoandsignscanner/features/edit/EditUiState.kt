package com.npic.photoandsignscanner.features.edit

import androidx.compose.runtime.Immutable
import com.npic.photoandsignscanner.domain.model.EditState

/**
 * Full view state for [EditScreen]. Wraps the immutable [EditState] (the actual edit graph)
 * with UI concerns: which tool is open, whether a background render is running, and any
 * terminal error to surface as a toast.
 *
 * Mutations flow through [EditViewModel]. The screen only reads.
 */
@Immutable
data class EditUiState(
    val edit: EditState,
    val activeTool: EditTool = EditTool.Crop,
    val isRendering: Boolean = false,
    val showDiscardConfirm: Boolean = false,
    val lastError: String? = null,
) {
    val dirty: Boolean get() = edit.hasChanges
}
