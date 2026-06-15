package com.nearexpiry.manager.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A Project is an isolated inventory. All [ExpiryItemEntity] rows belong to
 * exactly one project (via projectId), and the app shows/scopes everything
 * — Home stats, History, Scan, Export, notifications — to the currently
 * active project.
 *
 * "Project 1" (id = 1) is created by the v6→v7 migration and all
 * pre-existing items are assigned to it, so upgrading users lose nothing.
 */
@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    /** Hex colour tag (e.g. "#26C6DA") for quick visual identification. */
    val colorHex: String,
    val createdAt: Long
)
