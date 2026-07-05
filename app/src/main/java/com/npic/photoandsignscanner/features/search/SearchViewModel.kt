package com.npic.photoandsignscanner.features.search

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.npic.photoandsignscanner.domain.model.StudentRecord
import com.npic.photoandsignscanner.domain.repo.StudentRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn

/**
 * Search UI state.
 *
 * qc-round-12 Oracle #6 MAJOR #6: annotated `@Stable`, NOT `@Immutable`. Kotlin's
 * read-only `List<StudentRecord>` (results) can be backed by a mutable implementation
 * at runtime, so `@Immutable` would over-promise field immutability. The ViewModel
 * emits fresh instances via `stateIn` on each combine emission so equals-based
 * skip-recomposition (`@Stable`'s contract) is accurate. Matches the exemplary
 * `EditUiState` pattern. m1597 industry standard.
 */
@Stable
data class SearchUiState(
    val query: String = "",
    val results: List<StudentRecord> = emptyList(),
    val queryTrimmed: String = "",
) {
    val hasQuery: Boolean get() = queryTrimmed.isNotEmpty()
    val isEmpty: Boolean   get() = hasQuery && results.isEmpty()
}

/**
 * Bug#15. Client-side search over `StudentRepository.observeAll()`.
 *
 * The Room DB is currently a single-user local store — realistically bounded to a few
 * hundred records per session — so filtering in-memory is cheaper than adding a `LIKE`
 * DAO query + observing two Flows. Whichever record subset comes back from the repo Flow,
 * we run a case-insensitive substring match on `displayName` + a serial match against the
 * digit-only form of the query + a class-label match.
 *
 * The query text is debounced by 120 ms so keystrokes don't thrash the filter — imperceptible
 * to the user, saves a round of list-diff work on the recomposition side.
 */
class SearchViewModel(
    private val repository: StudentRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val state: StateFlow<SearchUiState> = combine(
        _query.debounce(120L),
        repository.observeAll(),
    ) { q, records ->
        val trimmed = q.trim()
        if (trimmed.isEmpty()) {
            SearchUiState(query = q, results = emptyList(), queryTrimmed = "")
        } else {
            SearchUiState(
                query = q,
                queryTrimmed = trimmed,
                results = filterRecords(records, trimmed),
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_TIMEOUT_MS),
        initialValue = SearchUiState(),
    )

    fun onQueryChange(next: String) {
        _query.value = next
    }

    fun clearQuery() {
        _query.value = ""
    }

    class Factory(private val repository: StudentRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return SearchViewModel(repository) as T
        }
    }

    private companion object {
        const val STATE_TIMEOUT_MS = 5_000L
    }
}

/**
 * Ranks matches so that (a) prefix hits on displayName win, (b) then substring hits on
 * displayName, (c) then class-label hits, (d) then serial-number hits. Case-insensitive.
 * Devanagari + accented-Latin fall through to the raw substring path — good enough for
 * v1.0; if it needs to be smarter, promote to a Room `LIKE` query with ICU.
 */
private fun filterRecords(records: List<StudentRecord>, query: String): List<StudentRecord> {
    val q = query.lowercase()
    val digitsOnly = query.filter { it.isDigit() }
    val classHit = q.removePrefix("class ").trim()
    // m2504 N7: strip a trailing ".jpeg" so a user pasting the exported filename still
    // hits a match; downstream branches compare against displaySerialLabel which has
    // no extension.
    val labelHit = q.removeSuffix(".jpeg")
    val ranked = records.mapNotNull { record ->
        val name = record.displayName.lowercase()
        val classLabel = record.classNum.label.lowercase()
        val serialText = record.serial.toString()
        val serialLabel = record.displaySerialLabel.lowercase()
        val rank: Int = when {
            name.startsWith(q) -> 0
            name.contains(q) -> 1
            classLabel == classHit -> 2
            classHit.isNotEmpty() && classLabel.contains(classHit) -> 3
            serialLabel == labelHit -> 4
            labelHit.isNotEmpty() && serialLabel.startsWith(labelHit) -> 5
            digitsOnly.isNotEmpty() && serialText == digitsOnly -> 6
            digitsOnly.isNotEmpty() && serialText.startsWith(digitsOnly) -> 7
            else -> -1
        }
        if (rank < 0) null else rank to record
    }
    return ranked.sortedWith(
        compareBy(
            { it.first },
            { it.second.classNum.ordinal },
            { it.second.serial },
            { it.second.duplicateIndex },
        ),
    ).map { it.second }
}
