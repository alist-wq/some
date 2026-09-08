package com.example.navigation.gnss

import com.example.navigation.core.MonotonicTimestamp
import com.example.navigation.navigation.NavigationMode
import kotlin.test.*

class SatelliteTrustScorerTest {
    private val time = MonotonicTimestamp(1)
    private val scorer = SatelliteTrustScorer()

    @Test fun `trusted local evidence dominates one suspicious satellite`() {
        val local = input(1, 38f)
        val bad = input(2, 10f, SpacecraftReferenceEvidence(GnssConstellation.GPS, 2, source = "tle", expectedAzimuthDegrees = 250.0, ageSeconds = 20.0))
        val result = scorer.score(time, listOf(local, bad), IndependentGnssContext(NavigationMode.PEDESTRIAN, 5.0, true, true, routeConsistent = true, clockConsistent = true))
        assertEquals(GnssMeasurementClassification.TRUSTED, result.satellites.first().classification)
        assertTrue(result.satellites.first().estimatorWeight > result.satellites.last().estimatorWeight)
    }

    @Test fun `remote replacement is recorded and rejected without Lima-specific rule`() {
        val result = scorer.score(time, listOf(input(1, 35f)), IndependentGnssContext(
            NavigationMode.PEDESTRIAN, positionInnovationMeters = 5_000_000.0, pdrConsistent = false,
            gyroConsistent = false, routeConsistent = false, clockConsistent = false))
        assertEquals(GnssMeasurementClassification.REJECTED, result.classification)
        assertContains(result.reasons, GnssTrustReasonCode.PHYSICAL_CONTINUITY_VIOLATION)
        assertEquals(0.0, result.satellites.single().estimatorWeight)
    }

    @Test fun `air alert and missing OBD do not alter pedestrian trust`() {
        val without = scorer.score(time, listOf(input(1, 35f)), IndependentGnssContext(NavigationMode.PEDESTRIAN, pdrConsistent = true, airAlertActive = false))
        val with = scorer.score(time, listOf(input(1, 35f)), IndependentGnssContext(NavigationMode.PEDESTRIAN, pdrConsistent = true, obdSpeedConsistent = false, airAlertActive = true))
        assertEquals(without.score, with.score)
    }

    @Test fun `vehicle missing OBD is explicit and never replaced by GNSS speed`() {
        val result = scorer.score(time, listOf(input(1, 35f)), IndependentGnssContext(NavigationMode.VEHICLE, obdSpeedConsistent = false))
        assertContains(result.reasons, GnssTrustReasonCode.INDEPENDENT_MOTION_DISAGREEMENT)
    }

    @Test fun `weighted estimator robustly limits remote candidate`() {
        val decision = scorer.score(time, listOf(input(1, 40f), input(2, 40f)), IndependentGnssContext(NavigationMode.PEDESTRIAN))
        val result = WeightedGnssPositionEstimator().estimate(time, listOf(
            WeightedPositionCandidate(50.45, 30.52, 1.0), WeightedPositionCandidate(50.4501, 30.5201, 1.0),
            WeightedPositionCandidate(-12.0464, -77.0428, 0.05)), decision)!!
        assertTrue(result.latitude > 49.0)
        assertTrue(result.contributingWeight > 0.0)
    }

    @Test fun `loss is unavailable and repeated inputs are deterministic`() {
        val context = IndependentGnssContext(NavigationMode.PEDESTRIAN)
        assertNull(scorer.score(time, emptyList(), context).classification)
        assertEquals(scorer.score(time, listOf(input(1, 30f)), context), scorer.score(time, listOf(input(1, 30f)), context))
    }

    private fun input(svid: Int, cn0: Float, reference: SpacecraftReferenceEvidence? = null) = SatelliteTrustInput(
        GnssSatelliteStatus(GnssConstellation.GPS, 2, true, 10f, 45f, cn0, svid), true, reference)
}
