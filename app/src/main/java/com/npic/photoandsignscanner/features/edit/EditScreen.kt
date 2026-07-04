package com.npic.photoandsignscanner.features.edit

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.npic.photoandsignscanner.core.theme.LocalNpicChrome
import com.npic.photoandsignscanner.core.theme.LocalReduceMotion
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
 * When the ViewModel's factory isn't threaded through (previews, tests), the default
 * `viewModel()` call fails at Save time — the Edit destination in [MainActivity] always
 * provides the factory in production.
 */
@Composable
fun EditScreen(
    capture: CameraCapture,
    onBack: () -> Unit,
    onNext: (sourcePath: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditViewModel,
) {
    LaunchedEffect(capture.rawPath) { viewModel.seed(capture) }

    val state = viewModel.state.collectAsStateWithLifecycle().value ?: return

    // PRD §5.5 commit path: when EditViewModel finishes rendering + writing the source,
    // it publishes the on-disk path via committedSourcePath. Forward it once, then clear
    // so process restore doesn't double-navigate.
    LaunchedEffect(state.committedSourcePath) {
        val path = state.committedSourcePath ?: return@LaunchedEffect
        onNext(path)
        viewModel.consumeCommittedSource()
    }

    val chrome = LocalNpicChrome.current
    Box(modifier = modifier.fillMaxSize().background(chrome.cameraBg)) {
        Column(Modifier.fillMaxSize()) {
            EditTopBar(
                mode = state.edit.mode,
                committing = state.committing,
                onBack = {
                    if (state.dirty) viewModel.requestDiscardConfirm() else onBack()
                },
                onNext = { viewModel.commitEdits() },
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
    committing: Boolean,
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
            .padding(horizontal = NpicSpacing.md),
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
            textAlign = TextAlign.Center,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = NpicSpacing.sm),
        )
        Box(
            modifier = Modifier
                .clip(NpicShapes.sm)
                .then(if (committing) Modifier else Modifier.clickable(onClick = onNext))
                .defaultMinSize(minHeight = 44.dp)
                .padding(horizontal = NpicSpacing.md, vertical = NpicSpacing.sm),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (committing) "Saving…" else "Next",
                color = if (committing) chrome.inkMuted else NpicColors.Saffron,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight(600)),
            )
        }
    }
}

@Composable
private fun ImageViewport(
    state: EditUiState,
    onCropChange: (com.npic.photoandsignscanner.domain.model.CropQuad) -> Unit,
    modifier: Modifier = Modifier,
) {
    val chrome = LocalNpicChrome.current

    // m1679 pinch-zoom on Crop tab. Zoom + pan are hoisted at viewport scope so both the
    // Image and the overlay share the same graphicsLayer transform — that keeps corner
    // handles pixel-locked to source pixels regardless of zoom level. Reset when leaving
    // Crop so re-entering the tab starts at 1× again (matches Adobe Scan behaviour).
    var zoom by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(state.activeTool) {
        if (state.activeTool != EditTool.Crop) {
            zoom = 1f
            pan = Offset.Zero
        }
    }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        zoom = (zoom * zoomChange).coerceIn(1f, 4f)
        pan += panChange
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(NpicSpacing.lg)
            .background(chrome.cameraBg, NpicShapes.md)
            .clip(NpicShapes.md),
        contentAlignment = Alignment.Center,
    ) {
        // Bug#3: Filter/Adjust/Rotate tabs must show a live preview reflecting current
        // edit state; Crop tab keeps raw source so the overlay handles hit the actual
        // pixels. livePreviewBitmap is populated by EditViewModel.scheduleLivePreview().
        val bitmap = if (state.activeTool == EditTool.Crop) {
            state.sourceBitmap
        } else {
            state.livePreviewBitmap ?: state.sourceBitmap
        }
        // Zoom container: single graphicsLayer over both children guarantees the quad
        // stays pixel-locked while the source pans/zooms underneath. transformable is
        // only enabled on the Crop tab and reacts to 2+ pointers (Compose's default),
        // so single-finger corner/box drags on the overlay still route to
        // detectDragGestures inside NpicCropOverlay.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                    translationX = pan.x
                    translationY = pan.y
                }
                .transformable(
                    state = transformState,
                    enabled = state.activeTool == EditTool.Crop,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
                Image(
                    bitmap = imageBitmap,
                    contentDescription = if (state.edit.mode == CameraMode.Photo) {
                        "Captured photo"
                    } else {
                        "Captured signature"
                    },
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (state.activeTool == EditTool.Crop) {
                NpicCropOverlay(
                    quad = state.edit.crop,
                    onQuadChange = onCropChange,
                    onLayoutSize = { /* layer 7c will feed this back into image-space math */ },
                    modifier = Modifier.fillMaxSize(),
                    aspectLock = state.edit.aspectLock,
                )
            }
        }
        AnimatedVisibility(
            visible = state.showInkFallbackBanner,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            InkFallbackBanner()
        }
    }
}

/**
 * "Couldn't detect ink automatically. Adjust the crop manually." — PRD §7.2 fallback banner.
 * Shown as a floating pill at the top of the viewport when [DetectSignatureInk] returned no
 * components. Terracotta accent so it reads as an advisory, not an error.
 */
@Composable
private fun InkFallbackBanner() {
    val chrome = LocalNpicChrome.current
    Row(
        modifier = Modifier
            .padding(top = NpicSpacing.md, start = NpicSpacing.md, end = NpicSpacing.md)
            .clip(NpicShapes.md)
            .background(NpicColors.Terracotta.copy(alpha = 0.92f))
            .padding(horizontal = NpicSpacing.md, vertical = NpicSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = null,
            tint = chrome.cameraInk,
            modifier = Modifier.padding(end = NpicSpacing.xs),
        )
        Text(
            text = "Couldn't detect ink automatically. Adjust the crop manually.",
            color = chrome.cameraInk,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun ToolContentRegion(
    state: EditUiState,
    viewModel: EditViewModel,
) {
    val reduceMotion = LocalReduceMotion.current
    val height by animateDpAsState(
        targetValue = state.activeTool.panelHeight,
        animationSpec = NpicMotion.standardOrSnap(reduceMotion),
        label = "tool_panel_height",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
    ) {
        AnimatedContent(
            targetState = state.activeTool,
            transitionSpec = {
                fadeIn(NpicMotion.fastOrSnap(reduceMotion)) togetherWith fadeOut(NpicMotion.fastOrSnap(reduceMotion))
            },
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
                    thumbnails = state.filterThumbnails,
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
