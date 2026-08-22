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
    private val lastNotifiedUpdateVersionCodeKey = longPreferencesKey("last_notified_update_version_code")

    // Remembers the last expiry date picked on the Scan screen, and the day it
    // was saved, so a batch of same-expiry items pre-fills — but only for the
    // same calendar day (first scan of a new day defaults to today).
    private val lastExpiryDateKey = stringPreferencesKey("last_expiry_date")        // "yyyy-MM-dd"
    private val lastExpirySavedDayKey = stringPreferencesKey("last_expiry_saved_day") // "yyyy-MM-dd"
    // Global expiry-date shortcut: after five consecutive explicit selections
    // of the same date, future scans reuse it without opening the date picker.
    private val expiryDateStreakDateKey = stringPreferencesKey("expiry_date_streak_date")
    private val expiryDateStreakCountKey = longPreferencesKey("expiry_date_streak_count")
    private val automaticExpiryDateKey = stringPreferencesKey("automatic_expiry_date")
    private val lastBranchIdKey = stringPreferencesKey("last_branch_id")
    // The actual Recheck codes live in Room; preferences store only UI metadata.
    private val recheckFileNameKey = stringPreferencesKey("recheck_excel_file_name")
    private val recheckCodeCountKey = longPreferencesKey("recheck_excel_code_count")
    // Sticky scan mode: after 2 consecutive uses of one mode, the Scan screen
    // opens in that mode by default.
    private val scanModeDefaultKey = stringPreferencesKey("scan_mode_default")   // "camera" | "manual"
    private val scanModeLastKey = stringPreferencesKey("scan_mode_last")
    private val scanModeStreakKey = longPreferencesKey("scan_mode_streak")
    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val themePromptShownKey = booleanPreferencesKey("theme_prompt_shown")
    // Set only while a new installation moves from theme selection to its
    // optional Google Drive setup choice. Existing installations do not see it.
    private val googleDriveOnboardingPendingKey = booleanPreferencesKey("google_drive_onboarding_pending")
    // Optional user-owned Google Drive backup state. OAuth tokens stay with
    // Google Play services; DataStore retains only display and preference data.
    private val googleDriveAccountEmailKey = stringPreferencesKey("google_drive_account_email")
    private val googleDriveBackupEnabledKey = booleanPreferencesKey("google_drive_backup_enabled")
    private val googleDrivePendingBackupNameKey = stringPreferencesKey("google_drive_pending_backup_name")
    private val googleDriveLastUploadErrorKey = stringPreferencesKey("google_drive_last_upload_error")
    private val googleDriveConsentRequiredKey = booleanPreferencesKey("google_drive_consent_required")
    private val googleDriveLastSuccessNameKey = stringPreferencesKey("google_drive_last_success_name")
    private val googleDriveLastSuccessTimeKey = longPreferencesKey("google_drive_last_success_time")

    /** "dark" (default), "light", or "system" — app appearance mode. */
    val themeModeFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[themeModeKey] ?: "dark"
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { it[themeModeKey] = mode }
    }

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

    // Sticky flashlight: after 2 consecutive camera scans with the torch in the
    // same state (on/off), that state becomes the default for future scans.
    private val torchDefaultKey = booleanPreferencesKey("torch_default")
    private val torchLastKey = booleanPreferencesKey("torch_last")
    private val torchStreakKey = longPreferencesKey("torch_streak")

    /** Whether the camera torch should start enabled. */
    suspend fun getTorchDefault(): Boolean =
        context.dataStore.data.first()[torchDefaultKey] ?: false

    /** Records the torch state of one completed camera scan. */
    suspend fun recordTorchUse(on: Boolean) {
        val prefs = context.dataStore.data.first()
        val last = prefs[torchLastKey]
        val streak = if (last == on) (prefs[torchStreakKey] ?: 0L) + 1 else 1L
        val newDefault = if (streak >= 2) on else (prefs[torchDefaultKey] ?: false)
        context.dataStore.edit {
            it[torchLastKey] = on
            it[torchStreakKey] = streak
            it[torchDefaultKey] = newDefault
        }
    }

    suspend fun getLastBranchId(): String =
        context.dataStore.data.first()[lastBranchIdKey] ?: ""

    suspend fun setLastBranchId(id: String) {
        context.dataStore.edit { it[lastBranchIdKey] = id }
    }

    suspend fun getRecheckFileName(): String =
        context.dataStore.data.first()[recheckFileNameKey] ?: ""

    suspend fun getRecheckCodeCount(): Int =
        (context.dataStore.data.first()[recheckCodeCountKey] ?: 0L).toInt()

    suspend fun setRecheckFileMetadata(fileName: String, codeCount: Int) {
        context.dataStore.edit {
            it[recheckFileNameKey] = fileName
            it[recheckCodeCountKey] = codeCount.toLong()
        }
    }

    suspend fun clearRecheckFileMetadata() {
        context.dataStore.edit {
            it.remove(recheckFileNameKey)
            it.remove(recheckCodeCountKey)
        }
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

    /**
     * Records one explicit date-picker selection. Five consecutive selections
     * of the same value activate the global automatic-date shortcut.
     */
    suspend fun recordExpiryDateSelection(expiryIso: String) {
        context.dataStore.edit { prefs ->
            val next = ExpiryDateShortcut.recordSelection(
                previousDate = prefs[expiryDateStreakDateKey],
                previousCount = prefs[expiryDateStreakCountKey] ?: 0L,
                selectedDate = expiryIso
            )
            prefs[expiryDateStreakDateKey] = next.selectedDate
            prefs[expiryDateStreakCountKey] = next.consecutiveSelections
            if (next.automaticDate != null) {
                prefs[automaticExpiryDateKey] = next.automaticDate
            } else {
                prefs.remove(automaticExpiryDateKey)
            }
        }
    }

    /** Returns the date that may be applied without showing the picker, if any. */
    suspend fun getAutomaticExpiryDate(): String? =
        context.dataStore.data.first()[automaticExpiryDateKey]

    /**
     * Clears the automatic-date shortcut and its streak. A manual item-detail
     * date edit starts a new five-selection sequence.
     */
    suspend fun resetExpiryDateShortcut() {
        context.dataStore.edit { prefs ->
            prefs.remove(expiryDateStreakDateKey)
            prefs.remove(expiryDateStreakCountKey)
            prefs.remove(automaticExpiryDateKey)
        }
    }

    suspend fun getLastUpdateCheck(): Long =
        context.dataStore.data.first()[lastUpdateCheckKey] ?: 0L

    suspend fun setLastUpdateCheck(timestamp: Long) {
        context.dataStore.edit { it[lastUpdateCheckKey] = timestamp }
    }

    /**
     * The versionCode the "Update Available" notification was posted for, or
     * 0 if none is currently outstanding. Used to detect — cheaply, with no
     * network call — that the notification is now stale because the app has
     * since been updated to that version or newer, so it can be cancelled
     * the moment the app is opened rather than waiting for the next
     * (throttled, once-a-day) network version check.
     */
    suspend fun getLastNotifiedUpdateVersionCode(): Long =
        context.dataStore.data.first()[lastNotifiedUpdateVersionCodeKey] ?: 0L

    suspend fun setLastNotifiedUpdateVersionCode(versionCode: Long) {
        context.dataStore.edit { it[lastNotifiedUpdateVersionCodeKey] = versionCode }
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

    /** True once the user has selected an appearance during first startup. */
    val themePromptShownFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[themePromptShownKey] ?: false
    }

    suspend fun setThemePromptShown() {
        context.dataStore.edit { prefs ->
            prefs[themePromptShownKey] = true
        }
    }

    suspend fun isGoogleDriveOnboardingPending(): Boolean =
        context.dataStore.data.first()[googleDriveOnboardingPendingKey] ?: false

    suspend fun beginGoogleDriveOnboarding() {
        context.dataStore.edit { prefs -> prefs[googleDriveOnboardingPendingKey] = true }
    }

    suspend fun completeGoogleDriveOnboarding() {
        context.dataStore.edit { prefs -> prefs.remove(googleDriveOnboardingPendingKey) }
    }

    suspend fun getGoogleDriveAccountEmail(): String =
        context.dataStore.data.first()[googleDriveAccountEmailKey] ?: ""

    suspend fun setGoogleDriveAccountEmail(email: String) {
        context.dataStore.edit { prefs -> prefs[googleDriveAccountEmailKey] = email }
    }

    suspend fun isGoogleDriveBackupEnabled(): Boolean =
        context.dataStore.data.first()[googleDriveBackupEnabledKey] ?: false

    suspend fun setGoogleDriveBackupEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[googleDriveBackupEnabledKey] = enabled }
    }

    suspend fun getGoogleDrivePendingBackupName(): String =
        context.dataStore.data.first()[googleDrivePendingBackupNameKey] ?: ""

    suspend fun setGoogleDrivePendingBackupName(name: String) {
        context.dataStore.edit { prefs -> prefs[googleDrivePendingBackupNameKey] = name }
    }

    suspend fun clearGoogleDrivePendingBackupIfMatches(name: String) {
        context.dataStore.edit { prefs ->
            if (prefs[googleDrivePendingBackupNameKey] == name) {
                prefs.remove(googleDrivePendingBackupNameKey)
            }
        }
    }

    suspend fun clearGoogleDrivePendingBackup() {
        context.dataStore.edit { prefs -> prefs.remove(googleDrivePendingBackupNameKey) }
    }

    suspend fun getGoogleDriveLastUploadError(): String =
        context.dataStore.data.first()[googleDriveLastUploadErrorKey] ?: ""

    suspend fun setGoogleDriveLastUploadError(error: String) {
        context.dataStore.edit { prefs -> prefs[googleDriveLastUploadErrorKey] = error }
    }

    suspend fun clearGoogleDriveLastUploadError() {
        context.dataStore.edit { prefs -> prefs.remove(googleDriveLastUploadErrorKey) }
    }

    suspend fun isGoogleDriveConsentRequired(): Boolean =
        context.dataStore.data.first()[googleDriveConsentRequiredKey] ?: false

    suspend fun setGoogleDriveConsentRequired(required: Boolean) {
        context.dataStore.edit { prefs ->
            if (required) prefs[googleDriveConsentRequiredKey] = true
            else prefs.remove(googleDriveConsentRequiredKey)
        }
    }

    suspend fun setGoogleDriveLastSuccess(backupName: String, completedAtMillis: Long) {
        context.dataStore.edit { prefs ->
            prefs[googleDriveLastSuccessNameKey] = backupName
            prefs[googleDriveLastSuccessTimeKey] = completedAtMillis
        }
    }

    suspend fun getGoogleDriveLastSuccessName(): String =
        context.dataStore.data.first()[googleDriveLastSuccessNameKey] ?: ""

    suspend fun getGoogleDriveLastSuccessTime(): Long =
        context.dataStore.data.first()[googleDriveLastSuccessTimeKey] ?: 0L

    suspend fun clearGoogleDriveBackupSettings() {
        context.dataStore.edit { prefs ->
            prefs.remove(googleDriveAccountEmailKey)
            prefs.remove(googleDriveBackupEnabledKey)
            prefs.remove(googleDrivePendingBackupNameKey)
            prefs.remove(googleDriveLastUploadErrorKey)
            prefs.remove(googleDriveConsentRequiredKey)
            prefs.remove(googleDriveLastSuccessNameKey)
            prefs.remove(googleDriveLastSuccessTimeKey)
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
