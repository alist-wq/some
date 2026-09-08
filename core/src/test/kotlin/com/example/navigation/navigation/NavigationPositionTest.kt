package com.example.navigation.navigation

import com.example.navigation.core.MonotonicTimestamp
import kotlin.test.Test
import kotlin.test.assertEquals

class NavigationPositionTest {
    @Test
    fun positionExposesAccuracyConfidenceSourceAndTimestamp() {
        val position = NavigationPosition(
            latitude = 49.9935,
            longitude = 36.2304,
            confidence = PositionConfidence.MEDIUM,
            estimatedAccuracyMeters = 35.0,
            source = PositionSource.VEHICLE_DEAD_RECKONING,
            timestamp = MonotonicTimestamp(987_654_321L)
        )

        assertEquals(35.0, position.estimatedAccuracyMeters)
        assertEquals(PositionConfidence.MEDIUM, position.confidence)
        assertEquals(PositionSource.VEHICLE_DEAD_RECKONING, position.source)
        assertEquals(MonotonicTimestamp(987_654_321L), position.timestamp)
    }

    @Test
    fun requiredEnumsContainDocumentedValues() {
        assertEquals(setOf("HIGH", "MEDIUM", "LOW", "LOST"), PositionConfidence.entries.map { it.name }.toSet())
        assertEquals(setOf("GNSS", "HYBRID", "VEHICLE_DEAD_RECKONING", "PEDESTRIAN_DEAD_RECKONING", "MAP_MATCHED_ESTIMATE"), PositionSource.entries.map { it.name }.toSet())
        assertEquals(setOf("VEHICLE", "PEDESTRIAN", "UNKNOWN"), NavigationMode.entries.map { it.name }.toSet())
    }
}
