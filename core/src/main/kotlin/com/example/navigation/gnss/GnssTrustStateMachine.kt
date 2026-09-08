package com.example.navigation.gnss

enum class GnssTrustState {
    TRUSTED,
    QUESTIONABLE,
    REJECTED,
    RECOVERING
}

data class GnssTrustStateMachineConfig(
    val rejectedThreshold: Double = 0.30,
    val questionableThreshold: Double = 0.60,
    val recoveryThreshold: Double = 0.55,
    val trustedThreshold: Double = 0.75,
    val questionableObservationsRequired: Int = 2,
    val rejectedObservationsRequired: Int = 3,
    val recoveringObservationsRequired: Int = 3,
    val trustedObservationsRequired: Int = 3
) {
    init {
        require(rejectedThreshold in 0.0..1.0)
        require(questionableThreshold in 0.0..1.0)
        require(recoveryThreshold in 0.0..1.0)
        require(trustedThreshold in 0.0..1.0)
        require(rejectedThreshold < recoveryThreshold)
        require(recoveryThreshold < trustedThreshold)
        require(questionableThreshold < trustedThreshold)
        require(questionableObservationsRequired > 0)
        require(rejectedObservationsRequired > 0)
        require(recoveringObservationsRequired > 0)
        require(trustedObservationsRequired > 0)
    }
}

data class GnssTrustStateUpdate(
    val previousState: GnssTrustState,
    val state: GnssTrustState,
    val score: Double,
    val pendingState: GnssTrustState?,
    val consecutiveObservations: Int,
    val requiredObservations: Int?
)

/** Applies hysteresis and consecutive-observation requirements to continuous GNSS trust scores. */
class GnssTrustStateMachine(
    private val config: GnssTrustStateMachineConfig = GnssTrustStateMachineConfig(),
    initialState: GnssTrustState = GnssTrustState.TRUSTED
) {
    var state: GnssTrustState = initialState
        private set
    private var pendingState: GnssTrustState? = null
    private var pendingCount: Int = 0

    fun update(score: GnssTrustScore): GnssTrustStateUpdate = update(score.value)

    fun update(score: Double): GnssTrustStateUpdate {
        require(score.isFinite() && score in 0.0..1.0) { "GNSS trust score must be in [0, 1]" }
        val previous = state
        val target = transitionTarget(score)
        if (target == null) {
            pendingState = null
            pendingCount = 0
        } else {
            if (pendingState == target) pendingCount++ else {
                pendingState = target
                pendingCount = 1
            }
            if (pendingCount >= requiredObservations(target)) {
                state = target
                pendingState = null
                pendingCount = 0
            }
        }
        return GnssTrustStateUpdate(
            previousState = previous,
            state = state,
            score = score,
            pendingState = pendingState,
            consecutiveObservations = pendingCount,
            requiredObservations = pendingState?.let(::requiredObservations)
        )
    }

    private fun transitionTarget(score: Double): GnssTrustState? = when (state) {
        GnssTrustState.TRUSTED ->
            if (score < config.questionableThreshold) GnssTrustState.QUESTIONABLE else null
        GnssTrustState.QUESTIONABLE -> when {
            score <= config.rejectedThreshold -> GnssTrustState.REJECTED
            score >= config.trustedThreshold -> GnssTrustState.TRUSTED
            else -> null
        }
        GnssTrustState.REJECTED ->
            if (score >= config.recoveryThreshold) GnssTrustState.RECOVERING else null
        GnssTrustState.RECOVERING -> when {
            score <= config.rejectedThreshold -> GnssTrustState.REJECTED
            score >= config.trustedThreshold -> GnssTrustState.TRUSTED
            else -> null
        }
    }

    private fun requiredObservations(target: GnssTrustState): Int = when (target) {
        GnssTrustState.TRUSTED -> config.trustedObservationsRequired
        GnssTrustState.QUESTIONABLE -> config.questionableObservationsRequired
        GnssTrustState.REJECTED -> config.rejectedObservationsRequired
        GnssTrustState.RECOVERING -> config.recoveringObservationsRequired
    }
}
