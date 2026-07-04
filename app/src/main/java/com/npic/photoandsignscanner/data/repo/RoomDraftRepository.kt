package com.npic.photoandsignscanner.data.repo

import com.npic.photoandsignscanner.data.db.DraftDao
import com.npic.photoandsignscanner.data.db.toDraft
import com.npic.photoandsignscanner.data.db.toEntity
import com.npic.photoandsignscanner.domain.model.StudentDraft
import com.npic.photoandsignscanner.domain.repo.DraftRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock

/**
 * Room-backed [DraftRepository] (PRD §8.3). Trivial bridge over [DraftDao] — the
 * "at most one active draft" v1.0 constraint is enforced upstream by SharedCaptureHolder
 * always upserting the same UUID.
 */
class RoomDraftRepository(
    private val dao: DraftDao,
) : DraftRepository {

    override fun observeActive(): Flow<StudentDraft?> =
        dao.observeActive().map { it?.toDraft() }

    override suspend fun getById(id: String): StudentDraft? =
        dao.getById(id)?.toDraft()

    override suspend fun upsert(draft: StudentDraft) {
        dao.upsert(draft.toEntity(updatedAt = Clock.System.now()))
    }

    override suspend fun delete(id: String) {
        dao.delete(id)
    }

    override suspend fun clear() {
        dao.clear()
    }
}
