package com.nearexpiry.manager.utils

import android.content.Context
import android.net.wifi.WifiManager
import android.text.format.Formatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.Socket

/**
 * Speaks the LAN wire-protocol used by Price_Tag_Final.py, but only the two
 * parts Near Expiry needs — PC discovery and catalog pull. (The CSV-push /
 * print parts from the Barcode-To-CSV app are intentionally omitted.)
 *
 *  DISCOVERY (UDP port 8765):
 *    Phone broadcasts  "PTAGWHO1"
 *    PC replies        JSON {"name":"…","ip":"…","port":N}
 *
 *  CATALOG PULL (TCP port from discovery reply):
 *    Phone → PC : "PTAGGDB1" (8 bytes)
 *    PC → Phone : LEN (4 bytes big-endian) + products.db bytes (LEN bytes)
 *
 * The PC generates the .db from the current master Excel file (cached;
 * regenerated only when the master changes) and streams it back.
 */
object LocalFileServer {

    const val DISCOVERY_PORT = 8765
    const val TCP_PORT       = 8765
    private val MAGIC_GDB     = "PTAGGDB1".toByteArray(Charsets.US_ASCII)
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

    /** Broadcasts a discovery request and collects all PCs that answer. */
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
                                ip   = json.optString("ip", reply.address.hostAddress ?: ""),
                                port = json.optInt("port", TCP_PORT)
                            )
                        )
                    } catch (_: IOException) { break }
                }
            }
        } catch (_: Exception) {}
        // De-duplicate by ip:port in case multiple broadcasts echo back.
        results.distinctBy { "${it.ip}:${it.port}" }
    }

    /**
     * Pulls the product catalog (.db) from [pc] over the PTAGGDB1 protocol.
     * Returns the raw bytes of the .db file, or throws on error.
     */
    suspend fun pullCatalogDb(
        pc: PcInfo,
        onProgress: (bytesReceived: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): ByteArray = withContext(Dispatchers.IO) {
        Socket(pc.ip, pc.port).use { sock ->
            sock.soTimeout = 300_000   // 5 min — PC may need to regenerate the DB

            val out = sock.getOutputStream()
            val inp = sock.getInputStream()

            // 1. Send magic
            out.write(MAGIC_GDB)
            out.flush()

            // 2. Receive LEN (4 bytes big-endian)
            val lenBytes = ByteArray(4)
            var read = 0
            while (read < 4) {
                val n = inp.read(lenBytes, read, 4 - read)
                if (n < 0) throw IOException("Connection closed before length received")
                read += n
            }
            val totalLen = ((lenBytes[0].toInt() and 0xFF) shl 24) or
                           ((lenBytes[1].toInt() and 0xFF) shl 16) or
                           ((lenBytes[2].toInt() and 0xFF) shl 8) or
                            (lenBytes[3].toInt() and 0xFF)

            if (totalLen <= 0 || totalLen > 100 * 1024 * 1024) {
                throw IOException("Invalid catalog size from PC: $totalLen bytes")
            }

            // 3. Receive .db bytes
            val buf = ByteArray(65536)
            val baos = ByteArrayOutputStream(totalLen)
            var received = 0L
            while (received < totalLen) {
                val toRead = minOf(buf.size.toLong(), totalLen - received).toInt()
                val n = inp.read(buf, 0, toRead)
                if (n < 0) throw IOException("Connection dropped after $received / $totalLen bytes")
                baos.write(buf, 0, n)
                received += n
                onProgress(received, totalLen.toLong())
            }
            baos.toByteArray()
        }
    }
}
