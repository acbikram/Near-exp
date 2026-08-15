package com.nearexpiry.manager.presentation.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EditableUnitTypesTest {

    @Test
    fun `allows only the four editable Unit Type values`() {
        assertEquals("PCS", EditableUnitTypes.normalizedOrNull("pcs"))
        assertEquals("KG", EditableUnitTypes.normalizedOrNull("KG"))
        assertEquals("CTN", EditableUnitTypes.normalizedOrNull(" ctn "))
        assertEquals("OFR", EditableUnitTypes.normalizedOrNull("ofr"))
        assertNull(EditableUnitTypes.normalizedOrNull("PKT"))
        assertNull(EditableUnitTypes.normalizedOrNull(""))
    }

    @Test
    fun `normalizes legacy KGS to editable KG`() {
        assertEquals("KG", EditableUnitTypes.normalizedOrNull("KGS"))
    }
}
