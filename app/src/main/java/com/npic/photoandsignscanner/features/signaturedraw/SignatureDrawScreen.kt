package com.npic.photoandsignscanner.features.signaturedraw

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.npic.photoandsignscanner.core.theme.LocalNpicChrome
import com.npic.photoandsignscanner.core.theme.NpicColors
import com.npic.photoandsignscanner.core.theme.NpicShapes
import com.npic.photoandsignscanner.core.theme.NpicSpacing
import com.npic.photoandsignscanner.core.ui.NpicIconButton
import com.npic.photoandsignscanner.core.ui.NpicIconButtonStyle

/**
 * Draw Signature screen (PRD §4.4).
 *
 * Full-screen white canvas + top bar (Close, "Draw Signature" title, Done) + bottom toolbar
 * (thickness slider 2..12 px, Undo, Redo, Clear). Done is disabled until any stroke exists.
 *
 * On finish, the caller receives the committed stroke list via [onDone]. Rasterization to
 * the 1500×500 export bitmap runs at the caller's discretion (usually on Dispatchers.Default
 * before Save, keyed to the actual on-screen canvas size which the caller measures).
 */
@Composable
fun SignatureDrawScreen(
    onBack: () -> Unit,
    onDone: (SignatureDrawResult) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SignatureDrawViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()

    Box(modifier = modifier.fillMaxSize().background(NpicColors.Ivory)) {
        Column(Modifier.fillMaxSize()) {
            SignatureDrawTopBar(
                canFinish = state.canFinish,
                onBack = onBack,
                onDone = {
                    onDone(
                        SignatureDrawResult(
                            strokes = state.strokes,
                        ),
                    )
                },
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(NpicSpacing.md),
                contentAlignment = Alignment.Center,
            ) {
                SignatureCanvas(
                    strokes = state.strokes,
                    inFlightStroke = state.inFlightStroke,
                    onBegin = { viewModel.beginStroke(it) },
                    onExtend = { viewModel.extendStroke(it) },
                    onEnd = { viewModel.endStroke() },
                    onCancel = { viewModel.cancelStroke() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(NpicShapes.md),
                )
            }

            SignatureDrawToolbar(
                thicknessPx = state.thicknessPx,
                canUndo = state.canUndo,
                canRedo = state.canRedo,
                onThicknessChange = { viewModel.setThickness(it) },
                onUndo = { viewModel.undo() },
                onRedo = { viewModel.redo() },
                onClear = { viewModel.requestClearConfirm() },
            )
        }
    }

    BackHandler(enabled = true) { onBack() }

    if (state.showClearConfirm) {
        ClearConfirmDialog(
            onKeep = { viewModel.dismissClearConfirm() },
            onClear = { viewModel.clearAll() },
        )
    }
}

/**
 * Payload handed back to the caller when the user commits their drawn signature. The caller
 * projects [strokes] through the on-screen canvas size onto the 1500×500 export bitmap via
 * [SignatureRasterizer.rasterize].
 */
data class SignatureDrawResult(
    val strokes: List<com.npic.photoandsignscanner.domain.model.DrawStroke>,
)

@Composable
private fun SignatureDrawTopBar(
    canFinish: Boolean,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NpicColors.Surface)
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(56.dp)
            .padding(horizontal = NpicSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NpicIconButton(
            icon = Icons.Outlined.Close,
            contentDescription = "Cancel",
            onClick = onBack,
            style = NpicIconButtonStyle.Plain,
        )
        Text(
            text = "Draw Signature",
            color = NpicColors.Ink,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = NpicSpacing.sm),
        )
        val doneColor = if (canFinish) NpicColors.Saffron else NpicColors.InkFaint
        Box(
            modifier = Modifier
                .clip(NpicShapes.sm)
                .then(if (canFinish) Modifier.clickable { onDone() } else Modifier)
                .padding(horizontal = NpicSpacing.md, vertical = NpicSpacing.sm),
        ) {
            Text(
                text = "Done",
                color = doneColor,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight(600)),
            )
        }
    }
}

@Composable
private fun SignatureDrawToolbar(
    thicknessPx: Float,
    canUndo: Boolean,
    canRedo: Boolean,
    onThicknessChange: (Float) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClear: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NpicColors.Surface)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = NpicSpacing.md, vertical = NpicSpacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Thickness",
                color = NpicColors.Ink,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(end = NpicSpacing.sm),
            )
            Slider(
                value = thicknessPx,
                onValueChange = onThicknessChange,
                valueRange = SignatureDrawUiState.MIN_THICKNESS..SignatureDrawUiState.MAX_THICKNESS,
                colors = SliderDefaults.colors(
                    thumbColor = NpicColors.Saffron,
                    activeTrackColor = NpicColors.Saffron,
                    inactiveTrackColor = NpicColors.BorderStrong,
                ),
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${thicknessPx.toInt()} px",
                color = NpicColors.InkMuted,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = NpicSpacing.sm),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = NpicSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(NpicSpacing.md, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NpicIconButton(
                icon = Icons.AutoMirrored.Outlined.Undo,
                contentDescription = "Undo",
                onClick = onUndo,
                style = NpicIconButtonStyle.Plain,
                enabled = canUndo,
            )
            NpicIconButton(
                icon = Icons.AutoMirrored.Outlined.Redo,
                contentDescription = "Redo",
                onClick = onRedo,
                style = NpicIconButtonStyle.Plain,
                enabled = canRedo,
            )
            NpicIconButton(
                icon = Icons.Outlined.DeleteOutline,
                contentDescription = "Clear",
                onClick = onClear,
                style = NpicIconButtonStyle.Plain,
            )
        }
    }
}

@Composable
private fun ClearConfirmDialog(
    onKeep: () -> Unit,
    onClear: () -> Unit,
) {
    val chrome = LocalNpicChrome.current
    AlertDialog(
        onDismissRequest = onKeep,
        title = { Text("Clear all strokes?", style = MaterialTheme.typography.headlineMedium) },
        text = {
            Text(
                "Your drawing will be erased. This can't be undone.",
                color = chrome.inkMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onClear) {
                Text("Clear", color = NpicColors.Terracotta)
            }
        },
        dismissButton = {
            TextButton(onClick = onKeep) {
                Text("Keep drawing", color = NpicColors.Saffron)
            }
        },
    )
}
