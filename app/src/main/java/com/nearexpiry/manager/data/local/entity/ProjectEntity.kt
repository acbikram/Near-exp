package com.nearexpiry.manager.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * A Project is an isolated inventory. All [ExpiryItemEntity] rows belong to
 * exactly one project (via projectId), and the app shows/scopes everything
 * — Home stats, History, Scan, Export, notifications — to the currently
 * active project.
 *
 * "Project 1" (id = 1) is created by the v6→v7 migration and all
 * pre-existing items are assigned to it, so upgrading users lose nothing.
 */
@Serializable
@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    /** Hex colour tag (e.g. "#26C6DA") for quick visual identification. */
    val colorHex: String,
    val createdAt: Long,
    /**
     * True once any item in this project has had its order manually changed
     * via Move Up/Down (i.e. has a non-null displayOrder). Purely a display
     * label flag — "Scan Order" vs "Custom Sort" — set the moment the first
     * move happens, and cleared by "Reset to Scan Order".
     */
    val hasCustomSort: Boolean = false,
    /** Latches once this is recognized as a Stock project with at least one item. */
    val isStockMode: Boolean = false
)
