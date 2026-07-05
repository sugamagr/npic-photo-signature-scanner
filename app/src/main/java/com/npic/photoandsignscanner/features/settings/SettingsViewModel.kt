package com.npic.photoandsignscanner.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.npic.photoandsignscanner.data.db.NpicDatabase
import com.npic.photoandsignscanner.data.settings.AppSettingsRepository
import com.npic.photoandsignscanner.data.storage.SourceStore
import com.npic.photoandsignscanner.domain.model.AppSettings
import com.npic.photoandsignscanner.domain.model.MotionPreference
import com.npic.photoandsignscanner.domain.repo.DraftRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Owns the Settings drawer state (user m1551 S3).
 *
 * Two responsibilities:
 * 1. Surface the current [AppSettings] as a StateFlow the drawer collects
 * 2. Execute the destructive "Clear all data" wipe as an atomic, ordered pipeline
 *
 * The wipe order matters. Room comes first because it's the source of truth
 * for what's visible in the UI; if the DB clear fails the user still has files
 * on disk that Detail can retry. Then source files. Then cache dirs (drafts +
 * exports). Preferences last so if any earlier stage fails we don't reset the
 * user's toggles under them.
 */
class SettingsViewModel(
    private val settingsRepository: AppSettingsRepository,
    private val database: NpicDatabase,
    private val draftRepository: DraftRepository,
    private val sourceStore: SourceStore,
    private val cacheDir: File,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AppSettings.Default,
        )

    fun setReduceMotion(preference: MotionPreference) {
        viewModelScope.launch { settingsRepository.setReduceMotion(preference) }
    }

    fun setHaptics(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setHaptics(enabled) }
    }

    fun clearAllData(onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                runCatching {
                    database.studentDao().clearAll()
                    draftRepository.clear()
                    sourceStore.deleteAll()
                    deleteCacheSubdir("drafts")
                    deleteCacheSubdir("exports")
                    settingsRepository.clear()
                }.isSuccess
            }
            onDone(success)
        }
    }

    private fun deleteCacheSubdir(name: String) {
        val dir = File(cacheDir, name)
        if (!dir.exists()) return
        val files = dir.listFiles() ?: return
        for (file in files) runCatching { file.delete() }
    }

    class Factory(
        private val settingsRepository: AppSettingsRepository,
        private val database: NpicDatabase,
        private val draftRepository: DraftRepository,
        private val sourceStore: SourceStore,
        private val cacheDir: File,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(
                settingsRepository = settingsRepository,
                database = database,
                draftRepository = draftRepository,
                sourceStore = sourceStore,
                cacheDir = cacheDir,
            ) as T
        }
    }
}
