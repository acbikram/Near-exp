package com.nearexpiry.manager.utils

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Applies Stock quantities directly to the selected Recheck workbook. All
 * untouched ZIP entries, rows, cells, styles, fonts, colours, widths, merged
 * cells, and sheet settings are copied verbatim. Only Physical Quantity and
 * Total Quantity numeric cells for rows under a detected POS Code header are
 * changed.
 */
object RecheckTemplateWorkbook {
    private enum class HeaderRole { CODE, PHYSICAL, DAMAGE, TOTAL }

    private val rowRegex = Regex("""<row([^>]*)>(.*?)</row>""", RegexOption.DOT_MATCHES_ALL)
    private val rowNumberRegex = Regex("""\br="(\d+)"""")
    private val cellRegex = Regex("""<c\b([^>]*)\br="([A-Z]+)(\d+)"([^>]*)>(.*?)</c>|<c\b([^>]*)\br="([A-Z]+)(\d+)"([^>]*)/>""", RegexOption.DOT_MATCHES_ALL)
    private val valueRegex = Regex("""<v>(.*?)</v>""", RegexOption.DOT_MATCHES_ALL)
    private val inlineRegex = Regex("""<is>.*?<t[^>]*>(.*?)</t>.*?</is>""", RegexOption.DOT_MATCHES_ALL)
    private val sharedItemRegex = Regex("""<si>(.*?)</si>""", RegexOption.DOT_MATCHES_ALL)
    private val tagRegex = Regex("""<[^>]+>""")

    /**
     * Returns a copy of [templateBytes] with every POS-code data row updated.
     * Codes absent from [quantitiesByCode] receive physical quantity zero. Any
     * existing Damage & Expiry quantity remains unchanged and is added to the
     * recomputed Total Quantity.
     */
    fun applyQuantities(templateBytes: ByteArray, quantitiesByCode: Map<String, Double>): ByteArray {
        val entries = unzip(templateBytes)
        val sharedStrings = sharedStrings(entries["xl/sharedStrings.xml"])
        var updated = false

        // The first sheet containing the required Recheck headers is the master
        // data sheet. Leave all other worksheets byte-for-byte unchanged.
        entries.keys
            .filter { it.startsWith("xl/worksheets/") && it.endsWith(".xml") }
            .sorted()
            .forEach { sheetPath ->
                if (updated) return@forEach
                val original = entries.getValue(sheetPath).toString(Charsets.UTF_8)
                val result = updateSheet(original, sharedStrings, quantitiesByCode)
                if (result.updatedRows > 0) {
                    entries[sheetPath] = result.xml.toByteArray(Charsets.UTF_8)
                    updated = true
                }
            }

        check(updated) {
            "The Stock Recheck Excel file needs POS Code and Physical Quantity columns with at least one item row."
        }
        return zip(entries)
    }

    private data class SheetResult(val xml: String, val updatedRows: Int)

    private fun updateSheet(
        sheetXml: String,
        sharedStrings: List<String>,
        quantitiesByCode: Map<String, Double>
    ): SheetResult {
        val parsedRows = parseRows(sheetXml, sharedStrings)
        val header = parsedRows.firstNotNullOfOrNull { (rowNumber, cells) ->
            val roles = cells.mapNotNull { (column, value) ->
                headerRole(value)?.let { it to column }
            }.toMap()
            if (roles[HeaderRole.CODE] != null && roles[HeaderRole.PHYSICAL] != null) rowNumber to roles else null
        } ?: return SheetResult(sheetXml, 0)

        val headerRow = header.first
        val columns = header.second
        val codeColumn = columns.getValue(HeaderRole.CODE)
        val physicalColumn = columns.getValue(HeaderRole.PHYSICAL)
        val damageColumn = columns[HeaderRole.DAMAGE]
        val totalColumn = columns[HeaderRole.TOTAL]
        var updatedRows = 0

        val updatedXml = rowRegex.replace(sheetXml) { rowMatch ->
            val attributes = rowMatch.groupValues[1]
            val rowNumber = rowNumberRegex.find(attributes)?.groupValues?.get(1)?.toIntOrNull()
                ?: return@replace rowMatch.value
            if (rowNumber <= headerRow) return@replace rowMatch.value

            val cells = parseCells(rowMatch.groupValues[2], sharedStrings)
            val code = RecheckExcelReader.normalizeCode(cells[codeColumn]?.value)
                ?: return@replace rowMatch.value
            val physical = quantitiesByCode[code] ?: 0.0
            val damage = damageColumn?.let { column -> cells[column]?.value?.toDoubleOrZero() } ?: 0.0
            val total = physical + damage
            updatedRows++

            var rowInner = rowMatch.groupValues[2]
            rowInner = replaceNumericCell(rowInner, physicalColumn, rowNumber, physical)
            if (totalColumn != null) {
                rowInner = replaceNumericCell(rowInner, totalColumn, rowNumber, total)
            }
            "<row$attributes>$rowInner</row>"
        }
        return SheetResult(updatedXml, updatedRows)
    }

    private data class ParsedCell(val value: String, val raw: String)

    private fun parseRows(sheetXml: String, sharedStrings: List<String>): Map<Int, Map<String, ParsedCell>> =
        rowRegex.findAll(sheetXml).mapNotNull { match ->
            val rowNumber = rowNumberRegex.find(match.groupValues[1])?.groupValues?.get(1)?.toIntOrNull()
                ?: return@mapNotNull null
            rowNumber to parseCells(match.groupValues[2], sharedStrings)
        }.toMap(linkedMapOf())

    private fun parseCells(rowInner: String, sharedStrings: List<String>): Map<String, ParsedCell> {
        val cells = linkedMapOf<String, ParsedCell>()
        cellRegex.findAll(rowInner).forEach { match ->
            val (column, attributes, inner) = if (match.groupValues[2].isNotEmpty()) {
                Triple(match.groupValues[2], match.groupValues[1] + match.groupValues[4], match.groupValues[5])
            } else {
                Triple(match.groupValues[7], match.groupValues[6] + match.groupValues[9], "")
            }
            cells[column] = ParsedCell(cellText(inner, attributes, sharedStrings), match.value)
        }
        return cells
    }

    private fun replaceNumericCell(rowInner: String, column: String, row: Int, value: Double): String {
        val reference = "$column$row"
        val numericCellRegex = Regex("""<c\b(?=[^>]*\br="$reference")[^>]*>.*?</c>|<c\b(?=[^>]*\br="$reference")[^>]*/>""", RegexOption.DOT_MATCHES_ALL)
        val numericValue = QuantityFormatter.format(value)
        return if (numericCellRegex.containsMatchIn(rowInner)) {
            numericCellRegex.replace(rowInner) { match ->
                val attributes = match.value.substringAfter("<c").substringBefore('>')
                    .replace(Regex("""\s+t="[^"]*""""), "")
                "<c$attributes><v>$numericValue</v></c>"
            }
        } else {
            "$rowInner<c r=\"$reference\"><v>$numericValue</v></c>"
        }
    }

    private fun headerRole(value: String): HeaderRole? {
        val compact = value.lowercase().replace(Regex("""[^a-z0-9]+"""), "")
        return when {
            compact.contains("poscode") || compact.contains("itemcode") || compact.contains("barcode") -> HeaderRole.CODE
            compact.contains("damage") || compact.contains("expiryquantity") || compact.contains("damagestock") -> HeaderRole.DAMAGE
            compact.contains("total") && (compact.contains("stock") || compact.contains("qty") || compact.contains("quantity")) -> HeaderRole.TOTAL
            compact.contains("physical") || compact.contains("phyqty") || compact.contains("physicalqty") ||
                (compact.contains("qty") && !compact.contains("damage") && !compact.contains("total")) -> HeaderRole.PHYSICAL
            else -> null
        }
    }

    private fun cellText(inner: String, attributes: String, sharedStrings: List<String>): String {
        inlineRegex.find(inner)?.let { return unescape(it.groupValues[1]) }
        if (attributes.contains("t=\"s\"")) {
            val index = valueRegex.find(inner)?.groupValues?.get(1)?.toIntOrNull()
            if (index != null && index in sharedStrings.indices) return sharedStrings[index]
        }
        return valueRegex.find(inner)?.let { unescape(it.groupValues[1]) }.orEmpty()
    }

    private fun sharedStrings(bytes: ByteArray?): List<String> = bytes
        ?.toString(Charsets.UTF_8)
        ?.let { xml ->
            sharedItemRegex.findAll(xml).map { match ->
                unescape(tagRegex.replace(match.groupValues[1], ""))
            }.toList()
        }
        ?: emptyList()

    private fun String.toDoubleOrZero(): Double =
        trim().replace(",", "").toDoubleOrNull() ?: 0.0

    private fun unzip(bytes: ByteArray): LinkedHashMap<String, ByteArray> {
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) entries[entry.name] = zip.readBytes()
                entry = zip.nextEntry
            }
        }
        return entries
    }

    private fun zip(entries: LinkedHashMap<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun unescape(value: String): String = value
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
}
