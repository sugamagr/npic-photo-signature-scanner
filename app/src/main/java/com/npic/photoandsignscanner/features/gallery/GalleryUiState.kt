package com.npic.photoandsignscanner.features.gallery

import androidx.compose.runtime.Immutable
import com.npic.photoandsignscanner.domain.model.ClassNum
import com.npic.photoandsignscanner.domain.model.SortMode
import com.npic.photoandsignscanner.domain.model.StudentRecord

/**
 * Gallery UI state. Immutable snapshot; the ViewModel emits new copies on each mutation.
 *
 * [records] is the FILTERED + SORTED list ready to render. [totalCount] is the pre-filter
 * population — used by the header ("12 of 47"). [classFilter] = null means "All".
 *
 * Selection mode is entered on long-press. When [selectedIds] is non-empty, the FAB is
 * replaced by the export/delete action bar (DESIGN §6.1, PRD §5.6).
 */
@Immutable
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
