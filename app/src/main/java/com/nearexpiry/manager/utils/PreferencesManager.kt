package com.nearexpiry.manager.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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

    val scanSoundFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[scanSoundKey] ?: true
    }

    val vibrationFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[vibrationKey] ?: true
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
