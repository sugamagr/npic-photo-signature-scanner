package com.npic.photoandsignscanner.domain.usecase

import com.npic.photoandsignscanner.domain.model.ClassNum
import com.npic.photoandsignscanner.domain.model.ExportFormat
import com.npic.photoandsignscanner.domain.model.NamingMode
import com.npic.photoandsignscanner.domain.model.StudentRecord

/**
 * Export filename scheme. Deterministic — same record + naming mode always yields the same
 * filename, so a duplicate export overwrites cleanly. Per m1537 user directive, format
 * (Combined vs PhotoOnly vs SignatureOnly) does NOT affect the filename — the file inside
 * the JPEG differs but the name identifies the student, not the composition.
 *
 * ### Rules
 * | Naming | Filename                              |
 * |--------|---------------------------------------|
 * | Serial | `{class:02d}{serial:04d}.jpeg`        |
 * | Name   | `{Name_Underscored}_{class:02d}.jpeg` |
 *
 * ### Name normalization
 * When Name mode is picked, [StudentRecord.displayName] is sanitized:
 *   - Whitespace runs collapse to a single underscore.
 *   - Any non-`[A-Za-z0-9_]` char is stripped (protects filesystems + preserves portal parse).
 *   - Empty result (name was all whitespace/punctuation) falls back to Serial mode for that
 *     record — user's displayName was garbage, filename must still be unique.
 *
 * The result carries the `.jpeg` extension per user directive. Callers append it to the
 * export directory / ZIP entry name verbatim.
 */
object GenerateFileName {

    /**
     * Produce the export filename for [record] under [namingMode]. See rules table in class
     * KDoc. [format] is accepted for call-site compatibility but is ignored — the same
     * filename works for all three formats since only one blob per student is exported at
     * any given time.
     */
    fun forExport(
        record: StudentRecord,
        format: ExportFormat,
        namingMode: NamingMode.Kind,
    ): String {
        val stem: String = when (namingMode) {
            NamingMode.Kind.Serial -> serialStem(record.classNum, record.serial)
            NamingMode.Kind.Name -> nameStem(record.displayName, record.classNum)
                // Fallback: if the display name normalises to empty, degrade to Serial so
                // the file is still uniquely addressable in the export bundle.
                ?: serialStem(record.classNum, record.serial)
        }
        return "$stem$EXTENSION"
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

    private const val EXTENSION = ".jpeg"
}
