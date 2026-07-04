package com.npic.photoandsignscanner.features.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.npic.photoandsignscanner.domain.model.CameraCapture
import com.npic.photoandsignscanner.domain.model.CameraMode
import com.npic.photoandsignscanner.domain.model.SignatureSource
import com.npic.photoandsignscanner.domain.model.StudentDraft
import com.npic.photoandsignscanner.domain.model.StudentRecord
import com.npic.photoandsignscanner.domain.repo.DraftRepository
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
 * ### Persistence
 * m2354 Bug H (user directive m2355 option 1): draft persistence is DISABLED. The
 * [DraftRepository] param stays wired so the Factory/DI shape doesn't ripple — but the
 * internal [persist] helper is now a no-op and the constructor skips warm-start
 * rehydrate. Every launch starts from an empty in-memory state. Users reported the old
 * resume-prompt as buggy ("even if i was not workign on a photo it shows so") and
 * decided they don't need draft survival across process death. DraftEntity + Room v2
 * schema stay unchanged for a future revival (auto-save UX v2) without a migration.
 */
class SharedCaptureHolder(
    @Suppress("unused") private val draftRepository: DraftRepository,
) : ViewModel() {

    private val _capture = MutableStateFlow<CameraCapture?>(null)
    val capture: StateFlow<CameraCapture?> = _capture.asStateFlow()

    private val _draft = MutableStateFlow<StudentDraft?>(null)
    val draft: StateFlow<StudentDraft?> = _draft.asStateFlow()

    // m2232 fix: when Detail launches an add-media / edit-media flow against an EXISTING
    // record, the target's id + classNum + serial + displayName + namingKind must survive
    // the whole Camera → Edit → (SignatureDraw) → confirm arc. Non-null here means "on
    // commit, call repo.replace(target.id, ...) instead of repo.save(...)"; the Save
    // sheet is bypassed for a lightweight ConfirmUpdate sheet so the user can't
    // accidentally rewrite class/serial/name and clone a phantom record.
    private val _target = MutableStateFlow<StudentRecord?>(null)
    val target: StateFlow<StudentRecord?> = _target.asStateFlow()

    // m2354 Bug H (m2355 option 1): no warm-start rehydrate. Every process launch starts
    // with empty _capture / _draft / _target. Prior Oracle O1-7 + C3 rationale was
    // sound but the persistence UX had a false-positive rate the user found worse than
    // losing occasional in-flight work. Restore this block along with persist() if a
    // future auto-save UX (v2) is revived.

    fun pushCapture(capture: CameraCapture) {
        _capture.value = capture
        // Oracle #5 A2 (qc-round-10): a fresh Camera capture must clear _target so nav
        // routes to Save, not stale UpdateConfirm. Reproducer: Detail → edit-media
        // (target set via beginUpdate) → user backs out to Gallery → Camera →
        // Capture. Without this line, target still holds the old record and Edit's
        // commit branch nav-routes to UpdateConfirm which then tries to repo.replace()
        // an unrelated record.
        _target.value = null
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

    /**
     * Overwrite the entire draft in one atomic publish + persist. Used by flows that
     * synthesise a fresh draft outside the incremental push* API — currently:
     *
     * 1. **Duplicate-to-another-class (user m1555)**: [DetailDestination]'s duplicate
     *    flow mints a new UUID, copies source assets to `sources/{newId}_*.jpg` via
     *    [com.npic.photoandsignscanner.data.repo.DuplicateAssetsUseCase], then hands the
     *    resulting draft to this method. Going through `pushPhoto` + `pushSignature`
     *    would work but would emit TWO persist ops racing on the same row and would
     *    stamp `updatedAt` twice — cleaner to seed atomically.
     *
     * The optional [capture] slot is provided for symmetry with [pushCapture] so callers
     * that already know the raw camera frame (e.g. Detail's edit-existing path) can pipe
     * it through. Duplicate flow passes null because a duplicated record has no live raw
     * capture — its assets are already the SourceStore-persisted finals from the source
     * record.
     */
    fun replaceDraft(draft: StudentDraft, capture: CameraCapture? = null) {
        _draft.value = draft
        _capture.value = capture
        // Duplicate-to-another-class explicitly wants a fresh record — null any prior
        // update target so the nav layer routes through Save (not ConfirmUpdate).
        _target.value = null
        persist(draft, capture)
    }

    /**
     * m2232: seed the holder for an "update this existing record" flow. Mints a fresh
     * draft that REUSES the record's UUID as its own id so Layer 9 SourceStore writes
     * (`sources/{draftId}_*.jpg`) overwrite the record's existing asset files in place —
     * no orphaned assets, no id-remap dance on repo.replace. Pre-fills photoPath and
     * signaturePath from the record so Save-sheet fallbacks (`add photo or signature`)
     * don't fire, and downstream push* calls only fill the missing slot.
     *
     * The target itself is stashed on [_target] so the nav layer can branch
     * Save → ConfirmUpdate on `target.value != null`.
     */
    fun beginUpdate(record: StudentRecord) {
        val existing = record.signaturePath
        val seededDraft = StudentDraft(
            id = record.id,
            photoPath = record.photoPath.takeIf { it.isNotBlank() },
            signaturePath = existing,
            photoMode = CameraMode.Photo,
            signatureSource = if (existing != null) SignatureSource.Captured else null,
            createdAt = record.createdAt,
        )
        _target.value = record
        _draft.value = seededDraft
        _capture.value = null
        persist(seededDraft)
    }

    /** Clear everything — call after Save success or explicit user cancel. */
    fun clear() {
        _capture.value = null
        _draft.value = null
        _target.value = null
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

    // m2354 Bug H (m2355 option 1): no-op. Kept as a function so every call site's
    // `persist(next)` / `persist(next, capture)` continues to compile — restoring
    // persistence in a future auto-save UX revival is a single-line change here.
    @Suppress("UNUSED_PARAMETER")
    private fun persist(draft: StudentDraft, capture: CameraCapture? = _capture.value) = Unit

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
