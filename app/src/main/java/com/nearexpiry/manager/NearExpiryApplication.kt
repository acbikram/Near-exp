package com.nearexpiry.manager

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.nearexpiry.manager.notifications.ExpiryNotificationWorker
import com.nearexpiry.manager.notifications.NotificationHelper
import com.nearexpiry.manager.notifications.AutoBackupWorker
import com.nearexpiry.manager.notifications.UpdateCheckWorker
import com.nearexpiry.manager.domain.repository.ExpiryRepository
import com.nearexpiry.manager.utils.ActiveProjectManager
import com.nearexpiry.manager.utils.AppUpdater
import com.nearexpiry.manager.utils.PreferencesManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class NearExpiryApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var activeProjectManager: ActiveProjectManager

    @Inject
    lateinit var preferencesManager: PreferencesManager

    @Inject
    lateinit var expiryRepository: ExpiryRepository

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // These launch conveniences must never block the app UI on OEM Android
        // builds that reject a notification or WorkManager request at startup.
        runCatching { NotificationHelper.createChannels(this) }
        runCatching { ExpiryNotificationWorker.schedule(this) }
        runCatching { UpdateCheckWorker.schedule(this) }
        runCatching { AutoBackupWorker.schedule(this) }
        // Delete update APKs already installed (version <= current). APKs for a
        // newer, downloaded-but-not-yet-installed version are kept so the user
        // can tap "Install" without re-downloading. This approximates
        // "delete after install": if they installed it, this launch is that new
        // version, so the matching APK is now same-version → removed.
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                AppUpdater.cleanupInstalledApks(this@NearExpiryApplication, BuildConfig.VERSION_NAME)
            }
        }
        // If the previously-active project was deleted, fall back to a valid one.
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { activeProjectManager.ensureValidActiveProject() }
            // Recycle bin: drop entries older than 30 days.
            runCatching { expiryRepository.purgeOldBinEntries(30) }
        }
        // Cheap, local, unthrottled check — every launch: if the app has been
        // updated to (or past) the version an "Update Available" notification
        // was posted for, cancel it immediately. This is what catches the
        // common case of updating via a manual APK install rather than the
        // app's own download flow, which the network-based check below
        // (throttled to once a day) could otherwise miss for up to a day.
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val notifiedVersionCode = preferencesManager.getLastNotifiedUpdateVersionCode()
                if (notifiedVersionCode > 0 && BuildConfig.VERSION_CODE.toLong() >= notifiedVersionCode) {
                    NotificationHelper.cancelUpdateAvailableNotification(this@NearExpiryApplication)
                    preferencesManager.setLastNotifiedUpdateVersionCode(0L)
                }
            }
        }
        // On-launch update check, throttled to at most once per day.
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val now = System.currentTimeMillis()
                val last = preferencesManager.getLastUpdateCheck()
                if (now - last >= 24 * 60 * 60 * 1000L) {
                    val result = AppUpdater.check(
                        currentVersionCode = BuildConfig.VERSION_CODE.toLong(),
                        currentVersionName = BuildConfig.VERSION_NAME
                    )
                    preferencesManager.setLastUpdateCheck(now)
                    if (result is AppUpdater.CheckResult.UpdateAvailable) {
                        preferencesManager.setLastNotifiedUpdateVersionCode(result.info.versionCode)
                        NotificationHelper.postUpdateAvailableNotification(this@NearExpiryApplication, result.info.versionName)
                    } else {
                        NotificationHelper.cancelUpdateAvailableNotification(this@NearExpiryApplication)
                        preferencesManager.setLastNotifiedUpdateVersionCode(0L)
                    }
                }
            }
        }
    }
}
