package com.example.navigation.obd

import java.io.Closeable

/** Transport boundary for ELM327 protocol operations. */
interface ObdTransport : Closeable {
    val isConnected: Boolean
    fun connect()
    fun disconnect()
    fun reconnect()
    fun command(command: String, timeoutMillis: Long): String
}

open class ObdTransportException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
class ObdTimeoutException(command: String, timeoutMillis: Long) :
    ObdTransportException("OBD command '$command' timed out after $timeoutMillis ms")
