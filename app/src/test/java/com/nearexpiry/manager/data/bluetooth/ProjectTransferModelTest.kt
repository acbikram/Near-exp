package com.nearexpiry.manager.data.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectTransferModelTest {
    private val valid = ProjectTransferModel(
        sourceProjectId = 7,
        project = ProjectTransferProject(
            name = "Expiry Project",
            colorHex = "#26C6DA",
            createdAt = 100L,
            hasCustomSort = true,
            isStockMode = false
        ),
        items = listOf(
            ProjectTransferItem(
                barcode = "8151352",
                expiryDate = "2026-09-30",
                quantity = 12.0,
                createdAt = 101L,
                updatedAt = 102L,
                productName = "Cleaner",
                productNameArabic = null,
                unit = "PCS",
                itemCode = "8151352",
                displayOrder = null
            )
        )
    )

    @Test
    fun `valid project transfer is accepted with complete metadata`() {
        assertTrue(valid.validate().isSuccess)
        assertEquals("8151352", valid.items.single().itemCode)
        assertEquals("2026-09-30", valid.items.single().expiryDate)
    }

    @Test
    fun `wrong protocol is rejected`() {
        assertTrue(valid.copy(protocol = "other").validate().isFailure)
        assertTrue(valid.copy(version = 99).validate().isFailure)
    }

    @Test
    fun `empty project name and barcode are rejected`() {
        assertTrue(valid.copy(project = valid.project.copy(name = " ")).validate().isFailure)
        assertTrue(valid.copy(items = listOf(valid.items.single().copy(barcode = ""))).validate().isFailure)
    }
}
