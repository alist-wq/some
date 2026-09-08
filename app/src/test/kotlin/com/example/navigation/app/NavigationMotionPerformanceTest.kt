package com.example.navigation.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationMotionPerformanceTest {
    @Test fun `profile retains frame map and navigation update measurements`() {
        val assessment = NavigationMotionPerformance.assess(NavigationMotionProfileSample(
            frameIntervalsMillis = listOf(16.0, 17.2),
            animationLatenciesMillis = listOf(4.0),
            mapUpdateCostsMillis = listOf(3.0, 5.0),
            navigationStateLatenciesMillis = listOf(2.0, 4.0),
            memoryDeltaBytes = 1_024L,
            batteryDrainPercent = 0.3,
            reducedMotion = false
        ))

        assertEquals(17.2, assessment.maximumFrameIntervalMillis)
        assertEquals(4.0, assessment.maximumAnimationLatencyMillis)
        assertEquals(5.0, assessment.maximumMapUpdateCostMillis)
        assertEquals(4.0, assessment.maximumNavigationStateLatencyMillis)
        assertEquals(1_024L, assessment.memoryDeltaBytes)
        assertEquals(0.3, assessment.batteryDrainPercent)
        assertFalse(assessment.reducedMotion)
    }

    @Test fun `offline motion has no remote animation dependency`() {
        val assessment = NavigationMotionPerformance.assess(NavigationMotionProfileSample(reducedMotion = true))

        assertEquals(300L, NavigationMotionPerformance.mapInterpolationDurationMillis)
        assertEquals(0, assessment.offlineAnimationDependencies)
        assertTrue(assessment.reducedMotion)
        assertNull(assessment.maximumFrameIntervalMillis)
        assertNull(assessment.memoryDeltaBytes)
        assertNull(assessment.batteryDrainPercent)
    }
}
