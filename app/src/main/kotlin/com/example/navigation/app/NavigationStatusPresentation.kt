package com.example.navigation.app

import com.example.navigation.navigation.NavigationPosition
import com.example.navigation.navigation.PositionConfidence
import com.example.navigation.navigation.PositionSource
import com.example.navigation.gnss.GnssTrustState

data class NavigationStatusPresentation(
    val sourceLabel: String,
    val confidenceLabel: String,
    val accuracyLabel: String,
    val warning: String?,
    val trustIcon: String,
    val trustTone: TrustVisualTone
)

enum class TrustVisualTone { NORMAL, CAUTION, DANGER, RECOVERY, UNAVAILABLE }

/** Converts explicit navigation provenance and uncertainty into user-facing, non-precise status text. */
object NavigationStatusPresentationFactory {
    fun create(position: NavigationPosition?, trustState: GnssTrustState = GnssTrustState.TRUSTED): NavigationStatusPresentation {
        if (position == null) return NavigationStatusPresentation(
            "UNAVAILABLE", "LOST", "Accuracy unavailable", "NO NAVIGATION POSITION", "?", TrustVisualTone.UNAVAILABLE
        )
        val source = when (position.source) {
            PositionSource.MANUAL_START -> "MANUAL START"
            PositionSource.GNSS -> "GNSS"
            PositionSource.HYBRID -> "HYBRID"
            PositionSource.VEHICLE_DEAD_RECKONING -> "VEHICLE DR"
            PositionSource.PEDESTRIAN_DEAD_RECKONING -> "PEDESTRIAN DR"
            PositionSource.MAP_MATCHED_ESTIMATE -> "MAP-MATCHED ESTIMATE"
        }
        val accuracy = if (position.estimatedAccuracyMeters.isFinite() && position.estimatedAccuracyMeters >= 0.0) {
            "±${format(position.estimatedAccuracyMeters)} m"
        } else "Accuracy unavailable"
        val warning = when {
            position.confidence == PositionConfidence.LOST -> "POSITION CONFIDENCE LOST"
            position.confidence == PositionConfidence.LOW -> "LOW POSITION CONFIDENCE"
            position.source != PositionSource.GNSS -> "GNSS NOT THE SOLE POSITION SOURCE"
            else -> null
        }
        val visual = trustVisual(trustState)
        return NavigationStatusPresentation(source, trustLabel(trustState), accuracy, warning, visual.first, visual.second)
    }

    private fun trustLabel(state: GnssTrustState) = when (state) {
        GnssTrustState.TRUSTED -> "GNSS NORMAL"
        GnssTrustState.QUESTIONABLE -> "GNSS QUESTIONABLE"
        GnssTrustState.REJECTED -> "GNSS UNTRUSTED"
        GnssTrustState.RECOVERING -> "GNSS RECOVERING"
    }

    private fun trustVisual(state: GnssTrustState) = when (state) {
        GnssTrustState.TRUSTED -> "✓" to TrustVisualTone.NORMAL
        GnssTrustState.QUESTIONABLE -> "!" to TrustVisualTone.CAUTION
        GnssTrustState.REJECTED -> "×" to TrustVisualTone.DANGER
        GnssTrustState.RECOVERING -> "↻" to TrustVisualTone.RECOVERY
    }

    private fun format(value: Double) = "%.0f".format(java.util.Locale.ROOT, value)
}
