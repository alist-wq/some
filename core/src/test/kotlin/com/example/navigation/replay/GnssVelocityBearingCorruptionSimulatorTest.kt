package com.example.navigation.replay

import com.example.navigation.core.MonotonicTimestamp
import com.example.navigation.gnss.GnssSample
import kotlin.test.Test
import kotlin.test.assertEquals

class GnssVelocityBearingCorruptionSimulatorTest {
    @Test
    fun velocityAndBearingCanBeCorruptedIndependentlyWithoutChangingPosition() {
        val simulator = GnssVelocityBearingCorruptionSimulator(
            GnssVelocityBearingCorruptionConfig(speedMetersPerSecond = 99.0f)
        )
        val original = sample()
        val corrupted = (simulator.transform(GnssSessionRecord(original)).single() as GnssSessionRecord).sample

        assertEquals(99.0f, corrupted.speedMetersPerSecond)
        assertEquals(original.bearingDegrees, corrupted.bearingDegrees)
        assertEquals(original.latitude, corrupted.latitude)
        assertEquals(original.longitude, corrupted.longitude)
    }

    @Test
    fun boundedCorruptionLeavesRecordsOutsideIntervalUntouched() {
        val simulator = GnssVelocityBearingCorruptionSimulator(
            GnssVelocityBearingCorruptionConfig(
                bearingDegrees = 180.0f,
                start = MonotonicTimestamp(10L),
                endExclusive = MonotonicTimestamp(20L)
            )
        )
        val before = (simulator.transform(GnssSessionRecord(sample(9L))).single() as GnssSessionRecord).sample
        val during = (simulator.transform(GnssSessionRecord(sample(10L))).single() as GnssSessionRecord).sample
        val after = (simulator.transform(GnssSessionRecord(sample(20L))).single() as GnssSessionRecord).sample

        assertEquals(87.0f, before.bearingDegrees)
        assertEquals(180.0f, during.bearingDegrees)
        assertEquals(87.0f, after.bearingDegrees)
    }

    private fun sample(timestamp: Long = 10L) = GnssSample(
        latitude = 49.9935,
        longitude = 36.2304,
        altitudeMeters = 100.0,
        horizontalAccuracyMeters = 3.0f,
        verticalAccuracyMeters = 5.0f,
        speedMetersPerSecond = 12.0f,
        speedAccuracyMetersPerSecond = 0.5f,
        bearingDegrees = 87.0f,
        bearingAccuracyDegrees = 2.0f,
        provider = "replay",
        timestamp = MonotonicTimestamp(timestamp),
        wallClockTimestampMillis = 1L
    )
}
