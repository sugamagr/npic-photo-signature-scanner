package com.npic.photoandsignscanner.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.npic.photoandsignscanner.domain.model.CameraMode
import com.npic.photoandsignscanner.domain.model.SignatureSource
import com.npic.photoandsignscanner.domain.model.StudentDraft
import kotlinx.datetime.Instant

/**
 * Persistent capture-in-progress record backing PRD §8.3 "Resume capture in progress?"
 * prompt. At most one draft is active at a time in v1.0 — the ID column stays a PK for
 * a future multi-draft world but the active-draft flow queries `observeActive()` which
 * returns the most-recently-updated row.
 *
 * All fields nullable except id + createdAt because a draft starts empty (Camera not
 * yet taken, Signature not yet drawn) and gets filled in as the user progresses through
 * the Camera → Edit → Signature → Save arc.
 */
@Entity(tableName = "drafts")
data class DraftEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "photoPath")
    val photoPath: String?,

    @ColumnInfo(name = "signaturePath")
    val signaturePath: String?,

    @ColumnInfo(name = "photoMode")
    val photoMode: String?,

    @ColumnInfo(name = "signatureSource")
    val signatureSource: String?,

    @ColumnInfo(name = "createdAt")
    val createdAt: Long,

    @ColumnInfo(name = "updatedAt")
    val updatedAt: Long,
)

internal fun DraftEntity.toDraft(): StudentDraft = StudentDraft(
    id = id,
    photoPath = photoPath,
    signaturePath = signaturePath,
    photoMode = photoMode?.let(CameraMode::valueOf),
    signatureSource = signatureSource?.let { name ->
        when (name) {
            "Captured" -> SignatureSource.Captured
            "Drawn"    -> SignatureSource.Drawn
            else       -> error("Corrupt draft $id: unknown signatureSource '$name'")
        }
    },
    createdAt = Instant.fromEpochMilliseconds(createdAt),
)

internal fun StudentDraft.toEntity(updatedAt: Instant): DraftEntity = DraftEntity(
    id = id,
    photoPath = photoPath,
    signaturePath = signaturePath,
    photoMode = photoMode?.name,
    signatureSource = signatureSource?.let {
        when (it) {
            SignatureSource.Captured -> "Captured"
            SignatureSource.Drawn    -> "Drawn"
        }
    },
    createdAt = createdAt.toEpochMilliseconds(),
    updatedAt = updatedAt.toEpochMilliseconds(),
)
