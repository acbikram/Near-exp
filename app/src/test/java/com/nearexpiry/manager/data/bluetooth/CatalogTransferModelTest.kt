package com.nearexpiry.manager.data.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogTransferModelTest {
    private val valid = CatalogTransferModel(
        catalog = listOf(
            CatalogTransferProduct(
                barcode = "000123",
                posCode = "P-1",
                nameEn = "Product",
                nameAr = "منتج",
                uom = "PCS",
                barcodeType = "EA"
            )
        ),
        customProducts = listOf(
            CatalogTransferCustomProduct(
                barcode = "custom-1",
                nameEn = "Manual product",
                nameAr = null,
                unit = "PCS",
                itemCode = "M-1"
            )
        )
    )

    @Test
    fun `valid catalog transfer preserves catalog and custom products`() {
        assertTrue(valid.validate().isSuccess)
        assertEquals("000123", valid.catalog.single().barcode)
        assertEquals("custom-1", valid.customProducts.single().barcode)
    }

    @Test
    fun `wrong protocol and version are rejected`() {
        assertTrue(valid.copy(protocol = "other").validate().isFailure)
        assertTrue(valid.copy(version = 99).validate().isFailure)
    }

    @Test
    fun `empty product barcodes are rejected`() {
        assertTrue(valid.copy(catalog = listOf(valid.catalog.single().copy(barcode = "", posCode = null))).validate().isFailure)
        assertTrue(valid.copy(customProducts = listOf(valid.customProducts.single().copy(barcode = ""))).validate().isFailure)
    }
}
