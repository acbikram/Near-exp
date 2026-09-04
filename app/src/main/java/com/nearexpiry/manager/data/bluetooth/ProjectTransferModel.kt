package com.nearexpiry.manager.data.bluetooth

import com.nearexpiry.manager.data.local.entity.ExpiryItemEntity
import com.nearexpiry.manager.data.local.entity.ProjectEntity
import kotlinx.serialization.Serializable

/** Wire-safe snapshot for transferring exactly one Near Expiry project. */
@Serializable
data class ProjectTransferModel(
    val protocol: String = PROTOCOL,
    val version: Int = VERSION,
    val sourceProjectId: Long,
    val project: ProjectTransferProject,
    val items: List<ProjectTransferItem>,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val PROTOCOL = "near-expiry-project-sync"
        const val VERSION = 1
    }
}

@Serializable
data class ProjectTransferProject(
    val name: String,
    val colorHex: String,
    val createdAt: Long,
    val hasCustomSort: Boolean,
    val isStockMode: Boolean
)

@Serializable
data class ProjectTransferItem(
    val barcode: String,
    val expiryDate: String,
    val quantity: Double,
    val createdAt: Long,
    val updatedAt: Long,
    val productName: String?,
    val productNameArabic: String?,
    val unit: String?,
    val itemCode: String?,
    val displayOrder: Long?
)

fun ProjectEntity.toTransferProject() = ProjectTransferProject(
    name = name,
    colorHex = colorHex,
    createdAt = createdAt,
    hasCustomSort = hasCustomSort,
    isStockMode = isStockMode
)

fun ExpiryItemEntity.toTransferItem() = ProjectTransferItem(
    barcode = barcode,
    expiryDate = expiryDate,
    quantity = quantity,
    createdAt = createdAt,
    updatedAt = updatedAt,
    productName = productName,
    productNameArabic = productNameArabic,
    unit = unit,
    itemCode = itemCode,
    displayOrder = displayOrder
)

fun ProjectTransferItem.toEntity(projectId: Long, now: Long = System.currentTimeMillis()) =
    ExpiryItemEntity(
        barcode = barcode,
        expiryDate = expiryDate,
        quantity = quantity,
        createdAt = createdAt,
        updatedAt = now,
        productName = productName,
        productNameArabic = productNameArabic,
        unit = unit,
        itemCode = itemCode,
        projectId = projectId,
        displayOrder = displayOrder
    )

private const val MAX_TRANSFER_ITEMS = 50_000

fun ProjectTransferModel.validate(): Result<ProjectTransferModel> = runCatching {
    require(protocol == ProjectTransferModel.PROTOCOL) { "Unsupported project-sync protocol" }
    require(version == ProjectTransferModel.VERSION) { "Unsupported project-sync version" }
    require(project.name.isNotBlank()) { "Project name is empty" }
    require(project.name.length <= 200) { "Project name is too long" }
    require(items.size <= MAX_TRANSFER_ITEMS) { "Project contains too many items" }
    require(items.all { it.barcode.isNotBlank() && it.barcode.length <= 512 }) { "Invalid barcode" }
    require(items.all { it.expiryDate.length <= 32 }) { "Invalid expiry date" }
    this
}
