package com.npic.photoandsignscanner.features.save

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.npic.photoandsignscanner.domain.model.NamingMode
import com.npic.photoandsignscanner.domain.model.SaveInput
import com.npic.photoandsignscanner.domain.model.SaveResult
import com.npic.photoandsignscanner.domain.model.StudentDraft
import com.npic.photoandsignscanner.domain.model.StudentRecord
import com.npic.photoandsignscanner.domain.repo.StudentRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * m2232 fix: drives the ConfirmUpdate bottom sheet that replaces the Save sheet whenever
 * the Detail screen kicks off an add-media / edit-media flow. The user cannot rewrite
 * class / serial / name here — only confirm that the merged draft should overwrite the
 * target record's row via [StudentRepository.replace].
 *
 * ### Why not reuse SaveSheet
 * Save is a "commit a new record" flow with class, naming-kind and value all editable.
 * Rewriting those fields when the user's intent is "add a signature to Rahul Kumar"
 * silently created a phantom record (m2232). Locking the identity fields would leave a
 * confusing UI with three greyed inputs; a dedicated sheet keeps the mental model clean.
 */
@Immutable
data class UpdateConfirmUiState(
    val target: StudentRecord,
    val draft: StudentDraft,
    val submitting: Boolean = false,
    val errorMessage: String? = null,
    val completed: Boolean = false,
) {
    /** Filename this update would produce, mirroring [SaveUiState.filename]. */
    val filename: String get() = reconstructInput(target).filename
}

class UpdateConfirmViewModel(
    private val repo: StudentRepository,
    private val draft: StudentDraft,
    private val target: StudentRecord,
    private val onDiscardDraftAssets: (draftId: String) -> Unit = {},
) : ViewModel() {

    private val _state = MutableStateFlow(UpdateConfirmUiState(target = target, draft = draft))
    val state: StateFlow<UpdateConfirmUiState> = _state.asStateFlow()

    private var submitJob: Job? = null

    fun submit() {
        if (_state.value.submitting) return
        _state.update { it.copy(submitting = true, errorMessage = null) }
        val input = reconstructInput(target)
        submitJob?.cancel()
        submitJob = viewModelScope.launch {
            when (val outcome = repo.replace(existingId = target.id, draft = draft, input = input)) {
                is SaveResult.Success ->
                    _state.update { it.copy(submitting = false, completed = true) }
                is SaveResult.DuplicateFound ->
                    // repo.replace is an in-place update against an existing id — it
                    // can't emit DuplicateFound in practice, but surface it defensively
                    // so we don't silently swallow a repo contract change.
                    _state.update {
                        it.copy(
                            submitting = false,
                            errorMessage = "Couldn't update — this class + serial is already taken.",
                        )
                    }
                SaveResult.MissingBothMedia ->
                    _state.update {
                        it.copy(
                            submitting = false,
                            errorMessage = "Add a photo or signature before updating.",
                        )
                    }
            }
        }
    }

    /**
     * User dismissed the sheet without updating. The just-committed draft is dropped
     * along with any SourceStore assets its Edit stage wrote — matching the "Keep
     * existing" Save-sheet branch (Oracle O1-11).
     */
    fun cancel() {
        onDiscardDraftAssets(draft.id)
    }

    class Factory(
        private val repo: StudentRepository,
        private val draft: StudentDraft,
        private val target: StudentRecord,
        private val onDiscardDraftAssets: (draftId: String) -> Unit = {},
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            UpdateConfirmViewModel(repo, draft, target, onDiscardDraftAssets) as T
    }
}

/**
 * Rebuilds the exact [SaveInput] that produced [target] on its original save. Requires
 * [StudentRecord.namingKind] to be set — falls back to Serial mode if the field somehow
 * arrived null (shouldn't happen after v2 migration; StudentEntity.toRecord() defaults
 * unknown values to Serial).
 */
private fun reconstructInput(target: StudentRecord): SaveInput {
    val naming: NamingMode = when (target.namingKind) {
        NamingMode.Kind.Serial -> NamingMode.Serial(target.serial)
        NamingMode.Kind.Name   -> NamingMode.Name(target.displayName)
    }
    return SaveInput(classNum = target.classNum, naming = naming)
}
