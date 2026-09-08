package com.example.navigation.gnss

import android.location.Location
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertFalse

@RunWith(AndroidJUnit4::class)
class AndroidGnssLocationProviderTest {
    @Test
    fun frameworkLocationIsConvertedImmediatelyToInternalGnssSample() {
        val location = Location("gps").apply {
            latitude = 49.9935
            longitude = 36.2304
            time = 1_725_000_000_000L
            elapsedRealtimeNanos = 123_456_789L
        }

        val sample = location.toGnssSample()

        assertEquals(49.9935, sample.latitude, 0.0)
        assertEquals(36.2304, sample.longitude, 0.0)
        assertEquals(123_456_789L, sample.timestamp.nanos)
        assertNull(sample.speedMetersPerSecond)
    }

    @Test fun frameworkMockStateIsRecordedSeparatelyFromLocation() {
        val location = Location("gps").apply { elapsedRealtimeNanos = 99L }
        val evidence = location.toAndroidLocationEvidence()
        assertEquals(99L, evidence.timestamp.nanos)
        assertFalse(evidence.mockLocation!!)
        assertEquals(EvidenceCapability.AVAILABLE, evidence.mockCapability)
    }
}
