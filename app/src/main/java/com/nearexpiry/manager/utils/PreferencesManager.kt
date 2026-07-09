package com.nearexpiry.manager.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class PreferencesManager @Inject constructor(@ApplicationContext private val context: Context) {

    private val scanSoundKey = booleanPreferencesKey("scan_sound")
    private val vibrationKey = booleanPreferencesKey("vibration")
    private val languagePromptShownKey = booleanPreferencesKey("language_prompt_shown")
    private val activeProjectIdKey = longPreferencesKey("active_project_id")
    private val lastUpdateCheckKey = longPreferencesKey("last_update_check")

    // Remembers the last expiry date picked on the Scan screen, and the day it
    // was saved, so a batch of same-expiry items pre-fills — but only for the
    // same calendar day (first scan of a new day defaults to today).
    private val lastExpiryDateKey = stringPreferencesKey("last_expiry_date")        // "yyyy-MM-dd"
    private val lastExpirySavedDayKey = stringPreferencesKey("last_expiry_saved_day") // "yyyy-MM-dd"
    private val lastBranchIdKey = stringPreferencesKey("last_branch_id")
    // Sticky scan mode: after 2 consecutive uses of one mode, the Scan screen
    // opens in that mode by default.
    private val scanModeDefaultKey = stringPreferencesKey("scan_mode_default")   // "camera" | "manual"
    private val scanModeLastKey = stringPreferencesKey("scan_mode_last")
    private val scanModeStreakKey = longPreferencesKey("scan_mode_streak")

    /** "camera" (default) or "manual" — how the Scan screen should open. */
    suspend fun getScanModeDefault(): String =
        context.dataStore.data.first()[scanModeDefaultKey] ?: "camera"

    /**
     * Records one completed entry in [mode] ("camera"/"manual"). Two consecutive
     * uses of the same mode make it the new default. Returns the current default.
     */
    suspend fun recordScanModeUse(mode: String): String {
        val prefs = context.dataStore.data.first()
        val last = prefs[scanModeLastKey]
        val streak = if (last == mode) (prefs[scanModeStreakKey] ?: 0L) + 1 else 1L
        val newDefault = if (streak >= 2) mode else (prefs[scanModeDefaultKey] ?: "camera")
        context.dataStore.edit {
            it[scanModeLastKey] = mode
            it[scanModeStreakKey] = streak
            it[scanModeDefaultKey] = newDefault
        }
        return newDefault
    }

    suspend fun getLastBranchId(): String =
        context.dataStore.data.first()[lastBranchIdKey] ?: ""

    suspend fun setLastBranchId(id: String) {
        context.dataStore.edit { it[lastBranchIdKey] = id }
    }

    suspend fun getLastExpiryForToday(todayIso: String): String? {
        val prefs = context.dataStore.data.first()
        val savedDay = prefs[lastExpirySavedDayKey]
        return if (savedDay == todayIso) prefs[lastExpiryDateKey] else null
    }

    suspend fun setLastExpiry(expiryIso: String, todayIso: String) {
        context.dataStore.edit {
            it[lastExpiryDateKey] = expiryIso
            it[lastExpirySavedDayKey] = todayIso
        }
    }

    suspend fun getLastUpdateCheck(): Long =
        context.dataStore.data.first()[lastUpdateCheckKey] ?: 0L

    suspend fun setLastUpdateCheck(timestamp: Long) {
        context.dataStore.edit { it[lastUpdateCheckKey] = timestamp }
    }

    /** The currently selected project. Defaults to 1 ("Project 1"). */
    val activeProjectIdFlow: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[activeProjectIdKey] ?: 1L
    }

    suspend fun setActiveProjectId(id: Long) {
        context.dataStore.edit { prefs ->
            prefs[activeProjectIdKey] = id
        }
    }

    fun getActiveProjectId(): Long = runCatching {
        runBlocking { activeProjectIdFlow.first() }
    }.getOrDefault(1L)

    val scanSoundFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[scanSoundKey] ?: true
    }

    val vibrationFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[vibrationKey] ?: true
    }

    /** True once the user has been shown the first-launch language picker. */
    val languagePromptShownFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[languagePromptShownKey] ?: false
    }

    suspend fun setLanguagePromptShown() {
        context.dataStore.edit { prefs ->
            prefs[languagePromptShownKey] = true
        }
    }

    suspend fun setScanSound(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[scanSoundKey] = enabled
        }
    }

    suspend fun setVibration(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[vibrationKey] = enabled
        }
    }

    /**
     * Synchronous helpers used by the ViewModel.
     * DataStore keeps its state in memory after the first load, so these
     * runBlocking calls are effectively instant after app start.
     */
    fun isScanSoundEnabled(): Boolean = runCatching {
        runBlocking { scanSoundFlow.first() }
    }.getOrDefault(true)

    fun isVibrationEnabled(): Boolean = runCatching {
        runBlocking { vibrationFlow.first() }
    }.getOrDefault(true)
}
