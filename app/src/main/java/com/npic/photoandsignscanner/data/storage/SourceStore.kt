package com.npic.photoandsignscanner.data.storage

import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * On-disk store for the canonical "source" bitmaps produced at Save-render time (PRD §5.5).
 *
 * ### What lives here
 * - Photos: long-side 1600 px, JPEG q92 (PRD §5.5.1)
 * - Signatures (both camera-captured and drawn): long-side 1500 px, JPEG q92 (PRD §5.5.2 /
 *   §6.0.2)
 *
 * Files are keyed by [StudentDraft.id] (UUID string, will become Room primary key in
 * Layer 10) at `filesDir/sources/{studentId}_{photo|signature}.jpg`. These are the
 * canonical assets — Detail loads from here, Export renders from here, everything else is
 * a derived cache.
 *
 * ### Why NOT the raw CameraX JPEG
 * The raw JPEG in `cache/drafts/` is 2–4 MB, un-cropped, un-perspective-corrected. The
 * source pipeline runs `EditRenderer.render()` first (rotation → straighten → warp → crop
 * → filter → adjust) and only THEN persists — so what lives here is exactly what the user
 * confirmed on the Edit screen, at the resolution the portal needs. This matches the PRD
 * §5.5 rule "immediately after user confirmation on the Edit screen (on Save), we produce
 * the source".
 *
 * ### Filename semantics
 * Callers pass the [StudentDraft.id] as UUID string. Two files max per draft, matching PRD
 * §5.5's `sources/{studentId}_photo.jpg` and `sources/{studentId}_signature.jpg` layout.
 * The write is atomic-enough for our purposes (single-file JPEG, no partial-write recovery
 * needed — a crashed write leaves the file that Detail's decode simply fails on and the
 * user re-captures).
 */
class SourceStore(private val filesDir: File) {

    /** Root directory for all committed source assets. Created lazily on first access. */
    val sourcesDir: File = File(filesDir, DIR_NAME).apply { mkdirs() }

    /** Absolute path where [studentId]'s committed photo lives (whether or not it exists). */
    fun photoFile(studentId: String): File = File(sourcesDir, "${studentId}_photo.jpg")

    /** Absolute path where [studentId]'s committed signature lives (whether or not it exists). */
    fun signatureFile(studentId: String): File = File(sourcesDir, "${studentId}_signature.jpg")

    /**
     * Persist [bitmap] as the canonical photo source for [studentId]. Scales the long side
     * down to 1600 px (PRD §5.5.1) preserving aspect, encodes JPEG at q92, and returns the
     * absolute file path. Returns null on IO failure.
     *
     * ### Bitmap ownership
     * [bitmap] is NOT recycled here — the caller owns its lifecycle. This lets a single
     * post-render bitmap serve multiple destinations (photo save + Combined layout render)
     * without a defensive copy at each stage.
     */
    suspend fun writePhoto(studentId: String, bitmap: Bitmap): String? =
        writeSized(bitmap, photoFile(studentId), PHOTO_LONG_SIDE)

    /**
     * Persist [bitmap] as the canonical signature source for [studentId]. Scales the long
     * side down to 1500 px (PRD §5.5.2 / §6.0.2) preserving aspect, encodes JPEG at q92,
     * and returns the absolute file path. Returns null on IO failure.
     */
    suspend fun writeSignature(studentId: String, bitmap: Bitmap): String? =
        writeSized(bitmap, signatureFile(studentId), SIG_LONG_SIDE)

    /** Delete BOTH source assets for [studentId]. Idempotent — missing files silently pass. */
    fun deleteFor(studentId: String) {
        runCatching { photoFile(studentId).delete() }
        runCatching { signatureFile(studentId).delete() }
    }

    /**
     * User m1551 S3 destructive Clear-all-data: wipe every file under `sources/`.
     * Idempotent — a missing directory is treated as already-clear. Errors are logged
     * per-file so a single stubborn asset doesn't abort the sweep.
     */
    suspend fun deleteAll(): Unit = withContext(Dispatchers.IO) {
        val files = sourcesDir.listFiles() ?: return@withContext
        for (file in files) {
            runCatching { file.delete() }
                .onFailure { Log.w(TAG, "deleteAll skipped ${file.name}: ${it.message}") }
        }
    }

    // ------------------------------------------------------------------ internals

    private suspend fun writeSized(source: Bitmap, target: File, longSide: Int): String? =
        withContext(Dispatchers.IO) {
            try {
                target.parentFile?.mkdirs()
                val scaled = scaleLongSide(source, longSide)
                try {
                    FileOutputStream(target).use { stream ->
                        scaled.compress(Bitmap.CompressFormat.JPEG, SOURCE_QUALITY, stream)
                    }
                    target.absolutePath
                } finally {
                    // Only recycle if we allocated a NEW scaled bitmap — never touch the
                    // caller's bitmap, they still own it.
                    if (scaled !== source) scaled.recycle()
                }
            } catch (t: Throwable) {
                Log.e(TAG, "writeSized failed for ${target.name}: ${t.message}", t)
                null
            }
        }

    /**
     * Downscale so the longer side equals [target] px, preserving aspect. Returns the input
     * bitmap unchanged when already at or below the target — no wasteful re-encode. This
     * matches Bitmap.createScaledBitmap semantics but with the "no-op if smaller" guard the
     * standard API lacks.
     */
    private fun scaleLongSide(source: Bitmap, target: Int): Bitmap {
        val currentLong = maxOf(source.width, source.height)
        if (currentLong <= target) return source
        val scale = target.toFloat() / currentLong.toFloat()
        val w = (source.width * scale).toInt().coerceAtLeast(1)
        val h = (source.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, w, h, true)
    }

    private companion object {
        const val TAG = "SourceStore"
        const val DIR_NAME = "sources"

        /** PRD §5.5.1 — canonical photo long-side. */
        const val PHOTO_LONG_SIDE = 1600

        /** PRD §5.5.2 / §6.0.2 — canonical signature long-side. */
        const val SIG_LONG_SIDE = 1500

        /** PRD §5.5 — source JPEG quality. Deliberately high; compression to 10–30 KB
         *  happens at Export time, not here. */
        const val SOURCE_QUALITY = 92
    }
}
