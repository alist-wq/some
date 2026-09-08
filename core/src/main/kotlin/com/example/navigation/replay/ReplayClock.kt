package com.example.navigation.replay

import com.example.navigation.core.MonotonicTimestamp

/** Pacing policy for replay while the virtual clock remains monotonic. */
enum class ReplayClockMode {
    REALTIME,
    ACCELERATED,
    STEP,
    MAX_SPEED
}

/**
 * Deterministic monotonic clock for replay. Its [now] value always advances to
 * recorded timestamps; only delivery pacing differs by [mode].
 */
class ReplayClock(
    private val mode: ReplayClockMode,
    private val accelerationFactor: Double = 1.0,
    private val sleep: (Long) -> Unit = Thread::sleep
) {
    private var stepPermits = 0

    var now: MonotonicTimestamp? = null
        private set

    init {
        require(mode != ReplayClockMode.ACCELERATED || accelerationFactor > 0.0) {
            "accelerationFactor must be positive"
        }
    }

    /** Permits one pending or subsequent [advanceTo] call in [ReplayClockMode.STEP]. */
    @Synchronized
    fun step() {
        stepPermits += 1
        (this as java.lang.Object).notifyAll()
    }

    /** Advances virtual time to [timestamp], applying this clock's pacing policy. */
    fun advanceTo(timestamp: MonotonicTimestamp) {
        val previous = synchronized(this) {
            now?.also { require(timestamp >= it) { "Replay timestamps must be monotonic" } }
        }
        val elapsedNanos = previous?.let(timestamp::differenceNanos)?.coerceAtLeast(0L) ?: 0L

        when (mode) {
            ReplayClockMode.REALTIME -> sleepIfNecessary(elapsedNanos)
            ReplayClockMode.ACCELERATED -> sleepIfNecessary((elapsedNanos / accelerationFactor).toLong())
            ReplayClockMode.STEP -> awaitStep()
            ReplayClockMode.MAX_SPEED -> Unit
        }
        synchronized(this) { now = timestamp }
    }

    private fun sleepIfNecessary(nanos: Long) {
        val millis = nanos / NANOS_PER_MILLISECOND
        if (millis > 0) sleep(millis)
    }

    @Synchronized
    private fun awaitStep() {
        while (stepPermits == 0) {
            (this as java.lang.Object).wait()
        }
        stepPermits -= 1
    }

    private companion object {
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}
