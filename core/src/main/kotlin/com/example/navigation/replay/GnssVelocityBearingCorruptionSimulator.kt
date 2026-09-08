package com.example.navigation.replay

import com.example.navigation.core.MonotonicTimestamp

/** Replay-only configuration for independent GNSS velocity and bearing corruption. */
data class GnssVelocityBearingCorruptionConfig(
    val speedMetersPerSecond: Float? = null,
    val bearingDegrees: Float? = null,
    val start: MonotonicTimestamp? = null,
    val endExclusive: MonotonicTimestamp? = null
) {
    init {
        require(speedMetersPerSecond != null || bearingDegrees != null) {
            "At least one GNSS field must be configured for corruption"
        }
        require(start == null || endExclusive == null || endExclusive >= start) {
            "endExclusive must not be before start"
        }
    }
}

/** Corrupts only configured GNSS speed and/or bearing values during replay. */
class GnssVelocityBearingCorruptionSimulator(
    private val config: GnssVelocityBearingCorruptionConfig
) : ReplayRecordTransformer {
    override fun transform(record: SensorSessionRecord): List<SensorSessionRecord> {
        if (record !is GnssSessionRecord || !isActive(record.timestamp)) return listOf(record)
        return listOf(record.copy(sample = record.sample.copy(
            speedMetersPerSecond = config.speedMetersPerSecond ?: record.sample.speedMetersPerSecond,
            bearingDegrees = config.bearingDegrees ?: record.sample.bearingDegrees
        )))
    }

    private fun isActive(timestamp: MonotonicTimestamp): Boolean =
        (config.start == null || timestamp >= config.start) &&
            (config.endExclusive == null || timestamp < config.endExclusive)
}
