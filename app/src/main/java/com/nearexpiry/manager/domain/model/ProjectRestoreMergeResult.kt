package com.nearexpiry.manager.domain.model

/** Summary returned after an atomic CSV/XLSX restore into one project. */
data class ProjectRestoreMergeResult(
    val projectId: Long,
    val inserted: Int,
    val merged: Int,
    val quantityAdded: Double
)
