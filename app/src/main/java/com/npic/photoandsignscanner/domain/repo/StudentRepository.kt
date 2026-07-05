package com.npic.photoandsignscanner.domain.repo

import com.npic.photoandsignscanner.domain.model.ClassNum
import com.npic.photoandsignscanner.domain.model.SaveInput
import com.npic.photoandsignscanner.domain.model.SaveResult
import com.npic.photoandsignscanner.domain.model.StudentDraft
import com.npic.photoandsignscanner.domain.model.StudentRecord
import kotlinx.coroutines.flow.Flow

/**
 * The Gallery + Save + Detail + Export screens all speak to this one interface. Layer 8b
 * ships an in-memory implementation (`data/repo/InMemoryStudentRepository.kt`) that seeds
 * from `MockGalleryData`; Room slots in behind the same interface in the Room layer.
 *
 * Non-suspending [observeAll] because Compose subscribes via `collectAsState`; the other
 * ops are `suspend` because they'll hit disk / DB in production.
 *
 * Duplicate detection is class-scoped per PRD §4.7: same `(classNum, serial)` OR
 * `(classNum, nameKey)` triggers the Duplicate Dialog. The comparison is case-insensitive
 * and whitespace-normalised — implementations should route through a shared `nameKey`
 * helper to keep the rule consistent between save and gallery search.
 */
interface StudentRepository {

    /** Cold flow of all records; observers get the current snapshot on subscribe. */
    fun observeAll(): Flow<List<StudentRecord>>

    /** Single-record lookup by ID for the Detail screen (PRD §4.9). Returns null if missing. */
    suspend fun getById(id: String): StudentRecord?

    /**
     * Cold flow of one record by ID — emits the current value on subscribe and re-emits
     * whenever that record changes (Oracle #5 A5, qc-round-10). Emits `null` if the row
     * is deleted or never existed. Backed by a `WHERE id = :id` query on the DAO so it
     * doesn't wake up on unrelated writes.
     */
    fun observeById(id: String): Flow<StudentRecord?>

    /**
     * Next available serial for [classNum] — `max(existingSerials) + 1`, floor 1. The
     * Save dialog uses this to auto-populate the serial input.
     */
    suspend fun nextSerial(classNum: ClassNum): Int

    /**
     * Class-scoped serial lookup for duplicate detection. m2502: returns all rows
     * sharing (classNum, serial) — list ordered by duplicateIndex ascending. Empty
     * when nothing matches. Callers that only need existence take `.isNotEmpty()`;
     * the Save flow forwards the whole list to [SaveResult.DuplicateFound].
     */
    suspend fun findAllByClassSerial(classNum: ClassNum, serial: Int): List<StudentRecord>

    /** m2502: Name-mode counterpart to [findAllByClassSerial]. */
    suspend fun findAllByClassName(classNum: ClassNum, name: String): List<StudentRecord>

    /**
     * Persist [draft] under [input]. Returns [SaveResult.Success] on the happy path,
     * [SaveResult.DuplicateFound] when the (class, serial) or (class, name) already
     * exists, or [SaveResult.MissingBothMedia] when the invariant is broken.
     */
    suspend fun save(draft: StudentDraft, input: SaveInput): SaveResult

    /**
     * m2502 "Keep both": persist [draft] under [input] alongside any existing record(s)
     * sharing the same (classNum, serial) or (classNum, name) group. Bypasses the
     * duplicate check and allocates the next duplicateIndex atomically. Returns
     * [SaveResult.Success] with the new record whose `duplicateIndex` is >= 1 (0 is
     * reserved for the original). Returns [SaveResult.MissingBothMedia] on the empty
     * draft invariant.
     */
    suspend fun saveAsDuplicate(draft: StudentDraft, input: SaveInput): SaveResult

    /**
     * Replace [existingId] with the new [draft]/[input] pair (PRD §4.7 "Keep new"). The
     * existing record's on-disk media is discarded by the repo; caller only supplies the
     * ID.
     */
    suspend fun replace(existingId: String, draft: StudentDraft, input: SaveInput): SaveResult

    /**
     * Delete a single record by ID (PRD §4.9 Detail overflow "Delete", §4.8 Gallery
     * selection-mode "Delete"). Idempotent — deleting a missing ID is a no-op.
     */
    suspend fun delete(id: String)
}
