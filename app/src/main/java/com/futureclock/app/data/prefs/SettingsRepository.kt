package com.futureclock.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
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

    val use24h: Flow<Boolean> = ds.data.map { it[keyUse24h] ?: true }
    val showSeconds: Flow<Boolean> = ds.data.map { it[keyShowSeconds] ?: true }
    val snoozeMinutes: Flow<Int> = ds.data.map { it[keySnoozeMin] ?: 5 }
    val themeMode: Flow<Int> = ds.data.map { it[keyThemeMode] ?: 0 } // 0=system, 1=light, 2=dark
    val gradualVolume: Flow<Boolean> = ds.data.map { it[keyGradualVol] ?: true }
    val lastTab: Flow<Int> = ds.data.map { it[keyLastTab] ?: 0 }

    suspend fun setUse24h(v: Boolean) = ds.edit { it[keyUse24h] = v }
    suspend fun setShowSeconds(v: Boolean) = ds.edit { it[keyShowSeconds] = v }
    suspend fun setSnoozeMinutes(v: Int) = ds.edit { it[keySnoozeMin] = v }
    suspend fun setThemeMode(v: Int) = ds.edit { it[keyThemeMode] = v }
    suspend fun setGradualVolume(v: Boolean) = ds.edit { it[keyGradualVol] = v }
    suspend fun setLastTab(v: Int) = ds.edit { it[keyLastTab] = v }
}
