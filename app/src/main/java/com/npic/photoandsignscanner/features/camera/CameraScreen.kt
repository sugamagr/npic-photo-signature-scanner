package com.npic.photoandsignscanner.features.camera

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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

    val controller = remember { NpicCameraController(context) }
    LaunchedEffect(lifecycleOwner) { controller.bindTo(lifecycleOwner) }
    LaunchedEffect(state.flash) { controller.applyFlash(state.flash) }

    // Layer 11: overlay reports BOTH the guide rect and the canvas Size it laid out in.
    // The canvas size lets the VM run the FILL_CENTER inverse against the captured JPEG's
    // real dimensions instead of the pre-Layer-11 identity-stub that pretended preview
    // px == image px.
    var guideBoxState by remember { mutableStateOf<Pair<Rect, Size>?>(null) }

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
            modifier = Modifier.fillMaxSize(),
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
                flash        = state.flash,
                sessionCount = state.sessionCount,
                onBack       = onBack,
                onFlashCycle = viewModel::cycleFlash,
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                NpicCameraOverlay(
                    aspect            = guideAspectAnim,
                    fillFraction      = guideFillAnim,
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
                    val target = File(context.cacheDir, "drafts").apply { mkdirs() }
                        .resolve("${UUID.randomUUID()}.jpg")
                    // Null when overlay hasn't laid out yet (first-frame shutter). VM
                    // treats null as "no seed available; detectors fall back to full-image
                    // bounds" (PRD §7.1 step 9 / §7.2 step 1).
                    val previewGuide = guideBoxState?.let { (r, s) -> PreviewGuide(r, s) }
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
    onBack: () -> Unit,
    onFlashCycle: () -> Unit,
) {
    val chrome = LocalNpicChrome.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(56.dp)
            .backdropBlur()
            .background(chrome.cameraBg.copy(alpha = 0.85f))
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

            SessionStackBadge(count = sessionCount)
        }
    }
}

@Composable
private fun SessionStackBadge(count: Int) {
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
            .background(chrome.cameraInk.copy(alpha = 0.20f), NpicShapes.sm)
            .semantics { contentDescription = "Captured $count in this session" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text  = label,
            color = NpicColors.Ink,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight(700)),
            modifier = Modifier
                .background(NpicColors.Saffron, NpicShapes.full)
                .padding(horizontal = NpicSpacing.xs, vertical = 2.dp),
        )
    }
}

/**
 * Guarded backdrop blur — DESIGN §7.2 calls for 24dp blur behind the top and bottom bars
 * on SDK 31+ (RenderNode-backed [Modifier.blur] is a no-op on older SDKs).
 */
private fun Modifier.backdropBlur(): Modifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        this.blur(radius = 24.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
    } else this

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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .backdropBlur()
            .background(chrome.cameraBg.copy(alpha = 0.85f))
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


