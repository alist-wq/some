package com.example.navigation.app

import com.example.navigation.gnss.GnssTrustState
import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationDiagnosticsTest {
    @Test fun `diagnostics default to explicit unavailable values`() {
        val diagnostics = NavigationDiagnostics()
        assertEquals(null, diagnostics.gnssTrustState)
        assertEquals(emptyList<Any>(), diagnostics.trustEvidence)
        assertEquals(emptyMap<Long, Double>(), diagnostics.routeCandidateProbabilities)
        assertEquals(null, diagnostics.lastTrustedGnss)
    }

    @Test fun `diagnostics retains supplied development values`() {
        val diagnostics = NavigationDiagnostics(
            gnssTrustState = GnssTrustState.REJECTED,
            obdRateHertz = 10.0,
            vehicleSpeedMetersPerSecond = 13.5,
            gyroYawRateRadiansPerSecond = 0.2,
            stepCadenceStepsPerMinute = 110.0,
            routeCandidateProbabilities = mapOf(7L to 0.8),
            positionUncertaintyMeters = 35.0
        )
        assertEquals(GnssTrustState.REJECTED, diagnostics.gnssTrustState)
        assertEquals(0.8, diagnostics.routeCandidateProbabilities[7L])
        assertEquals(35.0, diagnostics.positionUncertaintyMeters)
    }
}
