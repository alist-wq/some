package com.example.navigation.obd

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

data class ObdReconnectPolicy(
    val initialDelayMillis: Long = 1_000L,
    val maximumDelayMillis: Long = 30_000L
) {
    init {
        require(initialDelayMillis > 0)
        require(maximumDelayMillis >= initialDelayMillis)
    }
}

/** Reconnects and reinitializes an adapter after a transport, ELM, or ignition failure. */
class ObdReconnectionController(
    private val transport: ObdTransport,
    private val initializer: Elm327Initializer,
    private val listener: VehicleSpeedPollingListener,
    private val policy: ObdReconnectPolicy = ObdReconnectPolicy(),
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
) : AutoCloseable {
    private var reconnectScheduled = false
    private var nextDelayMillis = policy.initialDelayMillis

    @Synchronized
    fun onSpeedUnavailable(reason: String) {
        listener.onSpeedAvailabilityChanged(VehicleSpeedAvailability.UNAVAILABLE, reason)
        if (reconnectScheduled) return
        reconnectScheduled = true
        scheduler.schedule(::reconnect, nextDelayMillis, TimeUnit.MILLISECONDS)
    }

    private fun reconnect() {
        try {
            transport.reconnect()
            initializer.initialize()
            synchronized(this) {
                reconnectScheduled = false
                nextDelayMillis = policy.initialDelayMillis
            }
            listener.onSpeedAvailabilityChanged(VehicleSpeedAvailability.AVAILABLE, null)
        } catch (error: ObdTransportException) {
            retry(error.message ?: "OBD reconnection failed")
        } catch (error: IllegalArgumentException) {
            retry(error.message ?: "ELM327 initialization failed")
        }
    }

    private fun retry(reason: String) {
        listener.onSpeedAvailabilityChanged(VehicleSpeedAvailability.UNAVAILABLE, reason)
        val delay = synchronized(this) {
            val current = nextDelayMillis
            nextDelayMillis = (nextDelayMillis * 2).coerceAtMost(policy.maximumDelayMillis)
            current
        }
        scheduler.schedule(::reconnect, delay, TimeUnit.MILLISECONDS)
    }

    override fun close() {
        scheduler.shutdownNow()
    }
}
