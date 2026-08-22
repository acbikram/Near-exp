package com.nearexpiry.manager.utils

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores the selected Stock Recheck workbook privately inside the app. The
 * original workbook remains the export master so fonts, colors, column order,
 * formulas, descriptions, and Damage & Expiry values are retained exactly.
 */
@Singleton
class RecheckTemplateStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val templateFile: File
        get() = File(context.filesDir, "stock_recheck_master.xlsx")

    suspend fun replace(bytes: ByteArray) = withContext(Dispatchers.IO) {
        require(bytes.isNotEmpty()) { "The Stock Recheck Excel file is empty." }
        val target = templateFile
        val temporary = File(target.parentFile, "${target.name}.tmp")
        temporary.outputStream().use { it.write(bytes) }
        if (target.exists() && !target.delete()) {
            error("Unable to replace the previous Stock Recheck Excel file.")
        }
        if (!temporary.renameTo(target)) {
            temporary.delete()
            error("Unable to save the Stock Recheck Excel file.")
        }
    }

    suspend fun read(): ByteArray? = withContext(Dispatchers.IO) {
        templateFile.takeIf(File::exists)?.readBytes()
    }

    suspend fun hasTemplate(): Boolean = withContext(Dispatchers.IO) {
        templateFile.exists() && templateFile.length() > 0L
    }

    /** Removes only the saved Stock Recheck Excel template. */
    suspend fun delete() = withContext(Dispatchers.IO) {
        if (templateFile.exists() && !templateFile.delete()) {
            error("Unable to delete the Stock Recheck Excel file.")
        }
    }
}
