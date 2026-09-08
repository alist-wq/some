package com.example.navigation.gnss

import com.example.navigation.core.MonotonicTimestamp
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals

class GnssSampleTest {
    @Test
    fun sampleRoundTripsThroughSerialization() {
        val sample = GnssSample(
            latitude = 49.9935,
            longitude = 36.2304,
            altitudeMeters = null,
            horizontalAccuracyMeters = 4.5f,
            verticalAccuracyMeters = null,
            speedMetersPerSecond = 12.0f,
            speedAccuracyMetersPerSecond = null,
            bearingDegrees = 87.0f,
            bearingAccuracyDegrees = null,
            provider = "fused",
            timestamp = MonotonicTimestamp(123_456_789L),
            wallClockTimestampMillis = 1_725_000_000_000L
        )

        val bytes = ByteArrayOutputStream().also {
            ObjectOutputStream(it).use { output -> output.writeObject(sample) }
        }.toByteArray()
        val restored = ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() }

        assertEquals(sample, restored)
    }
}
