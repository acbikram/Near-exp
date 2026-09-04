# ML Kit + CameraX use reflection internally.
-keep class com.google.mlkit.** { *; }
-keep class androidx.camera.** { *; }

# OpenCSV uses reflection for bean mapping.
-keep class com.opencsv.** { *; }
-dontwarn com.opencsv.**

# Hilt generated components.
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ActivityComponentManager { *; }

# Room entities, DAOs, generated implementations, and database metadata.
-keep class androidx.room.** { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keepclassmembers class ** {
    @androidx.room.* <fields>;
}

# Apache POI uses reflective workbook and OOXML model access.
-keep class org.apache.poi.** { *; }
-dontwarn org.apache.poi.**
-dontwarn org.apache.xmlbeans.**

# Kotlin Coroutines are referenced through generated continuations and dispatchers.
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# Kotlin serialization.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep class kotlinx.serialization.** { *; }
-keepclassmembers @kotlinx.serialization.Serializable class ** { *; }

# DataStore.
-keep class androidx.datastore.** { *; }

# WorkManager and Hilt-Work workers.
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.CoroutineWorker { *; }
-keep class * extends androidx.work.ListenableWorker { *; }
-keep class androidx.work.** { *; }
-keepclassmembers class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Named workers in this application.
-keep class com.nearexpiry.manager.notifications.AutoBackupWorker { *; }
-keep class com.nearexpiry.manager.notifications.ExpiryNotificationWorker { *; }
-keep class com.nearexpiry.manager.notifications.SnoozedReminderWorker { *; }
-keep class com.nearexpiry.manager.notifications.TierNotificationWorker { *; }
-keep class com.nearexpiry.manager.notifications.UpdateCheckWorker { *; }
-keep class com.nearexpiry.manager.notifications.UpdateDownloadWorker { *; }

# Suppress warnings from optional dependencies.
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
