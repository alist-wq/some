package com.example.navigation.sensors

import com.example.navigation.core.MonotonicTimestamp
import java.io.Serializable

/** Declared physical relationship between the phone and its vehicle mounting. */
enum class PhoneMountingOrientation : Serializable {
    UNKNOWN,
    SCREEN_UP,
    SCREEN_DOWN,
    PORTRAIT_FORWARD,
    LANDSCAPE_FORWARD
}

/** Current quality classification for runtime IMU calibration inputs. */
enum class SensorStability : Serializable {
    UNKNOWN,
    UNSTABLE,
    STABLE
}

/** Persistable runtime calibration state; bias estimation is intentionally external. */
data class ImuCalibrationState(
    val gyroBiasXRadiansPerSecond: Float,
    val gyroBiasYRadiansPerSecond: Float,
    val gyroBiasZRadiansPerSecond: Float,
    val phoneMountingOrientation: PhoneMountingOrientation,
    val sensorStability: SensorStability,
    val updatedAt: MonotonicTimestamp
) : Serializable
