package com.npic.photoandsignscanner.features.edit

import androidx.lifecycle.ViewModel
import com.npic.photoandsignscanner.domain.model.CameraCapture
import com.npic.photoandsignscanner.domain.model.SignatureSource
import com.npic.photoandsignscanner.domain.model.StudentDraft
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock
import java.util.UUID

/**
 * Activity-scoped hand-off between Camera → Edit → Signature (optional) → Save. Carries
 * two things: the last raw [CameraCapture] (Edit still needs rawPath + guideBox + mode)
 * and the in-progress [StudentDraft] the Save dialog will persist.
 *
 * All destinations obtain the SAME instance via `viewModel(activityOwner)` so navigation
 * stays stringly-typed without shoving objects into route args.
 *
 * Not a persistent store — process death wipes it. Raw JPEGs live on disk at
 * `capture.rawPath` / `draft.photoPath` / `draft.signaturePath`, so if the app returns
 * from a cold start we either scan the drafts folder (PRD §8.3 draft persistence,
 * deferred) or drop back to Gallery.
 *
 * TODO(repo): swap for a `DraftRepository`-backed source when the Room layer lands.
 */
class SharedCaptureHolder : ViewModel() {

    private val _capture = MutableStateFlow<CameraCapture?>(null)
    val capture: StateFlow<CameraCapture?> = _capture.asStateFlow()

    private val _draft = MutableStateFlow<StudentDraft?>(null)
    val draft: StateFlow<StudentDraft?> = _draft.asStateFlow()

    fun pushCapture(capture: CameraCapture) {
        _capture.value = capture
    }

    /**
     * Merge a fresh photo into the draft. Called after Edit's Next action rasterises the
     * final image to disk.
     */
    fun pushPhoto(photoPath: String, mode: com.npic.photoandsignscanner.domain.model.CameraMode) {
        _draft.value = currentOrNew().copy(photoPath = photoPath, photoMode = mode)
    }

    /**
     * Merge a fresh signature into the draft. Called after Signature Draw's Done action
     * rasterises strokes to disk, or after a signature capture flow's Edit completes.
     */
    fun pushSignature(signaturePath: String, source: SignatureSource) {
        _draft.value = currentOrNew().copy(signaturePath = signaturePath, signatureSource = source)
    }

    /** Direct write (used when signature comes from Camera capture path). */
    fun pushSignaturePath(signaturePath: String) {
        _draft.value = currentOrNew().copy(signaturePath = signaturePath, signatureSource = SignatureSource.Captured)
    }

    /** Clear everything — call after Save success or explicit user cancel. */
    fun clear() {
        _capture.value = null
        _draft.value = null
    }

    private fun currentOrNew(): StudentDraft {
        return _draft.value ?: StudentDraft(
            id = UUID.randomUUID().toString(),
            photoPath = null,
            signaturePath = null,
            createdAt = Clock.System.now(),
        )
    }

    // ────────────────────────────────────────────────────────────────────────
    // Back-compat shim: [current] and [push] preserve the pre-8b Camera→Edit
    // hand-off surface so the Edit route code doesn't need to change.
    // Deprecated aliases; new call sites use [capture] / [pushCapture] directly.
    // ────────────────────────────────────────────────────────────────────────

    val current: StateFlow<CameraCapture?> get() = capture

    fun push(capture: CameraCapture) = pushCapture(capture)
}
