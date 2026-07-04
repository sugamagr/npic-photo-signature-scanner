package com.npic.photoandsignscanner.domain.usecase

import com.npic.photoandsignscanner.domain.model.ClassNum
import com.npic.photoandsignscanner.domain.model.ExportFormat
import com.npic.photoandsignscanner.domain.model.NamingMode
import com.npic.photoandsignscanner.domain.model.StudentRecord

/**
 * PRD §6.3 export filename scheme. Deterministic — same record + format + naming mode
 * always yields the same filename, so a duplicate export overwrites cleanly.
 *
 * ### Rules (from PRD §6.3)
 * | Format          | Naming | Filename                                        |
 * |-----------------|--------|-------------------------------------------------|
 * | Combined        | Serial | `{class:02d}{serial:04d}.jpg`                   |
 * | Combined        | Name   | `{Name_Underscored}_{class:02d}.jpg`            |
 * | PhotoOnly       | Serial | `photo_{class:02d}{serial:04d}.jpg`             |
 * | PhotoOnly       | Name   | `photo_{Name_Underscored}_{class:02d}.jpg`      |
 * | SignatureOnly   | Serial | `signature_{class:02d}{serial:04d}.jpg`         |
 * | SignatureOnly   | Name   | `signature_{Name_Underscored}_{class:02d}.jpg`  |
 *
 * ### Name normalization
 * When Name mode is picked, [StudentRecord.displayName] is sanitized:
 *   - Whitespace runs collapse to a single underscore.
 *   - Any non-`[A-Za-z0-9_]` char is stripped (protects filesystems + preserves portal parse).
 *   - Empty result (name was all whitespace/punctuation) falls back to Serial mode for that
 *     record — user's displayName was garbage, filename must still be unique.
 *
 * The result carries the `.jpg` extension (portal accepts only JPEG per PRD §6). Callers
 * append it to the export directory / ZIP entry name verbatim.
 */
object GenerateFileName {

    /**
     * Produce the export filename for [record] under [format] and [namingMode]. See rules
     * table in class KDoc.
     */
    fun forExport(
        record: StudentRecord,
        format: ExportFormat,
        namingMode: NamingMode.Kind,
    ): String {
        val prefix: String = when (format) {
            ExportFormat.Combined      -> ""
            ExportFormat.PhotoOnly     -> "photo_"
            ExportFormat.SignatureOnly -> "signature_"
        }

        val stem: String = when (namingMode) {
            NamingMode.Kind.Serial -> serialStem(record.classNum, record.serial)
            NamingMode.Kind.Name -> nameStem(record.displayName, record.classNum)
                // Fallback: if the display name normalises to empty, degrade to Serial so
                // the file is still uniquely addressable in the export bundle.
                ?: serialStem(record.classNum, record.serial)
        }

        return "$prefix$stem$EXTENSION"
    }

    // ------------------------------------------------------------------ helpers

    private fun serialStem(classNum: ClassNum, serial: Int): String {
        // portalCode is already zero-padded to width 2 (see ClassNum). Serial goes to 4 wide.
        return "${classNum.portalCode}${serial.toString().padStart(4, '0')}"
    }

    private fun nameStem(displayName: String, classNum: ClassNum): String? {
        val underscored = displayName
            .trim()
            .replace(WHITESPACE_RUN, "_")
            .replace(FORBIDDEN_CHARS, "")
        if (underscored.isEmpty()) return null
        return "${underscored}_${classNum.portalCode}"
    }

    /** Any run of whitespace collapses to one underscore. */
    private val WHITESPACE_RUN = Regex("\\s+")

    /** Strip anything that isn't ASCII letter/digit/underscore. Protects Devanagari, punctuation. */
    private val FORBIDDEN_CHARS = Regex("[^A-Za-z0-9_]")

    private const val EXTENSION = ".jpg"
}
