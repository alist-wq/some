package com.example.navigation.sensors

import com.example.navigation.core.MonotonicTimestamp
import java.io.Serializable

/** A three-axis gyroscope observation in radians per second. */
data class GyroscopeSample(
    val xRadiansPerSecond: Float,
    val yRadiansPerSecond: Float,
    val zRadiansPerSecond: Float,
    val timestamp: MonotonicTimestamp
) : Serializable {
    private companion object { private const val serialVersionUID = 1L }
}

/** A three-axis accelerometer observation in metres per second squared. */
data class AccelerometerSample(
    val xMetersPerSecondSquared: Float,
    val yMetersPerSecondSquared: Float,
    val zMetersPerSecondSquared: Float,
    val timestamp: MonotonicTimestamp
) : Serializable {
    private companion object { private const val serialVersionUID = 1L }
}

/**
 * Game rotation-vector quaternion components in Android order: x, y, z, then
 * optional scalar w. Device axes are +X right, +Y up, +Z out of the screen.
 * The game rotation vector is relative to its reference frame and does not
 * provide magnetometer-based north alignment.
 */
data class GameRotationVectorSample(
    val x: Float,
    val y: Float,
    val z: Float,
    val scalar: Float?,
    val timestamp: MonotonicTimestamp
) : Serializable {
    private companion object { private const val serialVersionUID = 1L }
}

/** A three-axis magnetometer observation in microteslas. */
data class MagnetometerSample(
    val xMicroteslas: Float,
    val yMicroteslas: Float,
    val zMicroteslas: Float,
    val timestamp: MonotonicTimestamp
) : Serializable {
    private companion object { private const val serialVersionUID = 1L }
}

/** An atmospheric-pressure observation in hectopascals. */
data class BarometerSample(
    val pressureHectopascals: Float,
    val timestamp: MonotonicTimestamp
) : Serializable {
    private companion object { private const val serialVersionUID = 1L }
}
