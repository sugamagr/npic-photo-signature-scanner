package com.npic.photoandsignscanner.features.signaturedraw

import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import com.npic.photoandsignscanner.domain.model.DrawStroke
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Presenter for the Signature Draw canvas (PRD §4.4). Owns stroke history + undo/redo stacks
 * + pen thickness. Rasterization to the 1500×500 export bitmap happens in
 * [SignatureRasterizer] on the caller's Dispatchers.Default coroutine when the user hits
 * Done — the ViewModel intentionally does not couple to bitmap allocation.
 */
class SignatureDrawViewModel : ViewModel() {

    private val _state = MutableStateFlow(SignatureDrawUiState())
    val state: StateFlow<SignatureDrawUiState> = _state.asStateFlow()

    fun beginStroke(start: Offset) {
        _state.update { s ->
            s.copy(inFlightStroke = DrawStroke(points = listOf(start), widthPx = s.thicknessPx))
        }
    }

    fun extendStroke(point: Offset) {
        _state.update { s ->
            val current = s.inFlightStroke ?: return@update s
            s.copy(inFlightStroke = current.copy(points = current.points + point))
        }
    }

    fun endStroke() {
        _state.update { s ->
            val committed = s.inFlightStroke ?: return@update s
            s.copy(
                strokes = s.strokes + committed,
                inFlightStroke = null,
                redoStack = emptyList(),
            )
        }
    }

    fun cancelStroke() {
        _state.update { it.copy(inFlightStroke = null) }
    }

    fun setThickness(thickness: Float) {
        val clamped = thickness.coerceIn(
            SignatureDrawUiState.MIN_THICKNESS,
            SignatureDrawUiState.MAX_THICKNESS,
        )
        _state.update { it.copy(thicknessPx = clamped) }
    }

    fun undo() {
        _state.update { s ->
            val last = s.strokes.lastOrNull() ?: return@update s
            s.copy(
                strokes = s.strokes.dropLast(1),
                redoStack = s.redoStack + last,
            )
        }
    }

    fun redo() {
        _state.update { s ->
            val next = s.redoStack.lastOrNull() ?: return@update s
            s.copy(
                strokes = s.strokes + next,
                redoStack = s.redoStack.dropLast(1),
            )
        }
    }

    fun requestClearConfirm() {
        _state.update { it.copy(showClearConfirm = true) }
    }

    fun dismissClearConfirm() {
        _state.update { it.copy(showClearConfirm = false) }
    }

    fun clearAll() {
        _state.update {
            it.copy(
                strokes = emptyList(),
                inFlightStroke = null,
                redoStack = emptyList(),
                showClearConfirm = false,
            )
        }
    }
}
