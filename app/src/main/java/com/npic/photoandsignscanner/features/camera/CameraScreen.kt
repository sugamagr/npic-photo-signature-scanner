package com.npic.photoandsignscanner.features.camera

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.npic.photoandsignscanner.core.theme.LocalNpicChrome
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
import com.npic.photoandsignscanner.domain.model.RectI
import java.io.File
import java.util.UUID
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

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
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val controller = remember { NpicCameraController(context) }
    LaunchedEffect(lifecycleOwner) { controller.bindTo(lifecycleOwner) }
    LaunchedEffect(state.flash) { controller.applyFlash(state.flash) }

    var guideBoxPx by remember { mutableStateOf<Rect?>(null) }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    this.controller = controller.controller
                }
            },
        )

        NpicCameraOverlay(
            aspect            = state.mode.guideAspect,
            fillFraction      = state.mode.guideFillFraction,
            onGuideBoxChanged = { guideBoxPx = it },
        )

        HintText(
            visible    = state.hintVisible,
            mode       = state.mode,
            guideBoxPx = guideBoxPx,
            onDismiss  = viewModel::dismissHint,
        )

        CameraTopBar(
            flash        = state.flash,
            sessionCount = state.sessionCount,
            onBack       = onBack,
            onFlashCycle = viewModel::cycleFlash,
        )

        CameraBottomBar(
            mode                     = state.mode,
            capturing                = state.capturing,
            onModeChange             = viewModel::setMode,
            onDrawInsteadClick       = onDrawInsteadClick,
            onImportFromGalleryClick = onImportFromGalleryClick,
            onShutter = {
                val target = File(context.cacheDir, "drafts").apply { mkdirs() }
                    .resolve("${UUID.randomUUID()}.jpg")
                viewModel.onCaptureStarted()
                scope.launch {
                    try {
                        val file = controller.takePicture(target)
                        val boxPx = guideBoxPx
                        val guideBoxImageSpace = boxPx?.let { rectPxToImageSpace(it, density) }
                            ?: RectI(0, 0, 0, 0)
                        viewModel.onCaptureFinished()
                        onCaptureComplete(
                            CameraCapture(
                                rawPath           = file.absolutePath,
                                mode              = state.mode,
                                guideBoxImageSpace = guideBoxImageSpace,
                                capturedAt        = Clock.System.now(),
                            )
                        )
                    } catch (t: Throwable) {
                        viewModel.onCaptureFailed(t.message ?: "Capture failed")
                    }
                }
            },
        )
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

    AnimatedVisibility(
        visible = visible && guideBoxPx != null,
        enter   = fadeIn(animationSpec = NpicMotion.emphasized()),
        exit    = fadeOut(animationSpec = NpicMotion.fast()),
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
    }
    if (visible) {
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
    Box(
        modifier = Modifier
            .size(44.dp)
            .background(chrome.cameraInk.copy(alpha = 0.20f), NpicShapes.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text  = count.toString(),
            color = NpicColors.Ink,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight(700)),
            modifier = Modifier
                .background(NpicColors.Saffron, NpicShapes.full)
                .padding(horizontal = NpicSpacing.xs, vertical = 2.dp),
        )
    }
}

@Composable
private fun BoxScope.CameraBottomBar(
    mode: CameraMode,
    capturing: Boolean,
    onModeChange: (CameraMode) -> Unit,
    onDrawInsteadClick: () -> Unit,
    onImportFromGalleryClick: () -> Unit,
    onShutter: () -> Unit,
) {
    val chrome = LocalNpicChrome.current

    Column(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
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

/**
 * TODO(pipeline): map the overlay's on-screen Rect (in preview-space pixels) to the raw
 * image-space pixel rect via CameraX's OutputTransform / MLKit ImageProxy analyzer. For the
 * scaffold we pass the on-screen rect through so Edit has a non-null seed area to render;
 * OpenCV edge detection currently treats the whole image so the seed is advisory only.
 */
private fun rectPxToImageSpace(rectPx: Rect, density: androidx.compose.ui.unit.Density): RectI {
    @Suppress("UNUSED_PARAMETER") val d = density
    return RectI(
        left   = rectPx.left.toInt(),
        top    = rectPx.top.toInt(),
        right  = rectPx.right.toInt(),
        bottom = rectPx.bottom.toInt(),
    )
}
