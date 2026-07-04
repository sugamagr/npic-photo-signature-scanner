package com.npic.photoandsignscanner.domain.repo

import com.npic.photoandsignscanner.domain.model.CameraCapture
import com.npic.photoandsignscanner.domain.model.StudentDraft
import kotlinx.coroutines.flow.Flow

/**
 * Persistent capture-in-progress store (PRD §8.3). Backs the Gallery's "Resume capture
 * in progress?" prompt: on cold start, if [observeActive] emits a non-null draft with
 * [StudentDraft.hasAnyMedia], the Gallery surfaces a Resume/Discard dialog.
 *
 * v1.0 keeps one active draft at a time; the interface is written so a future multi-
 * draft world can drop in without churn on call sites — [observeActive] returns the
 * newest, [upsert] is content-driven not slot-driven.
 */
interface DraftRepository {

    /** Cold flow of the newest active draft (or null if drafts table is empty). */
    fun observeActive(): Flow<StudentDraft?>

    /** Fetch a specific draft by ID. Used when resuming a specific in-progress arc. */
    suspend fun getById(id: String): StudentDraft?

    /**
     * Insert-or-replace by [StudentDraft.id]. Called each time SharedCaptureHolder
     * mutates its draft (Camera push, Edit commit, Signature push) so the resume-prompt
     * has a live snapshot to work from.
     *
     * Optional [capture] carries the raw CameraX JPEG path + guide box so a process kill
     * between shutter press and Edit commit can rehydrate the [CameraCapture] object.
     * Null when the draft state predates any capture (drawn-signature-first). Oracle O1-7.
     */
    suspend fun upsert(draft: StudentDraft, capture: CameraCapture? = null)

    /**
     * Fetch the newest draft's raw [CameraCapture], if any. Used by SharedCaptureHolder's
     * warm-start rehydrate so Edit can resume from a killed process. Oracle O1-7.
     */
    suspend fun latestCapture(): CameraCapture?

    /** Delete a specific draft — called after Save success or explicit user discard. */
    suspend fun delete(id: String)

    /** Wipe every draft. Convenience for "Discard" on the resume-prompt. */
    suspend fun clear()
}
