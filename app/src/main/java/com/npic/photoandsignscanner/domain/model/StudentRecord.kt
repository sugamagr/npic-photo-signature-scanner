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
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    val hasSignature: Boolean get() = signaturePath != null

    /** Filename this record would export as under the Serial naming mode. */
    val serialFilename: String
        get() = "${classNum.portalCode}${serial.toString().padStart(4, '0')}.jpg"
}
