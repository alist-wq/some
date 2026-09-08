package com.example.navigation.sensors

import com.example.navigation.core.MonotonicTimestamp
import kotlin.test.Test
import kotlin.test.assertEquals

class ImuCalibrationStateTest {
    @Test
    fun calibrationStateRetainsRuntimeCalibrationValues() {
        val state = ImuCalibrationState(0.1f, 0.2f, 0.3f, PhoneMountingOrientation.SCREEN_UP, SensorStability.STABLE, MonotonicTimestamp(4L))

        assertEquals(0.3f, state.gyroBiasZRadiansPerSecond)
        assertEquals(PhoneMountingOrientation.SCREEN_UP, state.phoneMountingOrientation)
        assertEquals(SensorStability.STABLE, state.sensorStability)
    }
}
