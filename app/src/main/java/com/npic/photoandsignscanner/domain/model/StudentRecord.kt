package com.npic.photoandsignscanner.domain.model

import kotlinx.datetime.Instant

/**
 * UI-facing student record. This is the read model the Gallery / Detail screens consume.
 *
 * The persistent shape (StudentEntity in Room) is a separate concern and will be mapped
 * onto this via a Repository. Deliberately does NOT expose file system paths — screens
 * request bitmaps through a media loader, not by path.
 *
 * `signaturePath = null` means "signature not yet captured/drawn" — Gallery renders a
 * terracotta ring on the thumbnail to flag it (DESIGN §7.4, PRD §5.7 export preflight).
 */
data class StudentRecord(
    val id: Long,
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
