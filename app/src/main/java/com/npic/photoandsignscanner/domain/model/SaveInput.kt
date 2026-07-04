package com.npic.photoandsignscanner.domain.model

import androidx.compose.runtime.Immutable

/**
 * The user-committed choices from the Save dialog (PRD §4.6). Combined with a
 * [StudentDraft] this fully specifies a save attempt against [StudentRepository].
 *
 * `displayName` is the human-facing name in either mode:
 *  * Serial → e.g. "090001"
 *  * Name   → the raw trimmed name text
 *
 * Kept separate from the filename so the DB row keeps the friendly display; the filename
 * is regenerated on export.
 */
@Immutable
data class SaveInput(
    val classNum: ClassNum,
    val naming: NamingMode,
) {
    /** Live filename preview shown under the input field (DESIGN §7.4). */
    val filename: String get() = naming.toFilename(classNum)

    /** Friendly display for the Gallery row. */
    val displayName: String get() = when (naming) {
        is NamingMode.Serial -> "${classNum.portalCode}${naming.number.toString().padStart(4, '0')}"
        is NamingMode.Name   -> naming.text.trim()
    }
}
