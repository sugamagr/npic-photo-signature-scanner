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
 * Selection mode is entered on long-press. When [selectedIds] is non-empty, the FAB is
 * replaced by the export/delete action bar (DESIGN §6.1, PRD §5.6).
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
    val isLoading: Boolean = false,
) {
    val isSelectionMode: Boolean get() = selectedIds.isNotEmpty()
    val isEmpty: Boolean get() = records.isEmpty() && !isLoading
    val hasAnyRecords: Boolean get() = totalCount > 0
}
