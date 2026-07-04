package com.npic.photoandsignscanner.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.npic.photoandsignscanner.domain.model.ClassNum
import com.npic.photoandsignscanner.domain.model.StudentRecord
import kotlinx.datetime.Instant

/**
 * Persistent shape of [StudentRecord] (PRD §8.1). Room owns this shape; the domain layer
 * reads/writes via a mapper so ClassNum stays enum-shaped upstream and gets stringified
 * only at the SQLite boundary.
 *
 * Unique index on (classNum, serial) enforces the PRD §4.7 duplicate rule at the DB
 * level — even if the app misses a check-before-insert race, SQLite rejects the second
 * write with a constraint violation. Duplicate detection code still runs first so the UI
 * can surface the resolve-dialog before the write attempt.
 *
 * namingKind is persisted so ExportViewModel knows whether a record was saved under
 * Serial mode (filename uses serial) or Name mode (filename uses displayName). Layer 9
 * currently INFERS this heuristically from displayName — Layer 10 makes it explicit.
 */
@Entity(
    tableName = "students",
    indices = [
        Index(value = ["classNum", "serial"], unique = true),
        Index(value = ["displayName"]),
    ],
)
data class StudentEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "classNum")
    val classNum: String,

    @ColumnInfo(name = "serial")
    val serial: Int,

    @ColumnInfo(name = "displayName")
    val displayName: String,

    @ColumnInfo(name = "photoPath")
    val photoPath: String,

    @ColumnInfo(name = "signaturePath")
    val signaturePath: String?,

    @ColumnInfo(name = "namingKind")
    val namingKind: String,

    @ColumnInfo(name = "createdAt")
    val createdAt: Long,

    @ColumnInfo(name = "updatedAt")
    val updatedAt: Long,
)

/** Room entity → domain read-model. */
internal fun StudentEntity.toRecord(): StudentRecord = StudentRecord(
    id = id,
    classNum = ClassNum.fromLabel(classNum)
        ?: error("Corrupt row $id: unknown classNum '$classNum'"),
    serial = serial,
    displayName = displayName,
    photoPath = photoPath,
    signaturePath = signaturePath,
    createdAt = Instant.fromEpochMilliseconds(createdAt),
    updatedAt = Instant.fromEpochMilliseconds(updatedAt),
)

/** Domain read-model → Room entity. namingKind supplied by caller (StudentRepository.save). */
internal fun StudentRecord.toEntity(namingKind: String): StudentEntity = StudentEntity(
    id = id,
    classNum = classNum.label,
    serial = serial,
    displayName = displayName,
    photoPath = photoPath,
    signaturePath = signaturePath,
    namingKind = namingKind,
    createdAt = createdAt.toEpochMilliseconds(),
    updatedAt = updatedAt.toEpochMilliseconds(),
)
