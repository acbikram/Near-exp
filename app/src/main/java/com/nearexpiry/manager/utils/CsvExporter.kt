package com.nearexpiry.manager.utils

import com.nearexpiry.manager.domain.model.ExpiryItem
import com.opencsv.CSVWriter
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object CsvExporter {
    fun writeCsv(outputStream: OutputStream, items: List<ExpiryItem>) {
        CSVWriter(OutputStreamWriter(outputStream)).use { writer ->
            writer.writeNext(
                arrayOf(
                    "Barcode", "ProductName", "ProductNameArabic", "Unit",
                    "ExpiryDate", "Quantity", "CreatedAt", "UpdatedAt"
                )
            )
            items.forEach { item ->
                writer.writeNext(
                    arrayOf(
                        item.barcode,
                        item.productName ?: "",
                        item.productNameArabic ?: "",
                        item.unit ?: "",
                        item.expiryDate,
                        if (item.quantity % 1.0 == 0.0) item.quantity.toInt().toString() else item.quantity.toString(),
                        formatTimestamp(item.createdAt),
                        formatTimestamp(item.updatedAt)
                    )
                )
            }
        }
    }

    private fun formatTimestamp(timestamp: Long): String {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault())
            .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    }
}
