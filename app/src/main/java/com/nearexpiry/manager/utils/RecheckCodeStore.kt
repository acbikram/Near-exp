package com.nearexpiry.manager.utils

import com.nearexpiry.manager.data.local.database.ExpiryDatabase
import com.nearexpiry.manager.data.local.entity.RecheckCodeEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistent global index of the selected Stock Recheck workbook. It provides
 * fast scan gating plus source-row ordering and description metadata for every
 * Stock/Recheck project.
 */
@Singleton
class RecheckCodeStore @Inject constructor(
    database: ExpiryDatabase
) {
    private val dao = database.recheckCodeDao()

    suspend fun hasSelectedFile(): Boolean = dao.count() > 0

    suspend fun importedCodeCount(): Int = dao.count()

    suspend fun orderedRows(): List<RecheckCodeEntity> = dao.getAllOrdered()

    fun observeOrderedRows(): Flow<List<RecheckCodeEntity>> = dao.observeAllOrdered()

    suspend fun containsCatalogItem(itemCode: String?, barcode: String): Boolean =
        matchingRow(itemCode, barcode) != null

    suspend fun matchingRow(itemCode: String?, barcode: String): RecheckCodeEntity? {
        val preferredCode = normalize(itemCode)
        val fallbackBarcode = normalize(barcode)
        if (preferredCode != null) {
            dao.findByCode(preferredCode)?.let { return it }
        }
        return if (fallbackBarcode != null) dao.findByCode(fallbackBarcode) else null
    }

    /** Replaces the complete ordered template index in one Room transaction. */
    suspend fun replaceRows(rows: Collection<RecheckExcelReader.Row>): Int {
        val unique = linkedMapOf<String, RecheckCodeEntity>()
        rows.forEach { row ->
            val code = normalize(row.code) ?: return@forEach
            unique.putIfAbsent(
                code,
                RecheckCodeEntity(
                    code = code,
                    sortOrder = row.sortOrder,
                    description = row.description.trim(),
                    uom = row.uom.trim()
                )
            )
        }
        dao.replaceAll(unique.values.toList())
        return unique.size
    }

    /** Case-insensitive matching with whitespace removed around a POS/item code. */
    fun normalize(value: String?): String? = value
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.uppercase()
}
