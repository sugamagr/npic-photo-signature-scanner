package com.npic.photoandsignscanner.data.db

import android.util.Log
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.npic.photoandsignscanner.domain.model.CameraCapture
import com.npic.photoandsignscanner.domain.model.CameraMode
import com.npic.photoandsignscanner.domain.model.RectI
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

    /**
     * Oracle O1-7: the raw CameraX JPEG path from the last shutter press. Persisting this
     * lets a warm start rehydrate the [com.npic.photoandsignscanner.domain.model.CameraCapture]
     * object so a process kill between Camera and Edit's commit doesn't strand the file
     * in `cache/drafts/` with no way for Edit to find it. Nullable because a draft can
     * predate any capture (drawn-signature-first flows). The four guideBox* fields carry
     * the projected image-space guide rect; all four null == no guide (use full image
     * bounds), consistent with [com.npic.photoandsignscanner.domain.model.RectI]? nullable
     * semantics on CameraCapture.
     */
    @ColumnInfo(name = "rawPath")
    val rawPath: String? = null,

    @ColumnInfo(name = "rawMode")
    val rawMode: String? = null,

    @ColumnInfo(name = "capturedAt")
    val capturedAt: Long? = null,

    @ColumnInfo(name = "guideBoxLeft")   val guideBoxLeft:   Int? = null,
    @ColumnInfo(name = "guideBoxTop")    val guideBoxTop:    Int? = null,
    @ColumnInfo(name = "guideBoxRight")  val guideBoxRight:  Int? = null,
    @ColumnInfo(name = "guideBoxBottom") val guideBoxBottom: Int? = null,

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
    // Oracle O5-M5: forward-compat parse. Unknown SignatureSource string reaches this
    // path only when a future variant is added to the sealed interface (e.g. Imported).
    // Erroring here would brick warm starts on old-app-new-DB scenarios that Section B of
    // DEFERRED-DECISIONS explicitly enumerates as post-v1.0 migration territory. Log and
    // treat as "signature source unknown" — the file paths are still intact.
    signatureSource = signatureSource?.let { name ->
        when (name) {
            "Captured" -> SignatureSource.Captured
            "Drawn"    -> SignatureSource.Drawn
            else       -> {
                Log.w("DraftEntity", "Corrupt draft $id: unknown signatureSource '$name'; treating as null")
                null
            }
        }
    },
    createdAt = Instant.fromEpochMilliseconds(createdAt),
)

/**
 * Rehydrate the last-committed [CameraCapture] from persisted draft columns. Returns null
 * when the draft predates any capture (drawn-signature-first flows) OR when the persisted
 * blob is partial. Consumers must gracefully accept null (SharedCaptureHolder does).
 * Oracle O1-7.
 */
internal fun DraftEntity.toCameraCaptureOrNull(): CameraCapture? {
    val path = rawPath ?: return null
    val modeName = rawMode ?: return null
    val mode = runCatching { CameraMode.valueOf(modeName) }.getOrNull() ?: return null
    val capturedAtMillis = capturedAt ?: return null
    val guideBox = if (
        guideBoxLeft != null && guideBoxTop != null &&
        guideBoxRight != null && guideBoxBottom != null
    ) {
        RectI(guideBoxLeft, guideBoxTop, guideBoxRight, guideBoxBottom)
    } else null
    return CameraCapture(
        rawPath = path,
        mode = mode,
        guideBoxImageSpace = guideBox,
        capturedAt = Instant.fromEpochMilliseconds(capturedAtMillis),
    )
}

/**
 * Merge a [StudentDraft] with an optional [CameraCapture] into the persisted entity shape.
 * Callers that don't touch the raw capture (e.g. Signature-first flows) pass null and the
 * rawPath/rawMode/guideBox columns stay null. Oracle O1-7.
 */
internal fun StudentDraft.toEntity(
    updatedAt: Instant,
    capture: CameraCapture? = null,
): DraftEntity = DraftEntity(
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
    rawPath = capture?.rawPath,
    rawMode = capture?.mode?.name,
    capturedAt = capture?.capturedAt?.toEpochMilliseconds(),
    guideBoxLeft   = capture?.guideBoxImageSpace?.left,
    guideBoxTop    = capture?.guideBoxImageSpace?.top,
    guideBoxRight  = capture?.guideBoxImageSpace?.right,
    guideBoxBottom = capture?.guideBoxImageSpace?.bottom,
    createdAt = createdAt.toEpochMilliseconds(),
    updatedAt = updatedAt.toEpochMilliseconds(),
)
