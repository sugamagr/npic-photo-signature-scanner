package com.npic.photoandsignscanner.features.save

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.npic.photoandsignscanner.core.theme.LocalNpicChrome
import com.npic.photoandsignscanner.core.theme.NpicColors
import com.npic.photoandsignscanner.core.theme.NpicSpacing
import com.npic.photoandsignscanner.core.theme.rememberNpicHaptics
import com.npic.photoandsignscanner.core.ui.NpicBottomSheet
import com.npic.photoandsignscanner.core.ui.NpicButton
import com.npic.photoandsignscanner.core.ui.NpicButtonStyle
import com.npic.photoandsignscanner.domain.model.NamingMode

/**
 * m2232 fix: the confirm-update bottom sheet used when Detail-launched add-media /
 * edit-media flows return with fresh assets. Renders read-only identity strip + the
 * merged photo/signature preview, then a single Update / Cancel action row.
 *
 * The user cannot change class, serial, or name here — that identity belongs to the
 * target record from [UpdateConfirmViewModel] and is enforced at the repo layer via
 * [com.npic.photoandsignscanner.domain.repo.StudentRepository.replace].
 */
@Composable
fun UpdateConfirmSheet(
    viewModel: UpdateConfirmViewModel,
    onCancel: () -> Unit,
    onUpdated: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptics = rememberNpicHaptics()

    // Success handoff mirrors SaveSheet: fire onUpdated exactly once and let the
    // destination pop the back stack / navigate. viewModel.completed flag stays true
    // for the lifetime of the sheet so config-change re-collect can't double-navigate.
    androidx.compose.runtime.LaunchedEffect(state.completed) {
        if (state.completed) {
            haptics.performLongPress()
            onUpdated()
        }
    }

    NpicBottomSheet(
        onDismiss = onCancel,
        title = "Update student",
    ) {
        val chrome = LocalNpicChrome.current

        // Identity strip: read-only, styled like SaveSheet's subtitle so the user
        // recognises this is the same conceptual layer but locked. m2232 lock — do
        // NOT expose classNum / serial / name as editable inputs here.
        IdentityStrip(
            classLabel = "Class ${state.target.classNum.label}",
            secondary = when (state.target.namingKind) {
                NamingMode.Kind.Serial -> "Serial ${state.target.serial.toString().padStart(4, '0')}"
                NamingMode.Kind.Name   -> state.target.displayName
            },
        )

        Text(
            text  = "Filename stays ${state.filename}.",
            color = chrome.inkMuted,
            style = MaterialTheme.typography.bodySmall,
        )

        // Reuse the standard preview strip so photo + signature render identically
        // to Save. No add-media links (both slots should already be filled by the
        // upstream capture / draw / import stage).
        PreviewStrip(
            photoPath = state.draft.photoPath,
            signaturePath = state.draft.signaturePath,
            onAddPhoto = null,
            onAddSignature = null,
        )

        state.errorMessage?.let { err ->
            Spacer(Modifier.size(NpicSpacing.xs))
            Text(
                text  = err,
                color = chrome.terracotta,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(NpicSpacing.sm),
        ) {
            NpicButton(
                label = "Cancel",
                onClick = {
                    viewModel.cancel()
                    onCancel()
                },
                style = NpicButtonStyle.Ghost,
            )
            NpicButton(
                label = "Update record",
                onClick = viewModel::submit,
                loading = state.submitting,
                enabled = !state.submitting,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun IdentityStrip(
    classLabel: String,
    secondary: String,
) {
    val chrome = LocalNpicChrome.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NpicColors.SaffronSoft.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(horizontal = NpicSpacing.md, vertical = NpicSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(NpicSpacing.md),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text  = classLabel,
                color = NpicColors.SaffronDeep,
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                text  = secondary,
                color = NpicColors.Ink,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Text(
            text  = "Locked",
            color = chrome.inkMuted,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
