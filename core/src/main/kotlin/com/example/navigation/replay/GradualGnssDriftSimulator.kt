package com.example.navigation.replay

import com.example.navigation.core.MonotonicTimestamp
import com.example.navigation.gnss.GnssSample
import kotlin.math.cos
import kotlin.math.sin

/** Configuration for a linear GNSS displacement that exists only during replay. */
data class GradualGnssDriftConfig(
    val start: MonotonicTimestamp,
    val endExclusive: MonotonicTimestamp,
    val directionDegrees: Double,
    val driftMetersPerSecond: Double
) {
    init {
        require(endExclusive >= start) { "endExclusive must not be before start" }
        require(driftMetersPerSecond >= 0.0) { "driftMetersPerSecond must not be negative" }
    }
}

/** Injects a progressively increasing false displacement into processed GNSS records. */
class GradualGnssDriftSimulator(private val config: GradualGnssDriftConfig) : ReplayRecordTransformer {
    override fun transform(record: SensorSessionRecord): List<SensorSessionRecord> {
        if (record !is GnssSessionRecord || record.timestamp < config.start || record.timestamp >= config.endExclusive) {
            return listOf(record)
        }
        val elapsedSeconds = record.timestamp.differenceNanos(config.start) / NANOS_PER_SECOND
        val distanceMeters = elapsedSeconds * config.driftMetersPerSecond
        val directionRadians = Math.toRadians(config.directionDegrees)
        val northMeters = distanceMeters * cos(directionRadians)
        val eastMeters = distanceMeters * sin(directionRadians)
        return listOf(record.copy(sample = record.sample.displaced(northMeters, eastMeters)))
    }

    private fun GnssSample.displaced(northMeters: Double, eastMeters: Double): GnssSample {
        val latitudeDelta = northMeters / METERS_PER_DEGREE
        val longitudeDelta = eastMeters / (METERS_PER_DEGREE * cos(Math.toRadians(latitude)))
        return copy(latitude = latitude + latitudeDelta, longitude = longitude + longitudeDelta)
    }

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000.0
        const val METERS_PER_DEGREE = 111_320.0
    }
}
