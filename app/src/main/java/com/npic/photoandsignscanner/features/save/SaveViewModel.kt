package com.npic.photoandsignscanner.features.save

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.npic.photoandsignscanner.domain.model.ClassNum
import com.npic.photoandsignscanner.domain.model.NamingMode
import com.npic.photoandsignscanner.domain.model.SaveResult
import com.npic.photoandsignscanner.domain.model.StudentDraft
import com.npic.photoandsignscanner.domain.repo.StudentRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
    private val onDiscardDraftAssets: (draftId: String) -> Unit = {},
    preselectedClass: ClassNum? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(SaveUiState(draft = draft))
    val state: StateFlow<SaveUiState> = _state.asStateFlow()

    // Track the active nextSerial() lookup so rapid class-toggling doesn't publish a
    // stale prior-class serial over the current selection (Oracle M-8b-M1 race).
    private var nextSerialJob: Job? = null
    private var saveJob: Job? = null

    init {
        // User m1555 duplicate-to-another-class: caller pre-seeds the target class so
        // the user lands on the Save sheet with class already picked and auto-serial
        // resolving in the background. setClass() also triggers the async nextSerial()
        // lookup that the sheet needs before the Serial input can auto-populate.
        if (preselectedClass != null) setClass(preselectedClass)
    }

    fun setClass(classNum: ClassNum) {
        val current = _state.value
        _state.value = current.copy(classNum = classNum, errorMessage = null)
        if (classNum !in current.autoSerialForClass) {
            nextSerialJob?.cancel()
            nextSerialJob = viewModelScope.launch {
                val next = repo.nextSerial(classNum)
                _state.update { s ->
                    if (s.classNum != classNum) return@update s
                    s.copy(
                        autoSerialForClass = s.autoSerialForClass + (classNum to next),
                        serialText = if (s.namingKind == NamingMode.Kind.Serial && s.serialText.isBlank())
                            formatSerial(next)
                        else s.serialText,
                    )
                }
            }
        }
    }

    fun setNamingKind(kind: NamingMode.Kind) {
        val current = _state.value
        val next = current.copy(namingKind = kind, errorMessage = null)
        // On first switch to Serial, seed the field from cached auto-serial for the picked class.
        val patched = if (kind == NamingMode.Kind.Serial && next.serialText.isBlank()) {
            next.classNum?.let { next.autoSerialForClass[it] }?.let { next.copy(serialText = formatSerial(it)) } ?: next
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
        if (_state.value.saving) return
        val input = _state.value.saveInput ?: return
        val current = _state.value
        _state.value = current.copy(saving = true, errorMessage = null)
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
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

    /**
     * Called by the destination after it has consumed [SaveUiState.completedRecordId] and
     * navigated. Prevents a second navigation if the state is re-collected on process
     * restore or configuration change (Oracle m-8b).
     */
    fun consumeCompleted() {
        _state.update { it.copy(completedRecordId = null) }
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
        // "Keep existing" abandons the current draft. Layer 9's SourceStore already wrote
        // sources/{draftId}_*.jpg at Edit's commit — those files are orphaned unless we
        // explicitly delete them here (Oracle O1-11). The existing record's assets are
        // keyed by a different id and untouched.
        onDiscardDraftAssets(draft.id)
        // Treat the existing record as the completion — caller navigates away from Save.
        _state.value = _state.value.copy(duplicate = null, completedRecordId = dupe.existing.id)
    }

    fun dismissDuplicate() {
        _state.value = _state.value.copy(duplicate = null)
    }

    // Zero-pad to the 4-digit format the Serial input requires (user m1537 B6c). Auto-serial
    // seeding must produce a value the [SaveUiState.serialError] validator accepts, otherwise
    // just picking a class would immediately paint the input Terracotta.
    private fun formatSerial(n: Int): String = n.toString().padStart(SaveUiState.SERIAL_TEXT_LENGTH, '0')

    /**
     * Factory injected at MainActivity level so both the destination composable and the
     * repository singleton stay wired without Hilt. The factory carries the [draft]
     * because it changes per navigation to the Save destination.
     */
    class Factory(
        private val repo: StudentRepository,
        private val draft: StudentDraft,
        private val onDiscardDraftAssets: (draftId: String) -> Unit = {},
        private val preselectedClass: ClassNum? = null,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SaveViewModel(repo, draft, onDiscardDraftAssets, preselectedClass) as T
    }
}
