package com.npic.photoandsignscanner.features.update

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.npic.photoandsignscanner.BuildConfig
import com.npic.photoandsignscanner.core.theme.LocalNpicChrome
import com.npic.photoandsignscanner.core.theme.NpicColors
import com.npic.photoandsignscanner.core.theme.NpicShapes
import com.npic.photoandsignscanner.core.theme.NpicSpacing
import com.npic.photoandsignscanner.core.ui.NpicBottomSheet
import com.npic.photoandsignscanner.core.ui.NpicButton
import com.npic.photoandsignscanner.core.ui.NpicButtonSize
import com.npic.photoandsignscanner.core.ui.NpicButtonStyle
import com.npic.photoandsignscanner.domain.model.UpdateManifest

/**
 * m2508: modal bottom sheet that hosts the entire update flow (DESIGN §7.10).
 *
 * Visibility rule: the sheet renders ONLY when [state] is a non-Idle,
 * non-UpToDate variant. The caller in MainActivity subscribes to
 * [UpdateViewModel.state] and unconditionally invokes this composable; the sheet
 * short-circuits internally when nothing needs to be shown. This keeps the wiring
 * in MainActivity to a single line.
 *
 * When [UpdateUiState.blocking] is true (forceUpdate or below minSupportedVersion),
 * the "Later" ghost button is hidden and swipe-to-dismiss is blocked so the user
 * cannot escape without upgrading.
 */
@Composable
fun UpdateSheet(viewModel: UpdateViewModel, activity: Activity) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // m2509 B2: re-check the "Install unknown apps" grant on ON_RESUME so returning
    // from Settings advances state to ReadyToInstall ONLY when the user actually
    // toggled it. Previous version optimistically flipped state before the user
    // returned, creating a confusing loop on denial (both audits flagged BLOCKER).
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onReturnedFromPermissionSettings()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val manifest = when (state) {
        is UpdateUiState.Available -> (state as UpdateUiState.Available).manifest
        is UpdateUiState.Downloading -> (state as UpdateUiState.Downloading).manifest
        is UpdateUiState.Verifying -> (state as UpdateUiState.Verifying).manifest
        is UpdateUiState.ReadyToInstall -> (state as UpdateUiState.ReadyToInstall).manifest
        is UpdateUiState.NeedsInstallPermission -> (state as UpdateUiState.NeedsInstallPermission).manifest
        is UpdateUiState.Installing -> (state as UpdateUiState.Installing).manifest
        is UpdateUiState.Failed -> (state as UpdateUiState.Failed).manifest
        else -> return
    }
    val blocking = when (state) {
        is UpdateUiState.Available -> (state as UpdateUiState.Available).blocking
        is UpdateUiState.Downloading -> (state as UpdateUiState.Downloading).blocking
        is UpdateUiState.Verifying -> (state as UpdateUiState.Verifying).blocking
        is UpdateUiState.ReadyToInstall -> (state as UpdateUiState.ReadyToInstall).blocking
        is UpdateUiState.NeedsInstallPermission -> (state as UpdateUiState.NeedsInstallPermission).blocking
        is UpdateUiState.Installing -> (state as UpdateUiState.Installing).blocking
        is UpdateUiState.Failed -> (state as UpdateUiState.Failed).blocking
        else -> false
    }

    // m2508: build the installer once per composition so ActionRow can source the
    // Special-access intent without doing PackageManager work at click time. Reads
    // LocalContext at composable scope — Compose forbids calling LocalContext.current
    // from a plain lambda body inside a non-composable helper.
    val installer = remember(context) {
        com.npic.photoandsignscanner.data.update.UpdateInstaller(context)
    }

    NpicBottomSheet(
        onDismiss = { if (!blocking) viewModel.dismiss() },
    ) {
        UpdateSheetContent(
            state = state,
            manifest = manifest,
            blocking = blocking,
            isAutoBlockerDevice = installer.isLikelyAutoBlockerDevice(),
            onLater = { viewModel.dismiss() },
            onUpdate = { viewModel.startDownload() },
            onInstall = { viewModel.install(activity) },
            onGrantPermission = {
                // m2509 B2: do NOT call retryInstallAfterPermission() here — the
                // ON_RESUME observer above does it AFTER the user returns AND only
                // when canRequestInstalls() actually flipped to true.
                context.startActivity(installer.unknownSourcesIntent())
            },
            onOpenAutoBlockerSettings = {
                context.startActivity(installer.autoBlockerSettingsIntent())
            },
            onRetry = { viewModel.retry() },
        )
    }
}

@Composable
private fun UpdateSheetContent(
    state: UpdateUiState,
    manifest: UpdateManifest,
    blocking: Boolean,
    isAutoBlockerDevice: Boolean,
    onLater: () -> Unit,
    onUpdate: () -> Unit,
    onInstall: () -> Unit,
    onGrantPermission: () -> Unit,
    onOpenAutoBlockerSettings: () -> Unit,
    onRetry: () -> Unit,
) {
    val chrome = LocalNpicChrome.current

    HeaderRow(
        title = when {
            state is UpdateUiState.Failed -> "Update failed"
            // m2509 M1: signal "must upgrade" clearly instead of the ambiguous
            // "Update available" copy that both audits called out.
            blocking -> "Required update"
            else -> "Update available"
        },
        subtitle = "v${BuildConfig.VERSION_NAME} → v${manifest.versionName}",
    )

    Spacer(Modifier.height(NpicSpacing.md))

    if (manifest.changelog.isNotBlank() && state !is UpdateUiState.Failed) {
        SectionDivider()
        Spacer(Modifier.height(NpicSpacing.md))
        Text(
            text = "What's new",
            style = MaterialTheme.typography.titleSmall,
            color = NpicColors.Ink,
        )
        Spacer(Modifier.height(NpicSpacing.xs))
        Text(
            text = manifest.changelog.trim(),
            style = MaterialTheme.typography.bodyMedium,
            color = NpicColors.Ink,
            // m2509 M2: server-controlled string. Cap render lines so a malicious
            // or accidentally-huge changelog can't push the action row off-screen.
            maxLines = 12,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(NpicSpacing.md))
    }

    SectionDivider()
    Spacer(Modifier.height(NpicSpacing.sm))
    Text(
        text = statusLine(state, manifest),
        style = MaterialTheme.typography.labelMedium,
        color = chrome.inkFaint,
    )
    Spacer(Modifier.height(NpicSpacing.md))

    when (state) {
        is UpdateUiState.Downloading -> DownloadProgressRow(
            fraction = state.progressFraction,
        )
        is UpdateUiState.Verifying -> DownloadProgressRow(
            fraction = null,
            label = "Verifying download…",
        )
        is UpdateUiState.Installing -> DownloadProgressRow(
            fraction = null,
            label = "Installing…",
        )
        else -> Unit
    }

    if (state is UpdateUiState.Failed) {
        Spacer(Modifier.height(NpicSpacing.xs))
        // m2509 M3: pair the reason line with an outlined error icon so it
        // matches the SaveSheet / ExportSheet MissingMediaWarning pattern —
        // color alone isn't a strong enough signal for red-green colorblind
        // users.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = NpicColors.Terracotta,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(NpicSpacing.xs))
            Text(
                text = state.reason,
                style = MaterialTheme.typography.bodyMedium,
                color = NpicColors.Terracotta,
            )
        }
        Spacer(Modifier.height(NpicSpacing.md))
    }

    ActionRow(
        state = state,
        blocking = blocking,
        isAutoBlockerDevice = isAutoBlockerDevice,
        onLater = onLater,
        onUpdate = onUpdate,
        onInstall = onInstall,
        onGrantPermission = onGrantPermission,
        onOpenAutoBlockerSettings = onOpenAutoBlockerSettings,
        onRetry = onRetry,
    )
}

@Composable
private fun HeaderRow(title: String, subtitle: String) {
    val chrome = LocalNpicChrome.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(NpicShapes.sm)
                .background(NpicColors.SaffronSoft, NpicShapes.sm),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Download,
                contentDescription = null,
                tint = NpicColors.SaffronDeep,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.width(NpicSpacing.sm))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = NpicColors.Ink,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = chrome.inkMuted,
            )
        }
    }
}

@Composable
private fun SectionDivider() {
    val chrome = LocalNpicChrome.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(chrome.borderSoft),
    )
}

@Composable
private fun DownloadProgressRow(
    fraction: Float?,
    label: String? = null,
) {
    val chrome = LocalNpicChrome.current
    Column(modifier = Modifier.fillMaxWidth()) {
        if (fraction != null) {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(NpicShapes.full),
                color = NpicColors.Saffron,
                trackColor = chrome.borderSoft,
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    color = NpicColors.Saffron,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(NpicSpacing.sm))
                Text(
                    text = label ?: "Working…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = chrome.inkMuted,
                )
            }
        }
    }
    Spacer(Modifier.height(NpicSpacing.md))
}

@Composable
private fun ActionRow(
    state: UpdateUiState,
    blocking: Boolean,
    isAutoBlockerDevice: Boolean,
    onLater: () -> Unit,
    onUpdate: () -> Unit,
    onInstall: () -> Unit,
    onGrantPermission: () -> Unit,
    onOpenAutoBlockerSettings: () -> Unit,
    onRetry: () -> Unit,
) {
    // m2509 B3: Failed + Samsung → stack a Secondary "Open Auto Blocker" button
    // above the primary "Try again" so the teacher can flip the toggle without
    // hunting through three levels of Settings. Two-line layout only fires on
    // this specific state combination.
    if (state is UpdateUiState.Failed && state.recoverable && isAutoBlockerDevice) {
        NpicButton(
            label = "Open Auto Blocker settings",
            onClick = onOpenAutoBlockerSettings,
            style = NpicButtonStyle.Secondary,
            size = NpicButtonSize.Large,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(NpicSpacing.sm))
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(NpicSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!blocking && state !is UpdateUiState.Installing) {
            NpicButton(
                label = "Later",
                onClick = onLater,
                style = NpicButtonStyle.Ghost,
                size = NpicButtonSize.Large,
                enabled = state !is UpdateUiState.Downloading &&
                        state !is UpdateUiState.Verifying,
            )
        }
        when (state) {
            is UpdateUiState.Available -> NpicButton(
                label = "Update now",
                onClick = onUpdate,
                style = NpicButtonStyle.Primary,
                size = NpicButtonSize.Large,
                modifier = Modifier.weight(1f),
            )
            is UpdateUiState.Downloading, is UpdateUiState.Verifying -> NpicButton(
                label = "Downloading…",
                onClick = {},
                style = NpicButtonStyle.Primary,
                size = NpicButtonSize.Large,
                enabled = false,
                loading = true,
                modifier = Modifier.weight(1f),
            )
            is UpdateUiState.ReadyToInstall -> NpicButton(
                label = "Install",
                onClick = onInstall,
                style = NpicButtonStyle.Primary,
                size = NpicButtonSize.Large,
                modifier = Modifier.weight(1f),
            )
            is UpdateUiState.NeedsInstallPermission -> NpicButton(
                label = "Allow installs",
                // Launches Settings → Special access → Install unknown apps.
                // Grant persists across app updates so the teacher only sees this once
                // (unless they revoke it manually).
                onClick = onGrantPermission,
                style = NpicButtonStyle.Primary,
                size = NpicButtonSize.Large,
                modifier = Modifier.weight(1f),
            )
            is UpdateUiState.Installing -> NpicButton(
                label = "Installing…",
                onClick = {},
                style = NpicButtonStyle.Primary,
                size = NpicButtonSize.Large,
                enabled = false,
                loading = true,
                modifier = Modifier.weight(1f),
            )
            is UpdateUiState.Failed -> if (state.recoverable) {
                NpicButton(
                    label = "Try again",
                    onClick = onRetry,
                    style = NpicButtonStyle.Primary,
                    size = NpicButtonSize.Large,
                    modifier = Modifier.weight(1f),
                )
            }
            else -> Unit
        }
    }
}

private fun statusLine(state: UpdateUiState, manifest: UpdateManifest): String {
    val sizeText = formatMb(manifest.apkSizeBytes)
    val dateText = manifest.releaseDate.take(10)
    return when (state) {
        is UpdateUiState.Available -> if (dateText.isNotBlank()) "$sizeText · Released $dateText"
        else sizeText
        is UpdateUiState.Downloading -> "${formatMb(state.downloadedBytes)} of $sizeText"
        is UpdateUiState.Verifying -> "$sizeText · Verifying"
        is UpdateUiState.ReadyToInstall -> "$sizeText · Ready to install"
        is UpdateUiState.NeedsInstallPermission -> "One-time permission required"
        is UpdateUiState.Installing -> "$sizeText · Installing"
        is UpdateUiState.Failed -> sizeText
        else -> sizeText
    }
}

private fun formatMb(bytes: Long): String {
    if (bytes <= 0) return "—"
    val mb = bytes.toDouble() / (1024.0 * 1024.0)
    return "%.1f MB".format(mb)
}
