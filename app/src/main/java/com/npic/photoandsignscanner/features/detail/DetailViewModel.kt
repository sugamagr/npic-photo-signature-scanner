package com.npic.photoandsignscanner.features.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.npic.photoandsignscanner.domain.repo.StudentRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Presenter for the Detail screen (PRD §4.9). Subscribes to the repo's `observeAll()`
 * flow filtered to the single [recordId] so the screen live-updates when the record is
 * mutated (e.g. Edit → Save-render replaces its photoPath, Layer 8d+).
 */
class DetailViewModel(
    private val repository: StudentRepository,
    private val recordId: Long,
) : ViewModel() {

    val state: StateFlow<DetailUiState> = repository.observeAll()
        .map { records ->
            val hit = records.firstOrNull { it.id == recordId }
            DetailUiState(
                record   = hit,
                isLoading = false,
                notFound = hit == null,
            )
        }
        .stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(STATE_TIMEOUT_MS),
            initialValue = DetailUiState(isLoading = true),
        )

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.delete(recordId)
            onDone()
        }
    }

    class Factory(
        private val repository: StudentRepository,
        private val recordId: Long,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            DetailViewModel(repository, recordId) as T
    }

    private companion object {
        const val STATE_TIMEOUT_MS = 5_000L
    }
}
