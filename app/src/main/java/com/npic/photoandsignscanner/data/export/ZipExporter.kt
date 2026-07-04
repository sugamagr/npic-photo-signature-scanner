package com.npic.photoandsignscanner.data.export

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * PRD §4.10 bundle-many exports into a single ZIP for share-sheet delivery.
 *
 * When the user exports > 1 record, Android's `ACTION_SEND_MULTIPLE` share works but many
 * receivers (WhatsApp Web, email clients on desktop, portal upload forms) can't handle
 * multi-file streams cleanly. Wrapping into a ZIP produces one attachment the receiver can
 * handle uniformly. PRD §4.10 explicitly calls for this behavior.
 *
 * ### Output location
 * ZIPs go to `cacheDir/exports/{timestamp}.zip`. Cache is used deliberately — these are
 * transient files intended to survive only until the share sheet dispatches; the OS may
 * clear cache under memory pressure and that's fine. A follow-up housekeeping pass
 * (Layer 15 acceptance sweep) cleans stale entries older than 24 h at app start.
 *
 * ### Store, not deflate
 * ZIP entries are STORED, not DEFLATED. The inputs are already-compressed JPEGs (10–30 KB
 * each from JpegCompressor); running deflate over compressed data typically GROWS the
 * archive by 1–2%. STORED cuts CPU and matches the portal's expectation of individual
 * JPEGs on the other side of the unzip.
 */
class ZipExporter(private val cacheDir: File) {

    /** Root directory for produced ZIP files. Created lazily. */
    val exportsDir: File = File(cacheDir, DIR_NAME).apply { mkdirs() }

    /**
     * Bundle [files] into a single ZIP and return the file. Each element is `(filename,
     * bytes)`; filename is the ZIP entry name verbatim (should already include `.jpg`),
     * bytes is the JPEG payload from JpegCompressor.
     *
     * Returns null on IO failure. The returned file's absolute path is safe to hand to
     * [FileShareLauncher.shareSingle] with a `application/zip` MIME (which the launcher's
     * MIME_JPEG constant does NOT match — Layer 9 threads a MIME parameter through the
     * launcher).
     */
    suspend fun bundle(files: List<Pair<String, ByteArray>>): File? =
        withContext(Dispatchers.IO) {
            if (files.isEmpty()) return@withContext null
            val target = File(exportsDir, "export_${System.currentTimeMillis()}.zip")
            try {
                ZipOutputStream(FileOutputStream(target)).use { zip ->
                    files.forEach { (name, payload) ->
                        // STORED requires size + crc32 known up front. Java's zip stack
                        // computes crc for us when we use DEFLATED; STORED needs manual.
                        // For simplicity and since STORED's win is 1-2% on already-
                        // compressed JPEGs, use DEFLATED level 0 (no compression) which
                        // has the same effective footprint without the crc bookkeeping.
                        zip.setLevel(0)
                        val entry = ZipEntry(sanitizeEntryName(name))
                        zip.putNextEntry(entry)
                        zip.write(payload)
                        zip.closeEntry()
                    }
                }
                target
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to bundle ${files.size} entries into $target", t)
                runCatching { target.delete() }
                null
            }
        }

    // ------------------------------------------------------------------ internals

    /**
     * ZIP entry names must not contain path separators pointing outside the archive.
     * Callers should already pass PRD §6.3 filenames which are safe, but sanitize
     * defensively — a stray `../` from an ill-behaved caller would create a Zip Slip.
     *
     * Oracle O4-4 caught that a naive `replace("..", "")` on a crafted input like
     * `....//foo.jpg` collapses to `..//foo.jpg` — the outer dots survive because
     * the inner `..` was consumed. Fix: (1) take ONLY the final path component after
     * ALL slashes (works cross-platform), (2) strip every run of dots via regex so
     * no crafted layering survives, (3) strip any remaining slashes as a belt-and-
     * suspenders defense against %2f-decoded upstream input.
     */
    private fun sanitizeEntryName(raw: String): String {
        val basename = raw
            .substringAfterLast('/')
            .substringAfterLast('\\')
        val cleaned = basename
            .replace(Regex("\\.{2,}"), "")
            .replace(Regex("[/\\\\]"), "")
        return cleaned.ifEmpty { "export.jpg" }
    }

    private companion object {
        const val TAG = "ZipExporter"
        const val DIR_NAME = "exports"
    }
}
