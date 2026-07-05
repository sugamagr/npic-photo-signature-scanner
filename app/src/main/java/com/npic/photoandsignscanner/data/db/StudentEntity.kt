package com.npic.photoandsignscanner.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.npic.photoandsignscanner.domain.model.ClassNum
import com.npic.photoandsignscanner.domain.model.NamingMode
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
        // m2502: composite UNIQUE now includes duplicateIndex so "Keep all" (m2506 rename) can persist
        // two rows with the same (classNum, serial). Default index 0 keeps existing rows
        // singleton-shaped; each subsequent Keep-both allocates the next available index
        // atomically (see StudentDao.peekNextDuplicateIndex + repo.saveAsDuplicate).
        Index(value = ["classNum", "serial", "duplicateIndex"], unique = true),
        // Oracle O5-B4: PRD §8.1 specifies (classNum, nameKey) composite index.
        // findByClassName joins on nameKey so this replaces the full-table LOWER/TRIM scan.
        // m2502: extended with duplicateIndex for the same Keep-both invariant on Name mode.
        Index(value = ["classNum", "nameKey", "duplicateIndex"], unique = true),
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

    /**
     * Normalized lookup key derived from displayName at write time. Case-folded, whitespace-
     * collapsed. Populated deterministically via [normalizeNameKey] on both insert and query
     * so findByClassName is a single indexed lookup. PRD §8.1.
     */
    @ColumnInfo(name = "nameKey")
    val nameKey: String,

    @ColumnInfo(name = "photoPath")
    val photoPath: String,

    @ColumnInfo(name = "signaturePath")
    val signaturePath: String?,

    @ColumnInfo(name = "namingKind")
    val namingKind: String,

    /**
     * m2502: 0 = original / singleton; 1 = first Keep-both duplicate; 2 = second; etc.
     * See [StudentRecord.duplicateIndex] KDoc for full semantics.
     */
    @ColumnInfo(name = "duplicateIndex", defaultValue = "0")
    val duplicateIndex: Int = 0,

    @ColumnInfo(name = "createdAt")
    val createdAt: Long,

    @ColumnInfo(name = "updatedAt")
    val updatedAt: Long,
)

/**
 * Deterministic normalization for the [StudentEntity.nameKey] lookup column. Trim, collapse
 * internal whitespace to single space, lowercase. Kept in the data layer so writes AND
 * queries route through the same function — any change here must be reflected on both.
 * Devanagari support is DEFERRED-DECISIONS B10 (NFKC when Hindi ships).
 */
// Oracle #4 S2 (qc-round-10): hoisted so writes + queries share one compiled Regex
// instead of allocating on every normalizeNameKey call. Any migration change must
// mirror this pattern — see NpicDatabase.MIGRATION_1_2 backfill.
private val NAME_WHITESPACE_RUN = Regex("\\s+")

internal fun normalizeNameKey(displayName: String): String =
    displayName.trim().replace(NAME_WHITESPACE_RUN, " ").lowercase()

/** Room entity → domain read-model. */
internal fun StudentEntity.toRecord(): StudentRecord = StudentRecord(
    id = id,
    classNum = ClassNum.fromLabel(classNum)
        ?: error("Corrupt row $id: unknown classNum '$classNum'"),
    serial = serial,
    displayName = displayName,
    photoPath = photoPath,
    signaturePath = signaturePath,
    // Forward-compat: unknown persisted values fall back to Serial. The v1→v2 migration
    // backfills the column with an empty default for legacy rows, and the m2232 fix
    // depends on this round-trip so add-signature update flows can reconstruct SaveInput.
    namingKind = runCatching { NamingMode.Kind.valueOf(namingKind) }.getOrDefault(NamingMode.Kind.Serial),
    duplicateIndex = duplicateIndex,
    createdAt = Instant.fromEpochMilliseconds(createdAt),
    updatedAt = Instant.fromEpochMilliseconds(updatedAt),
)

/** Domain read-model → Room entity. namingKind supplied by caller (StudentRepository.save). */
internal fun StudentRecord.toEntity(namingKind: String): StudentEntity = StudentEntity(
    id = id,
    classNum = classNum.label,
    serial = serial,
    displayName = displayName,
    nameKey = normalizeNameKey(displayName),
    photoPath = photoPath,
    signaturePath = signaturePath,
    namingKind = namingKind,
    duplicateIndex = duplicateIndex,
    createdAt = createdAt.toEpochMilliseconds(),
    updatedAt = updatedAt.toEpochMilliseconds(),
)
