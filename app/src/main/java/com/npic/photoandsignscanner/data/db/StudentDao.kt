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

    @Query(
        "SELECT * FROM students WHERE classNum = :classNum AND serial = :serial LIMIT 1",
    )
    suspend fun findByClassSerial(classNum: String, serial: Int): StudentEntity?

    /**
     * Case-insensitive + whitespace-tolerant lookup for the Save dialog's Name-mode
     * duplicate detection. Uses the [StudentEntity.nameKey] indexed column so this is a
     * single (classNum, nameKey) B-tree lookup rather than a full-table LOWER/TRIM scan.
     * Callers must normalize the query string via [normalizeNameKey] before invoking —
     * [RoomStudentRepository.findByClassName] handles that. Oracle O5-B4 / PRD §8.1.
     */
    @Query(
        "SELECT * FROM students WHERE classNum = :classNum AND nameKey = :nameKey LIMIT 1",
    )
    suspend fun findByClassNameKey(classNum: String, nameKey: String): StudentEntity?

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
