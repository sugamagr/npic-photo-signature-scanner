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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
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
import com.npic.photoandsignscanner.BuildConfig
import com.npic.photoandsignscanner.core.theme.NpicTheme
import com.npic.photoandsignscanner.data.export.ZipExporter
import com.npic.photoandsignscanner.data.settings.AppSettingsRepository
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
import com.npic.photoandsignscanner.domain.model.ClassNum
import com.npic.photoandsignscanner.domain.model.SignatureSource
import com.npic.photoandsignscanner.domain.model.StudentDraft
import com.npic.photoandsignscanner.domain.model.StudentRecord
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
import com.npic.photoandsignscanner.features.settings.SettingsDrawer
import com.npic.photoandsignscanner.features.settings.SettingsViewModel
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
    private val mediaStoreExporter by lazy {
        com.npic.photoandsignscanner.data.export.MediaStoreExporter(contentResolver)
    }

    // Room-backed persistence (PRD §8.1 + §8.3). One NpicDatabase per Activity — Room
    // internally serialises writes on its executor and shares a single connection pool,
    // so a lazy singleton here is the composition root for both repositories.
    private val roomDb by lazy { NpicDatabase.create(this) }
    private val studentRepository: StudentRepository by lazy { RoomStudentRepository(roomDb.studentDao()) }
    private val draftRepository: DraftRepository by lazy { RoomDraftRepository(roomDb.draftDao()) }

    // Settings drawer prefs (user m1551 S3). DataStore is process-scoped so we hand it
    // the applicationContext; keeping it as a lazy here (not a global object) means the
    // repository dies with the Activity and Espresso tests can spin a fresh instance.
    private val appSettingsRepository by lazy { AppSettingsRepository(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        splash.setKeepOnScreenCondition { false }

        setContent {
            NpicTheme(settingsFlow = appSettingsRepository.settings) {
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
                    val settingsVmFactory = remember(appSettingsRepository, roomDb, sourceStore) {
                        SettingsViewModel.Factory(
                            settingsRepository = appSettingsRepository,
                            database = roomDb,
                            sourceStore = sourceStore,
                            cacheDir = cacheDir,
                        )
                    }
                    val settingsVm: SettingsViewModel = viewModel(factory = settingsVmFactory)
                    val drawerState = rememberDrawerState(DrawerValue.Closed)
                    val drawerScope = rememberCoroutineScope()
                    val hostContext = LocalContext.current
                    ModalNavigationDrawer(
                        drawerState = drawerState,
                        drawerContent = {
                            SettingsDrawer(
                                viewModel     = settingsVm,
                                appVersion    = BuildConfig.VERSION_NAME,
                                onDismiss     = { drawerScope.launch { drawerState.close() } },
                                onClearAllDone = { success ->
                                    Toast.makeText(
                                        hostContext,
                                        if (success) "All data cleared" else "Clear failed — try again",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                },
                            )
                        },
                    ) {
                        NpicNavHost(
                            navController = rememberNavController(),
                            captureHolder = captureHolder,
                            editVmFactory = editVmFactory,
                            studentRepository = studentRepository,
                            sourceStore = sourceStore,
                            jpegCompressor = jpegCompressor,
                            combinedRenderer = combinedRenderer,
                            zipExporter = zipExporter,
                            mediaStoreExporter = mediaStoreExporter,
                            onOpenSettings = { drawerScope.launch { drawerState.open() } },
                        )
                    }
                }
            }
        }
    }
}

private object Route {
    const val Gallery         = "gallery"
    // Layer 12: Camera now accepts an optional `?mode=Photo|Signature` query so
    // SignaturePromptSheet's Capture branch can land the user directly in Signature mode
    // (was previously stuck on the Photo default). Nav-arg is a String because
    // NavType.EnumType isn't stable across the compose-nav versions we're on.
    const val CameraPattern   = "camera?mode={mode}"
    const val Camera          = "camera"
    fun camera(mode: CameraMode): String = "camera?mode=${mode.name}"
    const val Edit            = "edit"
    const val SignaturePrompt = "signature_prompt"
    const val SignatureDraw   = "signature_draw"
    // Save accepts an optional preselected class for the duplicate-to-another-class flow
    // (user m1555). Empty query arg means "no preselection" — the sheet opens with class
    // picker un-picked, same as the standard Camera → Edit → Save arc.
    const val SavePattern     = "save?classNum={classNum}"
    const val Save            = "save"
    fun save(preselect: ClassNum? = null): String =
        if (preselect == null) "save" else "save?classNum=${preselect.name}"
    // m2232: in-place update sheet for the Detail add/edit-media flows. Reads its
    // draft + target from SharedCaptureHolder; no args needed on the route itself.
    const val UpdateConfirm   = "update_confirm"
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
    mediaStoreExporter: com.npic.photoandsignscanner.data.export.MediaStoreExporter,
    onOpenSettings: () -> Unit,
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
                onOpenSettings = onOpenSettings,
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
                captureHolder = captureHolder,
                sourceStore = sourceStore,
                recordId = id,
                onBack = { navController.popBackStack() },
                onExport = { navController.navigate(Route.export(listOf(id))) },
                onNavigateToEdit = { navController.navigate(Route.Edit) },
                onNavigateToCamera = { mode -> navController.navigate(Route.camera(mode)) },
                onNavigateToDraw = { navController.navigate(Route.SignatureDraw) },
                onNavigateToSaveWithClass = { target -> navController.navigate(Route.save(target)) },
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
                mediaStoreExporter = mediaStoreExporter,
                onCancel = { navController.popBackStack() },
            )
        }
        composable(
            route = Route.CameraPattern,
            arguments = listOf(
                navArgument("mode") {
                    type = NavType.StringType
                    defaultValue = CameraMode.Photo.name
                },
            ),
        ) { backStackEntry ->
            val modeName = backStackEntry.arguments?.getString("mode") ?: CameraMode.Photo.name
            val initialMode = runCatching { CameraMode.valueOf(modeName) }.getOrDefault(CameraMode.Photo)
            CameraDestination(
                initialMode = initialMode,
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
                            // m2232: existing record → skip SignaturePrompt (identity is
                            // locked, media list is already known) and land on
                            // UpdateConfirmSheet. Fresh flow → prompt for a signature.
                            if (captureHolder.target.value != null) {
                                navController.navigate(Route.UpdateConfirm)
                            } else {
                                navController.navigate(Route.SignaturePrompt)
                            }
                        }
                        CameraMode.Signature -> {
                            captureHolder.pushSignaturePath(sourcePath)
                            if (captureHolder.target.value != null) {
                                navController.navigate(Route.UpdateConfirm)
                            } else {
                                navController.navigate(Route.Save)
                            }
                        }
                    }
                },
            )
        }
        composable(Route.SignaturePrompt) {
            SignaturePromptSheet(
                // Layer 12 fix: route to Camera in Signature mode so the guide-box is 3:1
                // and the shutter routes captures to the ink-isolation pipeline. Was
                // previously landing on the Photo default — user then had to tap the
                // Signature mode pill manually, defeating the prompt.
                onCapture = { navController.navigate(Route.camera(CameraMode.Signature)) },
                onDraw = { navController.navigate(Route.SignatureDraw) },
                onSkip = {
                    // m2232: existing record → UpdateConfirm; fresh flow → Save.
                    if (captureHolder.target.value != null) {
                        navController.navigate(Route.UpdateConfirm)
                    } else {
                        navController.navigate(Route.Save)
                    }
                },
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
                    // m2232: existing record → UpdateConfirm; fresh flow → Save.
                    if (captureHolder.target.value != null) {
                        navController.navigate(Route.UpdateConfirm)
                    } else {
                        navController.navigate(Route.Save)
                    }
                },
            )
        }
        composable(Route.UpdateConfirm) {
            UpdateConfirmDestination(
                captureHolder = captureHolder,
                studentRepository = studentRepository,
                sourceStore = sourceStore,
                onCancel = { navController.popBackStack() },
                onUpdated = { recordId ->
                    captureHolder.clear()
                    navController.popBackStack(Route.Gallery, inclusive = false)
                    navController.navigate(Route.detail(recordId))
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
        composable(
            route = Route.SavePattern,
            arguments = listOf(
                navArgument("classNum") {
                    type = NavType.StringType
                    defaultValue = ""
                    nullable = false
                },
            ),
        ) { backStackEntry ->
            val preselectName = backStackEntry.arguments?.getString("classNum").orEmpty()
            val preselectClass = runCatching { ClassNum.valueOf(preselectName) }.getOrNull()
            SaveDestination(
                captureHolder = captureHolder,
                studentRepository = studentRepository,
                sourceStore = sourceStore,
                preselectedClass = preselectClass,
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
    onOpenSettings: () -> Unit,
) {
    val factory = remember(studentRepository) { GalleryViewModel.Factory(studentRepository) }
    val viewModel: GalleryViewModel = viewModel(factory = factory)

    // PRD §8.3 resume-prompt. Only fires once per Gallery mount and only for the initial
    // warm-start draft — subsequent in-session draft mutations should NOT re-prompt.
    val draft by captureHolder.draft.collectAsStateWithLifecycle()
    var promptShown by rememberSaveable { mutableStateOf(false) }
    val activeDraft = draft
    val shouldPrompt = !promptShown && activeDraft != null && activeDraft.hasAnyMedia

    // Layer 12 + m1551 S3 restructure: destructive-action AlertDialogs live at the
    // destination level (not in GalleryScreen) so GalleryScreen stays presentation-only.
    // Overflow itself is now an anchored DropdownMenu inside GalleryTopBar; only the
    // two confirm dialogs it can raise still live here.
    // Haptics hoisted here so BOTH confirm dialogs (single + delete-all) share one
    // instance — matches m1551 S3 spec ("long-press feedback for destructive confirms")
    // and NpicHaptics.performLongPress no-ops when the user's toggle is off.
    val haptics = com.npic.photoandsignscanner.core.theme.rememberNpicHaptics()
    var pendingDelete by remember { mutableStateOf<Set<String>?>(null) }
    var pendingDeleteAll by remember { mutableStateOf(false) }

    GalleryScreen(
        viewModel          = viewModel,
        onCaptureClick     = onCaptureClick,
        onRecordClick      = onRecordClick,
        onExportSelection  = onExportSelection,
        onDeleteSelection  = { ids -> if (ids.isNotEmpty()) pendingDelete = ids },
        onRequestDeleteAll = { pendingDeleteAll = true },
        onSearchClick      = onSearchClick,
        onOpenSettings     = onOpenSettings,
    )

    val currentDelete = pendingDelete
    if (currentDelete != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { androidx.compose.material3.Text("Delete ${currentDelete.size} record${if (currentDelete.size == 1) "" else "s"}?") },
            text = { androidx.compose.material3.Text("This cannot be undone.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    haptics.performLongPress()
                    viewModel.deleteSelection(currentDelete)
                    pendingDelete = null
                }) { androidx.compose.material3.Text("Delete", color = com.npic.photoandsignscanner.core.theme.NpicColors.Terracotta) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { pendingDelete = null }) {
                    androidx.compose.material3.Text("Keep")
                }
            },
        )
    }

    if (pendingDeleteAll) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingDeleteAll = false },
            title = { androidx.compose.material3.Text("Delete all records?") },
            text = { androidx.compose.material3.Text("Every student record on this device will be removed. This cannot be undone.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    haptics.performLongPress()
                    val everyId = viewModel.state.value.records.map { it.id }.toSet()
                    viewModel.deleteSelection(everyId)
                    pendingDeleteAll = false
                }) { androidx.compose.material3.Text("Delete all", color = com.npic.photoandsignscanner.core.theme.NpicColors.Terracotta) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { pendingDeleteAll = false }) {
                    androidx.compose.material3.Text("Cancel")
                }
            },
        )
    }

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
    captureHolder: SharedCaptureHolder,
    sourceStore: com.npic.photoandsignscanner.data.storage.SourceStore,
    recordId: String,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onNavigateToEdit: () -> Unit,
    onNavigateToCamera: (CameraMode) -> Unit,
    onNavigateToDraw: () -> Unit,
    onNavigateToSaveWithClass: (ClassNum) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val factory = remember(studentRepository, recordId) {
        DetailViewModel.Factory(studentRepository, recordId)
    }
    val viewModel: DetailViewModel = viewModel(key = "detail-$recordId", factory = factory)
    val duplicateUseCase = remember(sourceStore) {
        com.npic.photoandsignscanner.data.repo.DuplicateAssetsUseCase(sourceStore)
    }

    // Layer 12: system photo picker for Import Photo / Import Signature. On Uri return
    // we copy the SAF-only stream into a cache/drafts/ file so downstream reads use the
    // same File-based rawPath contract as CameraX. Uri lifetime past the picker is
    // undefined on some OEMs — cheaper to snapshot the bytes than to fight persist
    // permission grants.
    // m2232: pendingImportTarget carries the record whose media we're replacing.
    // Signals to the launcher callback that this import lands on repo.replace() via
    // beginUpdate(target) — NOT on a fresh Save sheet with editable identity fields.
    var pendingImportMode by remember { mutableStateOf<CameraMode?>(null) }
    var pendingImportTarget by remember { mutableStateOf<StudentRecord?>(null) }
    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
    ) { uri: android.net.Uri? ->
        val mode = pendingImportMode ?: return@rememberLauncherForActivityResult
        val target = pendingImportTarget
        pendingImportMode = null
        pendingImportTarget = null
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val cachedPath = withContext(Dispatchers.IO) {
                copyUriToDraftsCache(context, uri)
            } ?: return@launch
            // m2232 target-branch: existing record → beginUpdate reuses record.id as
            // the draft id (so SourceStore assets overwrite in place) and stamps target
            // so downstream nav lands on UpdateConfirmSheet instead of Save.
            if (target != null) {
                captureHolder.beginUpdate(target)
            } else {
                captureHolder.clear()
            }
            captureHolder.pushCapture(
                com.npic.photoandsignscanner.domain.model.CameraCapture(
                    rawPath = cachedPath,
                    mode = mode,
                    guideBoxImageSpace = null,
                    capturedAt = Clock.System.now(),
                )
            )
            onNavigateToEdit()
        }
    }

    DetailScreen(
        viewModel = viewModel,
        onBack = onBack,
        onEditPhoto = { record ->
            // m2232: re-edit uses beginUpdate(record) so Edit's Next commit routes to
            // UpdateConfirmSheet + repo.replace(), preserving record identity. The
            // SourceStore asset is overwritten in place because beginUpdate reuses
            // record.id as the draft id.
            if (record.photoPath.isBlank()) {
                Toast.makeText(context, "No photo to edit", Toast.LENGTH_SHORT).show()
                return@DetailScreen
            }
            captureHolder.beginUpdate(record)
            captureHolder.pushCapture(
                com.npic.photoandsignscanner.domain.model.CameraCapture(
                    rawPath = record.photoPath,
                    mode = CameraMode.Photo,
                    guideBoxImageSpace = null,
                    capturedAt = Clock.System.now(),
                )
            )
            onNavigateToEdit()
        },
        onEditSignature = { record ->
            val existing = record.signaturePath
            if (existing.isNullOrBlank()) {
                Toast.makeText(context, "No signature to edit", Toast.LENGTH_SHORT).show()
                return@DetailScreen
            }
            captureHolder.beginUpdate(record)
            captureHolder.pushCapture(
                com.npic.photoandsignscanner.domain.model.CameraCapture(
                    rawPath = existing,
                    mode = CameraMode.Signature,
                    guideBoxImageSpace = null,
                    capturedAt = Clock.System.now(),
                )
            )
            onNavigateToEdit()
        },
        onCapturePhoto = { record ->
            captureHolder.beginUpdate(record)
            onNavigateToCamera(CameraMode.Photo)
        },
        onImportPhoto = { record ->
            pendingImportMode = CameraMode.Photo
            pendingImportTarget = record
            importLauncher.launch(
                androidx.activity.result.PickVisualMediaRequest(
                    androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                )
            )
        },
        onCaptureSignature = { record ->
            captureHolder.beginUpdate(record)
            onNavigateToCamera(CameraMode.Signature)
        },
        onDrawSignature = { record ->
            captureHolder.beginUpdate(record)
            onNavigateToDraw()
        },
        onImportSignature = { record ->
            pendingImportMode = CameraMode.Signature
            pendingImportTarget = record
            importLauncher.launch(
                androidx.activity.result.PickVisualMediaRequest(
                    androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                )
            )
        },
        onExport = { _ -> onExport() },
        onDuplicateToAnotherClass = { record, targetClass ->
            // User m1555 workflow B: copy source assets to a fresh draft keyed by new UUID,
            // seed the captureHolder, then reuse the standard Save sheet — its 4-digit
            // Serial validation + Name mode + duplicate check all apply unchanged. Class
            // is pre-selected via the SavePattern query arg so the user only picks
            // Serial/Name and value.
            scope.launch {
                val newDraft = duplicateUseCase.invoke(record)
                if (newDraft == null) {
                    Toast.makeText(context, "Couldn't duplicate — file copy failed", Toast.LENGTH_LONG).show()
                    return@launch
                }
                captureHolder.replaceDraft(newDraft)
                onNavigateToSaveWithClass(targetClass)
            }
        },
        onDeleted = {
            Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show()
            onBack()
        },
    )
}

/**
 * Copy a SAF Uri into `cache/drafts/{uuid}.jpg` so downstream Edit / Save code can rely on
 * a File-based path. Returns null if the source can't be opened. Runs on the IO dispatcher.
 */
private fun copyUriToDraftsCache(context: android.content.Context, uri: android.net.Uri): String? {
    val dir = File(context.cacheDir, "drafts").apply { mkdirs() }
    val out = File(dir, "${UUID.randomUUID()}.jpg")
    return try {
        context.contentResolver.openInputStream(uri).use { input ->
            if (input == null) return null
            FileOutputStream(out).use { output -> input.copyTo(output) }
        }
        out.absolutePath
    } catch (t: Throwable) {
        Log.w("Detail.import", "Failed to copy Uri $uri: ${t.message}")
        null
    }
}

@Composable
private fun CameraDestination(
    initialMode: CameraMode,
    onBack: () -> Unit,
    onCaptureComplete: (com.npic.photoandsignscanner.domain.model.CameraCapture) -> Unit,
    onDrawInsteadClick: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val viewModel: com.npic.photoandsignscanner.features.camera.CameraViewModel = viewModel()
    // Layer 12: honor the nav-arg by seeding the mode ONCE per destination entry.
    // Keyed on initialMode so a follow-on navigation with a different mode also re-seeds;
    // NOT on Unit because that would prevent re-entry after the user manually flipped modes.
    LaunchedEffect(initialMode) { viewModel.setMode(initialMode) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    // m1551 S1: Camera "Import from Photos" respects the CURRENT mode (Photo vs
    // Signature) at the moment the user taps import — NOT the initialMode nav-arg,
    // which is only the seed. Reads state.mode at callback time so a user who
    // switches pills before tapping import still lands on the intended pipeline.
    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val activeMode = state.mode
        scope.launch {
            val path = withContext(Dispatchers.IO) { copyUriToDraftsCache(context, uri) }
            if (path == null) {
                Toast.makeText(context, "Couldn't import that photo", Toast.LENGTH_SHORT).show()
                return@launch
            }
            // guideBoxImageSpace = null routes EditRenderer through its normalized-quad
            // sentinel remap (DEFERRED-DECISIONS A26) so the imported source shows in
            // full on Edit's viewport, matching the "no auto-detect for imports" contract
            // of PRD §4.9's Import CTA.
            onCaptureComplete(
                com.npic.photoandsignscanner.domain.model.CameraCapture(
                    rawPath = path,
                    mode = activeMode,
                    guideBoxImageSpace = null,
                    capturedAt = Clock.System.now(),
                )
            )
        }
    }

    CameraScreen(
        onBack = onBack,
        onCaptureComplete = onCaptureComplete,
        onDrawInsteadClick = onDrawInsteadClick,
        onImportFromGalleryClick = {
            importLauncher.launch(
                androidx.activity.result.PickVisualMediaRequest(
                    androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                )
            )
        },
        viewModel = viewModel,
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
private fun UpdateConfirmDestination(
    captureHolder: SharedCaptureHolder,
    studentRepository: StudentRepository,
    sourceStore: com.npic.photoandsignscanner.data.storage.SourceStore,
    onCancel: () -> Unit,
    onUpdated: (recordId: String) -> Unit,
) {
    val context = LocalContext.current
    val draftState by captureHolder.draft.collectAsStateWithLifecycle()
    val targetState by captureHolder.target.collectAsStateWithLifecycle()
    val draft = draftState
    val target = targetState

    // m2232: destination guards against process-restart into UpdateConfirm without a
    // draft or target. Pop back rather than crash — user retries the flow from Detail.
    if (draft == null || target == null) {
        LaunchedEffect(Unit) { onCancel() }
        return
    }

    val factory = remember(draft.id, target.id, sourceStore) {
        com.npic.photoandsignscanner.features.save.UpdateConfirmViewModel.Factory(
            repo = studentRepository,
            draft = draft,
            target = target,
            onDiscardDraftAssets = { draftId -> sourceStore.deleteFor(draftId) },
        )
    }
    val vmKey = "update-${draft.id}-${target.id}"
    val vm: com.npic.photoandsignscanner.features.save.UpdateConfirmViewModel =
        viewModel(key = vmKey, factory = factory)

    com.npic.photoandsignscanner.features.save.UpdateConfirmSheet(
        viewModel = vm,
        onCancel = onCancel,
        onUpdated = { onUpdated(target.id) },
    )
}

@Composable
private fun SaveDestination(
    captureHolder: SharedCaptureHolder,
    studentRepository: StudentRepository,
    sourceStore: com.npic.photoandsignscanner.data.storage.SourceStore,
    preselectedClass: ClassNum?,
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

    val factory = remember(draft.id, sourceStore, preselectedClass) {
        SaveViewModel.Factory(
            repo = studentRepository,
            draft = draft,
            // Oracle O1-11: "Keep existing" abandons the just-committed draft. Layer 9
            // SourceStore wrote sources/{draftId}_*.jpg at Edit's commit; without this
            // callback those files are orphaned in filesDir/sources/ forever.
            onDiscardDraftAssets = { draftId -> sourceStore.deleteFor(draftId) },
            preselectedClass = preselectedClass,
        )
    }
    val vmKey = if (preselectedClass == null) draft.id else "${draft.id}-${preselectedClass.name}"
    val saveViewModel: SaveViewModel = viewModel(key = vmKey, factory = factory)

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
    mediaStoreExporter: com.npic.photoandsignscanner.data.export.MediaStoreExporter,
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
            mediaStoreExporter = mediaStoreExporter,
            cacheDir = context.cacheDir,
        )
    }
    val viewModel: ExportViewModel = viewModel(key = "export-$idsKey", factory = factory)
    val appSettings = com.npic.photoandsignscanner.core.theme.LocalAppSettings.current

    ExportSheet(
        viewModel = viewModel,
        onCancel = onCancel,
        onShare = { result, _ ->
            handleExportResult(context, result, appSettings.exportMimePreference)
            onCancel()
        },
    )
}

private fun handleExportResult(
    context: android.content.Context,
    result: com.npic.photoandsignscanner.features.export.ExportResult,
    mimePref: com.npic.photoandsignscanner.domain.model.ExportMime,
) {
    // Export MIME preference (user m1551 S3): `Auto` lets each blob speak its true
    // MIME (image/jpeg for photos, application/zip for bundles) so the system chooser
    // shows every compatible receiver. `JpegOnly` forces image/jpeg for both, useful
    // when the target app (some school-portal uploaders) only exposes an image intent
    // filter and rejects application/zip.
    val jpegMime = FileShareLauncher.MIME_JPEG
    val zipMime = if (mimePref == com.npic.photoandsignscanner.domain.model.ExportMime.JpegOnly)
        jpegMime else FileShareLauncher.MIME_ZIP
    when (result) {
        is com.npic.photoandsignscanner.features.export.ExportResult.Ready.Single -> {
            raiseUnderMinToast(context, result.underMinCount, total = 1)
            FileShareLauncher.shareSingle(
                context = context,
                filePath = result.path,
                chooserTitle = "Export via UPMSP",
                mimeType = jpegMime,
            )
        }
        is com.npic.photoandsignscanner.features.export.ExportResult.Ready.Zip -> {
            raiseUnderMinToast(context, result.underMinCount, total = result.entryCount)
            FileShareLauncher.shareSingle(
                context = context,
                filePath = result.path,
                chooserTitle = "Export via UPMSP",
                mimeType = zipMime,
            )
        }
        is com.npic.photoandsignscanner.features.export.ExportResult.Saved -> {
            val n = result.galleryUris.size
            Toast.makeText(
                context,
                if (n == 1) "Saved 1 photo to Gallery" else "Saved $n photos to Gallery",
                Toast.LENGTH_SHORT,
            ).show()
            raiseUnderMinToast(context, result.underMinCount, total = n)
        }
        is com.npic.photoandsignscanner.features.export.ExportResult.SavedAndReady -> {
            val n = result.gallery.galleryUris.size
            Toast.makeText(
                context,
                if (n == 1) "Saved 1 photo to Gallery" else "Saved $n photos to Gallery",
                Toast.LENGTH_SHORT,
            ).show()
            raiseUnderMinToast(context, result.share.underMinCount, total = n)
            val share = result.share
            val (path, mime) = when (share) {
                is com.npic.photoandsignscanner.features.export.ExportResult.Ready.Single ->
                    share.path to jpegMime
                is com.npic.photoandsignscanner.features.export.ExportResult.Ready.Zip ->
                    share.path to zipMime
            }
            FileShareLauncher.shareSingle(
                context = context,
                filePath = path,
                chooserTitle = "Export via UPMSP",
                mimeType = mime,
            )
        }
        com.npic.photoandsignscanner.features.export.ExportResult.Failed ->
            Toast.makeText(context, "Couldn't prepare the export", Toast.LENGTH_SHORT).show()
    }
}

private fun raiseUnderMinToast(context: android.content.Context, underMinCount: Int, total: Int) {
    if (underMinCount <= 0) return
    val msg = when {
        total <= 1 -> "Export saved but is smaller than the portal minimum — the UPMSP portal may reject it."
        else -> "$underMinCount of $total items fell below portal minimum size — the UPMSP portal may reject them."
    }
    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
}
