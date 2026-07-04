package com.npic.photoandsignscanner.features.export

import androidx.compose.runtime.Stable
import com.npic.photoandsignscanner.domain.model.ExportFormat
import com.npic.photoandsignscanner.domain.model.StudentRecord

/**
 * Export sheet UI state (PRD §4.10 + DESIGN §7.8). [records] is the target set — always
 * non-empty when the sheet is open; single-record entries via Detail bottom bar, multi
 * entries via Gallery selection-mode Export.
 *
 * qc-round-12 Oracle #6 MAJOR #4: annotated `@Stable`, NOT `@Immutable`. Kotlin's
 * read-only `List<StudentRecord>` can be backed by a mutable implementation at
 * runtime, so `@Immutable` would over-promise field immutability. The ViewModel
 * emits fresh instances via `_state.update {}` so equals-based skip-recomposition
 * (`@Stable`'s contract) is accurate. Matches the exemplary `EditUiState` pattern.
 * m1597 industry standard.
 */
@Stable
data class ExportUiState(
    val records: List<StudentRecord> = emptyList(),
    val format: ExportFormat = ExportFormat.Combined,
    val exporting: Boolean = false,
    val warningExpanded: Boolean = false,
    /**
     * Count of exported items where JpegCompressor accepted a payload below the 10 KB
     * portal floor (PRD §6.1 Option A). The Export destination raises a toast for the
     * user so they know the portal MAY reject those items.
     */
    val underMinCount: Int = 0,
) {
    val recordCount: Int get() = records.size
    val isMulti: Boolean  get() = records.size > 1

    /**
     * Records that would be skipped for the current [format]: Combined and SignatureOnly
     * skip anything without a signature; PhotoOnly skips anything without a photo.
     */
    val skipped: List<StudentRecord> get() = records.filter { !hasRequiredMedia(it) }
    val effective: List<StudentRecord> get() = records.filter { hasRequiredMedia(it) }

    val hasWarning: Boolean get() = skipped.isNotEmpty()
    val canExport: Boolean get() = !exporting && effective.isNotEmpty()

    private fun hasRequiredMedia(r: StudentRecord): Boolean {
        val hasPhoto = r.photoPath.isNotBlank()
        val hasSig   = r.hasSignature
        return when (format) {
            ExportFormat.Combined      -> hasPhoto && hasSig
            ExportFormat.PhotoOnly     -> hasPhoto
            ExportFormat.SignatureOnly -> hasSig
        }
    }
}
