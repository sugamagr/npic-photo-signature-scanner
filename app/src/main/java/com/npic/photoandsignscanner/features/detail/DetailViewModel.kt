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
 * Presenter for the Detail screen (PRD §4.9). Subscribes to the repo's `observeById`
 * flow so the screen live-updates only when THIS record mutates (Edit → Save-render
 * replaces its photoPath, delete, etc.). Oracle #5 A5 (qc-round-10): scoped Flow —
 * unrelated inserts/updates on other rows no longer wake the Detail screen.
 */
class DetailViewModel(
    private val repository: StudentRepository,
    private val recordId: String,
) : ViewModel() {

    val state: StateFlow<DetailUiState> = repository.observeById(recordId)
        .map { hit ->
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
        private val recordId: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            DetailViewModel(repository, recordId) as T
    }

    private companion object {
        const val STATE_TIMEOUT_MS = 5_000L
    }
}
