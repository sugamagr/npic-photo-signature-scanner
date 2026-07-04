package com.npic.photoandsignscanner.features.save

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.npic.photoandsignscanner.domain.model.ClassNum
import com.npic.photoandsignscanner.domain.model.NamingMode
import com.npic.photoandsignscanner.domain.model.SaveResult
import com.npic.photoandsignscanner.domain.model.StudentDraft
import com.npic.photoandsignscanner.domain.repo.StudentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel driving the Save bottom sheet (PRD §4.6, DESIGN §7.4).
 *
 * State transitions:
 *  * Class picked                      → `setClass(c)` recomputes auto-serial
 *  * Naming toggle Serial ↔ Name       → `setNamingKind(k)` preserves the two independent text fields
 *  * Serial text edited                → `setSerialText(s)`  (raw string retained; validation via [SaveUiState.serialNumber])
 *  * Name text edited                  → `setNameText(t)`
 *  * Save tapped                       → `save()` calls the repo, populates `duplicate` on collision or `completedRecordId` on success
 *  * User picks "Keep new" in dupe UI  → `resolveDuplicateReplacingExisting()`
 *  * User picks "Keep existing"        → `dismissDuplicateKeepingExisting()` (Save flow ends; caller decides where to go)
 */
class SaveViewModel(
    private val repo: StudentRepository,
    private val draft: StudentDraft,
) : ViewModel() {

    private val _state = MutableStateFlow(SaveUiState(draft = draft))
    val state: StateFlow<SaveUiState> = _state.asStateFlow()

    fun setClass(classNum: ClassNum) {
        val current = _state.value
        _state.value = current.copy(classNum = classNum, errorMessage = null)
        // Auto-populate the serial input the first time we see this class.
        if (classNum !in current.autoSerialForClass) {
            viewModelScope.launch {
                val next = repo.nextSerial(classNum)
                _state.value = _state.value.copy(
                    autoSerialForClass = _state.value.autoSerialForClass + (classNum to next),
                    // Only overwrite the serial input if the user is on Serial mode and hasn't typed anything else.
                    serialText = if (_state.value.namingKind == NamingMode.Kind.Serial && _state.value.serialText.isBlank())
                        next.toString()
                    else _state.value.serialText,
                )
            }
        }
    }

    fun setNamingKind(kind: NamingMode.Kind) {
        val current = _state.value
        val next = current.copy(namingKind = kind, errorMessage = null)
        // On first switch to Serial, seed the field from cached auto-serial for the picked class.
        val patched = if (kind == NamingMode.Kind.Serial && next.serialText.isBlank()) {
            next.classNum?.let { next.autoSerialForClass[it] }?.let { next.copy(serialText = it.toString()) } ?: next
        } else next
        _state.value = patched
    }

    fun setSerialText(text: String) {
        // Keep to digits only; empty allowed while editing.
        val cleaned = text.filter { it.isDigit() }.take(4)
        _state.value = _state.value.copy(serialText = cleaned, errorMessage = null)
    }

    fun setNameText(text: String) {
        _state.value = _state.value.copy(nameText = text, errorMessage = null)
    }

    fun save() {
        val input = _state.value.saveInput ?: return
        val current = _state.value
        _state.value = current.copy(saving = true, errorMessage = null)
        viewModelScope.launch {
            when (val outcome = repo.save(current.draft, input)) {
                is SaveResult.Success ->
                    _state.value = _state.value.copy(saving = false, completedRecordId = outcome.record.id)
                is SaveResult.DuplicateFound ->
                    _state.value = _state.value.copy(saving = false, duplicate = outcome)
                SaveResult.MissingBothMedia ->
                    _state.value = _state.value.copy(saving = false, errorMessage = "Add a photo or signature to save.")
            }
        }
    }

    fun resolveDuplicateReplacingExisting() {
        val dupe = _state.value.duplicate ?: return
        _state.value = _state.value.copy(saving = true, duplicate = null)
        viewModelScope.launch {
            when (val outcome = repo.replace(dupe.existing.id, dupe.incoming, dupe.input)) {
                is SaveResult.Success ->
                    _state.value = _state.value.copy(saving = false, completedRecordId = outcome.record.id)
                is SaveResult.DuplicateFound ->
                    // Shouldn't happen after replace, but re-surface if the repo does.
                    _state.value = _state.value.copy(saving = false, duplicate = outcome)
                SaveResult.MissingBothMedia ->
                    _state.value = _state.value.copy(saving = false, errorMessage = "Add a photo or signature to save.")
            }
        }
    }

    fun dismissDuplicateKeepingExisting() {
        val dupe = _state.value.duplicate ?: return
        // Treat the existing record as the completion — caller navigates away from Save.
        _state.value = _state.value.copy(duplicate = null, completedRecordId = dupe.existing.id)
    }

    fun dismissDuplicate() {
        _state.value = _state.value.copy(duplicate = null)
    }

    /**
     * Factory injected at MainActivity level so both the destination composable and the
     * repository singleton stay wired without Hilt. The factory carries the [draft]
     * because it changes per navigation to the Save destination.
     */
    class Factory(
        private val repo: StudentRepository,
        private val draft: StudentDraft,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SaveViewModel(repo, draft) as T
    }
}
