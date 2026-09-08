package com.example.navigation.pedestrian

import com.example.navigation.routing.*
import kotlin.math.*

data class PedestrianPathHypothesis(val segmentIndex: Int, val progress: RouteProgress, val distanceMeters: Double, val probability: Double)
sealed interface PedestrianMatchState { data class Matched(val hypotheses: List<PedestrianPathHypothesis>) : PedestrianMatchState; data class FreeSpace(val reason: String) : PedestrianMatchState }

class PedestrianRouteMatcher(private val route: Route, private val maximumMatchDistanceMeters: Double = 35.0) {
    fun match(point: GeoPoint, observedHeadingDegrees: Double?, headingUncertaintyDegrees: Double): PedestrianMatchState {
        val candidates = route.segments.mapIndexedNotNull { index, s ->
            val projected = project(point, s.geometry.first(), s.geometry.last()); if (projected.second > maximumMatchDistanceMeters) null else {
                val headingDifference = if (observedHeadingDegrees == null || s.headingDegrees == null) 90.0 else angle(observedHeadingDegrees, s.headingDegrees)
                val score = exp(-projected.second / 15.0) * exp(-headingDifference / (headingUncertaintyDegrees.coerceAtLeast(10.0)))
                val prior = route.segments.take(index).sumOf { it.lengthMeters }; PedestrianPathHypothesis(index, RouteProgress(index, projected.first * s.lengthMeters, prior + projected.first * s.lengthMeters), projected.second, score)
            }
        }.sortedByDescending { it.probability }.take(3)
        if (candidates.isEmpty() || candidates.first().probability < 0.08) return PedestrianMatchState.FreeSpace("No route path is supported by current position and heading")
        val sum = candidates.sumOf { it.probability }; return PedestrianMatchState.Matched(candidates.map { it.copy(probability = it.probability / sum) })
    }
    private fun project(p: GeoPoint, a: GeoPoint, b: GeoPoint): Pair<Double, Double> { val x=(p.longitude-a.longitude)*cos(Math.toRadians(a.latitude)); val y=p.latitude-a.latitude; val dx=(b.longitude-a.longitude)*cos(Math.toRadians(a.latitude)); val dy=b.latitude-a.latitude; val t=((x*dx+y*dy)/(dx*dx+dy*dy)).coerceIn(0.0,1.0); return t to hypot(x-t*dx,y-t*dy)*111_320.0 }
    private fun angle(a: Double,b: Double)=abs((a-b+540)%360-180)
}

class PedestrianRouteProgressEstimator(private val route: Route) {
    private var last = 0.0
    fun estimate(match: PedestrianMatchState): RouteProgress? { val p=(match as? PedestrianMatchState.Matched)?.hypotheses?.maxByOrNull { it.probability }?.progress ?: return null; if (p.totalMetersAlongRoute + 2.0 < last) return null; last=max(last,p.totalMetersAlongRoute); return p.copy(totalMetersAlongRoute=last) }
}
