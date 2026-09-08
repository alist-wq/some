package com.example.navigation.gnss

import com.example.navigation.core.MonotonicTimestamp
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals

class RawGnssSampleTest {
    @Test
    fun rawSampleRoundTripsThroughSerializationWithoutAndroidObjects() {
        val sample = RawGnssSample(
            timestamp = MonotonicTimestamp(456_789L),
            clock = GnssClockInfo(
                timeNanos = 123_456_789L,
                fullBiasNanos = -456_789L,
                biasNanos = null,
                biasUncertaintyNanos = null,
                driftNanosPerSecond = 0.25,
                driftUncertaintyNanosPerSecond = null,
                leapSecond = 18,
                hardwareClockDiscontinuityCount = 1
            ),
            observations = listOf(
                RawSatelliteObservation(
                    svid = 12,
                    constellation = GnssConstellation.GPS,
                    carrierFrequencyHz = 1_575_420_000f,
                    cn0DbHz = 42.5f,
                    automaticGainControlDb = null,
                    pseudorangeRateMetersPerSecond = -10.0,
                    pseudorangeRateUncertaintyMetersPerSecond = 0.1,
                    accumulatedDeltaRangeMeters = null,
                    accumulatedDeltaRangeUncertaintyMeters = null,
                    stateFlags = 7L,
                    accumulatedDeltaRangeStateFlags = null,
                    multipathIndicator = GnssMultipathIndicator.NOT_DETECTED,
                    receivedSvTimeNanos = 987_654_321L,
                    receivedSvTimeUncertaintyNanos = 15L
                )
            )
        )

        val bytes = ByteArrayOutputStream().also {
            ObjectOutputStream(it).use { output -> output.writeObject(sample) }
        }.toByteArray()
        val restored = ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() }

        assertEquals(sample, restored)
    }
}
