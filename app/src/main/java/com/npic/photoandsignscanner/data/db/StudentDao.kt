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
     * duplicate detection. SQLite `LOWER()` handles ASCII; when Devanagari lands this
     * will need ICU-COLLATE or a nameKey column.
     */
    @Query(
        """
        SELECT * FROM students
        WHERE classNum = :classNum
          AND LOWER(TRIM(REPLACE(displayName, '  ', ' '))) = LOWER(TRIM(REPLACE(:name, '  ', ' ')))
        LIMIT 1
        """,
    )
    suspend fun findByClassName(classNum: String, name: String): StudentEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: StudentEntity)

    @Query("DELETE FROM students WHERE id = :id")
    suspend fun delete(id: String)

    /**
     * PRD §4.7 replace-existing path: delete then insert in the same transaction so
     * observers see one atomic swap instead of a brief "record vanished" flicker.
     */
    @Transaction
    suspend fun replace(existingId: String, incoming: StudentEntity) {
        delete(existingId)
        insert(incoming)
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
