package com.npic.photoandsignscanner.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.npic.photoandsignscanner.core.theme.NpicTheme
import com.npic.photoandsignscanner.data.imaging.BitmapAdjustments
import com.npic.photoandsignscanner.data.imaging.BitmapFilters
import com.npic.photoandsignscanner.data.imaging.OpenCvBridge
import com.npic.photoandsignscanner.data.imaging.PhotoEdgeDetector
import com.npic.photoandsignscanner.data.imaging.SignatureInkIsolator
import com.npic.photoandsignscanner.features.camera.CameraScreen
import com.npic.photoandsignscanner.features.edit.EditScreen
import com.npic.photoandsignscanner.features.edit.EditViewModel
import com.npic.photoandsignscanner.features.edit.SharedCaptureHolder
import com.npic.photoandsignscanner.features.gallery.GalleryScreen
import com.npic.photoandsignscanner.features.gallery.GalleryViewModel
import com.npic.photoandsignscanner.features.signaturedraw.SignatureDrawResult
import com.npic.photoandsignscanner.features.signaturedraw.SignatureDrawScreen

/**
 * Single-activity entry point.
 *
 * The whole app is one Activity with a Compose NavHost. Three destinations live today:
 * [Route.Gallery] (which IS the Home per DESIGN §6), [Route.Camera], and [Route.Edit].
 * Signature, Save, Detail, and Export slot in as new [composable] entries during their
 * respective layers — each one owns its own ViewModel via `viewModel()` inside the
 * `composable { }` block so back-stack scoping falls out for free.
 *
 * Camera → Edit hand-off flows through [SharedCaptureHolder], an Activity-scoped ViewModel
 * that carries the last [com.npic.photoandsignscanner.domain.model.CameraCapture] across
 * the route boundary without serialising it through nav-arg strings. When Room lands the
 * capture will read from disk instead and this holder collapses to a nullable cursor.
 */
class MainActivity : ComponentActivity() {

    // Activity-scoped imaging graph. All Edit destinations share the same OpenCV bridge and
    // pipeline instances so Mat allocations don't churn per screen transition. Room + full
    // DI wiring lands with the Save layer; until then this is the composition root.
    private val openCvBridge by lazy { OpenCvBridge() }
    private val photoEdgeDetector by lazy { PhotoEdgeDetector(openCvBridge) }
    private val signatureInkIsolator by lazy { SignatureInkIsolator(openCvBridge) }
    private val bitmapAdjustments by lazy { BitmapAdjustments(openCvBridge) }
    private val bitmapFilters by lazy { BitmapFilters(openCvBridge, bitmapAdjustments) }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        splash.setKeepOnScreenCondition { false }

        val editVmFactory = EditViewModel.Factory(
            detectPhotoEdges = photoEdgeDetector,
            detectSignatureInk = signatureInkIsolator,
            bitmapFilters = bitmapFilters,
            bitmapAdjustments = bitmapAdjustments,
        )

        setContent {
            NpicTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color    = MaterialTheme.colorScheme.background,
                ) {
                    val captureHolder: SharedCaptureHolder = viewModel()
                    NpicNavHost(
                        navController = rememberNavController(),
                        captureHolder = captureHolder,
                        editVmFactory = editVmFactory,
                    )
                }
            }
        }
    }
}

private object Route {
    const val Gallery       = "gallery"
    const val Camera        = "camera"
    const val Edit          = "edit"
    const val SignatureDraw = "signature_draw"
}

@Composable
private fun NpicNavHost(
    navController: NavHostController,
    captureHolder: SharedCaptureHolder,
    editVmFactory: EditViewModel.Factory,
) {
    NavHost(navController = navController, startDestination = Route.Gallery) {
        composable(Route.Gallery) {
            GalleryDestination(
                onCaptureClick = { navController.navigate(Route.Camera) },
            )
        }
        composable(Route.Camera) {
            CameraDestination(
                onBack = { navController.popBackStack() },
                onCaptureComplete = { capture ->
                    captureHolder.push(capture)
                    navController.navigate(Route.Edit) {
                        popUpTo(Route.Camera) { inclusive = true }
                    }
                },
                onDrawInsteadClick = { navController.navigate(Route.SignatureDraw) },
            )
        }
        composable(Route.Edit) {
            EditDestination(
                captureHolder = captureHolder,
                editVmFactory = editVmFactory,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Route.SignatureDraw) {
            SignatureDrawDestination(
                onBack = { navController.popBackStack() },
                onDone = { navController.popBackStack() },
            )
        }
    }
}

@Composable
private fun GalleryDestination(onCaptureClick: () -> Unit) {
    val context = LocalContext.current
    val viewModel: GalleryViewModel = viewModel()
    GalleryScreen(
        viewModel = viewModel,
        onCaptureClick = onCaptureClick,
        onRecordClick = { id ->
            Toast.makeText(context, "Open record #$id → Detail (next layer)", Toast.LENGTH_SHORT).show()
        },
        onExportSelection = { ids ->
            Toast.makeText(context, "Export ${ids.size} record(s) → Share sheet (next layer)", Toast.LENGTH_SHORT).show()
        },
        onDeleteSelection = { ids ->
            Toast.makeText(context, "Delete ${ids.size} record(s) → Confirm dialog (next layer)", Toast.LENGTH_SHORT).show()
        },
        onOverflowClick = {
            Toast.makeText(context, "Overflow menu (next layer)", Toast.LENGTH_SHORT).show()
        },
        onSearchClick = {
            Toast.makeText(context, "Search (next layer)", Toast.LENGTH_SHORT).show()
        },
    )
}

@Composable
private fun CameraDestination(
    onBack: () -> Unit,
    onCaptureComplete: (com.npic.photoandsignscanner.domain.model.CameraCapture) -> Unit,
    onDrawInsteadClick: () -> Unit,
) {
    val context = LocalContext.current
    CameraScreen(
        onBack = onBack,
        onCaptureComplete = onCaptureComplete,
        onDrawInsteadClick = onDrawInsteadClick,
        onImportFromGalleryClick = {
            Toast.makeText(context, "Import from Photos (next layer)", Toast.LENGTH_SHORT).show()
        },
    )
}

@Composable
private fun EditDestination(
    captureHolder: SharedCaptureHolder,
    editVmFactory: EditViewModel.Factory,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val capture by captureHolder.current.collectAsState()

    // Guard the null case defensively: if a user process-restarts into Route.Edit (e.g.
    // via a deep link that doesn't exist yet, or a state restoration edge), pop back to
    // Gallery rather than blank-screen. TODO(repo): once DraftRepository lands, look up
    // the last-active draft here instead.
    val current = capture
    if (current == null) {
        androidx.compose.runtime.LaunchedEffect(Unit) { onBack() }
        return
    }

    val editViewModel: EditViewModel = viewModel(factory = editVmFactory)

    EditScreen(
        capture = current,
        onBack = {
            captureHolder.clear()
            onBack()
        },
        onNext = {
            Toast.makeText(context, "Signature prompt (next layer)", Toast.LENGTH_SHORT).show()
        },
        viewModel = editViewModel,
    )
}

@Composable
private fun SignatureDrawDestination(
    onBack: () -> Unit,
    onDone: (SignatureDrawResult) -> Unit,
) {
    val context = LocalContext.current
    SignatureDrawScreen(
        onBack = onBack,
        onDone = { result ->
            Toast.makeText(
                context,
                "Drew signature with ${result.strokes.size} stroke(s) → Save (next layer)",
                Toast.LENGTH_SHORT,
            ).show()
            onDone(result)
        },
    )
}
