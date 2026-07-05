package com.npic.photoandsignscanner.domain.model

import androidx.compose.runtime.Immutable
import kotlinx.datetime.Instant

/**
 * UI-facing student record. This is the read model the Gallery / Detail screens consume.
 *
 * The persistent shape (StudentEntity in Room) is a separate concern and will be mapped
 * onto this via a Repository. Deliberately does NOT expose file system paths — screens
 * request bitmaps through a media loader, not by path.
 *
 * `signaturePath = null` means "signature not yet captured/drawn" — Gallery flags this on
 * the thumbnail (DESIGN §6.7, PRD §5.7 export preflight).
 *
 * IDs are UUID strings (PRD §8.1). They survive DB rebuilds, are safe to embed in exported
 * filenames when we want stable per-record references, and match the `StudentEntity`
 * `@PrimaryKey String` used by Room in Layer 10. Selection sets in Gallery flip to
 * `Set<String>` at the same time.
 */
@Immutable
data class StudentRecord(
    val id: String,
    val classNum: ClassNum,
    val serial: Int,
    val displayName: String,
    val photoPath: String,
    val signaturePath: String?,
    // How the record was originally named. Persisted so add-signature / add-photo update
    // flows from Detail (m2232) can reconstruct the exact SaveInput that produced the row,
    // instead of the pre-fix behaviour that dropped the record identity and forced the
    // user to re-pick Serial vs Name (creating a phantom second record).
    val namingKind: NamingMode.Kind,
    // m2502: ordinal position when the user chose "Keep both" on a duplicate. 0 = original
    // (or singleton); 1 = first duplicate; 2 = second; etc. Two records with the same
    // (classNum, serial) are DB-legal only when their duplicateIndex differs — enforced
    // by the composite UNIQUE index on StudentEntity. UI shows "090001 (2)" for index 1,
    // export batches suffix collisions when >1 same filename lands in one batch.
    val duplicateIndex: Int = 0,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    val hasSignature: Boolean get() = signaturePath != null

    /** Filename this record would export as under the Serial naming mode. */
    val serialFilename: String
        get() = "${classNum.portalCode}${serial.toString().padStart(4, '0')}.jpeg"

    /**
     * m2502: user-facing serial label. First occurrence renders as clean "090001"; any
     * subsequent duplicate the user kept renders as "090001 (2)", "090001 (3)", …
     * Used by Detail metadata + Search subtitle + ExportSheet skipped-list. NOT used by
     * export filenames — those are recomputed per-batch in ExportViewModel so a solo
     * export of the "(2)" record still lands as clean "090001.jpeg".
     */
    val displaySerialLabel: String
        get() {
            val base = "${classNum.portalCode}${serial.toString().padStart(4, '0')}"
            return if (duplicateIndex == 0) base else "${base}_${duplicateIndex + 1}"
        }
}
