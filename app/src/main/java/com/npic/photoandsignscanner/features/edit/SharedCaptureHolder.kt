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
            // Warm-start rehydrate. `observeActive` is a Flow but we only want the first
            // emission — collecting forever would fight in-memory writes made during the
            // same session. First non-null wins; after that this ViewModel owns the
            // truth and pushes changes down to the repo, not the other way around.
            val initial = draftRepository.observeActive()
            initial.collect { persisted ->
                if (_draft.value == null && persisted != null) {
                    _draft.value = persisted
                }
                return@collect
            }
        }
    }

    fun pushCapture(capture: CameraCapture) {
        _capture.value = capture
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

    private fun persist(draft: StudentDraft) {
        viewModelScope.launch { draftRepository.upsert(draft) }
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
