package com.example.navigation.replay

import com.example.navigation.core.MonotonicTimestamp
import kotlin.test.Test
import kotlin.test.assertEquals

class ReplayClockTest {
    @Test
    fun maxSpeedAdvancesVirtualTimeWithoutSleeping() {
        val clock = ReplayClock(ReplayClockMode.MAX_SPEED)

        clock.advanceTo(MonotonicTimestamp(10L))
        clock.advanceTo(MonotonicTimestamp(20L))

        assertEquals(MonotonicTimestamp(20L), clock.now)
    }

    @Test
    fun acceleratedModeScalesRecordedDelay() {
        val delays = mutableListOf<Long>()
        val clock = ReplayClock(ReplayClockMode.ACCELERATED, accelerationFactor = 2.0, sleep = delays::add)

        clock.advanceTo(MonotonicTimestamp(0L))
        clock.advanceTo(MonotonicTimestamp(10_000_000L))

        assertEquals(listOf(5L), delays)
        assertEquals(MonotonicTimestamp(10_000_000L), clock.now)
    }

    @Test
    fun stepModeAdvancesOnlyAfterEachGrantedStep() {
        val clock = ReplayClock(ReplayClockMode.STEP)
        clock.step()
        clock.advanceTo(MonotonicTimestamp(1L))
        clock.step()
        clock.advanceTo(MonotonicTimestamp(2L))

        assertEquals(MonotonicTimestamp(2L), clock.now)
    }
}
