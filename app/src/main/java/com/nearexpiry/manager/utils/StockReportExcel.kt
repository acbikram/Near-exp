package com.nearexpiry.manager.utils

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Generates the Stock Check workbook in the supplied Recheck template format.
 *
 * The worksheet matches its visible eight-column structure:
 * A Sr. No. | B POS_CODE | C ITEM_DESCRIPTION | D UOM | E AS_GROUPING |
 * F PHY_QTY | G Damage Expiry Stock | H TOTAL_STOCK.
 * Columns E and G deliberately remain empty; F and H carry the same quantity.
 */
object StockReportExcel {

    data class Row(
        val posCode: String,
        val description: String,
        val uom: String,
        val quantity: Double,
        /** Highlight POS code when the catalog is missing a description or UOM. */
        val highlightPosCode: Boolean
    )

    fun write(out: OutputStream, rows: List<Row>, title: String, sheetName: String) {
        ZipOutputStream(out).use { zip ->
            entry(zip, "[Content_Types].xml", contentTypes())
            entry(zip, "_rels/.rels", rootRels())
            entry(zip, "xl/workbook.xml", workbook(sheetName))
            entry(zip, "xl/_rels/workbook.xml.rels", workbookRels())
            entry(zip, "xl/styles.xml", styles())
            entry(zip, "xl/worksheets/sheet1.xml", sheet(rows, title))
            zip.finish()
        }
    }

    private fun entry(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun contentTypes() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>"""

    private fun rootRels() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

    private fun workbook(sheetName: String) = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets><sheet name="${esc(sheetName.take(31).ifBlank { "Stock Check" })}" sheetId="1" r:id="rId1"/></sheets>
</workbook>"""

    private fun workbookRels() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""

    /** Styles mirror the supplied Recheck workbook: Calibri, yellow title, navy headers, thin cell borders. */
    private fun styles() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<fonts count="3">
<font><sz val="11"/><name val="Calibri"/></font>
<font><b/><sz val="11"/><name val="Calibri"/></font>
<font><b/><sz val="14"/><name val="Calibri"/></font>
</fonts>
<fills count="4">
<fill><patternFill patternType="none"/></fill>
<fill><patternFill patternType="gray125"/></fill>
<fill><patternFill patternType="solid"><fgColor rgb="FFFFFF00"/><bgColor indexed="64"/></patternFill></fill>
<fill><patternFill patternType="solid"><fgColor rgb="FF004D82"/><bgColor indexed="64"/></patternFill></fill>
</fills>
<borders count="2">
<border><left/><right/><top/><bottom/><diagonal/></border>
<border><left style="thin"><color indexed="64"/></left><right style="thin"><color indexed="64"/></right><top style="thin"><color indexed="64"/></top><bottom style="thin"><color indexed="64"/></bottom><diagonal/></border>
</borders>
<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
<cellXfs count="5">
<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
<xf numFmtId="0" fontId="2" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center"/></xf>
<xf numFmtId="0" fontId="1" fillId="3" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="left" wrapText="1"/></xf>
<xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1"/>
<xf numFmtId="0" fontId="0" fillId="2" borderId="1" xfId="0" applyFill="1" applyBorder="1"/>
</cellXfs>
<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
</styleSheet>"""

    private fun sheet(rows: List<Row>, title: String): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<cols>
<col min="1" max="1" width="6.85546875" customWidth="1"/>
<col min="2" max="2" width="12.42578125" customWidth="1"/>
<col min="3" max="3" width="68.42578125" customWidth="1"/>
<col min="4" max="4" width="13" customWidth="1"/>
<col min="5" max="5" width="20.42578125" customWidth="1"/>
<col min="6" max="6" width="13.140625" customWidth="1"/>
<col min="7" max="7" width="13" customWidth="1"/>
<col min="8" max="8" width="13" customWidth="1"/>
</cols><sheetData>
""")
        append("<row r=\"1\" ht=\"18.75\" customHeight=\"1\"><c r=\"A1\" s=\"1\" t=\"inlineStr\"><is><t>${esc(title)}</t></is></c>")
        for (col in 2..8) append("<c r=\"${letter(col)}1\" s=\"1\"/>")
        append("</row>\n")
        val headers = listOf("Sr. No.", "POS_CODE", "ITEM_DESCRIPTION", "UOM", "AS_GROUPING", "PHY_QTY", "Damage Expiry Stock", "TOTAL_STOCK")
        append("<row r=\"2\" ht=\"45\" customHeight=\"1\">")
        headers.forEachIndexed { index, header ->
            append("<c r=\"${letter(index + 1)}2\" s=\"2\" t=\"inlineStr\"><is><t>${esc(header)}</t></is></c>")
        }
        append("</row>\n")
        rows.forEachIndexed { index, row ->
            val r = index + 3
            append("<row r=\"$r\">")
            append("<c r=\"A$r\" s=\"3\"><v>${index + 1}</v></c>")
            append("<c r=\"B$r\" s=\"${if (row.highlightPosCode) 4 else 3}\" t=\"inlineStr\"><is><t>${esc(row.posCode)}</t></is></c>")
            append("<c r=\"C$r\" s=\"3\" t=\"inlineStr\"><is><t>${esc(row.description)}</t></is></c>")
            append("<c r=\"D$r\" s=\"3\" t=\"inlineStr\"><is><t>${esc(row.uom)}</t></is></c>")
            append("<c r=\"E$r\" s=\"3\"/>")
            append("<c r=\"F$r\" s=\"3\"><v>${qty(row.quantity)}</v></c>")
            append("<c r=\"G$r\" s=\"3\"/>")
            append("<c r=\"H$r\" s=\"3\"><v>${qty(row.quantity)}</v></c>")
            append("</row>\n")
        }
        append("</sheetData><mergeCells count=\"1\"><mergeCell ref=\"A1:H1\"/></mergeCells></worksheet>")
    }

    private fun qty(value: Double): String = if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

    private fun letter(column: Int): String = ('A'.code + column - 1).toChar().toString()

    private fun esc(value: String): String = buildString {
        for (ch in value) when (ch) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&apos;")
            else -> if (ch.code >= 0x20 || ch == '\t' || ch == '\n' || ch == '\r') append(ch)
        }
    }
}
