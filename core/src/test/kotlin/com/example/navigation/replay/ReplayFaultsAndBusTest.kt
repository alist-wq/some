package com.example.navigation.replay

import com.example.navigation.core.MonotonicTimestamp
import com.example.navigation.gnss.GnssSample
import com.example.navigation.navigation.NavigationMode
import kotlin.test.Test
import kotlin.test.assertEquals
import java.util.concurrent.atomic.AtomicInteger

class ReplayFaultsAndBusTest {
    @Test
    fun busDispatchesOnlyTheMatchingTypedCallback() {
        val bus = ReplayEventBus()
        val gnss = AtomicInteger()
        val speed = AtomicInteger()
        bus.subscribe(object : ReplayEventSubscriber {
            override fun onGnss(sample: GnssSample) { gnss.incrementAndGet() }
            override fun onVehicleSpeed(sample: com.example.navigation.obd.VehicleSpeedSample) { speed.incrementAndGet() }
        })

        bus.publish(ReplayEvent.Gnss(sample()))

        assertEquals(1, gnss.get())
        assertEquals(0, speed.get())
    }

    @Test
    fun outageRemovesPositionsAndCanKeepRawGnss() {
        val simulator = GnssOutageSimulator(GnssOutageConfig(MonotonicTimestamp(10L), 10L))
        val position = GnssSessionRecord(sample())
        val raw = RawGnssSessionRecord(com.example.navigation.gnss.RawGnssSample(
            MonotonicTimestamp(10L),
            com.example.navigation.gnss.GnssClockInfo(1L, null, null, null, null, null, null, null),
            emptyList()
        ))

        assertEquals(emptyList(), simulator.transform(position))
        assertEquals(listOf(raw), simulator.transform(raw))
    }

    @Test
    fun jumpRemainDisplacesSubsequentGnssPositions() {
        val simulator = GnssPositionJumpSimulator(
            GnssPositionJumpConfig(MonotonicTimestamp(10L), northMeters = 20_000.0, behavior = GnssJumpBehavior.REMAIN)
        )

        val first = simulator.transform(GnssSessionRecord(sample(10L))).single() as GnssSessionRecord
        val second = simulator.transform(GnssSessionRecord(sample(11L))).single() as GnssSessionRecord

        assertEquals(first.sample.latitude, second.sample.latitude)
        assertEquals(49.9935 + 20_000.0 / 111_320.0, first.sample.latitude)
    }

    private fun sample(timestamp: Long = 10L) = GnssSample(
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
