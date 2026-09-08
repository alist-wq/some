package com.example.navigation.replay

import com.example.navigation.core.MonotonicTimestamp

/** Canonical replay fixture configuration; Lima is data only and never production detection logic. */
data class Lima001ScenarioConfig(
    val remoteLocation: RemoteLocationSpoofConfig,
    val timeCorruption: GnssTimeCorruptionConfig? = null,
    val velocityBearingCorruption: GnssVelocityBearingCorruptionConfig? = null
)

object Lima001Scenario {
    const val LIMA_LATITUDE = -12.0464
    const val LIMA_LONGITUDE = -77.0428

    fun defaultRemoteLocation(start: MonotonicTimestamp, durationNanos: Long, recoveryDurationNanos: Long = 0L) =
        RemoteLocationSpoofConfig(LIMA_LATITUDE, LIMA_LONGITUDE, start, durationNanos, recoveryDurationNanos)

    fun transformers(config: Lima001ScenarioConfig): List<ReplayRecordTransformer> = buildList {
        add(RemoteLocationSpoofSimulator(config.remoteLocation))
        config.timeCorruption?.let { add(GnssTimeCorruptionSimulator(it)) }
        config.velocityBearingCorruption?.let { add(GnssVelocityBearingCorruptionSimulator(it)) }
    }
}
