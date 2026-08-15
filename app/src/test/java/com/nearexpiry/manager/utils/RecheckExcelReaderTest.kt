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
