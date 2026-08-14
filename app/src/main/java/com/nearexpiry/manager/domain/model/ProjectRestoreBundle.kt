package com.nearexpiry.manager.domain.model

import com.nearexpiry.manager.data.local.entity.ExpiryItemEntity

/**
 * Fully validated project payload supplied by a backup restore operation.
 * IDs are deliberately excluded: restore matches existing projects by name
 * and writes items as fresh rows in the selected target project.
 */
data class ProjectRestoreBundle(
    val name: String,
    val colorHex: String,
    val items: List<ExpiryItemEntity>
)
