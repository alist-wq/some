package com.example.navigation.obd

import com.example.navigation.core.MonotonicTimestamp
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.math.max

/** Observable state used by navigation to distinguish OBD speed from an unavailable speed source. */
enum class VehicleSpeedAvailability { AVAILABLE, UNAVAILABLE }

data class ObdPollingMetrics(
    val pollCount: Long,
    val successfulPollCount: Long,
    val timeoutCount: Long,
    val failureCount: Long,
    val lastLatencyMillis: Long?,
    val meanLatencyMillis: Double,
    val actualPollRateHz: Double,
    val timeoutRate: Double
)

interface VehicleSpeedPollingListener {
    fun onVehicleSpeed(sample: VehicleSpeedSample) {}
    fun onSpeedAvailabilityChanged(availability: VehicleSpeedAvailability, reason: String?) {}
    fun onPollingMetrics(metrics: ObdPollingMetrics) {}
}

/**
 * Polls Mode 01 PID 0D on one serial executor. Fixed-delay scheduling guarantees a second
 * command is never started while the preceding command is still in flight.
 */
class VehicleSpeedPollingScheduler(
    private val vehicleSpeedProvider: VehicleSpeedProvider,
    pollRateHz: Int = DEFAULT_POLL_RATE_HZ,
    private val listener: VehicleSpeedPollingListener = object : VehicleSpeedPollingListener {},
    private val timestampNanos: () -> Long = System::nanoTime,
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
) : AutoCloseable {
    private val pollIntervalMillis = 1_000L / pollRateHz
    private var running = false
    private var pollCount = 0L
    private var successfulPollCount = 0L
    private var timeoutCount = 0L
    private var failureCount = 0L
    private var latencyTotalMillis = 0L
    private var lastLatencyMillis: Long? = null
    private var firstSuccessfulPollNanos: Long? = null
    private var lastSuccessfulPollNanos: Long? = null

    init { require(pollRateHz in MIN_POLL_RATE_HZ..MAX_POLL_RATE_HZ) { "pollRateHz must be 5..20" } }

    @Synchronized
    fun start() {
        if (running) return
        running = true
        scheduler.scheduleWithFixedDelay(::poll, 0, pollIntervalMillis, TimeUnit.MILLISECONDS)
    }

    @Synchronized
    fun stop() {
        running = false
        scheduler.shutdownNow()
    }

    internal fun pollOnceForTest() = poll(force = true)

    private fun poll() = poll(force = false)

    private fun poll(force: Boolean) {
        synchronized(this) { if (!force && !running) return }
        val startedAt = timestampNanos()
        try {
            val sample = vehicleSpeedProvider.read(MonotonicTimestamp(startedAt))
            val completedAt = timestampNanos()
            synchronized(this) {
                pollCount++
                successfulPollCount++
                lastLatencyMillis = nanosToMillis(completedAt - startedAt)
                latencyTotalMillis += lastLatencyMillis!!
                firstSuccessfulPollNanos = firstSuccessfulPollNanos ?: startedAt
                lastSuccessfulPollNanos = completedAt
            }
            listener.onVehicleSpeed(sample)
            listener.onSpeedAvailabilityChanged(VehicleSpeedAvailability.AVAILABLE, null)
        } catch (error: ObdTimeoutException) {
            recordFailure(startedAt, error.message ?: "Vehicle speed request timed out", timedOut = true)
        } catch (error: ObdTransportException) {
            recordFailure(startedAt, error.message ?: "Vehicle speed is unavailable", timedOut = false)
        } finally {
            listener.onPollingMetrics(metrics())
        }
    }

    private fun recordFailure(startedAt: Long, reason: String, timedOut: Boolean) {
        val latencyMillis = nanosToMillis(timestampNanos() - startedAt)
        synchronized(this) {
            pollCount++
            failureCount++
            if (timedOut) timeoutCount++
            latencyTotalMillis += latencyMillis
            lastLatencyMillis = latencyMillis
        }
        listener.onSpeedAvailabilityChanged(VehicleSpeedAvailability.UNAVAILABLE, reason)
    }

    @Synchronized
    fun metrics(): ObdPollingMetrics {
        val completed = successfulPollCount + failureCount
        val rate = firstSuccessfulPollNanos?.let { first ->
            lastSuccessfulPollNanos?.let { last ->
                if (last > first) (successfulPollCount - 1) * 1_000_000_000.0 / (last - first) else 0.0
            }
        } ?: 0.0
        return ObdPollingMetrics(
            pollCount = pollCount,
            successfulPollCount = successfulPollCount,
            timeoutCount = timeoutCount,
            failureCount = failureCount,
            lastLatencyMillis = lastLatencyMillis,
            meanLatencyMillis = if (completed == 0L) 0.0 else latencyTotalMillis.toDouble() / completed,
            actualPollRateHz = rate,
            timeoutRate = if (pollCount == 0L) 0.0 else timeoutCount.toDouble() / pollCount
        )
    }

    override fun close() = stop()

    private fun nanosToMillis(nanos: Long): Long = TimeUnit.NANOSECONDS.toMillis(max(0L, nanos))

    private companion object {
        const val MIN_POLL_RATE_HZ = 5
        const val MAX_POLL_RATE_HZ = 20
        const val DEFAULT_POLL_RATE_HZ = 10
    }
}
