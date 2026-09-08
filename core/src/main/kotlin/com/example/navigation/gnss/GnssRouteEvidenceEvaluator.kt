package com.example.navigation.gnss

import com.example.navigation.core.MonotonicTimestamp
import com.example.navigation.routing.RouteCandidateState
import kotlin.math.exp
import kotlin.math.pow

data class GnssRouteProjection(
    val distanceToCurrentRouteMeters: Double,
    val projectedRouteProgressMeters: Double
) {
    init {
        require(distanceToCurrentRouteMeters.isFinite() && distanceToCurrentRouteMeters >= 0.0)
        require(projectedRouteProgressMeters.isFinite() && projectedRouteProgressMeters >= 0.0)
    }
}

data class ReachableRouteProgress(val minimumMeters: Double, val maximumMeters: Double) {
    init {
        require(minimumMeters.isFinite() && maximumMeters.isFinite() && minimumMeters >= 0.0 && maximumMeters >= minimumMeters)
    }
}

data class GnssRouteEvidenceConfig(
    val routeDistanceScaleMeters: Double = 25.0,
    val progressScaleMeters: Double = 40.0
) {
    init {
        require(routeDistanceScaleMeters.isFinite() && routeDistanceScaleMeters > 0.0)
        require(progressScaleMeters.isFinite() && progressScaleMeters > 0.0)
    }
}

/** Scores a supplied GNSS-to-route projection against route reachability and active hypotheses. */
class GnssRouteEvidenceEvaluator(private val config: GnssRouteEvidenceConfig = GnssRouteEvidenceConfig()) {
    fun evaluate(
        timestamp: MonotonicTimestamp,
        projection: GnssRouteProjection,
        reachableProgress: ReachableRouteProgress,
        hypotheses: List<RouteCandidateState>
    ): GnssTrustEvidence {
        if (hypotheses.isEmpty()) return unavailable(timestamp, "No current route hypotheses are available")
        val routeScore = exp(-projection.distanceToCurrentRouteMeters / config.routeDistanceScaleMeters)
        val reachabilityError = when {
            projection.projectedRouteProgressMeters < reachableProgress.minimumMeters ->
                reachableProgress.minimumMeters - projection.projectedRouteProgressMeters
            projection.projectedRouteProgressMeters > reachableProgress.maximumMeters ->
                projection.projectedRouteProgressMeters - reachableProgress.maximumMeters
            else -> 0.0
        }
        val reachabilityScore = exp(-reachabilityError / config.progressScaleMeters)
        val hypothesisScore = hypotheses.sumOf { candidate ->
            candidate.probability * exp(-kotlin.math.abs(candidate.totalRouteDistanceMeters - projection.projectedRouteProgressMeters) / config.progressScaleMeters)
        }.coerceIn(0.0, 1.0)
        val score = (routeScore * reachabilityScore * hypothesisScore).pow(1.0 / 3.0)
        return GnssTrustEvidence(
            GnssTrustEvidenceType.ROUTE_CONSISTENCY,
            GnssTrustEvidenceAvailability.AVAILABLE,
            score,
            "Route distance=${format(projection.distanceToCurrentRouteMeters)}m; reachability error=${format(reachabilityError)}m; hypothesis compatibility=${format(hypothesisScore)}",
            timestamp
        )
    }

    private fun unavailable(timestamp: MonotonicTimestamp, detail: String) = GnssTrustEvidence(
        GnssTrustEvidenceType.ROUTE_CONSISTENCY, GnssTrustEvidenceAvailability.UNAVAILABLE, null, detail, timestamp
    )
    private fun format(value: Double) = "%.2f".format(java.util.Locale.ROOT, value)
}
