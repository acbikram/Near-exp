package com.nearexpiry.manager.utils

import android.content.Context
import android.net.wifi.WifiManager
import android.text.format.Formatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Speaks the LAN wire-protocol used by the Price Tag PC application. An
 * unpaired installation retains the original discovery and request framing.
 * Once a PC has been paired, callers provide [deviceToken] and every TCP
 * request receives the PTAGAUTH preamble before its existing request magic.
 */
object LocalFileServer {

    const val DISCOVERY_PORT = 8765
    const val TCP_PORT = 8765
    private const val CONNECT_TIMEOUT_MS = 10_000
    private val MAGIC_GDB = "PTAGGDB1".toByteArray(Charsets.US_ASCII)
    private val MAGIC_XLSX = "PTAGXLSX".toByteArray(Charsets.US_ASCII)
    private val MAGIC_PNG = "PTAGPNG1".toByteArray(Charsets.US_ASCII)
    private val DISCOVERY_REQ = "PTAGWHO1".toByteArray(Charsets.US_ASCII)

    data class PcInfo(val name: String, val ip: String, val port: Int) {
        override fun toString() = "$name ($ip:$port)"
    }

    fun getWifiIpAddress(context: Context): String? {
        val wm = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
        @Suppress("DEPRECATION")
        val ip = wm.connectionInfo?.ipAddress ?: return null
        if (ip == 0) return null
        @Suppress("DEPRECATION")
        return Formatter.formatIpAddress(ip)
    }

    /** Broadcasts a legacy discovery request and collects PCs that answer. */
    suspend fun discoverPcs(timeoutMs: Int = 2500): List<PcInfo> = withContext(Dispatchers.IO) {
        val results = mutableListOf<PcInfo>()
        try {
            DatagramSocket().use { sock ->
                sock.broadcast = true
                sock.soTimeout = timeoutMs
                sock.send(
                    DatagramPacket(
                        DISCOVERY_REQ, DISCOVERY_REQ.size,
                        InetAddress.getByName("255.255.255.255"), DISCOVERY_PORT
                    )
                )
                val buf = ByteArray(2048)
                val deadline = System.currentTimeMillis() + timeoutMs
                while (System.currentTimeMillis() < deadline) {
                    try {
                        val reply = DatagramPacket(buf, buf.size)
                        sock.receive(reply)
                        val text = String(buf, 0, reply.length, Charsets.UTF_8)
                        val json = JSONObject(text)
                        results.add(
                            PcInfo(
                                name = json.optString("name", "PC"),
                                ip = json.optString("ip", reply.address.hostAddress ?: ""),
                                port = json.optInt("port", TCP_PORT)
                            )
                        )
                    } catch (_: IOException) {
                        break
                    }
                }
            }
        } catch (_: Exception) {
            // Discovery is intentionally best-effort. The caller shows its own
            // generic connection state without exposing network internals.
        }
        results.distinctBy { "${it.ip}:${it.port}" }
    }

    /**
     * Pulls the product catalog (.db) using the PTAGGDB1 protocol. When
     * [deviceToken] is supplied, PTAGAUTH + token precedes PTAGGDB1; when it is
     * null, the exact pre-pairing legacy frame is retained.
     */
    suspend fun pullCatalogDb(
        pc: PcInfo,
        deviceToken: String? = null,
        onProgress: (bytesReceived: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): ByteArray = withContext(Dispatchers.IO) {
        connectedSocket(pc).use { sock ->
            sock.soTimeout = 300_000 // The PC may regenerate its catalog database.
            val output = DataOutputStream(sock.getOutputStream())
            val input = sock.getInputStream()

            writeRequestStart(output, deviceToken, MAGIC_GDB)
            output.flush()

            val lenBytes = ByteArray(4)
            var read = 0
            while (read < 4) {
                val n = input.read(lenBytes, read, 4 - read)
                if (n < 0) throw IOException("Connection closed before catalog length received")
                read += n
            }
            val totalLen = ((lenBytes[0].toInt() and 0xFF) shl 24) or
                ((lenBytes[1].toInt() and 0xFF) shl 16) or
                ((lenBytes[2].toInt() and 0xFF) shl 8) or
                (lenBytes[3].toInt() and 0xFF)
            if (totalLen <= 0 || totalLen > 100 * 1024 * 1024) {
                throw IOException("Invalid catalog size received from PC")
            }

            val buffer = ByteArray(65_536)
            val bytes = ByteArrayOutputStream(totalLen)
            var received = 0L
            while (received < totalLen) {
                val toRead = minOf(buffer.size.toLong(), totalLen - received).toInt()
                val n = input.read(buffer, 0, toRead)
                if (n < 0) throw IOException("Connection dropped while receiving the catalog")
                bytes.write(buffer, 0, n)
                received += n
                onProgress(received, totalLen.toLong())
            }
            bytes.toByteArray()
        }
    }

    /**
     * Sends an .xlsx report using PTAGXLSX. A paired request is framed as
     * PTAGAUTH + token + PTAGXLSX + the unchanged existing XLSX payload.
     */
    suspend fun sendXlsxToPc(
        pc: PcInfo,
        fileName: String,
        fileBytes: ByteArray,
        deviceToken: String? = null,
        onProgress: (sent: Long, total: Long) -> Unit = { _, _ -> }
    ) = withContext(Dispatchers.IO) {
        connectedSocket(pc).use { sock ->
            sock.soTimeout = 60_000
            val output = DataOutputStream(sock.getOutputStream())
            val input = sock.getInputStream()

            val nameBytes = fileName.toByteArray(Charsets.UTF_8)
            writeRequestStart(output, deviceToken, MAGIC_XLSX)
            output.write(intToBE(nameBytes.size))
            output.write(nameBytes)
            output.write(intToBE(fileBytes.size))

            val total = fileBytes.size.toLong()
            var sent = 0L
            val chunk = 65_536
            while (sent < total) {
                val end = minOf(sent + chunk, total).toInt()
                output.write(fileBytes, sent.toInt(), end - sent.toInt())
                sent = end.toLong()
                onProgress(sent, total)
            }
            output.flush()

            val ack = ByteArray(2)
            var read = 0
            while (read < 2) {
                val n = input.read(ack, read, 2 - read)
                if (n < 0) break
                read += n
            }
            if (read < 2 || ack[0] != 'O'.code.toByte() || ack[1] != 'K'.code.toByte()) {
                throw IOException("The PC did not confirm the report transfer")
            }
        }
    }

    /** Sends the existing PTAGPNG1 connection test, with PTAGAUTH when paired. */
    suspend fun testConnection(pc: PcInfo, deviceToken: String? = null) = withContext(Dispatchers.IO) {
        connectedSocket(pc).use { sock ->
            sock.soTimeout = 10_000
            val output = DataOutputStream(sock.getOutputStream())
            writeRequestStart(output, deviceToken, MAGIC_PNG)
            output.flush()

            // PTAGPNG1 response bytes are owned by the existing PC protocol.
            // A response of any kind proves the authenticated request reached
            // the paired PC; EOF is treated as a rejected or failed connection.
            if (sock.getInputStream().read() < 0) {
                throw IOException("The PC did not answer the connection test")
            }
        }
    }

    private fun connectedSocket(pc: PcInfo): Socket = Socket().apply {
        connect(InetSocketAddress(pc.ip, pc.port), CONNECT_TIMEOUT_MS)
    }

    private fun writeRequestStart(
        output: DataOutputStream,
        deviceToken: String?,
        requestMagic: ByteArray
    ) {
        if (deviceToken != null) {
            PriceTagProtocol.writeAuthPreamble(output, deviceToken)
        }
        output.write(requestMagic)
    }

    private fun intToBE(value: Int): ByteArray = byteArrayOf(
        ((value ushr 24) and 0xFF).toByte(),
        ((value ushr 16) and 0xFF).toByte(),
        ((value ushr 8) and 0xFF).toByte(),
        (value and 0xFF).toByte()
    )
}
