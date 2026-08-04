package com.futureclock.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private val ds get() = context.dataStore

    private val keyUse24h = booleanPreferencesKey("use_24h")
    private val keyShowSeconds = booleanPreferencesKey("show_seconds")
    private val keySnoozeMin = intPreferencesKey("snooze_minutes")
    private val keyThemeMode = intPreferencesKey("theme_mode")
    private val keyGradualVol = booleanPreferencesKey("gradual_volume")
    private val keyLastTab = intPreferencesKey("last_tab")
    private val keyCustomZoneEnabled = booleanPreferencesKey("custom_zone_enabled")
    private val keyCustomZoneName = stringPreferencesKey("custom_zone_name")
    private val keyCustomZoneOffsetMinutes = intPreferencesKey("custom_zone_offset_minutes")

    val use24h: Flow<Boolean> = ds.data.catch { emit(emptyPreferences()) }.map { it[keyUse24h] ?: true }
    val showSeconds: Flow<Boolean> = ds.data.catch { emit(emptyPreferences()) }.map { it[keyShowSeconds] ?: true }
    val snoozeMinutes: Flow<Int> = ds.data.catch { emit(emptyPreferences()) }.map { it[keySnoozeMin] ?: 5 }
    val themeMode: Flow<Int> = ds.data.catch { emit(emptyPreferences()) }.map { it[keyThemeMode] ?: 0 } // 0=system, 1=light, 2=dark
    val gradualVolume: Flow<Boolean> = ds.data.catch { emit(emptyPreferences()) }.map { it[keyGradualVol] ?: true }
    val lastTab: Flow<Int> = ds.data.catch { emit(emptyPreferences()) }.map { it[keyLastTab] ?: 0 }
    val customZoneEnabled: Flow<Boolean> = ds.data.catch { emit(emptyPreferences()) }.map { it[keyCustomZoneEnabled] ?: false }
    val customZoneName: Flow<String> = ds.data.catch { emit(emptyPreferences()) }.map { it[keyCustomZoneName] ?: "Custom time" }
    val customZoneOffsetMinutes: Flow<Int> = ds.data.catch { emit(emptyPreferences()) }.map { it[keyCustomZoneOffsetMinutes] ?: 0 }

    suspend fun setUse24h(v: Boolean) {
        ds.edit { it[keyUse24h] = v }
        com.futureclock.app.widget.WidgetUpdateScheduler.refreshAll(context)
    }
    suspend fun setShowSeconds(v: Boolean) = ds.edit { it[keyShowSeconds] = v }
    suspend fun setSnoozeMinutes(v: Int) = ds.edit { it[keySnoozeMin] = v }
    suspend fun setThemeMode(v: Int) = ds.edit { it[keyThemeMode] = v }
    suspend fun setGradualVolume(v: Boolean) = ds.edit { it[keyGradualVol] = v }
    suspend fun setLastTab(v: Int) = ds.edit { it[keyLastTab] = v }

    suspend fun setCustomZone(enabled: Boolean, name: String, offsetMinutes: Int) = ds.edit {
        it[keyCustomZoneEnabled] = enabled
        it[keyCustomZoneName] = name.trim().ifEmpty { "Custom time" }
        it[keyCustomZoneOffsetMinutes] = offsetMinutes.coerceIn(-12 * 60, 14 * 60)
    }
}
