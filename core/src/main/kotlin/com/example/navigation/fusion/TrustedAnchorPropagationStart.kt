package com.example.navigation.fusion

import com.example.navigation.gnss.GnssTrustState
import com.example.navigation.navigation.NavigationPosition

data class TrustedAnchorPropagationResult(val state: VehicleMotionState, val startedFromAnchor: Boolean, val detail: String)

/** Starts DR once from the last trusted local anchor and never replaces it with questionable/rejected GNSS. */
class TrustedAnchorPropagationStart {
    private var propagatingFromAnchor = false

    fun apply(state: VehicleMotionState, anchor: NavigationPosition?, trustState: GnssTrustState): TrustedAnchorPropagationResult {
        if (trustState == GnssTrustState.TRUSTED) {
            propagatingFromAnchor = false
            return TrustedAnchorPropagationResult(state, false, "GNSS trusted; anchor propagation inactive")
        }
        if (propagatingFromAnchor) return TrustedAnchorPropagationResult(state, false, "DR continues from previously selected trusted anchor")
        if (anchor == null) return TrustedAnchorPropagationResult(state, false, "No trusted anchor is available; existing DR state continues")
        propagatingFromAnchor = true
        val anchored = state.copy(
            position = anchor.copy(timestamp = state.timestamp),
            uncertaintyMeters = maxOf(state.uncertaintyMeters, anchor.estimatedAccuracyMeters)
        )
        return TrustedAnchorPropagationResult(anchored, true, "DR started from last trusted local anchor")
    }
}
