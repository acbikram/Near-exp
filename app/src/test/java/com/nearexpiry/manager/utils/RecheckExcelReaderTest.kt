package com.nearexpiry.manager.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class RecheckExcelReaderTest {
    @Test
    fun `imports codes below flexible POS item and barcode headers`() {
        val bytes = workbook(
            """
            <worksheet><sheetData>
              <row r="1"><c r="A1" t="inlineStr"><is><t>Report title</t></is></c></row>
              <row r="2">
                <c r="A2" t="inlineStr"><is><t>POS_CODE</t></is></c>
                <c r="B2" t="inlineStr"><is><t>Item Code</t></is></c>
                <c r="C2" t="inlineStr"><is><t>Barcode Value</t></is></c>
                <c r="D2" t="inlineStr"><is><t>Description</t></is></c>
              </row>
              <row r="3">
                <c r="A3" t="inlineStr"><is><t> pos-001 </t></is></c>
                <c r="B3" t="inlineStr"><is><t>item-002</t></is></c>
                <c r="C3" t="inlineStr"><is><t>123456</t></is></c>
                <c r="D3" t="inlineStr"><is><t>Ignore me</t></is></c>
              </row>
            </sheetData></worksheet>
            """.trimIndent()
        )

        assertEquals(setOf("POS-001", "ITEM-002", "123456"), RecheckExcelReader.readCodes(bytes))
    }

    @Test
    fun `reports source rows duplicates and blank POS rows while preserving unique scan codes`() {
        val bytes = workbook(
            """
            <worksheet><sheetData>
              <row r="1">
                <c r="A1" t="inlineStr"><is><t>Barcode</t></is></c>
                <c r="B1" t="inlineStr"><is><t>POS Code</t></is></c>
                <c r="C1" t="inlineStr"><is><t>Damage Expiry Stock</t></is></c>
                <c r="D1" t="inlineStr"><is><t>Description</t></is></c>
              </row>
              <row r="2">
                <c r="A2" t="inlineStr"><is><t>barcode-a</t></is></c>
                <c r="B2" t="inlineStr"><is><t>pos-001</t></is></c>
                <c r="C2"><v>2.5</v></c>
              </row>
              <row r="3">
                <c r="A3" t="inlineStr"><is><t>barcode-b</t></is></c>
                <c r="B3" t="inlineStr"><is><t>pos-002</t></is></c>
              </row>
              <row r="4">
                <c r="A4" t="inlineStr"><is><t>barcode-c</t></is></c>
                <c r="B4" t="inlineStr"><is><t>pos-001</t></is></c>
              </row>
              <row r="5"><c r="D5" t="inlineStr"><is><t>Missing POS code</t></is></c></row>
            </sheetData></worksheet>
            """.trimIndent()
        )

        val result = RecheckExcelReader.readImport(bytes)

        assertEquals(3, result.sourceCodeRowCount)
        assertEquals(2, result.uniqueCodeCount)
        assertEquals(1, result.duplicateCodeRowCount)
        assertEquals(1, result.blankCodeRowCount)
        assertEquals(listOf("POS-001", "POS-002"), result.rows.map { it.code })
        assertEquals(2.5, result.rows.first().damageExpiryQuantity, 0.0)
    }

    @Test
    fun `captures workbook Sr No for each matching POS Code`() {
        val bytes = workbook(
            """
            <worksheet><sheetData>
              <row r="1">
                <c r="A1" t="inlineStr"><is><t>Sr. No.</t></is></c>
                <c r="B1" t="inlineStr"><is><t>POS_CODE</t></is></c>
              </row>
              <row r="2"><c r="A2"><v>42</v></c><c r="B2" t="inlineStr"><is><t>pos-0042</t></is></c></row>
              <row r="3"><c r="A3"><v>99</v></c><c r="B3" t="inlineStr"><is><t>pos-0099</t></is></c></row>
            </sheetData></worksheet>
            """.trimIndent()
        )

        val rows = RecheckExcelReader.readRows(bytes)

        assertEquals(listOf("POS-0042", "POS-0099"), rows.map { it.code })
        assertEquals(listOf(42, 99), rows.map { it.serialNumber })
    }

    @Test
    fun `captures Damage Expiry Stock for numeric POS Code rows`() {
        val bytes = workbook(
            """
            <worksheet><sheetData>
              <row r="1">
                <c r="A1" t="inlineStr"><is><t>Sr. No.</t></is></c>
                <c r="B1" t="inlineStr"><is><t>POS_CODE</t></is></c>
                <c r="F1" t="inlineStr"><is><t>PHY_QTY</t></is></c>
                <c r="G1" t="inlineStr"><is><t>Damage Expiry Stock</t></is></c>
                <c r="H1" t="inlineStr"><is><t>TOTAL_STOCK</t></is></c>
              </row>
              <row r="2"><c r="A2"><v>10</v></c><c r="B2"><v>8897605</v></c><c r="F2" s="4"/><c r="G2" s="4"><v>5</v></c><c r="H2" s="5"/></row>
            </sheetData></worksheet>
            """.trimIndent()
        )

        val row = RecheckExcelReader.readRows(bytes).single()

        assertEquals("8897605", row.code)
        assertEquals(10, row.serialNumber)
        assertEquals(5.0, row.damageExpiryQuantity, 0.0)
    }

    @Test
    fun `normalizes only a trailing decimal artifact from numeric POS codes`() {
        assertEquals("8600617", RecheckExcelReader.normalizeCode("8600617.0"))
        assertEquals("80006588", RecheckExcelReader.normalizeCode("8.0006588E7"))
        assertEquals("00123", RecheckExcelReader.normalizeCode("00123"))
        assertEquals("POS-1.0", RecheckExcelReader.normalizeCode("POS-1.0"))
    }

    @Test
    fun `ignores workbooks without a supported code header`() {
        val bytes = workbook(
            """
            <worksheet><sheetData>
              <row r="1"><c r="A1" t="inlineStr"><is><t>Description</t></is></c></row>
              <row r="2"><c r="A2" t="inlineStr"><is><t>Olives</t></is></c></row>
            </sheetData></worksheet>
            """.trimIndent()
        )

        assertEquals(emptySet<String>(), RecheckExcelReader.readCodes(bytes))
    }

    private fun workbook(sheetXml: String): ByteArray = ByteArrayOutputStream().use { output ->
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            zip.write(sheetXml.toByteArray())
            zip.closeEntry()
        }
        output.toByteArray()
    }
}
