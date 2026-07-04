package com.npic.photoandsignscanner.features.camera

import androidx.lifecycle.ViewModel
import com.npic.photoandsignscanner.domain.model.CameraMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Camera presenter. Owns the mode, flash, session count, capture-in-progress flag, and the
 * first-run hint. Deliberately no CameraX bindings live here — [NpicCameraController] owns
 * that surface, and the screen wires the two together.
 *
 * TODO(repo): draft persistence (PRD §8.3) hooks in through a `DraftRepository` injected
 * here. Skipping for the shell — session state resets on back-out, which is acceptable for
 * an internal-preview build.
 */
class CameraViewModel : ViewModel() {

    private val _state = MutableStateFlow(CameraUiState())
    val state: StateFlow<CameraUiState> = _state.asStateFlow()

    fun setMode(mode: CameraMode) {
        _state.update { it.copy(mode = mode, hintVisible = true, lastError = null) }
    }

    fun cycleFlash() {
        _state.update { it.copy(flash = it.flash.cycle()) }
    }

    fun dismissHint() {
        _state.update { it.copy(hintVisible = false) }
    }

    fun onCaptureStarted() {
        _state.update { it.copy(capturing = true, lastError = null) }
    }

    fun onCaptureFinished() {
        _state.update {
            it.copy(
                capturing = false,
                sessionCount = it.sessionCount + 1,
            )
        }
    }

    fun onCaptureFailed(message: String) {
        _state.update { it.copy(capturing = false, lastError = message) }
    }
}
