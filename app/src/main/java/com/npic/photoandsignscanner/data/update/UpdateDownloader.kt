package com.npic.photoandsignscanner.data.update

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.util.Log
import com.npic.photoandsignscanner.domain.model.UpdateManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * m2508: streams the release APK from [UpdateManifest.apkUrl] into
 * `cache/updates/npic-{versionName}.apk` via Android's DownloadManager.
 *
 * DownloadManager was chosen over OkHttp/Ktor because it:
 *   1. Survives Doze and app process death (system service holds the download)
 *   2. Automatically resumes on network return
 *   3. Renders a system notification with progress the user already recognizes
 *   4. Handles Wi-Fi vs cellular routing per the policy set here
 *
 * The user picked "download on any network" in the m2508 scoping question, so we do
 * NOT restrict to Wi-Fi. The system notification still shows size so a teacher on
 * cellular sees "68 MB" before enqueueing.
 *
 * After completion we verify SHA-256 against [UpdateManifest.apkSha256] — a mismatch
 * deletes the file and surfaces [Progress.Failed] with reason CHECKSUM. This catches
 * both corrupt downloads and (theoretical) MITM tampering.
 */
class UpdateDownloader(
    private val context: Context,
) {

    private val downloadManager: DownloadManager
        get() = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    /**
     * Directory where downloaded APKs stage before install. Cache dir means Android
     * can reclaim if the device runs low on storage; the APK is disposable once
     * PackageInstaller has streamed it.
     */
    private val updatesDir: File
        get() = File(context.cacheDir, "updates").apply { mkdirs() }

    /**
     * Returns the file path where the APK for [manifest] would stage. Used by the
     * ViewModel to detect a resumable cached download after a process restart.
     */
    fun stagedApkFile(manifest: UpdateManifest): File =
        File(updatesDir, "npic-${manifest.versionName}.apk")

    /**
     * Enqueues a download and emits [Progress] until the download finishes with
     * [Progress.Complete] or [Progress.Failed]. Suspends on a polling loop against
     * DownloadManager's cursor; 400 ms is fast enough for a responsive progress bar
     * without churning battery.
     */
    fun download(manifest: UpdateManifest): Flow<Progress> = flow {
        val destination = stagedApkFile(manifest)

        // m2508: reuse a valid cached APK. If we already downloaded this exact
        // version before (user cancelled install, closed the sheet, came back
        // next launch), skip the network entirely. SHA-256 gate ensures the
        // cache wasn't tampered with by another app writing to cacheDir.
        if (destination.exists() && destination.length() == manifest.apkSizeBytes) {
            val cachedHash = withContext(Dispatchers.IO) { sha256(destination) }
            if (cachedHash.equals(manifest.apkSha256, ignoreCase = true)) {
                emit(Progress.Complete(destination))
                return@flow
            }
            destination.delete()
        }

        val request = DownloadManager.Request(Uri.parse(manifest.apkUrl)).apply {
            setDestinationUri(Uri.fromFile(destination))
            setTitle("NPIC Photo Scanner v${manifest.versionName}")
            setDescription("Downloading update…")
            // NETWORK_MOBILE | NETWORK_WIFI = allow any network per m2508 policy.
            setAllowedNetworkTypes(
                DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE
            )
            setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )
        }

        val downloadId = downloadManager.enqueue(request)
        // m2509 H1: `try/finally` with NonCancellable ensures DownloadManager.remove()
        // + destination.delete() run when the flow collector is cancelled (user
        // dismisses sheet, ViewModel cleared, navigation change). CancellationException
        // is rethrown by coroutines and skips regular catch blocks, so cleanup MUST
        // happen in finally under NonCancellable to actually reach the system service.
        var completedNaturally = false
        try {
            while (true) {
                val snapshot = queryProgressWithRetry(downloadId)
                if (snapshot == null) {
                    emit(Progress.Failed(Reason.CANCELLED))
                    destination.delete()
                    return@flow
                }
                when (snapshot.status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        completedNaturally = true
                        break
                    }
                    DownloadManager.STATUS_FAILED -> {
                        // m2509 H2: read COLUMN_REASON so ERROR_INSUFFICIENT_SPACE +
                        // ERROR_FILE_ERROR surface as "not enough storage" instead of
                        // the misleading "check your connection" default. Everything
                        // else stays NETWORK — HTTP_DATA_ERROR, CANNOT_RESUME, timeouts.
                        val reason = when (snapshot.failureReason) {
                            DownloadManager.ERROR_INSUFFICIENT_SPACE,
                            DownloadManager.ERROR_FILE_ERROR,
                            DownloadManager.ERROR_DEVICE_NOT_FOUND -> Reason.STORAGE
                            else -> Reason.NETWORK
                        }
                        emit(Progress.Failed(reason))
                        destination.delete()
                        return@flow
                    }
                    else -> emit(
                        Progress.Downloading(
                            downloadedBytes = snapshot.downloaded,
                            totalBytes = snapshot.total.coerceAtLeast(manifest.apkSizeBytes),
                        )
                    )
                }
                delay(POLL_INTERVAL_MS)
            }
        } finally {
            if (!completedNaturally) {
                withContext(NonCancellable) {
                    runCatching { downloadManager.remove(downloadId) }
                    runCatching { destination.delete() }
                }
            }
        }

        emit(Progress.Verifying)
        val actualHash = withContext(Dispatchers.IO) { sha256(destination) }
        if (!actualHash.equals(manifest.apkSha256, ignoreCase = true)) {
            destination.delete()
            emit(Progress.Failed(Reason.CHECKSUM))
            return@flow
        }
        emit(Progress.Complete(destination))
    }.flowOn(Dispatchers.IO)

    /**
     * m2509 H5: retry up to [SNAPSHOT_RETRY_ATTEMPTS] times with a short delay before
     * concluding the download was cancelled. Transient DownloadManager cursor
     * failures (database briefly locked, system service restarting) look identical
     * to a real user cancellation from a single-query perspective; treating them
     * the same misclassifies a network glitch as user intent. Three attempts at
     * 100 ms adds at most 300 ms to a real cancellation detection — imperceptible.
     */
    private suspend fun queryProgressWithRetry(downloadId: Long): CursorSnapshot? {
        var attempts = SNAPSHOT_RETRY_ATTEMPTS
        while (attempts-- > 0) {
            val snapshot = queryProgress(downloadId)
            if (snapshot != null) return snapshot
            if (attempts > 0) delay(SNAPSHOT_RETRY_DELAY_MS)
        }
        return null
    }

    private fun queryProgress(downloadId: Long): CursorSnapshot? {
        val query = DownloadManager.Query().setFilterById(downloadId)
        return downloadManager.query(query)?.use { cursor: Cursor ->
            if (!cursor.moveToFirst()) return@use null
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val downloaded = cursor.getLong(
                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            )
            val total = cursor.getLong(
                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            )
            val failureReason = if (status == DownloadManager.STATUS_FAILED) {
                cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
            } else 0
            CursorSnapshot(
                status = status,
                downloaded = downloaded,
                total = total,
                failureReason = failureReason,
            )
        }
    }

    /**
     * Compute the SHA-256 digest of [file] as a lowercase hex string. Streams in
     * 8 KB chunks so a 70 MB APK never lands entirely in memory. Runs on IO.
     */
    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    /**
     * Progress emissions the ViewModel folds into UI state. Each Downloading emission
     * carries fresh byte counters; the ViewModel maps to a 0f..1f fraction and drives
     * the primary-button-turned-progress-bar in UpdateSheet.
     */
    sealed interface Progress {
        data class Downloading(val downloadedBytes: Long, val totalBytes: Long) : Progress
        data object Verifying : Progress
        data class Complete(val apkFile: File) : Progress
        data class Failed(val reason: Reason) : Progress
    }

    enum class Reason { NETWORK, STORAGE, CHECKSUM, CANCELLED }

    private data class CursorSnapshot(
        val status: Int,
        val downloaded: Long,
        val total: Long,
        val failureReason: Int,
    )

    companion object {
        private const val TAG = "UpdateDownloader"
        private const val POLL_INTERVAL_MS = 400L
        private const val SNAPSHOT_RETRY_ATTEMPTS = 3
        private const val SNAPSHOT_RETRY_DELAY_MS = 100L
    }
}
