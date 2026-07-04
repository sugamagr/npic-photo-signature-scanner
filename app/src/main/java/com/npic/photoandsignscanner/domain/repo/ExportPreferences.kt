package com.npic.photoandsignscanner.domain.repo

import com.npic.photoandsignscanner.domain.model.ExportFormat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Session-scoped memory of the user's last-picked export format (DESIGN §7.8 "Selection
 * state persists per-session; forget on cold start"). Singleton `object` — resets on
 * process death, no DataStore persistence intended.
 */
object ExportPreferences {

    private val _lastFormat = MutableStateFlow(ExportFormat.Combined)
    val lastFormat: StateFlow<ExportFormat> = _lastFormat

    fun remember(format: ExportFormat) { _lastFormat.value = format }
}
