package com.example.navigation.app

import com.example.navigation.gnss.GnssTrustState
import com.example.navigation.navigation.PositionConfidence
import com.example.navigation.navigation.PositionSource
import com.example.navigation.routing.GeoPoint

data class ManualOriginTrackingPresentation(
    val visible: Boolean,
    val origin: GeoPoint?,
    val statusLabel: String,
    val detail: String,
    val sourceLabel: String,
    val confidenceLabel: String,
    val accuracyLabel: String,
    val warning: String?
)

/** Keeps manual-origin provenance visible without treating it as a live position. */
object ManualOriginTrackingPresentationFactory {
    fun create(
        state: ManualOriginTrackingState,
        gnssTrustState: GnssTrustState
    ): ManualOriginTrackingPresentation {
        val status = NavigationStatusPresentationFactory.create(state.position, gnssTrustState)
        val position = state.position
        val detail = when {
            !state.active -> "Manual-origin tracking is stopped."
            position?.source == PositionSource.MANUAL_START -> "Started from a manually selected point; it is not a live measured position."
            position?.confidence == PositionConfidence.LOST -> "Measurements are unavailable. The estimate is degraded and its uncertainty is increasing."
            else -> "A subsequent navigation estimate is being shown separately from the manual origin."
        }
        return ManualOriginTrackingPresentation(
            visible = state.origin != null,
            origin = state.origin?.point,
            statusLabel = if (state.active) "MANUAL ORIGIN ACTIVE" else "MANUAL ORIGIN STOPPED",
            detail = detail,
            sourceLabel = status.sourceLabel,
            confidenceLabel = position?.confidence?.name ?: "LOST",
            accuracyLabel = status.accuracyLabel,
            warning = status.warning
        )
    }
}
