package com.nearexpiry.manager.utils

import com.nearexpiry.manager.domain.model.ExpiryItem
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Writes the Damage Project workbook without adding a heavyweight Excel dependency. */
object DamageReportExcel {
    private val headers = listOf("Sr. No.", "Item Code", "Description", "UOM", "Quantity", "Reason", "Expiry Date", "CTN No.")

    fun write(out: OutputStream, items: List<ExpiryItem>) {
        val rows = items.sortedWith(compareBy<ExpiryItem> { it.cartonNumber?.toLongOrNull() ?: Long.MAX_VALUE }
            .thenBy { it.effectiveOrder }.thenBy { it.id })
        val zip = ZipOutputStream(out)
        entry(zip, "[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/></Types>""")
        entry(zip, "_rels/.rels", """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
        entry(zip, "xl/workbook.xml", """<?xml version="1.0" encoding="UTF-8"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Damage" sheetId="1" r:id="rId1"/></sheets></workbook>""")
        entry(zip, "xl/_rels/workbook.xml.rels", """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>""")
        entry(zip, "xl/styles.xml", styles())
        entry(zip, "xl/worksheets/sheet1.xml", sheet(rows))
        zip.finish()
    }

    private fun entry(zip: ZipOutputStream, name: String, body: String) {
        zip.putNextEntry(ZipEntry(name)); zip.write(body.toByteArray()); zip.closeEntry()
    }

    private fun styles() = """<?xml version="1.0" encoding="UTF-8"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts><fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FFBDD7EE"/><bgColor indexed="64"/></patternFill></fill></fills><borders count="2"><border><left/><right/><top/><bottom/><diagonal/></border><border><left style="thin"/><right style="thin"/><top style="thin"/><bottom style="thin"/><diagonal/></border></borders><cellXfs count="3"><xf fontId="0" fillId="0" borderId="0"/><xf fontId="1" fillId="2" borderId="1" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1"/></xf><xf fontId="0" fillId="0" borderId="1" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1"/></xf></cellXfs></styleSheet>"""

    private fun sheet(items: List<ExpiryItem>): String {
        val sb = StringBuilder("""<?xml version="1.0" encoding="UTF-8"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><cols><col min="1" max="1" width="8"/><col min="2" max="2" width="16"/><col min="3" max="3" width="42"/><col min="4" max="4" width="10"/><col min="5" max="5" width="12"/><col min="6" max="6" width="18"/><col min="7" max="7" width="16"/><col min="8" max="8" width="12"/></cols><sheetData>""")
        sb.append("<row r=\"1\">")
        headers.forEachIndexed { i, h -> sb.append(cell(col(i), 1, h, 1)) }
        sb.append("</row>")
        items.forEachIndexed { index, item ->
            val r = index + 2
            val values = listOf((index + 1).toString(), item.itemCode ?: item.barcode, item.displayName, item.unit ?: "", formatQty(item.quantity), item.damageReason ?: "Expiry", item.expiryDate.takeUnless { it.isBlank() || it == "0000-00-00" } ?: "", item.cartonNumber ?: "")
            sb.append("<row r=\"$r\">")
            values.forEachIndexed { i, value -> sb.append(cell(col(i), r, value, 2)) }
            sb.append("</row>")
        }
        sb.append("</sheetData>")
        val groups = items.withIndex().filter { !it.value.cartonNumber.isNullOrBlank() }
            .groupBy { it.value.cartonNumber }.values.filter { it.size > 1 }
        if (groups.isNotEmpty()) {
            sb.append("<mergeCells count=\"${groups.size}\">")
            groups.forEach { group -> sb.append("<mergeCell ref=\"H${group.first().index + 2}:H${group.last().index + 2}\"/>") }
            sb.append("</mergeCells>")
        }
        sb.append("</worksheet>")
        return sb.toString()
    }

    private fun col(i: Int) = ('A'.code + i).toChar().toString()
    private fun cell(col: String, row: Int, value: String, style: Int) = "<c r=\"$col$row\" s=\"$style\" t=\"inlineStr\"><is><t>${esc(value)}</t></is></c>"
    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    private fun formatQty(value: Double) = if (value % 1.0 == 0.0) value.toLong().toString() else "%.2f".format(java.util.Locale.US, value)
}
