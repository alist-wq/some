package com.example.navigation.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RenavSplashMotionTest {
    @Test fun `spin accelerates from one to three revolutions per second`() {
        val start = RenavSplashMotion.frameAt(0)
        val midpoint = RenavSplashMotion.frameAt(1_500)
        val end = RenavSplashMotion.frameAt(3_000)
        assertEquals(0f, start.rotationDegrees, 0.001f)
        assertEquals(2.25f, (midpoint.rotationDegrees - start.rotationDegrees) / 360f, 0.001f)
        assertEquals(6f, (end.rotationDegrees - start.rotationDegrees) / 360f, 0.001f)
    }

    @Test fun `artwork fades to name and main screen hands off after five seconds`() {
        val fadeStart = RenavSplashMotion.frameAt(3_000)
        val fadeEnd = RenavSplashMotion.frameAt(3_600)
        val handoff = RenavSplashMotion.frameAt(5_000)
        assertEquals(1f, fadeStart.artworkAlpha, 0.001f)
        assertEquals(0f, fadeStart.nameAlpha, 0.001f)
        assertEquals(0f, fadeEnd.artworkAlpha, 0.001f)
        assertEquals(1f, fadeEnd.nameAlpha, 0.001f)
        assertTrue(handoff.complete)
    }
}
