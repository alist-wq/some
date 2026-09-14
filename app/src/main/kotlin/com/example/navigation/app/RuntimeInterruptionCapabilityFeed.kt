package com.example.navigation.app

import com.example.navigation.maps.InstalledRoutingGraphResult
import com.example.navigation.maps.RegionalMapDownloadPhase
import com.example.navigation.maps.RegionalMapDownloadState
import com.example.navigation.routing.ActiveRouteLifecycle

enum class RuntimePermissionState { GRANTED, DENIED, REVOKED, UNAVAILABLE }
enum class HeadingQualityState { GOOD, POOR, STALE, UNKNOWN, UNAVAILABLE }
enum class AudioInterruptionState { AVAILABLE, FOCUS_LOST, ROUTE_INTERRUPTED, SILENT, UNAVAILABLE }
enum class RouteCoverageState { READY, MAP_UNAVAILABLE, GRAPH_UNAVAILABLE, MAP_DOWNLOAD_IN_PROGRESS, UNKNOWN }
enum class RuntimeRecoveryAction {
    REQUEST_LOCATION_PERMISSION,
    OPEN_APP_SETTINGS,
    RETRY_MAP_DOWNLOAD,
    CONTINUE_SILENTLY,
    PAUSE_GUIDANCE,
    DOWNLOAD_MAP,
    ALTERNATE_ROUTE
}

data class RuntimePermissionCapability(
    val location: RuntimePermissionState,
    val motion: RuntimePermissionState,
    /** Eligibility only; launching Settings remains a caller-owned platform action. */
    val settingsRecoveryEligible: Boolean
)

data class RuntimeInterruptionInput(
    val permissions: RuntimePermissionCapability,
    val sensorCapabilities: SensorCapabilityReport,
    val headingQuality: HeadingQualityState = HeadingQualityState.UNKNOWN,
    val audio: AudioInterruptionState = AudioInterruptionState.UNAVAILABLE,
    val mapDownload: RegionalMapDownloadState = RegionalMapDownloadState(),
    val installedRoutingGraph: InstalledRoutingGraphResult? = null,
    val liveSensorQuality: LiveSensorQualityCapabilityFeed? = null,
    /** Read-only lifecycle capability; callers own the actual pause request. */
    val activeRouteLifecycle: ActiveRouteLifecycle? = null,
    /** Resolved Android Settings capability; callers own the user-triggered launch. */
    val settingsRecovery: SettingsRecoveryCapability = SettingsRecoveryCapability(
        launchPlan = null,
        disabledReason = "Android Settings recovery has not been resolved"
    ),
    /** Real Android focus/output capability when the platform owner is available. */
    val guidanceAudio: GuidanceOutputCapability? = null
)

data class RuntimeRecoveryActionPresentation(
    val action: RuntimeRecoveryAction,
    val available: Boolean,
    val disabledReason: String? = null,
    val accessibilityLabel: String
) {
    init { require(available || !disabledReason.isNullOrBlank()) }
}

data class RuntimeInterruptionCapabilityFeed(
    val permissions: RuntimePermissionCapability,
    val headingQuality: HeadingQualityState,
    val audio: AudioInterruptionState,
    val coverage: RouteCoverageState,
    val interruptions: List<String>,
    val actions: List<RuntimeRecoveryActionPresentation>
) {
    fun action(action: RuntimeRecoveryAction): RuntimeRecoveryActionPresentation = actions.first { it.action == action }
}

/**
 * Read-only adapter for platform/runtime availability. It never requests permissions, changes
 * audio focus, pauses a route, recalibrates a sensor, or changes trust/routing decisions.
 */
object RuntimeInterruptionCapabilityFeedFactory {
    fun create(input: RuntimeInterruptionInput): RuntimeInterruptionCapabilityFeed {
        val featureFlags = CapabilityFeatureFlags.from(input.sensorCapabilities)
        val heading = when {
            !featureFlags.isEnabled(NavigationFeature.GYRO_HEADING) ||
                !featureFlags.isEnabled(NavigationFeature.GAME_ROTATION_HEADING) -> HeadingQualityState.UNAVAILABLE
            else -> input.liveSensorQuality?.headingQuality ?: input.headingQuality
        }
        val audio = input.guidanceAudio?.audio ?: input.audio
        val coverage = coverage(input.mapDownload, input.installedRoutingGraph)
        val interruptions = buildList {
            if (input.permissions.location != RuntimePermissionState.GRANTED) add("Location permission is ${input.permissions.location.name.lowercase()}")
            if (input.permissions.motion != RuntimePermissionState.GRANTED) add("Motion permission is ${input.permissions.motion.name.lowercase()}")
            when (heading) {
                HeadingQualityState.POOR -> add("Heading quality is poor")
                HeadingQualityState.STALE -> add("Heading observations are stale")
                HeadingQualityState.UNAVAILABLE -> add("Gyroscope or game rotation vector is unavailable")
                HeadingQualityState.UNKNOWN -> add("Heading quality is unavailable")
                HeadingQualityState.GOOD -> Unit
            }
            when (audio) {
                AudioInterruptionState.FOCUS_LOST -> add("Audio focus is lost")
                AudioInterruptionState.ROUTE_INTERRUPTED -> add("Audio route is interrupted")
                AudioInterruptionState.SILENT -> add("Visual guidance is continuing silently")
                AudioInterruptionState.UNAVAILABLE -> add("Audio-focus capability is unavailable")
                AudioInterruptionState.AVAILABLE -> Unit
            }
            if (coverage != RouteCoverageState.READY) add("Route coverage is ${coverage.name.lowercase().replace('_', ' ')}")
        }
        return RuntimeInterruptionCapabilityFeed(
            permissions = input.permissions,
            headingQuality = heading,
            audio = audio,
            coverage = coverage,
            interruptions = interruptions,
            actions = actions(input, audio, coverage)
        )
    }

    private fun coverage(download: RegionalMapDownloadState, graph: InstalledRoutingGraphResult?): RouteCoverageState = when (download.phase) {
        RegionalMapDownloadPhase.DOWNLOADING, RegionalMapDownloadPhase.PREPARING, RegionalMapDownloadPhase.VERIFYING,
        RegionalMapDownloadPhase.PAUSED -> RouteCoverageState.MAP_DOWNLOAD_IN_PROGRESS
        RegionalMapDownloadPhase.READY -> when (graph) {
            is InstalledRoutingGraphResult.Ready -> RouteCoverageState.READY
            is InstalledRoutingGraphResult.Unavailable -> RouteCoverageState.GRAPH_UNAVAILABLE
            null -> RouteCoverageState.UNKNOWN
        }
        else -> RouteCoverageState.MAP_UNAVAILABLE
    }

    private fun actions(
        input: RuntimeInterruptionInput,
        audio: AudioInterruptionState,
        coverage: RouteCoverageState
    ): List<RuntimeRecoveryActionPresentation> = listOf(
        action(RuntimeRecoveryAction.REQUEST_LOCATION_PERMISSION,
            input.permissions.location != RuntimePermissionState.GRANTED,
            "Location permission is already granted"),
        action(RuntimeRecoveryAction.OPEN_APP_SETTINGS,
            input.settingsRecovery.isAvailable,
            input.settingsRecovery.disabledReason ?: "Android Settings recovery is not available",
            input.settingsRecovery.accessibilityLabel()),
        action(RuntimeRecoveryAction.RETRY_MAP_DOWNLOAD,
            input.mapDownload.phase in setOf(RegionalMapDownloadPhase.FAILED, RegionalMapDownloadPhase.CANCELLED, RegionalMapDownloadPhase.STALE, RegionalMapDownloadPhase.CORRUPT),
            "Map download is not in a retryable state"),
        action(RuntimeRecoveryAction.CONTINUE_SILENTLY,
            audio in setOf(AudioInterruptionState.FOCUS_LOST, AudioInterruptionState.ROUTE_INTERRUPTED, AudioInterruptionState.UNAVAILABLE),
            if (audio == AudioInterruptionState.SILENT) "Visual guidance is already selected" else "Audio guidance is available"),
        action(RuntimeRecoveryAction.PAUSE_GUIDANCE,
            input.activeRouteLifecycle?.canRequestPause() == true,
            "Active guidance is not currently pausable"),
        action(RuntimeRecoveryAction.DOWNLOAD_MAP,
            coverage == RouteCoverageState.MAP_UNAVAILABLE,
            "A map download is not currently required"),
        action(RuntimeRecoveryAction.ALTERNATE_ROUTE,
            false,
            "Route alternatives are not available from the current routing runtime")
    )

    private fun action(action: RuntimeRecoveryAction, available: Boolean, unavailableReason: String, accessibilityLabel: String? = null) = RuntimeRecoveryActionPresentation(
        action = action,
        available = available,
        disabledReason = unavailableReason.takeUnless { available },
        accessibilityLabel = accessibilityLabel ?: if (available) action.name.lowercase().replace('_', ' ') else "${action.name.lowercase().replace('_', ' ')} unavailable: $unavailableReason"
    )
}
