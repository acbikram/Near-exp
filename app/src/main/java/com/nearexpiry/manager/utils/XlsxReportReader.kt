package com.nearexpiry.manager.utils

import com.nearexpiry.manager.data.local.entity.ExpiryItemEntity
import java.io.ByteArrayInputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.zip.ZipInputStream

/**
 * Reads the app's own Near-Expiry report .xlsx (as produced by
 * [CompanyReportExcel] / "Make Excel File") back into items, so an old report
 * can be restored after accidental data loss.
 *
 * Sheet layout: row 1 = title, row 2 = header, data rows 3..N, last row =
 * signature. Columns: E = POS code, F = description, G = UOM, H = qty,
 * I = expiry (Excel serial date). Values are inline strings (the app's own
 * writer) but shared strings are also handled in case the file was re-saved
 * in Excel.
 */
object XlsxReportReader {

    /** Excel's day-serial epoch (serial 1 = 1900-01-01, with the 1900 leap bug). */
    private val EXCEL_EPOCH: LocalDate = LocalDate.of(1899, 12, 30)

    private val ROW_RE = Regex("""<row[^>]*\br="(\d+)"[^>]*>(.*?)</row>""", RegexOption.DOT_MATCHES_ALL)
    private val CELL_RE = Regex("""<c\b[^>]*\br="([A-Z]+)(\d+)"([^>]*)>(.*?)</c>|<c\b[^>]*\br="([A-Z]+)(\d+)"([^>]*)/>""", RegexOption.DOT_MATCHES_ALL)
    private val INLINE_RE = Regex("""<is>.*?<t[^>]*>(.*?)</t>.*?</is>""", RegexOption.DOT_MATCHES_ALL)
    private val V_RE = Regex("""<v>(.*?)</v>""", RegexOption.DOT_MATCHES_ALL)
    private val SI_RE = Regex("""<si>(.*?)</si>""", RegexOption.DOT_MATCHES_ALL)
    private val TAG_RE = Regex("""<[^>]+>""")

    data class ParsedRow(
        val itemCode: String,
        val description: String,
        val uom: String,
        val quantity: Double,
        val expiryIso: String
    )

    /** Parses the xlsx [bytes]; returns rows, or empty if it isn't a report. */
    fun parse(bytes: ByteArray): List<ParsedRow> {
        var sheetXml: String? = null
        var sharedXml: String? = null
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                when (entry.name) {
                    "xl/worksheets/sheet1.xml" -> sheetXml = zip.readBytes().toString(Charsets.UTF_8)
                    "xl/sharedStrings.xml" -> sharedXml = zip.readBytes().toString(Charsets.UTF_8)
                }
                entry = zip.nextEntry
            }
        }
        val sheet = sheetXml ?: return emptyList()
        val shared = sharedXml?.let { xml ->
            SI_RE.findAll(xml).map { TAG_RE.replace(it.groupValues[1], "") }.toList()
        } ?: emptyList()

        // rowNum -> (col -> text)
        val rows = HashMap<Int, HashMap<String, String>>()
        for (rm in ROW_RE.findAll(sheet)) {
            val rowNum = rm.groupValues[1].toIntOrNull() ?: continue
            val cells = HashMap<String, String>()
            for (cm in CELL_RE.findAll(rm.groupValues[2])) {
                val (col, attrs, inner) = if (cm.groupValues[1].isNotEmpty()) {
                    Triple(cm.groupValues[1], cm.groupValues[3], cm.groupValues[4])
                } else {
                    Triple(cm.groupValues[5], cm.groupValues[7], "")
                }
                cells[col] = cellText(inner, attrs, shared)
            }
            rows[rowNum] = cells
        }
        if (rows.isEmpty()) return emptyList()

        val maxRow = rows.keys.max()
        val out = ArrayList<ParsedRow>()
        for (rn in 3 until maxRow) {   // skip title (1), header (2), signature (maxRow)
            val c = rows[rn] ?: continue
            val pos = c["E"]?.trim().orEmpty()
            val qtyStr = c["H"]?.trim().orEmpty()
            if (pos.isEmpty() && qtyStr.isEmpty()) continue   // blank/decoration row
            if (pos.isEmpty()) continue
            val qty = qtyStr.toDoubleOrNull() ?: continue
            if (qty <= 0) continue
            val serial = c["I"]?.trim()?.toDoubleOrNull() ?: continue
            val expiry = EXCEL_EPOCH.plusDays(serial.toLong())
                .format(DateTimeFormatter.ISO_LOCAL_DATE)
            out.add(
                ParsedRow(
                    itemCode = pos,
                    description = c["F"]?.trim().orEmpty(),
                    uom = c["G"]?.trim().orEmpty(),
                    quantity = qty,
                    expiryIso = expiry
                )
            )
        }
        return out
    }

    /** Converts parsed rows to insert-ready entities for [projectId]. */
    fun toEntities(rows: List<ParsedRow>, projectId: Long): List<ExpiryItemEntity> {
        val now = System.currentTimeMillis()
        return rows.map { r ->
            ExpiryItemEntity(
                id = 0,
                barcode = r.itemCode,           // report has no barcode column
                expiryDate = r.expiryIso,
                quantity = r.quantity,
                createdAt = now,
                updatedAt = now,
                productName = r.description.takeIf { it.isNotBlank() },
                productNameArabic = null,
                unit = r.uom.takeIf { it.isNotBlank() },
                itemCode = r.itemCode,
                projectId = projectId
            )
        }
    }

    private fun cellText(inner: String, attrs: String, shared: List<String>): String {
        INLINE_RE.find(inner)?.let { return unesc(it.groupValues[1]) }
        if (attrs.contains("t=\"s\"")) {
            val idx = V_RE.find(inner)?.groupValues?.get(1)?.toIntOrNull()
            if (idx != null && idx < shared.size) return shared[idx]
        }
        return V_RE.find(inner)?.let { unesc(it.groupValues[1]) } ?: ""
    }

    private fun unesc(s: String): String = s
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&apos;", "'")
}
