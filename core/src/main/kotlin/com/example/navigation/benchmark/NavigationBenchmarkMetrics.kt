package com.example.navigation.benchmark

import com.example.navigation.core.MonotonicTimestamp
import com.example.navigation.gnss.GnssTrustState
import com.example.navigation.navigation.PositionConfidence

/** One timestamped comparison between the expected route state and an algorithm estimate. */
data class BenchmarkRouteSample(
    val timestamp: MonotonicTimestamp,
    val expectedSegmentId: Long?,
    val estimatedSegmentId: Long?,
    val expectedProgressMeters: Double?,
    val estimatedProgressMeters: Double?,
    val confidence: PositionConfidence
)

/** A known interval during which GNSS observations are deliberately corrupted or unavailable. */
data class GnssFaultInterval(
    val startTimestamp: MonotonicTimestamp,
    val endTimestamp: MonotonicTimestamp
) {
    init { require(endTimestamp >= startTimestamp) { "GNSS fault interval cannot end before it starts" } }
}

/** GNSS-trust state with independently-known ground truth used only by the benchmark. */
data class BenchmarkGnssTrustSample(
    val timestamp: MonotonicTimestamp,
    val state: GnssTrustState,
    val observationsAreTrustworthy: Boolean
)

data class GnssFaultResponseMetrics(
    val interval: GnssFaultInterval,
    val rejectionDelayNanos: Long?,
    val recoveryTimeNanos: Long?
)

data class NavigationBenchmarkMetrics(
    val correctRoutePercentage: Double?,
    val meanRouteProgressErrorMeters: Double?,
    val maxRouteProgressErrorMeters: Double?,
    val wrongBranchCount: Int,
    val meanGnssRejectionDelayNanos: Long?,
    val gnssFalseRejectionCount: Int,
    val meanGnssRecoveryTimeNanos: Long?,
    val lowConfidenceDurationNanos: Long,
    val faultResponses: List<GnssFaultResponseMetrics>
)

/** Calculates benchmark metrics from ordered, replay-derived samples. */
class NavigationBenchmarkMetricsEngine {
    fun calculate(
        routeSamples: List<BenchmarkRouteSample>,
        gnssTrustSamples: List<BenchmarkGnssTrustSample>,
        gnssFaultIntervals: List<GnssFaultInterval>
    ): NavigationBenchmarkMetrics {
        requireStrictlyIncreasing(routeSamples.map { it.timestamp }, "Route samples")
        requireStrictlyIncreasing(gnssTrustSamples.map { it.timestamp }, "GNSS trust samples")
        requireStrictlyIncreasing(gnssFaultIntervals.map { it.startTimestamp }, "GNSS fault intervals")

        val comparableRoutes = routeSamples.filter { it.expectedSegmentId != null && it.estimatedSegmentId != null }
        val correctRoutePercentage = comparableRoutes.takeIf { it.isNotEmpty() }?.let { comparable ->
            comparable.count { it.expectedSegmentId == it.estimatedSegmentId } * 100.0 / comparable.size
        }
        val progressErrors = routeSamples.mapNotNull { sample ->
            val expected = sample.expectedProgressMeters
            val estimated = sample.estimatedProgressMeters
            if (expected != null && estimated != null) kotlin.math.abs(expected - estimated) else null
        }
        val faultResponses = gnssFaultIntervals.map { interval ->
            val rejected = gnssTrustSamples.firstOrNull {
                it.timestamp >= interval.startTimestamp && it.timestamp <= interval.endTimestamp && it.state == GnssTrustState.REJECTED
            }
            val recovered = gnssTrustSamples.firstOrNull {
                it.timestamp >= interval.endTimestamp && it.state == GnssTrustState.TRUSTED
            }
            GnssFaultResponseMetrics(
                interval,
                rejected?.timestamp?.differenceNanos(interval.startTimestamp),
                recovered?.timestamp?.differenceNanos(interval.endTimestamp)
            )
        }
        return NavigationBenchmarkMetrics(
            correctRoutePercentage = correctRoutePercentage,
            meanRouteProgressErrorMeters = progressErrors.takeIf { it.isNotEmpty() }?.average(),
            maxRouteProgressErrorMeters = progressErrors.maxOrNull(),
            wrongBranchCount = wrongBranchCount(routeSamples),
            meanGnssRejectionDelayNanos = faultResponses.mapNotNull { it.rejectionDelayNanos }.averageOrNull(),
            gnssFalseRejectionCount = falseRejectionCount(gnssTrustSamples),
            meanGnssRecoveryTimeNanos = faultResponses.mapNotNull { it.recoveryTimeNanos }.averageOrNull(),
            lowConfidenceDurationNanos = lowConfidenceDuration(routeSamples),
            faultResponses = faultResponses
        )
    }

    private fun wrongBranchCount(samples: List<BenchmarkRouteSample>): Int {
        var mismatching = false
        var count = 0
        for (sample in samples) {
            val mismatch = sample.expectedSegmentId != null && sample.estimatedSegmentId != null && sample.expectedSegmentId != sample.estimatedSegmentId
            if (mismatch && !mismatching) count++
            mismatching = mismatch
        }
        return count
    }

    private fun falseRejectionCount(samples: List<BenchmarkGnssTrustSample>): Int =
        samples.zipWithNext().count { (previous, current) ->
            current.observationsAreTrustworthy && current.state == GnssTrustState.REJECTED && previous.state != GnssTrustState.REJECTED
        } + if (samples.firstOrNull()?.let { it.observationsAreTrustworthy && it.state == GnssTrustState.REJECTED } == true) 1 else 0

    private fun lowConfidenceDuration(samples: List<BenchmarkRouteSample>): Long =
        samples.zipWithNext().sumOf { (current, next) ->
            if (current.confidence == PositionConfidence.LOW || current.confidence == PositionConfidence.LOST) next.timestamp.differenceNanos(current.timestamp) else 0L
        }

    private fun List<Long>.averageOrNull(): Long? = takeIf { it.isNotEmpty() }?.average()?.toLong()

    private fun requireStrictlyIncreasing(timestamps: List<MonotonicTimestamp>, label: String) {
        require(timestamps.zipWithNext().all { (previous, current) -> current > previous }) { "$label must be strictly ordered by monotonic timestamp" }
    }
}
