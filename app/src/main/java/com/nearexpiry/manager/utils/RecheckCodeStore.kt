package com.nearexpiry.manager.utils

import com.nearexpiry.manager.data.local.database.ExpiryDatabase
import com.nearexpiry.manager.data.local.entity.RecheckCodeEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistent global list of codes imported from the selected Stock Recheck
 * workbook. This is intentionally shared by all Stock/Recheck projects.
 */
@Singleton
class RecheckCodeStore @Inject constructor(
    database: ExpiryDatabase
) {
    private val dao = database.recheckCodeDao()

    suspend fun hasSelectedFile(): Boolean = dao.count() > 0

    suspend fun importedCodeCount(): Int = dao.count()

    suspend fun containsCatalogItem(itemCode: String?, barcode: String): Boolean {
        val preferredCode = normalize(itemCode)
        return if (preferredCode != null) {
            dao.contains(preferredCode)
        } else {
            normalize(barcode)?.let { dao.contains(it) } ?: false
        }
    }

    suspend fun replaceCodes(rawCodes: Collection<String>): Int {
        val normalized = rawCodes.mapNotNull(::normalize).distinct()
        dao.replaceAll(normalized.map(::RecheckCodeEntity))
        return normalized.size
    }

    /** Case-insensitive matching with whitespace removed around a POS/item code. */
    fun normalize(value: String?): String? = value
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.uppercase()
}
