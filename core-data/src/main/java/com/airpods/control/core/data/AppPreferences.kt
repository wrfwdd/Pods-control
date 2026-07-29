package com.airpods.control.core.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("airpods_prefs")

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {

    // ---- Popup behavior ----
    val popupEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_POPUP_ENABLED] ?: true }
    val popupAutoDismissMs: Flow<Long> = context.dataStore.data.map { it[KEY_POPUP_DISMISS_MS] ?: 4000L }
    val popupLockScreenOnly: Flow<Boolean> = context.dataStore.data.map { it[KEY_POPUP_LOCKSCREEN] ?: false }

    // ---- Theme ----
    val darkTheme: Flow<Int> = context.dataStore.data.map { it[KEY_DARK_THEME] ?: 0 } // 0=system 1=light 2=dark
    val accentColor: Flow<Long> = context.dataStore.data.map { it[KEY_ACCENT_COLOR] ?: 0xFF0A84FF }

    // ---- Protocol ----
    val protocolMode: Flow<Int> = context.dataStore.data.map { it[KEY_PROTOCOL_MODE] ?: 0 } // 0=auto 1=aacp 2=btOnly

    suspend fun setPopupEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_POPUP_ENABLED] = enabled }
    }
    suspend fun setPopupAutoDismissMs(ms: Long) {
        context.dataStore.edit { it[KEY_POPUP_DISMISS_MS] = ms }
    }
    suspend fun setDarkTheme(mode: Int) {
        context.dataStore.edit { it[KEY_DARK_THEME] = mode }
    }
    suspend fun setAccentColor(color: Long) {
        context.dataStore.edit { it[KEY_ACCENT_COLOR] = color }
    }
    suspend fun setProtocolMode(mode: Int) {
        context.dataStore.edit { it[KEY_PROTOCOL_MODE] = mode }
    }

    companion object {
        private val KEY_POPUP_ENABLED = booleanPreferencesKey("popup_enabled")
        private val KEY_POPUP_DISMISS_MS = longPreferencesKey("popup_dismiss_ms")
        private val KEY_POPUP_LOCKSCREEN = booleanPreferencesKey("popup_lockscreen_only")
        private val KEY_DARK_THEME = intPreferencesKey("dark_theme")
        private val KEY_ACCENT_COLOR = longPreferencesKey("accent_color")
        private val KEY_PROTOCOL_MODE = intPreferencesKey("protocol_mode")
    }
}

