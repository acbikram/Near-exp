package com.nearexpiry.manager.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory

class RecheckTemplateWorkbookTest {
    @Test
    fun `preserves every styled template row and writes physical plus damage totals`() {
        val template = workbook(
            """
            <worksheet><sheetData>
              <row r="1">
                <c r="B1" t="inlineStr"><is><t>POS_CODE</t></is></c>
                <c r="F1" t="inlineStr"><is><t>PHY_QTY</t></is></c>
                <c r="G1" t="inlineStr"><is><t>Damage Expiry Stock</t></is></c>
                <c r="H1" t="inlineStr"><is><t>TOTAL_STOCK</t></is></c>
              </row>
              <row r="2"><c r="B2" s="4"><v>8897605.0</v></c><c r="F2" s="4"/><c r="G2" s="4"><v>5.0</v></c><c r="H2" s="5"/></row>
              <row r="3"><c r="B3" s="4"><v>8.0006588E7</v></c><c r="F3" s="4"/><c r="G3" s="4"/><c r="H3" s="5"/></row>
            </sheetData></worksheet>
            """.trimIndent()
        )

        val exported = RecheckTemplateWorkbook.applyQuantities(
            template,
            mapOf("8897605" to 3.0, "80006588" to 2.0)
        )
        val sheet = worksheet(exported)

        // Parse as XML first: Excel must never receive malformed self-closing replacements.
        DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(ByteArrayInputStream(sheet.toByteArray()))
        assertEquals(3, Regex("""<row\b""").findAll(sheet).count())
        assertTrue(sheet.contains("<c r=\"B2\" s=\"4\"><v>8897605.0</v></c>"))
        assertTrue(sheet.contains("<c r=\"F2\" s=\"4\"><v>3</v></c>"))
        assertTrue(sheet.contains("<c r=\"G2\" s=\"4\"><v>5.0</v></c>"))
        assertTrue(sheet.contains("<c r=\"H2\" s=\"5\"><v>8</v></c>"))
        assertTrue(sheet.contains("<c r=\"F3\" s=\"4\"><v>2</v></c>"))
        assertTrue(sheet.contains("<c r=\"H3\" s=\"5\"><v>2</v></c>"))
    }

    private fun workbook(sheetXml: String): ByteArray = ByteArrayOutputStream().use { output ->
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            zip.write(sheetXml.toByteArray())
            zip.closeEntry()
        }
        output.toByteArray()
    }

    private fun worksheet(workbook: ByteArray): String = ZipInputStream(ByteArrayInputStream(workbook)).use { zip ->
        var entry = zip.nextEntry
        while (entry != null) {
            if (entry.name == "xl/worksheets/sheet1.xml") {
                return@use zip.readBytes().toString(Charsets.UTF_8)
            }
            entry = zip.nextEntry
        }
        error("sheet1.xml not found")
    }
}
