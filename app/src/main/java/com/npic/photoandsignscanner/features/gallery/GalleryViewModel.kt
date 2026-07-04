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

    private val _classFilter = MutableStateFlow<ClassNum?>(null)
    private val _sortMode    = MutableStateFlow(SortMode.Newest)
    private val _selected    = MutableStateFlow<Set<String>>(emptySet())

    val state: StateFlow<GalleryUiState> = combine(
        repository.observeAll(),
        _classFilter,
        _sortMode,
        _selected,
    ) { all, filter, sort, selected ->
        compute(all, filter, sort, selected)
    }.stateIn(
        scope        = viewModelScope,
        started      = SharingStarted.WhileSubscribed(STATE_TIMEOUT_MS),
        initialValue = GalleryUiState(isLoading = true),
    )

    fun setClassFilter(classNum: ClassNum?) { _classFilter.value = classNum }

    fun setSortMode(mode: SortMode) { _sortMode.value = mode }

    fun toggleSelect(id: String) {
        _selected.update { current ->
            if (id in current) current - id else current + id
        }
    }

    fun clearSelection() {
        if (_selected.value.isNotEmpty()) _selected.value = emptySet()
    }

    fun selectAll() {
        _selected.value = state.value.records.map { it.id }.toSet()
    }

    private fun compute(
        all: List<StudentRecord>,
        filter: ClassNum?,
        sort: SortMode,
        selected: Set<String>,
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
            records        = sorted,
            totalCount     = all.size,
            countsByClass  = counts,
            classFilter    = filter,
            sortMode       = sort,
            selectedIds    = liveSelected,
            isLoading      = false,
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
