package com.example.navigation.gnss

import com.example.navigation.core.MonotonicTimestamp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class Cn0FeatureExtractorTest {
    @Test fun `extracts constellation statistics`() {
        val result = Cn0FeatureExtractor().extract(raw(listOf(observation(GnssConstellation.GPS, 20f), observation(GnssConstellation.GPS, 30f))))

        val gps = result.byConstellation.getValue(GnssConstellation.GPS)
        assertEquals(2, gps.satelliteCount); assertEquals(25.0, gps.meanDbHz); assertEquals(25.0, gps.medianDbHz); assertEquals(25.0, gps.varianceDbHzSquared)
    }

    @Test fun `reports synchronized multi-constellation change without conclusion`() {
        val extractor = Cn0FeatureExtractor()
        extractor.extract(raw(listOf(observation(GnssConstellation.GPS, 20f), observation(GnssConstellation.GALILEO, 30f))))

        val change = extractor.extract(raw(listOf(observation(GnssConstellation.GPS, 25f), observation(GnssConstellation.GALILEO, 35f)))).synchronizedChange

        assertNotNull(change); assertEquals(2, change.constellationCount); assertEquals(5.0, change.signedMeanChange)
    }

    private fun raw(observations: List<RawSatelliteObservation>) = RawGnssSample(MonotonicTimestamp(1), clock, observations)
    private fun observation(constellation: GnssConstellation, cn0: Float) = RawSatelliteObservation(1, constellation, null, cn0, null, null, null, null, null, 0, null, GnssMultipathIndicator.UNKNOWN, null, null)
    private companion object { val clock = GnssClockInfo(0, null, null, null, null, null, null, null) }
}
