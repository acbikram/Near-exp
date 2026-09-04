package com.nearexpiry.manager.data.bluetooth

import android.bluetooth.BluetoothAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BluetoothTransferManager @Inject constructor() {
    data class PairedDevice(val name: String, val address: String)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun pairedDevices(): List<PairedDevice> = runCatching {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return emptyList()
        adapter.bondedDevices.orEmpty()
            .sortedBy { it.name.orEmpty().lowercase() }
            .map { PairedDevice(it.name?.takeIf(String::isNotBlank) ?: it.address, it.address) }
    }.getOrDefault(emptyList())

    suspend fun send(deviceAddress: String, model: ProjectTransferModel): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                withTimeout(SOCKET_TIMEOUT_MS.toLong()) {
                    val adapter = BluetoothAdapter.getDefaultAdapter()
                        ?: error("Bluetooth is not available on this device")
                    val device = adapter.getRemoteDevice(deviceAddress)
                    adapter.cancelDiscovery()
                    device.createRfcommSocketToServiceRecord(SERVICE_UUID).use { socket ->
                        socket.connect()
                        writeFrame(
                            socket.outputStream,
                            json.encodeToString(ProjectTransferModel.serializer(), model)
                        )
                    }
                }
            }
        }

    suspend fun receive(): Result<ProjectTransferModel> = withContext(Dispatchers.IO) {
        runCatching {
            withTimeout(SOCKET_TIMEOUT_MS.toLong()) {
                val adapter = BluetoothAdapter.getDefaultAdapter()
                    ?: error("Bluetooth is not available on this device")
                adapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SERVICE_UUID).use { server ->
                    server.accept().use { socket ->
                        val payload = readFrame(socket.inputStream)
                        val model = json.decodeFromString(ProjectTransferModel.serializer(), payload)
                        model.validate().getOrThrow()
                    }
                }
            }
        }
    }

    private fun writeFrame(output: java.io.OutputStream, payload: String) {
        val bytes = payload.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_FRAME_BYTES) { "Transfer is too large" }
        DataOutputStream(output).apply {
            writeInt(bytes.size)
            write(bytes)
            flush()
        }
    }

    private fun readFrame(input: java.io.InputStream): String {
        val stream = DataInputStream(input)
        val length = stream.readInt()
        require(length in 1..MAX_FRAME_BYTES) { "Invalid transfer frame" }
        val bytes = ByteArray(length)
        stream.readFully(bytes)
        return bytes.toString(Charsets.UTF_8)
    }

    companion object {
        private const val SERVICE_NAME = "Near Expiry Project Sync"
        private const val SOCKET_TIMEOUT_MS = 30_000
        private const val MAX_FRAME_BYTES = 10 * 1024 * 1024
        private val SERVICE_UUID: UUID = UUID.fromString("7d7c9e1e-8b5b-4cb6-9d2f-76d4bb2c2a01")
    }
}
