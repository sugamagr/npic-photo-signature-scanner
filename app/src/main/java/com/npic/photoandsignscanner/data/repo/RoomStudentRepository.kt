package com.npic.photoandsignscanner.data.repo

import android.database.sqlite.SQLiteConstraintException
import com.npic.photoandsignscanner.data.db.StudentDao
import com.npic.photoandsignscanner.data.db.normalizeNameKey
import com.npic.photoandsignscanner.data.db.toEntity
import com.npic.photoandsignscanner.data.db.toRecord
import com.npic.photoandsignscanner.data.storage.SourceStore
import com.npic.photoandsignscanner.domain.model.ClassNum
import com.npic.photoandsignscanner.domain.model.NamingMode
import com.npic.photoandsignscanner.domain.model.SaveInput
import com.npic.photoandsignscanner.domain.model.SaveResult
import com.npic.photoandsignscanner.domain.model.StudentDraft
import com.npic.photoandsignscanner.domain.model.StudentRecord
import com.npic.photoandsignscanner.domain.repo.StudentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock

/**
 * Room-backed [StudentRepository] (PRD §8.1). All heavy lifting is in the DAO's
 * @Transaction methods — this class exists to bridge domain shapes ↔ Room entities and
 * to sequence the "duplicate check → insert / return DuplicateFound" flow required by
 * PRD §4.7.
 *
 * The DAO's UNIQUE(classNum, serial) index gives us a DB-level backstop against the
 * check-then-write race: if two saves collide, one commits and the other trips
 * [SQLiteConstraintException] which we translate into [SaveResult.DuplicateFound].
 */
class RoomStudentRepository(
    private val dao: StudentDao,
    // Oracle #2 D3 (qc-round-10): repo owns the on-disk asset lifecycle so a
    // record delete cascades to sources/{id}_photo.jpg and _signature.jpg. Without
    // this, orphaned JPEGs accumulate forever — each ~200 KB × N deletes over the
    // life of the install. Passing SourceStore into the repo (rather than deleting
    // from the callsite) keeps delete atomicity honest: the caller can't forget.
    private val sourceStore: SourceStore,
) : StudentRepository {

    override fun observeAll(): Flow<List<StudentRecord>> =
        dao.observeAll().map { list -> list.map { it.toRecord() } }

    override suspend fun getById(id: String): StudentRecord? =
        dao.getById(id)?.toRecord()

    override fun observeById(id: String): Flow<StudentRecord?> =
        dao.observeById(id).map { it?.toRecord() }

    override suspend fun nextSerial(classNum: ClassNum): Int =
        dao.peekNextSerial(classNum.label)

    override suspend fun findAllByClassSerial(classNum: ClassNum, serial: Int): List<StudentRecord> =
        dao.findAllByClassSerial(classNum.label, serial).map { it.toRecord() }

    override suspend fun findAllByClassName(classNum: ClassNum, name: String): List<StudentRecord> =
        dao.findAllByClassNameKey(classNum.label, normalizeNameKey(name)).map { it.toRecord() }

    override suspend fun save(draft: StudentDraft, input: SaveInput): SaveResult {
        if (!draft.hasAnyMedia) return SaveResult.MissingBothMedia

        val duplicates = when (input.naming) {
            is NamingMode.Serial -> findAllByClassSerial(input.classNum, input.naming.number)
            is NamingMode.Name   -> findAllByClassName(input.classNum, input.naming.text)
        }
        if (duplicates.isNotEmpty()) {
            return SaveResult.DuplicateFound(existing = duplicates, incoming = draft, input = input)
        }

        val serial = when (val n = input.naming) {
            is NamingMode.Serial -> n.number
            // Name mode still needs a serial slot for stable ordering + future export routes.
            is NamingMode.Name   -> dao.nextSerialAndBump(input.classNum.label)
        }

        val now = Clock.System.now()
        val record = StudentRecord(
            id            = draft.id,
            classNum      = input.classNum,
            serial        = serial,
            displayName   = input.displayName,
            photoPath     = draft.photoPath.orEmpty(),
            signaturePath = draft.signaturePath,
            namingKind    = input.naming.kind,
            createdAt     = draft.createdAt,
            updatedAt     = now,
        )

        return try {
            // Oracle O1-4/O5-B1/B2: insert + counter bump go through one transaction.
            // Serial mode advances the counter to the user-picked serial when that's
            // higher than the current stored value (PRD §4.7: never rewinds). Name
            // mode already bumped via nextSerialAndBump above so passes null here.
            val advanceTo = (input.naming as? NamingMode.Serial)?.number
            dao.insertWithCounter(
                entity = record.toEntity(namingKind = input.naming.kind.name),
                advanceCounterTo = advanceTo,
            )
            SaveResult.Success(record)
        } catch (constraint: SQLiteConstraintException) {
            // Race backstop: another Save inserted the same (class, serial) between our
            // duplicate check and this insert. Re-read the existing rows and route to the
            // duplicate-resolve UI. m2502: now list-shaped so N-way dialog can render.
            val existing = when (input.naming) {
                is NamingMode.Serial -> findAllByClassSerial(input.classNum, input.naming.number)
                is NamingMode.Name   -> findAllByClassName(input.classNum, input.naming.text)
            }
            if (existing.isNotEmpty()) {
                SaveResult.DuplicateFound(existing = existing, incoming = draft, input = input)
            } else {
                throw constraint
            }
        }
    }

    /**
     * m2502 "Keep all" (m2506 rename) write. Skips the duplicate check because the caller
     * (SaveViewModel.resolveDuplicateKeepingAll) has already surfaced the dialog and
     * the user has consciously chosen to allow the collision. Delegates to
     * [StudentDao.insertAsDuplicateBySerial] / [StudentDao.insertAsDuplicateByName]
     * which atomically read MAX(duplicateIndex)+1 and insert in one transaction.
     * The class counter is NOT touched — Keep-both reuses an existing serial, so
     * moving the monotonic counter would strand a future auto-serial.
     */
    override suspend fun saveAsDuplicate(draft: StudentDraft, input: SaveInput): SaveResult {
        if (!draft.hasAnyMedia) return SaveResult.MissingBothMedia

        val serial = when (val n = input.naming) {
            is NamingMode.Serial -> n.number
            is NamingMode.Name   -> dao.nextSerialAndBump(input.classNum.label)
        }
        val now = Clock.System.now()
        val record = StudentRecord(
            id            = draft.id,
            classNum      = input.classNum,
            serial        = serial,
            displayName   = input.displayName,
            photoPath     = draft.photoPath.orEmpty(),
            signaturePath = draft.signaturePath,
            namingKind    = input.naming.kind,
            createdAt     = draft.createdAt,
            updatedAt     = now,
        )
        val entity = record.toEntity(namingKind = input.naming.kind.name)
        val assignedIndex = insertAsDuplicateWithRetry(entity, input.naming)
        return SaveResult.Success(record.copy(duplicateIndex = assignedIndex))
    }

    /**
     * m2503: two Keep-both taps racing on the same (classNum, serial) can both peek
     * duplicateIndex=N inside their DEFERRED transactions before either commits; the
     * loser's insert then hits SQLiteConstraintException. One retry re-peeks under the
     * loser's transaction — the winner's row is now visible so MAX(duplicateIndex)+1
     * lands on a fresh slot. Bounded to one retry: three concurrent taps on the same
     * cluster are vanishingly unlikely on a single-user phone, so on a second
     * SQLiteConstraintException we let the exception propagate — the calling coroutine
     * fails, saving=true is stuck until the ViewModel is reconstructed. Not graceful,
     * but the m2504 saving-guard in SaveViewModel makes the 3-tap race unreachable
     * from UI in practice.
     */
    private suspend fun insertAsDuplicateWithRetry(
        entity: com.npic.photoandsignscanner.data.db.StudentEntity,
        naming: NamingMode,
    ): Int {
        val doInsert: suspend () -> Int = {
            when (naming) {
                is NamingMode.Serial -> dao.insertAsDuplicateBySerial(entity)
                is NamingMode.Name   -> dao.insertAsDuplicateByName(entity)
            }
        }
        return try {
            doInsert()
        } catch (_: SQLiteConstraintException) {
            doInsert()
        }
    }

    override suspend fun replace(existingId: String, draft: StudentDraft, input: SaveInput): SaveResult {
        if (!draft.hasAnyMedia) return SaveResult.MissingBothMedia

        // m2503: replace must preserve the target's duplicateIndex — the UNIQUE
        // (classNum, serial, duplicateIndex) constraint would otherwise reject the
        // delete+insert when a sibling holds index=0.
        val targetDuplicateIndex = dao.getById(existingId)?.duplicateIndex ?: 0

        val serial = when (val n = input.naming) {
            is NamingMode.Serial -> n.number
            is NamingMode.Name   -> dao.nextSerialAndBump(input.classNum.label)
        }
        val now = Clock.System.now()
        val record = StudentRecord(
            id            = draft.id,
            classNum      = input.classNum,
            serial        = serial,
            displayName   = input.displayName,
            photoPath     = draft.photoPath.orEmpty(),
            signaturePath = draft.signaturePath,
            namingKind    = input.naming.kind,
            duplicateIndex = targetDuplicateIndex,
            createdAt     = draft.createdAt,
            updatedAt     = now,
        )
        // Oracle #2 D2 (qc-round-10): replace() must advance the class counter for
        // Serial-mode inputs, matching save(). Without this, a "Keep new" with
        // serial N > current counter leaves the counter stale — the next auto-serial
        // collides with N and the user hits DuplicateFound on a fresh save. Name mode
        // already bumped via nextSerialAndBump above so passes null.
        val advanceTo = (input.naming as? NamingMode.Serial)?.number
        // qc-round-12 Oracle #3 MAJOR #1: two callers reach replace() with different id
        // invariants:
        //   (a) UpdateConfirm flow (m2232 signature-flow): SharedCaptureHolder.beginUpdate
        //       mints draft.id == existingId so SourceStore files are overwritten in place
        //       by the Edit-time writePhoto/writeSignature. No orphan cleanup needed.
        //   (b) SaveViewModel.resolveDuplicateReplacingExisting (Keep-new): existingId is
        //       dupe.existing.id (the OLD record's UUID) while draft.id is dupe.incoming.id
        //       (the NEW record's UUID). sources/{existingId}_photo.jpg + _signature.jpg
        //       would be orphaned forever without an explicit delete — every Keep-new
        //       leaked ~200-400 KB. deleteFor() is idempotent (missing file → no-op).
        if (existingId != draft.id) {
            sourceStore.deleteFor(existingId)
        }
        dao.replaceWithCounter(
            existingId       = existingId,
            incoming         = record.toEntity(namingKind = input.naming.kind.name),
            advanceCounterTo = advanceTo,
        )
        return SaveResult.Success(record)
    }

    override suspend fun delete(id: String) {
        dao.delete(id)
        // Oracle #2 D3 (qc-round-10): delete BOTH source assets to prevent orphan
        // accumulation. deleteFor() is idempotent so a missing file is a no-op —
        // safe even if the record had no signature.
        sourceStore.deleteFor(id)
    }
}
