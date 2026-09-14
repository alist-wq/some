package com.example.navigation.app

import com.example.navigation.core.MonotonicTimestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidanceAudioFocusCapabilityTest {
    @Test fun `active output requests focus and terminal output abandons it`() {
        val platform = FakePlatform(GuidanceAudioFocusResult.GRANTED)
        val owner = GuidanceAudioFocusOwner(platform)

        val active = owner.setGuidanceOutputActive(true, time(10))
        val terminal = owner.setGuidanceOutputActive(false, time(20))

        assertEquals(AudioInterruptionState.AVAILABLE, active.audio)
        assertTrue(active.guidanceOutputActive)
        assertEquals(1, platform.requestCount)
        assertFalse(terminal.guidanceOutputActive)
        assertEquals(1, platform.abandonCount)
    }

    @Test fun `focus loss recovery and route interruption retain monotonic timestamps`() {
        val platform = FakePlatform(GuidanceAudioFocusResult.GRANTED)
        val owner = GuidanceAudioFocusOwner(platform)
        owner.setGuidanceOutputActive(true, time(10))

        platform.focus(GuidanceAudioFocusEvent.TRANSIENT_LOSS, time(20))
        assertEquals(AudioInterruptionState.FOCUS_LOST, owner.snapshot().audio)
        assertEquals(time(20), owner.snapshot().timestamp)

        platform.focus(GuidanceAudioFocusEvent.GAINED, time(30))
        platform.route(false, time(40))
        assertEquals(AudioInterruptionState.ROUTE_INTERRUPTED, owner.snapshot().audio)
        assertEquals(time(40), owner.snapshot().timestamp)
    }

    @Test fun `known unavailable output route prevents a focus request`() {
        val platform = FakePlatform(GuidanceAudioFocusResult.GRANTED)
        val owner = GuidanceAudioFocusOwner(platform)
        platform.route(false, time(5))

        val state = owner.setGuidanceOutputActive(true, time(10))

        assertEquals(AudioInterruptionState.ROUTE_INTERRUPTED, state.audio)
        assertEquals(0, platform.requestCount)
    }

    @Test fun `visual continuation is deliberate and does not re-request focus`() {
        val platform = FakePlatform(GuidanceAudioFocusResult.GRANTED)
        val owner = GuidanceAudioFocusOwner(platform)
        owner.setGuidanceOutputActive(true, time(10))

        val silent = owner.continueSilently(time(20))
        platform.focus(GuidanceAudioFocusEvent.PERMANENT_LOSS, time(30))

        assertEquals(AudioInterruptionState.SILENT, silent.audio)
        assertTrue(silent.visualOnly)
        assertEquals(AudioInterruptionState.SILENT, owner.snapshot().audio)
        assertEquals(1, platform.requestCount)
        assertEquals(1, platform.abandonCount)
    }

    @Test fun `focus failure is explicit and capability feed remains presentation only`() {
        val platform = FakePlatform(GuidanceAudioFocusResult.FAILED)
        val owner = GuidanceAudioFocusOwner(platform)
        val before = NavigationMapState()
        val capability = owner.setGuidanceOutputActive(true, time(10))
        val feed = RuntimeInterruptionCapabilityFeedFactory.create(RuntimeInterruptionInput(
            permissions = RuntimePermissionCapability(RuntimePermissionState.GRANTED, RuntimePermissionState.GRANTED, false),
            sensorCapabilities = capabilities(), guidanceAudio = capability
        ))

        assertEquals(AudioInterruptionState.UNAVAILABLE, feed.audio)
        assertTrue(feed.action(RuntimeRecoveryAction.CONTINUE_SILENTLY).available)
        assertEquals(before, NavigationMapState())
    }

    private fun capabilities() = SensorCapabilityReport(
        gyroscope = true,
        accelerometer = true,
        gameRotationVector = true,
        magnetometer = true,
        barometer = false,
        rawGnss = true,
        agc = true,
        carrierFrequencies = true,
        androidVersion = 35,
        deviceModel = "fixture"
    )
    private fun time(nanos: Long) = MonotonicTimestamp(nanos)

    private class FakePlatform(private val result: GuidanceAudioFocusResult) : GuidanceAudioFocusPlatform {
        var requestCount = 0
        var abandonCount = 0
        private var focusListener: ((GuidanceAudioFocusEvent, MonotonicTimestamp) -> Unit)? = null
        private var routeListener: ((Boolean, MonotonicTimestamp) -> Unit)? = null

        override fun requestFocus(listener: (GuidanceAudioFocusEvent, MonotonicTimestamp) -> Unit): GuidanceAudioFocusResult {
            requestCount++
            focusListener = listener
            return result
        }
        override fun abandonFocus() { abandonCount++ }
        override fun setOutputRouteListener(listener: (Boolean, MonotonicTimestamp) -> Unit) { routeListener = listener }
        fun focus(event: GuidanceAudioFocusEvent, timestamp: MonotonicTimestamp) { focusListener?.invoke(event, timestamp) }
        fun route(available: Boolean, timestamp: MonotonicTimestamp) { routeListener?.invoke(available, timestamp) }
    }
}
