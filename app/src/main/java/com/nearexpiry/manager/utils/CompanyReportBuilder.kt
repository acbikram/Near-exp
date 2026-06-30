package com.nearexpiry.manager.utils

import com.nearexpiry.manager.domain.model.ExpiryItem
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Turns the app's expiry items into rows for the company Near-Expiry workbook,
 * applying the company's reporting rules:
 *
 *  • Date window: items whose expiry falls from the FIRST day of next month
 *    through the LAST day of the month three months out. E.g. if today is
 *    2026-07-04 → window 2026-08-01 .. 2026-10-31 (Aug, Sep, Oct).
 *  • UOM mapping → restricted to the template's list:
 *      PCS→PCS, CTN→CTN, KGS/Kg→Kg, PKT→PKT, OFR/offer→PKT, blank/unknown→PCS.
 *  • Item description: the app's catalog name; blank if none.
 */
object CompanyReportBuilder {

    /** Excel's day 0 is 1899-12-30 (the well-known 1900 date-system base). */
    private val EXCEL_EPOCH: LocalDate = LocalDate.of(1899, 12, 30)

    private fun toExcelSerial(date: LocalDate): Long =
        ChronoUnit.DAYS.between(EXCEL_EPOCH, date)

    fun mapUom(unit: String?): String {
        val u = unit?.trim()?.uppercase() ?: return "PCS"
        return when (u) {
            "PCS" -> "PCS"
            "CTN" -> "CTN"
            "KGS", "KG" -> "Kg"
            "PKT" -> "PKT"
            "OFR", "OFFER" -> "PKT"
            "" -> "PCS"
            else -> "PCS"
        }
    }

    /** A year-month the user can pick, e.g. (2026, 8). */
    data class YearMonth(val year: Int, val month: Int) : Comparable<YearMonth> {
        override fun compareTo(other: YearMonth): Int =
            if (year != other.year) year - other.year else month - other.month

        fun label(): String {
            val m = java.time.Month.of(month)
                .getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH)
            return "$m $year"
        }
        /** "08 2026" style token for filenames. */
        fun token(): String = month.toString().padStart(2, '0')
    }

    /**
     * Every distinct expiry year-month present in [items], sorted chronologically
     * (soonest first). These are the options shown in the month picker.
     */
    fun availableMonths(items: List<ExpiryItem>): List<YearMonth> =
        items.mapNotNull { item ->
            ExpiryDateUtils.parseOrNull(item.expiryDate)?.let { YearMonth(it.year, it.monthValue) }
        }.distinct().sorted()

    /**
     * Builds rows for [items] whose expiry falls in one of [selectedMonths],
     * pre-filled with branch info. Sorted by **scan order** (createdAt ascending)
     * so the first-scanned item is the first row and the last-scanned is last.
     */
    fun buildRows(
        items: List<ExpiryItem>,
        selectedMonths: Set<YearMonth>,
        area: String,
        branchId: String,
        branchName: String
    ): List<CompanyReportExcel.Row> {
        return items.mapNotNull { item ->
            val expiry = ExpiryDateUtils.parseOrNull(item.expiryDate) ?: return@mapNotNull null
            val ym = YearMonth(expiry.year, expiry.monthValue)
            if (ym !in selectedMonths) return@mapNotNull null
            item to CompanyReportExcel.Row(
                area = area,
                branchId = branchId,
                branchName = branchName,
                posCode = item.itemCode?.takeIf { it.isNotBlank() } ?: item.barcode,
                description = item.productName?.takeIf { it.isNotBlank() } ?: "",
                uom = mapUom(item.unit),
                qty = item.quantity,
                expiryExcelSerial = toExcelSerial(expiry)
            )
        }
            .sortedBy { it.first.createdAt }   // scan order: first scanned → first row
            .map { it.second }
    }

    /** Filename like "Near_Expiry_1102_08,09 & 10 2026.xlsx" from selected months. */
    fun fileName(branchId: String, selectedMonths: Set<YearMonth>): String {
        val sorted = selectedMonths.sorted()
        val year = sorted.lastOrNull()?.year ?: LocalDate.now().year
        val tokens = sorted.map { it.token() }
        val pretty = when {
            tokens.isEmpty() -> ""
            tokens.size == 1 -> tokens.first()
            else -> tokens.dropLast(1).joinToString(",") + " & " + tokens.last()
        }
        return "Near_Expiry_${branchId}_$pretty $year.xlsx"
    }

    /** Title like "Near Expiry Form Jubail 2 (Month Of 07, 08 & 09 2026)". */
    fun title(branchName: String, selectedMonths: Set<YearMonth>): String {
        val sorted = selectedMonths.sorted()
        val year = sorted.lastOrNull()?.year ?: LocalDate.now().year
        val tokens = sorted.map { it.token() }
        val pretty = when {
            tokens.isEmpty() -> ""
            tokens.size == 1 -> tokens.first()
            else -> tokens.dropLast(1).joinToString(", ") + " & " + tokens.last()
        }
        return "Near Expiry Form $branchName (Month Of $pretty $year)"
    }
}
