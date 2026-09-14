package com.example.navigation.app

import com.example.navigation.maps.InstalledRoutingGraphResult
import com.example.navigation.maps.RegionalMapDownloadPhase
import com.example.navigation.maps.RegionalMapDownloadState
import com.example.navigation.maps.RegionalPackageVersion
import com.example.navigation.navigation.NavigationMode
import com.example.navigation.navigation.PositionConfidence
import com.example.navigation.navigation.PositionSource
import com.example.navigation.routing.ActiveRouteLifecycle
import com.example.navigation.routing.ConfirmedDestination
import com.example.navigation.routing.ConfirmedRoutePlan
import com.example.navigation.routing.GeoPoint
import com.example.navigation.routing.Route
import com.example.navigation.routing.RouteAccessType
import com.example.navigation.routing.RoutePlanCandidate
import com.example.navigation.routing.RoutePlanningOrigin
import com.example.navigation.routing.RouteSegment
import com.example.navigation.routing.RouteTurnInformation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeInterruptionCapabilityFeedTest {
    @Test fun `permission revocation exposes the resolved settings recovery action`() {
        val feed = RuntimeInterruptionCapabilityFeedFactory.create(input(
            permissions = RuntimePermissionCapability(RuntimePermissionState.REVOKED, RuntimePermissionState.GRANTED, true),
            settingsRecovery = SettingsRecoveryCapability(
                SettingsRecoveryLaunchPlan("fixture", SettingsRecoveryDestination.LOCATION, SettingsRecoveryDestination.APP_DETAILS),
                null
            )
        ))
        assertTrue(feed.action(RuntimeRecoveryAction.REQUEST_LOCATION_PERMISSION).available)
        assertTrue(feed.action(RuntimeRecoveryAction.OPEN_APP_SETTINGS).available)
        assertEquals("Open Android permission settings", feed.action(RuntimeRecoveryAction.OPEN_APP_SETTINGS).accessibilityLabel)
    }

    @Test fun `unavailable and poor sensor states never recalibrate or alter navigation`() {
        val unavailable = RuntimeInterruptionCapabilityFeedFactory.create(input(sensorCapabilities = capabilityReport(gyro = false)))
        val poor = RuntimeInterruptionCapabilityFeedFactory.create(input(headingQuality = HeadingQualityState.POOR))
        assertEquals(HeadingQualityState.UNAVAILABLE, unavailable.headingQuality)
        assertEquals(HeadingQualityState.POOR, poor.headingQuality)
        assertFalse(poor.action(RuntimeRecoveryAction.PAUSE_GUIDANCE).available)
    }

    @Test fun `audio loss offers visual silent continuation while unavailable remains explicit`() {
        val lost = RuntimeInterruptionCapabilityFeedFactory.create(input(audio = AudioInterruptionState.FOCUS_LOST))
        val unavailable = RuntimeInterruptionCapabilityFeedFactory.create(input(audio = AudioInterruptionState.UNAVAILABLE))
        assertTrue(lost.action(RuntimeRecoveryAction.CONTINUE_SILENTLY).available)
        assertTrue(unavailable.interruptions.any { it.contains("unavailable") })
    }

    @Test fun `real audio capability overrides fallback and distinguishes deliberate visual guidance`() {
        val silent = GuidanceOutputCapability(AudioInterruptionState.SILENT, com.example.navigation.core.MonotonicTimestamp(10), true, true)
        val feed = RuntimeInterruptionCapabilityFeedFactory.create(input().copy(guidanceAudio = silent))
        assertEquals(AudioInterruptionState.SILENT, feed.audio)
        assertTrue(feed.interruptions.any { it.contains("silently") })
        assertFalse(feed.action(RuntimeRecoveryAction.CONTINUE_SILENTLY).available)
    }

    @Test fun `coverage distinguishes map retry graph gap and ready routing`() {
        val retry = RuntimeInterruptionCapabilityFeedFactory.create(input(mapDownload = RegionalMapDownloadState(phase = RegionalMapDownloadPhase.FAILED)))
        val graphGap = RuntimeInterruptionCapabilityFeedFactory.create(input(
            mapDownload = RegionalMapDownloadState(phase = RegionalMapDownloadPhase.READY),
            installedRoutingGraph = InstalledRoutingGraphResult.Unavailable("Graph not installed")
        ))
        assertEquals(RouteCoverageState.MAP_UNAVAILABLE, retry.coverage)
        assertTrue(retry.action(RuntimeRecoveryAction.RETRY_MAP_DOWNLOAD).available)
        assertEquals(RouteCoverageState.GRAPH_UNAVAILABLE, graphGap.coverage)
        assertFalse(graphGap.action(RuntimeRecoveryAction.ALTERNATE_ROUTE).available)
    }

    @Test fun `pause action is available only when the live route lifecycle accepts it`() {
        val lifecycle = ActiveRouteLifecycle()
        lifecycle.start(pausePlan())

        val active = RuntimeInterruptionCapabilityFeedFactory.create(input(activeRouteLifecycle = lifecycle))
        assertTrue(active.action(RuntimeRecoveryAction.PAUSE_GUIDANCE).available)

        lifecycle.requestPause()
        val requested = RuntimeInterruptionCapabilityFeedFactory.create(input(activeRouteLifecycle = lifecycle))
        assertFalse(requested.action(RuntimeRecoveryAction.PAUSE_GUIDANCE).available)
    }

    private fun input(
        permissions: RuntimePermissionCapability = RuntimePermissionCapability(RuntimePermissionState.GRANTED, RuntimePermissionState.GRANTED, false),
        sensorCapabilities: SensorCapabilityReport = capabilityReport(),
        headingQuality: HeadingQualityState = HeadingQualityState.GOOD,
        audio: AudioInterruptionState = AudioInterruptionState.AVAILABLE,
        mapDownload: RegionalMapDownloadState = RegionalMapDownloadState(),
        installedRoutingGraph: InstalledRoutingGraphResult? = null,
        activeRouteLifecycle: ActiveRouteLifecycle? = null,
        settingsRecovery: SettingsRecoveryCapability = SettingsRecoveryCapability(null, "Android Settings recovery has not been resolved")
    ) = RuntimeInterruptionInput(permissions, sensorCapabilities, headingQuality, audio, mapDownload, installedRoutingGraph,
        activeRouteLifecycle = activeRouteLifecycle, settingsRecovery = settingsRecovery)

    private fun capabilityReport(gyro: Boolean = true) = SensorCapabilityReport(
        gyroscope = gyro, accelerometer = true, gameRotationVector = true, magnetometer = true, barometer = false,
        rawGnss = true, agc = true, carrierFrequencies = true, androidVersion = 35, deviceModel = "fixture"
    )

    private fun pausePlan(): ConfirmedRoutePlan {
        val version = RegionalPackageVersion("fixture", "map-1", "graph-1")
        val route = Route("fixture", listOf(1, 2), listOf(10), 50.0, listOf(
            RouteSegment(listOf(GeoPoint(50.0, 36.0), GeoPoint(50.0, 36.01)), 50.0, 90.0, 10, RouteTurnInformation.ARRIVAL, RouteAccessType.PEDESTRIAN)
        ))
        return ConfirmedRoutePlan("pause", RoutePlanCandidate(
            route, RoutePlanningOrigin(GeoPoint(50.0, 36.0), PositionSource.GNSS, PositionConfidence.HIGH, 5.0),
            ConfirmedDestination("destination", "Fixture", GeoPoint(50.0, 36.01), version), NavigationMode.PEDESTRIAN, version
        ))
    }
}
