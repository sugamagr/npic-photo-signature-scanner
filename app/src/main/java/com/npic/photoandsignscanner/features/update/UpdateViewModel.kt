package com.npic.photoandsignscanner.features.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.pm.PackageInstaller
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.npic.photoandsignscanner.data.update.UpdateDownloader
import com.npic.photoandsignscanner.data.update.UpdateInstaller
import com.npic.photoandsignscanner.data.update.UpdateRepository
import com.npic.photoandsignscanner.domain.model.UpdateAvailability
import com.npic.photoandsignscanner.domain.model.UpdateManifest
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

/**
 * m2508: state machine for the self-hosted updater.
 *
 * Lifecycle:
 *   Idle → (checkForUpdates) → Checking → UpToDate | Available
 *   Available → (startDownload) → Downloading → Verifying → ReadyToInstall
 *   ReadyToInstall → (install) → Installing → (BroadcastReceiver) → Success | Failed
 *
 * At most one flow is live at a time — startDownload cancels any prior job. Install
 * status arrives asynchronously via [UpdateInstaller.registerStatusReceiver]; the
 * receiver is registered lazily on first install call and torn down when the
 * ViewModel is cleared.
 */
class UpdateViewModel(
    private val repository: UpdateRepository,
    private val downloader: UpdateDownloader,
    private val installer: UpdateInstaller,
) : ViewModel() {

    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    private var activeJob: Job? = null
    private var statusReceiver: BroadcastReceiver? = null

    /**
     * Runs the manifest check. Safe to call repeatedly; a check while a download is
     * already active is ignored so a user tapping "Check for updates" in Settings
     * mid-download doesn't collapse the progress state.
     */
    fun checkForUpdates() {
        val current = _state.value
        if (current is UpdateUiState.Downloading || current is UpdateUiState.Verifying ||
            current is UpdateUiState.Installing
        ) return
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _state.value = UpdateUiState.Checking
            when (val result = repository.check()) {
                is UpdateAvailability.UpToDate -> _state.value = UpdateUiState.UpToDate
                is UpdateAvailability.Available -> {
                    val staged = downloader.stagedApkFile(result.manifest)
                    val readyCached = staged.exists() && staged.length() == result.manifest.apkSizeBytes
                    _state.value = UpdateUiState.Available(
                        manifest = result.manifest,
                        blocking = result.blocking,
                        hasStagedApk = readyCached,
                    )
                }
            }
        }
    }

    /**
     * Enqueues the APK download for the manifest currently in state. No-op if the
     * state isn't [UpdateUiState.Available]. Emissions from the flow update state
     * in place so the sheet renders live progress.
     */
    fun startDownload() {
        val available = _state.value as? UpdateUiState.Available ?: return
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _state.value = UpdateUiState.Downloading(
                manifest = available.manifest,
                blocking = available.blocking,
                progressFraction = 0f,
                downloadedBytes = 0,
                totalBytes = available.manifest.apkSizeBytes,
            )
            downloader.download(available.manifest).collectLatest { progress ->
                _state.value = when (progress) {
                    is UpdateDownloader.Progress.Downloading -> UpdateUiState.Downloading(
                        manifest = available.manifest,
                        blocking = available.blocking,
                        progressFraction = fractionOf(progress.downloadedBytes, progress.totalBytes),
                        downloadedBytes = progress.downloadedBytes,
                        totalBytes = progress.totalBytes,
                    )
                    is UpdateDownloader.Progress.Verifying -> UpdateUiState.Verifying(
                        manifest = available.manifest,
                        blocking = available.blocking,
                    )
                    is UpdateDownloader.Progress.Complete -> UpdateUiState.ReadyToInstall(
                        manifest = available.manifest,
                        blocking = available.blocking,
                        apkFile = progress.apkFile,
                    )
                    is UpdateDownloader.Progress.Failed -> UpdateUiState.Failed(
                        manifest = available.manifest,
                        blocking = available.blocking,
                        reason = reasonMessage(progress.reason),
                        recoverable = true,
                    )
                }
            }
        }
    }

    /**
     * Kicks off the PackageInstaller session. If the OS reports we don't yet have
     * REQUEST_INSTALL_PACKAGES granted, state transitions to [UpdateUiState.NeedsInstallPermission]
     * carrying the settings intent the sheet uses to launch the grant flow.
     */
    fun install(context: Context) {
        val ready = _state.value as? UpdateUiState.ReadyToInstall ?: return
        ensureStatusReceiver(context)
        _state.value = UpdateUiState.Installing(
            manifest = ready.manifest,
            blocking = ready.blocking,
        )
        when (val outcome = installer.install(ready.apkFile)) {
            UpdateInstaller.InstallOutcome.Committed -> Unit
            is UpdateInstaller.InstallOutcome.NeedsPermission ->
                _state.value = UpdateUiState.NeedsInstallPermission(
                    manifest = ready.manifest,
                    blocking = ready.blocking,
                    apkFile = ready.apkFile,
                )
            is UpdateInstaller.InstallOutcome.Failed -> _state.value = UpdateUiState.Failed(
                manifest = ready.manifest,
                blocking = ready.blocking,
                reason = outcome.message,
                recoverable = true,
            )
        }
    }

    /** Called after the user grants REQUEST_INSTALL_PACKAGES to retry install. */
    fun retryInstallAfterPermission() {
        val pending = _state.value as? UpdateUiState.NeedsInstallPermission ?: return
        _state.value = UpdateUiState.ReadyToInstall(
            manifest = pending.manifest,
            blocking = pending.blocking,
            apkFile = pending.apkFile,
        )
    }

    /**
     * Retry from Failed. Restarts the check if the failure was network-side; retries
     * install if the APK is already staged.
     */
    fun retry() {
        val failed = _state.value as? UpdateUiState.Failed ?: return
        val staged = downloader.stagedApkFile(failed.manifest)
        if (staged.exists() && staged.length() == failed.manifest.apkSizeBytes) {
            _state.value = UpdateUiState.ReadyToInstall(
                manifest = failed.manifest,
                blocking = failed.blocking,
                apkFile = staged,
            )
        } else {
            _state.value = UpdateUiState.Available(
                manifest = failed.manifest,
                blocking = failed.blocking,
                hasStagedApk = false,
            )
            startDownload()
        }
    }

    /** User dismissed the sheet with "Later". Only allowed when the update is non-blocking. */
    fun dismiss() {
        val current = _state.value
        val blocking = when (current) {
            is UpdateUiState.Available -> current.blocking
            is UpdateUiState.Downloading -> current.blocking
            is UpdateUiState.Verifying -> current.blocking
            is UpdateUiState.ReadyToInstall -> current.blocking
            is UpdateUiState.NeedsInstallPermission -> current.blocking
            is UpdateUiState.Failed -> current.blocking
            is UpdateUiState.Installing -> current.blocking
            else -> false
        }
        if (blocking) return
        activeJob?.cancel()
        _state.value = UpdateUiState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        statusReceiver?.let(installer::unregister)
        statusReceiver = null
    }

    private fun ensureStatusReceiver(context: Context) {
        if (statusReceiver != null) return
        statusReceiver = installer.registerStatusReceiver { status, message ->
            val current = _state.value
            val manifest = manifestOf(current) ?: return@registerStatusReceiver
            val blocking = blockingOf(current)
            when (status) {
                PackageInstaller.STATUS_SUCCESS -> _state.value = UpdateUiState.Idle
                PackageInstaller.STATUS_FAILURE_BLOCKED -> _state.value = UpdateUiState.Failed(
                    manifest = manifest,
                    blocking = blocking,
                    reason = if (installer.isLikelyAutoBlockerDevice()) {
                        "Samsung Auto Blocker is preventing this update. Turn it off in Settings → Security and privacy → Auto Blocker, then try again."
                    } else {
                        "The install was blocked by a device policy. ${message ?: ""}".trim()
                    },
                    recoverable = true,
                )
                PackageInstaller.STATUS_FAILURE_ABORTED -> _state.value = UpdateUiState.ReadyToInstall(
                    manifest = manifest,
                    blocking = blocking,
                    apkFile = downloader.stagedApkFile(manifest),
                )
                else -> _state.value = UpdateUiState.Failed(
                    manifest = manifest,
                    blocking = blocking,
                    reason = "Install failed. ${message ?: "Try again."}".trim(),
                    recoverable = true,
                )
            }
        }
    }

    private fun manifestOf(state: UpdateUiState): UpdateManifest? = when (state) {
        is UpdateUiState.Available -> state.manifest
        is UpdateUiState.Downloading -> state.manifest
        is UpdateUiState.Verifying -> state.manifest
        is UpdateUiState.ReadyToInstall -> state.manifest
        is UpdateUiState.NeedsInstallPermission -> state.manifest
        is UpdateUiState.Installing -> state.manifest
        is UpdateUiState.Failed -> state.manifest
        else -> null
    }

    private fun blockingOf(state: UpdateUiState): Boolean = when (state) {
        is UpdateUiState.Available -> state.blocking
        is UpdateUiState.Downloading -> state.blocking
        is UpdateUiState.Verifying -> state.blocking
        is UpdateUiState.ReadyToInstall -> state.blocking
        is UpdateUiState.NeedsInstallPermission -> state.blocking
        is UpdateUiState.Installing -> state.blocking
        is UpdateUiState.Failed -> state.blocking
        else -> false
    }

    private fun fractionOf(downloaded: Long, total: Long): Float {
        if (total <= 0L) return 0f
        return (downloaded.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f)
    }

    private fun reasonMessage(reason: UpdateDownloader.Reason): String = when (reason) {
        UpdateDownloader.Reason.NETWORK -> "Download failed. Check your connection and try again."
        UpdateDownloader.Reason.CHECKSUM -> "The downloaded file was corrupted. Try again."
        UpdateDownloader.Reason.CANCELLED -> "Download was cancelled."
    }

    class Factory(
        private val repository: UpdateRepository,
        private val downloader: UpdateDownloader,
        private val installer: UpdateInstaller,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return UpdateViewModel(
                repository = repository,
                downloader = downloader,
                installer = installer,
            ) as T
        }
    }
}

/**
 * m2508: presentation-facing state for the update sheet. Every non-Idle state carries
 * the [UpdateManifest] and [blocking] flag so the sheet doesn't have to remember them
 * across transitions.
 */
sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data object UpToDate : UpdateUiState

    data class Available(
        val manifest: UpdateManifest,
        val blocking: Boolean,
        val hasStagedApk: Boolean,
    ) : UpdateUiState

    data class Downloading(
        val manifest: UpdateManifest,
        val blocking: Boolean,
        val progressFraction: Float,
        val downloadedBytes: Long,
        val totalBytes: Long,
    ) : UpdateUiState

    data class Verifying(
        val manifest: UpdateManifest,
        val blocking: Boolean,
    ) : UpdateUiState

    data class ReadyToInstall(
        val manifest: UpdateManifest,
        val blocking: Boolean,
        val apkFile: File,
    ) : UpdateUiState

    data class NeedsInstallPermission(
        val manifest: UpdateManifest,
        val blocking: Boolean,
        val apkFile: File,
    ) : UpdateUiState

    data class Installing(
        val manifest: UpdateManifest,
        val blocking: Boolean,
    ) : UpdateUiState

    data class Failed(
        val manifest: UpdateManifest,
        val blocking: Boolean,
        val reason: String,
        val recoverable: Boolean,
    ) : UpdateUiState
}
