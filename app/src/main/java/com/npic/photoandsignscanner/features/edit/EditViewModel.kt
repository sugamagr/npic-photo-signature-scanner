package com.npic.photoandsignscanner.features.edit

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.npic.photoandsignscanner.domain.model.CropQuad
import com.npic.photoandsignscanner.data.imaging.BitmapAdjustments
import com.npic.photoandsignscanner.data.imaging.BitmapFilters
import com.npic.photoandsignscanner.data.imaging.EditRenderer
import com.npic.photoandsignscanner.data.storage.SourceStore
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
    private val editRenderer: EditRenderer,
    private val sourceStore: SourceStore,
    private val draftIdProvider: () -> String,
) : ViewModel() {

    private val _state = MutableStateFlow<EditUiState?>(null)
    val state: StateFlow<EditUiState?> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var detectionJob: Job? = null
    private var thumbnailsJob: Job? = null
    private var commitJob: Job? = null
    private var livePreviewJob: Job? = null

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

        // Oracle O1-2: DO NOT recycle prior bitmaps synchronously here. A Compose frame may
        // still be reading state.sourceBitmap when seed() fires with a new capture; recycling
        // before the state.value overwrite lets that read hit a freed native buffer → crash
        // or corrupted draw. Capture the prior bitmaps, publish the new state first, then
        // defer recycle to the next main-thread tick so Compose has moved past the mutation
        // frame. The ~10-30 MB working set is reclaimed within one frame — still bounded.
        val toRecycle = current?.let { prior ->
            buildList<Bitmap> {
                prior.sourceBitmap?.let(::add)
                prior.previewBitmap?.let(::add)
                prior.livePreviewBitmap?.let(::add)
                addAll(prior.filterThumbnails.values)
            }
        }.orEmpty()

        _state.value = EditUiState(edit = EditState.seedFrom(capture))

        if (toRecycle.isNotEmpty()) {
            // Oracle #1 C1 (qc-round-10): tie deferred recycle to viewModelScope so it cancels
            // cleanly on onCleared() — Handler.post could fire after VM death causing use-after-
            // recycle on bitmaps a stale Compose read holds. Dispatchers.Main.immediate keeps
            // the same "next main-thread tick" semantic as post() so Compose still moves past
            // the mutation frame before recycle runs. No wasteful Handler allocation.
            viewModelScope.launch(Dispatchers.Main.immediate) {
                toRecycle.forEach { runCatching { it.recycle() } }
            }
        }

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
                // Bug#3: prime the live preview so Filter/Adjust/Rotate tabs show a
                // meaningful image (Auto filter applied) the first time the user opens them.
                scheduleLivePreview()
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
            val raw = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }) ?: return@withContext null
            // CameraX (via LifecycleCameraController) writes the JPEG with an EXIF
            // orientation tag; the pixel buffer stays in raw-sensor orientation. Coil
            // (used in Gallery/Detail) respects EXIF, but BitmapFactory.decodeFile does
            // NOT — so without this rotate the Edit viewport shows the photo sideways
            // (m1720). We must also physically rotate here because CameraViewModel.capture
            // already swaps guide-box dimensions based on EXIF, so guideBoxImageSpace
            // addresses ROTATED-DISPLAY coordinates; leaving the source in raw-sensor
            // orientation makes the crop quad hit far outside bitmap bounds → gray blob.
            applyExifOrientation(raw, path)
        } catch (t: Throwable) {
            Log.e(TAG, "decodeSource failed for $path: ${t.message}", t)
            null
        }
    }

    private fun applyExifOrientation(source: Bitmap, path: String): Bitmap {
        val orientation = runCatching {
            ExifInterface(path).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        if (orientation == ExifInterface.ORIENTATION_NORMAL ||
            orientation == ExifInterface.ORIENTATION_UNDEFINED) return source
        val matrix = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { postRotate(90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_TRANSVERSE -> { postRotate(270f); postScale(-1f, 1f) }
            }
        }
        return try {
            val rotated = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
            if (rotated !== source) source.recycle()
            rotated
        } catch (t: Throwable) {
            Log.e(TAG, "applyExifOrientation failed for $path: ${t.message}", t)
            source
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
        // Crop-tool preview intentionally displays raw source + overlay; no re-render.
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
        scheduleLivePreview()
    }

    fun setAdjustments(adjustments: Adjustments) {
        _state.update { it?.copy(edit = it.edit.copy(adjustments = adjustments)) }
        scheduleLivePreview()
    }

    fun rotateCw() {
        _state.update { it?.copy(edit = it.edit.copy(rotation = it.edit.rotation.rotateCw())) }
        scheduleLivePreview()
    }

    fun rotateCcw() {
        _state.update { it?.copy(edit = it.edit.copy(rotation = it.edit.rotation.rotateCcw())) }
        scheduleLivePreview()
    }

    fun setStraighten(degrees: Float) {
        _state.update { it?.copy(edit = it.edit.copy(rotation = it.edit.rotation.withStraighten(degrees))) }
        scheduleLivePreview()
    }

    /**
     * Bug#3 fix. Schedule a debounced re-render of [EditUiState.previewBitmap] through
     * [EditRenderer] using the current [EditState]. Result lands on
     * [EditUiState.livePreviewBitmap] which the viewport displays in preference to the raw
     * source (except in Crop mode, where the overlay needs the untransformed source).
     *
     * Runs on [Dispatchers.Default]. The 1920px preview means each render is ~30-55 ms
     * on a mid-tier Snapdragon 6-series (measured on A35 5G) — still inside PRD §4.5
     * "<100 ms" budget so no debounce delay is needed. Prior render is cancelled and
     * its result recycled so slider drags don't stack.
     */
    private fun scheduleLivePreview() {
        val snapshot = _state.value ?: return
        val preview = snapshot.previewBitmap ?: return
        val source = snapshot.sourceBitmap ?: return
        val activeRawPath = snapshot.edit.source.rawPath

        // CropQuad ordinates live in FULL-RESOLUTION source coordinate space by design
        // (EditState.seedFrom KDoc: "image space of the source"). The live preview renders
        // against a 384px-long-side downsample of that source, so we must translate the
        // quad into preview space before EditRenderer runs — otherwise OpenCV's
        // warpPerspective samples entirely outside the preview buffer, filling the output
        // with the BORDER_CONSTANT (mid-gray in ARGB_8888) — the m1653/m1780 gray blob.
        // Uniform scale is correct because downsample() preserves aspect ratio.
        val scale = preview.width.toFloat() / source.width.toFloat()
        val editSnapshot = snapshot.edit.copy(crop = snapshot.edit.crop.scaledBy(scale))

        livePreviewJob?.cancel()
        livePreviewJob = viewModelScope.launch {
            val rendered = withContext(Dispatchers.Default) {
                runCatching { editRenderer.render(preview, editSnapshot) }
                    .onFailure { Log.e(TAG, "live-preview render failed: ${it.message}", it) }
                    .getOrNull()
            } ?: return@launch
            // Oracle #1 C2 (qc-round-10): StateFlow.update{} may retry its lambda if a
            // concurrent producer wins the CAS, so any recycle inside the lambda could
            // fire twice on the same bitmap. Capture the old ref, let update{} commit,
            // then recycle exactly once outside the retry loop.
            var stale: Bitmap? = null
            _state.update { s ->
                if (s == null || s.edit.source.rawPath != activeRawPath) {
                    rendered.recycle()
                    s
                } else {
                    stale = s.livePreviewBitmap
                    s.copy(livePreviewBitmap = rendered)
                }
            }
            stale?.recycle()
        }
    }

    fun requestDiscardConfirm() {
        _state.update { it?.copy(showDiscardConfirm = true) }
    }

    fun dismissDiscardConfirm() {
        _state.update { it?.copy(showDiscardConfirm = false) }
    }

    /**
     * PRD §5.5 commit path. Runs [EditRenderer.render] on the full-resolution source
     * bitmap using the current [EditState], then persists the result via [SourceStore] as
     * either `sources/{draftId}_photo.jpg` (Photo mode, long-side 1600) or
     * `sources/{draftId}_signature.jpg` (Signature mode, long-side 1500).
     *
     * The written path is published on [EditUiState.committedSourcePath]. The screen
     * observes that field via [LaunchedEffect] and hands the path to the
     * [MainActivity]-provided `onNext` callback, which pushes it into
     * [SharedCaptureHolder].
     *
     * Guarded against double-tap via [EditUiState.committing]. Concurrent-safe against
     * `seed()` racing: cancels a prior commit if a new seed lands mid-render, and
     * discards the render result if a newer capture replaced the state.
     */
    fun commitEdits() {
        val snapshot = _state.value ?: return
        if (snapshot.committing || snapshot.committedSourcePath != null) return
        val source = snapshot.sourceBitmap ?: run {
            _state.update { it?.copy(lastError = "Photo still loading — try again") }
            return
        }
        commitJob?.cancel()
        val activeRawPath = snapshot.edit.source.rawPath
        val editSnapshot = snapshot.edit
        val draftId = draftIdProvider()

        _state.update { it?.copy(committing = true, lastError = null) }

        commitJob = viewModelScope.launch {
            val path = withContext(Dispatchers.Default) {
                val rendered = runCatching { editRenderer.render(source, editSnapshot) }
                    .onFailure { Log.e(TAG, "EditRenderer.render failed: ${it.message}", it) }
                    .getOrNull() ?: return@withContext null
                try {
                    when (editSnapshot.mode) {
                        CameraMode.Photo     -> sourceStore.writePhoto(draftId, rendered)
                        CameraMode.Signature -> sourceStore.writeSignature(draftId, rendered)
                    }
                } finally {
                    // Fresh allocation from EditRenderer.render — always ours to recycle.
                    rendered.recycle()
                }
            }
            _state.update { s ->
                if (s == null || s.edit.source.rawPath != activeRawPath) s
                else if (path == null) s.copy(committing = false, lastError = "Couldn't save the edited image")
                else s.copy(committing = false, committedSourcePath = path)
            }
        }
    }

    /**
     * Clear [EditUiState.committedSourcePath] after the destination has consumed it and
     * navigated. Prevents a second navigation on process restore or configuration change.
     */
    fun consumeCommittedSource() {
        _state.update { it?.copy(committedSourcePath = null) }
    }

    override fun onCleared() {
        // Recycle cached bitmaps so leaving Edit reclaims the ~10-30 MB working set.
        _state.value?.let { s ->
            s.sourceBitmap?.recycle()
            s.previewBitmap?.recycle()
            s.livePreviewBitmap?.recycle()
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
        private val editRenderer: EditRenderer,
        private val sourceStore: SourceStore,
        private val draftIdProvider: () -> String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            EditViewModel(
                detectPhotoEdges,
                detectSignatureInk,
                bitmapFilters,
                bitmapAdjustments,
                editRenderer,
                sourceStore,
                draftIdProvider,
            ) as T
    }

    private companion object {
        const val TAG = "EditViewModel"

        // Live-preview render size (m1863 → m1869). 1440 was crisp on-viewport but the
        // user asked for slightly more resolution. 1920 keeps pixel-perfect rendering
        // with clear margin over the viewport height and matches what mainstream scanner
        // apps use (Adobe Scan ~1600, Microsoft Lens ~2048). Filter+adjust cost on a
        // ~854×1920 preview is 30-55ms on Snapdragon 6 Gen 1 (A35) — comfortably inside
        // PRD §4.5's <100ms slider budget so Adjust drag stays real-time without
        // debouncing. Memory hit is ~5.9MB per preview bitmap.
        const val PREVIEW_LONG_SIDE = 1920

        const val THUMBNAIL_LONG_SIDE = 192
    }
}
