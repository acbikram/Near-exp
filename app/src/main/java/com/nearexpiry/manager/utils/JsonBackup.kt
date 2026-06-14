package com.nearexpiry.manager.utils

import com.nearexpiry.manager.data.local.entity.ExpiryItemEntity
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

object JsonBackup {
    private val json = Json { prettyPrint = true }

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
}
