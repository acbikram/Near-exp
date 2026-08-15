package com.nearexpiry.manager.utils

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/**
 * Reads the global Stock Recheck Excel workbook selected by the user.
 *
 * The sheet layout is deliberately flexible: the reader looks through every
 * worksheet for a header containing POS Code, Item Code, or Barcode (ignoring
 * spaces, underscores, punctuation, and letter case), then imports the values
 * below that same column. This supports the app's own Recheck layout as well
 * as externally prepared stock sheets.
 */
object RecheckExcelReader {
    private val rowRegex = Regex("""<row[^>]*\br="(\d+)"[^>]*>(.*?)</row>""", RegexOption.DOT_MATCHES_ALL)
    private val cellRegex = Regex("""<c\b[^>]*\br="([A-Z]+)(\d+)"([^>]*)>(.*?)</c>|<c\b[^>]*\br="([A-Z]+)(\d+)"([^>]*)/>""", RegexOption.DOT_MATCHES_ALL)
    private val inlineRegex = Regex("""<is>.*?<t[^>]*>(.*?)</t>.*?</is>""", RegexOption.DOT_MATCHES_ALL)
    private val valueRegex = Regex("""<v>(.*?)</v>""", RegexOption.DOT_MATCHES_ALL)
    private val sharedItemRegex = Regex("""<si>(.*?)</si>""", RegexOption.DOT_MATCHES_ALL)
    private val tagRegex = Regex("""<[^>]+>""")

    /** Returns normalized code values from every matching header column. */
    fun readCodes(bytes: ByteArray): Set<String> {
        val workbookEntries = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "xl/sharedStrings.xml" ||
                    (entry.name.startsWith("xl/worksheets/") && entry.name.endsWith(".xml"))
                ) {
                    workbookEntries[entry.name] = zip.readBytes()
                }
                entry = zip.nextEntry
            }
        }

        val sharedStrings = workbookEntries["xl/sharedStrings.xml"]
            ?.toString(Charsets.UTF_8)
            ?.let { xml ->
                sharedItemRegex.findAll(xml).map { match ->
                    unescape(tagRegex.replace(match.groupValues[1], ""))
                }.toList()
            }
            ?: emptyList()

        val allCodes = linkedSetOf<String>()
        workbookEntries
            .filterKeys { it.startsWith("xl/worksheets/") && it.endsWith(".xml") }
            .toSortedMap()
            .values
            .forEach { sheetBytes ->
                val rows = parseRows(sheetBytes.toString(Charsets.UTF_8), sharedStrings)
                allCodes += codesFromSheet(rows)
            }
        return allCodes
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

    private fun codesFromSheet(rows: Map<Int, Map<String, String>>): Set<String> {
        val headers = mutableListOf<Pair<Int, String>>()
        rows.forEach { (rowNumber, cells) ->
            cells.forEach { (column, value) ->
                if (isAcceptedHeader(value)) headers += rowNumber to column
            }
        }
        if (headers.isEmpty()) return emptySet()

        // Each header governs only the cells below that same column. This also
        // supports workbooks that place POS Code and Item Code in different
        // sections or sheets without importing the header text itself.
        return headers
            .asSequence()
            .flatMap { (headerRow, column) ->
                rows.asSequence()
                    .filter { (rowNumber, _) -> rowNumber > headerRow }
                    .mapNotNull { (_, cells) -> cells[column] }
            }
            .filterNot(::isAcceptedHeader)
            .mapNotNull(::normalizeCode)
            .toCollection(linkedSetOf())
    }

    private fun isAcceptedHeader(value: String): Boolean {
        val compact = value
            .lowercase()
            .replace(Regex("""[^a-z0-9]+"""), "")
        return compact.contains("poscode") ||
            compact.contains("itemcode") ||
            compact.contains("barcode")
    }

    private fun cellText(inner: String, attributes: String, sharedStrings: List<String>): String {
        inlineRegex.find(inner)?.let { return unescape(it.groupValues[1]) }
        if (attributes.contains("t=\"s\"")) {
            val index = valueRegex.find(inner)?.groupValues?.get(1)?.toIntOrNull()
            if (index != null && index in sharedStrings.indices) return sharedStrings[index]
        }
        return valueRegex.find(inner)?.let { unescape(it.groupValues[1]) }.orEmpty()
    }

    private fun normalizeCode(value: String?): String? = value
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
