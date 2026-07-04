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
 * automatically. MediaStore auto-suffixes collisions (` (1)`, ` (2)`) so callers don't need
 * to check for existence.
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

    private companion object {
        const val TAG = "MediaStoreExporter"
        const val JPEG_MIME = "image/jpeg"
        // Both DCIM/ and Pictures/ are Gallery-indexed; Pictures/ is conventional for
        // app-authored images (DCIM is camera-authored).
        const val GALLERY_RELATIVE_PATH = "Pictures/NPIC"
        const val GALLERY_SUBDIR = "NPIC"
    }
}
