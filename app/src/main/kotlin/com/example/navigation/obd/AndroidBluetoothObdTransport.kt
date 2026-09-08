package com.example.navigation.obd

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import java.io.IOException
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** Bluetooth RFCOMM transport for ELM327-compatible adapters. */
class AndroidBluetoothObdTransport(
    private val device: BluetoothDevice,
    private val socketFactory: (BluetoothDevice) -> BluetoothSocket = { adapter ->
        adapter.createRfcommSocketToServiceRecord(SERIAL_PORT_PROFILE_UUID)
    }
) : ObdTransport {
    private val commandExecutor = Executors.newSingleThreadExecutor()
    private var socket: BluetoothSocket? = null

    override val isConnected: Boolean
        get() = socket?.isConnected == true

    @Synchronized
    override fun connect() {
        if (isConnected) return
        try {
            val newSocket = socketFactory(device)
            newSocket.connect()
            socket = newSocket
        } catch (error: IOException) {
            disconnect()
            throw ObdTransportException("Unable to connect to OBD adapter", error)
        }
    }

    @Synchronized
    override fun disconnect() {
        try {
            socket?.close()
        } catch (_: IOException) {
            // Closing a failed Bluetooth socket is best effort.
        } finally {
            socket = null
        }
    }

    @Synchronized
    override fun reconnect() {
        disconnect()
        connect()
    }

    @Synchronized
    override fun command(command: String, timeoutMillis: Long): String {
        require(timeoutMillis > 0) { "timeoutMillis must be positive" }
        val activeSocket = socket ?: throw ObdTransportException("OBD adapter is not connected")
        try {
            activeSocket.outputStream.write("${command.trim()}\r".toByteArray(Charsets.US_ASCII))
            activeSocket.outputStream.flush()
            val response = commandExecutor.submit<String> {
                buildString {
                    while (true) {
                        val next = activeSocket.inputStream.read()
                        if (next == -1) throw ObdTransportException("OBD adapter closed the connection")
                        append(next.toChar())
                        if (next.toChar() == '>') return@submit toString()
                    }
                }
            }
            return response.get(timeoutMillis, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            disconnect()
            throw ObdTimeoutException(command, timeoutMillis)
        } catch (error: IOException) {
            disconnect()
            throw ObdTransportException("OBD transport error", error)
        } catch (error: Exception) {
            if (error is ObdTransportException) throw error
            throw ObdTransportException("OBD command failed", error)
        }
    }

    override fun close() {
        disconnect()
        commandExecutor.shutdownNow()
    }

    private companion object {
        val SERIAL_PORT_PROFILE_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}
