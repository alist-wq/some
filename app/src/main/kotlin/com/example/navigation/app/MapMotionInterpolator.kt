package com.example.navigation.app

import com.example.navigation.routing.GeoPoint

/** Bounded interpolation for ordinary map updates; confidence policy remains owned by navigation state. */
internal object MapMotionInterpolator {
    fun position(from: GeoPoint, to: GeoPoint, fraction: Float): GeoPoint {
        val t = fraction.coerceIn(0f, 1f).toDouble()
        val latitude = from.latitude + (to.latitude - from.latitude) * t
        val longitudeDelta = ((to.longitude - from.longitude + 540.0) % 360.0) - 180.0
        val longitude = ((from.longitude + longitudeDelta * t + 540.0) % 360.0) - 180.0
        return GeoPoint(latitude, longitude)
    }

    fun heading(fromDegrees: Double, toDegrees: Double, fraction: Float): Double {
        val t = fraction.coerceIn(0f, 1f).toDouble()
        val delta = ((toDegrees - fromDegrees + 540.0) % 360.0) - 180.0
        return (fromDegrees + delta * t + 360.0) % 360.0
    }
}
