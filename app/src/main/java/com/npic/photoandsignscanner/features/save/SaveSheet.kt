package com.npic.photoandsignscanner.features.save

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
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
import com.npic.photoandsignscanner.domain.model.StudentRecord
import java.io.File

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

        // Class row. m2403 Bug Q: classNum is now non-nullable with default Nine per
        // SaveUiState. The old "Pick a class to continue" hint + subtitle became dead
        // branches since state.classNum can never be null now.
        Column {
            Text(
                text  = "Class",
                color = chrome.inkMuted,
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.size(NpicSpacing.xxs))
            NpicSegmentedControl(
                options = ClassNum.entries,
                selected = state.classNum,
                onSelect = viewModel::setClass,
                labelOf = { it.label },
            )
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

    // Duplicate dialog overlays the sheet when populated. Swipe-down routes through
    // dismissDuplicateKeepingExisting so the draft assets are cleaned up rather than
    // leaving the app in an ambiguous half-saved state.
    state.duplicate?.let { dupe ->
        DuplicateSheet(
            duplicate = dupe,
            onKeepExisting = viewModel::dismissDuplicateKeepingExisting,
            onReplace = viewModel::resolveDuplicateReplacingExisting,
            onKeepBoth = viewModel::resolveDuplicateKeepingBoth,
            onCancel = viewModel::dismissDuplicateKeepingExisting,
        )
    }
}

/**
 * m2502: N-way Duplicate Sheet (PRD §4.7, DESIGN §7.5).
 *
 * Renders one preview card per existing record + one for the incoming draft, horizontally
 * scrollable so 3+ duplicates coexist without overflowing. Each card shows a 96dp photo
 * thumbnail + 32dp signature underneath so the user can visually distinguish which record
 * they're keeping. Three actions:
 *   - **Keep existing** (Ghost): drop the new capture, keep every existing record as-is
 *   - **Replace**       (Destructive): overwrite the radio-selected existing record with
 *     the new capture. DISABLED until the user picks a card so accidental data loss is
 *     impossible; matches the "conscious destructive action" rule from PRD §4.7.
 *   - **Keep both**     (Primary, Saffron): persist the new capture alongside the
 *     existing rows via [StudentRepository.saveAsDuplicate] with the next duplicateIndex.
 *
 * The dialog is presentation-only — collision resolution + duplicateIndex allocation
 * happen in [SaveViewModel.resolveDuplicateKeepingBoth] → [StudentRepository.saveAsDuplicate].
 */
@Composable
private fun DuplicateSheet(
    duplicate: SaveResult.DuplicateFound,
    onKeepExisting: () -> Unit,
    onReplace: (targetExistingId: String) -> Unit,
    onKeepBoth: () -> Unit,
    onCancel: () -> Unit,
) {
    val chrome = LocalNpicChrome.current
    // rememberSaveable survives rotation so the user's selection is not lost mid-decision.
    var selectedExistingId by rememberSaveable { mutableStateOf<String?>(null) }

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
                text  = if (duplicate.existing.size == 1) "Duplicate found" else "${duplicate.existing.size} duplicates found",
                color = NpicColors.Ink,
                style = MaterialTheme.typography.titleLarge,
            )
        }

        val subjectDescription = when (duplicate.input.naming) {
            // qc-round-9: extension is .jpeg after NamingMode.toFilename fix; strip both
            // suffixes so a future extension change doesn't silently leak into user copy.
            is NamingMode.Serial -> "serial ${duplicate.input.filename.removeSuffix(".jpeg").removeSuffix(".jpg")}"
            is NamingMode.Name   -> "name \"${(duplicate.input.naming as NamingMode.Name).text.trim()}\""
        }
        Text(
            text  = "A student with $subjectDescription already exists in Class ${duplicate.input.classNum.label}. " +
                    "Keep both, replace one, or drop the new capture?",
            color = chrome.inkMuted,
            style = MaterialTheme.typography.bodyMedium,
        )

        DuplicateCardRow(
            duplicate = duplicate,
            selectedExistingId = selectedExistingId,
            onSelectExisting = { id ->
                // Toggle-off when the user taps the currently-selected card so they can
                // deselect back to the null-safe default. Matches the RadioGroup UX
                // convention used elsewhere in the app (SegmentedControl doesn't support
                // this but a radio "grid" typically does).
                selectedExistingId = if (selectedExistingId == id) null else id
            },
        )

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
                label = "Replace",
                onClick = { selectedExistingId?.let(onReplace) },
                style = NpicButtonStyle.Destructive,
                enabled = selectedExistingId != null,
            )
            NpicButton(
                label = "Keep both",
                onClick = onKeepBoth,
                style = NpicButtonStyle.Primary,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Horizontally scrollable row of preview cards. Existing records first (ordered by
 * duplicateIndex ASC from the repo), then the incoming "new" card at the end.
 * The new card is visually distinct (Saffron border always, never radio-selectable)
 * so users can never accidentally pick it as a Replace target.
 */
@Composable
private fun DuplicateCardRow(
    duplicate: SaveResult.DuplicateFound,
    selectedExistingId: String?,
    onSelectExisting: (String) -> Unit,
) {
    val scrollState = rememberScrollState()
    // Right-edge fade appears only while more cards remain off-screen so users see the
    // row is scrollable (matches Adobe Scan's filter strip affordance).
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .drawWithContent {
                drawContent()
                if (scrollState.canScrollForward) {
                    val fadeWidth = 24.dp.toPx()
                    drawRect(
                        brush = Brush.horizontalGradient(
                            0f to Color.Transparent,
                            1f to NpicColors.Surface,
                            startX = size.width - fadeWidth,
                            endX = size.width,
                        ),
                        topLeft = androidx.compose.ui.geometry.Offset(size.width - fadeWidth, 0f),
                        size = androidx.compose.ui.geometry.Size(fadeWidth, size.height),
                    )
                }
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .selectableGroup()
                .semantics { contentDescription = "Duplicate records to compare" },
            horizontalArrangement = Arrangement.spacedBy(NpicSpacing.sm),
        ) {
            duplicate.existing.forEach { record ->
                ExistingDuplicateCard(
                    record = record,
                    selected = selectedExistingId == record.id,
                    onSelect = { onSelectExisting(record.id) },
                )
            }
            IncomingDuplicateCard(
                classNum = duplicate.input.classNum,
                displayName = duplicate.input.displayName,
                photoPath = duplicate.incoming.photoPath,
                signaturePath = duplicate.incoming.signaturePath,
            )
        }
    }
}

@Composable
private fun ExistingDuplicateCard(
    record: StudentRecord,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    DuplicatePreviewCard(
        title = record.displaySerialLabel,
        subtitle = record.displayName.ifBlank { "Class ${record.classNum.label}" },
        photoPath = record.photoPath.takeIf { it.isNotBlank() },
        signaturePath = record.signaturePath,
        selected = selected,
        role = Role.RadioButton,
        onClick = onSelect,
    )
}

@Composable
private fun IncomingDuplicateCard(
    classNum: ClassNum,
    displayName: String,
    photoPath: String?,
    signaturePath: String?,
) {
    DuplicatePreviewCard(
        title = "New (just captured)",
        subtitle = displayName.ifBlank { "Class ${classNum.label}" },
        photoPath = photoPath,
        signaturePath = signaturePath,
        // Always visually highlighted so users see which card is new. Not a Replace
        // target (Replace overwrites an EXISTING row). Focusable so TalkBack / D-pad
        // users can still land on it and hear that it's the new capture.
        selected = true,
        role = null,
        onClick = null,
        semanticsOverride = "New capture. Class ${classNum.label}. ${displayName.ifBlank { "" }}. Not selectable.",
    )
}

/**
 * Shared preview-card shell. 140dp wide × 200dp tall: 96dp photo thumbnail on top,
 * 32dp signature strip below, title + subtitle at the bottom. Selected state shows the
 * 3dp Saffron border used elsewhere in the app (matches (b3) taste tokens).
 */
@Composable
private fun DuplicatePreviewCard(
    title: String,
    subtitle: String,
    photoPath: String?,
    signaturePath: String?,
    selected: Boolean,
    role: Role?,
    onClick: (() -> Unit)?,
    semanticsOverride: String? = null,
) {
    val chrome = LocalNpicChrome.current
    val base = Modifier
        .width(140.dp)
        .clip(NpicShapes.md)
        .background(NpicColors.Surface, NpicShapes.md)
        .border(
            width = if (selected) 3.dp else 1.dp,
            color = if (selected) NpicColors.Saffron else chrome.borderSoft,
            shape = NpicShapes.md,
        )
    val interactive = if (onClick != null && role != null) {
        base.selectable(selected = selected, onClick = onClick, role = role)
    } else {
        base.focusable()
    }
    val description = semanticsOverride ?: "$title. $subtitle"
    Column(
        modifier = interactive
            .padding(NpicSpacing.sm)
            .semantics(mergeDescendants = true) { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(NpicSpacing.xxs),
    ) {
        // Photo thumb — crops to fit, missing-photo placeholder matches the PreviewStrip
        // visual language so both surfaces feel like one design system.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .clip(NpicShapes.sm)
                .background(chrome.saffronSoft.copy(alpha = 0.35f), NpicShapes.sm),
            contentAlignment = Alignment.Center,
        ) {
            if (photoPath != null) {
                AsyncImage(
                    model = File(photoPath),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(96.dp).clip(NpicShapes.sm),
                )
            } else {
                Text(
                    text  = "No photo",
                    color = chrome.inkMuted,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        // Signature strip (32dp tall) — 3:1 aspect matching Signature camera guide.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .clip(NpicShapes.xs)
                .background(chrome.saffronSoft.copy(alpha = 0.20f), NpicShapes.xs),
            contentAlignment = Alignment.Center,
        ) {
            if (signaturePath != null) {
                AsyncImage(
                    model = File(signaturePath),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().height(32.dp),
                )
            } else {
                Text(
                    text  = "No signature",
                    color = chrome.inkFaint,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        Text(
            text  = title,
            color = NpicColors.Ink,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight(600)),
            maxLines = 1,
        )
        Text(
            text  = subtitle,
            color = chrome.inkMuted,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
        )
    }
}
