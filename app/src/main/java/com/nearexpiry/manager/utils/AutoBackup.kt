package com.nearexpiry.manager.utils

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import com.nearexpiry.manager.data.local.database.ExpiryDatabase
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Automatic local backup: writes an all-projects JSON (the same format the
 * Backup button and Restore Database use) into the phone's PUBLIC Documents
 * folder — "Documents/Near Expiry Backups/". Files there are NOT deleted when
 * the app is uninstalled, so after a reinstall the data can be recovered with
 * Settings → Backup & Restore → Restore Database.
 *
 * Runs daily around 12:00 (noon) via [com.nearexpiry.manager.notifications.AutoBackupWorker]
 * and on demand via the "Backup Now To Internal Storage" button. Keeps a
 * rolling window of the newest [KEEP_COUNT] backups; older ones are deleted
 * (files owned by a previous install that can't be deleted are just skipped).
 */
object AutoBackup {

    private const val FOLDER = "Near Expiry Backups"
    private const val PREFIX = "NearExpiry_auto_backup_"
    private const val KEEP_COUNT = 7

    /** Writes today's backup. Returns the display name of the file written. */
    suspend fun run(context: Context): String {
        val db = ExpiryDatabase.getInstance(context)
        val projects = db.projectDao().getAllProjectsOnce()
        val bundles = projects.map { p ->
            ProjectBackup(project = p, items = db.expiryItemDao().getAllItemsOnce(p.id))
        }

        val name = PREFIX + LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE) + ".json"
        val resolver = context.contentResolver
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val relPath = Environment.DIRECTORY_DOCUMENTS + "/" + FOLDER

        // Same-day rerun: remove today's earlier file first (ignore failures on
        // files owned by a previous install).
        deleteByName(context, name)

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
            put(MediaStore.MediaColumns.RELATIVE_PATH, relPath)
        }
        val uri = resolver.insert(collection, values)
            ?: throw IllegalStateException("Could not create backup file")
        resolver.openOutputStream(uri)?.use { out ->
            JsonBackup.exportAllProjects(out, bundles)
        } ?: throw IllegalStateException("Could not write backup file")

        pruneOld(context)
        return name
    }

    /** Deletes all but the newest [KEEP_COUNT] backups (best-effort). */
    private fun pruneOld(context: Context) {
        val resolver = context.contentResolver
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val names = ArrayList<Pair<Long, String>>()
        resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME),
            MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ? AND " +
                MediaStore.MediaColumns.DISPLAY_NAME + " LIKE ?",
            arrayOf("%$FOLDER%", "$PREFIX%"),
            null
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            while (c.moveToNext()) names.add(c.getLong(idCol) to c.getString(nameCol))
        }
        // Dated names sort chronologically; keep the newest KEEP_COUNT.
        names.sortByDescending { it.second }
        for ((id, _) in names.drop(KEEP_COUNT)) {
            try {
                resolver.delete(ContentUris.withAppendedId(collection, id), null, null)
            } catch (_: Exception) {
                // Owned by a previous install — can't delete without user
                // interaction; harmless, skip.
            }
        }
    }

    private fun deleteByName(context: Context, name: String) {
        val resolver = context.contentResolver
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ? AND " +
                MediaStore.MediaColumns.DISPLAY_NAME + " = ?",
            arrayOf("%$FOLDER%", name),
            null
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            while (c.moveToNext()) {
                try {
                    resolver.delete(
                        ContentUris.withAppendedId(collection, c.getLong(idCol)), null, null
                    )
                } catch (_: Exception) { /* previous-install file; skip */ }
            }
        }
    }
}
