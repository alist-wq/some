package com.example.navigation.pedestrian

import com.example.navigation.core.MonotonicTimestamp
import java.util.ArrayDeque

enum class ElevationMotion {
    UP,
    DOWN,
    LEVEL
}

data class ElevationEventDetectorConfig(
    val minimumSustainedDurationNanos: Long = 5_000_000_000L,
    val minimumVerticalChangeMeters: Double = 1.0
) {
    init {
        require(minimumSustainedDurationNanos > 0L)
        require(minimumVerticalChangeMeters.isFinite() && minimumVerticalChangeMeters > 0.0)
    }
}

/** A sustained elevation classification over the detector's configured time window. */
data class ElevationEvent(
    val motion: ElevationMotion,
    val relativeAltitudeChangeMeters: Double,
    val startedAt: MonotonicTimestamp,
    val timestamp: MonotonicTimestamp
)

/**
 * Detects a stable UP, DOWN, or LEVEL state from relative-altitude estimates.
 * A state is emitted only when it changes, avoiding repeated events for one continuous movement.
 */
class ElevationEventDetector(private val config: ElevationEventDetectorConfig = ElevationEventDetectorConfig()) {
    private val window = ArrayDeque<RelativeAltitudeEstimate>()
    private var previousTimestamp: MonotonicTimestamp? = null
    private var lastReportedMotion: ElevationMotion? = null

    fun process(estimate: RelativeAltitudeEstimate): ElevationEvent? {
        previousTimestamp?.let { previous ->
            require(estimate.timestamp > previous) { "Relative-altitude timestamps must increase monotonically." }
        }
        previousTimestamp = estimate.timestamp
        window.addLast(estimate)

        val cutoff = estimate.timestamp.nanos - config.minimumSustainedDurationNanos
        while (window.size > 1 && window.elementAt(1).timestamp.nanos <= cutoff) {
            window.removeFirst()
        }

        val start = window.first()
        if (estimate.timestamp.differenceNanos(start.timestamp) < config.minimumSustainedDurationNanos) return null

        val change = estimate.relativeAltitudeMeters - start.relativeAltitudeMeters
        val motion = when {
            change >= config.minimumVerticalChangeMeters -> ElevationMotion.UP
            change <= -config.minimumVerticalChangeMeters -> ElevationMotion.DOWN
            else -> ElevationMotion.LEVEL
        }
        if (motion == lastReportedMotion) return null
        lastReportedMotion = motion
        return ElevationEvent(motion, change, start.timestamp, estimate.timestamp)
    }

    fun reset() {
        window.clear()
        previousTimestamp = null
        lastReportedMotion = null
    }
}
