package com.example.navigation.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MonotonicTimestampTest {
    @Test
    fun timestampsAreOrderedByElapsedNanoseconds() {
        val earlier = MonotonicTimestamp(10L)
        val later = MonotonicTimestamp(20L)
        assertTrue(earlier < later)
        assertTrue(later > earlier)
    }

    @Test
    fun subtractionReturnsElapsedDifference() {
        val earlier = MonotonicTimestamp(1_000L)
        val later = MonotonicTimestamp(2_500L)
        assertEquals(1_500L, later.differenceNanos(earlier))
        assertEquals(1_500L, (later - earlier).toNanos())
    }

    @Test
    fun nanosecondConversionPreservesValue() {
        val timestamp = MonotonicTimestamp(123_456_789L)
        assertEquals(123_456_789L, timestamp.nanos)
    }
}
