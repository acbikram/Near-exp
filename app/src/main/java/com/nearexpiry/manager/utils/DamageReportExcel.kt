package com.nearexpiry.manager.utils

import com.nearexpiry.manager.domain.model.ExpiryItem
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Damage Project workbook with the same title-row convention as Near Expiry reports. */
object DamageReportExcel {
    private const val SIGNATURE_TEXT =
        "Storekepeer                                                                      " +
        "Supervisor                                                       " +
        "Accountant                                                               Area manager"
    private val headers = listOf(
        "Sr. No.", "Area", "ID Branch", "Branch Name", "POS Code",
        "English Description", "UOM", "Qty", "Expiry Date", "Type", "Reason", "CTN No."
    )

    data class BranchInfo(val area: String, val id: String, val name: String)

    fun write(
        out: OutputStream,
        items: List<ExpiryItem>,
        branch: BranchInfo = BranchInfo("", "", ""),
        projectName: String = "Damage"
    ) {
        val rows = items.sortedWith(
            compareBy<ExpiryItem> { it.cartonNumber?.toLongOrNull() ?: Long.MAX_VALUE }
                .thenBy { it.effectiveOrder }.thenBy { it.id }
        )
        ZipOutputStream(out).use { zip ->
            entry(zip, "[Content_Types].xml", contentTypes())
            entry(zip, "_rels/.rels", rootRels())
            entry(zip, "xl/workbook.xml", workbook())
            entry(zip, "xl/_rels/workbook.xml.rels", workbookRels())
            entry(zip, "xl/styles.xml", styles())
            entry(zip, "xl/worksheets/sheet1.xml", sheet(rows, branch, projectName))
            zip.finish()
        }
    }

    private fun contentTypes() = """<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/></Types>"""
    private fun rootRels() = """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""
    private fun workbook() = """<?xml version="1.0" encoding="UTF-8"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Damage" sheetId="1" r:id="rId1"/></sheets></workbook>"""
    private fun workbookRels() = """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>"""

    private fun entry(zip: ZipOutputStream, name: String, body: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(body.toByteArray())
        zip.closeEntry()
    }

    private fun styles() = """<?xml version="1.0" encoding="UTF-8"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts><fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FFBDD7EE"/><bgColor indexed="64"/></patternFill></fill></fills><borders count="2"><border><left/><right/><top/><bottom/><diagonal/></border><border><left style="thin"/><right style="thin"/><top style="thin"/><bottom style="thin"/><diagonal/></border></borders><cellXfs count="5"><xf fontId="0" fillId="0" borderId="0"/><xf fontId="1" fillId="2" borderId="1" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1"/></xf><xf fontId="0" fillId="0" borderId="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf><xf fontId="1" fillId="0" borderId="1" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1"/></xf><xf fontId="1" fillId="0" borderId="1" applyAlignment="1"><alignment horizontal="center" vertical="top" wrapText="1"/></xf></cellXfs></styleSheet>"""

    private fun sheet(items: List<ExpiryItem>, branch: BranchInfo, projectName: String): String {
        val sb = StringBuilder("""<?xml version="1.0" encoding="UTF-8"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><cols><col min="1" max="1" width="8"/><col min="2" max="2" width="14"/><col min="3" max="3" width="12"/><col min="4" max="4" width="24"/><col min="5" max="5" width="16"/><col min="6" max="6" width="42"/><col min="7" max="7" width="10"/><col min="8" max="8" width="10"/><col min="9" max="9" width="14"/><col min="10" max="10" width="10"/><col min="11" max="11" width="20"/><col min="12" max="12" width="12"/></cols><sheetData>""")
        sb.append("<row r=\"1\">")
        sb.append(cell("A", 1, projectName, 1))
        sb.append("</row><row r=\"2\">")
        headers.forEachIndexed { i, header -> sb.append(cell(col(i), 2, header, 1)) }
        sb.append("</row>")
        items.forEachIndexed { index, item ->
            val row = index + 3
            val isExpiry = item.damageReason.equals("Expiry", ignoreCase = true)
            val values = listOf(
                (index + 1).toString(), branch.area, branch.id, branch.name,
                item.itemCode ?: item.barcode, item.productName ?: "", item.unit ?: "",
                formatQty(item.quantity), if (isExpiry) item.expiryDate else "",
                if (isExpiry) "Expiry" else "Damage", if (isExpiry) "" else item.damageReason.orEmpty(),
                item.cartonNumber.orEmpty()
            )
            sb.append("<row r=\"$row\">")
            values.forEachIndexed { i, value -> sb.append(cell(col(i), row, value, 2)) }
            sb.append("</row>")
        }
        // One final merged signature row, matching Near Expiry exactly.
        val signatureRow = items.size + 3
        sb.append("<row r=\"$signatureRow\" ht=\"60\" customHeight=\"1\">")
        sb.append(cell("A", signatureRow, SIGNATURE_TEXT, 4))
        for (i in 1 until headers.size) sb.append("<c r=\"${col(i)}$signatureRow\" s=\"4\"/>")
        sb.append("</row>")
        sb.append("</sheetData>")
        val groups = items.withIndex().filter { !it.value.cartonNumber.isNullOrBlank() }
            .groupBy { it.value.cartonNumber }.values.filter { it.size > 1 }
        sb.append("<mergeCells count=\"${groups.size + 2}\">")
        sb.append("<mergeCell ref=\"A1:L1\"/>")
        sb.append("<mergeCell ref=\"A${signatureRow}:L${signatureRow}\"/>")
        groups.forEach { group ->
            sb.append("<mergeCell ref=\"L${group.first().index + 3}:L${group.last().index + 3}\"/>")
        }
        sb.append("</mergeCells></worksheet>")
        return sb.toString()
    }

    private fun col(index: Int) = ('A'.code + index).toChar().toString()
    private fun cell(col: String, row: Int, value: String, style: Int) = "<c r=\"$col$row\" s=\"$style\" t=\"inlineStr\"><is><t>${esc(value)}</t></is></c>"
    private fun esc(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    private fun formatQty(value: Double) = if (value % 1.0 == 0.0) value.toLong().toString() else "%.2f".format(java.util.Locale.US, value)
}
