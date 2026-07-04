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

    /**
     * Next available serial for [classNum] — `max(existingSerials) + 1`, floor 1. The
     * Save dialog uses this to auto-populate the serial input.
     */
    suspend fun nextSerial(classNum: ClassNum): Int

    /** Class-scoped serial lookup for duplicate detection. */
    suspend fun findByClassSerial(classNum: ClassNum, serial: Int): StudentRecord?

    /** Class-scoped normalised-name lookup for duplicate detection. */
    suspend fun findByClassName(classNum: ClassNum, name: String): StudentRecord?

    /**
     * Persist [draft] under [input]. Returns [SaveResult.Success] on the happy path,
     * [SaveResult.DuplicateFound] when the (class, serial) or (class, name) already
     * exists, or [SaveResult.MissingBothMedia] when the invariant is broken.
     */
    suspend fun save(draft: StudentDraft, input: SaveInput): SaveResult

    /**
     * Replace [existingId] with the new [draft]/[input] pair (PRD §4.7 "Keep new"). The
     * existing record's on-disk media is discarded by the repo; caller only supplies the
     * ID.
     */
    suspend fun replace(existingId: Long, draft: StudentDraft, input: SaveInput): SaveResult
}
