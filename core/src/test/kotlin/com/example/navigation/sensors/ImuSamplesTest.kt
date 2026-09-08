package com.example.navigation.sensors

import com.example.navigation.core.MonotonicTimestamp
import kotlin.test.Test
import kotlin.test.assertEquals

class ImuSamplesTest {
    @Test
    fun modelsRetainTheirMonotonicTimestamp() {
        val timestamp = MonotonicTimestamp(42L)

        assertEquals(timestamp, GyroscopeSample(1f, 2f, 3f, timestamp).timestamp)
        assertEquals(timestamp, AccelerometerSample(1f, 2f, 3f, timestamp).timestamp)
        assertEquals(timestamp, GameRotationVectorSample(1f, 2f, 3f, null, timestamp).timestamp)
        assertEquals(timestamp, MagnetometerSample(1f, 2f, 3f, timestamp).timestamp)
        assertEquals(timestamp, BarometerSample(1013.25f, timestamp).timestamp)
    }

    @Test
    fun rotationVectorSupportsOptionalScalarComponent() {
        assertEquals(null, GameRotationVectorSample(0f, 0f, 0f, null, MonotonicTimestamp(1L)).scalar)
        assertEquals(1f, GameRotationVectorSample(0f, 0f, 0f, 1f, MonotonicTimestamp(1L)).scalar)
    }
}
