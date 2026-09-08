package com.example.navigation.replay

import com.example.navigation.core.MonotonicTimestamp
import com.example.navigation.gnss.GnssSample
import kotlin.test.Test
import kotlin.test.assertEquals

class GradualGnssDriftSimulatorTest {
    @Test
    fun driftGrowsAtConfiguredRateAndDirectionInsideConfiguredRange() {
        val simulator = GradualGnssDriftSimulator(
            GradualGnssDriftConfig(
                start = MonotonicTimestamp(0L),
                endExclusive = MonotonicTimestamp(20_000_000_000L),
                directionDegrees = 0.0,
                driftMetersPerSecond = 5.0
            )
        )

        val atStart = simulator.transform(GnssSessionRecord(sample(0L))).single() as GnssSessionRecord
        val atTenSeconds = simulator.transform(GnssSessionRecord(sample(10_000_000_000L))).single() as GnssSessionRecord
        val atEnd = simulator.transform(GnssSessionRecord(sample(20_000_000_000L))).single() as GnssSessionRecord

        assertEquals(49.9935, atStart.sample.latitude)
        assertEquals(49.9935 + 50.0 / 111_320.0, atTenSeconds.sample.latitude)
        assertEquals(49.9935, atEnd.sample.latitude)
    }

    private fun sample(timestamp: Long) = GnssSample(
        latitude = 49.9935,
        longitude = 36.2304,
        altitudeMeters = null,
        horizontalAccuracyMeters = null,
        verticalAccuracyMeters = null,
        speedMetersPerSecond = null,
        speedAccuracyMetersPerSecond = null,
        bearingDegrees = null,
        bearingAccuracyDegrees = null,
        provider = "replay",
        timestamp = MonotonicTimestamp(timestamp),
        wallClockTimestampMillis = null
    )
}
