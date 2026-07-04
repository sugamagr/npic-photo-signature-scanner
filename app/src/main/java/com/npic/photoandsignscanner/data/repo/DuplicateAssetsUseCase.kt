package com.npic.photoandsignscanner.data.repo

import android.util.Log
import com.npic.photoandsignscanner.data.storage.SourceStore
import com.npic.photoandsignscanner.domain.model.CameraMode
import com.npic.photoandsignscanner.domain.model.SignatureSource
import com.npic.photoandsignscanner.domain.model.StudentDraft
import com.npic.photoandsignscanner.domain.model.StudentRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import java.io.File
import java.util.UUID

/**
 * "Duplicate to another class" use case (user m1555, workflow B per m1551).
 *
 * Given an existing [StudentRecord], produce a fresh [StudentDraft] whose photo and
 * signature assets are **independent byte-for-byte copies** in `sources/{newId}_*.jpg`,
 * so the resulting record can be edited, deleted, or exported without affecting the
 * source record.
 *
 * ### Why file-byte copy (not bitmap decode-encode)
 * The originals in `filesDir/sources/` are already at the canonical PRD §5.5 dimensions
 * (long-side 1600 for photos, 1500 for signatures) and at JPEG q92. Decoding and re-encoding
 * would double the round-trip cost and introduce a second lossy pass for zero benefit —
 * `File.inputStream().copyTo(outputStream)` produces bitwise-identical assets.
 *
 * ### Why NOT link/hardlink
 * A hardlink would tie the two records' delete lifecycles together. If the user deletes
 * the source record, [com.npic.photoandsignscanner.data.storage.SourceStore.deleteFor]
 * would evict the shared inode and the duplicate would lose its assets. Independent
 * copies are the correct semantic for "duplicate".
 */
class DuplicateAssetsUseCase(private val sourceStore: SourceStore) {

    /**
     * Copy [source]'s assets into a new draft. Returns null on IO failure (any missing or
     * unreadable source file). Callers should surface a user-facing error toast on null.
     *
     * The returned draft has:
     *   - Fresh UUID (becomes the future [StudentRecord.id])
     *   - `photoPath` pointing at the fresh copy in `sources/{newId}_photo.jpg`
     *   - `signaturePath` pointing at the fresh copy (or null if source had none)
     *   - `photoMode = Photo` and `signatureSource = Captured` — these are metadata-only
     *     hints for the Save UI; the actual capture mode / source is lost when a record
     *     is duplicated. Treating everything as "Captured" is honest: the pixels came from
     *     the source record which itself could have been captured or drawn.
     */
    suspend fun invoke(source: StudentRecord): StudentDraft? = withContext(Dispatchers.IO) {
        val newId = UUID.randomUUID().toString()
        val sourcePhoto = source.photoPath.takeIf { it.isNotBlank() }?.let(::File)
        val sourceSig = source.signaturePath?.takeIf { it.isNotBlank() }?.let(::File)

        // At least ONE asset must exist; a record with neither shouldn't be reachable
        // from Detail (Gallery filters record.hasAnyMedia) but guard anyway.
        if (sourcePhoto == null && sourceSig == null) {
            Log.w(TAG, "duplicate skipped: source record ${source.id} has no assets")
            return@withContext null
        }

        val newPhoto = sourcePhoto?.let { orig ->
            val dst = sourceStore.photoFile(newId)
            runCatching {
                dst.parentFile?.mkdirs()
                orig.inputStream().use { input ->
                    dst.outputStream().use { output -> input.copyTo(output) }
                }
                dst.absolutePath
            }.onFailure { Log.e(TAG, "photo copy failed: ${it.message}", it) }.getOrNull()
        }

        val newSig = sourceSig?.let { orig ->
            val dst = sourceStore.signatureFile(newId)
            runCatching {
                dst.parentFile?.mkdirs()
                orig.inputStream().use { input ->
                    dst.outputStream().use { output -> input.copyTo(output) }
                }
                dst.absolutePath
            }.onFailure { Log.e(TAG, "signature copy failed: ${it.message}", it) }.getOrNull()
        }

        // If BOTH copies failed we've produced a useless draft — bail so Save doesn't
        // land a record pointing at nonexistent files.
        if (newPhoto == null && newSig == null) {
            Log.w(TAG, "duplicate failed: no assets could be copied for ${source.id}")
            return@withContext null
        }

        StudentDraft(
            id = newId,
            photoPath = newPhoto,
            signaturePath = newSig,
            photoMode = if (newPhoto != null) CameraMode.Photo else null,
            signatureSource = if (newSig != null) SignatureSource.Captured else null,
            createdAt = Clock.System.now(),
        )
    }

    private companion object {
        const val TAG = "DuplicateAssets"
    }
}
