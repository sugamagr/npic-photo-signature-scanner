package com.npic.photoandsignscanner.features.edit

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.npic.photoandsignscanner.core.theme.LocalNpicChrome
import com.npic.photoandsignscanner.core.theme.LocalReduceMotion
import com.npic.photoandsignscanner.core.theme.NpicColors
import com.npic.photoandsignscanner.core.theme.NpicMotion
import com.npic.photoandsignscanner.core.theme.NpicShapes
import com.npic.photoandsignscanner.core.theme.NpicSpacing
import com.npic.photoandsignscanner.core.ui.NpicButton
import com.npic.photoandsignscanner.core.ui.NpicButtonStyle
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
                canReset = state.edit.hasChanges,
                onBack = {
                    if (state.dirty) viewModel.requestDiscardConfirm() else onBack()
                },
                onReset = { viewModel.resetAll() },
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
    canReset: Boolean,
    onBack: () -> Unit,
    onReset: () -> Unit,
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
                .then(if (committing || !canReset) Modifier else Modifier.clickable(onClick = onReset))
                .defaultMinSize(minHeight = 44.dp)
                .padding(horizontal = NpicSpacing.sm, vertical = NpicSpacing.sm),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Reset",
                color = if (committing || !canReset) chrome.inkMuted else NpicColors.Terracotta,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight(600)),
            )
        }
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

    // m2228 Bug C coordinate bridge. EditState.crop lives in source-image pixel space
    // (EditRenderer + scheduleLivePreview both expect that); NpicCropOverlay reports drags
    // back in overlay-canvas pixel space (its Canvas draw scope). Prior code wrote canvas
    // px straight to EditState.crop, so scheduleLivePreview's `scale = preview.w / source.w`
    // then mis-mapped viewport coords to source coords — filter tap re-rendered a random
    // slice. Fix: bridge both directions using the letterboxed display rect (source aspect
    // vs canvas aspect determines the letterbox offset since the Image is ContentScale.Fit).
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

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
            if (state.activeTool == EditTool.Crop && bitmap != null && canvasSize != IntSize.Zero) {
                // Letterbox: Image is ContentScale.Fit so the drawn image is inset by
                // (canvas - display)/2 on the limiting axis. Corners MUST land inside the
                // drawn image or dragging them yields empty regions.
                val srcW = bitmap.width.toFloat()
                val srcH = bitmap.height.toFloat()
                val canvasW = canvasSize.width.toFloat()
                val canvasH = canvasSize.height.toFloat()
                val srcAspect = srcW / srcH
                val canvasAspect = canvasW / canvasH
                val displayW: Float
                val displayH: Float
                val offsetX: Float
                val offsetY: Float
                if (srcAspect > canvasAspect) {
                    displayW = canvasW
                    displayH = displayW / srcAspect
                    offsetX = 0f
                    offsetY = (canvasH - displayH) / 2f
                } else {
                    displayH = canvasH
                    displayW = displayH * srcAspect
                    offsetX = (canvasW - displayW) / 2f
                    offsetY = 0f
                }

                // Sentinel path: EditState.crop stays at the (0..1) unit square after a
                // fresh capture (guideBoxImageSpace == null, per m2154 removal + m2228
                // GuideBoxCropper). Expand it to full source bounds BEFORE mapping so the
                // corner handles land at the actual image edges instead of a 1×1 blob.
                // Inline sentinel test: no `maxOrdinate` extension on CropQuad — mirrors
                // EditRenderer.isNormalizedSentinel logic (< NORMALIZED_SENTINEL_THRESHOLD = 1.5f).
                val q0 = state.edit.crop
                val maxOrd = maxOf(
                    q0.tl.x, q0.tr.x, q0.br.x, q0.bl.x,
                    q0.tl.y, q0.tr.y, q0.br.y, q0.bl.y,
                )
                val sourceQuad = if (maxOrd < 1.5f) {
                    com.npic.photoandsignscanner.domain.model.CropQuad(
                        tl = Offset(0f, 0f),
                        tr = Offset(srcW, 0f),
                        br = Offset(srcW, srcH),
                        bl = Offset(0f, srcH),
                    )
                } else state.edit.crop

                fun sourceToCanvas(q: com.npic.photoandsignscanner.domain.model.CropQuad) =
                    com.npic.photoandsignscanner.domain.model.CropQuad(
                        tl = Offset(offsetX + (q.tl.x / srcW) * displayW, offsetY + (q.tl.y / srcH) * displayH),
                        tr = Offset(offsetX + (q.tr.x / srcW) * displayW, offsetY + (q.tr.y / srcH) * displayH),
                        br = Offset(offsetX + (q.br.x / srcW) * displayW, offsetY + (q.br.y / srcH) * displayH),
                        bl = Offset(offsetX + (q.bl.x / srcW) * displayW, offsetY + (q.bl.y / srcH) * displayH),
                    )

                fun canvasToSource(q: com.npic.photoandsignscanner.domain.model.CropQuad) =
                    com.npic.photoandsignscanner.domain.model.CropQuad(
                        tl = Offset(((q.tl.x - offsetX) / displayW) * srcW, ((q.tl.y - offsetY) / displayH) * srcH),
                        tr = Offset(((q.tr.x - offsetX) / displayW) * srcW, ((q.tr.y - offsetY) / displayH) * srcH),
                        br = Offset(((q.br.x - offsetX) / displayW) * srcW, ((q.br.y - offsetY) / displayH) * srcH),
                        bl = Offset(((q.bl.x - offsetX) / displayW) * srcW, ((q.bl.y - offsetY) / displayH) * srcH),
                    )

                NpicCropOverlay(
                    quad = sourceToCanvas(sourceQuad),
                    onQuadChange = { canvasQuad -> onCropChange(canvasToSource(canvasQuad)) },
                    onLayoutSize = { canvasSize = it },
                    modifier = Modifier.fillMaxSize(),
                    aspectLock = state.edit.aspectLock,
                )
            } else if (state.activeTool == EditTool.Crop) {
                // First layout pass: canvasSize is still IntSize.Zero. Render an invisible
                // overlay just to receive onLayoutSize so the real overlay appears on the
                // next recomp. Without this, the "if bitmap && canvasSize" gate never opens.
                NpicCropOverlay(
                    quad = state.edit.crop,
                    onQuadChange = {},
                    onLayoutSize = { canvasSize = it },
                    modifier = Modifier.fillMaxSize(),
                    aspectLock = state.edit.aspectLock,
                )
            }
        }
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
                    onResetCrop = { viewModel.resetCrop() },
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
            NpicButton(
                label   = "Discard",
                onClick = onDiscard,
                style   = NpicButtonStyle.Destructive,
            )
        },
        dismissButton = {
            NpicButton(
                label   = "Keep editing",
                onClick = onKeepEditing,
                style   = NpicButtonStyle.Ghost,
            )
        },
    )
}
