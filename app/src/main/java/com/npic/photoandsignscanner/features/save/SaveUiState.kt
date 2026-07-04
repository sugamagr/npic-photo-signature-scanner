package com.npic.photoandsignscanner.features.save

import androidx.compose.runtime.Immutable
import com.npic.photoandsignscanner.domain.model.ClassNum
import com.npic.photoandsignscanner.domain.model.NamingMode
import com.npic.photoandsignscanner.domain.model.SaveInput
import com.npic.photoandsignscanner.domain.model.SaveResult
import com.npic.photoandsignscanner.domain.model.StudentDraft

/**
 * View state for the Save bottom sheet (DESIGN §7.4, PRD §4.6).
 *
 * `classNum == null` while the user hasn't picked yet — Save button stays disabled
 * (validation matches PRD §4.6: Class is required). The serial input is auto-populated
 * from [StudentRepository.nextSerial] whenever the class or naming mode changes; the raw
 * text field lives in [serialText] so the user can freely edit / clear it without losing
 * their in-progress digits.
 *
 * `duplicate` is populated when a `save()` call returned [SaveResult.DuplicateFound]; the
 * screen shows the Duplicate Dialog (PRD §4.7) on top and blocks the sheet until the user
 * resolves it. `completedRecord` is set on [SaveResult.Success] so the destination knows
 * to dismiss + navigate + toast.
 */
@Immutable
data class SaveUiState(
    val draft: StudentDraft,
    val classNum: ClassNum? = null,
    val namingKind: NamingMode.Kind = NamingMode.Kind.Serial,
    val serialText: String = "",
    val nameText: String = "",
    val autoSerialForClass: Map<ClassNum, Int> = emptyMap(),
    val saving: Boolean = false,
    val duplicate: SaveResult.DuplicateFound? = null,
    val completedRecordId: Long? = null,
    val errorMessage: String? = null,
) {
    /** Currently-typed serial as an Int, or null if the input isn't a valid 1..9999 number. */
    val serialNumber: Int?
        get() = serialText.toIntOrNull()?.takeIf { it in NamingMode.Serial.MIN..NamingMode.Serial.MAX }

    /** Whether the current inputs form a valid [NamingMode]. */
    val naming: NamingMode?
        get() = when (namingKind) {
            NamingMode.Kind.Serial -> serialNumber?.let(NamingMode::Serial)
            NamingMode.Kind.Name   -> nameText.trim().takeIf(NamingMode.Name::isValid)?.let(NamingMode::Name)
        }

    /** Resolved [SaveInput] when all fields validate; drives the Save button's enabled state. */
    val saveInput: SaveInput?
        get() {
            val c = classNum ?: return null
            val n = naming ?: return null
            return SaveInput(classNum = c, naming = n)
        }

    /** Live filename preview shown as helper text under the input (DESIGN §7.4). */
    val filenamePreview: String?
        get() = saveInput?.filename

    /**
     * All-in green-light: class picked, at least one medium present, valid input.
     * Save button is disabled otherwise per PRD §4.6.
     */
    val canSave: Boolean
        get() = !saving && draft.hasAnyMedia && saveInput != null

    /** PRD §4.6 empty-state message under the preview strip. */
    val previewHint: String?
        get() = if (draft.hasAnyMedia) null else "Add a photo or signature to save."
}
