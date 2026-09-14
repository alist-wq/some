package com.example.navigation.app

import com.example.navigation.navigation.NavigationPosition
import com.example.navigation.navigation.PositionConfidence
import com.example.navigation.routing.GeoPoint
import com.example.navigation.routing.RoutePlanningOrigin

/** Selects an explicit manual origin first; live origins need reliable confidence. */
fun pedestrianRoutePlanningOrigin(
    manual: ManualOriginTrackingState,
    livePosition: NavigationPosition?
): RoutePlanningOrigin? {
    val manualPosition = manual.position
    val position = when {
        manual.active && manualPosition != null && manualPosition.confidence != PositionConfidence.LOST -> manualPosition
        livePosition?.confidence in setOf(PositionConfidence.HIGH, PositionConfidence.MEDIUM) -> livePosition
        else -> null
    } ?: return null
    return RoutePlanningOrigin(
        GeoPoint(position.latitude, position.longitude), position.source,
        position.confidence, position.estimatedAccuracyMeters
    )
}
