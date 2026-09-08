package com.example.navigation.navigation

import com.example.navigation.core.MonotonicTimestamp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MotionClassificationTest {
    @Test
    fun `stores normalized probabilities for every motion state`() {
        val classification = MotionClassification(
            vehicleProbability = 0.65,
            pedestrianProbability = 0.20,
            stationaryProbability = 0.10,
            unknownProbability = 0.05,
            timestamp = MonotonicTimestamp(42L)
        )

        assertEquals(0.65, classification.probabilityOf(MotionClassificationState.VEHICLE))
        assertEquals(0.20, classification.probabilityOf(MotionClassificationState.PEDESTRIAN))
        assertEquals(0.10, classification.probabilityOf(MotionClassificationState.STATIONARY))
        assertEquals(0.05, classification.probabilityOf(MotionClassificationState.UNKNOWN))
        assertEquals(MonotonicTimestamp(42L), classification.timestamp)
    }

    @Test
    fun `unknown factory remains explicit until evidence is available`() {
        val classification = MotionClassification.unknown(MonotonicTimestamp(7L))

        assertEquals(1.0, classification.probabilityOf(MotionClassificationState.UNKNOWN))
        assertEquals(0.0, classification.probabilityOf(MotionClassificationState.VEHICLE))
    }

    @Test
    fun `rejects invalid or non-normalized probability distributions`() {
        assertFailsWith<IllegalArgumentException> {
            MotionClassification(0.5, 0.3, 0.1, 0.05, MonotonicTimestamp(1L))
        }
        assertFailsWith<IllegalArgumentException> {
            MotionClassification(-0.1, 0.5, 0.3, 0.3, MonotonicTimestamp(1L))
        }
    }
}
