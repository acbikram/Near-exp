package com.nearexpiry.manager.data.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BluetoothTransferManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class PairedDevice(val name: String, val address: String)

    private val activeCancel = AtomicReference<(() -> Unit)?>(null)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @SuppressLint("MissingPermission")
    fun pairedDevices(): List<PairedDevice> = runCatching {
        check(hasBluetoothPermission()) { "Bluetooth permission required" }
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return emptyList()
        adapter.bondedDevices.orEmpty()
            .sortedBy { it.name.orEmpty().lowercase() }
            .map { PairedDevice(it.name?.takeIf(String::isNotBlank) ?: it.address, it.address) }
    }.getOrDefault(emptyList())

    @SuppressLint("MissingPermission")
    suspend fun send(deviceAddress: String, model: ProjectTransferModel): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                check(hasBluetoothPermission()) { "Bluetooth permission required" }
                withTimeout(SOCKET_TIMEOUT_MS.toLong()) {
                    val adapter = BluetoothAdapter.getDefaultAdapter()
                        ?: error("Bluetooth is not available on this device")
                    val device = adapter.getRemoteDevice(deviceAddress)
                    adapter.cancelDiscovery()
                    device.createRfcommSocketToServiceRecord(SERVICE_UUID).use { socket ->
                        activeCancel.set { runCatching { socket.close() } }
                        socket.connect()
                        writeFrame(
                            socket.outputStream,
                            json.encodeToString(ProjectTransferModel.serializer(), model)
                        )
                        activeCancel.compareAndSet(activeCancel.get(), null)
                    }
                }
                Result.success(Unit)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                if (!currentCoroutineContext().isActive) {
                    throw CancellationException("Bluetooth transfer cancelled", error)
                }
                Result.failure(error)
            } finally {
                activeCancel.set(null)
            }
        }

    @SuppressLint("MissingPermission")
    suspend fun receive(): Result<ProjectTransferModel> = withContext(Dispatchers.IO) {
        try {
            check(hasBluetoothPermission()) { "Bluetooth permission required" }
            val model = withTimeout(SOCKET_TIMEOUT_MS.toLong()) {
                val adapter = BluetoothAdapter.getDefaultAdapter()
                    ?: error("Bluetooth is not available on this device")
                adapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SERVICE_UUID).use { server ->
                    activeCancel.set { runCatching { server.close() } }
                    server.accept().use { socket ->
                        activeCancel.set {
                            runCatching { server.close() }
                            runCatching { socket.close() }
                        }
                        val payload = readFrame(socket.inputStream)
                        json.decodeFromString(ProjectTransferModel.serializer(), payload)
                    }
                }
            }
            model.validate().getOrThrow()
            Result.success(model)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            if (!currentCoroutineContext().isActive) {
                throw CancellationException("Bluetooth transfer cancelled", error)
            }
            Result.failure(error)
        } finally {
            activeCancel.set(null)
        }
    }

    fun cancelActiveTransfer() {
        activeCancel.getAndSet(null)?.invoke()
    }

    private fun hasBluetoothPermission(): Boolean {
        val permissions = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE
            )
        } else {
            listOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN)
        }
        return permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
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
