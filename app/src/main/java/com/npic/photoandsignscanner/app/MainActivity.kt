package com.npic.photoandsignscanner.app

import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.npic.photoandsignscanner.core.theme.NpicTheme
import com.npic.photoandsignscanner.data.imaging.BitmapAdjustments
import com.npic.photoandsignscanner.data.imaging.BitmapFilters
import com.npic.photoandsignscanner.data.imaging.OpenCvBridge
import com.npic.photoandsignscanner.data.imaging.PhotoEdgeDetector
import com.npic.photoandsignscanner.data.imaging.SignatureInkIsolator
import com.npic.photoandsignscanner.data.repo.InMemoryStudentRepository
import com.npic.photoandsignscanner.domain.model.CameraMode
import com.npic.photoandsignscanner.domain.model.SignatureSource
import com.npic.photoandsignscanner.domain.model.StudentDraft
import com.npic.photoandsignscanner.domain.repo.StudentRepository
import com.npic.photoandsignscanner.features.camera.CameraScreen
import com.npic.photoandsignscanner.data.export.FileShareLauncher
import com.npic.photoandsignscanner.features.detail.DetailScreen
import com.npic.photoandsignscanner.features.detail.DetailViewModel
import com.npic.photoandsignscanner.features.edit.EditScreen
import com.npic.photoandsignscanner.features.export.ExportSheet
import com.npic.photoandsignscanner.features.export.ExportViewModel
import com.npic.photoandsignscanner.features.edit.EditViewModel
import com.npic.photoandsignscanner.features.edit.SharedCaptureHolder
import com.npic.photoandsignscanner.features.gallery.GalleryScreen
import com.npic.photoandsignscanner.features.gallery.GalleryViewModel
import com.npic.photoandsignscanner.features.save.SaveSheet
import com.npic.photoandsignscanner.features.save.SaveViewModel
import com.npic.photoandsignscanner.features.save.SignaturePromptSheet
import com.npic.photoandsignscanner.features.signaturedraw.SignatureDrawResult
import com.npic.photoandsignscanner.features.signaturedraw.SignatureDrawScreen
import com.npic.photoandsignscanner.features.signaturedraw.SignatureRasterizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Single-activity entry point.
 *
 * The whole app is one Activity with a Compose NavHost. Five destinations live today:
 * [Route.Gallery] (which IS the Home per DESIGN §6), [Route.Camera], [Route.Edit],
 * [Route.SignatureDraw], and [Route.Save]. Detail + Export slot in as new [composable]
 * entries during their respective layers.
 *
 * The Camera → Edit → Save flow flows through [SharedCaptureHolder], an Activity-scoped
 * ViewModel that carries the last [com.npic.photoandsignscanner.domain.model.CameraCapture]
 * plus the in-progress [StudentDraft] across route boundaries without serialising them
 * through nav-arg strings. When Room lands the holder collapses to a nullable cursor
 * backed by a `DraftRepository`.
 */
class MainActivity : ComponentActivity() {

    // Activity-scoped imaging graph. All Edit destinations share the same OpenCV bridge
    // and pipeline instances so Mat allocations don't churn per screen transition. Room
    // + full DI wiring lands with the Save layer; until then this is the composition root.
    private val openCvBridge by lazy { OpenCvBridge() }
    private val photoEdgeDetector by lazy { PhotoEdgeDetector(openCvBridge) }
    private val signatureInkIsolator by lazy { SignatureInkIsolator(openCvBridge) }
    private val bitmapAdjustments by lazy { BitmapAdjustments(openCvBridge) }
    private val bitmapFilters by lazy { BitmapFilters(openCvBridge, bitmapAdjustments) }

    // In-memory StudentRepository seeded from MockGalleryData. Gallery reads from this
    // for future migration off the mock seed; Save writes into it. Room impl slots in
    // behind the interface without touching the composables.
    private val studentRepository: StudentRepository by lazy { InMemoryStudentRepository() }

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
                        studentRepository = studentRepository,
                    )
                }
            }
        }
    }
}

private object Route {
    const val Gallery         = "gallery"
    const val Camera          = "camera"
    const val Edit            = "edit"
    const val SignaturePrompt = "signature_prompt"
    const val SignatureDraw   = "signature_draw"
    const val Save            = "save"
    const val DetailPattern   = "detail/{id}"
    fun detail(id: Long): String = "detail/$id"
    const val ExportPattern   = "export/{ids}"
    fun export(ids: Collection<Long>): String = "export/${ids.joinToString(",")}"
}

@Composable
private fun NpicNavHost(
    navController: NavHostController,
    captureHolder: SharedCaptureHolder,
    editVmFactory: EditViewModel.Factory,
    studentRepository: StudentRepository,
) {
    NavHost(navController = navController, startDestination = Route.Gallery) {
        composable(Route.Gallery) {
            GalleryDestination(
                studentRepository = studentRepository,
                onCaptureClick = { navController.navigate(Route.Camera) },
                onRecordClick = { id -> navController.navigate(Route.detail(id)) },
                onExportSelection = { ids -> navController.navigate(Route.export(ids.toList())) },
            )
        }
        composable(
            route = Route.DetailPattern,
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getLong("id") ?: 0L
            DetailDestination(
                studentRepository = studentRepository,
                recordId = id,
                onBack = { navController.popBackStack() },
                onExport = { navController.navigate(Route.export(listOf(id))) },
            )
        }
        composable(
            route = Route.ExportPattern,
            arguments = listOf(navArgument("ids") { type = NavType.StringType }),
        ) { backStackEntry ->
            val raw = backStackEntry.arguments?.getString("ids").orEmpty()
            val ids = raw.split(",").mapNotNull { it.toLongOrNull() }
            ExportDestination(
                studentRepository = studentRepository,
                recordIds = ids,
                onCancel = { navController.popBackStack() },
            )
        }
        composable(Route.Camera) {
            CameraDestination(
                onBack = { navController.popBackStack() },
                onCaptureComplete = { capture ->
                    captureHolder.pushCapture(capture)
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
                onNext = { capture ->
                    // Layer 8b: the edited photo lives at capture.rawPath. Real Save-time
                    // full-res render lands with the Save-render layer; for now we forward
                    // the raw capture path so the Save sheet has something to persist.
                    captureHolder.pushPhoto(capture.rawPath, capture.mode)
                    val nextRoute = when (capture.mode) {
                        CameraMode.Photo     -> Route.SignaturePrompt
                        CameraMode.Signature -> Route.Save
                    }
                    navController.navigate(nextRoute) {
                        popUpTo(Route.Edit) { inclusive = true }
                    }
                },
            )
        }
        composable(Route.SignaturePrompt) {
            SignaturePromptSheet(
                onCapture = {
                    // TODO(camera): open Camera in Signature mode. Layer 8b navigates to
                    // Camera default (Photo) — Camera-layer follow-up threads an initial
                    // mode through the route.
                    navController.navigate(Route.Camera) {
                        popUpTo(Route.SignaturePrompt) { inclusive = true }
                    }
                },
                onDraw = {
                    navController.navigate(Route.SignatureDraw) {
                        popUpTo(Route.SignaturePrompt) { inclusive = true }
                    }
                },
                onSkip = {
                    navController.navigate(Route.Save) {
                        popUpTo(Route.SignaturePrompt) { inclusive = true }
                    }
                },
                onDismiss = { navController.popBackStack() },
            )
        }
        composable(Route.SignatureDraw) {
            SignatureDrawDestination(
                onBack = { navController.popBackStack() },
                onDone = { path ->
                    captureHolder.pushSignature(path, SignatureSource.Drawn)
                    navController.navigate(Route.Save) {
                        popUpTo(Route.SignatureDraw) { inclusive = true }
                    }
                },
            )
        }
        composable(Route.Save) {
            SaveDestination(
                captureHolder = captureHolder,
                studentRepository = studentRepository,
                onCancel = { navController.popBackStack() },
                onSaved = {
                    captureHolder.clear()
                    // PRD §4.8 "After save: The Save flow returns to Camera (Photo) so
                    // the user can keep capturing." Pop back to Gallery first, then push
                    // Camera on top so Back from Camera → Gallery works naturally.
                    navController.popBackStack(Route.Gallery, inclusive = false)
                    navController.navigate(Route.Camera)
                },
            )
        }
    }
}

@Composable
private fun GalleryDestination(
    studentRepository: StudentRepository,
    onCaptureClick: () -> Unit,
    onRecordClick: (Long) -> Unit,
    onExportSelection: (Set<Long>) -> Unit,
) {
    val context = LocalContext.current
    val factory = remember(studentRepository) { GalleryViewModel.Factory(studentRepository) }
    val viewModel: GalleryViewModel = viewModel(factory = factory)
    GalleryScreen(
        viewModel = viewModel,
        onCaptureClick = onCaptureClick,
        onRecordClick = onRecordClick,
        onExportSelection = onExportSelection,
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
private fun DetailDestination(
    studentRepository: StudentRepository,
    recordId: Long,
    onBack: () -> Unit,
    onExport: () -> Unit,
) {
    val context = LocalContext.current
    val factory = remember(studentRepository, recordId) {
        DetailViewModel.Factory(studentRepository, recordId)
    }
    val viewModel: DetailViewModel = viewModel(key = "detail-$recordId", factory = factory)
    DetailScreen(
        viewModel = viewModel,
        onBack = onBack,
        onEditPhoto = {
            Toast.makeText(context, "Edit photo (next layer)", Toast.LENGTH_SHORT).show()
        },
        onEditSignature = {
            Toast.makeText(context, "Edit signature (next layer)", Toast.LENGTH_SHORT).show()
        },
        onCapturePhoto = {
            Toast.makeText(context, "Capture photo (next layer)", Toast.LENGTH_SHORT).show()
        },
        onImportPhoto = {
            Toast.makeText(context, "Import photo (next layer)", Toast.LENGTH_SHORT).show()
        },
        onCaptureSignature = {
            Toast.makeText(context, "Capture signature (next layer)", Toast.LENGTH_SHORT).show()
        },
        onDrawSignature = {
            Toast.makeText(context, "Draw signature (next layer)", Toast.LENGTH_SHORT).show()
        },
        onImportSignature = {
            Toast.makeText(context, "Import signature (next layer)", Toast.LENGTH_SHORT).show()
        },
        onExport = { _ -> onExport() },
        onDuplicateToAnotherClass = {
            Toast.makeText(context, "Duplicate to another class (next layer)", Toast.LENGTH_SHORT).show()
        },
        onDeleted = {
            Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show()
            onBack()
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
    onNext: (com.npic.photoandsignscanner.domain.model.CameraCapture) -> Unit,
) {
    val capture by captureHolder.capture.collectAsState()

    // Guard the null case defensively: if a user process-restarts into Route.Edit (e.g.
    // via a deep link that doesn't exist yet, or a state restoration edge), pop back to
    // Gallery rather than blank-screen. TODO(repo): once DraftRepository lands, look up
    // the last-active draft here instead.
    val current = capture
    if (current == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val editViewModel: EditViewModel = viewModel(factory = editVmFactory)

    EditScreen(
        capture = current,
        onBack = onBack,
        onNext = { onNext(current) },
        viewModel = editViewModel,
    )
}

@Composable
private fun SignatureDrawDestination(
    onBack: () -> Unit,
    onDone: (signaturePath: String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cacheDir = remember(context) { File(context.cacheDir, "drafts").apply { mkdirs() } }

    SignatureDrawScreen(
        onBack = onBack,
        onDone = { result ->
            // Rasterise strokes on Dispatchers.Default and write a 1500×500 JPEG into
            // cache/drafts/. The signaturePath is then attached to the shared draft.
            scope.launch {
                val path = withContext(Dispatchers.Default) {
                    rasterizeSignatureToDisk(result, cacheDir)
                }
                if (path != null) {
                    Toast.makeText(
                        context,
                        "Signature saved (${result.strokes.size} stroke(s))",
                        Toast.LENGTH_SHORT,
                    ).show()
                    onDone(path)
                } else {
                    Toast.makeText(context, "Couldn't save signature", Toast.LENGTH_SHORT).show()
                }
            }
        },
    )
}

/**
 * Rasterises the drawn strokes to a 1500×500 JPEG in [cacheDir] and returns the file
 * path. Returns null on empty strokes (the SignatureDrawScreen guards Done against this,
 * so it's a defensive branch) or IO failure.
 *
 * Uses a fake canvas size matching the export aspect so the rasteriser's scale math
 * degenerates to 1:1. The real on-device canvas size doesn't matter here — strokes are
 * already in canvas coords and we don't have that value at Done time. TODO(pipeline):
 * pipe the actual canvas size through SignatureDrawResult so per-stroke widths scale
 * naturally from device DP → 1500-px space.
 */
private fun rasterizeSignatureToDisk(result: SignatureDrawResult, cacheDir: File): String? {
    val canvasSize = Size(SignatureRasterizer.EXPORT_WIDTH, SignatureRasterizer.EXPORT_HEIGHT)
    val bitmap: Bitmap = SignatureRasterizer.rasterize(result.strokes, canvasSize) ?: return null
    return try {
        val file = File(cacheDir, "${UUID.randomUUID()}_signature.jpg")
        FileOutputStream(file).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, stream)
        }
        file.absolutePath
    } catch (t: Throwable) {
        Log.e("MainActivity", "Failed to write signature JPEG", t)
        null
    } finally {
        bitmap.recycle()
    }
}

@Composable
private fun SaveDestination(
    captureHolder: SharedCaptureHolder,
    studentRepository: StudentRepository,
    onCancel: () -> Unit,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val draftState by captureHolder.draft.collectAsState()

    // Guard the null case: if the user lands on Save without a draft (deep link edge,
    // process death), synthesise an empty draft so the sheet renders with the "add a
    // photo or signature to save" empty state and the user can Cancel out cleanly.
    val draft = draftState ?: remember {
        StudentDraft(
            id = UUID.randomUUID().toString(),
            photoPath = null,
            signaturePath = null,
            createdAt = Clock.System.now(),
        )
    }

    val factory = remember(draft.id) {
        SaveViewModel.Factory(repo = studentRepository, draft = draft)
    }
    val saveViewModel: SaveViewModel = viewModel(key = draft.id, factory = factory)

    SaveSheet(
        viewModel = saveViewModel,
        onCancel = onCancel,
        onSaved = { recordId ->
            Toast.makeText(context, "Saved student #$recordId", Toast.LENGTH_SHORT).show()
            onSaved()
        },
    )
}

@Composable
private fun ExportDestination(
    studentRepository: StudentRepository,
    recordIds: List<Long>,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val idsKey = remember(recordIds) { recordIds.joinToString(",") }
    val factory = remember(studentRepository, idsKey) {
        ExportViewModel.Factory(studentRepository, recordIds)
    }
    val viewModel: ExportViewModel = viewModel(key = "export-$idsKey", factory = factory)

    ExportSheet(
        viewModel = viewModel,
        onCancel = onCancel,
        onShare = { paths, _ ->
            if (paths.isEmpty()) {
                Toast.makeText(context, "Nothing to export", Toast.LENGTH_SHORT).show()
            } else if (paths.size == 1) {
                FileShareLauncher.shareSingle(context, paths.first(), "Export via UPMSP")
            } else {
                FileShareLauncher.shareMulti(context, paths, "Export via UPMSP")
            }
            onCancel()
        },
    )
}
