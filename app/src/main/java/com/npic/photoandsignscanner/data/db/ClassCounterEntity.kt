package com.npic.photoandsignscanner.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Per-class monotonic serial counter (PRD §8.1). Kept out of [StudentEntity] so
 * nextSerial can bump atomically without racing against student inserts (SQLite row-lock
 * on this table is tiny + independent of the students table's write throughput).
 *
 * Not user-facing — the Save dialog reads via StudentRepository.nextSerial. Deleting a
 * student does NOT decrement lastSerial; PRD §4.7 explicitly says serials never rewind
 * because portal exports may reference deleted serial slots historically.
 */
@Entity(tableName = "class_counters")
data class ClassCounterEntity(
    @PrimaryKey
    @ColumnInfo(name = "classNum")
    val classNum: String,

    @ColumnInfo(name = "lastSerial")
    val lastSerial: Int,
)
