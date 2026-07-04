package com.npic.photoandsignscanner.features.camera

import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.npic.photoandsignscanner.domain.model.CameraCapture
import com.npic.photoandsignscanner.domain.model.CameraMode
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

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

    private var captureJob: Job? = null

    fun setMode(mode: CameraMode) {
        _state.update { it.copy(mode = mode, hintVisible = true, lastError = null) }
    }

    fun cycleFlash() {
        _state.update { it.copy(flash = it.flash.cycle()) }
    }

    fun dismissHint() {
        _state.update { it.copy(hintVisible = false) }
    }

    /**
     * Own the entire shutter workflow inside [viewModelScope] so the coroutine survives
     * screen re-composition but is cancelled cleanly when the ViewModel itself dies
     * (config change → new VM instance, or Activity destroy). The screen previously
     * launched this in `rememberCoroutineScope()` which orphaned the coroutine on back-out.
     */
    fun capture(
        controller: NpicCameraController,
        target: File,
        previewGuide: PreviewGuide?,
        onDone: (CameraCapture) -> Unit,
    ) {
        if (_state.value.capturing) return
        val currentMode = _state.value.mode
        _state.update { it.copy(capturing = true, lastError = null) }
        captureJob?.cancel()
        captureJob = viewModelScope.launch {
            try {
                val file = controller.takePicture(target)
                // Layer 11: now that we have the WRITTEN JPEG, decode just its bounds
                // (cheap — no pixel buffer allocated) and project the overlay's
                // preview-space rect into the image's pixel grid via PreviewGuide.toImageSpace.
                // Result feeds EditRenderer's OpenCV seed area (PRD §7.1 / §7.2).
                val guideBoxImageSpace = previewGuide?.let { pg ->
                    withContext(Dispatchers.IO) {
                        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(file.absolutePath, opts)
                        if (opts.outWidth <= 0 || opts.outHeight <= 0) return@withContext null
                        // Oracle O2-3.1: outWidth/outHeight are RAW pixel dimensions and
                        // ignore EXIF orientation. Some CameraX-backed devices write a
                        // rotated JPEG whose display shape swaps w/h. Read the EXIF tag
                        // and swap so PreviewGuide.toImageSpace sees the same aspect the
                        // decoder will materialise when the bitmap is finally rendered.
                        val exif = androidx.exifinterface.media.ExifInterface(file.absolutePath)
                        val rotation = exif.getAttributeInt(
                            androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                            androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL,
                        )
                        val swap =
                            rotation == androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 ||
                            rotation == androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 ||
                            rotation == androidx.exifinterface.media.ExifInterface.ORIENTATION_TRANSPOSE ||
                            rotation == androidx.exifinterface.media.ExifInterface.ORIENTATION_TRANSVERSE
                        val (imgW, imgH) = if (swap) opts.outHeight to opts.outWidth
                        else opts.outWidth to opts.outHeight
                        pg.toImageSpace(imgW, imgH)
                    }
                }
                _state.update {
                    it.copy(
                        capturing = false,
                        sessionCount = it.sessionCount + 1,
                        lastCapturePath = file.absolutePath,
                    )
                }
                onDone(
                    CameraCapture(
                        rawPath            = file.absolutePath,
                        mode               = currentMode,
                        guideBoxImageSpace = guideBoxImageSpace,
                        capturedAt         = Clock.System.now(),
                    )
                )
            } catch (t: Throwable) {
                _state.update {
                    it.copy(capturing = false, lastError = t.message ?: "Capture failed")
                }
            }
        }
    }
}
