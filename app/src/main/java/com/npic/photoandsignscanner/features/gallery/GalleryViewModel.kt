package com.npic.photoandsignscanner.features.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.npic.photoandsignscanner.domain.model.ClassNum
import com.npic.photoandsignscanner.domain.model.SortMode
import com.npic.photoandsignscanner.domain.model.StudentRecord
import com.npic.photoandsignscanner.domain.repo.StudentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Gallery presenter.
 *
 * Reads from a [StudentRepository] — Layer 8b's `InMemoryStudentRepository` today,
 * Room-backed impl slots in behind the same interface later. Filter + sort + selection
 * state live in local [MutableStateFlow]s and are combined with the repo's
 * `observeAll()` on `Dispatchers.Default`, so recomputes never touch Main. Compose
 * subscribes via `state.collectAsState` and gets a fresh [GalleryUiState] on every
 * upstream emission.
 */
class GalleryViewModel(
    private val repository: StudentRepository,
) : ViewModel() {

    private val _classFilter    = MutableStateFlow<ClassNum?>(null)
    private val _sortMode       = MutableStateFlow(SortMode.Newest)
    private val _selected       = MutableStateFlow<Set<String>>(emptySet())
    // m2470: selection mode is separate from selection set — entering is on
    // long-press, exiting is ONLY on explicit user cancel (Close button in the
    // selection top bar, system back, or Delete/Export completion). Deriving
    // it from `_selected.isNotEmpty()` auto-exited when the user deselected
    // their last cell, which felt broken.
    private val _selectionMode  = MutableStateFlow(false)

    val state: StateFlow<GalleryUiState> = combine(
        combine(repository.observeAll(), _classFilter, _sortMode, ::Triple),
        combine(_selected, _selectionMode, ::Pair),
    ) { (all, filter, sort), (selected, selectionMode) ->
        compute(all, filter, sort, selected, selectionMode)
    }.stateIn(
        scope        = viewModelScope,
        started      = SharingStarted.WhileSubscribed(STATE_TIMEOUT_MS),
        initialValue = GalleryUiState(isLoading = true),
    )

    fun setClassFilter(classNum: ClassNum?) { _classFilter.value = classNum }

    fun setSortMode(mode: SortMode) { _sortMode.value = mode }

    /**
     * Enter selection mode and toggle [id]'s presence in the selection set. Called
     * from long-press on a thumbnail (first cell of a session). Idempotent: if
     * selection mode is already on and [id] is already selected, this deselects.
     */
    fun toggleSelect(id: String) {
        _selected.update { current ->
            if (id in current) current - id else current + id
        }
        _selectionMode.value = true
    }

    /**
     * Explicit user cancel — Close button in the selection top bar or system back.
     * Empties the set AND leaves selection mode. This is the ONLY way (aside from
     * Delete-completion in [deleteSelection]) that selection mode can turn off.
     */
    fun clearSelection() {
        _selected.value = emptySet()
        _selectionMode.value = false
    }

    fun selectAll() {
        _selected.value = state.value.records.map { it.id }.toSet()
        _selectionMode.value = true
    }

    /**
     * Best-effort delete of every id in [ids]. Per-id failures are swallowed
     * ([StudentRepository.delete] is idempotent for unknown ids), and the selection is
     * unconditionally cleared on completion so the multi-select toolbar dismisses.
     */
    fun deleteSelection(ids: Set<String>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            ids.forEach { runCatching { repository.delete(it) } }
            _selected.value = emptySet()
            _selectionMode.value = false
        }
    }

    private fun compute(
        all: List<StudentRecord>,
        filter: ClassNum?,
        sort: SortMode,
        selected: Set<String>,
        selectionMode: Boolean,
    ): GalleryUiState {
        val filtered = if (filter == null) all else all.filter { it.classNum == filter }
        val sorted = when (sort) {
            SortMode.Newest -> filtered.sortedByDescending { it.createdAt }
            SortMode.Oldest -> filtered.sortedBy { it.createdAt }
            SortMode.NameAscending ->
                filtered.sortedBy { it.displayName.lowercase() }
            SortMode.NameDescending ->
                filtered.sortedByDescending { it.displayName.lowercase() }
            SortMode.ClassAscending ->
                filtered.sortedWith(compareBy({ it.classNum.ordinal }, { it.serial }))
            SortMode.ClassDescending ->
                filtered.sortedWith(
                    compareByDescending<StudentRecord> { it.classNum.ordinal }.thenBy { it.serial },
                )
        }
        val counts = all.groupingBy { it.classNum }.eachCount()
        // Prune selection IDs that no longer exist (e.g. after Delete). Prevents phantom
        // selection state after upstream records vanish.
        val existingIds = all.mapTo(HashSet(all.size)) { it.id }
        val liveSelected = if (selected.all { it in existingIds }) selected
                           else selected.intersect(existingIds)
        return GalleryUiState(
            records         = sorted,
            totalCount      = all.size,
            countsByClass   = counts,
            classFilter     = filter,
            sortMode        = sort,
            selectedIds     = liveSelected,
            isSelectionMode = selectionMode,
            isLoading       = false,
        )
    }

    class Factory(private val repository: StudentRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            GalleryViewModel(repository) as T
    }

    private companion object {
        const val STATE_TIMEOUT_MS = 5_000L
    }
}
