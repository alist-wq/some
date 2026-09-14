package com.example.navigation.app

import android.content.Context
import com.example.navigation.maps.RegionalPackageVersion
import com.example.navigation.navigation.NavigationMode
import com.example.navigation.navigation.PositionConfidence
import com.example.navigation.navigation.PositionSource
import com.example.navigation.routing.GeoPoint

data class InterruptedNavigationSession(
    val sessionFileName: String,
    /** Android elapsed-realtime value; valid only for diagnosis within the same boot. */
    val checkpointElapsedRealtimeNanos: Long,
    val checkpointWallClockMillis: Long
) {
    init {
        require(sessionFileName.isNotBlank())
        require(checkpointElapsedRealtimeNanos >= 0L)
        require(checkpointWallClockMillis >= 0L)
    }
}

enum class ProcessRestartRecoveryDisposition { NONE, RECOVER_INTERRUPTED_SESSION, DISCARD_STALE_SESSION }

data class ProcessRestartRecoveryDecision(
    val disposition: ProcessRestartRecoveryDisposition,
    val sessionFileName: String? = null,
    /** Always false: dead reckoning requires fresh measurements after process restart. */
    val resumeDeadReckoning: Boolean = false
)

/** Conservative process-restart policy: retain session diagnostics, never reuse an old monotonic state. */
class ProcessRestartRecoveryPolicy(private val maximumCheckpointAgeMillis: Long = 15 * 60 * 1000L) {
    init { require(maximumCheckpointAgeMillis > 0L) }

    fun evaluate(session: InterruptedNavigationSession?, nowWallClockMillis: Long): ProcessRestartRecoveryDecision {
        if (session == null) return ProcessRestartRecoveryDecision(ProcessRestartRecoveryDisposition.NONE)
        val ageMillis = nowWallClockMillis - session.checkpointWallClockMillis
        return if (ageMillis < 0L || ageMillis > maximumCheckpointAgeMillis) {
            ProcessRestartRecoveryDecision(ProcessRestartRecoveryDisposition.DISCARD_STALE_SESSION, session.sessionFileName)
        } else {
            ProcessRestartRecoveryDecision(ProcessRestartRecoveryDisposition.RECOVER_INTERRUPTED_SESSION, session.sessionFileName)
        }
    }
}

/** Minimal durable route metadata. It intentionally contains no inertial or monotonic estimator state. */
data class ConfirmedRouteRecoveryCheckpoint(
    val schemaVersion: Int = SCHEMA_VERSION,
    val confirmedRouteId: String,
    val destinationStableId: String,
    val destinationLabel: String,
    val destination: GeoPoint,
    val navigationMode: NavigationMode,
    val packageVersion: RegionalPackageVersion,
    val routeStartedWallClockMillis: Long,
    val checkpointWallClockMillis: Long,
    val lastConfidence: PositionConfidence?,
    val lastSource: PositionSource?
) {
    init {
        require(schemaVersion == SCHEMA_VERSION)
        require(confirmedRouteId.isNotBlank() && destinationStableId.isNotBlank() && destinationLabel.isNotBlank())
        require(navigationMode != NavigationMode.UNKNOWN)
        require(routeStartedWallClockMillis >= 0L && checkpointWallClockMillis >= routeStartedWallClockMillis)
    }

    val routeAgeMillis: Long get() = checkpointWallClockMillis - routeStartedWallClockMillis

    companion object { const val SCHEMA_VERSION = 1 }
}

enum class ConfirmedRouteRecoveryDisposition {
    NONE,
    RESUME_REQUIRES_FRESH_OBSERVATIONS,
    REVIEW_ONLY_STALE,
    REVIEW_ONLY_MISSING_PACKAGE,
    REVIEW_ONLY_INCOMPATIBLE_PACKAGE,
    REVIEW_ONLY_LOW_CONFIDENCE,
    DISCARDED
}

enum class ConfirmedRouteTerminalEvent { CANCELLED, ARRIVAL_ACKNOWLEDGED }

data class ConfirmedRouteRecoveryDecision(
    val disposition: ConfirmedRouteRecoveryDisposition,
    val checkpoint: ConfirmedRouteRecoveryCheckpoint? = null,
    val routeAgeMillis: Long? = null,
    val checkpointAgeMillis: Long? = null,
    val compatibility: String? = null,
    val requiresFreshLocation: Boolean = false,
    val requiresFreshSensor: Boolean = false
) {
    /** This remains false for every decision; callers must use [RouteRecoveryFreshObservationGate]. */
    val resumesGuidance: Boolean = false
}

/** Evaluates review/resume eligibility without reusing any previous motion state. */
class ConfirmedRouteRecoveryPolicy(private val maximumCheckpointAgeMillis: Long = 15 * 60 * 1000L) {
    init { require(maximumCheckpointAgeMillis > 0L) }

    fun evaluate(
        checkpoint: ConfirmedRouteRecoveryCheckpoint?,
        installedPackage: RegionalPackageVersion?,
        nowWallClockMillis: Long
    ): ConfirmedRouteRecoveryDecision {
        if (checkpoint == null) return ConfirmedRouteRecoveryDecision(ConfirmedRouteRecoveryDisposition.NONE)
        val age = nowWallClockMillis - checkpoint.checkpointWallClockMillis
        val routeAge = (nowWallClockMillis - checkpoint.routeStartedWallClockMillis).takeIf { it >= 0L }
        val base = { disposition: ConfirmedRouteRecoveryDisposition, compatibility: String ->
            ConfirmedRouteRecoveryDecision(disposition, checkpoint, routeAge, age.takeIf { it >= 0L }, compatibility)
        }
        if (age < 0L || age > maximumCheckpointAgeMillis) return base(ConfirmedRouteRecoveryDisposition.REVIEW_ONLY_STALE, "Checkpoint is stale or has a future wall-clock time")
        if (installedPackage == null) return base(ConfirmedRouteRecoveryDisposition.REVIEW_ONLY_MISSING_PACKAGE, "Regional map and routing graph package are unavailable")
        if (installedPackage != checkpoint.packageVersion) return base(ConfirmedRouteRecoveryDisposition.REVIEW_ONLY_INCOMPATIBLE_PACKAGE, "Regional map or routing graph version does not match the confirmed route")
        if (checkpoint.lastConfidence in setOf(PositionConfidence.LOW, PositionConfidence.LOST)) {
            return base(ConfirmedRouteRecoveryDisposition.REVIEW_ONLY_LOW_CONFIDENCE, "Last displayed position confidence was low or lost")
        }
        return ConfirmedRouteRecoveryDecision(
            disposition = ConfirmedRouteRecoveryDisposition.RESUME_REQUIRES_FRESH_OBSERVATIONS,
            checkpoint = checkpoint,
            routeAgeMillis = routeAge,
            checkpointAgeMillis = age,
            compatibility = "Compatible regional package; fresh location and sensor observations are required",
            requiresFreshLocation = true,
            requiresFreshSensor = true
        )
    }

    fun discarded(): ConfirmedRouteRecoveryDecision = ConfirmedRouteRecoveryDecision(ConfirmedRouteRecoveryDisposition.DISCARDED)
}

/** Ephemeral post-relaunch gate. Its state is never persisted and cannot carry a prior estimator forward. */
class RouteRecoveryFreshObservationGate {
    private var hasFreshLocation = false
    private var hasFreshSensor = false

    fun observeLocation() { hasFreshLocation = true }
    fun observeSensor() { hasFreshSensor = true }
    fun mayActivateGuidance(): Boolean = hasFreshLocation && hasFreshSensor
}

/** Small durable checkpoint for graceful UI recovery after the process hosting the foreground service is killed. */
class NavigationProcessRecoveryStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun save(session: InterruptedNavigationSession) {
        preferences.edit()
            .putString(KEY_FILE_NAME, session.sessionFileName)
            .putLong(KEY_ELAPSED_NANOS, session.checkpointElapsedRealtimeNanos)
            .putLong(KEY_WALL_CLOCK_MILLIS, session.checkpointWallClockMillis)
            .apply()
    }

    fun consume(policy: ProcessRestartRecoveryPolicy, nowWallClockMillis: Long): ProcessRestartRecoveryDecision {
        val fileName = preferences.getString(KEY_FILE_NAME, null)
        val elapsedNanos = preferences.getLong(KEY_ELAPSED_NANOS, -1L)
        val wallClockMillis = preferences.getLong(KEY_WALL_CLOCK_MILLIS, -1L)
        val saved = if (fileName.isNullOrBlank() || elapsedNanos < 0L || wallClockMillis < 0L) null
        else InterruptedNavigationSession(fileName, elapsedNanos, wallClockMillis)
        clearInterruptedSession()
        return policy.evaluate(saved, nowWallClockMillis)
    }

    fun saveConfirmedRoute(checkpoint: ConfirmedRouteRecoveryCheckpoint) {
        preferences.edit()
            .putInt(KEY_ROUTE_SCHEMA_VERSION, checkpoint.schemaVersion)
            .putString(KEY_ROUTE_ID, checkpoint.confirmedRouteId)
            .putString(KEY_DESTINATION_ID, checkpoint.destinationStableId)
            .putString(KEY_DESTINATION_LABEL, checkpoint.destinationLabel)
            .putString(KEY_DESTINATION_LATITUDE, checkpoint.destination.latitude.toString())
            .putString(KEY_DESTINATION_LONGITUDE, checkpoint.destination.longitude.toString())
            .putString(KEY_ROUTE_MODE, checkpoint.navigationMode.name)
            .putString(KEY_ROUTE_REGION_ID, checkpoint.packageVersion.regionId)
            .putString(KEY_ROUTE_MAP_VERSION, checkpoint.packageVersion.mapVersion)
            .putString(KEY_ROUTE_GRAPH_VERSION, checkpoint.packageVersion.routingGraphVersion)
            .putLong(KEY_ROUTE_STARTED_WALL_CLOCK, checkpoint.routeStartedWallClockMillis)
            .putLong(KEY_ROUTE_CHECKPOINT_WALL_CLOCK, checkpoint.checkpointWallClockMillis)
            .putString(KEY_ROUTE_LAST_CONFIDENCE, checkpoint.lastConfidence?.name)
            .putString(KEY_ROUTE_LAST_SOURCE, checkpoint.lastSource?.name)
            .apply()
    }

    fun evaluateConfirmedRoute(
        policy: ConfirmedRouteRecoveryPolicy,
        installedPackage: RegionalPackageVersion?,
        nowWallClockMillis: Long
    ): ConfirmedRouteRecoveryDecision = policy.evaluate(readConfirmedRoute(), installedPackage, nowWallClockMillis)

    fun discardConfirmedRoute(policy: ConfirmedRouteRecoveryPolicy = ConfirmedRouteRecoveryPolicy()): ConfirmedRouteRecoveryDecision {
        clearConfirmedRoute()
        return policy.discarded()
    }

    /** Explicit terminal route operations; neither can be interpreted as a resumed route. */
    fun clearConfirmedRouteFor(event: ConfirmedRouteTerminalEvent) {
        when (event) {
            ConfirmedRouteTerminalEvent.CANCELLED,
            ConfirmedRouteTerminalEvent.ARRIVAL_ACKNOWLEDGED -> clearConfirmedRoute()
        }
    }

    fun clearConfirmedRoute() { preferences.edit().remove(ROUTE_KEY_PREFIX + "schema").remove(ROUTE_KEY_PREFIX + "id")
        .remove(ROUTE_KEY_PREFIX + "destination_id").remove(ROUTE_KEY_PREFIX + "destination_label")
        .remove(ROUTE_KEY_PREFIX + "destination_latitude").remove(ROUTE_KEY_PREFIX + "destination_longitude")
        .remove(ROUTE_KEY_PREFIX + "mode").remove(ROUTE_KEY_PREFIX + "region_id")
        .remove(ROUTE_KEY_PREFIX + "map_version").remove(ROUTE_KEY_PREFIX + "graph_version")
        .remove(ROUTE_KEY_PREFIX + "started_wall_clock").remove(ROUTE_KEY_PREFIX + "checkpoint_wall_clock")
        .remove(ROUTE_KEY_PREFIX + "last_confidence").remove(ROUTE_KEY_PREFIX + "last_source").apply() }

    fun clearInterruptedSession() { preferences.edit().remove(KEY_FILE_NAME).remove(KEY_ELAPSED_NANOS).remove(KEY_WALL_CLOCK_MILLIS).apply() }
    fun clear() { preferences.edit().clear().apply() }

    private fun readConfirmedRoute(): ConfirmedRouteRecoveryCheckpoint? = runCatching {
        val schema = preferences.getInt(KEY_ROUTE_SCHEMA_VERSION, -1)
        if (schema != ConfirmedRouteRecoveryCheckpoint.SCHEMA_VERSION) return null
        ConfirmedRouteRecoveryCheckpoint(
            schemaVersion = schema,
            confirmedRouteId = requireNotNull(preferences.getString(KEY_ROUTE_ID, null)),
            destinationStableId = requireNotNull(preferences.getString(KEY_DESTINATION_ID, null)),
            destinationLabel = requireNotNull(preferences.getString(KEY_DESTINATION_LABEL, null)),
            destination = GeoPoint(
                requireNotNull(preferences.getString(KEY_DESTINATION_LATITUDE, null)).toDouble(),
                requireNotNull(preferences.getString(KEY_DESTINATION_LONGITUDE, null)).toDouble()
            ),
            navigationMode = NavigationMode.valueOf(requireNotNull(preferences.getString(KEY_ROUTE_MODE, null))),
            packageVersion = RegionalPackageVersion(
                requireNotNull(preferences.getString(KEY_ROUTE_REGION_ID, null)),
                requireNotNull(preferences.getString(KEY_ROUTE_MAP_VERSION, null)),
                requireNotNull(preferences.getString(KEY_ROUTE_GRAPH_VERSION, null))
            ),
            routeStartedWallClockMillis = preferences.getLong(KEY_ROUTE_STARTED_WALL_CLOCK, -1L),
            checkpointWallClockMillis = preferences.getLong(KEY_ROUTE_CHECKPOINT_WALL_CLOCK, -1L),
            lastConfidence = preferences.getString(KEY_ROUTE_LAST_CONFIDENCE, null)?.let(PositionConfidence::valueOf),
            lastSource = preferences.getString(KEY_ROUTE_LAST_SOURCE, null)?.let(PositionSource::valueOf)
        )
    }.getOrNull()

    private companion object {
        const val PREFERENCES = "navigation_process_recovery"
        const val KEY_FILE_NAME = "session_file_name"
        const val KEY_ELAPSED_NANOS = "checkpoint_elapsed_nanos"
        const val KEY_WALL_CLOCK_MILLIS = "checkpoint_wall_clock_millis"
        const val ROUTE_KEY_PREFIX = "confirmed_route_"
        const val KEY_ROUTE_SCHEMA_VERSION = ROUTE_KEY_PREFIX + "schema"
        const val KEY_ROUTE_ID = ROUTE_KEY_PREFIX + "id"
        const val KEY_DESTINATION_ID = ROUTE_KEY_PREFIX + "destination_id"
        const val KEY_DESTINATION_LABEL = ROUTE_KEY_PREFIX + "destination_label"
        const val KEY_DESTINATION_LATITUDE = ROUTE_KEY_PREFIX + "destination_latitude"
        const val KEY_DESTINATION_LONGITUDE = ROUTE_KEY_PREFIX + "destination_longitude"
        const val KEY_ROUTE_MODE = ROUTE_KEY_PREFIX + "mode"
        const val KEY_ROUTE_REGION_ID = ROUTE_KEY_PREFIX + "region_id"
        const val KEY_ROUTE_MAP_VERSION = ROUTE_KEY_PREFIX + "map_version"
        const val KEY_ROUTE_GRAPH_VERSION = ROUTE_KEY_PREFIX + "graph_version"
        const val KEY_ROUTE_STARTED_WALL_CLOCK = ROUTE_KEY_PREFIX + "started_wall_clock"
        const val KEY_ROUTE_CHECKPOINT_WALL_CLOCK = ROUTE_KEY_PREFIX + "checkpoint_wall_clock"
        const val KEY_ROUTE_LAST_CONFIDENCE = ROUTE_KEY_PREFIX + "last_confidence"
        const val KEY_ROUTE_LAST_SOURCE = ROUTE_KEY_PREFIX + "last_source"
    }
}
