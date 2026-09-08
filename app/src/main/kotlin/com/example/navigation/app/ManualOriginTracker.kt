package com.example.navigation.app

import com.example.navigation.core.MonotonicTimestamp
import com.example.navigation.navigation.NavigationPosition
import com.example.navigation.navigation.PositionConfidence
import com.example.navigation.navigation.PositionSource

data class ManualOriginTrackingConfig(
    val initialUncertaintyMeters: Double = 100.0,
    val unavailableUncertaintyGrowthMeters: Double = 50.0
) {
    init {
        require(initialUncertaintyMeters.isFinite() && initialUncertaintyMeters > 0.0)
        require(unavailableUncertaintyGrowthMeters.isFinite() && unavailableUncertaintyGrowthMeters > 0.0)
    }
}

data class ManualOriginTrackingState(
    val active: Boolean = false,
    val origin: ManualStartPoint? = null,
    val position: NavigationPosition? = null,
    val acceptedMeasurementCount: Int = 0
)

sealed interface ManualTrackingObservation {
    val timestamp: MonotonicTimestamp
    data class Position(val value: NavigationPosition) : ManualTrackingObservation {
        override val timestamp = value.timestamp
    }
    data class Unavailable(override val timestamp: MonotonicTimestamp) : ManualTrackingObservation
}

/** Initializes from manual provenance, then accepts only explicit reliable navigation estimates. */
class ManualOriginTracker(private val config: ManualOriginTrackingConfig = ManualOriginTrackingConfig()) {
    var state: ManualOriginTrackingState = ManualOriginTrackingState()
        private set

    fun start(confirmed: ManualStartSelectionState, timestamp: MonotonicTimestamp): ManualOriginTrackingState {
        require(!state.active) { "Stop or restart explicitly before starting another manual origin" }
        require(confirmed.phase == ManualStartSelectionPhase.CONFIRMED)
        val origin = requireNotNull(confirmed.selectedStart)
        state = ManualOriginTrackingState(
            active = true,
            origin = origin,
            position = NavigationPosition(
                origin.point.latitude, origin.point.longitude, PositionConfidence.LOW,
                config.initialUncertaintyMeters, PositionSource.MANUAL_START, timestamp
            )
        )
        return state
    }

    fun observe(observation: ManualTrackingObservation): ManualOriginTrackingState {
        require(state.active)
        val current = requireNotNull(state.position)
        require(observation.timestamp > current.timestamp)
        state = when (observation) {
            is ManualTrackingObservation.Position -> {
                val estimate = observation.value
                if (estimate.confidence in setOf(PositionConfidence.HIGH, PositionConfidence.MEDIUM) &&
                    estimate.source != PositionSource.MANUAL_START) {
                    state.copy(position = estimate, acceptedMeasurementCount = state.acceptedMeasurementCount + 1)
                } else {
                    degrade(current, observation.timestamp)
                }
            }
            is ManualTrackingObservation.Unavailable -> degrade(current, observation.timestamp)
        }
        return state
    }

    fun stop(): ManualOriginTrackingState {
        state = state.copy(active = false)
        return state
    }

    fun restart(confirmed: ManualStartSelectionState, timestamp: MonotonicTimestamp): ManualOriginTrackingState {
        state = ManualOriginTrackingState()
        return start(confirmed, timestamp)
    }

    fun replay(
        confirmed: ManualStartSelectionState,
        timestamp: MonotonicTimestamp,
        observations: List<ManualTrackingObservation>
    ): ManualOriginTrackingState {
        restart(confirmed, timestamp)
        observations.forEach(::observe)
        return state
    }

    private fun degrade(current: NavigationPosition, timestamp: MonotonicTimestamp): ManualOriginTrackingState =
        state.copy(position = current.copy(
            confidence = PositionConfidence.LOST,
            estimatedAccuracyMeters = current.estimatedAccuracyMeters + config.unavailableUncertaintyGrowthMeters,
            timestamp = timestamp
        ))
}
