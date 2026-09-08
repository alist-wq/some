package com.example.navigation.app

import com.example.navigation.core.MonotonicTimestamp
import com.example.navigation.navigation.NavigationPosition
import com.example.navigation.navigation.PositionConfidence
import com.example.navigation.navigation.PositionSource
import com.example.navigation.gnss.GnssTrustState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationStatusPresentationTest {
    @Test fun `vehicle dead reckoning exposes source confidence accuracy and warning`() {
        val status = NavigationStatusPresentationFactory.create(
            NavigationPosition(50.0, 30.0, PositionConfidence.LOW, 35.0, PositionSource.VEHICLE_DEAD_RECKONING, MonotonicTimestamp(1)),
            GnssTrustState.REJECTED
        )
        assertEquals("VEHICLE DR", status.sourceLabel)
        assertEquals("GNSS UNTRUSTED", status.confidenceLabel)
        assertEquals("±35 m", status.accuracyLabel)
        assertEquals("LOW POSITION CONFIDENCE", status.warning)
    }

    @Test fun `all GNSS trust states use the required normal-view labels`() {
        val position = NavigationPosition(50.0, 30.0, PositionConfidence.MEDIUM, 10.0, PositionSource.GNSS, MonotonicTimestamp(1))
        assertEquals("GNSS NORMAL", NavigationStatusPresentationFactory.create(position, GnssTrustState.TRUSTED).confidenceLabel)
        assertEquals("GNSS QUESTIONABLE", NavigationStatusPresentationFactory.create(position, GnssTrustState.QUESTIONABLE).confidenceLabel)
        assertEquals("GNSS UNTRUSTED", NavigationStatusPresentationFactory.create(position, GnssTrustState.REJECTED).confidenceLabel)
        assertEquals("GNSS RECOVERING", NavigationStatusPresentationFactory.create(position, GnssTrustState.RECOVERING).confidenceLabel)
    }

    @Test fun `trust states have stable accessible icon color tone and text combinations`() {
        val position = NavigationPosition(50.0, 30.0, PositionConfidence.MEDIUM, 10.0, PositionSource.GNSS, MonotonicTimestamp(1))
        val expected = listOf(
            GnssTrustState.TRUSTED to ("GNSS NORMAL" to ("✓" to TrustVisualTone.NORMAL)),
            GnssTrustState.QUESTIONABLE to ("GNSS QUESTIONABLE" to ("!" to TrustVisualTone.CAUTION)),
            GnssTrustState.REJECTED to ("GNSS UNTRUSTED" to ("×" to TrustVisualTone.DANGER)),
            GnssTrustState.RECOVERING to ("GNSS RECOVERING" to ("↻" to TrustVisualTone.RECOVERY))
        )
        expected.forEach { (state, visual) ->
            val status = NavigationStatusPresentationFactory.create(position, state)
            assertEquals(visual.first, status.confidenceLabel)
            assertEquals(visual.second.first, status.trustIcon)
            assertEquals(visual.second.second, status.trustTone)
        }
    }

    @Test fun `missing position is explicitly unavailable`() {
        val status = NavigationStatusPresentationFactory.create(null)
        assertEquals("UNAVAILABLE", status.sourceLabel)
        assertEquals("LOST", status.confidenceLabel)
        assertEquals(TrustVisualTone.UNAVAILABLE, status.trustTone)
        assertTrue(status.warning!!.contains("NO NAVIGATION POSITION"))
    }
}
