package com.npic.photoandsignscanner.features.edit

import androidx.lifecycle.ViewModel
import com.npic.photoandsignscanner.core.ui.CropQuad
import com.npic.photoandsignscanner.domain.model.Adjustments
import com.npic.photoandsignscanner.domain.model.AspectLock
import com.npic.photoandsignscanner.domain.model.CameraCapture
import com.npic.photoandsignscanner.domain.model.EditState
import com.npic.photoandsignscanner.domain.model.FilterPreset
import com.npic.photoandsignscanner.domain.model.RotationSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Edit presenter. Non-destructive: every knob mutates [EditState] in-memory only. The final
 * render pipeline (PRD §5.5 / §7) runs on [next] when the user commits.
 *
 * TODO(pipeline): [next] currently just fires an out-of-band callback. Real Save flow runs
 * OpenCV + JPEG binary-search compression + Room insert; lands with the Save layer.
 */
class EditViewModel : ViewModel() {

    private val _state = MutableStateFlow<EditUiState?>(null)
    val state: StateFlow<EditUiState?> = _state.asStateFlow()

    /** Seed from a fresh capture. No-op if the same capture is already loaded. */
    fun seed(capture: CameraCapture) {
        val current = _state.value
        if (current != null && current.edit.source.rawPath == capture.rawPath) return
        _state.value = EditUiState(edit = EditState.seedFrom(capture))
    }

    fun setActiveTool(tool: EditTool) {
        _state.update { it?.copy(activeTool = tool, lastError = null) }
    }

    fun setCrop(quad: CropQuad) {
        _state.update { it?.copy(edit = it.edit.copy(crop = quad)) }
    }

    fun resetCropToAuto() {
        _state.update { s ->
            s ?: return@update null
            s.copy(edit = EditState.seedFrom(s.edit.source).copy(
                filter      = s.edit.filter,
                adjustments = s.edit.adjustments,
                rotation    = s.edit.rotation,
                aspectLock  = s.edit.aspectLock,
            ))
        }
    }

    fun setAspectLock(lock: AspectLock) {
        _state.update { it?.copy(edit = it.edit.copy(aspectLock = lock)) }
    }

    fun setFilter(preset: FilterPreset) {
        _state.update { it?.copy(edit = it.edit.copy(filter = preset)) }
    }

    fun setAdjustments(adjustments: Adjustments) {
        _state.update { it?.copy(edit = it.edit.copy(adjustments = adjustments)) }
    }

    fun rotateCw() {
        _state.update { it?.copy(edit = it.edit.copy(rotation = it.edit.rotation.rotateCw())) }
    }

    fun rotateCcw() {
        _state.update { it?.copy(edit = it.edit.copy(rotation = it.edit.rotation.rotateCcw())) }
    }

    fun setStraighten(degrees: Float) {
        _state.update { it?.copy(edit = it.edit.copy(rotation = it.edit.rotation.withStraighten(degrees))) }
    }

    fun requestDiscardConfirm() {
        _state.update { it?.copy(showDiscardConfirm = true) }
    }

    fun dismissDiscardConfirm() {
        _state.update { it?.copy(showDiscardConfirm = false) }
    }
}
