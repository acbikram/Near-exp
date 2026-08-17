package com.nearexpiry.manager.utils

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/**
 * Reads the selected Stock Recheck workbook without assuming fixed column
 * letters. The POS Code column is preferred whenever it exists; Item Code and
 * Barcode are only fallbacks. Description, UOM, and Damage/Expiry headers are
 * optional and may appear in any position.
 */
object RecheckExcelReader {
    data class Row(
        val code: String,
        val sortOrder: Int,
        val description: String,
        val uom: String,
        val damageExpiryQuantity: Double,
        val serialNumber: Int?
    )

    /**
     * Import diagnostics distinguish physical POS-code rows in the workbook
     * from the unique logical codes used for scan matching. The workbook itself
     * remains the export master, so duplicate template rows are never removed
     * from the exported Excel file.
     */
    data class ImportResult(
        val rows: List<Row>,
        val sourceCodeRowCount: Int,
        val duplicateCodeRowCount: Int,
        val blankCodeRowCount: Int,
        /** Positive Damage/Expiry rows in the source workbook, before code deduplication. */
        val damageExpiryItemCount: Int = 0,
        /** Total positive Damage/Expiry quantity in the source workbook. */
        val damageExpiryTotal: Double = 0.0
    ) {
        val uniqueCodeCount: Int get() = rows.size
    }

    private data class SheetRows(
        val rows: List<Row>,
        val sourceCodeRowCount: Int,
        val blankCodeRowCount: Int
    )

    private enum class HeaderRole { CODE, DESCRIPTION, UOM, DAMAGE, SERIAL }

    private val rowRegex = Regex("""<row[^>]*\br="(\d+)"[^>]*>(.*?)</row>""", RegexOption.DOT_MATCHES_ALL)
    // A blank styled cell such as <c r="F12" s="4"/> must not capture
    // the following column's closing tag. This protects Damage/Expiry parsing.
    private val cellRegex = Regex(
        """<c\b[^>]*\br="([A-Z]+)(\d+)"([^>]*?)(?<!/)>(.*?)</c>|<c\b[^>]*\br="([A-Z]+)(\d+)"([^>]*)/>""",
        RegexOption.DOT_MATCHES_ALL
    )
    private val inlineRegex = Regex("""<is>.*?<t[^>]*>(.*?)</t>.*?</is>""", RegexOption.DOT_MATCHES_ALL)
    private val valueRegex = Regex("""<v>(.*?)</v>""", RegexOption.DOT_MATCHES_ALL)
    private val sharedItemRegex = Regex("""<si>(.*?)</si>""", RegexOption.DOT_MATCHES_ALL)
    private val tagRegex = Regex("""<[^>]+>""")

    /** Returns only the distinct logical codes used by Stock scan matching. */
    fun readRows(bytes: ByteArray): List<Row> = readImport(bytes).rows

    /**
     * Reads the master Recheck sheet and reports exactly how its source rows
     * became scan-matchable codes. A duplicate POS code counts once for scanning
     * and History, but remains in the untouched export template.
     */
    fun readImport(bytes: ByteArray): ImportResult {
        val entries = workbookEntries(bytes)
        val sharedStrings = sharedStrings(entries["xl/sharedStrings.xml"])
        var firstHeaderSheet: SheetRows? = null

        entries
            .filterKeys { it.startsWith("xl/worksheets/") && it.endsWith(".xml") }
            .toSortedMap()
            .values
            .forEach { sheetBytes ->
                val sheetRows = rowsFromSheet(parseRows(sheetBytes.toString(Charsets.UTF_8), sharedStrings))
                    ?: return@forEach
                if (firstHeaderSheet == null) firstHeaderSheet = sheetRows
                if (sheetRows.sourceCodeRowCount > 0) {
                    return toImportResult(sheetRows)
                }
            }

        return firstHeaderSheet?.let(::toImportResult)
            ?: ImportResult(emptyList(), 0, 0, 0)
    }

    private fun toImportResult(sheetRows: SheetRows): ImportResult {
        val uniqueRows = linkedMapOf<String, Row>()
        sheetRows.rows.forEach { row -> uniqueRows.putIfAbsent(row.code, row) }
        val orderedRows = uniqueRows.values.mapIndexed { index, row -> row.copy(sortOrder = index) }
        val positiveDamageRows = sheetRows.rows.filter { it.damageExpiryQuantity > 0.0 }
        return ImportResult(
            rows = orderedRows,
            sourceCodeRowCount = sheetRows.sourceCodeRowCount,
            duplicateCodeRowCount = sheetRows.rows.size - orderedRows.size,
            blankCodeRowCount = sheetRows.blankCodeRowCount,
            damageExpiryItemCount = positiveDamageRows.size,
            damageExpiryTotal = positiveDamageRows.sumOf { it.damageExpiryQuantity }
        )
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

    private fun rowsFromSheet(rows: Map<Int, Map<String, String>>): SheetRows? {
        val header = rows.entries.firstNotNullOfOrNull { (rowNumber, cells) ->
            primaryCodeColumn(cells)?.let { codeColumn ->
                rowNumber to headerColumns(cells, codeColumn)
            }
        } ?: return null

        val headerRow = header.first
        val columns = header.second
        val codeColumn = columns.getValue(HeaderRole.CODE)
        val descriptionColumn = columns[HeaderRole.DESCRIPTION]
        val uomColumn = columns[HeaderRole.UOM]
        val damageColumn = columns[HeaderRole.DAMAGE]
        val serialColumn = columns[HeaderRole.SERIAL]
        val sourceRows = mutableListOf<Row>()
        var blankCodeRows = 0

        rows.asSequence()
            .filter { (rowNumber, _) -> rowNumber > headerRow }
            .forEach { (_, cells) ->
                val code = normalizeCode(cells[codeColumn])
                if (code == null) {
                    if (cells.values.any { it.isNotBlank() }) blankCodeRows++
                    return@forEach
                }
                sourceRows += Row(
                    code = code,
                    sortOrder = 0,
                    description = cells[descriptionColumn].orEmpty().trim(),
                    uom = cells[uomColumn].orEmpty().trim(),
                    damageExpiryQuantity = cells[damageColumn]
                        ?.trim()
                        ?.replace(",", "")
                        ?.toDoubleOrNull()
                        ?: 0.0,
                    serialNumber = cells[serialColumn].toSerialNumberOrNull()
                )
            }

        return SheetRows(
            rows = sourceRows,
            sourceCodeRowCount = sourceRows.size,
            blankCodeRowCount = blankCodeRows
        )
    }

    /** Ensures POS Code wins over Item Code and Barcode when multiple headers exist. */
    private fun primaryCodeColumn(cells: Map<String, String>): String? = cells
        .asSequence()
        .filter { (_, value) -> headerRole(value) == HeaderRole.CODE }
        .minWithOrNull(
            compareBy<Map.Entry<String, String>> { codeHeaderPriority(it.value) }
                .thenBy { it.key }
        )
        ?.key

    private fun headerColumns(cells: Map<String, String>, codeColumn: String): Map<HeaderRole, String> {
        val columns = linkedMapOf<HeaderRole, String>()
        columns[HeaderRole.CODE] = codeColumn
        cells.forEach { (column, value) ->
            when (val role = headerRole(value)) {
                HeaderRole.DESCRIPTION, HeaderRole.UOM, HeaderRole.DAMAGE, HeaderRole.SERIAL -> columns.putIfAbsent(role, column)
                else -> Unit
            }
        }
        return columns
    }

    private fun codeHeaderPriority(value: String): Int {
        val compact = compactHeader(value)
        return when {
            compact.contains("poscode") -> 0
            compact.contains("itemcode") -> 1
            compact.contains("barcode") -> 2
            else -> 3
        }
    }

    private fun headerRole(value: String): HeaderRole? {
        val compact = compactHeader(value)
        return when {
            compact == "srno" || compact == "serialno" || compact == "serialnumber" || compact == "sno" -> HeaderRole.SERIAL
            compact.contains("poscode") || compact.contains("itemcode") || compact.contains("barcode") -> HeaderRole.CODE
            compact.contains("itemdescription") || compact == "description" ||
                compact.contains("productdescription") || compact.contains("itemname") -> HeaderRole.DESCRIPTION
            compact == "uom" || compact.contains("unitofmeasure") || compact == "unit" -> HeaderRole.UOM
            compact.contains("damage") || compact.contains("expiryquantity") || compact.contains("damagestock") -> HeaderRole.DAMAGE
            else -> null
        }
    }

    private fun compactHeader(value: String): String =
        value.lowercase().replace(Regex("""[^a-z0-9]+"""), "")

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

    /**
     * Keeps text POS codes intact while normalizing Excel's numeric artifacts,
     * including trailing .0 and scientific notation such as 8.0006588E7.
     * Leading-zero text codes are deliberately preserved.
     */
    fun normalizeCode(value: String?): String? {
        val raw = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val isExcelNumericArtifact = raw.matches(Regex("""^\d+\.0+$""")) ||
            raw.matches(Regex("""^\d+(?:\.\d+)?[eE][+-]?\d+$"""))
        val normalizedNumeric = if (isExcelNumericArtifact) {
            runCatching { java.math.BigDecimal(raw).toBigIntegerExact().toString() }.getOrNull()
        } else {
            null
        }
        return (normalizedNumeric ?: raw).uppercase()
    }

    private fun String?.toSerialNumberOrNull(): Int? {
        val value = this?.trim()?.replace(",", "") ?: return null
        val numeric = value.toDoubleOrNull() ?: return null
        return numeric.toInt().takeIf { numeric > 0.0 && numeric % 1.0 == 0.0 }
    }

    private fun unescape(value: String): String = value
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
}
