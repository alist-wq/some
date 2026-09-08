package com.example.navigation.replay

import com.example.navigation.gnss.GnssSample
import kotlin.math.cos

enum class GnssJumpBehavior { INSTANT, REMAIN, RECOVER }

data class GnssPositionJumpConfig(
    val at: com.example.navigation.core.MonotonicTimestamp,
    val northMeters: Double,
    val eastMeters: Double = 0.0,
    val behavior: GnssJumpBehavior = GnssJumpBehavior.INSTANT,
    val recoveryAfterNanos: Long? = null
) {
    init { require(recoveryAfterNanos == null || recoveryAfterNanos >= 0) { "recoveryAfterNanos must not be negative" } }
}

/** Replay-only GNSS position teleport transform. */
class GnssPositionJumpSimulator(private val config: GnssPositionJumpConfig) : ReplayRecordTransformer {
    private var triggered = false

    override fun transform(record: SensorSessionRecord): List<SensorSessionRecord> {
        if (record !is GnssSessionRecord || record.timestamp < config.at) return listOf(record)
        if (config.behavior == GnssJumpBehavior.INSTANT && triggered) return listOf(record)
        if (config.behavior == GnssJumpBehavior.RECOVER && config.recoveryAfterNanos != null &&
            record.timestamp.nanos >= config.at.nanos + config.recoveryAfterNanos) return listOf(record)
        triggered = true
        return listOf(record.copy(sample = record.sample.displaced()))
    }

    private fun GnssSample.displaced(): GnssSample {
        val latitudeDelta = config.northMeters / METERS_PER_DEGREE
        val longitudeDelta = config.eastMeters / (METERS_PER_DEGREE * cos(Math.toRadians(latitude)))
        return copy(latitude = latitude + latitudeDelta, longitude = longitude + longitudeDelta)
    }

    private companion object { const val METERS_PER_DEGREE = 111_320.0 }
}
