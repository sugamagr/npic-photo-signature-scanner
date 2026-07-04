package com.npic.photoandsignscanner.features.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.npic.photoandsignscanner.domain.model.CameraCapture
import com.npic.photoandsignscanner.domain.model.CameraMode
import com.npic.photoandsignscanner.domain.model.SignatureSource
import com.npic.photoandsignscanner.domain.model.StudentDraft
import com.npic.photoandsignscanner.domain.repo.DraftRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
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
 * ### Persistence (PRD §8.3)
 * Every draft mutation is fire-and-forget-persisted to [DraftRepository] on
 * [viewModelScope]. On construction, the newest active draft is loaded back into the
 * in-memory [StateFlow] so a warm start immediately sees the last work. The raw
 * [CameraCapture] intentionally stays in memory only — it holds an OpenCV-detected guide
 * box plus a `rawPath` pointing at `cache/drafts/` which itself doesn't survive an OS
 * cache purge; the persistent story is `draft.photoPath` / `draft.signaturePath`, which
 * point at Layer-9 `SourceStore` assets in `filesDir/sources/` that DO survive.
 */
class SharedCaptureHolder(
    private val draftRepository: DraftRepository,
) : ViewModel() {

    private val _capture = MutableStateFlow<CameraCapture?>(null)
    val capture: StateFlow<CameraCapture?> = _capture.asStateFlow()

    private val _draft = MutableStateFlow<StudentDraft?>(null)
    val draft: StateFlow<StudentDraft?> = _draft.asStateFlow()

    init {
        viewModelScope.launch {
            // Warm-start rehydrate: read ONE emission and terminate. `observeActive()`
            // is a cold Flow that emits indefinitely; a `collect{return@collect}` only
            // returns from the lambda, not the collect call, so the coroutine would
            // otherwise stay alive fighting in-memory writes. `firstOrNull()` closes
            // the collector after the first value (or immediate null on empty DB).
            val persisted = draftRepository.observeActive().firstOrNull()
            if (_draft.value == null && persisted != null) {
                _draft.value = persisted
            }
            // Oracle O1-7: also rehydrate the raw CameraCapture so a killed process
            // between shutter press and Edit's commit can pick up the same rawPath +
            // guide-box. latestCapture() returns null when the persisted draft has no
            // rawPath (drawn-signature-first flows) — treat as "no capture yet".
            if (_capture.value == null) {
                val restored = runCatching { draftRepository.latestCapture() }.getOrNull()
                if (restored != null) _capture.value = restored
            }
        }
    }

    fun pushCapture(capture: CameraCapture) {
        _capture.value = capture
        // Persist to survive process kill. Mint a draft if none exists so latestCapture()
        // has a row to hang the rawPath columns off. Oracle O1-7.
        val next = currentOrNew()
        _draft.value = next
        persist(next, capture)
    }

    /**
     * Merge a fresh photo into the draft. Called after Edit's Next action rasterises the
     * final image to disk.
     */
    fun pushPhoto(photoPath: String, mode: CameraMode) {
        val next = currentOrNew().copy(photoPath = photoPath, photoMode = mode)
        _draft.value = next
        persist(next)
    }

    /**
     * Merge a fresh signature into the draft. Called after Signature Draw's Done action
     * rasterises strokes to disk, or after a signature capture flow's Edit completes.
     */
    fun pushSignature(signaturePath: String, source: SignatureSource) {
        val next = currentOrNew().copy(signaturePath = signaturePath, signatureSource = source)
        _draft.value = next
        persist(next)
    }

    /** Direct write (used when signature comes from Camera capture path). */
    fun pushSignaturePath(signaturePath: String) {
        val next = currentOrNew().copy(
            signaturePath = signaturePath,
            signatureSource = SignatureSource.Captured,
        )
        _draft.value = next
        persist(next)
    }

    /** Clear everything — call after Save success or explicit user cancel. */
    fun clear() {
        val existingId = _draft.value?.id
        _capture.value = null
        _draft.value = null
        if (existingId != null) {
            viewModelScope.launch { draftRepository.delete(existingId) }
        }
    }

    /**
     * Return the current draft's UUID string, or mint a fresh draft (and thus a fresh
     * UUID) if none exists yet. Guarantees a stable ID for the entire Camera→Edit→Save
     * arc — critical for [com.npic.photoandsignscanner.data.storage.SourceStore], which
     * keys committed source assets by this ID.
     */
    fun draftIdOrMint(): String {
        val existing = _draft.value
        if (existing != null) return existing.id
        val fresh = currentOrNew()
        _draft.value = fresh
        persist(fresh)
        return fresh.id
    }

    private fun currentOrNew(): StudentDraft {
        return _draft.value ?: StudentDraft(
            id = UUID.randomUUID().toString(),
            photoPath = null,
            signaturePath = null,
            createdAt = Clock.System.now(),
        )
    }

    private fun persist(draft: StudentDraft, capture: CameraCapture? = _capture.value) {
        viewModelScope.launch { draftRepository.upsert(draft, capture) }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Back-compat shim: [current] and [push] preserve the pre-8b Camera→Edit
    // hand-off surface so the Edit route code doesn't need to change.
    // Deprecated aliases; new call sites use [capture] / [pushCapture] directly.
    // ────────────────────────────────────────────────────────────────────────

    val current: StateFlow<CameraCapture?> get() = capture

    fun push(capture: CameraCapture) = pushCapture(capture)

    /**
     * Factory required now that [SharedCaptureHolder] takes a [DraftRepository]
     * constructor param. Wired at the Activity level in [com.npic.photoandsignscanner.
     * app.MainActivity] so every destination that requests `viewModel<SharedCaptureHolder>()`
     * receives the same repo-backed instance.
     */
    class Factory(private val draftRepository: DraftRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SharedCaptureHolder(draftRepository) as T
    }
}
