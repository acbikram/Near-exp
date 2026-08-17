package com.nearexpiry.manager.utils

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/**
 * Reads the selected Stock Recheck workbook without assuming fixed column
 * letters. A POS Code / Item Code / Barcode header is required; description and
 * UOM headers are optional and are retained when present. Source-row order is
 * preserved exactly for Stock History and template-driven export.
 */
object RecheckExcelReader {
    data class Row(
        val code: String,
        val sortOrder: Int,
        val description: String,
        val uom: String
    )

    private enum class HeaderRole { CODE, DESCRIPTION, UOM }

    private val rowRegex = Regex("""<row[^>]*\br="(\d+)"[^>]*>(.*?)</row>""", RegexOption.DOT_MATCHES_ALL)
    private val cellRegex = Regex("""<c\b[^>]*\br="([A-Z]+)(\d+)"([^>]*)>(.*?)</c>|<c\b[^>]*\br="([A-Z]+)(\d+)"([^>]*)/>""", RegexOption.DOT_MATCHES_ALL)
    private val inlineRegex = Regex("""<is>.*?<t[^>]*>(.*?)</t>.*?</is>""", RegexOption.DOT_MATCHES_ALL)
    private val valueRegex = Regex("""<v>(.*?)</v>""", RegexOption.DOT_MATCHES_ALL)
    private val sharedItemRegex = Regex("""<si>(.*?)</si>""", RegexOption.DOT_MATCHES_ALL)
    private val tagRegex = Regex("""<[^>]+>""")

    /**
     * Returns all distinct data rows from the first workbook worksheet that
     * contains a supported code header. Duplicate codes retain their first
     * source row because Stock scans and History identify an item by code.
     */
    fun readRows(bytes: ByteArray): List<Row> {
        val entries = workbookEntries(bytes)
        val sharedStrings = sharedStrings(entries["xl/sharedStrings.xml"])
        val uniqueRows = linkedMapOf<String, Row>()

        entries
            .filterKeys { it.startsWith("xl/worksheets/") && it.endsWith(".xml") }
            .toSortedMap()
            .values
            .forEach { sheetBytes ->
                val rows = rowsFromSheet(parseRows(sheetBytes.toString(Charsets.UTF_8), sharedStrings))
                if (rows.isNotEmpty()) {
                    rows.forEach { row -> uniqueRows.putIfAbsent(row.code, row) }
                    // The selected workbook is the master template. Use the
                    // first matching sheet rather than combining unrelated tabs.
                    return uniqueRows.values.mapIndexed { index, row -> row.copy(sortOrder = index) }
                }
            }
        return emptyList()
    }

    /**
     * Compatibility helper for existing callers that need every code under any
     * POS Code, Item Code, or Barcode header. Template-driven flows use
     * [readRows], which intentionally selects one primary source-code column.
     */
    fun readCodes(bytes: ByteArray): Set<String> {
        val entries = workbookEntries(bytes)
        val sharedStrings = sharedStrings(entries["xl/sharedStrings.xml"])
        entries
            .filterKeys { it.startsWith("xl/worksheets/") && it.endsWith(".xml") }
            .toSortedMap()
            .values
            .forEach { sheetBytes ->
                val rows = parseRows(sheetBytes.toString(Charsets.UTF_8), sharedStrings)
                val header = rows.entries.firstOrNull { (_, cells) ->
                    cells.values.any { headerRole(it) == HeaderRole.CODE }
                } ?: return@forEach
                val codeColumns = header.value
                    .filterValues { headerRole(it) == HeaderRole.CODE }
                    .keys
                return rows
                    .asSequence()
                    .filter { (rowNumber, _) -> rowNumber > header.key }
                    .flatMap { (_, cells) -> codeColumns.asSequence().mapNotNull { column -> normalizeCode(cells[column]) } }
                    .toCollection(linkedSetOf())
            }
        return emptySet()
    }

    private fun workbookEntries(bytes: ByteArray): Map<String, ByteArray> {
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "xl/sharedStrings.xml" ||
                    (entry.name.startsWith("xl/worksheets/") && entry.name.endsWith(".xml"))
                ) {
                    entries[entry.name] = zip.readBytes()
                }
                entry = zip.nextEntry
            }
        }
        return entries
    }

    private fun sharedStrings(bytes: ByteArray?): List<String> = bytes
        ?.toString(Charsets.UTF_8)
        ?.let { xml ->
            sharedItemRegex.findAll(xml).map { match ->
                unescape(tagRegex.replace(match.groupValues[1], ""))
            }.toList()
        }
        ?: emptyList()

    private fun rowsFromSheet(rows: Map<Int, Map<String, String>>): List<Row> {
        val header = rows.entries.firstNotNullOfOrNull { (rowNumber, cells) ->
            val roles = cells.mapNotNull { (column, value) ->
                headerRole(value)?.let { role -> role to column }
            }.toMap()
            roles[HeaderRole.CODE]?.let { rowNumber to roles }
        } ?: return emptyList()

        val headerRow = header.first
        val columns = header.second
        val codeColumn = columns.getValue(HeaderRole.CODE)
        val descriptionColumn = columns[HeaderRole.DESCRIPTION]
        val uomColumn = columns[HeaderRole.UOM]

        return rows
            .asSequence()
            .filter { (rowNumber, _) -> rowNumber > headerRow }
            .mapNotNull { (_, cells) ->
                normalizeCode(cells[codeColumn])?.let { code ->
                    Row(
                        code = code,
                        sortOrder = 0,
                        description = cells[descriptionColumn].orEmpty().trim(),
                        uom = cells[uomColumn].orEmpty().trim()
                    )
                }
            }
            .toList()
    }

    private fun headerRole(value: String): HeaderRole? {
        val compact = value.lowercase().replace(Regex("""[^a-z0-9]+"""), "")
        return when {
            compact.contains("poscode") || compact.contains("itemcode") || compact.contains("barcode") -> HeaderRole.CODE
            compact.contains("itemdescription") || compact == "description" ||
                compact.contains("productdescription") || compact.contains("itemname") -> HeaderRole.DESCRIPTION
            compact == "uom" || compact.contains("unitofmeasure") || compact == "unit" -> HeaderRole.UOM
            else -> null
        }
    }

    private fun parseRows(sheetXml: String, sharedStrings: List<String>): Map<Int, Map<String, String>> {
        val rows = linkedMapOf<Int, Map<String, String>>()
        for (rowMatch in rowRegex.findAll(sheetXml)) {
            val rowNumber = rowMatch.groupValues[1].toIntOrNull() ?: continue
            val cells = linkedMapOf<String, String>()
            for (cellMatch in cellRegex.findAll(rowMatch.groupValues[2])) {
                val (column, attributes, inner) = if (cellMatch.groupValues[1].isNotEmpty()) {
                    Triple(cellMatch.groupValues[1], cellMatch.groupValues[3], cellMatch.groupValues[4])
                } else {
                    Triple(cellMatch.groupValues[5], cellMatch.groupValues[7], "")
                }
                cells[column] = cellText(inner, attributes, sharedStrings)
            }
            if (cells.isNotEmpty()) rows[rowNumber] = cells
        }
        return rows
    }

    private fun cellText(inner: String, attributes: String, sharedStrings: List<String>): String {
        inlineRegex.find(inner)?.let { return unescape(it.groupValues[1]) }
        if (attributes.contains("t=\"s\"")) {
            val index = valueRegex.find(inner)?.groupValues?.get(1)?.toIntOrNull()
            if (index != null && index in sharedStrings.indices) return sharedStrings[index]
        }
        return valueRegex.find(inner)?.let { unescape(it.groupValues[1]) }.orEmpty()
    }

    fun normalizeCode(value: String?): String? = value
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.uppercase()

    private fun unescape(value: String): String = value
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
}
