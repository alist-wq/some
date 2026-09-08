package com.example.navigation.app

import com.example.navigation.routing.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Test

class MapMotionInterpolatorTest {
    @Test fun `position interpolation is bounded and monotonic`() {
        val from = GeoPoint(50.0, 30.0)
        val to = GeoPoint(50.1, 30.2)
        assertEquals(from, MapMotionInterpolator.position(from, to, -1f))
        val midpoint = MapMotionInterpolator.position(from, to, 0.5f)
        assertEquals(50.05, midpoint.latitude, 0.000001)
        assertEquals(30.1, midpoint.longitude, 0.000001)
        val boundedEnd = MapMotionInterpolator.position(from, to, 2f)
        assertEquals(to.latitude, boundedEnd.latitude, 0.000001)
        assertEquals(to.longitude, boundedEnd.longitude, 0.000001)
    }

    @Test fun `heading uses the shortest bounded rotation`() {
        assertEquals(0.0, MapMotionInterpolator.heading(350.0, 10.0, 0.5f), 0.0001)
        assertEquals(10.0, MapMotionInterpolator.heading(350.0, 10.0, 1f), 0.0001)
    }
}
