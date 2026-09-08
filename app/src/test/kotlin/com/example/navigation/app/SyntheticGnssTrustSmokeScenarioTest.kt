package com.example.navigation.app

import com.example.navigation.gnss.GnssMeasurementClassification
import com.example.navigation.gnss.GnssTrustState
import com.example.navigation.navigation.PositionSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyntheticGnssTrustSmokeScenarioTest {
    @Test fun `injection fixture exposes rejected fallback without claiming true position`() {
        val snapshot = SyntheticGnssTrustSmokeScenario.run()
        assertEquals(GnssTrustState.REJECTED, snapshot.trustState)
        assertEquals(GnssMeasurementClassification.REJECTED, snapshot.decision?.classification)
        assertEquals(PositionSource.PEDESTRIAN_DEAD_RECKONING, snapshot.navigationPosition?.source)
        assertTrue(snapshot.suspiciousEpochCount > 0)
    }
}
