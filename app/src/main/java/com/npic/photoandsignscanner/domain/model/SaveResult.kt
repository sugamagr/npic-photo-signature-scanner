package com.npic.photoandsignscanner.domain.model

import androidx.compose.runtime.Immutable

/**
 * Outcome of a [StudentRepository.save] call. Modeled as a sealed hierarchy so callers
 * `when`-exhaustively branch on the three legitimate paths per PRD §4.6 + §4.7:
 *
 *  * [Success]           — persisted, Gallery observers see it next tick
 *  * [DuplicateFound]    — same class+serial or class+name already exists; Save dialog
 *                          forwards to the Duplicate Dialog (PRD §4.7) with both records
 *                          side-by-side so the user picks Keep-new or Keep-existing
 *  * [MissingBothMedia]  — draft has no photo AND no signature; Save button should have
 *                          been disabled but the repo double-checks to keep the invariant
 *                          honest
 */
@Immutable
sealed interface SaveResult {
    @Immutable data class Success(val record: StudentRecord) : SaveResult

    @Immutable data class DuplicateFound(
        // m2502: N-way. Ordered by duplicateIndex asc so existing[0] is the original.
        // Dialog shows every existing entry beside the incoming draft; user picks
        // Keep existing (drop new), Replace (which existing?), or Keep all (allocate
        // next duplicateIndex atomically).
        val existing: List<StudentRecord>,
        val incoming: StudentDraft,
        val input: SaveInput,
    ) : SaveResult

    @Immutable data object MissingBothMedia : SaveResult
}
