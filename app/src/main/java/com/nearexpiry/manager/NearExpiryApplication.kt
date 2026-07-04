package com.nearexpiry.manager

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.nearexpiry.manager.notifications.ExpiryNotificationWorker
import com.nearexpiry.manager.notifications.NotificationHelper
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
        // Create notification channels on every start (safe; OS ignores duplicates).
        NotificationHelper.createChannels(this)
        // Schedule the daily 8 AM expiry check (KEEP policy — won't reset on restart).
        ExpiryNotificationWorker.schedule(this)
        // Schedule the daily background update check.
        UpdateCheckWorker.schedule(this)
        // Delete update APKs already installed (version <= current). APKs for a
        // newer, downloaded-but-not-yet-installed version are kept so the user
        // can tap "Install" without re-downloading. This approximates
        // "delete after install": if they installed it, this launch is that new
        // version, so the matching APK is now same-version → removed.
        AppUpdater.cleanupInstalledApks(this, BuildConfig.VERSION_NAME)
        // If the previously-active project was deleted, fall back to a valid one.
        CoroutineScope(Dispatchers.IO).launch {
            activeProjectManager.ensureValidActiveProject()
            // Recycle bin: drop entries older than 30 days.
            expiryRepository.purgeOldBinEntries(30)
        }
        // On-launch update check, throttled to at most once per day.
        CoroutineScope(Dispatchers.IO).launch {
            val now = System.currentTimeMillis()
            val last = preferencesManager.getLastUpdateCheck()
            if (now - last >= 24 * 60 * 60 * 1000L) {
                val result = AppUpdater.check(
                    currentVersionCode = BuildConfig.VERSION_CODE.toLong(),
                    currentVersionName = BuildConfig.VERSION_NAME
                )
                preferencesManager.setLastUpdateCheck(now)
                if (result is AppUpdater.CheckResult.UpdateAvailable) {
                    NotificationHelper.postUpdateAvailableNotification(this@NearExpiryApplication, result.info.versionName)
                }
            }
        }
    }
}
