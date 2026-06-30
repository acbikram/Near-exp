package com.nearexpiry.manager.utils

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Generates the company's "Near Expiry" submission workbook as a self-contained
 * .xlsm-named OOXML file (it contains no macros — the original template had
 * none either — so this is a plain spreadsheet that opens identically in Excel).
 *
 * The layout mirrors the company template exactly:
 *   Row 1  : merged title "Near Expiry Form"
 *   Row 2  : headers  S.N | Area | ID Branch | Branch Name | POS Code |
 *            ITEM_DESCRIPTION | UOM | Qty | Expiry Date | Type
 *   Row 3+ : one row per item (values written directly — no VLOOKUP)
 *   Last 2 : signature row ("Storekeeper … Supervisor … Accountant … Area
 *            manager") + a closing bordered row, kept as the closed-table base.
 *
 * Columns A..J. Expiry dates use the d-mmm-yy number format. Column J carries a
 * data-validation dropdown whose only allowed value is "Near expir".
 *
 * Everything is hand-written XML so no heavyweight Excel library is needed on
 * Android.
 */
object CompanyReportExcel {

    data class Row(
        val area: String,
        val branchId: String,
        val branchName: String,
        val posCode: String,
        val description: String,
        val uom: String,
        val qty: Double,
        val expiryExcelSerial: Long,   // days since 1899-12-30 (Excel date)
        val type: String = "Near expir"
    )

    private const val SIGNATURE_TEXT =
        "Storekepeer                                                                      " +
        "Supervisor                                                       " +
        "Accountant                                                               Area manager"

    fun write(out: OutputStream, rows: List<Row>) {
        val zip = ZipOutputStream(out)

        zip.putNextEntry(ZipEntry("[Content_Types].xml"))
        zip.write(contentTypes().toByteArray()); zip.closeEntry()

        zip.putNextEntry(ZipEntry("_rels/.rels"))
        zip.write(rootRels().toByteArray()); zip.closeEntry()

        zip.putNextEntry(ZipEntry("xl/workbook.xml"))
        zip.write(workbook().toByteArray()); zip.closeEntry()

        zip.putNextEntry(ZipEntry("xl/_rels/workbook.xml.rels"))
        zip.write(workbookRels().toByteArray()); zip.closeEntry()

        zip.putNextEntry(ZipEntry("xl/styles.xml"))
        zip.write(styles().toByteArray()); zip.closeEntry()

        zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
        zip.write(sheet(rows).toByteArray()); zip.closeEntry()

        zip.finish()
        zip.flush()
    }

    // ── Content types & relationships ──────────────────────────────────────

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

    private fun workbook() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets><sheet name="Near Expiry " sheetId="1" r:id="rId1"/></sheets>
</workbook>"""

    private fun workbookRels() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""

    // ── Styles ─────────────────────────────────────────────────────────────
    // Style indexes (s="..."):
    //  0 = default
    //  1 = title (Calibri 24 bold, centered)
    //  2 = header (Calibri 11 bold, centered, thin border, grey fill)
    //  3 = data text cell (thin border, centered)
    //  4 = data date cell (thin border, centered, d-mmm-yy)
    //  5 = signature (Calibri 11 bold, centered)
    private fun styles() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<numFmts count="1"><numFmt numFmtId="164" formatCode="d\-mmm\-yy;@"/></numFmts>
<fonts count="4">
<font><sz val="11"/><name val="Calibri"/></font>
<font><b/><sz val="24"/><name val="Calibri"/></font>
<font><b/><sz val="11"/><name val="Calibri"/></font>
<font><sz val="11"/><name val="Calibri"/></font>
</fonts>
<fills count="3">
<fill><patternFill patternType="none"/></fill>
<fill><patternFill patternType="gray125"/></fill>
<fill><patternFill patternType="solid"><fgColor rgb="FFD9D9D9"/><bgColor indexed="64"/></patternFill></fill>
</fills>
<borders count="2">
<border><left/><right/><top/><bottom/><diagonal/></border>
<border><left style="thin"><color indexed="64"/></left><right style="thin"><color indexed="64"/></right><top style="thin"><color indexed="64"/></top><bottom style="thin"><color indexed="64"/></bottom><diagonal/></border>
</borders>
<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
<cellXfs count="6">
<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
<xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>
<xf numFmtId="0" fontId="2" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1"/></xf>
<xf numFmtId="0" fontId="3" fillId="0" borderId="1" xfId="0" applyFont="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>
<xf numFmtId="164" fontId="3" fillId="0" borderId="1" xfId="0" applyFont="1" applyBorder="1" applyNumberFormat="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>
<xf numFmtId="0" fontId="2" fillId="0" borderId="1" xfId="0" applyFont="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>
</cellXfs>
<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
</styleSheet>"""

    // ── Worksheet ──────────────────────────────────────────────────────────

    private fun sheet(rows: List<Row>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<cols>
<col min="1" max="1" width="4.71" customWidth="1"/>
<col min="2" max="2" width="10.29" customWidth="1"/>
<col min="3" max="3" width="12.86" customWidth="1"/>
<col min="4" max="4" width="24.57" customWidth="1"/>
<col min="5" max="5" width="17" customWidth="1"/>
<col min="6" max="6" width="54.71" customWidth="1"/>
<col min="7" max="7" width="6.14" customWidth="1"/>
<col min="8" max="8" width="8.29" customWidth="1"/>
<col min="9" max="9" width="15.71" customWidth="1"/>
<col min="10" max="10" width="20.14" customWidth="1"/>
</cols>
<sheetData>
""")
        // Row 1 — title (merged A1:J1)
        sb.append("""<row r="1" ht="40.15" customHeight="1"><c r="A1" s="1" t="inlineStr"><is><t>Near Expiry Form </t></is></c>""")
        for (col in 2..10) sb.append("""<c r="${colLetter(col)}1" s="1"/>""")
        sb.append("</row>\n")

        // Row 2 — headers
        val headers = listOf("S.N","Area","ID Branch","Branch Name","POS Code","ITEM_DESCRIPTION","UOM","Qty","Expiry Date","Type")
        sb.append("""<row r="2" ht="29.45" customHeight="1">""")
        headers.forEachIndexed { i, h ->
            sb.append("""<c r="${colLetter(i+1)}2" s="2" t="inlineStr"><is><t>${esc(h)}</t></is></c>""")
        }
        sb.append("</row>\n")

        // Data rows start at 3
        var r = 3
        rows.forEachIndexed { idx, row ->
            sb.append("""<row r="$r" ht="23.1" customHeight="1">""")
            sb.append("""<c r="A$r" s="3"><v>${idx + 1}</v></c>""")
            sb.append("""<c r="B$r" s="3" t="inlineStr"><is><t>${esc(row.area)}</t></is></c>""")
            sb.append("""<c r="C$r" s="3" t="inlineStr"><is><t>${esc(row.branchId)}</t></is></c>""")
            sb.append("""<c r="D$r" s="3" t="inlineStr"><is><t>${esc(row.branchName)}</t></is></c>""")
            sb.append("""<c r="E$r" s="3" t="inlineStr"><is><t>${esc(row.posCode)}</t></is></c>""")
            sb.append("""<c r="F$r" s="3" t="inlineStr"><is><t>${esc(row.description)}</t></is></c>""")
            sb.append("""<c r="G$r" s="3" t="inlineStr"><is><t>${esc(row.uom)}</t></is></c>""")
            sb.append("""<c r="H$r" s="3"><v>${fmtQty(row.qty)}</v></c>""")
            sb.append("""<c r="I$r" s="4"><v>${row.expiryExcelSerial}</v></c>""")
            sb.append("""<c r="J$r" s="3" t="inlineStr"><is><t>${esc(row.type)}</t></is></c>""")
            sb.append("</row>\n")
            r++
        }

        // Signature labels row, an empty merged signing row, then the closing
        // bordered row (the "closed table" base).
        val sigRow = r
        sb.append("""<row r="$sigRow" ht="29.45" customHeight="1"><c r="A$sigRow" s="5" t="inlineStr"><is><t>${esc(SIGNATURE_TEXT)}</t></is></c>""")
        for (col in 2..10) sb.append("""<c r="${colLetter(col)}$sigRow" s="5"/>""")
        sb.append("</row>\n")

        // Empty merged row (A:J) — the physical space to sign.
        val signRow = r + 1
        sb.append("""<row r="$signRow" ht="29.45" customHeight="1">""")
        for (col in 1..10) sb.append("""<c r="${colLetter(col)}$signRow" s="3"/>""")
        sb.append("</row>\n")

        val closeRow = r + 2
        sb.append("""<row r="$closeRow" ht="29.45" customHeight="1">""")
        for (col in 1..10) sb.append("""<c r="${colLetter(col)}$closeRow" s="3"/>""")
        sb.append("</row>\n")

        sb.append("</sheetData>\n")

        // Merge title (A1:J1), the signature labels row, and the empty signing row.
        sb.append("""<mergeCells count="3"><mergeCell ref="A1:J1"/><mergeCell ref="A$sigRow:J$sigRow"/><mergeCell ref="A$signRow:J$signRow"/></mergeCells>
""")

        // Data-validation dropdown on column J for the data rows: only "Near expir".
        if (rows.isNotEmpty()) {
            sb.append("""<dataValidations count="1"><dataValidation type="list" allowBlank="1" showInputMessage="1" showErrorMessage="1" sqref="J3:J${sigRow - 1}"><formula1>"Near expir"</formula1></dataValidation></dataValidations>
""")
        }

        sb.append("</worksheet>")
        return sb.toString()
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private fun colLetter(col: Int): String {
        var c = col
        val sb = StringBuilder()
        while (c > 0) {
            val rem = (c - 1) % 26
            sb.insert(0, ('A' + rem))
            c = (c - 1) / 26
        }
        return sb.toString()
    }

    private fun fmtQty(q: Double): String =
        if (q == q.toLong().toDouble()) q.toLong().toString() else q.toString()

    private fun esc(s: String): String = buildString {
        for (ch in s) when (ch) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&apos;")
            else -> if (ch.code < 0x20 && ch != '\t' && ch != '\n' && ch != '\r') Unit else append(ch)
        }
    }

    /** Convenience for callers that want bytes. */
    fun toBytes(rows: List<Row>): ByteArray {
        val bos = ByteArrayOutputStream()
        write(bos, rows)
        return bos.toByteArray()
    }
}
