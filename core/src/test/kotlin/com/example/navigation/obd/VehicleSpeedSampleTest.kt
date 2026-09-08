package com.example.navigation.obd

import com.example.navigation.core.MonotonicTimestamp
import kotlin.test.Test
import kotlin.test.assertEquals

class VehicleSpeedSampleTest {
    @Test
    fun samplePreservesSpeedTimestampAndSource() {
        val sample = VehicleSpeedSample(13.5, MonotonicTimestamp(123L), VehicleSpeedSource.OBD)

        assertEquals(13.5, sample.metersPerSecond)
        assertEquals(MonotonicTimestamp(123L), sample.timestamp)
        assertEquals(VehicleSpeedSource.OBD, sample.source)
    }
}
