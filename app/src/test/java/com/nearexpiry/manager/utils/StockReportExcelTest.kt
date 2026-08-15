package com.nearexpiry.manager.utils

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class StockReportExcelTest {

    @Test
    fun `writes sample-compatible header total stock and Item Code error suppression`() {
        val workbook = ByteArrayOutputStream().use { output ->
            StockReportExcel.write(
                out = output,
                rows = listOf(
                    StockReportExcel.Row(
                        posCode = "001234567",
                        description = "Sample carton",
                        uom = "CTN",
                        quantity = 12.5,
                        highlightPosCode = false
                    )
                ),
                title = "Recheck on 15.08.2026",
                sheetName = "15.08.2026"
            )
            output.toByteArray()
        }
        val entries = unzip(workbook)
        val styles = entries.getValue("xl/styles.xml")
        val sheet = entries.getValue("xl/worksheets/sheet1.xml")

        assertTrue(styles.contains("<color rgb=\"FFFFFFFF\"/>"))
        assertTrue(styles.contains("<fgColor rgb=\"FFE6E0EC\"/>"))
        assertTrue(sheet.contains("<c r=\"A2\" s=\"2\""))
        assertTrue(sheet.contains("<c r=\"H3\" s=\"6\"><v>12.5</v></c>"))
        assertTrue(sheet.contains("<c r=\"B3\" s=\"3\" t=\"inlineStr\"><is><t>001234567</t></is></c>"))
        assertTrue(sheet.contains("<ignoredError sqref=\"B3:B1048576\" numberStoredAsText=\"1\""))
    }

    private fun unzip(bytes: ByteArray): Map<String, String> = buildMap {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                put(entry.name, zip.readBytes().toString(Charsets.UTF_8))
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }
}
