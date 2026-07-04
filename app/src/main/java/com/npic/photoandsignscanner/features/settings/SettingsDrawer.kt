package com.npic.photoandsignscanner.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.SettingsSuggest
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.npic.photoandsignscanner.core.theme.LocalNpicChrome
import com.npic.photoandsignscanner.core.theme.NpicColors
import com.npic.photoandsignscanner.core.theme.NpicShapes
import com.npic.photoandsignscanner.core.theme.NpicSpacing
import com.npic.photoandsignscanner.core.ui.NpicButton
import com.npic.photoandsignscanner.core.ui.NpicButtonSize
import com.npic.photoandsignscanner.core.ui.NpicButtonStyle
import com.npic.photoandsignscanner.core.ui.NpicSegmentedControl
import com.npic.photoandsignscanner.domain.model.AppSettings
import com.npic.photoandsignscanner.domain.model.ExportMime
import com.npic.photoandsignscanner.domain.model.MotionPreference

/**
 * Hamburger drawer content per user directive m1551 S3. Rendered inside a
 * [ModalDrawerSheet] hosted at the MainActivity level so any destination that
 * asks for it can open the same instance.
 *
 * Sections:
 *   1. Header — brand mark + "Settings" title + subtitle
 *   2. Motion  — 3-way override (System / On / Off)
 *   3. Haptics — labeled Switch
 *   4. Export  — 2-way MIME preference (Auto / JPEG only)
 *   5. Data    — destructive "Clear all data" with confirm dialog
 *   6. Footer  — app version + tagline
 *
 * All animation on this surface respects [LocalReduceMotion] via the design-system
 * primitives; nothing here rolls its own tween.
 */
@Composable
fun SettingsDrawer(
    viewModel: SettingsViewModel,
    appVersion: String,
    onDismiss: () -> Unit,
    onClearAllDone: (Boolean) -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    ModalDrawerSheet(
        drawerContainerColor = NpicColors.Ivory,
        drawerContentColor   = NpicColors.Ink,
        drawerShape          = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
        modifier             = Modifier.width(320.dp),
    ) {
        SettingsDrawerContent(
            settings         = settings,
            appVersion       = appVersion,
            onSetMotion      = viewModel::setReduceMotion,
            onSetHaptics     = viewModel::setHaptics,
            onSetExportMime  = viewModel::setExportMime,
            onClearAll       = { viewModel.clearAllData(onClearAllDone) },
            onDismiss        = onDismiss,
        )
    }
}

@Composable
private fun SettingsDrawerContent(
    settings: AppSettings,
    appVersion: String,
    onSetMotion: (MotionPreference) -> Unit,
    onSetHaptics: (Boolean) -> Unit,
    onSetExportMime: (ExportMime) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    var showClearConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = NpicSpacing.lg)
            .padding(top = NpicSpacing.lg, bottom = NpicSpacing.xl),
    ) {
        SettingsHeader()
        Spacer(Modifier.height(NpicSpacing.xl))

        SettingsSection(title = "Motion") {
            Text(
                text  = "Override the system animation setting when you want the app tuned differently from everything else.",
                color = LocalNpicChrome.current.inkMuted,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(NpicSpacing.sm))
            NpicSegmentedControl(
                options  = listOf(MotionPreference.System, MotionPreference.Off, MotionPreference.On),
                selected = settings.reduceMotionOverride,
                onSelect = onSetMotion,
                labelOf  = ::motionLabel,
            )
        }

        Spacer(Modifier.height(NpicSpacing.xl))

        SettingsSection(title = "Haptics") {
            HapticsRow(
                enabled  = settings.hapticsEnabled,
                onToggle = onSetHaptics,
            )
        }

        Spacer(Modifier.height(NpicSpacing.xl))

        SettingsSection(title = "Export MIME") {
            Text(
                text  = "Auto lets receiver apps pick. JPEG only forces image/jpeg for single-photo shares — helps with picky share targets.",
                color = LocalNpicChrome.current.inkMuted,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(NpicSpacing.sm))
            NpicSegmentedControl(
                options  = listOf(ExportMime.Auto, ExportMime.JpegOnly),
                selected = settings.exportMimePreference,
                onSelect = onSetExportMime,
                labelOf  = ::mimeLabel,
            )
        }

        Spacer(Modifier.height(NpicSpacing.xl))

        SettingsSection(title = "Data") {
            Text(
                text  = "Delete every saved record, draft, source file, and cached export. Cannot be undone.",
                color = LocalNpicChrome.current.inkMuted,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(NpicSpacing.sm))
            NpicButton(
                label     = "Clear all data",
                onClick   = { showClearConfirm = true },
                style     = NpicButtonStyle.Destructive,
                size      = NpicButtonSize.Small,
                startIcon = Icons.Outlined.DeleteSweep,
                modifier  = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(NpicSpacing.xxxl))

        SettingsFooter(appVersion = appVersion)

        Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars).height(0.dp))
    }

    if (showClearConfirm) {
        ClearAllConfirmDialog(
            onKeep  = { showClearConfirm = false },
            onClear = {
                showClearConfirm = false
                onClearAll()
                onDismiss()
            },
        )
    }
}

@Composable
private fun SettingsHeader() {
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
                imageVector        = Icons.Outlined.SettingsSuggest,
                contentDescription = null,
                tint               = NpicColors.SaffronDeep,
                modifier           = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.width(NpicSpacing.sm))
        Column {
            Text(
                text  = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                color = NpicColors.Ink,
            )
            Text(
                text  = "Preferences and data",
                style = MaterialTheme.typography.bodyMedium,
                color = chrome.inkMuted,
            )
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text  = title.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight(600),
                letterSpacing = 0.8.sp,
            ),
            color = LocalNpicChrome.current.inkFaint,
        )
        Spacer(Modifier.height(NpicSpacing.sm))
        content()
    }
}

@Composable
private fun HapticsRow(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val chrome = LocalNpicChrome.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(NpicShapes.sm)
            .background(NpicColors.Surface, NpicShapes.sm)
            .border(1.dp, chrome.borderSoft, NpicShapes.sm)
            .semantics { role = Role.Switch }
            .clickable { onToggle(!enabled) }
            .padding(horizontal = NpicSpacing.md, vertical = NpicSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text  = "Haptic feedback",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight(600)),
                color = NpicColors.Ink,
            )
            Text(
                text  = if (enabled) "On for shutter, save, and delete" else "Off",
                style = MaterialTheme.typography.bodySmall,
                color = chrome.inkMuted,
            )
        }
        Spacer(Modifier.width(NpicSpacing.sm))
        Switch(
            checked = enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor    = NpicColors.Ivory,
                checkedTrackColor    = NpicColors.Saffron,
                checkedBorderColor   = NpicColors.SaffronDeep,
                uncheckedThumbColor  = NpicColors.Surface,
                uncheckedTrackColor  = chrome.borderSoft,
                uncheckedBorderColor = chrome.borderStrong,
            ),
        )
    }
}

@Composable
private fun SettingsFooter(appVersion: String) {
    val chrome = LocalNpicChrome.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(chrome.borderSoft),
        )
        Spacer(Modifier.height(NpicSpacing.md))
        Text(
            text  = "Version $appVersion",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight(600)),
            color = NpicColors.Ink,
        )
        Text(
            text  = "Built for UP Board schools",
            style = MaterialTheme.typography.bodySmall,
            color = chrome.inkMuted,
        )
    }
}

@Composable
private fun ClearAllConfirmDialog(
    onKeep: () -> Unit,
    onClear: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onKeep,
        containerColor   = NpicColors.SurfaceRaised,
        titleContentColor = NpicColors.Ink,
        textContentColor  = LocalNpicChrome.current.inkMuted,
        title = {
            Text(
                text  = "Clear all data?",
                style = MaterialTheme.typography.headlineMedium,
            )
        },
        text = {
            Text(
                text  = "This deletes every saved student, draft, source file, and cached export from this device. Portal filenames stay unaffected but you'll lose the ability to re-share anything already saved. This can't be undone.",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            NpicButton(
                label     = "Clear everything",
                onClick   = onClear,
                style     = NpicButtonStyle.Destructive,
                size      = NpicButtonSize.Small,
                startIcon = Icons.Outlined.DeleteSweep,
            )
        },
        dismissButton = {
            NpicButton(
                label   = "Keep",
                onClick = onKeep,
                style   = NpicButtonStyle.Ghost,
                size    = NpicButtonSize.Small,
            )
        },
    )
}

private fun motionLabel(preference: MotionPreference): String = when (preference) {
    MotionPreference.System -> "System"
    MotionPreference.Off    -> "Full"
    MotionPreference.On     -> "Reduce"
}

private fun mimeLabel(preference: ExportMime): String = when (preference) {
    ExportMime.Auto     -> "Auto"
    ExportMime.JpegOnly -> "JPEG only"
}
