package com.example.navigation.replay

import com.example.navigation.core.MonotonicTimestamp

enum class GnssTimeCorruptionMode { JUMP, OFFSET, GRADUAL_DRIFT }

data class GnssTimeCorruptionConfig(
    val mode: GnssTimeCorruptionMode,
    val start: MonotonicTimestamp,
    val offsetNanos: Long,
    val endExclusive: MonotonicTimestamp? = null
) {
    init {
        require(mode != GnssTimeCorruptionMode.GRADUAL_DRIFT || (endExclusive != null && endExclusive > start))
        require(endExclusive == null || endExclusive > start)
    }
}

/** Corrupts GNSS-reported time only; every replay and sensor-fusion timestamp remains untouched. */
class GnssTimeCorruptionSimulator(private val config: GnssTimeCorruptionConfig) : ReplayRecordTransformer {
    override fun transform(record: SensorSessionRecord): List<SensorSessionRecord> {
        if (record.timestamp < config.start || (config.endExclusive != null && record.timestamp >= config.endExclusive)) return listOf(record)
        val offset = activeOffset(record.timestamp)
        return when (record) {
            is GnssSessionRecord -> listOf(record.copy(sample = record.sample.copy(
                wallClockTimestampMillis = record.sample.wallClockTimestampMillis?.let { Math.addExact(it, offset / NANOS_PER_MILLISECOND) }
            )))
            is RawGnssSessionRecord -> listOf(record.copy(sample = record.sample.copy(
                clock = record.sample.clock.copy(timeNanos = Math.addExact(record.sample.clock.timeNanos, offset))
            )))
            else -> listOf(record)
        }
    }

    private fun activeOffset(timestamp: MonotonicTimestamp): Long = when (config.mode) {
        GnssTimeCorruptionMode.JUMP, GnssTimeCorruptionMode.OFFSET -> config.offsetNanos
        GnssTimeCorruptionMode.GRADUAL_DRIFT -> {
            val duration = config.endExclusive!!.differenceNanos(config.start)
            (config.offsetNanos.toDouble() * timestamp.differenceNanos(config.start) / duration).toLong()
        }
    }

    private companion object { const val NANOS_PER_MILLISECOND = 1_000_000L }
}
