package com.nearexpiry.manager.utils

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import kotlin.concurrent.thread

class PriceTagPairingProtocolTest {

    @Test
    fun `QR validation accepts protocol 3 and rejects expired or malformed payloads`() {
        val accepted = PriceTagProtocol.parsePairingQr(
            """{"v":3,"type":"price_tag_pair","host":"192.168.1.25","port":8765,"code":"one-time","expiresAt":200,"pcName":"Office PC"}""",
            nowEpochSeconds = 100
        ).getOrThrow()
        assertEquals("192.168.1.25", accepted.host)
        assertEquals(8765, accepted.port)

        val expired = PriceTagProtocol.parsePairingQr(
            """{"v":3,"type":"price_tag_pair","host":"192.168.1.25","port":8765,"code":"one-time","expiresAt":100,"pcName":"Office PC"}""",
            nowEpochSeconds = 100
        )
        assertTrue(expired.isFailure)

        val wrongVersion = PriceTagProtocol.parsePairingQr(
            """{"v":2,"type":"price_tag_pair","host":"192.168.1.25","port":8765,"code":"one-time","expiresAt":200,"pcName":"Office PC"}""",
            nowEpochSeconds = 100
        )
        assertTrue(wrongVersion.isFailure)
    }

    @Test
    fun `PTAGPAIR frame uses exact magic ushort lengths and UTF8 fields`() {
        val bytes = ByteArrayOutputStream().use { byteStream ->
            DataOutputStream(byteStream).use { output ->
                PriceTagProtocol.writePairingFrame(output, "code-✓", "device-123")
            }
            byteStream.toByteArray()
        }
        val input = DataInputStream(bytes.inputStream())
        val magic = ByteArray(8)
        input.readFully(magic)
        assertArrayEquals("PTAGPAIR".toByteArray(StandardCharsets.US_ASCII), magic)
        val codeLength = input.readUnsignedShort()
        assertEquals("code-✓".toByteArray(StandardCharsets.UTF_8).size, codeLength)
        assertEquals("code-✓", input.readUtf8UShort(codeLength))
        val deviceLength = input.readUnsignedShort()
        assertEquals("device-123".toByteArray(StandardCharsets.UTF_8).size, deviceLength)
        assertEquals("device-123", input.readUtf8UShort(deviceLength))
        val appNameLength = input.readUnsignedShort()
        assertEquals("Near Expiry Manager".toByteArray(StandardCharsets.UTF_8).size, appNameLength)
        assertEquals("Near Expiry Manager", input.readUtf8UShort(appNameLength))
        assertEquals(0, input.available())
    }

    @Test
    fun `successful pairing persists response credential while a rejected response preserves old pairing without credential leakage`() = runBlocking {
        val store = InMemoryStore()
        val manager = PriceTagPairingManager(store, FixedDeviceIdProvider("android-device-1"))
        ServerSocket(0).use { server ->
            val received = ByteArrayOutputStream()
            val worker = thread(start = true) {
                server.accept().use { socket ->
                    val input = DataInputStream(socket.getInputStream())
                    val magic = ByteArray(8)
                    input.readFully(magic)
                    received.write(magic)
                    repeat(3) {
                        val length = input.readUnsignedShort()
                        received.write((length ushr 8) and 0xFF)
                        received.write(length and 0xFF)
                        received.write(ByteArray(length).also(input::readFully))
                    }
                    socket.getOutputStream().write(
                        ("""{"type":"paired","protocol":3,"deviceToken":"long-lived-token","pcName":"Counter PC"}""" + "\n")
                            .toByteArray(StandardCharsets.UTF_8)
                    )
                }
            }
            val paired = manager.pair(pairingPayload(server.localPort, "one-time-code"))
            worker.join()
            assertEquals("Counter PC", paired.pcName)
            assertEquals("long-lived-token", store.value?.deviceToken)
            assertArrayEquals("PTAGPAIR".toByteArray(StandardCharsets.US_ASCII), received.toByteArray().copyOfRange(0, 8))
        }

        val old = PairedPriceTagPc("192.168.1.7", 8765, "old-token", "Old PC", 3, 1L)
        store.value = old
        ServerSocket(0).use { server ->
            val worker = thread(start = true) {
                server.accept().use { socket ->
                    // The request is consumed only so the client can receive the rejection line.
                    socket.getInputStream().read(ByteArray(1024))
                    socket.getOutputStream().write(("""{"type":"rejected"}""" + "\n").toByteArray(StandardCharsets.UTF_8))
                }
            }
            try {
                manager.pair(pairingPayload(server.localPort, "new-one-time-code"))
                fail("A rejected pairing response must fail")
            } catch (error: Exception) {
                assertFalse(error.message.orEmpty().contains("new-one-time-code"))
                assertFalse(error.message.orEmpty().contains("old-token"))
            }
            worker.join()
        }
        assertEquals(old, store.value)
        assertNotNull(store.value)
    }

    @Test
    fun `paired PTAGPNG test connection uses PTAGAUTH preamble`() = runBlocking {
        ServerSocket(0).use { server ->
            val captured = ByteArrayOutputStream()
            val worker = thread(start = true) {
                server.accept().use { socket ->
                    val input = socket.getInputStream()
                    val expectedBytes = 8 + 2 + "device-token".toByteArray(StandardCharsets.UTF_8).size + 8
                    while (captured.size() < expectedBytes) {
                        val buffer = ByteArray(expectedBytes - captured.size())
                        val count = input.read(buffer)
                        if (count < 0) break
                        captured.write(buffer, 0, count)
                    }
                    socket.getOutputStream().write(byteArrayOf(1))
                }
            }
            LocalFileServer.testConnection(
                LocalFileServer.PcInfo("Test PC", "127.0.0.1", server.localPort),
                "device-token"
            )
            worker.join()
            val expected = ByteArrayOutputStream().use { stream ->
                DataOutputStream(stream).use { output ->
                    PriceTagProtocol.writeAuthPreamble(output, "device-token")
                    output.write("PTAGPNG1".toByteArray(StandardCharsets.US_ASCII))
                }
                stream.toByteArray()
            }
            assertArrayEquals(expected, captured.toByteArray())
        }
    }

    @Test
    fun `authenticated XLSX frame has PTAGAUTH preamble and legacy XLSX frame stays unchanged`() = runBlocking {
        val fileName = "report.xlsx"
        val fileBytes = byteArrayOf(1, 2, 3)

        val authenticated = captureXlsxRequest(fileName, fileBytes, "saved-device-token")
        val expectedAuthPrefix = ByteArrayOutputStream().use { stream ->
            DataOutputStream(stream).use { PriceTagProtocol.writeAuthPreamble(it, "saved-device-token") }
            stream.toByteArray()
        }
        assertArrayEquals(expectedAuthPrefix, authenticated.copyOfRange(0, expectedAuthPrefix.size))
        assertArrayEquals(
            "PTAGXLSX".toByteArray(StandardCharsets.US_ASCII),
            authenticated.copyOfRange(expectedAuthPrefix.size, expectedAuthPrefix.size + 8)
        )

        val legacy = captureXlsxRequest(fileName, fileBytes, null)
        assertArrayEquals("PTAGXLSX".toByteArray(StandardCharsets.US_ASCII), legacy.copyOfRange(0, 8))
        assertFalse(String(legacy, StandardCharsets.US_ASCII).startsWith("PTAGAUTH"))
        assertEquals(expectedLegacyXlsxFrame(fileName, fileBytes).toList(), legacy.toList())
    }

    private suspend fun captureXlsxRequest(
        fileName: String,
        fileBytes: ByteArray,
        deviceToken: String?
    ): ByteArray {
        ServerSocket(0).use { server ->
            val captured = ByteArrayOutputStream()
            val worker = thread(start = true) {
                server.accept().use { socket ->
                    val input = socket.getInputStream()
                    val buffer = ByteArray(256)
                    while (true) {
                        val count = input.read(buffer)
                        if (count <= 0) break
                        captured.write(buffer, 0, count)
                        if (captured.size() >= expectedLegacyXlsxFrame(fileName, fileBytes).size + (if (deviceToken == null) 0 else 10 + deviceToken.toByteArray().size)) {
                            break
                        }
                    }
                    socket.getOutputStream().write("OK".toByteArray(StandardCharsets.US_ASCII))
                }
            }
            LocalFileServer.sendXlsxToPc(
                pc = LocalFileServer.PcInfo("Test PC", "127.0.0.1", server.localPort),
                fileName = fileName,
                fileBytes = fileBytes,
                deviceToken = deviceToken
            )
            worker.join()
            return captured.toByteArray()
        }
    }

    private fun expectedLegacyXlsxFrame(fileName: String, fileBytes: ByteArray): ByteArray =
        ByteArrayOutputStream().use { stream ->
            DataOutputStream(stream).use { output ->
                val name = fileName.toByteArray(StandardCharsets.UTF_8)
                output.write("PTAGXLSX".toByteArray(StandardCharsets.US_ASCII))
                output.writeInt(name.size)
                output.write(name)
                output.writeInt(fileBytes.size)
                output.write(fileBytes)
            }
            stream.toByteArray()
        }

    private fun pairingPayload(port: Int, code: String) = PriceTagPairingPayload(
        host = "127.0.0.1",
        port = port,
        code = code,
        expiresAtEpochSeconds = Long.MAX_VALUE / 1_000L,
        pcName = "QR PC"
    )

    private fun DataInputStream.readUtf8UShort(length: Int): String {
        val bytes = ByteArray(length)
        readFully(bytes)
        return String(bytes, StandardCharsets.UTF_8)
    }

    private class InMemoryStore(var value: PairedPriceTagPc? = null) : PriceTagPairingStore {
        override fun get(): PairedPriceTagPc? = value
        override fun save(pairedPc: PairedPriceTagPc) { value = pairedPc }
        override fun clear() { value = null }
    }

    private class FixedDeviceIdProvider(private val value: String) : PriceTagDeviceIdProvider {
        override fun getStableDeviceId(): String = value
    }
}
