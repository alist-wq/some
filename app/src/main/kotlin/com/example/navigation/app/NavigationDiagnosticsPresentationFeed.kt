package com.example.navigation.app

import com.example.navigation.core.MonotonicTimestamp
import com.example.navigation.gnss.GnssTrustState
import com.example.navigation.navigation.NavigationPosition
import com.example.navigation.navigation.PositionConfidence
import com.example.navigation.navigation.PositionSource

/**
 * Immutable, display-neutral diagnostics contract. It deliberately retains only the
 * presentation facts available in [NavigationMapState] and [NavigationDiagnostics].
 */
data class NavigationDiagnosticsPresentation(
    val generatedAtNanos: Long,
    val position: PositionPresentation,
    val lastTrustedPosition: HistoricalPositionAnchor?,
    val sensorContributions: List<SensorContributionPresentation>,
    val satelliteStatus: SatelliteStatusPresentation,
    val capabilityGaps: List<String>,
    val timelineSample: DiagnosticsTimelineSample?
)

enum class PositionPresentationState { RELIABLE, DEGRADED, UNRELIABLE, DEAD_RECKONING, NO_FIX }
enum class PresentationAvailability { AVAILABLE, STALE, TIMESTAMP_UNAVAILABLE, UNAVAILABLE }

data class PositionPresentation(
    val state: PositionPresentationState,
    val source: PositionSource?,
    val confidence: PositionConfidence?,
    val timestampNanos: Long?,
    val ageNanos: Long?,
    val ageAvailability: PresentationAvailability,
    val estimatedAccuracyMeters: Double?,
    val reasons: List<String>
)

/** A previous trusted fix only; consumers must never render this as the current estimate. */
data class HistoricalPositionAnchor(
    val label: String = "LAST_TRUSTED_HISTORICAL_ANCHOR",
    val position: NavigationPosition,
    val timestampNanos: Long
)

data class SensorContributionPresentation(
    val sensor: String,
    val value: Double?,
    val timestampNanos: Long?,
    val availability: PresentationAvailability
)

data class SatelliteStatusPresentation(
    val timestampNanos: Long?,
    val availability: PresentationAvailability,
    val visibleByConstellation: Map<String, Int>,
    val usedByConstellation: Map<String, Int>
)

/** Contains no coordinates, so retention/export does not create a location-history store. */
data class DiagnosticsTimelineSample(
    val timestampNanos: Long,
    val positionState: PositionPresentationState,
    val source: PositionSource?,
    val ageAvailability: PresentationAvailability,
    val reasons: List<String>,
    val satelliteAvailability: PresentationAvailability,
    val visibleByConstellation: Map<String, Int>,
    val usedByConstellation: Map<String, Int>
)

/** Deterministic immutable bounded history for an already consent-gated diagnostics export. */
class DiagnosticsTimelineBuffer(private val capacity: Int) {
    init { require(capacity > 0) { "Timeline capacity must be positive" } }

    private var samples: List<DiagnosticsTimelineSample> = emptyList()

    fun append(sample: DiagnosticsTimelineSample): List<DiagnosticsTimelineSample> {
        samples = (samples + sample).takeLast(capacity)
        return samples
    }

    fun snapshot(): List<DiagnosticsTimelineSample> = samples
}

object NavigationDiagnosticsPresentationFactory {
    /**
     * [staleAfterNanos] is a caller-supplied presentation policy, not a trust threshold.
     * A negative age is treated as unavailable because the two monotonic observations are
     * not a valid ordered pair for an age claim.
     */
    fun create(
        state: NavigationMapState,
        now: MonotonicTimestamp,
        staleAfterNanos: Long
    ): NavigationDiagnosticsPresentation {
        require(staleAfterNanos >= 0L) { "Stale threshold cannot be negative" }
        val diagnostics = state.diagnostics
        val position = state.estimatedPosition
        val reasons = (diagnostics.latestTrustDecision?.reasons.orEmpty().map { it.name } + diagnostics.unavailableFields)
            .distinct().sorted()
        val positionPresentation = position.toPresentation(now, staleAfterNanos, diagnostics.gnssTrustState, reasons)
        val satelliteTimestamp = diagnostics.latestTrustDecision?.timestamp
        val satelliteAvailability = satelliteTimestamp.availabilityAt(now, staleAfterNanos, hasValue =
            diagnostics.visibleSatellitesByConstellation.isNotEmpty() || diagnostics.usedSatellitesByConstellation.isNotEmpty()
        )
        val satelliteStatus = SatelliteStatusPresentation(
            timestampNanos = satelliteTimestamp?.nanos,
            availability = satelliteAvailability,
            visibleByConstellation = diagnostics.visibleSatellitesByConstellation.toSortedMap(),
            usedByConstellation = diagnostics.usedSatellitesByConstellation.toSortedMap()
        )
        val contributions = listOf(
            contribution("OBD_RATE_HERTZ", diagnostics.obdRateHertz),
            contribution("VEHICLE_SPEED_METERS_PER_SECOND", diagnostics.vehicleSpeedMetersPerSecond),
            contribution("GYRO_YAW_RATE_RADIANS_PER_SECOND", diagnostics.gyroYawRateRadiansPerSecond),
            contribution("STEP_CADENCE_STEPS_PER_MINUTE", diagnostics.stepCadenceStepsPerMinute)
        ) + diagnostics.trustEvidence.sortedBy { it.timestamp.nanos }.map {
            SensorContributionPresentation("GNSS_TRUST_${it.type.name}", it.trustScore, it.timestamp.nanos,
                if (it.trustScore == null) PresentationAvailability.UNAVAILABLE else PresentationAvailability.AVAILABLE)
        }
        val sampleTimestamp = position?.timestamp ?: satelliteTimestamp
        val timeline = sampleTimestamp?.let {
            DiagnosticsTimelineSample(
                timestampNanos = it.nanos,
                positionState = positionPresentation.state,
                source = positionPresentation.source,
                ageAvailability = positionPresentation.ageAvailability,
                reasons = reasons,
                satelliteAvailability = satelliteAvailability,
                visibleByConstellation = satelliteStatus.visibleByConstellation,
                usedByConstellation = satelliteStatus.usedByConstellation
            )
        }
        return NavigationDiagnosticsPresentation(
            generatedAtNanos = now.nanos,
            position = positionPresentation,
            lastTrustedPosition = diagnostics.lastTrustedGnss?.let { HistoricalPositionAnchor(position = it, timestampNanos = it.timestamp.nanos) },
            sensorContributions = contributions,
            satelliteStatus = satelliteStatus,
            capabilityGaps = diagnostics.unavailableFields.distinct().sorted(),
            timelineSample = timeline
        )
    }

    private fun NavigationPosition?.toPresentation(
        now: MonotonicTimestamp,
        staleAfterNanos: Long,
        trustState: GnssTrustState?,
        reasons: List<String>
    ): PositionPresentation {
        if (this == null) return PositionPresentation(
            PositionPresentationState.NO_FIX, null, null, null, null,
            PresentationAvailability.UNAVAILABLE, null, reasons
        )
        val availability = timestamp.availabilityAt(now, staleAfterNanos, hasValue = true)
        return PositionPresentation(
            state = when {
                source == PositionSource.VEHICLE_DEAD_RECKONING || source == PositionSource.PEDESTRIAN_DEAD_RECKONING -> PositionPresentationState.DEAD_RECKONING
                confidence == PositionConfidence.LOST || trustState == GnssTrustState.REJECTED -> PositionPresentationState.UNRELIABLE
                confidence == PositionConfidence.LOW || trustState == GnssTrustState.QUESTIONABLE || trustState == GnssTrustState.RECOVERING -> PositionPresentationState.DEGRADED
                else -> PositionPresentationState.RELIABLE
            },
            source = source,
            confidence = confidence,
            timestampNanos = timestamp.nanos,
            ageNanos = now.differenceNanos(timestamp).takeIf { it >= 0L },
            ageAvailability = availability,
            estimatedAccuracyMeters = estimatedAccuracyMeters,
            reasons = reasons
        )
    }

    private fun MonotonicTimestamp?.availabilityAt(
        now: MonotonicTimestamp,
        staleAfterNanos: Long,
        hasValue: Boolean
    ): PresentationAvailability = when {
        !hasValue || this == null || now.differenceNanos(this) < 0L -> PresentationAvailability.UNAVAILABLE
        now.differenceNanos(this) > staleAfterNanos -> PresentationAvailability.STALE
        else -> PresentationAvailability.AVAILABLE
    }

    private fun contribution(sensor: String, value: Double?) = SensorContributionPresentation(
        sensor = sensor,
        value = value,
        timestampNanos = null,
        availability = if (value == null) PresentationAvailability.UNAVAILABLE else PresentationAvailability.TIMESTAMP_UNAVAILABLE
    )
}

/** Text-only data format for callers that already obtained explicit diagnostics-export consent. */
object DiagnosticsTimelineExport {
    private const val HEADER = "timestampNanos\tpositionState\tsource\tpositionAge\treasons\tsatelliteAvailability\tvisible\tused"

    fun render(samples: List<DiagnosticsTimelineSample>): String = buildString {
        appendLine(HEADER)
        samples.forEach { sample ->
            appendLine(listOf(
                sample.timestampNanos,
                sample.positionState.name,
                sample.source?.name.orEmpty(),
                sample.ageAvailability.name,
                sample.reasons.joinToString(","),
                sample.satelliteAvailability.name,
                encodeCounts(sample.visibleByConstellation),
                encodeCounts(sample.usedByConstellation)
            ).joinToString("\t"))
        }
    }.trimEnd()

    fun parse(text: String): List<DiagnosticsTimelineSample> {
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        require(lines.firstOrNull() == HEADER) { "Unexpected diagnostics timeline export header" }
        return lines.drop(1).map { line ->
            val cells = line.split('\t')
            require(cells.size == 8) { "Unexpected diagnostics timeline export row" }
            DiagnosticsTimelineSample(
                timestampNanos = cells[0].toLong(),
                positionState = PositionPresentationState.valueOf(cells[1]),
                source = cells[2].takeIf { it.isNotEmpty() }?.let(PositionSource::valueOf),
                ageAvailability = PresentationAvailability.valueOf(cells[3]),
                reasons = cells[4].takeIf { it.isNotEmpty() }?.split(',').orEmpty(),
                satelliteAvailability = PresentationAvailability.valueOf(cells[5]),
                visibleByConstellation = decodeCounts(cells[6]),
                usedByConstellation = decodeCounts(cells[7])
            )
        }
    }

    private fun encodeCounts(counts: Map<String, Int>) = counts.toSortedMap().entries.joinToString(",") { "${it.key}:${it.value}" }
    private fun decodeCounts(value: String): Map<String, Int> = value.takeIf { it.isNotEmpty() }?.split(',')?.associate {
        val (name, count) = it.split(':', limit = 2)
        name to count.toInt()
    }.orEmpty()
}
