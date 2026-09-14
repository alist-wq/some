package com.example.navigation.app

import com.example.navigation.core.MonotonicTimestamp
import com.example.navigation.gnss.GnssEpochTrustDecision
import com.example.navigation.gnss.GnssMeasurementClassification
import com.example.navigation.gnss.GnssTrustReasonCode
import com.example.navigation.gnss.GnssTrustState
import com.example.navigation.navigation.NavigationPosition
import com.example.navigation.navigation.PositionConfidence
import com.example.navigation.navigation.PositionSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationDiagnosticsPresentationFeedTest {
    private val now = MonotonicTimestamp(100_000L)
    private val decision = GnssEpochTrustDecision(
        timestamp = MonotonicTimestamp(90_000L), score = 0.4,
        classification = GnssMeasurementClassification.UNCERTAIN,
        reasons = listOf(GnssTrustReasonCode.ROUTE_DISAGREEMENT), satellites = emptyList(),
        scoringVersion = "fixture", evidenceCapability = com.example.navigation.gnss.EvidenceCapability.AVAILABLE
    )

    @Test fun `presentation separates reliable degraded unreliable and dead reckoning states`() {
        assertEquals(PositionPresentationState.RELIABLE, present(PositionConfidence.HIGH, PositionSource.GNSS, GnssTrustState.TRUSTED).position.state)
        assertEquals(PositionPresentationState.DEGRADED, present(PositionConfidence.MEDIUM, PositionSource.HYBRID, GnssTrustState.QUESTIONABLE).position.state)
        assertEquals(PositionPresentationState.UNRELIABLE, present(PositionConfidence.LOST, PositionSource.GNSS, GnssTrustState.REJECTED).position.state)
        assertEquals(PositionPresentationState.DEAD_RECKONING, present(PositionConfidence.LOW, PositionSource.PEDESTRIAN_DEAD_RECKONING, GnssTrustState.REJECTED).position.state)
    }

    @Test fun `no fix and unavailable capabilities are explicit without fabricated timestamps`() {
        val feed = NavigationDiagnosticsPresentationFactory.create(NavigationMapState(diagnostics = NavigationDiagnostics(
            unavailableFields = listOf("raw GNSS clock")
        )), now, staleAfterNanos = 20_000L)
        assertEquals(PositionPresentationState.NO_FIX, feed.position.state)
        assertEquals(PresentationAvailability.UNAVAILABLE, feed.position.ageAvailability)
        assertEquals(PresentationAvailability.UNAVAILABLE, feed.satelliteStatus.availability)
        assertNull(feed.timelineSample)
        assertEquals(listOf("raw GNSS clock"), feed.capabilityGaps)
    }

    @Test fun `stale position and satellites remain distinct from live data`() {
        val feed = present(PositionConfidence.HIGH, PositionSource.GNSS, GnssTrustState.TRUSTED, timestamp = 1L)
        assertEquals(PresentationAvailability.STALE, feed.position.ageAvailability)
        assertEquals(PresentationAvailability.AVAILABLE, feed.satelliteStatus.availability)
        assertEquals(99_999L, feed.position.ageNanos)
    }

    @Test fun `last trusted anchor is historical and never replaces current estimate`() {
        val anchor = position(22L, PositionSource.GNSS)
        val feed = NavigationDiagnosticsPresentationFactory.create(NavigationMapState(
            estimatedPosition = position(95_000L, PositionSource.PEDESTRIAN_DEAD_RECKONING),
            diagnostics = NavigationDiagnostics(lastTrustedGnss = anchor)
        ), now, 20_000L)
        assertEquals("LAST_TRUSTED_HISTORICAL_ANCHOR", feed.lastTrustedPosition?.label)
        assertEquals(22L, feed.lastTrustedPosition?.timestampNanos)
        assertEquals(PositionSource.PEDESTRIAN_DEAD_RECKONING, feed.position.source)
    }

    @Test fun `timeline retention is bounded deterministic and export round trips labels`() {
        val buffer = DiagnosticsTimelineBuffer(2)
        val first = sample(1L, PositionPresentationState.RELIABLE)
        val second = sample(2L, PositionPresentationState.DEGRADED)
        val third = sample(3L, PositionPresentationState.UNRELIABLE)
        buffer.append(first)
        buffer.append(second)
        assertEquals(listOf(second, third), buffer.append(third))
        val exported = GnssTrustExportReport.renderDiagnosticsTimeline(buffer.snapshot())
        assertTrue(exported.contains("timestampNanos"))
        assertTrue(exported.contains("ROUTE_DISAGREEMENT"))
        assertEquals(buffer.snapshot(), DiagnosticsTimelineExport.parse(exported))
    }

    private fun present(
        confidence: PositionConfidence,
        source: PositionSource,
        trustState: GnssTrustState,
        timestamp: Long = 95_000L
    ) = NavigationDiagnosticsPresentationFactory.create(NavigationMapState(
        estimatedPosition = position(timestamp, source, confidence),
        diagnostics = NavigationDiagnostics(
            gnssTrustState = trustState,
            latestTrustDecision = decision,
            visibleSatellitesByConstellation = mapOf("GPS" to 5),
            usedSatellitesByConstellation = mapOf("GPS" to 4)
        )
    ), now, staleAfterNanos = 20_000L)

    private fun position(timestamp: Long, source: PositionSource, confidence: PositionConfidence = PositionConfidence.HIGH) =
        NavigationPosition(50.0, 30.0, confidence, 12.0, source, MonotonicTimestamp(timestamp))

    private fun sample(timestamp: Long, state: PositionPresentationState) = DiagnosticsTimelineSample(
        timestamp, state, PositionSource.GNSS, PresentationAvailability.AVAILABLE,
        listOf("ROUTE_DISAGREEMENT"), PresentationAvailability.AVAILABLE, mapOf("GPS" to 4), mapOf("GPS" to 3)
    )
}
