package com.example.navigation.routing

import btools.mapaccess.OsmNode
import btools.router.OsmNodeNamed
import btools.router.RoutingContext
import btools.router.RoutingEngine
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.roundToInt

data class BrouterRouteRequest(
    val origin: GeoPoint,
    val destination: GeoPoint,
    val profileId: String,
    val verifiedPackage: VerifiedBrouterPackage
)

data class BrouterRoute(
    val routeId: String,
    val packageRegionId: String,
    val packageVersion: String,
    val profileId: String,
    val profileVersion: String,
    val profileChecksum: String,
    val geometry: List<GeoPoint>,
    val distanceMeters: Int,
    val estimatedDurationSeconds: Int
)

sealed interface BrouterRouteResult {
    data class Ready(val route: BrouterRoute) : BrouterRouteResult
    data class Unavailable(val reason: String) : BrouterRouteResult
    data class NoRoute(val reason: String) : BrouterRouteResult
}

/** In-process-only bridge to the embedded BRouter core; it never obtains package data or uses a BRouter app. */
class EmbeddedBrouterRoutingAdapter private constructor(
    private val core: BrouterRoutingCore
) {
    constructor() : this(EmbeddedBrouterRoutingCore())
    internal constructor(core: BrouterRoutingCore, testOnly: Unit = Unit) : this(core)
    fun calculate(request: BrouterRouteRequest): BrouterRouteResult {
        if (request.profileId != PEDESTRIAN_PROFILE_ID) return BrouterRouteResult.Unavailable("The requested BRouter profile is unavailable")
        val packageProfile = request.verifiedPackage.profile
        if (packageProfile.fileName.toString() != "$PEDESTRIAN_PROFILE_ID.brf") {
            return BrouterRouteResult.Unavailable("The verified package does not contain the RENAV pedestrian profile")
        }
        if (!covers(request.verifiedPackage.segments, request.origin) || !covers(request.verifiedPackage.segments, request.destination)) {
            return BrouterRouteResult.Unavailable("Origin or destination is outside verified BRouter segment coverage")
        }
        return when (val result = core.route(request)) {
            is BrouterCoreRouteResult.NoRoute -> BrouterRouteResult.NoRoute(result.reason)
            is BrouterCoreRouteResult.Failed -> BrouterRouteResult.Unavailable(result.reason)
            is BrouterCoreRouteResult.Found -> {
                if (result.geometry.size < 2) return BrouterRouteResult.NoRoute("BRouter returned incomplete route geometry")
                val profileChecksum = sha256(packageProfile.toFile().readBytes())
                val identity = listOf(
                    request.verifiedPackage.regionId,
                    request.verifiedPackage.packageVersion,
                    request.profileId,
                    profileChecksum,
                    result.distanceMeters.toString(),
                    result.geometry.joinToString(";") { "%.7f,%.7f".format(Locale.ROOT, it.latitude, it.longitude) }
                ).joinToString("|")
                BrouterRouteResult.Ready(BrouterRoute(
                    routeId = sha256(identity.toByteArray()),
                    packageRegionId = request.verifiedPackage.regionId,
                    packageVersion = request.verifiedPackage.packageVersion,
                    profileId = request.profileId,
                    profileVersion = request.verifiedPackage.profileVersion,
                    profileChecksum = profileChecksum,
                    geometry = result.geometry,
                    distanceMeters = result.distanceMeters,
                    estimatedDurationSeconds = result.estimatedDurationSeconds
                ))
            }
        }
    }

    private fun covers(segments: List<java.nio.file.Path>, point: GeoPoint): Boolean = segments.any { segment ->
        val match = SEGMENT_NAME.matchEntire(segment.fileName.toString()) ?: return@any false
        val longitude = match.groupValues[2].toDouble()
        val latitude = match.groupValues[4].toDouble()
        val minimumLongitude = if (match.groupValues[1] == "E") longitude else -longitude - 5.0
        val minimumLatitude = if (match.groupValues[3] == "N") latitude else -latitude - 5.0
        point.longitude >= minimumLongitude && point.longitude <= minimumLongitude + 5.0 &&
            point.latitude >= minimumLatitude && point.latitude <= minimumLatitude + 5.0
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private companion object {
        const val PEDESTRIAN_PROFILE_ID = "renav-pedestrian"
        val SEGMENT_NAME = Regex("([EW])(\\d+)_([NS])(\\d+)\\.rd5")
    }
}

internal interface BrouterRoutingCore {
    fun route(request: BrouterRouteRequest): BrouterCoreRouteResult
}

internal sealed interface BrouterCoreRouteResult {
    data class Found(val geometry: List<GeoPoint>, val distanceMeters: Int, val estimatedDurationSeconds: Int) : BrouterCoreRouteResult
    data class NoRoute(val reason: String) : BrouterCoreRouteResult
    data class Failed(val reason: String) : BrouterCoreRouteResult
}

private class EmbeddedBrouterRoutingCore : BrouterRoutingCore {
    override fun route(request: BrouterRouteRequest): BrouterCoreRouteResult {
        return try {
            val context = RoutingContext().apply { localFunction = request.verifiedPackage.profile.toAbsolutePath().toString() }
            val waypoints = listOf(request.origin, request.destination).map(::waypoint)
            val engine = RoutingEngine(null, null, request.verifiedPackage.root.toFile(), waypoints, context)
            engine.isDaemon = true
            engine.start()
            engine.join(MAX_RUNNING_TIME_MILLIS)
            if (engine.isAlive) {
                engine.terminate()
                engine.join(TERMINATION_GRACE_MILLIS)
                BrouterCoreRouteResult.Failed("Embedded BRouter routing exceeded the ${MAX_RUNNING_TIME_MILLIS / 1_000}-second execution limit")
            } else {
                val track = engine.foundTrack ?: return BrouterCoreRouteResult.NoRoute(engine.errorMessage ?: "No pedestrian route is available")
                val geometry = track.nodes.map { GeoPoint((it.iLat - LATITUDE_OFFSET) / COORDINATE_SCALE, (it.iLon - LONGITUDE_OFFSET) / COORDINATE_SCALE) }
                BrouterCoreRouteResult.Found(geometry, engine.distance, track.totalSeconds)
            }
        } catch (error: Exception) {
            BrouterCoreRouteResult.Failed("Embedded BRouter routing failed: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    private fun waypoint(point: GeoPoint): OsmNodeNamed = OsmNodeNamed(OsmNode(
        (point.longitude * COORDINATE_SCALE + LONGITUDE_OFFSET).roundToInt(),
        (point.latitude * COORDINATE_SCALE + LATITUDE_OFFSET).roundToInt()
    ))

    private companion object {
        const val MAX_RUNNING_TIME_MILLIS = 30_000L
        const val TERMINATION_GRACE_MILLIS = 1_000L
        const val COORDINATE_SCALE = 1_000_000.0
        const val LONGITUDE_OFFSET = 180_000_000.0
        const val LATITUDE_OFFSET = 90_000_000.0
    }
}
