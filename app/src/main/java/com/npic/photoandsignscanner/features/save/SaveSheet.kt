package com.npic.photoandsignscanner.features.save

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.npic.photoandsignscanner.core.theme.LocalNpicChrome
import com.npic.photoandsignscanner.core.theme.NpicColors
import com.npic.photoandsignscanner.core.theme.NpicShapes
import com.npic.photoandsignscanner.core.theme.NpicSpacing
import com.npic.photoandsignscanner.core.theme.rememberNpicHaptics
import com.npic.photoandsignscanner.core.ui.NpicBottomSheet
import com.npic.photoandsignscanner.core.ui.NpicButton
import com.npic.photoandsignscanner.core.ui.NpicButtonStyle
import com.npic.photoandsignscanner.core.ui.NpicSegmentedControl
import com.npic.photoandsignscanner.core.ui.NpicTextField
import com.npic.photoandsignscanner.domain.model.ClassNum
import com.npic.photoandsignscanner.domain.model.NamingMode
import com.npic.photoandsignscanner.domain.model.SaveResult

/**
 * The Save bottom sheet (PRD §4.6, DESIGN §7.4).
 *
 * Blocking flow: cannot be dismissed by swipe once opened — [onCancel] fires only through
 * the explicit Cancel button. When the ViewModel's state transitions to `completedRecordId`
 * the destination composable dismisses the sheet and fires [onSaved]. When `duplicate`
 * is populated the Duplicate Dialog (DESIGN §7.5) renders on top blocking the sheet body.
 */
@Composable
fun SaveSheet(
    viewModel: SaveViewModel,
    onCancel: () -> Unit,
    onSaved: (recordId: String) -> Unit,
    onAddPhoto: (() -> Unit)? = null,
    onAddSignature: (() -> Unit)? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptics = rememberNpicHaptics()

    // Fire the completion callback exactly once when the ViewModel signals success. The
    // destination is responsible for popping the back stack + navigating to the Camera
    // (Photo) so the user can keep capturing (PRD §4.8 "After save"). consumeCompleted()
    // clears the id so process-restore / config-change re-collects don't double-navigate.
    androidx.compose.runtime.LaunchedEffect(state.completedRecordId) {
        state.completedRecordId?.let {
            haptics.performLongPress()
            onSaved(it)
            viewModel.consumeCompleted()
        }
    }

    NpicBottomSheet(
        onDismiss = onCancel,
        title = "Save student",
    ) {
        val chrome = LocalNpicChrome.current

        // Subtitle per DESIGN §7.4.
        Text(
            text  = "Class selection is required",
            color = chrome.inkMuted,
            style = MaterialTheme.typography.bodySmall,
        )

        // Class row.
        Column {
            Text(
                text  = "Class",
                color = chrome.inkMuted,
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.size(NpicSpacing.xxs))
            NpicSegmentedControl(
                options = ClassNum.entries,
                selected = state.classNum ?: ClassNum.Nine,
                onSelect = viewModel::setClass,
                labelOf = { it.label },
            )
            if (state.classNum == null) {
                Spacer(Modifier.size(NpicSpacing.xxs))
                Text(
                    text  = "Pick a class to continue",
                    color = chrome.inkMuted,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        // Naming mode row.
        Column {
            Text(
                text  = "Save by",
                color = chrome.inkMuted,
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.size(NpicSpacing.xxs))
            NpicSegmentedControl(
                options = listOf(NamingMode.Kind.Serial, NamingMode.Kind.Name),
                selected = state.namingKind,
                onSelect = viewModel::setNamingKind,
                labelOf = {
                    when (it) {
                        NamingMode.Kind.Serial -> "Serial"
                        NamingMode.Kind.Name   -> "Name"
                    }
                },
            )
        }

        // Value input contextual to the naming mode.
        when (state.namingKind) {
            NamingMode.Kind.Serial -> NpicTextField(
                value = state.serialText,
                onValueChange = viewModel::setSerialText,
                label = "Serial number",
                placeholder = "e.g. 0001",
                helper = state.filenamePreview?.let { "Will be saved as: $it" },
                errorText = state.serialError,
                keyboardType = KeyboardType.Number,
            )
            NamingMode.Kind.Name -> NpicTextField(
                value = state.nameText,
                onValueChange = viewModel::setNameText,
                label = "Student name",
                placeholder = "e.g. Rahul Kumar",
                helper = state.filenamePreview?.let { "Will be saved as: $it" },
            )
        }

        // Preview strip + optional hint.
        Column {
            PreviewStrip(
                photoPath = state.draft.photoPath,
                signaturePath = state.draft.signaturePath,
                onAddPhoto = onAddPhoto,
                onAddSignature = onAddSignature,
            )
            state.previewHint?.let { hint ->
                Spacer(Modifier.size(NpicSpacing.xs))
                Text(
                    text  = hint,
                    color = chrome.terracotta,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            state.errorMessage?.let { err ->
                Spacer(Modifier.size(NpicSpacing.xs))
                Text(
                    text  = err,
                    color = chrome.terracotta,
                    style = MaterialTheme.typography.labelMedium,
                    // Assertive: validation errors interrupt to prevent the user tapping
                    // Save again on an already-broken form (Oracle M-8b-M2-QCC).
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                )
            }
        }

        // Actions row. RowScope.weight flexes Save; Cancel keeps its intrinsic width.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(NpicSpacing.sm),
        ) {
            NpicButton(
                label = "Cancel",
                onClick = onCancel,
                style = NpicButtonStyle.Ghost,
            )
            NpicButton(
                label = "Save",
                onClick = viewModel::save,
                enabled = state.canSave,
                loading = state.saving,
                modifier = Modifier.weight(1f),
            )
        }
    }

    // Duplicate dialog overlays the sheet when populated.
    state.duplicate?.let { dupe ->
        DuplicateSheet(
            duplicate = dupe,
            onKeepExisting = viewModel::dismissDuplicateKeepingExisting,
            onKeepNew = viewModel::resolveDuplicateReplacingExisting,
            onCancel = viewModel::dismissDuplicate,
        )
    }
}

/**
 * Duplicate Dialog (PRD §4.7, DESIGN §7.5). Modal bottom sheet because it needs preview
 * space for two side-by-side cards; the shell of the sheet is [NpicBottomSheet].
 *
 * Layer 8b shows text-only cards summarising each option; real photo/signature previews
 * land when Save persists actual media (which requires the Room + storage layer).
 */
@Composable
private fun DuplicateSheet(
    duplicate: SaveResult.DuplicateFound,
    onKeepExisting: () -> Unit,
    onKeepNew: () -> Unit,
    onCancel: () -> Unit,
) {
    val chrome = LocalNpicChrome.current
    // Selection defaults to "new" — matches the visual saffron highlight the layer 8b
    // impl shipped and keeps the destructive Keep-new button as the primary CTA per
    // PRD §4.7 "radio-select-then-confirm" flow.
    var selection by remember { mutableStateOf(DuplicateSide.New) }

    NpicBottomSheet(onDismiss = onCancel) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NpicSpacing.sm),
        ) {
            Icon(
                imageVector = Icons.Outlined.Warning,
                contentDescription = "Warning",
                tint = chrome.terracotta,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text  = "Duplicate found",
                color = NpicColors.Ink,
                style = MaterialTheme.typography.titleLarge,
            )
        }

        val subjectDescription = when (duplicate.input.naming) {
            is NamingMode.Serial -> "serial ${duplicate.input.filename.removeSuffix(".jpg")}"
            is NamingMode.Name   -> "name \"${(duplicate.input.naming as NamingMode.Name).text.trim()}\""
        }
        Text(
            text  = "A student with $subjectDescription already exists in Class ${duplicate.input.classNum.label}. " +
                    "Which one should be kept?",
            color = chrome.inkMuted,
            style = MaterialTheme.typography.bodyMedium,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(NpicSpacing.sm),
        ) {
            DuplicateCard(
                title = "New (just captured)",
                subtitle = "Class ${duplicate.input.classNum.label} · ${duplicate.input.displayName}",
                selected = selection == DuplicateSide.New,
                onSelect = { selection = DuplicateSide.New },
                modifier = Modifier.weight(1f),
            )
            DuplicateCard(
                title = "Existing",
                subtitle = "Class ${duplicate.existing.classNum.label} · ${duplicate.existing.displayName}",
                selected = selection == DuplicateSide.Existing,
                onSelect = { selection = DuplicateSide.Existing },
                modifier = Modifier.weight(1f),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(NpicSpacing.sm),
        ) {
            NpicButton(
                label = "Keep existing",
                onClick = onKeepExisting,
                style = NpicButtonStyle.Ghost,
            )
            NpicButton(
                label = "Keep new",
                onClick = onKeepNew,
                style = NpicButtonStyle.Destructive,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private enum class DuplicateSide { New, Existing }

@Composable
private fun DuplicateCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val chrome = LocalNpicChrome.current
    Column(
        modifier = modifier
            .clip(NpicShapes.md)
            .background(NpicColors.Surface, NpicShapes.md)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) NpicColors.Saffron else chrome.borderSoft,
                shape = NpicShapes.md,
            )
            .selectable(
                selected = selected,
                onClick = onSelect,
                role = Role.RadioButton,
            )
            .padding(NpicSpacing.md)
            .semantics(mergeDescendants = true) { contentDescription = "$title. $subtitle" },
        verticalArrangement = Arrangement.spacedBy(NpicSpacing.xxs),
    ) {
        Text(
            text  = title,
            color = NpicColors.Ink,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight(600)),
        )
        Text(
            text  = subtitle,
            color = chrome.inkMuted,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
