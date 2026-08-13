package com.nearexpiry.manager.presentation.screens.scan

import org.junit.Assert.assertEquals
import org.junit.Test

class ManualBarcodeInputTest {

    @Test
    fun `keeps ASCII barcode digits visible as entered`() {
        assertEquals("1234567890123", normalizeBarcodeDigits("1234567890123"))
    }

    @Test
    fun `normalizes Arabic Indic and Persian digits to ASCII barcode digits`() {
        assertEquals("1234567890", normalizeBarcodeDigits("١٢٣٤٥٦٧٨٩۰"))
    }

    @Test
    fun `ignores non-digit characters from manual barcode input`() {
        assertEquals("12345", normalizeBarcodeDigits("12-3 4A5"))
    }
}
