package com.example.navigation.gnss

import com.example.navigation.navigation.NavigationPosition

enum class QuestionableAnchorPolicy { PRESERVE, UPDATE }

data class LastTrustedPositionAnchorConfig(
    val questionablePolicy: QuestionableAnchorPolicy = QuestionableAnchorPolicy.PRESERVE
)

data class TrustedAnchorUpdate(
    val anchor: NavigationPosition?,
    val updated: Boolean,
    val reason: String
)

/** Retains the last navigation state confirmed by the GNSS trust state machine. */
class LastTrustedPositionAnchor(private val config: LastTrustedPositionAnchorConfig = LastTrustedPositionAnchorConfig()) {
    var anchor: NavigationPosition? = null
        private set
    private var previousTimestampNanos: Long? = null

    fun observe(position: NavigationPosition, trustState: GnssTrustState): TrustedAnchorUpdate {
        previousTimestampNanos?.let { require(position.timestamp.nanos >= it) { "Anchor observations must be monotonic" } }
        previousTimestampNanos = position.timestamp.nanos
        val update = trustState == GnssTrustState.TRUSTED ||
            (trustState == GnssTrustState.QUESTIONABLE && config.questionablePolicy == QuestionableAnchorPolicy.UPDATE)
        if (update) anchor = position
        return TrustedAnchorUpdate(anchor, update, when {
            trustState == GnssTrustState.TRUSTED -> "trusted GNSS state"
            trustState == GnssTrustState.QUESTIONABLE && update -> "questionable policy permits update"
            else -> "anchor preserved while GNSS is $trustState"
        })
    }
}
