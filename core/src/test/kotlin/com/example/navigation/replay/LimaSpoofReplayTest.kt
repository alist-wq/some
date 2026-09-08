package com.example.navigation.replay

import com.example.navigation.core.MonotonicTimestamp
import com.example.navigation.gnss.GnssClockInfo
import com.example.navigation.gnss.GnssSample
import com.example.navigation.gnss.RawGnssSample
import com.example.navigation.obd.VehicleSpeedSample
import com.example.navigation.obd.VehicleSpeedSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LimaSpoofReplayTest {
    @Test fun `remote spoof supports Lima arbitrary targets motion and local recovery without changing other sensors`() {
        val lima = RemoteLocationSpoofSimulator(RemoteLocationSpoofConfig(
            Lima001Scenario.LIMA_LATITUDE, Lima001Scenario.LIMA_LONGITUDE, time(10), 20, recoveryDurationNanos = 10,
            behavior = RemoteLocationBehavior.CONTINUE_RELATIVE_MOTION
        ))
        val first = lima.transform(gnss(10, 50.0, 30.0)).single() as GnssSessionRecord
        val moving = lima.transform(gnss(20, 50.01, 30.02)).single() as GnssSessionRecord
        val recovered = lima.transform(gnss(40, 50.03, 30.06)).single() as GnssSessionRecord
        assertEquals(Lima001Scenario.LIMA_LATITUDE, first.sample.latitude)
        assertEquals(Lima001Scenario.LIMA_LONGITUDE + 0.02, moving.sample.longitude)
        assertEquals(50.03, recovered.sample.latitude)
        val speed = VehicleSpeedSessionRecord(VehicleSpeedSample(8.0, time(15), VehicleSpeedSource.OBD))
        assertEquals(listOf(speed), lima.transform(speed))

        val arbitrary = RemoteLocationSpoofSimulator(RemoteLocationSpoofConfig(1.25, 2.5, time(0), 10))
        val target = arbitrary.transform(gnss(1, 50.0, 30.0)).single() as GnssSessionRecord
        assertEquals(1.25, target.sample.latitude)
        assertEquals(2.5, target.sample.longitude)
    }

    @Test fun `GNSS time corruption changes only GNSS time fields and preserves monotonic timestamps`() {
        val simulator = GnssTimeCorruptionSimulator(GnssTimeCorruptionConfig(GnssTimeCorruptionMode.GRADUAL_DRIFT, time(10), 2_000_000_000, time(20)))
        val location = simulator.transform(gnss(15, 50.0, 30.0)).single() as GnssSessionRecord
        val rawInput = RawGnssSessionRecord(RawGnssSample(time(15), GnssClockInfo(100, null, null, null, null, null, null, null), emptyList()))
        val raw = simulator.transform(rawInput).single() as RawGnssSessionRecord
        assertEquals(time(15), location.timestamp)
        assertEquals(50.0, location.sample.latitude)
        assertEquals(1_000L, location.sample.wallClockTimestampMillis)
        assertEquals(time(15), raw.timestamp)
        assertEquals(1_000_000_100L, raw.sample.clock.timeNanos)
        val backward = GnssTimeCorruptionSimulator(GnssTimeCorruptionConfig(GnssTimeCorruptionMode.OFFSET, time(10), -1_000_000_000))
            .transform(gnss(15, 50.0, 30.0)).single() as GnssSessionRecord
        assertEquals(-1_000L, backward.sample.wallClockTimestampMillis)
    }

    @Test fun `LIMA 001 composes position time velocity and bearing faults then returns local location`() {
        val config = Lima001ScenarioConfig(
            remoteLocation = Lima001Scenario.defaultRemoteLocation(time(10), durationNanos = 10),
            timeCorruption = GnssTimeCorruptionConfig(GnssTimeCorruptionMode.JUMP, time(10), 1_000_000_000, time(20)),
            velocityBearingCorruption = GnssVelocityBearingCorruptionConfig(15f, 180f, time(10), time(20))
        )
        fun apply(record: SensorSessionRecord): SensorSessionRecord = Lima001Scenario.transformers(config).fold(listOf(record)) { records, transformer -> records.flatMap(transformer::transform) }.single()
        val spoofed = apply(gnss(10, 50.0, 30.0)) as GnssSessionRecord
        val local = apply(gnss(20, 50.1, 30.1)) as GnssSessionRecord
        assertEquals(Lima001Scenario.LIMA_LATITUDE, spoofed.sample.latitude)
        assertEquals(15f, spoofed.sample.speedMetersPerSecond)
        assertEquals(180f, spoofed.sample.bearingDegrees)
        assertEquals(1_000L, spoofed.sample.wallClockTimestampMillis)
        assertEquals(50.1, local.sample.latitude)
        assertTrue(local.timestamp == time(20))
    }

    private fun gnss(t: Long, latitude: Double, longitude: Double) = GnssSessionRecord(GnssSample(latitude, longitude, null, null, null, 5f, null, 10f, null, "replay", time(t), 0L))
    private fun time(nanos: Long) = MonotonicTimestamp(nanos)
}
