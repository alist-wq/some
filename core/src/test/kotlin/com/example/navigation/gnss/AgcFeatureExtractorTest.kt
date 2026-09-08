package com.example.navigation.gnss

import com.example.navigation.core.MonotonicTimestamp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AgcFeatureExtractorTest {
    @Test fun `unsupported AGC is explicit and graceful`() {
        val result = AgcFeatureExtractor().extract(raw(listOf(observation(GnssConstellation.GPS, null))))

        assertFalse(result.supported); assertTrue(result.byConstellation.isEmpty())
    }

    @Test fun `extracts AGC statistics and synchronized pattern`() {
        val extractor = AgcFeatureExtractor()
        extractor.extract(raw(listOf(observation(GnssConstellation.GPS, 2.0), observation(GnssConstellation.GALILEO, 3.0))))
        val result = extractor.extract(raw(listOf(observation(GnssConstellation.GPS, 4.0), observation(GnssConstellation.GALILEO, 5.0))))

        assertTrue(result.supported); assertEquals(4.0, result.byConstellation.getValue(GnssConstellation.GPS).meanDbHz); assertNotNull(result.synchronizedChange)
    }

    private fun raw(observations: List<RawSatelliteObservation>) = RawGnssSample(MonotonicTimestamp(1), clock, observations)
    private fun observation(constellation: GnssConstellation, agc: Double?) = RawSatelliteObservation(1, constellation, null, null, agc, null, null, null, null, 0, null, GnssMultipathIndicator.UNKNOWN, null, null)
    private companion object { val clock = GnssClockInfo(0, null, null, null, null, null, null, null) }
}
