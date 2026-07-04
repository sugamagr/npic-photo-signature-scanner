package com.npic.photoandsignscanner.features.export

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.npic.photoandsignscanner.domain.model.ExportFormat
import com.npic.photoandsignscanner.domain.model.StudentRecord
import com.npic.photoandsignscanner.domain.repo.ExportPreferences
import com.npic.photoandsignscanner.domain.repo.StudentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Presenter for the Export sheet. Loads the target records by ID from the repo on
 * construction (so late deletes don't crash), seeds the format from [ExportPreferences],
 * and exposes a `share(files)` callback owned by the caller (which owns the Context
 * required to fire the Android Share Sheet).
 */
class ExportViewModel(
    private val repository: StudentRepository,
    private val recordIds: List<Long>,
) : ViewModel() {

    private val _state = MutableStateFlow(ExportUiState(format = ExportPreferences.lastFormat.value))
    val state: StateFlow<ExportUiState> = _state.asStateFlow()

    init {
        loadRecords()
    }

    fun setFormat(format: ExportFormat) {
        ExportPreferences.remember(format)
        _state.value = _state.value.copy(format = format, warningExpanded = false)
    }

    fun toggleWarningExpanded() {
        _state.value = _state.value.copy(warningExpanded = !_state.value.warningExpanded)
    }

    /**
     * Prepares export files (Layer 8c.2 stub: reuses on-disk raw paths). Fires [onReady]
     * with the file paths to share; caller wires this to [com.npic.photoandsignscanner
     * .data.export.FileShareLauncher] since it owns the Android Context.
     */
    fun beginExport(onReady: (List<String>) -> Unit) {
        // Guard against double-tap: without this, tapping Export twice before the
        // share sheet appears would enqueue a second identical share attempt
        // (Oracle M-8b-M3).
        if (_state.value.exporting) return
        val effective = _state.value.effective
        if (effective.isEmpty()) return
        _state.value = _state.value.copy(exporting = true)
        viewModelScope.launch {
            val paths = effective.mapNotNull { record ->
                when (_state.value.format) {
                    ExportFormat.Combined      -> record.photoPath.takeIf { it.isNotBlank() }
                    ExportFormat.PhotoOnly     -> record.photoPath.takeIf { it.isNotBlank() }
                    ExportFormat.SignatureOnly -> record.signaturePath
                }
            }
            _state.value = _state.value.copy(exporting = false)
            onReady(paths)
        }
    }

    private fun loadRecords() {
        viewModelScope.launch {
            val loaded: List<StudentRecord> = recordIds.mapNotNull { id -> repository.getById(id) }
            _state.value = _state.value.copy(records = loaded)
        }
    }

    class Factory(
        private val repository: StudentRepository,
        private val recordIds: List<Long>,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ExportViewModel(repository, recordIds) as T
    }
}
