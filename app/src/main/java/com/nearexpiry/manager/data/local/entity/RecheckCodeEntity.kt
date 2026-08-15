package com.nearexpiry.manager.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One normalized POS / Item / Barcode value imported from the user-selected
 * Stock Recheck Excel workbook. The list is intentionally global because the
 * user requested one selected file to govern every Stock/Recheck project.
 */
@Entity(tableName = "recheck_codes")
data class RecheckCodeEntity(
    @PrimaryKey
    val code: String
)
