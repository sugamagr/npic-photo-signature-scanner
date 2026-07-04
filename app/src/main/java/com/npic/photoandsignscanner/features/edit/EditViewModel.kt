package com.npic.photoandsignscanner.features.edit

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.npic.photoandsignscanner.domain.model.CropQuad
import com.npic.photoandsignscanner.data.imaging.BitmapAdjustments
import com.npic.photoandsignscanner.data.imaging.BitmapFilters
import com.npic.photoandsignscanner.domain.model.Adjustments
import com.npic.photoandsignscanner.domain.model.AspectLock
import com.npic.photoandsignscanner.domain.model.CameraCapture
import com.npic.photoandsignscanner.domain.model.CameraMode
import com.npic.photoandsignscanner.domain.model.DetectedCrop
import com.npic.photoandsignscanner.domain.model.EditState
import com.npic.photoandsignscanner.domain.model.FilterPreset
import com.npic.photoandsignscanner.domain.model.RectI
import com.npic.photoandsignscanner.domain.usecase.DetectPhotoEdges
import com.npic.photoandsignscanner.domain.usecase.DetectSignatureInk
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Edit presenter. Non-destructive: every knob mutates [EditState] in-memory only. The final
 * render pipeline (PRD §5.5 / §7) runs on Save when the user commits.
 *
 * Seeding fans out to three parallel jobs on background dispatchers:
 *   1. Decode the source JPEG (Dispatchers.IO) → publishes [EditUiState.sourceBitmap] +
 *      [EditUiState.previewBitmap] (384px longest-side downsample for Adjust drag).
 *   2. Run [DetectPhotoEdges] / [DetectSignatureInk] on Dispatchers.Default → publishes
 *      the detected crop with a 220ms crossfade origin flag. Falls back to the guide-box
 *      quad seeded synchronously at mount so the user never sees an empty overlay.
 *   3. Render 8 filter thumbnails at 192px (DESIGN §6.18) on Dispatchers.Default → publishes
 *      [EditUiState.filterThumbnails] map, keyed by [FilterPreset].
 *
 * TODO(repo): Save flow (Room insert + JPEG binary-search compression per PRD §5.5 / §6) is
 * left as a stub via [next] until the Save layer lands.
 */
class EditViewModel(
    private val detectPhotoEdges: DetectPhotoEdges,
    private val detectSignatureInk: DetectSignatureInk,
    private val bitmapFilters: BitmapFilters,
    private val bitmapAdjustments: BitmapAdjustments,
) : ViewModel() {

    private val _state = MutableStateFlow<EditUiState?>(null)
    val state: StateFlow<EditUiState?> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var detectionJob: Job? = null
    private var thumbnailsJob: Job? = null

    /**
     * Seed from a fresh capture. No-op if the same capture is already loaded. Cancels any
     * in-flight jobs for a previous capture before starting fresh ones.
     */
    fun seed(capture: CameraCapture) {
        val current = _state.value
        if (current != null && current.edit.source.rawPath == capture.rawPath) return

        loadJob?.cancel()
        detectionJob?.cancel()
        thumbnailsJob?.cancel()

        // Recycle prior-capture bitmaps before overwriting the state; otherwise the
        // ~10-30 MB working set from the previous edit leaks until GC eventually reclaims it.
        current?.let { prior ->
            prior.sourceBitmap?.recycle()
            prior.previewBitmap?.recycle()
            prior.filterThumbnails.values.forEach { it.recycle() }
        }

        _state.value = EditUiState(edit = EditState.seedFrom(capture))

        val activeRawPath = capture.rawPath
        loadJob = viewModelScope.launch {
            val decoded = decodeSource(capture.rawPath)
            if (decoded != null) {
                val preview = downsample(decoded, PREVIEW_LONG_SIDE)
                _state.update { s ->
                    // Guard against a newer seed() replacing the state while we were decoding.
                    if (s == null || s.edit.source.rawPath != activeRawPath) {
                        decoded.recycle()
                        preview.recycle()
                        s
                    } else {
                        s.copy(sourceBitmap = decoded, previewBitmap = preview)
                    }
                }
                launchDetection(capture, decoded, activeRawPath)
                launchThumbnails(preview, activeRawPath)
            } else {
                _state.update { s ->
                    if (s?.edit?.source?.rawPath == activeRawPath) {
                        s.copy(lastError = "Couldn't decode capture")
                    } else s
                }
            }
        }
    }

    private fun launchDetection(capture: CameraCapture, source: Bitmap, activeRawPath: String) {
        detectionJob = viewModelScope.launch {
            // Null guide-box → user shuttered before overlay laid out. Use full decoded-image
            // bounds as the seed rect so IoU scoring still runs against a real region rather
            // than the (0,0,0,0) sentinel that Layer 6 originally passed through.
            val seed = capture.guideBoxImageSpace
                ?: RectI(0, 0, source.width, source.height)
            val detected = when (capture.mode) {
                CameraMode.Photo -> detectPhotoEdges(source, seed)
                CameraMode.Signature -> detectSignatureInk(source, seed)
            }
            val quad = detectedToQuad(detected, seed)
            _state.update { s ->
                // Discard the result if a newer seed replaced the state under us.
                if (s == null || s.edit.source.rawPath != activeRawPath) s
                else s.copy(
                    edit = s.edit.copy(crop = quad),
                    detectionOrigin = detected.origin,
                )
            }
        }
    }

    private fun launchThumbnails(preview: Bitmap, activeRawPath: String) {
        thumbnailsJob = viewModelScope.launch {
            val thumbs = withContext(Dispatchers.Default) {
                val base = downsample(preview, THUMBNAIL_LONG_SIDE)
                FilterPreset.entries.associateWith { preset ->
                    val copy = base.copy(Bitmap.Config.ARGB_8888, true)
                    bitmapFilters.apply(copy, preset)
                    copy
                }.also { base.recycle() }
            }
            _state.update { s ->
                if (s == null || s.edit.source.rawPath != activeRawPath) {
                    thumbs.values.forEach { it.recycle() }
                    s
                } else {
                    s.copy(filterThumbnails = thumbs)
                }
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private suspend fun decodeSource(path: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            })
        } catch (t: Throwable) {
            Log.e(TAG, "decodeSource failed for $path: ${t.message}", t)
            null
        }
    }

    private fun downsample(source: Bitmap, longSide: Int): Bitmap {
        val current = maxOf(source.width, source.height)
        if (current <= longSide) return source.copy(Bitmap.Config.ARGB_8888, true)
        val scale = longSide.toFloat() / current.toFloat()
        val w = (source.width * scale).toInt().coerceAtLeast(1)
        val h = (source.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, w, h, true)
    }

    private fun detectedToQuad(detected: DetectedCrop, fallback: RectI): CropQuad =
        when (detected) {
            is DetectedCrop.Quad -> CropQuad(
                tl = Offset(detected.topLeft.x.toFloat(), detected.topLeft.y.toFloat()),
                tr = Offset(detected.topRight.x.toFloat(), detected.topRight.y.toFloat()),
                br = Offset(detected.bottomRight.x.toFloat(), detected.bottomRight.y.toFloat()),
                bl = Offset(detected.bottomLeft.x.toFloat(), detected.bottomLeft.y.toFloat()),
            )
            is DetectedCrop.Rect -> CropQuad(
                tl = Offset(detected.left.toFloat(), detected.top.toFloat()),
                tr = Offset(detected.right.toFloat(), detected.top.toFloat()),
                br = Offset(detected.right.toFloat(), detected.bottom.toFloat()),
                bl = Offset(detected.left.toFloat(), detected.bottom.toFloat()),
            )
        }.takeIf { detected.origin == DetectedCrop.Origin.Detected }
            ?: CropQuad(
                tl = Offset(fallback.left.toFloat(), fallback.top.toFloat()),
                tr = Offset(fallback.right.toFloat(), fallback.top.toFloat()),
                br = Offset(fallback.right.toFloat(), fallback.bottom.toFloat()),
                bl = Offset(fallback.left.toFloat(), fallback.bottom.toFloat()),
            )

    // ------------------------------------------------------------------ mutators

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

    override fun onCleared() {
        // Recycle cached bitmaps so leaving Edit reclaims the ~10-30 MB working set.
        _state.value?.let { s ->
            s.sourceBitmap?.recycle()
            s.previewBitmap?.recycle()
            s.filterThumbnails.values.forEach { it.recycle() }
        }
        super.onCleared()
    }

    /**
     * ViewModel factory. Threads the imaging dependencies down from Activity scope so the
     * ViewModel stays constructor-injected (no service-locator). Room + DI wiring lands with
     * the Save layer.
     */
    class Factory(
        private val detectPhotoEdges: DetectPhotoEdges,
        private val detectSignatureInk: DetectSignatureInk,
        private val bitmapFilters: BitmapFilters,
        private val bitmapAdjustments: BitmapAdjustments,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            EditViewModel(detectPhotoEdges, detectSignatureInk, bitmapFilters, bitmapAdjustments) as T
    }

    private companion object {
        const val TAG = "EditViewModel"
        const val PREVIEW_LONG_SIDE = 384
        const val THUMBNAIL_LONG_SIDE = 192
    }
}
