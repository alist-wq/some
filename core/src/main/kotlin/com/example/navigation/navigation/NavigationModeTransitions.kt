package com.example.navigation.navigation

import com.example.navigation.core.MonotonicTimestamp

enum class ModeTransitionDecision {
    NO_CHANGE,
    USER_CONFIRMATION_REQUIRED,
    TRANSITION
}

enum class ModeTransitionEvidence {
    OBD_STOPPED,
    OBD_DISCONNECTED,
    STEP_SEQUENCE,
    PEDESTRIAN_CLASSIFICATION,
    OBD_MOVING,
    VEHICLE_CLASSIFICATION,
    USER_CONFIRMED
}

data class NavigationModeTransition(
    val fromMode: NavigationMode,
    val toMode: NavigationMode,
    /** The prior mode's last position, preserved as the next mode's initialization anchor. */
    val initialPosition: NavigationPosition,
    val timestamp: MonotonicTimestamp
)

data class ModeTransitionEvaluation(
    val decision: ModeTransitionDecision,
    val evidence: Set<ModeTransitionEvidence>,
    val transition: NavigationModeTransition? = null
) {
    init {
        require((decision == ModeTransitionDecision.TRANSITION) == (transition != null)) {
            "Only a transition decision may contain a transition"
        }
    }
}

data class VehicleToPedestrianTransitionConfig(
    val stoppedSpeedMetersPerSecond: Double = 0.5,
    val minimumConsecutiveWalkingSteps: Int = 3,
    val minimumPedestrianProbability: Double = 0.6
) {
    init {
        require(stoppedSpeedMetersPerSecond.isFinite() && stoppedSpeedMetersPerSecond >= 0.0)
        require(minimumConsecutiveWalkingSteps > 0)
        require(minimumPedestrianProbability.isFinite() && minimumPedestrianProbability in 0.0..1.0)
    }
}

data class VehicleToPedestrianObservation(
    val currentPosition: NavigationPosition,
    val obdConnected: Boolean,
    val obdSpeedMetersPerSecond: Double?,
    val consecutiveWalkingSteps: Int,
    val motion: MotionClassification
) {
    init {
        require(obdSpeedMetersPerSecond == null ||
            (obdSpeedMetersPerSecond.isFinite() && obdSpeedMetersPerSecond >= 0.0))
        require(consecutiveWalkingSteps >= 0)
    }
}

/** Stateful detector that requires a vehicle stop before disconnect and walking evidence. */
class VehicleToPedestrianTransitionDetector(
    private val config: VehicleToPedestrianTransitionConfig = VehicleToPedestrianTransitionConfig()
) {
    private var stoppedWhileConnected = false
    private var previousTimestamp: MonotonicTimestamp? = null

    fun evaluate(observation: VehicleToPedestrianObservation): ModeTransitionEvaluation {
        requireIncreasingTimestamp(observation.motion.timestamp)
        if (observation.obdConnected && observation.obdSpeedMetersPerSecond != null &&
            observation.obdSpeedMetersPerSecond <= config.stoppedSpeedMetersPerSecond) {
            stoppedWhileConnected = true
        }

        val evidence = buildSet {
            if (stoppedWhileConnected) add(ModeTransitionEvidence.OBD_STOPPED)
            if (!observation.obdConnected) add(ModeTransitionEvidence.OBD_DISCONNECTED)
            if (observation.consecutiveWalkingSteps >= config.minimumConsecutiveWalkingSteps) {
                add(ModeTransitionEvidence.STEP_SEQUENCE)
            }
            if (observation.motion.pedestrianProbability >= config.minimumPedestrianProbability) {
                add(ModeTransitionEvidence.PEDESTRIAN_CLASSIFICATION)
            }
        }
        val required = setOf(
            ModeTransitionEvidence.OBD_STOPPED,
            ModeTransitionEvidence.OBD_DISCONNECTED,
            ModeTransitionEvidence.STEP_SEQUENCE,
            ModeTransitionEvidence.PEDESTRIAN_CLASSIFICATION
        )
        return if (evidence.containsAll(required)) {
            ModeTransitionEvaluation(
                decision = ModeTransitionDecision.TRANSITION,
                evidence = evidence,
                transition = NavigationModeTransition(
                    NavigationMode.VEHICLE,
                    NavigationMode.PEDESTRIAN,
                    observation.currentPosition,
                    observation.motion.timestamp
                )
            )
        } else {
            ModeTransitionEvaluation(ModeTransitionDecision.NO_CHANGE, evidence)
        }
    }

    fun reset() {
        stoppedWhileConnected = false
        previousTimestamp = null
    }

    private fun requireIncreasingTimestamp(timestamp: MonotonicTimestamp) {
        previousTimestamp?.let { require(timestamp > it) { "Transition observations must increase monotonically" } }
        previousTimestamp = timestamp
    }
}

data class PedestrianToVehicleTransitionConfig(
    val movingSpeedMetersPerSecond: Double = 1.0,
    val minimumVehicleProbability: Double = 0.7
) {
    init {
        require(movingSpeedMetersPerSecond.isFinite() && movingSpeedMetersPerSecond > 0.0)
        require(minimumVehicleProbability.isFinite() && minimumVehicleProbability in 0.0..1.0)
    }
}

data class PedestrianToVehicleObservation(
    val currentPosition: NavigationPosition,
    val obdConnected: Boolean,
    val obdSpeedMetersPerSecond: Double?,
    val userConfirmed: Boolean,
    val motion: MotionClassification
) {
    init {
        require(obdSpeedMetersPerSecond == null ||
            (obdSpeedMetersPerSecond.isFinite() && obdSpeedMetersPerSecond >= 0.0))
    }
}

/** Conservative MVP transition requiring independent vehicle motion and explicit confirmation. */
class PedestrianToVehicleTransitionDetector(
    private val config: PedestrianToVehicleTransitionConfig = PedestrianToVehicleTransitionConfig()
) {
    private var previousTimestamp: MonotonicTimestamp? = null

    fun evaluate(observation: PedestrianToVehicleObservation): ModeTransitionEvaluation {
        previousTimestamp?.let {
            require(observation.motion.timestamp > it) { "Transition observations must increase monotonically" }
        }
        previousTimestamp = observation.motion.timestamp

        val evidence = buildSet {
            if (observation.obdConnected && observation.obdSpeedMetersPerSecond != null &&
                observation.obdSpeedMetersPerSecond >= config.movingSpeedMetersPerSecond) {
                add(ModeTransitionEvidence.OBD_MOVING)
            }
            if (observation.motion.vehicleProbability >= config.minimumVehicleProbability) {
                add(ModeTransitionEvidence.VEHICLE_CLASSIFICATION)
            }
            if (observation.userConfirmed) add(ModeTransitionEvidence.USER_CONFIRMED)
        }
        val vehicleEvidence = evidence.containsAll(setOf(
            ModeTransitionEvidence.OBD_MOVING,
            ModeTransitionEvidence.VEHICLE_CLASSIFICATION
        ))
        return when {
            !vehicleEvidence -> ModeTransitionEvaluation(ModeTransitionDecision.NO_CHANGE, evidence)
            ModeTransitionEvidence.USER_CONFIRMED !in evidence ->
                ModeTransitionEvaluation(ModeTransitionDecision.USER_CONFIRMATION_REQUIRED, evidence)
            else -> ModeTransitionEvaluation(
                decision = ModeTransitionDecision.TRANSITION,
                evidence = evidence,
                transition = NavigationModeTransition(
                    NavigationMode.PEDESTRIAN,
                    NavigationMode.VEHICLE,
                    observation.currentPosition,
                    observation.motion.timestamp
                )
            )
        }
    }

    fun reset() {
        previousTimestamp = null
    }
}
