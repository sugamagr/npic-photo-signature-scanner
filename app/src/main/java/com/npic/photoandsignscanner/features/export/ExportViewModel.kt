package com.npic.photoandsignscanner.features.export

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.npic.photoandsignscanner.data.export.MediaStoreExporter
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
import kotlinx.datetime.Instant
import java.io.File
import java.io.FileOutputStream

/**
 * Layer 9 Export pipeline (PRD §4.10 + §6 + §6.2 + §6.3). For each effective record:
 *
 *   1. Load the source bitmap(s) from [SourceStore]-managed paths.
 *   2. Compose per format via [CombinedRenderer] (Combined) or pass-through (PhotoOnly /
 *      SignatureOnly).
 *   3. Compress into the portal window via [JpegCompressor]: PhotoOnly / SignatureOnly
 *      cap at 23 KB, Combined at 28 KB (m2228 tightened from 30 KB to leave safety margin
 *      against the observed 30.23 KB portal-edge overshoot).
 *   4. Name the payload via [GenerateFileName].
 *
 * Then bundle the results for the share sheet:
 *   - **1 file**: write to `cacheDir/exports/{filename}` and hand the path to
 *     [ExportResult.Single].
 *   - **> 1 file**: bundle every payload into a single ZIP via [ZipExporter] and hand the
 *     ZIP path to [ExportResult.Zip] (PRD §4.10 multi-share prefers ZIP over
 *     ACTION_SEND_MULTIPLE so desktop / portal receivers get one clean attachment).
 *
 * ### Naming mode (m2496)
 * Each record's `namingKind` is now persisted on [StudentRecord] (m2232 Room v2), so we
 * use it as the source of truth. The user can also override on a per-batch basis via
 * [setNamingOverride] — but only for Name-mode records. Serial-mode records ignore the
 * override because their `displayName` is placeholder text ("Serial 42"), not a real
 * name; forcing them to Name-mode filenames would produce `Serial_42_09.jpeg`, which
 * is worse than the deterministic `090001.jpeg`.
 *
 * ### Under-min accepted
 * When [JpegCompressor] returns `underMinAccepted = true` (PRD §6.1 Option A — Q95 on a
 * heavily-downscaled bitmap still landed below the 10 KB floor), the state exposes
 * [ExportUiState.underMinCount] so the sheet's caller can raise a toast warning that the
 * portal may reject those items.
 */
class ExportViewModel(
    private val repository: StudentRepository,
    private val recordIds: List<String>,
    private val sourceStore: SourceStore,
    private val jpegCompressor: JpegCompressor,
    private val combinedRenderer: CombinedRenderer,
    private val zipExporter: ZipExporter,
    private val mediaStoreExporter: MediaStoreExporter,
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
     * m2496: user-chosen filename naming override for the current export batch. Applied
     * to Name-mode records only (Serial-mode records ignore it — they only have one
     * meaningful filename). Setting `null` restores each record's persisted default.
     */
    fun setNamingOverride(kind: NamingMode.Kind?) {
        _state.value = _state.value.copy(namingOverride = kind)
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
                runCatching { produceBundle(effective, snapshot.format, snapshot.namingOverride) }
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

    /**
     * Save the rendered export payload(s) to the device Gallery via MediaStore. Runs the
     * SAME pipeline as [beginExport] (render → compress → filename), then writes each
     * payload as an individual JPEG under `Pictures/NPIC/`. Even when [beginExport] would
     * ZIP a multi-record set, this method skips ZIP because Gallery apps can't index ZIP
     * contents — users want to see the individual photos in their library.
     *
     * Shares the [ExportUiState.exporting] guard with [beginExport] so double-tap across
     * either action can't fire the pipeline twice.
     */
    fun beginSaveToGallery(onReady: (ExportResult) -> Unit) {
        if (_state.value.exporting) return
        val snapshot = _state.value
        val effective = snapshot.effective
        if (effective.isEmpty()) return
        _state.value = snapshot.copy(exporting = true, underMinCount = 0)

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { saveToGallery(effective, snapshot.format, snapshot.namingOverride) }
                    .onFailure { Log.e(TAG, "saveToGallery failed: ${it.message}", it) }
                    .getOrNull()
            } ?: ExportResult.Failed
            _state.value = _state.value.copy(
                exporting = false,
                underMinCount = when (result) {
                    is ExportResult.Ready -> result.underMinCount
                    is ExportResult.Saved -> result.underMinCount
                    else -> 0
                },
            )
            onReady(result)
        }
    }

    /**
     * Run the pipeline once, save every payload to Gallery AND write the share bundle
     * (single JPEG or ZIP). Emits an [ExportResult.SavedAndReady] carrying both outcomes
     * so the caller can raise the "Saved N to Gallery" toast and immediately fire the
     * share sheet with the same bytes — compression runs exactly once.
     */
    fun beginSaveAndShare(onReady: (ExportResult) -> Unit) {
        if (_state.value.exporting) return
        val snapshot = _state.value
        val effective = snapshot.effective
        if (effective.isEmpty()) return
        _state.value = snapshot.copy(exporting = true, underMinCount = 0)

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { saveAndShare(effective, snapshot.format, snapshot.namingOverride) }
                    .onFailure { Log.e(TAG, "saveAndShare failed: ${it.message}", it) }
                    .getOrNull()
            } ?: ExportResult.Failed
            _state.value = _state.value.copy(
                exporting = false,
                underMinCount = when (result) {
                    is ExportResult.Ready -> result.underMinCount
                    is ExportResult.Saved -> result.underMinCount
                    is ExportResult.SavedAndReady -> result.share.underMinCount
                    else -> 0
                },
            )
            onReady(result)
        }
    }

    // ------------------------------------------------------------------ pipeline

    /**
     * Shared render pipeline for every export action. Returns per-record payloads
     * (filename + compressed bytes + under-min flag). Called by [produceBundle],
     * [saveToGallery], and [saveAndShare] so compression runs exactly once per
     * user-initiated export regardless of destination fan-out.
     *
     * m2496: filename honors [ExportUiState.namingOverride] when set — but only for
     * records that were originally saved under Name mode. Serial-mode records always
     * export as `090001.jpeg` regardless of the toggle, because their displayName
     * is placeholder text ("Serial 42"), not a real name the user typed.
     */
    private fun renderPayloads(
        records: List<StudentRecord>,
        format: ExportFormat,
        namingOverride: NamingMode.Kind?,
    ): List<Payload> {
        // Priority 3 (necessary): m2502 duplicate-index feature — solo exports of any
        // record must land as clean `090001.jpeg`, but when two records with the SAME
        // filename appear in ONE batch (e.g. two records sharing class+serial via
        // duplicateIndex), the later-created one gets ` (2)` / ` (3)` appended before
        // the extension. Earlier-created keeps the clean name. See DESIGN duplicate
        // Q4/Q6 answers in the m2502 change history.
        val rendered = records.mapNotNull { record ->
            renderRecord(record, format)?.let { (bytes, underMin) ->
                Payload(
                    filename = GenerateFileName.forExport(
                        record = record,
                        format = format,
                        namingMode = resolveNamingKind(record, namingOverride),
                    ),
                    bytes = bytes,
                    underMin = underMin,
                    createdAt = record.createdAt,
                    recordId = record.id,
                )
            }
        }
        return resolveBatchCollisions(rendered)
    }

    /**
     * m2502: append ` (2)` / ` (3)` / … before the file extension for any group of
     * payloads that would otherwise collide on filename inside a single export batch.
     * Deterministic ordering: earliest `createdAt` (then `recordId` as tie-breaker)
     * keeps the clean name; successive duplicates get the suffix. Groups of size 1
     * pass through untouched, so a solo export of a duplicate record still lands as
     * clean `090001.jpeg`.
     */
    private fun resolveBatchCollisions(payloads: List<Payload>): List<Payload> {
        val groups = payloads.groupBy { it.filename }
        if (groups.values.all { it.size == 1 }) return payloads
        val remapped = HashMap<String, String>(payloads.size)
        for ((filename, group) in groups) {
            if (group.size == 1) {
                remapped[group.single().recordId] = filename
                continue
            }
            val ordered = group.sortedWith(
                compareBy({ it.createdAt }, { it.recordId }),
            )
            ordered.forEachIndexed { index, payload ->
                remapped[payload.recordId] = if (index == 0) filename else suffixed(filename, index + 1)
            }
        }
        // Preserve original payload order (which mirrors the caller's record order —
        // Gallery selection order or explicit list) so the share sheet / ZIP entries
        // appear in the sequence the user picked, not sorted by createdAt.
        return payloads.map { it.copy(filename = remapped.getValue(it.recordId)) }
    }

    /**
     * Insert `_N` before the last `.` in [filename]. Underscore matches the Name-mode
     * filename convention (`Rahul_Kumar_09.jpeg`) and stays portal-safe: the UPMSP
     * bulk-upload page parses filenames with letters/digits/underscores, but a space
     * plus parentheses would risk rejection or misparse.
     */
    private fun suffixed(filename: String, index: Int): String {
        val dot = filename.lastIndexOf('.')
        return if (dot <= 0) "${filename}_$index"
        else filename.substring(0, dot) + "_$index" + filename.substring(dot)
    }

    /**
     * m2496: resolve the filename naming mode for a single record given the batch-wide
     * override. Serial-mode records ignore the override (their displayName is
     * placeholder text); Name-mode records take the override when set, else fall
     * back to Name (their persisted default).
     */
    private fun resolveNamingKind(
        record: StudentRecord,
        override: NamingMode.Kind?,
    ): NamingMode.Kind =
        when (record.namingKind) {
            NamingMode.Kind.Serial -> NamingMode.Kind.Serial
            NamingMode.Kind.Name   -> override ?: NamingMode.Kind.Name
        }

    private suspend fun produceBundle(
        records: List<StudentRecord>,
        format: ExportFormat,
        namingOverride: NamingMode.Kind?,
    ): ExportResult {
        val outputs = renderPayloads(records, format, namingOverride)
        if (outputs.isEmpty()) return ExportResult.Failed
        return bundleForShare(outputs)
    }

    private suspend fun saveToGallery(
        records: List<StudentRecord>,
        format: ExportFormat,
        namingOverride: NamingMode.Kind?,
    ): ExportResult {
        val outputs = renderPayloads(records, format, namingOverride)
        if (outputs.isEmpty()) return ExportResult.Failed
        val saved = writeAllToGallery(outputs)
        if (saved.isEmpty()) return ExportResult.Failed
        return ExportResult.Saved(saved, outputs.count { it.underMin })
    }

    private suspend fun saveAndShare(
        records: List<StudentRecord>,
        format: ExportFormat,
        namingOverride: NamingMode.Kind?,
    ): ExportResult {
        val outputs = renderPayloads(records, format, namingOverride)
        if (outputs.isEmpty()) return ExportResult.Failed
        val saved = writeAllToGallery(outputs)
        val underMinCount = outputs.count { it.underMin }
        val share = bundleForShare(outputs)
        return if (share is ExportResult.Ready && saved.isNotEmpty()) {
            ExportResult.SavedAndReady(
                gallery = ExportResult.Saved(saved, underMinCount),
                share = share,
            )
        } else if (share is ExportResult.Ready) {
            // Gallery write failed for every record but the share bundle exists — degrade
            // gracefully to share-only rather than dropping the whole action.
            share
        } else if (saved.isNotEmpty()) {
            ExportResult.Saved(saved, underMinCount)
        } else {
            ExportResult.Failed
        }
    }

    private suspend fun bundleForShare(outputs: List<Payload>): ExportResult {
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

    private suspend fun writeAllToGallery(outputs: List<Payload>): List<Uri> =
        outputs.mapNotNull { payload ->
            mediaStoreExporter.saveJpeg(payload.filename, payload.bytes)
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
                // m2228 per-format ceilings: PhotoOnly / SignatureOnly cap at 23 KB;
                // Combined at 28 KB (matches JpegCompressor.MAX_BYTES default). Portal
                // rejects at 30.23 KB and above in practice.
                val maxBytes = when (format) {
                    ExportFormat.Combined -> 28 * 1024
                    ExportFormat.PhotoOnly, ExportFormat.SignatureOnly -> 23 * 1024
                }
                val result = jpegCompressor.compress(composed, maxBytes = maxBytes)
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

    private fun loadRecords() {
        viewModelScope.launch {
            val loaded: List<StudentRecord> = recordIds.mapNotNull { id -> repository.getById(id) }
            _state.value = _state.value.copy(records = loaded)
        }
    }

    private data class Payload(
        val filename: String,
        val bytes: ByteArray,
        val underMin: Boolean,
        val createdAt: Instant,
        val recordId: String,
    ) {
        override fun equals(other: Any?): Boolean = this === other
        override fun hashCode(): Int = System.identityHashCode(this)
    }

    class Factory(
        private val repository: StudentRepository,
        private val recordIds: List<String>,
        private val sourceStore: SourceStore,
        private val jpegCompressor: JpegCompressor,
        private val combinedRenderer: CombinedRenderer,
        private val zipExporter: ZipExporter,
        private val mediaStoreExporter: MediaStoreExporter,
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
                mediaStoreExporter,
                cacheDir,
            ) as T
    }

    private companion object {
        const val TAG = "ExportViewModel"
        const val EXPORTS_SUBDIR = "exports"
    }
}

/**
 * Sealed outcome of the three export actions on [ExportViewModel]. The caller
 * pattern-matches to fire the correct share intent, raise the "Saved N to Gallery" toast,
 * and surface the PRD §6.1 under-min warning.
 *
 * Variants:
 * - [Ready.Single] / [Ready.Zip] — share-only path (beginExport)
 * - [Saved] — Gallery-only path (beginSaveToGallery)
 * - [SavedAndReady] — both (beginSaveAndShare); nests the two sinks so callers can
 *   independently raise the save toast and fire the share sheet
 * - [Failed] — terminal error
 */
sealed interface ExportResult {
    sealed interface Ready : ExportResult {
        val underMinCount: Int

        /** Single-record export: one JPEG at [path]. Shared as `image/jpeg`. */
        data class Single(val path: String, override val underMinCount: Int) : Ready

        /** Multi-record export: ZIP at [path]. Shared as `application/zip`. */
        data class Zip(val path: String, override val underMinCount: Int, val entryCount: Int) : Ready
    }

    /**
     * Save-to-Gallery outcome. [galleryUris] holds the content:// URIs of the JPEGs
     * written to `Pictures/NPIC/` via MediaStore. Caller raises "Saved N to Gallery"
     * plus the under-min toast if [underMinCount] > 0.
     */
    data class Saved(val galleryUris: List<android.net.Uri>, val underMinCount: Int) : ExportResult

    /**
     * Save-and-share outcome. Both sinks succeeded; caller raises the save toast
     * AND fires the share sheet with [share].
     */
    data class SavedAndReady(val gallery: Saved, val share: Ready) : ExportResult

    /** Terminal failure — nothing to share. Caller shows an error toast. */
    data object Failed : ExportResult
}
