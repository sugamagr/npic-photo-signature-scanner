package com.npic.photoandsignscanner.domain.model

import androidx.compose.runtime.Immutable
import kotlinx.datetime.Instant

/**
 * Where the signature bitmap came from. Photos always come from the camera; signatures can
 * be captured OR drawn (PRD §4.3). Kept as sealed so future variants (imported, imported
 * from Gallery) fit without touching call sites.
 */
@Immutable
sealed interface SignatureSource {
    /** Captured through the camera pipeline: raw JPEG at capture time, user confirms crop in Edit. */
    @Immutable data object Captured : SignatureSource
    /** Drawn on the Signature Draw canvas (PRD §4.4) and rasterized to 1500×500 JPEG. */
    @Immutable data object Drawn    : SignatureSource
}

/**
 * In-progress student record before the Save dialog completes. Carried across the
 * Camera → Edit → Signature (optional) → Save flow via [com.npic.photoandsignscanner
 * .features.edit.SharedCaptureHolder]. When the Save dialog succeeds this becomes a
 * [StudentRecord] persisted by the repository.
 *
 * PRD §4.6 requires at least one of {photoPath, signaturePath} to be present before Save
 * can proceed — expressed here as [hasAnyMedia].
 *
 * TODO(uuid): id already String-typed here; matches PRD §8.1 target. The Long id on
 * [StudentRecord] migrates when Room lands.
 */
@Immutable
data class StudentDraft(
    val id: String,
    val photoPath: String?,
    val signaturePath: String?,
    val photoMode: CameraMode? = null,
    val signatureSource: SignatureSource? = null,
    val createdAt: Instant,
) {
    val hasPhoto: Boolean     get() = photoPath != null
    val hasSignature: Boolean get() = signaturePath != null
    val hasAnyMedia: Boolean  get() = hasPhoto || hasSignature
}
