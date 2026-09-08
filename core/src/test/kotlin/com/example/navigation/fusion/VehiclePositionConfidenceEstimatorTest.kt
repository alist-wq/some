package com.example.navigation.fusion

import com.example.navigation.navigation.PositionConfidence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VehiclePositionConfidenceEstimatorTest {
    private val estimator = VehiclePositionConfidenceEstimator()

    @Test fun `all required degradation factors increase uncertainty`() {
        val baseline = estimate(input())
        val degraded = listOf(
            input(timeSinceTrustedGnssNanos = 60_000_000_000L),
            input(distanceSinceTrustedGnssMeters = 1_000.0),
            input(gyroUncertaintyDegrees = 20.0),
            input(obdAvailable = false),
            input(routeAmbiguity = 1.0),
            input(candidateProbabilitySeparation = 0.0)
        )

        assertTrue(degraded.all { estimate(it) > baseline })
    }

    @Test fun `large accumulated uncertainty becomes lost`() {
        val result = estimator.estimate(input(
            timeSinceTrustedGnssNanos = 600_000_000_000L,
            distanceSinceTrustedGnssMeters = 5_000.0,
            gyroUncertaintyDegrees = 50.0,
            obdAvailable = false,
            routeAmbiguity = 1.0,
            candidateProbabilitySeparation = 0.0
        ))

        assertEquals(PositionConfidence.LOST, result.confidence)
    }

    private fun estimate(input: VehiclePositionConfidenceInput) = estimator.estimate(input).uncertaintyMeters
    private fun input(
        timeSinceTrustedGnssNanos: Long = 0,
        distanceSinceTrustedGnssMeters: Double = 0.0,
        gyroUncertaintyDegrees: Double = 0.0,
        obdAvailable: Boolean = true,
        routeAmbiguity: Double = 0.0,
        candidateProbabilitySeparation: Double = 1.0
    ) = VehiclePositionConfidenceInput(timeSinceTrustedGnssNanos, distanceSinceTrustedGnssMeters,
        gyroUncertaintyDegrees, obdAvailable, routeAmbiguity, candidateProbabilitySeparation)
}
