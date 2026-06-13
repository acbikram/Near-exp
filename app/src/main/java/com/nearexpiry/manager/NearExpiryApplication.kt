package com.nearexpiry.manager

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.nearexpiry.manager.notifications.ExpiryNotificationWorker
import com.nearexpiry.manager.notifications.NotificationHelper
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class NearExpiryApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

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
    }
}
