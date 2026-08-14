package com.nearexpiry.manager.domain.model

data class Project(
    val id: Long,
    val name: String,
    val colorHex: String,
    val createdAt: Long,
    val hasCustomSort: Boolean = false,
    /** Permanently true after this project is activated as an inventory/stock check. */
    val isStockMode: Boolean = false
)
