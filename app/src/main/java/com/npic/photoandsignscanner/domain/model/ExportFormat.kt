package com.npic.photoandsignscanner.domain.model

/**
 * Export format choice presented to the user in the Export sheet (PRD §4.10, DESIGN §7.8).
 *
 * `Combined` matches the UPMSP portal template (photo top ~75% + signature bottom ~25%,
 * 4:5 canvas, white bg) — the pre-selected default. `PhotoOnly` and `SignatureOnly` are
 * equal-weight escape hatches for manual portal corrections or non-portal uses.
 *
 * The compression pipeline (PRD §6) treats all three formats identically at the byte-window
 * layer; only the source composition differs.
 */
enum class ExportFormat(val title: String, val subtitle: String) {
    Combined("Combined (photo + signature)", "Portal-ready · matches UPMSP template"),
    PhotoOnly("Photo only",                  "Passport photo without signature"),
    SignatureOnly("Signature only",          "Handwritten signature without photo");

    /** True when the format requires both photo and signature to render meaningfully. */
    val requiresPhoto: Boolean       get() = this != SignatureOnly
    val requiresSignature: Boolean   get() = this != PhotoOnly
}
