package com.example.navigation.core

import java.time.Duration

/**
 * A timestamp from Android's elapsed-realtime clock, expressed in nanoseconds.
 * This is time elapsed since boot for ordering and measuring sensor observations.
 * It is not Unix time and must never be combined with wall-clock timestamps.
 */
@JvmInline
value class MonotonicTimestamp(val nanos: Long) : Comparable<MonotonicTimestamp> {
    /** Returns the elapsed duration from this timestamp to [other]. */
    operator fun minus(other: MonotonicTimestamp): Duration =
        Duration.ofNanos(nanos - other.nanos)

    /** Returns the elapsed interval in nanoseconds from this timestamp to [other]. */
    fun differenceNanos(other: MonotonicTimestamp): Long = nanos - other.nanos

    override fun compareTo(other: MonotonicTimestamp): Int = nanos.compareTo(other.nanos)
}
