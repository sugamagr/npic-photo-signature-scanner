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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.npic.photoandsignscanner.core.theme.NpicTheme
import com.npic.photoandsignscanner.data.export.ZipExporter
import com.npic.photoandsignscanner.data.imaging.BitmapAdjustments
import com.npic.photoandsignscanner.data.imaging.BitmapFilters
import com.npic.photoandsignscanner.data.imaging.CombinedRenderer
import com.npic.photoandsignscanner.data.imaging.EditRenderer
import com.npic.photoandsignscanner.data.imaging.JpegCompressor
import com.npic.photoandsignscanner.data.imaging.OpenCvBridge
import com.npic.photoandsignscanner.data.imaging.PhotoEdgeDetector
import com.npic.photoandsignscanner.data.imaging.SignatureInkIsolator
import com.npic.photoandsignscanner.data.db.NpicDatabase
import com.npic.photoandsignscanner.data.repo.RoomDraftRepository
import com.npic.photoandsignscanner.data.repo.RoomStudentRepository
import com.npic.photoandsignscanner.domain.repo.DraftRepository
import com.npic.photoandsignscanner.data.storage.SourceStore
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
import com.npic.photoandsignscanner.features.search.SearchScreen
import com.npic.photoandsignscanner.features.search.SearchViewModel
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
    private val editRenderer by lazy { EditRenderer(openCvBridge, bitmapFilters, bitmapAdjustments) }

    // Layer 9 Save-render + Export-render graph (PRD §5.5 / §6 / §6.2 / §4.10). Kept
    // Activity-scoped so a single SourceStore instance owns the `filesDir/sources/`
    // directory across screen transitions.
    private val sourceStore by lazy { SourceStore(filesDir) }
    private val jpegCompressor by lazy { JpegCompressor() }
    private val combinedRenderer by lazy { CombinedRenderer() }
    private val zipExporter by lazy { ZipExporter(cacheDir) }

    // Room-backed persistence (PRD §8.1 + §8.3). One NpicDatabase per Activity — Room
    // internally serialises writes on its executor and shares a single connection pool,
    // so a lazy singleton here is the composition root for both repositories.
    private val roomDb by lazy { NpicDatabase.create(this) }
    private val studentRepository: StudentRepository by lazy { RoomStudentRepository(roomDb.studentDao()) }
    private val draftRepository: DraftRepository by lazy { RoomDraftRepository(roomDb.draftDao()) }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        splash.setKeepOnScreenCondition { false }

        setContent {
            NpicTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color    = MaterialTheme.colorScheme.background,
                ) {
                    // SharedCaptureHolder now takes a DraftRepository (PRD §8.3). The
                    // Factory closes over the Activity-scoped repo so every destination
                    // that requests `viewModel<SharedCaptureHolder>()` receives the same
                    // repo-backed instance and drafts survive process death.
                    val sharedCaptureFactory = remember(draftRepository) {
                        SharedCaptureHolder.Factory(draftRepository)
                    }
                    val captureHolder: SharedCaptureHolder = viewModel(factory = sharedCaptureFactory)
                    // EditViewModel.Factory captures a live draftIdProvider that reads
                    // the current or freshly-minted draft ID out of the captureHolder.
                    // Doing it in the Factory instead of a constructor param means every
                    // new EditViewModel binding sees the current draft — critical when
                    // the user re-enters Edit for a follow-up capture in the same session.
                    val editVmFactory = remember(captureHolder) {
                        EditViewModel.Factory(
                            detectPhotoEdges = photoEdgeDetector,
                            detectSignatureInk = signatureInkIsolator,
                            bitmapFilters = bitmapFilters,
                            bitmapAdjustments = bitmapAdjustments,
                            editRenderer = editRenderer,
                            sourceStore = sourceStore,
                            draftIdProvider = { captureHolder.draftIdOrMint() },
                        )
                    }
                    NpicNavHost(
                        navController = rememberNavController(),
                        captureHolder = captureHolder,
                        editVmFactory = editVmFactory,
                        studentRepository = studentRepository,
                        sourceStore = sourceStore,
                        jpegCompressor = jpegCompressor,
                        combinedRenderer = combinedRenderer,
                        zipExporter = zipExporter,
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
    const val Search          = "search"
    const val DetailPattern   = "detail/{id}"
    fun detail(id: String): String = "detail/$id"
    const val ExportPattern   = "export/{ids}"
    // UUIDs are RFC 4122 hex-with-hyphens — URL-safe in a path segment without escaping,
    // and safe to comma-join in a single segment because they never contain commas.
    fun export(ids: Collection<String>): String = "export/${ids.joinToString(",")}"
}

@Composable
private fun NpicNavHost(
    navController: NavHostController,
    captureHolder: SharedCaptureHolder,
    editVmFactory: EditViewModel.Factory,
    studentRepository: StudentRepository,
    sourceStore: SourceStore,
    jpegCompressor: JpegCompressor,
    combinedRenderer: CombinedRenderer,
    zipExporter: ZipExporter,
) {
    NavHost(navController = navController, startDestination = Route.Gallery) {
        composable(Route.Gallery) {
            GalleryDestination(
                studentRepository = studentRepository,
                captureHolder = captureHolder,
                onCaptureClick = { navController.navigate(Route.Camera) },
                onRecordClick = { id -> navController.navigate(Route.detail(id)) },
                onExportSelection = { ids -> navController.navigate(Route.export(ids.toList())) },
                onSearchClick = { navController.navigate(Route.Search) },
                onResumeDraft = { draft ->
                    // PRD §8.3: route the user back to the earliest missing-media step so
                    // they finish where they left off. Photo first, then signature.
                    when {
                        draft.photoPath == null -> navController.navigate(Route.Camera)
                        draft.signaturePath == null -> navController.navigate(Route.SignaturePrompt)
                        else -> navController.navigate(Route.Save)
                    }
                },
            )
        }
        composable(
            route = Route.DetailPattern,
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id").orEmpty()
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
            val ids = raw.split(",").filter { it.isNotBlank() }
            ExportDestination(
                studentRepository = studentRepository,
                recordIds = ids,
                sourceStore = sourceStore,
                jpegCompressor = jpegCompressor,
                combinedRenderer = combinedRenderer,
                zipExporter = zipExporter,
                onCancel = { navController.popBackStack() },
            )
        }
        composable(Route.Camera) {
            CameraDestination(
                onBack = { navController.popBackStack() },
                onCaptureComplete = { capture ->
                    captureHolder.pushCapture(capture)
                    navController.navigate(Route.Edit)
                },
                onDrawInsteadClick = { navController.navigate(Route.SignatureDraw) },
            )
        }
        composable(Route.Edit) {
            EditDestination(
                captureHolder = captureHolder,
                editVmFactory = editVmFactory,
                onBack = { navController.popBackStack() },
                onNext = { sourcePath, mode ->
                    when (mode) {
                        CameraMode.Photo -> {
                            captureHolder.pushPhoto(sourcePath, mode)
                            navController.navigate(Route.SignaturePrompt)
                        }
                        CameraMode.Signature -> {
                            captureHolder.pushSignaturePath(sourcePath)
                            navController.navigate(Route.Save)
                        }
                    }
                },
            )
        }
        composable(Route.SignaturePrompt) {
            SignaturePromptSheet(
                onCapture = { navController.navigate(Route.Camera) },
                onDraw = { navController.navigate(Route.SignatureDraw) },
                onSkip = { navController.navigate(Route.Save) },
                onDismiss = { navController.popBackStack() },
            )
        }
        composable(Route.SignatureDraw) {
            SignatureDrawDestination(
                sourceStore = sourceStore,
                draftIdProvider = { captureHolder.draftIdOrMint() },
                onBack = { navController.popBackStack() },
                onDone = { path ->
                    captureHolder.pushSignature(path, SignatureSource.Drawn)
                    navController.navigate(Route.Save)
                },
            )
        }
        composable(Route.Search) {
            val factory = remember(studentRepository) {
                SearchViewModel.Factory(studentRepository)
            }
            val vm: SearchViewModel = viewModel(factory = factory)
            SearchScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onRecordClick = { id ->
                    navController.popBackStack()
                    navController.navigate(Route.detail(id))
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
    captureHolder: SharedCaptureHolder,
    onCaptureClick: () -> Unit,
    onRecordClick: (String) -> Unit,
    onExportSelection: (Set<String>) -> Unit,
    onResumeDraft: (StudentDraft) -> Unit,
    onSearchClick: () -> Unit,
) {
    val context = LocalContext.current
    val factory = remember(studentRepository) { GalleryViewModel.Factory(studentRepository) }
    val viewModel: GalleryViewModel = viewModel(factory = factory)

    // PRD §8.3 resume-prompt. Only fires once per Gallery mount and only for the initial
    // warm-start draft — subsequent in-session draft mutations should NOT re-prompt.
    val draft by captureHolder.draft.collectAsStateWithLifecycle()
    var promptShown by rememberSaveable { mutableStateOf(false) }
    val activeDraft = draft
    val shouldPrompt = !promptShown && activeDraft != null && activeDraft.hasAnyMedia

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
        onSearchClick = onSearchClick,
    )

    if (shouldPrompt) {
        ResumeDraftDialog(
            draft = activeDraft,
            onResume = {
                promptShown = true
                onResumeDraft(activeDraft)
            },
            onDiscard = {
                promptShown = true
                captureHolder.clear()
            },
        )
    }
}

@Composable
private fun ResumeDraftDialog(
    draft: StudentDraft,
    onResume: () -> Unit,
    onDiscard: () -> Unit,
) {
    val summary = buildString {
        if (draft.photoPath != null) append("photo")
        if (draft.photoPath != null && draft.signaturePath != null) append(" + ")
        if (draft.signaturePath != null) append("signature")
    }.ifEmpty { "capture" }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDiscard,
        title = { androidx.compose.material3.Text("Resume capture in progress?") },
        text = { androidx.compose.material3.Text("You have an unsaved $summary. Continue where you left off, or discard it?") },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onResume) {
                androidx.compose.material3.Text("Resume")
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDiscard) {
                androidx.compose.material3.Text("Discard")
            }
        },
    )
}

@Composable
private fun DetailDestination(
    studentRepository: StudentRepository,
    recordId: String,
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
    onNext: (sourcePath: String, mode: CameraMode) -> Unit,
) {
    val capture by captureHolder.capture.collectAsStateWithLifecycle()

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
        onNext = { sourcePath -> onNext(sourcePath, current.mode) },
        viewModel = editViewModel,
    )
}

@Composable
private fun SignatureDrawDestination(
    sourceStore: SourceStore,
    draftIdProvider: () -> String,
    onBack: () -> Unit,
    onDone: (signaturePath: String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    SignatureDrawScreen(
        onBack = onBack,
        onDone = { result ->
            // PRD §5.5.2 / §6.0.2: rasterise strokes to 1500×500 and persist through
            // SourceStore as `sources/{draftId}_signature.jpg` — the canonical committed
            // asset. Landing straight in sources/ (not cache/drafts/) means Export can
            // decode from there without a second copy step.
            scope.launch {
                val path = withContext(Dispatchers.Default) {
                    persistDrawnSignature(result, sourceStore, draftIdProvider())
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
 * Rasterise [result.strokes] to a 1500×500 bitmap and hand it to [SourceStore] for
 * canonical persistence at `sources/{draftId}_signature.jpg`. Returns the absolute path
 * on success, null on empty strokes (SignatureDrawScreen guards Done against this, so
 * it's a defensive branch) or IO failure.
 *
 * Note the canvas size passed to the rasteriser matches the export dimensions — strokes
 * are already in canvas-space and we don't have the actual on-device canvas size at Done
 * time. TODO(pipeline): pipe the real canvas size through SignatureDrawResult so per-
 * stroke widths scale naturally from device DP → 1500-px space.
 */
private suspend fun persistDrawnSignature(
    result: SignatureDrawResult,
    sourceStore: SourceStore,
    draftId: String,
): String? {
    val canvasSize = Size(SignatureRasterizer.EXPORT_WIDTH, SignatureRasterizer.EXPORT_HEIGHT)
    val bitmap: Bitmap = SignatureRasterizer.rasterize(result.strokes, canvasSize) ?: return null
    return try {
        sourceStore.writeSignature(draftId, bitmap)
    } catch (t: Throwable) {
        Log.e("MainActivity", "Failed to persist drawn signature", t)
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
    val draftState by captureHolder.draft.collectAsStateWithLifecycle()

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
    recordIds: List<String>,
    sourceStore: SourceStore,
    jpegCompressor: JpegCompressor,
    combinedRenderer: CombinedRenderer,
    zipExporter: ZipExporter,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val idsKey = remember(recordIds) { recordIds.joinToString(",") }
    val factory = remember(studentRepository, idsKey) {
        ExportViewModel.Factory(
            repository = studentRepository,
            recordIds = recordIds,
            sourceStore = sourceStore,
            jpegCompressor = jpegCompressor,
            combinedRenderer = combinedRenderer,
            zipExporter = zipExporter,
            cacheDir = context.cacheDir,
        )
    }
    val viewModel: ExportViewModel = viewModel(key = "export-$idsKey", factory = factory)

    ExportSheet(
        viewModel = viewModel,
        onCancel = onCancel,
        onShare = { result, _ ->
            when (result) {
                is com.npic.photoandsignscanner.features.export.ExportResult.Ready.Single -> {
                    if (result.underMinCount > 0) {
                        Toast.makeText(
                            context,
                            "Export saved but is smaller than the portal minimum — the UPMSP portal may reject it.",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                    FileShareLauncher.shareSingle(
                        context = context,
                        filePath = result.path,
                        chooserTitle = "Export via UPMSP",
                        mimeType = FileShareLauncher.MIME_JPEG,
                    )
                }
                is com.npic.photoandsignscanner.features.export.ExportResult.Ready.Zip -> {
                    if (result.underMinCount > 0) {
                        Toast.makeText(
                            context,
                            "${result.underMinCount} of ${result.entryCount} items fell below portal minimum size — the UPMSP portal may reject them.",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                    FileShareLauncher.shareSingle(
                        context = context,
                        filePath = result.path,
                        chooserTitle = "Export via UPMSP",
                        mimeType = FileShareLauncher.MIME_ZIP,
                    )
                }
                com.npic.photoandsignscanner.features.export.ExportResult.Failed ->
                    Toast.makeText(context, "Couldn't prepare the export", Toast.LENGTH_SHORT).show()
            }
            onCancel()
        },
    )
}
