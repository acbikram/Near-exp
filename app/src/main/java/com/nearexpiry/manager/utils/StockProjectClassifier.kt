package com.nearexpiry.manager.utils

/** Shared Stock Mode qualification for new project names and permanently latched projects. */
object StockProjectClassifier {
    fun isStockProject(isStockMode: Boolean, projectName: String?): Boolean =
        isStockMode || projectName?.let(::hasStockKeyword) == true

    fun hasStockKeyword(projectName: String): Boolean =
        projectName.contains("stock", ignoreCase = true) ||
            projectName.contains("recheck", ignoreCase = true)
}
