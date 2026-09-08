package com.example.navigation.pedestrian

import com.example.navigation.core.MonotonicTimestamp
import com.example.navigation.sensors.GyroscopeSample
import kotlin.math.PI
import kotlin.math.abs

enum class PedestrianTurnDirection {
    STRAIGHT,
    LEFT,
    RIGHT,
    U_TURN,
    UNKNOWN
}

data class PedestrianTurnDetectorConfig(
    val turnStartYawRateRadiansPerSecond: Double = 0.20,
    val turnSettledYawRateRadiansPerSecond: Double = 0.05,
    val majorTurnDegrees: Double = 35.0,
    val uTurnDegrees: Double = 135.0
) {
    init {
        require(turnStartYawRateRadiansPerSecond.isFinite() && turnStartYawRateRadiansPerSecond > 0.0)
        require(turnSettledYawRateRadiansPerSecond.isFinite() && turnSettledYawRateRadiansPerSecond in 0.0..<turnStartYawRateRadiansPerSecond)
        require(majorTurnDegrees.isFinite() && majorTurnDegrees > 0.0)
        require(uTurnDegrees.isFinite() && uTurnDegrees > majorTurnDegrees && uTurnDegrees <= 180.0)
    }
}

data class PedestrianTurnEvent(
    val direction: PedestrianTurnDirection,
    val relativeTurnDegrees: Double,
    val timestamp: MonotonicTimestamp
)

/** Detects relative pedestrian turns from integrated gyro yaw; it does not estimate compass heading. */
class PedestrianGyroTurnDetector(
    private val config: PedestrianTurnDetectorConfig = PedestrianTurnDetectorConfig()
) {
    private var previousTimestamp: MonotonicTimestamp? = null
    private var active = false
    private var accumulatedTurnDegrees = 0.0

    fun process(sample: GyroscopeSample): PedestrianTurnEvent? {
        require(sample.zRadiansPerSecond.isFinite()) { "Gyroscope yaw rate must be finite" }
        val previous = previousTimestamp
        previousTimestamp = sample.timestamp
        if (previous == null) return null
        require(sample.timestamp > previous) { "Gyroscope samples must be strictly increasing" }
        val yawRate = sample.zRadiansPerSecond.toDouble()
        if (active || abs(yawRate) >= config.turnStartYawRateRadiansPerSecond) {
            accumulatedTurnDegrees += yawRate * sample.timestamp.differenceNanos(previous) / NANOS_PER_SECOND * DEGREES_PER_RADIAN
            active = true
        }
        if (!active || abs(yawRate) > config.turnSettledYawRateRadiansPerSecond) return null

        val event = PedestrianTurnEvent(classify(accumulatedTurnDegrees), accumulatedTurnDegrees, sample.timestamp)
        active = false
        accumulatedTurnDegrees = 0.0
        return event
    }

    fun reset() {
        previousTimestamp = null
        active = false
        accumulatedTurnDegrees = 0.0
    }

    private fun classify(turnDegrees: Double): PedestrianTurnDirection = when {
        !turnDegrees.isFinite() -> PedestrianTurnDirection.UNKNOWN
        abs(turnDegrees) >= config.uTurnDegrees -> PedestrianTurnDirection.U_TURN
        turnDegrees >= config.majorTurnDegrees -> PedestrianTurnDirection.LEFT
        turnDegrees <= -config.majorTurnDegrees -> PedestrianTurnDirection.RIGHT
        else -> PedestrianTurnDirection.STRAIGHT
    }

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000.0
        const val DEGREES_PER_RADIAN = 180.0 / PI
    }
}
