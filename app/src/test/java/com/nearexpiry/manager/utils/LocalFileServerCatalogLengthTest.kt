package com.nearexpiry.manager.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalFileServerCatalogLengthTest {
    @Test
    fun `decodes big endian catalog length`() {
        assertEquals(12_345, LocalFileServer.decodeBigEndianInt(byteArrayOf(0, 0, 0x30, 0x39)))
    }

    @Test
    fun `decodes little endian catalog length`() {
        assertEquals(12_345, LocalFileServer.decodeLittleEndianInt(byteArrayOf(0x39, 0x30, 0, 0)))
    }
}
