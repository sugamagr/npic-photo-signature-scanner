package com.npic.photoandsignscanner.features.edit

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.npic.photoandsignscanner.core.theme.NpicMotion
import com.npic.photoandsignscanner.core.theme.NpicShapes
import com.npic.photoandsignscanner.core.theme.NpicSpacing
import com.npic.photoandsignscanner.core.ui.NpicCropOverlay
import com.npic.photoandsignscanner.core.ui.NpicIconButton
import com.npic.photoandsignscanner.core.ui.NpicIconButtonStyle
import com.npic.photoandsignscanner.domain.model.CameraCapture
import com.npic.photoandsignscanner.domain.model.CameraMode

/**
 * Edit screen. Adobe-Scan continuity: dark chrome, image letterboxed on CameraBg, tool tabs
 * at bottom, tool content directly on CameraBg (NOT a panel — DESIGN §7.3 explicit).
 *
 * BackHandler triggers a discard-confirm dialog when the edit graph is dirty; otherwise it
 * falls through to [onBack]. The `Next` action in the top bar hands off the current
 * [com.npic.photoandsignscanner.domain.model.EditState] via [onNext].
 *
 * The [capture] handoff comes from [SharedCaptureHolder]; the screen seeds the ViewModel on
 * first composition and re-seeds if a different capture arrives (e.g. rapid re-captures).
 */
@Composable
fun EditScreen(
    capture: CameraCapture,
    onBack: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditViewModel = viewModel(),
) {
    LaunchedEffect(capture.rawPath) { viewModel.seed(capture) }

    val state = viewModel.state.collectAsState().value ?: return

    val chrome = LocalNpicChrome.current
    Box(modifier = modifier.fillMaxSize().background(chrome.cameraBg)) {
        Column(Modifier.fillMaxSize()) {
            EditTopBar(
                mode = state.edit.mode,
                onBack = {
                    if (state.dirty) viewModel.requestDiscardConfirm() else onBack()
                },
                onNext = onNext,
            )

            ImageViewport(
                state = state,
                onCropChange = { viewModel.setCrop(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )

            ToolContentRegion(
                state = state,
                viewModel = viewModel,
            )

            EditToolTabs(
                active = state.activeTool,
                onSelect = { viewModel.setActiveTool(it) },
            )
        }
    }

    BackHandler(enabled = true) {
        if (state.dirty) viewModel.requestDiscardConfirm() else onBack()
    }

    if (state.showDiscardConfirm) {
        DiscardConfirmDialog(
            onKeepEditing = { viewModel.dismissDiscardConfirm() },
            onDiscard = {
                viewModel.dismissDiscardConfirm()
                onBack()
            },
        )
    }
}

@Composable
private fun EditTopBar(
    mode: CameraMode,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    val chrome = LocalNpicChrome.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(chrome.cameraBg.copy(alpha = 0.85f))
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(56.dp)
            .padding(horizontal = NpicSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NpicIconButton(
            icon = Icons.AutoMirrored.Outlined.ArrowBack,
            contentDescription = "Back",
            onClick = onBack,
            style = NpicIconButtonStyle.OnDark,
        )
        Text(
            text = if (mode == CameraMode.Photo) "Edit Photo" else "Edit Signature",
            color = chrome.cameraInk,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = NpicSpacing.sm),
        )
        Box(
            modifier = Modifier
                .clip(NpicShapes.sm)
                .clickable(onClick = onNext)
                .padding(horizontal = NpicSpacing.md, vertical = NpicSpacing.sm),
        ) {
            Text(
                text = "Next",
                color = NpicColors.Saffron,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight(600)),
            )
        }
    }
}

@Composable
private fun ImageViewport(
    state: EditUiState,
    onCropChange: (com.npic.photoandsignscanner.core.ui.CropQuad) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Viewport shrinks when a non-Crop tool opens per DESIGN §7.3.0.a — that shrink is
    // driven entirely by the parent Column: this Box just fills whatever cell it gets,
    // so the transition is smooth without a nested animateContentSize.
    val chrome = LocalNpicChrome.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(NpicSpacing.lg)
            .background(chrome.cameraSurface, NpicShapes.md)
            .clip(NpicShapes.md),
        contentAlignment = Alignment.Center,
    ) {
        // TODO(pipeline): swap this placeholder for the actual bitmap (loaded from
        // `state.edit.source.rawPath` via Coil ImageDecoder) with letterboxing math. For
        // the Layer 7a shell we render the crop overlay on a flat CameraSurface rectangle
        // so the interaction is exercisable end-to-end.
        NpicCropOverlay(
            quad = state.edit.crop,
            onQuadChange = onCropChange,
            onLayoutSize = { /* layer 7b will feed this back into image-space math */ },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun ToolContentRegion(
    state: EditUiState,
    viewModel: EditViewModel,
) {
    val height by animateDpAsState(
        targetValue = state.activeTool.panelHeight,
        animationSpec = NpicMotion.standard(),
        label = "tool_panel_height",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
    ) {
        AnimatedContent(
            targetState = state.activeTool,
            transitionSpec = { fadeIn(NpicMotion.fast()) togetherWith fadeOut(NpicMotion.fast()) },
            label = "active_tool",
        ) { tool ->
            when (tool) {
                EditTool.Crop -> CropTool(
                    aspectLock = state.edit.aspectLock,
                    onResetToAuto = { viewModel.resetCropToAuto() },
                    onAspectLockChange = { viewModel.setAspectLock(it) },
                )
                EditTool.Filter -> FilterTool(
                    selected = state.edit.filter,
                    onSelect = { viewModel.setFilter(it) },
                )
                EditTool.Adjust -> AdjustTool(
                    adjustments = state.edit.adjustments,
                    onAdjustmentsChange = { viewModel.setAdjustments(it) },
                )
                EditTool.Rotate -> RotateTool(
                    rotation = state.edit.rotation,
                    onRotateCw = { viewModel.rotateCw() },
                    onRotateCcw = { viewModel.rotateCcw() },
                    onStraightenChange = { viewModel.setStraighten(it) },
                )
            }
        }
    }
}

@Composable
private fun DiscardConfirmDialog(
    onKeepEditing: () -> Unit,
    onDiscard: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onKeepEditing,
        title = {
            Text(
                text = "Discard changes?",
                style = MaterialTheme.typography.headlineMedium,
            )
        },
        text = {
            Text(
                text = "Your edits will be lost. The captured image stays in the drafts folder.",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onDiscard) {
                Text("Discard", color = NpicColors.Terracotta)
            }
        },
        dismissButton = {
            TextButton(onClick = onKeepEditing) {
                Text("Keep editing", color = NpicColors.Saffron)
            }
        },
    )
}
