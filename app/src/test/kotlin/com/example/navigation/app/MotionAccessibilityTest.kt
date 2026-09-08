package com.example.navigation.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionAccessibilityTest {
    @Test fun `zero animator scale requests reduced motion`() {
        assertTrue(motionAccessibilityPreference(0f).reducedMotion)
    }

    @Test fun `positive animator scale keeps ordinary motion`() {
        assertFalse(motionAccessibilityPreference(1f).reducedMotion)
    }

    @Test fun `invalid animator scale falls back to reduced motion`() {
        assertTrue(motionAccessibilityPreference(Float.NaN).reducedMotion)
    }
}
