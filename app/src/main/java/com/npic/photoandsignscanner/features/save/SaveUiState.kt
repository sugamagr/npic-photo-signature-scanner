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
    val completedRecordId: String? = null,
    val errorMessage: String? = null,
) {
    /**
     * Currently-typed serial as an Int, or null unless the input is exactly 4 digits AND
     * parses inside [NamingMode.Serial.MIN]..[NamingMode.Serial.MAX]. User m1537 B6c locks
     * serial input to exactly four digits — e.g. `0001`, `0034`, `9999`. Anything shorter
     * (`123`) or longer (rejected upstream by [SaveViewModel.setSerialText]) leaves the
     * Save button disabled.
     */
    val serialNumber: Int?
        get() = serialText
            .takeIf { it.length == SERIAL_TEXT_LENGTH }
            ?.toIntOrNull()
            ?.takeIf { it in NamingMode.Serial.MIN..NamingMode.Serial.MAX }

    /**
     * User-facing validation copy for the Serial field. Surfaces only after the user has
     * typed something so an empty field looks like a normal placeholder rather than a
     * scolding error. `0000` is rejected explicitly since [NamingMode.Serial.MIN] is 1.
     */
    val serialError: String?
        get() = when {
            namingKind != NamingMode.Kind.Serial -> null
            serialText.isEmpty() -> null
            serialText.length < SERIAL_TEXT_LENGTH -> "Serial needs 4 digits (e.g. 0001)."
            serialText.toIntOrNull() == 0 -> "Serial can't be 0000."
            serialNumber == null -> "Serial must be between 0001 and 9999."
            else -> null
        }

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

    companion object {
        const val SERIAL_TEXT_LENGTH = 4
    }
}
