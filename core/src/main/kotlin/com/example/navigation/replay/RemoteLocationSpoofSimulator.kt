package com.example.navigation.replay

import com.example.navigation.core.MonotonicTimestamp
import com.example.navigation.gnss.GnssSample

enum class RemoteLocationBehavior { STATIC, CONTINUE_RELATIVE_MOTION }

data class RemoteLocationSpoofConfig(
    val targetLatitude: Double,
    val targetLongitude: Double,
    val start: MonotonicTimestamp,
    val durationNanos: Long,
    val recoveryDurationNanos: Long = 0L,
    val behavior: RemoteLocationBehavior = RemoteLocationBehavior.STATIC
) {
    init {
        require(targetLatitude in -90.0..90.0)
        require(targetLongitude in -180.0..180.0)
        require(durationNanos > 0)
        require(recoveryDurationNanos >= 0)
    }
    val spoofEndExclusive: MonotonicTimestamp get() = MonotonicTimestamp(Math.addExact(start.nanos, durationNanos))
    val recoveryEndExclusive: MonotonicTimestamp get() = MonotonicTimestamp(Math.addExact(spoofEndExclusive.nanos, recoveryDurationNanos))
}

/** Replay-only remote GNSS-location spoof; it never modifies non-GNSS records or source data. */
class RemoteLocationSpoofSimulator(private val config: RemoteLocationSpoofConfig) : ReplayRecordTransformer {
    private var firstOriginal: GnssSample? = null

    override fun transform(record: SensorSessionRecord): List<SensorSessionRecord> {
        if (record !is GnssSessionRecord || record.timestamp < config.start) return listOf(record)
        val original = record.sample
        val spoofed = spoof(original)
        val result = when {
            record.timestamp < config.spoofEndExclusive -> spoofed
            config.recoveryDurationNanos == 0L || record.timestamp >= config.recoveryEndExclusive -> original
            else -> interpolate(spoofed, original, record.timestamp.differenceNanos(config.spoofEndExclusive).toDouble() / config.recoveryDurationNanos)
        }
        return listOf(record.copy(sample = result))
    }

    private fun spoof(original: GnssSample): GnssSample {
        val anchor = firstOriginal ?: original.also { firstOriginal = it }
        return when (config.behavior) {
            RemoteLocationBehavior.STATIC -> original.copy(latitude = config.targetLatitude, longitude = config.targetLongitude)
            RemoteLocationBehavior.CONTINUE_RELATIVE_MOTION -> original.copy(
                latitude = config.targetLatitude + (original.latitude - anchor.latitude),
                longitude = config.targetLongitude + (original.longitude - anchor.longitude)
            )
        }
    }

    private fun interpolate(from: GnssSample, to: GnssSample, fraction: Double): GnssSample {
        val bounded = fraction.coerceIn(0.0, 1.0)
        return to.copy(
            latitude = from.latitude + (to.latitude - from.latitude) * bounded,
            longitude = from.longitude + (to.longitude - from.longitude) * bounded
        )
    }
}
