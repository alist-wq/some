package com.example.navigation.pedestrian

import com.example.navigation.core.MonotonicTimestamp
import com.example.navigation.navigation.*
import com.example.navigation.routing.*
import kotlin.test.*

class PedestrianNavigationPipelineTest {
    private val t = MonotonicTimestamp(1_000_000_000)

    @Test fun `pose classifier is probabilistic and preserves unknown ambiguity`() {
        val classifier = PhonePoseClassifier()
        assertEquals(PhonePose.HAND_FORWARD, classifier.classify(PhonePoseFeatures(0.1, 0.0, 1.0, t)).pose)
        assertEquals(PhonePose.HAND_SWINGING, classifier.classify(PhonePoseFeatures(4.0, 4.0, 0.0, t)).pose)
        assertEquals(PhonePose.UNKNOWN, classifier.classify(PhonePoseFeatures(0.0, 0.0, 0.0, t)).pose)
    }

    @Test fun `heading combines relative gyro trusted GNSS and route while unknown input stays uncertain`() {
        val pose = PhonePoseClassifier().classify(PhonePoseFeatures(.1, 0.0, 1.0, t))
        val result = PedestrianHeadingEstimator().estimate(PedestrianHeadingInput(0.0, 10.0, pose, 20.0, .9, 15.0, t))
        assertNotNull(result.headingDegrees); assertTrue(result.headingDegrees in 10.0..20.0)
        val unknown = PedestrianHeadingEstimator().estimate(PedestrianHeadingInput(null, 0.0, pose, null, 0.0, null, t))
        assertNull(unknown.headingDegrees); assertEquals(180.0, unknown.uncertaintyDegrees)
    }

    @Test fun `motion and dead reckoning propagate steps without acceleration integration`() {
        val pose = PhonePoseClassifier().classify(PhonePoseFeatures(.1, 0.0, 1.0, t))
        val heading = PedestrianMovementDirection(90.0, 15.0, t)
        val initial = PedestrianMotionState(0, 0.0, null, .75, .15, 180.0, 0.0, MonotonicTimestamp(0))
        val state = initial.onStep(StrideEstimate(.75, .15), heading, pose)
        assertEquals(1, state.stepCount); assertEquals(.75, state.distanceMeters)
        val origin = NavigationPosition(50.0, 30.0, PositionConfidence.HIGH, 3.0, PositionSource.GNSS, MonotonicTimestamp(0))
        val position = PedestrianDeadReckoner(origin).propagate(state, StrideEstimate(.75, .15))
        assertTrue(position.longitude > origin.longitude); assertEquals(PositionSource.PEDESTRIAN_DEAD_RECKONING, position.source)
    }

    @Test fun `route matching retains ambiguity progress and free-space state`() {
        val route = route()
        val matcher = PedestrianRouteMatcher(route)
        val matched = matcher.match(GeoPoint(50.0, 30.0005), 90.0, 20.0)
        assertIs<PedestrianMatchState.Matched>(matched); assertTrue(matched.hypotheses.isNotEmpty())
        val progress = PedestrianRouteProgressEstimator(route).estimate(matched)
        assertNotNull(progress); assertTrue(progress.totalMetersAlongRoute > 0)
        assertIs<PedestrianMatchState.FreeSpace>(matcher.match(GeoPoint(51.0, 31.0), 90.0, 20.0))
    }

    @Test fun `confidence degrades with pose ambiguity distance and unmatched path`() {
        val model = PedestrianConfidenceModel()
        val strong = model.evaluate(PedestrianConfidenceInput(.1, 10.0, .9, .05, 5.0, 5, .9, true))
        val weak = model.evaluate(PedestrianConfidenceInput(.3, 100.0, .1, .9, 500.0, 700, .2, false))
        assertTrue(strong.score > weak.score); assertTrue(strong.estimatedAccuracyMeters < weak.estimatedAccuracyMeters)
        assertEquals(PositionConfidence.LOST, weak.confidence)
    }

    private fun route(): Route {
        val a=GeoPoint(50.0,30.0); val b=GeoPoint(50.0,30.001); val c=GeoPoint(50.001,30.001)
        val segments=listOf(
            RouteSegment(listOf(a,b),72.0,90.0,1,RouteTurnInformation.START,RouteAccessType.PEDESTRIAN),
            RouteSegment(listOf(b,c),111.0,0.0,2,RouteTurnInformation.LEFT,RouteAccessType.PEDESTRIAN))
        return Route("fixture",listOf(1,2,3),listOf(10,11),183.0,segments)
    }
}
