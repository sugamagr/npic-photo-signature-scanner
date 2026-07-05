package com.npic.photoandsignscanner.data.export

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Writes rendered export JPEGs to the device Gallery via MediaStore. Scoped-storage safe
 * on SDK 29+ (no runtime permission needed); on SDK ≤28 relies on the legacy
 * WRITE_EXTERNAL_STORAGE declared in AndroidManifest (per DEFERRED-DECISIONS B8).
 *
 * Files land under `Pictures/NPIC/` so they surface in Google Photos + AOSP Gallery apps
 * automatically.
 *
 * m2505 P1: re-exporting the same batch would otherwise let MediaStore auto-suffix the
 * collision as `090001 (1).jpeg` — the exact portal-unsafe format the whole batch-
 * collision resolver in m2503 replaced with `_N`. Gallery filenames would diverge from
 * share-sheet filenames. This class pre-deletes any existing entry with the same
 * DISPLAY_NAME under our RELATIVE_PATH before insert so the freshly-generated JPEG lands
 * with the exact filename the ExportViewModel computed.
 */
class MediaStoreExporter(private val contentResolver: ContentResolver) {

    /**
     * Save a single JPEG to the public Gallery under `Pictures/NPIC/`.
     *
     * On SDK 29+ uses the two-phase `IS_PENDING = 1 → 0` protocol so the file isn't
     * visible in Gallery apps until writing finishes (prevents half-decoded thumbnails).
     * On SDK ≤28 falls back to the single-phase insert.
     *
     * @return the content:// Uri of the saved file, or `null` on failure.
     */
    suspend fun saveJpeg(displayName: String, bytes: ByteArray): Uri? =
        withContext(Dispatchers.IO) {
            val useIsPending = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
            deleteExistingIfPresent(displayName, useIsPending)
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, JPEG_MIME)
                if (useIsPending) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, GALLERY_RELATIVE_PATH)
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                } else {
                    // Pre-Q: RELATIVE_PATH is API 29+, so files would land in Pictures/ root
                    // without this branch. Set the legacy DATA column to an explicit path
                    // under Pictures/NPIC/ so Gallery indexing matches the SDK 29+ behaviour.
                    @Suppress("DEPRECATION")
                    val dir = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                        GALLERY_SUBDIR,
                    ).apply { mkdirs() }
                    @Suppress("DEPRECATION")
                    put(MediaStore.Images.Media.DATA, File(dir, displayName).absolutePath)
                }
            }
            val uri: Uri = try {
                contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            } catch (t: Throwable) {
                Log.e(TAG, "MediaStore insert failed for $displayName: ${t.message}", t)
                null
            } ?: return@withContext null

            val wrote = runCatching {
                contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(bytes)
                    out.flush()
                    true
                } ?: false
            }.onFailure { Log.e(TAG, "Write to $uri failed: ${it.message}", it) }
                .getOrDefault(false)

            if (!wrote) {
                runCatching { contentResolver.delete(uri, null, null) }
                return@withContext null
            }

            if (useIsPending) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                runCatching { contentResolver.update(uri, values, null, null) }
                    .onFailure { Log.w(TAG, "IS_PENDING clear failed for $uri: ${it.message}") }
            }
            uri
        }

    /**
     * m2505 P1: pre-delete any existing MediaStore entry with the same DISPLAY_NAME under
     * our RELATIVE_PATH so the fresh insert lands with the exact filename we computed
     * instead of MediaStore's auto-suffixed collision (` (1).jpeg`). Safe to call on a
     * non-existent name — the query returns zero rows and we no-op.
     *
     * SDK 29+ uses a MediaStore selection query. SDK ≤28 has no RELATIVE_PATH column,
     * so we fall back to deleting the legacy File under `Pictures/NPIC/`.
     */
    private fun deleteExistingIfPresent(displayName: String, useIsPending: Boolean) {
        if (useIsPending) {
            val selection =
                "${MediaStore.Images.Media.DISPLAY_NAME} = ? AND " +
                    "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
            val selectionArgs = arrayOf(displayName, "$GALLERY_RELATIVE_PATH%")
            runCatching {
                contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Images.Media._ID),
                    selection,
                    selectionArgs,
                    null,
                )?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    while (cursor.moveToNext()) {
                        val existingUri = Uri.withAppendedPath(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            cursor.getLong(idColumn).toString(),
                        )
                        runCatching { contentResolver.delete(existingUri, null, null) }
                            .onFailure { Log.w(TAG, "Pre-delete failed for $existingUri: ${it.message}") }
                    }
                }
            }.onFailure { Log.w(TAG, "Pre-delete query failed for $displayName: ${it.message}") }
        } else {
            @Suppress("DEPRECATION")
            val legacyDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                GALLERY_SUBDIR,
            )
            runCatching {
                val existing = File(legacyDir, displayName)
                if (existing.exists()) existing.delete()
            }.onFailure { Log.w(TAG, "Legacy pre-delete failed for $displayName: ${it.message}") }
        }
    }

    private companion object {
        const val TAG = "MediaStoreExporter"
        const val JPEG_MIME = "image/jpeg"
        // Both DCIM/ and Pictures/ are Gallery-indexed; Pictures/ is conventional for
        // app-authored images (DCIM is camera-authored).
        const val GALLERY_RELATIVE_PATH = "Pictures/NPIC"
        const val GALLERY_SUBDIR = "NPIC"
    }
}
