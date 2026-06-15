package com.nearexpiry.manager.domain.model

import com.nearexpiry.manager.utils.LanguageManager

data class ExpiryItem(
    val id: Long,
    val barcode: String,
    val expiryDate: String,
    val quantity: Double,
    val createdAt: Long,
    val updatedAt: Long,
    val productName: String? = null,
    val productNameArabic: String? = null,
    val unit: String? = null,
    /** POS/Item Code from column B of the product catalog (posCode). */
    val itemCode: String? = null,
    /** The project (isolated inventory) this item belongs to. */
    val projectId: Long = 1
) {
    /**
     * The product description shown in the UI, in the user's selected
     * app language: Arabic name first when the app is in Arabic (falling
     * back to the English name if no Arabic name is available), otherwise
     * the English name. Falls back further to [itemCode] then [barcode]
     * if no product name was resolved at all.
     *
     * This only affects on-screen display — CSV/JSON export always
     * includes both productName and productNameArabic as separate fields,
     * unchanged.
     */
    val displayName: String
        get() = if (LanguageManager.isArabic()) {
            productNameArabic?.takeIf { it.isNotBlank() }
                ?: productName?.takeIf { it.isNotBlank() }
                ?: itemCode
                ?: barcode
        } else {
            productName?.takeIf { it.isNotBlank() }
                ?: itemCode
                ?: barcode
        }
}
