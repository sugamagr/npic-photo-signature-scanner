package com.npic.photoandsignscanner.features.gallery

import androidx.compose.runtime.Stable
import com.npic.photoandsignscanner.domain.model.ClassNum
import com.npic.photoandsignscanner.domain.model.SortMode
import com.npic.photoandsignscanner.domain.model.StudentRecord

/**
 * Gallery UI state. Snapshot published by the ViewModel; new copies are emitted on each
 * mutation via `_state.update {}` so equals-based skip-recomposition is correct.
 *
 * [records] is the FILTERED + SORTED list ready to render. [totalCount] is the pre-filter
 * population — used by the header ("12 of 47"). [classFilter] = null means "All".
 *
 * Selection mode is entered on long-press and only exits on explicit user cancel
 * (Close button in the selection top bar, system back, or Delete/Export completion).
 * m2470: the mode is a separate flag from [selectedIds] — deriving it from
 * `selectedIds.isNotEmpty()` (the pre-m2470 shape) auto-exited multi-select the
 * moment the user deselected their last cell, which felt broken because industry
 * apps (Samsung Gallery, Google Photos) stay in multi-select mode with zero items
 * selected until the user explicitly leaves.
 *
 * qc-round-12 Oracle #6 MAJOR #2: annotated `@Stable`, NOT `@Immutable`. Kotlin's
 * read-only `List<StudentRecord>` / `Map<ClassNum, Int>` / `Set<String>` types can be
 * backed by mutable implementations at runtime, so `@Immutable` would over-promise
 * field immutability. In practice the ViewModel always emits fresh instances via
 * `_state.update {}` so equals-based skip-recomposition (`@Stable`'s contract) is
 * accurate. Matches the exemplary `EditUiState` pattern. m1597 industry standard.
 */
@Stable
data class GalleryUiState(
    val records: List<StudentRecord> = emptyList(),
    val totalCount: Int = 0,
    val countsByClass: Map<ClassNum, Int> = emptyMap(),
    val classFilter: ClassNum? = null,
    val sortMode: SortMode = SortMode.Newest,
    val selectedIds: Set<String> = emptySet(),
    // m2470: real field, not `selectedIds.isNotEmpty()`. See class KDoc.
    val isSelectionMode: Boolean = false,
    val isLoading: Boolean = false,
) {
    val isEmpty: Boolean get() = records.isEmpty() && !isLoading
    val hasAnyRecords: Boolean get() = totalCount > 0
}
