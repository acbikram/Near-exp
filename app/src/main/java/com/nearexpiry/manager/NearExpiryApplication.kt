package com.nearexpiry.manager

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.nearexpiry.manager.notifications.ExpiryNotificationWorker
import com.nearexpiry.manager.notifications.NotificationHelper
import com.nearexpiry.manager.utils.ActiveProjectManager
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
        // If the previously-active project was deleted, fall back to a valid one.
        CoroutineScope(Dispatchers.IO).launch {
            activeProjectManager.ensureValidActiveProject()
        }
    }
}
