package com.npic.photoandsignscanner.features.export

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.npic.photoandsignscanner.data.export.ZipExporter
import com.npic.photoandsignscanner.data.imaging.CombinedRenderer
import com.npic.photoandsignscanner.data.imaging.JpegCompressor
import com.npic.photoandsignscanner.data.storage.SourceStore
import com.npic.photoandsignscanner.domain.model.ExportFormat
import com.npic.photoandsignscanner.domain.model.NamingMode
import com.npic.photoandsignscanner.domain.model.StudentRecord
import com.npic.photoandsignscanner.domain.repo.ExportPreferences
import com.npic.photoandsignscanner.domain.repo.StudentRepository
import com.npic.photoandsignscanner.domain.usecase.GenerateFileName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Layer 9 Export pipeline (PRD §4.10 + §6 + §6.2 + §6.3). For each effective record:
 *
 *   1. Load the source bitmap(s) from [SourceStore]-managed paths.
 *   2. Compose per format via [CombinedRenderer] (Combined) or pass-through (PhotoOnly /
 *      SignatureOnly).
 *   3. Compress into the 10–30 KB portal window via [JpegCompressor].
 *   4. Name the payload via [GenerateFileName].
 *
 * Then bundle the results for the share sheet:
 *   - **1 file**: write to `cacheDir/exports/{filename}` and hand the path to
 *     [ExportResult.Single].
 *   - **> 1 file**: bundle every payload into a single ZIP via [ZipExporter] and hand the
 *     ZIP path to [ExportResult.Zip] (PRD §4.10 multi-share prefers ZIP over
 *     ACTION_SEND_MULTIPLE so desktop / portal receivers get one clean attachment).
 *
 * ### Naming mode inference
 * Layer 9 does not yet persist the user's Save-time Serial/Name choice on the record
 * (that moves onto [StudentRecord] with Room in Layer 10). Until then, infer: if
 * [StudentRecord.displayName] matches the Serial-mode default `Serial N` (or is blank),
 * treat as Serial mode; otherwise Name mode. This preserves the intent for both mock and
 * real records and will be replaced by a real persisted enum in Layer 10.
 *
 * ### Under-min accepted
 * When [JpegCompressor] returns `underMinAccepted = true` (PRD §6.1 Option A — Q95 on a
 * heavily-downscaled bitmap still landed below the 10 KB floor), the state exposes
 * [ExportUiState.underMinCount] so the sheet's caller can raise a toast warning that the
 * portal may reject those items.
 */
class ExportViewModel(
    private val repository: StudentRepository,
    private val recordIds: List<Long>,
    private val sourceStore: SourceStore,
    private val jpegCompressor: JpegCompressor,
    private val combinedRenderer: CombinedRenderer,
    private val zipExporter: ZipExporter,
    private val cacheDir: File,
) : ViewModel() {

    private val _state = MutableStateFlow(ExportUiState(format = ExportPreferences.lastFormat.value))
    val state: StateFlow<ExportUiState> = _state.asStateFlow()

    init {
        loadRecords()
    }

    fun setFormat(format: ExportFormat) {
        ExportPreferences.remember(format)
        _state.value = _state.value.copy(format = format, warningExpanded = false)
    }

    fun toggleWarningExpanded() {
        _state.value = _state.value.copy(warningExpanded = !_state.value.warningExpanded)
    }

    /**
     * Prepare the export bundle for the current [ExportUiState.format]. Fires [onReady]
     * with an [ExportResult] describing what to hand to
     * [com.npic.photoandsignscanner.data.export.FileShareLauncher]. The caller owns the
     * share sheet dispatch because that requires the Android Context.
     *
     * Guarded against double-tap via [ExportUiState.exporting] (Oracle M-8b-M3).
     */
    fun beginExport(onReady: (ExportResult) -> Unit) {
        if (_state.value.exporting) return
        val snapshot = _state.value
        val effective = snapshot.effective
        if (effective.isEmpty()) return
        _state.value = snapshot.copy(exporting = true, underMinCount = 0)

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { produceBundle(effective, snapshot.format) }
                    .onFailure { Log.e(TAG, "produceBundle failed: ${it.message}", it) }
                    .getOrNull()
            } ?: ExportResult.Failed
            _state.value = _state.value.copy(
                exporting = false,
                underMinCount = if (result is ExportResult.Ready) result.underMinCount else 0,
            )
            onReady(result)
        }
    }

    // ------------------------------------------------------------------ pipeline

    private suspend fun produceBundle(records: List<StudentRecord>, format: ExportFormat): ExportResult {
        val outputs = records.mapNotNull { record ->
            renderRecord(record, format)?.let { (bytes, underMin) ->
                Payload(
                    filename = GenerateFileName.forExport(record, format, inferNamingKind(record)),
                    bytes = bytes,
                    underMin = underMin,
                )
            }
        }
        if (outputs.isEmpty()) return ExportResult.Failed

        val underMinCount = outputs.count { it.underMin }
        return when {
            outputs.size == 1 -> {
                val payload = outputs.single()
                val target = File(exportsCacheDir(), payload.filename)
                writeBytes(target, payload.bytes)
                    ?.let { ExportResult.Ready.Single(it, underMinCount) }
                    ?: ExportResult.Failed
            }
            else -> {
                val zip = zipExporter.bundle(outputs.map { it.filename to it.bytes })
                zip?.let { ExportResult.Ready.Zip(it.absolutePath, underMinCount, outputs.size) }
                    ?: ExportResult.Failed
            }
        }
    }

    private fun renderRecord(record: StudentRecord, format: ExportFormat): Pair<ByteArray, Boolean>? {
        val photo = if (format.requiresPhoto) decode(record.photoPath) else null
        val signature = if (format.requiresSignature) decode(record.signaturePath) else null

        try {
            val composed: Bitmap = when (format) {
                ExportFormat.Combined -> {
                    if (photo == null || signature == null) return null
                    combinedRenderer.render(photo, signature)
                }
                ExportFormat.PhotoOnly -> {
                    photo ?: return null
                    // JpegCompressor doesn't mutate its source, so we can share the
                    // decoded bitmap directly and recycle at the end.
                    photo
                }
                ExportFormat.SignatureOnly -> {
                    signature ?: return null
                    signature
                }
            }
            return try {
                val result = jpegCompressor.compress(composed)
                result.bytes to result.underMinAccepted
            } finally {
                // Combined path allocates a fresh composed bitmap distinct from photo /
                // signature — recycle it here. PhotoOnly / SignatureOnly reuse the decoded
                // bitmap in-place; recycling happens below regardless via the outer finally.
                if (composed !== photo && composed !== signature) composed.recycle()
            }
        } finally {
            photo?.recycle()
            signature?.recycle()
        }
    }

    private fun decode(path: String?): Bitmap? {
        if (path.isNullOrBlank()) return null
        return try {
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            })
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to decode source at $path: ${t.message}", t)
            null
        }
    }

    private fun writeBytes(target: File, bytes: ByteArray): String? = try {
        target.parentFile?.mkdirs()
        FileOutputStream(target).use { it.write(bytes) }
        target.absolutePath
    } catch (t: Throwable) {
        Log.e(TAG, "Failed to write ${bytes.size}b to $target: ${t.message}", t)
        null
    }

    private fun exportsCacheDir(): File = File(cacheDir, EXPORTS_SUBDIR).apply { mkdirs() }

    /**
     * Best-effort inference of the user's original Save-time naming choice per record.
     * Layer 10 replaces this with a persisted enum on [StudentRecord]. See class KDoc.
     */
    private fun inferNamingKind(record: StudentRecord): NamingMode.Kind =
        if (record.displayName.isBlank() || record.displayName.matches(SERIAL_PLACEHOLDER)) {
            NamingMode.Kind.Serial
        } else {
            NamingMode.Kind.Name
        }

    private fun loadRecords() {
        viewModelScope.launch {
            val loaded: List<StudentRecord> = recordIds.mapNotNull { id -> repository.getById(id) }
            _state.value = _state.value.copy(records = loaded)
        }
    }

    private data class Payload(val filename: String, val bytes: ByteArray, val underMin: Boolean) {
        override fun equals(other: Any?): Boolean = this === other
        override fun hashCode(): Int = System.identityHashCode(this)
    }

    class Factory(
        private val repository: StudentRepository,
        private val recordIds: List<Long>,
        private val sourceStore: SourceStore,
        private val jpegCompressor: JpegCompressor,
        private val combinedRenderer: CombinedRenderer,
        private val zipExporter: ZipExporter,
        private val cacheDir: File,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ExportViewModel(
                repository,
                recordIds,
                sourceStore,
                jpegCompressor,
                combinedRenderer,
                zipExporter,
                cacheDir,
            ) as T
    }

    private companion object {
        const val TAG = "ExportViewModel"
        const val EXPORTS_SUBDIR = "exports"

        /**
         * Matches the placeholder displayName Layer 8b uses when the user picks Serial
         * naming mode and never types a name: `"Serial 42"`. Used by [inferNamingKind]
         * to route back to Serial-format filenames. Layer 10 removes this heuristic.
         */
        val SERIAL_PLACEHOLDER = Regex("^Serial \\d+$")
    }
}

/**
 * Sealed outcome of [ExportViewModel.beginExport]. The caller pattern-matches to fire the
 * correct share intent (single JPEG vs. ZIP bundle) and to raise the PRD §6.1 under-min
 * toast.
 */
sealed interface ExportResult {
    sealed interface Ready : ExportResult {
        val underMinCount: Int

        /** Single-record export: one JPEG at [path]. Shared as `image/jpeg`. */
        data class Single(val path: String, override val underMinCount: Int) : Ready

        /** Multi-record export: ZIP at [path]. Shared as `application/zip`. */
        data class Zip(val path: String, override val underMinCount: Int, val entryCount: Int) : Ready
    }

    /** Terminal failure — nothing to share. Caller shows an error toast. */
    data object Failed : ExportResult
}
