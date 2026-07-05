package com.npic.photoandsignscanner.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.npic.photoandsignscanner.domain.model.AppSettings
import com.npic.photoandsignscanner.domain.model.MotionPreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Top-level Context extension per androidx.datastore convention — creates a
// single-instance store per process keyed by name. Placed at file scope
// (outside the class) because `preferencesDataStore` is a property delegate
// that must be initialized exactly once per name; the class holds a reference
// to the resulting store via `context.appSettingsDataStore`.
private val Context.appSettingsDataStore by preferencesDataStore(name = "npic_settings")

/**
 * DataStore-backed persistence for [AppSettings].
 *
 * Anchored to user directive m1551 S3. DataStore Preferences (not
 * SharedPreferences) because:
 *   - Type-safe key accessors instead of stringly-typed get/put pairs
 *   - Coroutine-first API that composes with our Flow-based UI wiring
 *   - Atomic transactions via `edit { }` (SharedPreferences.apply is fire-and-forget)
 *   - Migration path if we ever need proto-store for structured settings
 *
 * The [settings] Flow is safe to collect on the main thread; DataStore
 * handles disk I/O on its own dispatcher. Setters are suspend and must be
 * called from a coroutine — typically ViewModel scope.
 */
class AppSettingsRepository(private val context: Context) {

    private object Keys {
        val ReduceMotion = stringPreferencesKey("reduce_motion_override")
        val Haptics = booleanPreferencesKey("haptics_enabled")
        // m2175: Export MIME override removed from the drawer. Any pre-existing
        // "export_mime" key in DataStore is silently ignored on read (the value
        // was written by an older build; nothing looks it up anymore).
    }

    val settings: Flow<AppSettings> = context.appSettingsDataStore.data.map { prefs ->
        prefs.toAppSettings()
    }

    suspend fun setReduceMotion(preference: MotionPreference) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.ReduceMotion] = preference.name
        }
    }

    suspend fun setHaptics(enabled: Boolean) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.Haptics] = enabled
        }
    }

    /**
     * Clears every persisted preference back to [AppSettings.Default].
     *
     * Used by the Settings drawer's "Clear all data" affordance AFTER the
     * caller has wiped Room + filesDir/sources + cache dirs. Order matters:
     * the settings wipe happens last so if the destructive Room wipe fails
     * the user still has their preferences intact for the retry.
     */
    suspend fun clear() {
        context.appSettingsDataStore.edit { it.clear() }
    }

    private fun Preferences.toAppSettings(): AppSettings {
        val motion = this[Keys.ReduceMotion]?.let { name ->
            runCatching { MotionPreference.valueOf(name) }.getOrNull()
        } ?: MotionPreference.System
        val haptics = this[Keys.Haptics] ?: true
        return AppSettings(
            reduceMotionOverride = motion,
            hapticsEnabled = haptics,
        )
    }
}
