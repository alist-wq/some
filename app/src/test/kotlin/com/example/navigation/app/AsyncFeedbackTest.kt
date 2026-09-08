package com.example.navigation.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AsyncFeedbackTest {
    @Test fun `map loading has no retry action`() {
        assertEquals("LOADING OFFLINE MAP…", mapLoadFeedback(MapLoadState.LOADING).label)
        assertNull(mapLoadFeedback(MapLoadState.LOADING).actionLabel)
    }

    @Test fun `map unavailable exposes retry action`() {
        assertEquals("RETRY MAP", mapLoadFeedback(MapLoadState.UNAVAILABLE).actionLabel)
    }
}
