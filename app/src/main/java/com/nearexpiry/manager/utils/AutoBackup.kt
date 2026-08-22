package com.nearexpiry.manager.utils

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import com.nearexpiry.manager.data.local.database.ExpiryDatabase
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Automatic all-project backup stored in the public Documents/Near Expiry
 * Backups folder. Backups run at noon and midnight, so [KEEP_COUNT] preserves
 * fourteen snapshots: the newest seven days of twice-daily backups.
 */
object AutoBackup {

    private const val FOLDER = "Near Expiry Backups"
    private const val PREFIX = "NearExpiry_auto_backup_"
    private const val KEEP_COUNT = 14
    private val fileTimestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")

    /** Writes a timestamped all-projects backup and returns its display name. */
    suspend fun run(context: Context): String {
        val db = ExpiryDatabase.getInstance(context)
        val projects = db.projectDao().getAllProjectsOnce()
        val bundles = projects.map { project ->
            ProjectBackup(
                project = project,
                items = db.expiryItemDao().getAllItemsOnce(project.id)
            )
        }

        val name = PREFIX + LocalDateTime.now().format(fileTimestampFormatter) + ".json"
        val resolver = context.contentResolver
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val relativePath = Environment.DIRECTORY_DOCUMENTS + "/" + FOLDER
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
        }
        val uri = resolver.insert(collection, values)
            ?: throw IllegalStateException("Could not create backup file")
        resolver.openOutputStream(uri)?.use { output ->
            JsonBackup.exportAllProjects(output, bundles)
        } ?: throw IllegalStateException("Could not write backup file")

        pruneOld(context)
        return name
    }

    /** Reads one app-created automatic backup from Documents for Drive upload. */
    fun readBackup(context: Context, name: String): ByteArray? {
        val resolver = context.contentResolver
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ? AND " +
                MediaStore.MediaColumns.DISPLAY_NAME + " = ?",
            arrayOf("%$FOLDER%", name),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val uri = ContentUris.withAppendedId(
                    collection,
                    cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                )
                return resolver.openInputStream(uri)?.use { it.readBytes() }
            }
        }
        return null
    }

    /** Deletes all but the newest fourteen automatic files, best-effort. */
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
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            while (cursor.moveToNext()) names.add(cursor.getLong(idColumn) to cursor.getString(nameColumn))
        }
        names.sortByDescending { it.second }
        names.drop(KEEP_COUNT).forEach { (id, _) ->
            try {
                resolver.delete(ContentUris.withAppendedId(collection, id), null, null)
            } catch (_: Exception) {
                // Previous-install files may not be removable; keep them harmlessly.
            }
        }
    }
}
