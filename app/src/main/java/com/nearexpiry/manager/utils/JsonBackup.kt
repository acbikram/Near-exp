package com.nearexpiry.manager.utils

import com.nearexpiry.manager.data.local.entity.ExpiryItemEntity
import com.nearexpiry.manager.data.local.entity.ProjectEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

/**
 * version 1: barcode, expiryDate, quantity, createdAt, updatedAt
 * version 2: + productName, productNameArabic, unit (resolved from the
 *            local product catalog at scan time). These are nullable with
 *            default = null on [ExpiryItemEntity], so older (v1) backups
 *            still decode correctly -- missing fields just become null.
 */
@Serializable
data class BackupData(
    val version: Int = 2,
    val items: List<ExpiryItemEntity>
)

/** One project plus its items, for a full multi-project backup. */
@Serializable
data class ProjectBackup(
    val project: ProjectEntity,
    val items: List<ExpiryItemEntity>
)

/** A backup of EVERY project and its items. */
@Serializable
data class AllProjectsBackup(
    val version: Int = 3,
    val type: String = "all_projects",
    val projects: List<ProjectBackup>
)

object JsonBackup {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    fun exportToJson(outputStream: OutputStream, items: List<ExpiryItemEntity>) {
        val backup = BackupData(items = items)
        outputStream.bufferedWriter().use {
            it.write(json.encodeToString(backup))
        }
    }

    fun importFromJson(inputStream: InputStream): List<ExpiryItemEntity> {
        val backup = inputStream.bufferedReader().use {
            json.decodeFromString<BackupData>(it.readText())
        }
        return backup.items
    }

    fun exportAllProjects(outputStream: OutputStream, projects: List<ProjectBackup>) {
        val backup = AllProjectsBackup(projects = projects)
        outputStream.bufferedWriter().use {
            it.write(json.encodeToString(backup))
        }
    }

    fun importAllProjects(inputStream: InputStream): AllProjectsBackup {
        return inputStream.bufferedReader().use {
            json.decodeFromString<AllProjectsBackup>(it.readText())
        }
    }

    /** Peeks whether the JSON text is an all-projects backup (vs single-project). */
    fun isAllProjectsBackup(text: String): Boolean =
        text.contains("\"all_projects\"") || text.contains("\"projects\"")
}
