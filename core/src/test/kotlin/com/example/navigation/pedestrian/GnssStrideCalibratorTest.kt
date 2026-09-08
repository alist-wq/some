package com.example.navigation.pedestrian

import com.example.navigation.core.MonotonicTimestamp
import com.example.navigation.gnss.GnssSample
import com.example.navigation.gnss.GnssTrustState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GnssStrideCalibratorTest {
    @Test fun `trusted GNSS windows calibrate distance per step and cadence relationship`() {
        val calibrator = GnssStrideCalibrator()
        calibrator.observeWindow(trajectory(6.0), steps(10, 80.0), GnssTrustState.TRUSTED)
        val result = calibrator.observeWindow(trajectory(8.0), steps(10, 120.0), GnssTrustState.TRUSTED)

        assertEquals(0.70, result.distancePerStepAtReferenceCadenceMeters, 0.01)
        assertEquals(0.005, result.cadenceGainMetersPerStepPerMinute, 0.0002)
        assertTrue(result.estimatedStepLengthMeters(120.0) > result.estimatedStepLengthMeters(80.0))
        assertFalse(result.frozen)
    }

    @Test fun `questionable GNSS freezes existing calibration`() {
        val calibrator = GnssStrideCalibrator()
        val trusted = calibrator.observeWindow(trajectory(7.0), steps(10, 100.0), GnssTrustState.TRUSTED)

        val frozen = calibrator.observeWindow(trajectory(12.0), steps(10, 100.0), GnssTrustState.QUESTIONABLE)

        assertEquals(trusted.copy(frozen = true), frozen)
        assertEquals(1, calibrator.currentCalibration().calibrationWindowCount)
    }

    @Test fun `rejected and recovering GNSS also cannot update calibration`() {
        val calibrator = GnssStrideCalibrator()
        val initial = calibrator.currentCalibration()

        assertEquals(initial, calibrator.observeWindow(trajectory(7.0), steps(10, 100.0), GnssTrustState.REJECTED))
        assertEquals(initial, calibrator.observeWindow(trajectory(7.0), steps(10, 100.0), GnssTrustState.RECOVERING))
    }

    private fun trajectory(distanceMeters: Double): List<GnssSample> {
        val longitudeDegrees = Math.toDegrees(distanceMeters / EARTH_RADIUS_METERS)
        return listOf(gnss(0.0, 0), gnss(longitudeDegrees, 10))
    }

    private fun steps(count: Int, cadence: Double): List<DetectedStep> = List(count) { index ->
        DetectedStep(MonotonicTimestamp(index * 500_000_000L), 2.0, cadence)
    }

    private fun gnss(longitude: Double, seconds: Long) = GnssSample(
        0.0, longitude, null, 2f, null, null, null, null, null, "gps",
        MonotonicTimestamp(seconds * 1_000_000_000L), null
    )

    private companion object { const val EARTH_RADIUS_METERS = 6_371_000.0 }
}
