package com.nearexpiry.manager.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One distinct POS / Item / Barcode row imported from the selected global Stock
 * Recheck workbook. The workbook itself is preserved separately as the export
 * template; these fields provide fast Stock scan gating and History ordering.
 */
@Entity(
    tableName = "recheck_codes",
    indices = [Index(value = ["sortOrder"])]
)
data class RecheckCodeEntity(
    @PrimaryKey
    val code: String,
    /** First source-row position for this code in the Recheck workbook. */
    @ColumnInfo(defaultValue = "2147483647")
    val sortOrder: Int = Int.MAX_VALUE,
    /** Template description shown for matching scanned Stock items. */
    @ColumnInfo(defaultValue = "''")
    val description: String = "",
    /** Template UOM shown when the scanned item has no unit. */
    @ColumnInfo(defaultValue = "''")
    val uom: String = ""
)
