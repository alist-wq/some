package com.example.navigation.replay

import com.example.navigation.core.MonotonicTimestamp

enum class RawGnssOutageBehavior { KEEP, DROP }

data class GnssOutageConfig(
    val start: MonotonicTimestamp,
    val durationNanos: Long,
    val rawBehavior: RawGnssOutageBehavior = RawGnssOutageBehavior.KEEP
) {
    init { require(durationNanos >= 0) { "durationNanos must not be negative" } }
    val endExclusive: MonotonicTimestamp get() = MonotonicTimestamp(start.nanos + durationNanos)
}

/** Replay-only GNSS outage transform. */
class GnssOutageSimulator(private val config: GnssOutageConfig) : ReplayRecordTransformer {
    override fun transform(record: SensorSessionRecord): List<SensorSessionRecord> {
        val inOutage = record.timestamp >= config.start && record.timestamp < config.endExclusive
        return if (!inOutage) listOf(record) else when (record) {
            is GnssSessionRecord -> emptyList()
            is RawGnssSessionRecord -> if (config.rawBehavior == RawGnssOutageBehavior.DROP) emptyList() else listOf(record)
            else -> listOf(record)
        }
    }
}
