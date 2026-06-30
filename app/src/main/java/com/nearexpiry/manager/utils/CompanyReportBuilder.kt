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

    data class Window(val start: LocalDate, val endInclusive: LocalDate) {
        /** The three month numbers in the window, e.g. [8, 9, 10]. */
        val months: List<Int>
            get() {
                val list = ArrayList<Int>(3)
                var m = start
                while (!m.isAfter(endInclusive)) {
                    if (list.isEmpty() || list.last() != m.monthValue) list.add(m.monthValue)
                    m = m.plusMonths(1).withDayOfMonth(1)
                }
                return list.distinct()
            }
    }

    /**
     * Window = 1st of next month .. last day of the month three months out
     * (i.e. next month + the following two months).
     */
    fun reportWindow(today: LocalDate = LocalDate.now()): Window {
        val start = today.plusMonths(1).withDayOfMonth(1)
        val lastMonthStart = start.plusMonths(2)              // 3rd month in the window
        val end = lastMonthStart.withDayOfMonth(lastMonthStart.lengthOfMonth())
        return Window(start, end)
    }

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

    /**
     * Builds the rows for [items] that fall in the report [window], pre-filled
     * with the given branch info. Items outside the window are excluded. Result
     * is sorted by expiry date ascending (soonest first).
     */
    fun buildRows(
        items: List<ExpiryItem>,
        window: Window,
        area: String,
        branchId: String,
        branchName: String
    ): List<CompanyReportExcel.Row> {
        return items.mapNotNull { item ->
            val expiry = ExpiryDateUtils.parseOrNull(item.expiryDate) ?: return@mapNotNull null
            if (expiry.isBefore(window.start) || expiry.isAfter(window.endInclusive)) return@mapNotNull null
            CompanyReportExcel.Row(
                area = area,
                branchId = branchId,
                branchName = branchName,
                posCode = item.itemCode?.takeIf { it.isNotBlank() } ?: item.barcode,
                description = item.productName?.takeIf { it.isNotBlank() } ?: "",
                uom = mapUom(item.unit),
                qty = item.quantity,
                expiryExcelSerial = toExcelSerial(expiry)
            )
        }.sortedBy { it.expiryExcelSerial }
    }

    /** Filename like "Near_Expiry_1102_08,09 & 10 2026.xlsm". */
    fun fileName(branchId: String, window: Window): String {
        val months = window.months.joinToString(",") { it.toString().padStart(2, '0') }
        // Render as "08,09 & 10" — replace the last comma with " & ".
        val pretty = months.substringBeforeLast(",") + " & " + months.substringAfterLast(",")
        val year = window.endInclusive.year
        return "Near_Expiry_${branchId}_$pretty $year.xlsm"
    }
}
