package com.npic.photoandsignscanner.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * DAO for `students` + `class_counters`. `nextSerialAndBump` is @Transaction so the
 * "read counter → increment → write back" trio is atomic against concurrent Save calls.
 * PRD §4.7 requires serials to be unique per class; this closes the race entirely.
 */
@Dao
interface StudentDao {

    @Query("SELECT * FROM students ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<StudentEntity>>

    @Query("SELECT * FROM students WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): StudentEntity?

    /**
     * Oracle #5 A5 (qc-round-10): scoped Flow subscription that emits only when THIS
     * row changes. DetailViewModel previously listened on `observeAll()` and filtered
     * client-side — every unrelated insert/update/delete woke the Detail screen up.
     * Room invalidates this Flow only on writes touching `students.id = :id`.
     * Emits `null` when the row is missing (never removed, or deleted after view).
     */
    @Query("SELECT * FROM students WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<StudentEntity?>

    /**
     * m2502: N-way lookup for the Save-flow duplicate check and the DuplicateSheet UI.
     * Returns every row sharing (classNum, serial) ordered by duplicateIndex ascending —
     * existing[0] is the original, existing[1..N] are prior Keep-both entries.
     */
    @Query(
        "SELECT * FROM students WHERE classNum = :classNum AND serial = :serial " +
            "ORDER BY duplicateIndex ASC",
    )
    suspend fun findAllByClassSerial(classNum: String, serial: Int): List<StudentEntity>

    /**
     * Case-insensitive + whitespace-tolerant lookup for the Save dialog's Name-mode
     * duplicate detection. Uses the [StudentEntity.nameKey] indexed column so this is a
     * single (classNum, nameKey) B-tree lookup rather than a full-table LOWER/TRIM scan.
     * Callers must normalize the query string via [normalizeNameKey] before invoking —
     * [RoomStudentRepository.findByClassName] handles that. Oracle O5-B4 / PRD §8.1.
     * m2502: returns list ordered by duplicateIndex for N-way Keep-both support.
     */
    @Query(
        "SELECT * FROM students WHERE classNum = :classNum AND nameKey = :nameKey " +
            "ORDER BY duplicateIndex ASC",
    )
    suspend fun findAllByClassNameKey(classNum: String, nameKey: String): List<StudentEntity>

    /**
     * m2502: next-available duplicateIndex for a (classNum, serial) group. Called inside
     * [insertDuplicate] under one transaction so two concurrent Keep-both saves can't
     * race to the same index (the composite UNIQUE constraint would then reject one).
     * Returns 1 for the first Keep-both (existing has index 0); N+1 for subsequent.
     */
    @Query(
        "SELECT COALESCE(MAX(duplicateIndex), -1) + 1 FROM students " +
            "WHERE classNum = :classNum AND serial = :serial",
    )
    suspend fun peekNextDuplicateIndexForSerial(classNum: String, serial: Int): Int

    /** m2502: Name-mode counterpart to [peekNextDuplicateIndexForSerial]. */
    @Query(
        "SELECT COALESCE(MAX(duplicateIndex), -1) + 1 FROM students " +
            "WHERE classNum = :classNum AND nameKey = :nameKey",
    )
    suspend fun peekNextDuplicateIndexForName(classNum: String, nameKey: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: StudentEntity)

    @Query("DELETE FROM students WHERE id = :id")
    suspend fun delete(id: String)

    /**
     * User m1551 S3 destructive Clear-all-data affordance. Wipes both tables in a
     * single transaction so observers can't glimpse a half-cleared state where
     * students are gone but counters still report advanced serials.
     */
    @Transaction
    suspend fun clearAll() {
        deleteAllStudents()
        deleteAllCounters()
    }

    @Query("DELETE FROM students")
    suspend fun deleteAllStudents()

    @Query("DELETE FROM class_counters")
    suspend fun deleteAllCounters()

    /**
     * PRD §4.7 replace-existing path: delete then insert in the same transaction so
     * observers see one atomic swap instead of a brief "record vanished" flicker.
     */
    @Transaction
    suspend fun replace(existingId: String, incoming: StudentEntity) {
        delete(existingId)
        insert(incoming)
    }

    /**
     * Oracle #2 D2 (qc-round-10): PRD §4.7 replace + optional counter advance in one
     * transaction. Serial-mode replace paths must bump the counter when the new serial
     * exceeds the current lastSerial, otherwise the next auto-populate collides. Passing
     * null skips the counter update (Name-mode already bumped via [nextSerialAndBump]).
     */
    @Transaction
    suspend fun replaceWithCounter(
        existingId: String,
        incoming: StudentEntity,
        advanceCounterTo: Int?,
    ) {
        delete(existingId)
        insert(incoming)
        if (advanceCounterTo != null) {
            val current = getLastSerial(incoming.classNum) ?: 0
            if (advanceCounterTo > current) {
                setCounter(
                    ClassCounterEntity(
                        classNum = incoming.classNum,
                        lastSerial = advanceCounterTo,
                    ),
                )
            }
        }
    }

    /**
     * Oracle O1-4/O5-B1/B2: insert a student and (optionally) advance the class-serial
     * counter as a single atomic transaction. Without this, two concurrent Saves could
     * both pass the duplicate check, both attempt insert, and one gets orphaned by the
     * UNIQUE(classNum, serial) constraint AFTER writing files to SourceStore. Rolling
     * counter and insert into one transaction closes the race entirely.
     *
     * `advanceCounterTo` is the serial to compare-and-swap into class_counters. Null
     * skips the counter update (used by [replace] and by callers that already bumped
     * via [nextSerialAndBump]).
     */
    @Transaction
    suspend fun insertWithCounter(
        entity: StudentEntity,
        advanceCounterTo: Int?,
    ) {
        insert(entity)
        if (advanceCounterTo != null) {
            val current = getLastSerial(entity.classNum) ?: 0
            if (advanceCounterTo > current) {
                setCounter(
                    ClassCounterEntity(
                        classNum = entity.classNum,
                        lastSerial = advanceCounterTo,
                    ),
                )
            }
        }
    }

    /**
     * m2502: atomic "Keep all" (m2506 rename) insert. Reads MAX(duplicateIndex) for the target
     * (classNum, serial) group and writes [entity] with duplicateIndex = max + 1 in
     * one transaction so two concurrent Keep-both writes can't hand out the same
     * index (the composite UNIQUE constraint would then reject one). Returns the
     * assigned duplicateIndex so the caller can echo it into the domain-side record.
     *
     * Does NOT touch class_counters — Keep-both reuses an existing serial and must
     * never move the monotonic counter (which would strand future auto-serials).
     */
    @Transaction
    suspend fun insertAsDuplicateBySerial(entity: StudentEntity): Int {
        val nextIndex = peekNextDuplicateIndexForSerial(entity.classNum, entity.serial)
        insert(entity.copy(duplicateIndex = nextIndex))
        return nextIndex
    }

    /** m2502: Name-mode counterpart. See [insertAsDuplicateBySerial]. */
    @Transaction
    suspend fun insertAsDuplicateByName(entity: StudentEntity): Int {
        val nextIndex = peekNextDuplicateIndexForName(entity.classNum, entity.nameKey)
        insert(entity.copy(duplicateIndex = nextIndex))
        return nextIndex
    }

    // ─── class_counters ─────────────────────────────────────────────────────

    @Query("SELECT lastSerial FROM class_counters WHERE classNum = :classNum LIMIT 1")
    suspend fun getLastSerial(classNum: String): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setCounter(counter: ClassCounterEntity)

    /**
     * Atomically compute the next-available serial. Never rewinds (PRD §4.7): even after
     * a delete, the counter stays advanced so historical portal exports keep pointing at
     * unique slots.
     */
    @Transaction
    suspend fun nextSerialAndBump(classNum: String): Int {
        val current = getLastSerial(classNum) ?: 0
        val next = current + 1
        setCounter(ClassCounterEntity(classNum = classNum, lastSerial = next))
        return next
    }

    /**
     * Peek at the next serial without bumping. Used by SaveViewModel to auto-populate
     * the Save dialog's Serial field. The actual bump happens in [nextSerialAndBump]
     * inside RoomStudentRepository.save() so a cancelled Save doesn't waste a serial.
     */
    @Query(
        """
        SELECT COALESCE(
            (SELECT lastSerial FROM class_counters WHERE classNum = :classNum),
            0
        ) + 1
        """,
    )
    suspend fun peekNextSerial(classNum: String): Int
}
