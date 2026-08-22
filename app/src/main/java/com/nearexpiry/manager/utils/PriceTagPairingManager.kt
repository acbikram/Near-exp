package com.nearexpiry.manager.utils

import android.content.Context
import android.provider.Settings
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The short-lived QR payload accepted from the Price Tag PC application.
 * The one-time [code] remains in memory only for the confirmation and pairing
 * request; it is deliberately never persisted.
 */
data class PriceTagPairingPayload(
    val host: String,
    val port: Int,
    val code: String,
    val expiresAtEpochSeconds: Long,
    val pcName: String
)

/**
 * The long-lived authenticated PC connection retained after a successful
 * one-time pairing. This record is stored only in encrypted preferences.
 */
data class PairedPriceTagPc(
    val host: String,
    val port: Int,
    val deviceToken: String,
    val pcName: String,
    val protocol: Int,
    val pairedAtMillis: Long
) {
    fun toPcInfo(): LocalFileServer.PcInfo = LocalFileServer.PcInfo(pcName, host, port)
}

/** The credential record boundary used by pairing and by focused unit tests. */
interface PriceTagPairingStore {
    fun get(): PairedPriceTagPc?
    fun save(pairedPc: PairedPriceTagPc)
    fun clear()
}

/**
 * The production store is backed by an Android Keystore-protected key and
 * encrypted preferences. It retains only the long-lived paired credential;
 * QR one-time codes are intentionally absent from this schema.
 */
@Singleton
class EncryptedPriceTagPairingStore @Inject constructor(
    @ApplicationContext private val context: Context
) : PriceTagPairingStore {
    companion object {
        private const val PREFS_NAME = "price_tag_paired_pc"
        private const val KEY_HOST = "host"
        private const val KEY_PORT = "port"
        private const val KEY_TOKEN = "device_token"
        private const val KEY_PC_NAME = "pc_name"
        private const val KEY_PROTOCOL = "protocol"
        private const val KEY_PAIRED_AT = "paired_at"
    }

    @Suppress("DEPRECATION")
    private fun encryptedPreferences() = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    override fun get(): PairedPriceTagPc? {
        val prefs = encryptedPreferences()
        val host = prefs.getString(KEY_HOST, null)?.trim().orEmpty()
        val token = prefs.getString(KEY_TOKEN, null).orEmpty()
        val port = prefs.getInt(KEY_PORT, -1)
        val protocol = prefs.getInt(KEY_PROTOCOL, -1)
        if (host.isEmpty() || token.isEmpty() || port !in 1_024..65_535 || protocol != PriceTagProtocol.PROTOCOL_VERSION) {
            return null
        }
        return PairedPriceTagPc(
            host = host,
            port = port,
            deviceToken = token,
            pcName = prefs.getString(KEY_PC_NAME, "Price Tag PC").orEmpty().ifBlank { "Price Tag PC" },
            protocol = protocol,
            pairedAtMillis = prefs.getLong(KEY_PAIRED_AT, 0L)
        )
    }

    override fun save(pairedPc: PairedPriceTagPc) {
        encryptedPreferences().edit()
            .putString(KEY_HOST, pairedPc.host)
            .putInt(KEY_PORT, pairedPc.port)
            .putString(KEY_TOKEN, pairedPc.deviceToken)
            .putString(KEY_PC_NAME, pairedPc.pcName)
            .putInt(KEY_PROTOCOL, pairedPc.protocol)
            .putLong(KEY_PAIRED_AT, pairedPc.pairedAtMillis)
            .apply()
    }

    override fun clear() {
        encryptedPreferences().edit().clear().apply()
    }
}

/** Supplies the stable device/install identifier used in the PTAGPAIR frame. */
interface PriceTagDeviceIdProvider {
    fun getStableDeviceId(): String
}

@Singleton
class AndroidPriceTagDeviceIdProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : PriceTagDeviceIdProvider {
    override fun getStableDeviceId(): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?.trim()
            .orEmpty()
        require(androidId.isNotEmpty()) { "This device could not provide a stable installation ID." }
        require(androidId.toByteArray(StandardCharsets.UTF_8).size <= 120) {
            "This device could not provide a valid installation ID."
        }
        return androidId
    }
}

/**
 * Pure wire-format helpers. They intentionally do not log any supplied values,
 * because pairing codes and device tokens are credentials.
 */
object PriceTagProtocol {
    const val PROTOCOL_VERSION = 3
    private const val QR_TYPE = "price_tag_pair"
    private const val MAX_CODE_BYTES = 512
    private const val MAX_DEVICE_ID_BYTES = 120
    private const val MAX_APP_NAME_BYTES = 80
    private const val MAX_DEVICE_TOKEN_BYTES = 8_192

    private val PAIR_MAGIC = "PTAGPAIR".toByteArray(StandardCharsets.US_ASCII)
    private val AUTH_MAGIC = "PTAGAUTH".toByteArray(StandardCharsets.US_ASCII)

    /**
     * Parses and validates a Price Tag PC pairing QR payload. [nowEpochSeconds]
     * is injectable so expiry behavior remains deterministic in unit tests.
     */
    fun parsePairingQr(
        rawPayload: String,
        nowEpochSeconds: Long = System.currentTimeMillis() / 1_000L
    ): Result<PriceTagPairingPayload> = runCatching {
        val json = JSONObject(rawPayload)
        require(json.optInt("v", -1) == PROTOCOL_VERSION) { "This QR code is not supported." }
        require(json.optString("type") == QR_TYPE) { "This QR code is not a Price Tag PC pairing code." }

        val host = json.optString("host").trim()
        val code = json.optString("code")
        val port = json.optInt("port", -1)
        val expiresAt = json.optLong("expiresAt", -1L)
        val pcName = json.optString("pcName").trim().ifBlank { "Price Tag PC" }

        require(host.isNotEmpty() && host.none { it.isISOControl() || it.isWhitespace() }) {
            "The QR code has no valid PC address."
        }
        require(port in 1_024..65_535) { "The QR code has an invalid PC port." }
        require(code.isNotEmpty()) { "The QR code has no pairing code." }
        require(code.toByteArray(StandardCharsets.UTF_8).size <= MAX_CODE_BYTES) {
            "The pairing code is too long."
        }
        require(expiresAt > nowEpochSeconds) { "This pairing QR code has expired. Please scan a new one." }

        PriceTagPairingPayload(
            host = host,
            port = port,
            code = code,
            expiresAtEpochSeconds = expiresAt,
            pcName = pcName
        )
    }

    /** Writes the exact PTAGPAIR frame specified by the PC protocol. */
    fun writePairingFrame(
        output: DataOutputStream,
        code: String,
        deviceId: String,
        appName: String = "Near Expiry Manager"
    ) {
        output.write(PAIR_MAGIC)
        writeUtf8UShort(output, code, "pairing code", MAX_CODE_BYTES)
        writeUtf8UShort(output, deviceId, "device ID", MAX_DEVICE_ID_BYTES)
        writeUtf8UShort(output, appName, "app name", MAX_APP_NAME_BYTES)
    }

    /** Writes the PTAGAUTH credential preamble immediately before a legacy magic. */
    fun writeAuthPreamble(output: DataOutputStream, deviceToken: String) {
        output.write(AUTH_MAGIC)
        writeUtf8UShort(output, deviceToken, "device token", MAX_DEVICE_TOKEN_BYTES)
    }

    private fun writeUtf8UShort(
        output: DataOutputStream,
        value: String,
        fieldName: String,
        maxBytes: Int
    ) {
        require(value.isNotEmpty()) { "$fieldName is required." }
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        require(bytes.size <= maxBytes && bytes.size <= 0xFFFF) { "$fieldName is too long." }
        output.writeShort(bytes.size)
        output.write(bytes)
    }
}

/**
 * Owns secure persistence and the one-time pairing socket exchange. Existing
 * catalog and report requests obtain only [PairedPriceTagPc] from this class;
 * raw QR codes are never returned after pairing and are never written to disk.
 */
@Singleton
class PriceTagPairingManager @Inject constructor(
    private val pairedPcStore: PriceTagPairingStore,
    private val deviceIdProvider: PriceTagDeviceIdProvider
) {
    companion object {
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 10_000
        private const val MAX_RESPONSE_CHARS = 8_192
    }

    suspend fun getPairedPc(): PairedPriceTagPc? = withContext(Dispatchers.IO) {
        pairedPcStore.get()
    }

    /** Erases the encrypted token and all paired-PC metadata. */
    suspend fun forgetPairedPc() = withContext(Dispatchers.IO) {
        pairedPcStore.clear()
    }

    /**
     * Connects to the QR-advertised PC, exchanges PTAGPAIR, validates one JSON
     * response line, then atomically persists the returned credential. A failed
     * attempt never changes an existing saved connection.
     */
    suspend fun pair(payload: PriceTagPairingPayload): PairedPriceTagPc = withContext(Dispatchers.IO) {
        require(payload.expiresAtEpochSeconds > System.currentTimeMillis() / 1_000L) {
            "This pairing QR code has expired. Please scan a new one."
        }

        val deviceId = deviceIdProvider.getStableDeviceId()
        val paired = Socket().use { socket ->
            socket.connect(InetSocketAddress(payload.host, payload.port), CONNECT_TIMEOUT_MS)
            socket.soTimeout = READ_TIMEOUT_MS
            val output = DataOutputStream(socket.getOutputStream())
            PriceTagProtocol.writePairingFrame(output, payload.code, deviceId)
            output.flush()

            val responseLine = readOneJsonLine(socket)
            parsePairingResponse(responseLine, payload)
        }
        persist(paired)
        paired
    }

    /** Uses PTAGAUTH + PTAGPNG1 to verify the currently paired PC. */
    suspend fun testConnection(pairedPc: PairedPriceTagPc): Unit = withContext(Dispatchers.IO) {
        LocalFileServer.testConnection(pairedPc.toPcInfo(), pairedPc.deviceToken)
    }

    private fun readOneJsonLine(socket: Socket): String {
        val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
        val line = StringBuilder()
        while (true) {
            val character = reader.read()
            if (character == -1) break
            if (character == '\n'.code) break
            if (character != '\r'.code) {
                require(line.length < MAX_RESPONSE_CHARS) { "The PC response was too large." }
                line.append(character.toChar())
            }
        }
        return line.toString().takeIf { it.isNotBlank() }
            ?: throw IOException("The PC did not return a pairing response.")
    }

    private fun parsePairingResponse(responseLine: String, payload: PriceTagPairingPayload): PairedPriceTagPc {
        val json = try {
            JSONObject(responseLine)
        } catch (_: Exception) {
            throw IOException("The PC returned an invalid pairing response.")
        }
        if (json.optString("type") != "paired" ||
            json.optInt("protocol", -1) != PriceTagProtocol.PROTOCOL_VERSION
        ) {
            throw IOException("The PC rejected the pairing request. Please scan a fresh QR code and try again.")
        }
        val token = json.optString("deviceToken")
        if (token.isBlank() || token.toByteArray(StandardCharsets.UTF_8).size > 8_192) {
            throw IOException("The PC returned an invalid pairing credential.")
        }
        return PairedPriceTagPc(
            host = payload.host,
            port = payload.port,
            deviceToken = token,
            pcName = json.optString("pcName").trim().ifBlank { payload.pcName },
            protocol = PriceTagProtocol.PROTOCOL_VERSION,
            pairedAtMillis = System.currentTimeMillis()
        )
    }

    private fun persist(pairedPc: PairedPriceTagPc) {
        pairedPcStore.save(pairedPc)
    }
}
