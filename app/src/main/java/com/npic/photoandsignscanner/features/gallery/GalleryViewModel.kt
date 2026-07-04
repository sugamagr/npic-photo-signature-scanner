package com.npic.photoandsignscanner.features.gallery

import androidx.lifecycle.ViewModel
import com.npic.photoandsignscanner.domain.model.ClassNum
import com.npic.photoandsignscanner.domain.model.SortMode
import com.npic.photoandsignscanner.domain.model.StudentRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Gallery presenter.
 *
 * At shell stage this reads from [MockGalleryData]. When the Room-backed repository lands
 * (Layer 5), swap the [seed] source for a `Flow<List<StudentRecord>>` injected via
 * constructor and collect it into [_all]. The mutation methods (filter / sort / select)
 * stay identical.
 *
 * All derivation runs on the caller (Main). The dataset is bounded (max ~2000 records for
 * a two-year school; median 200–400) so filter+sort in-memory is fine — no LazyPagingSource
 * required.
 */
class GalleryViewModel(
    private val seed: List<StudentRecord> = MockGalleryData.records(),
) : ViewModel() {

    /** Full population — never filtered. Selection references IDs, so this is the source of truth. */
    private val _all = MutableStateFlow(seed)

    private val _classFilter = MutableStateFlow<ClassNum?>(null)
    private val _sortMode    = MutableStateFlow(SortMode.Newest)
    private val _selected    = MutableStateFlow<Set<Long>>(emptySet())

    private val _state = MutableStateFlow(compute())
    val state: StateFlow<GalleryUiState> = _state.asStateFlow()

    // ─────────────────────────────────────────────────────────────────────────
    // Actions
    // ─────────────────────────────────────────────────────────────────────────

    fun setClassFilter(classNum: ClassNum?) {
        _classFilter.value = classNum
        recompute()
    }

    fun setSortMode(mode: SortMode) {
        _sortMode.value = mode
        recompute()
    }

    fun toggleSelect(id: Long) {
        _selected.update { current ->
            if (id in current) current - id else current + id
        }
        recompute()
    }

    fun clearSelection() {
        if (_selected.value.isNotEmpty()) {
            _selected.value = emptySet()
            recompute()
        }
    }

    fun selectAll() {
        _selected.value = _state.value.records.map { it.id }.toSet()
        recompute()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Derivation
    // ─────────────────────────────────────────────────────────────────────────

    private fun recompute() { _state.value = compute() }

    private fun compute(): GalleryUiState {
        val all = _all.value
        val filter = _classFilter.value
        val filtered = if (filter == null) all else all.filter { it.classNum == filter }
        val sorted = when (_sortMode.value) {
            SortMode.Newest -> filtered.sortedByDescending { it.createdAt }
            SortMode.Oldest -> filtered.sortedBy { it.createdAt }
            SortMode.ClassAscending ->
                filtered.sortedWith(compareBy({ it.classNum.ordinal }, { it.serial }))
            SortMode.NameAscending ->
                filtered.sortedBy { it.displayName.lowercase() }
        }
        return GalleryUiState(
            records     = sorted,
            totalCount  = all.size,
            classFilter = filter,
            sortMode    = _sortMode.value,
            selectedIds = _selected.value,
            isLoading   = false,
        )
    }
}
