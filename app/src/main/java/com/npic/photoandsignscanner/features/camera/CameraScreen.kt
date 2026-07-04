package com.npic.photoandsignscanner.features.camera

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.FlashAuto
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.npic.photoandsignscanner.core.theme.LocalNpicChrome
import com.npic.photoandsignscanner.core.theme.LocalReduceMotion
import com.npic.photoandsignscanner.core.theme.NpicColors
import com.npic.photoandsignscanner.core.theme.NpicMotion
import com.npic.photoandsignscanner.core.theme.NpicShapes
import com.npic.photoandsignscanner.core.theme.NpicSpacing
import com.npic.photoandsignscanner.core.theme.rememberNpicHaptics
import com.npic.photoandsignscanner.core.ui.NpicButton
import com.npic.photoandsignscanner.core.ui.NpicButtonStyle
import com.npic.photoandsignscanner.core.ui.NpicCameraOverlay
import com.npic.photoandsignscanner.core.ui.NpicIconButton
import com.npic.photoandsignscanner.core.ui.NpicIconButtonStyle
import com.npic.photoandsignscanner.domain.model.CameraCapture
import com.npic.photoandsignscanner.domain.model.CameraMode
import com.npic.photoandsignscanner.domain.model.FlashMode
import java.io.File
import java.util.UUID

/**
 * Unified Camera screen. Full-bleed CameraX preview under all chrome; mode swapping only
 * reshapes the [NpicCameraOverlay] guide box — the preview never changes color and CameraX
 * is never rebound.
 *
 * The permission gate lives here (not in navigation) because Camera is the ONLY screen that
 * consumes CAMERA and we want the Rationale / Open-Settings branches to feel like part of
 * the same view, not a modal detour.
 *
 * The capture flow:
 *   1. Shutter tap → viewModel.onCaptureStarted (flips the progress arc on).
 *   2. controller.takePicture writes a JPEG to cache/drafts/{uuid}.jpg (suspending).
 *   3. On success, we build a [CameraCapture] with the current on-screen guide-box rect
 *      passed through as [RectI]. TODO(pipeline): map preview-space rect → image-space
 *      via CameraX's OutputTransform when Edit is ready to consume it.
 *   4. viewModel.onCaptureFinished (arc off, sessionCount++) then [onCaptureComplete] fires.
 */
@Composable
fun CameraScreen(
    onBack: () -> Unit,
    onCaptureComplete: (CameraCapture) -> Unit,
    onDrawInsteadClick: () -> Unit,
    onImportFromGalleryClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CameraViewModel = viewModel(),
) {
    val context = LocalContext.current
    val permission = rememberCameraPermissionState(context)
    val state by viewModel.state.collectAsStateWithLifecycle()

    BackHandler(onBack = onBack)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(LocalNpicChrome.current.cameraBg),
    ) {
        when (permission.state) {
            CameraPermissionState.NotAsked        -> CameraNotAsked(onRequest = permission::request)
            CameraPermissionState.DeniedTransient -> CameraDeniedTransient(onRetry = permission::request, onBack = onBack)
            CameraPermissionState.DeniedPermanent -> CameraDeniedPermanent(onBack = onBack)
            CameraPermissionState.Granted         -> CameraGranted(
                state                    = state,
                viewModel                = viewModel,
                onBack                   = onBack,
                onCaptureComplete        = onCaptureComplete,
                onDrawInsteadClick       = onDrawInsteadClick,
                onImportFromGalleryClick = onImportFromGalleryClick,
            )
        }
    }
}

@Composable
private fun CameraGranted(
    state: CameraUiState,
    viewModel: CameraViewModel,
    onBack: () -> Unit,
    onCaptureComplete: (CameraCapture) -> Unit,
    onDrawInsteadClick: () -> Unit,
    onImportFromGalleryClick: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val reduceMotion = LocalReduceMotion.current
    val haptics = rememberNpicHaptics()

    val controller = remember { NpicCameraController(context) }
    LaunchedEffect(lifecycleOwner) { controller.bindTo(lifecycleOwner) }
    LaunchedEffect(state.flash) { controller.applyFlash(state.flash) }
    // Oracle O2-1.1: config-change recomposition may build a fresh NpicCameraController
    // via remember{} while the previous instance still holds a bound camera slot on some
    // OEMs (Samsung One UI observed). Explicit unbind on dispose releases the slot.
    DisposableEffect(controller) {
        onDispose { runCatching { controller.unbind() } }
    }

    // Layer 13: subscribe to the device accelerometer so the overlay level indicator
    // can snap to Sage within ±2° of horizontal. Returns null on devices without an
    // accelerometer or before the first stable reading — NpicCameraOverlay treats
    // null as "hide the level indicator" (PRD §4.2 / DESIGN §6.16).
    val tiltDegrees by rememberDeviceTiltDegrees()

    // Layer 11: overlay reports BOTH the guide rect and the canvas Size it laid out in.
    // The canvas size lets the VM run the FILL_CENTER inverse against the captured JPEG's
    // real dimensions instead of the pre-Layer-11 identity-stub that pretended preview
    // px == image px.
    var guideBoxState by remember { mutableStateOf<Pair<Rect, Size>?>(null) }

    // m2475 Bug U: PreviewView renders full-bleed (entire screen) while the guide-box
    // overlay lives in an inset middle-cell (excludes top+bottom bars). The overlay's
    // Rect + canvas Size are OVERLAY-LOCAL coords, but PreviewGuide's FILL_CENTER inverse
    // must run in PREVIEW-LOCAL coords (matching the pixel space that CameraX actually
    // captured). Track the PreviewView's window-space position + size and the overlay
    // Box's window-space position; the delta becomes previewOffset. Without this, the
    // top bar (~56dp + status bar) shifts the captured photo upward and clips both
    // top and bottom (user's photos m2475: crop rect shifted UP vs the guide box).
    var previewViewPos by remember { mutableStateOf(Offset.Zero) }
    var previewViewSize by remember { mutableStateOf(Size.Zero) }
    var overlayBoxPos by remember { mutableStateOf(Offset.Zero) }

    // DESIGN §7.2: mode swap reshapes the guide box 3:4 ↔ 3:1 over 220ms EaseInOutCubic
    // instead of snapping. Animate the two overlay props, not the enum itself.
    // WCAG 2.3.3: when reduce-motion is on, snap to the new aspect instead of morphing.
    val guideAspectAnim by animateFloatAsState(
        targetValue   = state.mode.guideAspect,
        animationSpec = NpicMotion.standardOrSnap(reduceMotion, NpicMotion.EaseInOutCubic),
        label         = "camera_guide_aspect",
    )
    val guideFillAnim by animateFloatAsState(
        targetValue   = state.mode.guideFillFraction,
        animationSpec = NpicMotion.standardOrSnap(reduceMotion, NpicMotion.EaseInOutCubic),
        label         = "camera_guide_fill",
    )

    // The preview stays full-bleed (framing must not shift when the top/bottom bars fade
    // in/out) — but the OVERLAY math has to sit inside the visible framing area so the
    // guide box never collides with the shutter. Split the two: preview + top/bottom bars
    // live in the outer Box; the overlay + hint live in an inner Column-cell whose height
    // excludes the bottom bar. Was: single Box, causing the 85% signature fill-fraction
    // to punch through the 96dp shutter row (user bug report, 2024-12).
    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coords ->
                    // m2475 Bug U: capture PreviewView's window-space rect. Size feeds
                    // FILL_CENTER inverse scale; position becomes the anchor that the
                    // overlay's offset is measured against.
                    previewViewPos = coords.positionInWindow()
                    previewViewSize = Size(coords.size.width.toFloat(), coords.size.height.toFloat())
                },
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    this.controller = controller.controller
                }
            },
            update = { /* PreviewView keeps its own controller reference; nothing to refresh per recomp. */ },
        )

        Column(Modifier.fillMaxSize()) {
            CameraTopBar(
                flash           = state.flash,
                sessionCount    = state.sessionCount,
                lastCapturePath = state.lastCapturePath,
                onBack          = onBack,
                onFlashCycle    = viewModel::cycleFlash,
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .onGloballyPositioned { coords ->
                        // m2475 Bug U: overlay Box's window-space top-left. The overlay's
                        // rect + canvas Size are LOCAL to this Box, so PreviewGuide needs
                        // (overlayBoxPos - previewViewPos) to translate rect corners into
                        // PreviewView-local space where FILL_CENTER inverse actually applies.
                        overlayBoxPos = coords.positionInWindow()
                    },
            ) {
                NpicCameraOverlay(
                    aspect            = guideAspectAnim,
                    fillFraction      = guideFillAnim,
                    tiltDegrees       = tiltDegrees,
                    onGuideBoxChanged = { rect, size -> guideBoxState = rect to size },
                )
                HintText(
                    visible    = state.hintVisible,
                    mode       = state.mode,
                    guideBoxPx = guideBoxState?.first,
                    onDismiss  = viewModel::dismissHint,
                )
            }
            CameraBottomBar(
                mode                     = state.mode,
                capturing                = state.capturing,
                onModeChange             = viewModel::setMode,
                onDrawInsteadClick       = onDrawInsteadClick,
                onImportFromGalleryClick = onImportFromGalleryClick,
                onShutter = {
                    haptics.performClick()
                    val target = File(context.cacheDir, "drafts").apply { mkdirs() }
                        .resolve("${UUID.randomUUID()}.jpg")
                    // m2475 Bug U: PreviewGuide now takes PREVIEW-view size (not overlay
                    // canvas size) plus the overlay-vs-preview offset. Null when either
                    // side hasn't laid out yet — VM falls back to full-image bounds.
                    val previewGuide = guideBoxState?.let { (r, _) ->
                        if (previewViewSize == Size.Zero) return@let null
                        PreviewGuide(
                            rect          = r,
                            previewSize   = previewViewSize,
                            previewOffset = overlayBoxPos - previewViewPos,
                        )
                    }
                    viewModel.capture(
                        controller   = controller,
                        target       = target,
                        previewGuide = previewGuide,
                        onDone       = onCaptureComplete,
                    )
                },
            )
        }
    }
}

@Composable
private fun HintText(
    visible: Boolean,
    mode: CameraMode,
    guideBoxPx: Rect?,
    onDismiss: () -> Unit,
) {
    val chrome = LocalNpicChrome.current
    val density = LocalDensity.current
    val topDp = guideBoxPx?.let { box ->
        with(density) { (box.bottom + 20.dp.toPx()).toDp() }
    } ?: 0.dp

    val reduceMotion = LocalReduceMotion.current
    AnimatedVisibility(
        visible = visible && guideBoxPx != null,
        enter   = fadeIn(animationSpec = NpicMotion.emphasizedOrSnap(reduceMotion)),
        exit    = fadeOut(animationSpec = NpicMotion.fastOrSnap(reduceMotion)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = topDp)
                .padding(horizontal = NpicSpacing.xl),
            contentAlignment = Alignment.TopCenter,
        ) {
            Text(
                text = when (mode) {
                    CameraMode.Photo     -> "Align the photo inside the box"
                    CameraMode.Signature -> "Align the signature inside the box"
                },
                color = chrome.cameraInk.copy(alpha = 0.80f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .background(Color.Transparent)
                    .padding(NpicSpacing.sm),
            )
        }
        // Key on (visible, mode) INSIDE the AnimatedVisibility content so the timer only
        // runs while the hint is actually on screen. Previously the LaunchedEffect sat
        // outside AnimatedVisibility and could fire onDismiss() on a screen the user had
        // already left (Oracle M-6-M1 code-quality).
        LaunchedEffect(mode) {
            kotlinx.coroutines.delay(6000)
            onDismiss()
        }
    }
}

@Composable
private fun CameraTopBar(
    flash: FlashMode,
    sessionCount: Int,
    lastCapturePath: String?,
    onBack: () -> Unit,
    onFlashCycle: () -> Unit,
) {
    val chrome = LocalNpicChrome.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(topBarScrim(chrome.cameraBg))
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(56.dp)
            .padding(horizontal = NpicSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        NpicIconButton(
            icon = Icons.AutoMirrored.Outlined.ArrowBack,
            contentDescription = "Back",
            onClick = onBack,
            style = NpicIconButtonStyle.OnDark,
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NpicSpacing.xs),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val flashIcon = when (flash) {
                    FlashMode.Auto -> Icons.Outlined.FlashAuto
                    FlashMode.On   -> Icons.Outlined.FlashOn
                    FlashMode.Off  -> Icons.Outlined.FlashOff
                }
                NpicIconButton(
                    icon = flashIcon,
                    contentDescription = "Flash ${flash.label}",
                    onClick = onFlashCycle,
                    style = NpicIconButtonStyle.OnDark,
                )
                Text(
                    text  = flash.label,
                    color = chrome.cameraInkMuted,
                    style = MaterialTheme.typography.labelSmall,
                )
            }

            SessionStackBadge(count = sessionCount, lastCapturePath = lastCapturePath)
        }
    }
}

@Composable
private fun SessionStackBadge(count: Int, lastCapturePath: String?) {
    val chrome = LocalNpicChrome.current
    if (count <= 0) {
        // Reserve the same 44dp footprint so the flash cluster doesn't jump when the
        // first capture lands.
        Box(Modifier.size(44.dp))
        return
    }
    // Cap at "99+" so a runaway session doesn't blow past the 44dp footprint.
    val label = if (count > 99) "99+" else count.toString()
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(NpicShapes.sm)
            .background(chrome.cameraInk.copy(alpha = 0.20f), NpicShapes.sm)
            // Oracle O2-5.2: DESIGN §7.2 specifies 1dp CameraInk@20% border on the badge.
            .border(1.dp, chrome.cameraInk.copy(alpha = 0.20f), NpicShapes.sm)
            .semantics { contentDescription = "Captured $count in this session" },
    ) {
        // Layer 13: last-capture thumbnail fills the badge so the user's most recent frame
        // is directly recognizable at a glance (DESIGN §7.2). Fallback to a Saffron-tinted
        // placeholder when Coil can't decode the file (e.g. cache purge mid-session).
        if (lastCapturePath != null) {
            coil.compose.AsyncImage(
                model = java.io.File(lastCapturePath),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(NpicShapes.sm),
            )
        }
        Text(
            text  = label,
            color = NpicColors.Ink,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight(700)),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(2.dp)
                .background(NpicColors.Saffron, NpicShapes.full)
                .padding(horizontal = NpicSpacing.xs, vertical = 2.dp),
        )
    }
}

// Camera-chrome scrims. DESIGN §7.2 originally asked for "24dp backdrop blur behind the
// top and bottom bars". Compose's `Modifier.blur()` blurs the composable AND its
// descendants (documented behaviour of RenderNode.setRenderEffect at the layer level),
// so the previous `Modifier.blur(24.dp)` on the bar Column actually blurred the shutter
// ring, the mode-pill labels and the gallery icon into invisibility on the near-black
// cameraBg — the user could see the corner brackets but the entire bottom bar rendered
// as a flat slab (device screenshot, m1599). Android provides no first-class
// backdrop-blur primitive in Compose, so the industry-standard fix is to drop the blur
// and use a vertical scrim gradient: this is what Google Camera, VSCO and Adobe
// Lightroom Mobile do for exactly this "chrome floating over a live preview" case.
// The gradient gives visual separation without touching the chrome's own pixels and
// costs one extra draw per frame instead of a full RenderNode blur pass.
private fun topBarScrim(bg: Color): Brush = Brush.verticalGradient(
    colors = listOf(bg.copy(alpha = 0.95f), bg.copy(alpha = 0f)),
)

private fun bottomBarScrim(bg: Color): Brush = Brush.verticalGradient(
    colors = listOf(bg.copy(alpha = 0f), bg.copy(alpha = 0.95f)),
)

@Composable
private fun CameraBottomBar(
    mode: CameraMode,
    capturing: Boolean,
    onModeChange: (CameraMode) -> Unit,
    onDrawInsteadClick: () -> Unit,
    onImportFromGalleryClick: () -> Unit,
    onShutter: () -> Unit,
) {
    val chrome = LocalNpicChrome.current

    // Bug#4: this bar now sits inside CameraGranted's Column (not the outer Box),
    // so no .align() modifier — the parent Column pins it to the bottom by layout order.
    // The scrim replaces the earlier flat cameraBg@85% + Modifier.blur combo (see
    // bottomBarScrim rationale). Scrim before insets so the gradient fades all the way
    // down into the nav-bar area on gesture-nav devices.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(bottomBarScrim(chrome.cameraBg))
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(vertical = NpicSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ModePillsRow(
            mode               = mode,
            onModeChange       = onModeChange,
            onDrawInsteadClick = onDrawInsteadClick,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .padding(horizontal = NpicSpacing.xl),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            NpicIconButton(
                icon = Icons.Outlined.PhotoLibrary,
                contentDescription = "Import from gallery",
                onClick = onImportFromGalleryClick,
                style = NpicIconButtonStyle.OnDark,
            )
            ShutterButton(onClick = onShutter, capturing = capturing)
            // Right slot reserved for v1.1 (front/back camera swap when we ship it).
            Box(Modifier.size(44.dp))
        }
    }
}

@Composable
private fun CameraNotAsked(onRequest: () -> Unit) {
    LaunchedEffect(Unit) { onRequest() }
    Box(
        modifier = Modifier.fillMaxSize().background(LocalNpicChrome.current.cameraBg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Requesting camera permission…",
            color = LocalNpicChrome.current.cameraInkMuted,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun CameraDeniedTransient(onRetry: () -> Unit, onBack: () -> Unit) {
    val chrome = LocalNpicChrome.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(chrome.cameraBg)
            .padding(NpicSpacing.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text  = "Camera access needed",
            color = chrome.cameraInk,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Box(Modifier.height(NpicSpacing.md))
        Text(
            text  = "The scanner cannot digitize passport photos and signatures without the camera. Your captures never leave the device.",
            color = chrome.cameraInkMuted,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Box(Modifier.height(NpicSpacing.xl))
        NpicButton(label = "Allow camera", onClick = onRetry)
        Box(Modifier.height(NpicSpacing.sm))
        NpicButton(label = "Back", onClick = onBack, style = NpicButtonStyle.Ghost)
    }
}

@Composable
private fun CameraDeniedPermanent(onBack: () -> Unit) {
    val chrome = LocalNpicChrome.current
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(chrome.cameraBg)
            .padding(NpicSpacing.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text  = "Camera blocked in Settings",
            color = chrome.cameraInk,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Box(Modifier.height(NpicSpacing.md))
        Text(
            text  = "Open Settings → Apps → NPIC Scanner → Permissions → Camera and switch it on to continue.",
            color = chrome.cameraInkMuted,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Box(Modifier.height(NpicSpacing.xl))
        NpicButton(
            label = "Open Settings",
            onClick = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            },
        )
        Box(Modifier.height(NpicSpacing.sm))
        NpicButton(label = "Back", onClick = onBack, style = NpicButtonStyle.Ghost)
    }
}


