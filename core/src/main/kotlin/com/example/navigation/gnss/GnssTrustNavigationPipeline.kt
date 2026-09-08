package com.example.navigation.gnss

import com.example.navigation.core.MonotonicTimestamp
import com.example.navigation.navigation.NavigationMode
import com.example.navigation.navigation.NavigationPosition
import com.example.navigation.navigation.PositionConfidence
import com.example.navigation.navigation.PositionSource
import com.example.navigation.replay.*
import kotlin.math.cos
import kotlin.math.hypot

data class GnssTrustNavigationSnapshot(
    val decision: GnssEpochTrustDecision? = null,
    val weightedSolution: WeightedGnssPositionSolution? = null,
    val navigationPosition: NavigationPosition? = null,
    val trustState: GnssTrustState = GnssTrustState.TRUSTED,
    val visibleSatellites: Map<GnssConstellation, Int> = emptyMap(),
    val usedSatellites: Map<GnssConstellation, Int> = emptyMap(),
    val suspiciousEpochCount: Int = 0,
    val mockLocationEvidence: Boolean? = null,
    val unavailableFields: Set<String> = emptySet()
)

data class GnssTrustPipelineOutput(
    val snapshot: GnssTrustNavigationSnapshot,
    val derivedRecords: List<SensorSessionRecord>
)

/** Shared deterministic live/replay trust integration. Receiver records are never modified. */
class GnssTrustNavigationPipeline(
    private val navigationMode: () -> NavigationMode,
    private val independentContext: (GnssSample) -> IndependentGnssContext = {
        IndependentGnssContext(navigationMode = navigationMode())
    },
    private val referenceProvider: SpacecraftReferenceProvider = UnavailableSpacecraftReferenceProvider,
    private val scorer: SatelliteTrustScorer = SatelliteTrustScorer(),
    private val estimator: WeightedGnssPositionEstimator = WeightedGnssPositionEstimator(),
    private val stateMachine: GnssTrustStateMachine = GnssTrustStateMachine()
) : SensorSessionRecordConsumer {
    private var latestStatus: GnssStatusSnapshot? = null
    private var latestMock: AndroidLocationEvidence? = null
    private var previousSatelliteKeys = emptySet<Pair<GnssConstellation, Int>>()
    private var lastTrusted: NavigationPosition? = null
    private var latestRawClock: GnssClockInfo? = null
    private var previousRawClock: GnssClockInfo? = null
    private var snapshot = GnssTrustNavigationSnapshot()
    private var outputListener: (GnssTrustPipelineOutput) -> Unit = {}

    fun observe(listener: (GnssTrustPipelineOutput) -> Unit) { outputListener = listener }
    fun snapshot(): GnssTrustNavigationSnapshot = snapshot

    override fun onRecord(record: SensorSessionRecord) {
        when (record) {
            is GnssStatusSessionRecord -> latestStatus = record.status
            is AndroidLocationEvidenceSessionRecord -> latestMock = record.evidence
            is RawGnssSessionRecord -> {
                previousRawClock = latestRawClock
                latestRawClock = record.sample.clock
            }
            is GnssSessionRecord -> evaluate(record.sample)
            else -> Unit
        }
    }

    private fun evaluate(sample: GnssSample) {
        val status = latestStatus?.takeIf { ageNanos(sample.timestamp, it.timestamp) <= STATUS_MAX_AGE_NANOS }
        val unavailable = buildSet {
            if (status == null) add("satellite status")
            if (latestRawClock == null) add("raw GNSS clock")
            if (latestMock?.mockCapability != EvidenceCapability.AVAILABLE) add("mock-location flag")
        }
        val contextBase = independentContext(sample)
        val context = contextBase.copy(
            navigationMode = navigationMode(),
            mockLocation = latestMock?.takeIf { ageNanos(sample.timestamp, it.timestamp) <= STATUS_MAX_AGE_NANOS }?.mockLocation,
            clockConsistent = clockConsistency()
        )
        val satellites = status?.satellites.orEmpty().map { satellite ->
            val svid = satellite.svid ?: 0
            SatelliteTrustInput(
                satellite,
                previousEpochContainedSatellite = if (previousSatelliteKeys.isEmpty()) null else (satellite.constellation to svid) in previousSatelliteKeys,
                reference = referenceProvider.referenceAt(sample.timestamp, satellite.constellation, svid)
            )
        }
        previousSatelliteKeys = satellites.map { it.status.constellation to (it.status.svid ?: 0) }.toSet()
        val decision = scorer.score(sample.timestamp, satellites, context)
        val state = decision.score?.let(stateMachine::update)?.state ?: stateMachine.state
        val candidateWeight = decision.satellites.sumOf { it.estimatorWeight }
        val solution = if (decision.classification != GnssMeasurementClassification.REJECTED) estimator.estimate(
            sample.timestamp,
            listOf(WeightedPositionCandidate(sample.latitude, sample.longitude, candidateWeight.coerceAtLeast(0.0001))),
            decision
        ) else null
        val navigationPosition = if (solution != null) {
            val recovering = state == GnssTrustState.RECOVERING || state == GnssTrustState.QUESTIONABLE
            NavigationPosition(
                solution.latitude, solution.longitude,
                if (recovering) PositionConfidence.MEDIUM else solution.confidence,
                solution.estimatedAccuracyMeters.coerceAtLeast(sample.horizontalAccuracyMeters?.toDouble() ?: 0.0),
                if (recovering) PositionSource.HYBRID else PositionSource.GNSS,
                solution.timestamp
            ).also { if (state == GnssTrustState.TRUSTED) lastTrusted = it }
        } else fallback(sample.timestamp)
        val suspiciousCount = snapshot.suspiciousEpochCount + if (
            decision.classification == GnssMeasurementClassification.SUSPICIOUS || decision.classification == GnssMeasurementClassification.REJECTED
        ) 1 else 0
        snapshot = GnssTrustNavigationSnapshot(
            decision, solution, navigationPosition, state,
            status?.satellites.orEmpty().groupingBy { it.constellation }.eachCount(),
            status?.satellites.orEmpty().filter { it.usedInFix }.groupingBy { it.constellation }.eachCount(),
            suspiciousCount, context.mockLocation, unavailable
        )
        val derived = buildList<SensorSessionRecord> {
            add(GnssTrustSessionRecord(decision))
            solution?.let { add(WeightedGnssSolutionSessionRecord(it)) }
        }
        outputListener(GnssTrustPipelineOutput(snapshot, derived))
    }

    private fun fallback(timestamp: MonotonicTimestamp): NavigationPosition? = lastTrusted?.let { anchor ->
        val seconds = ageNanos(timestamp, anchor.timestamp) / 1_000_000_000.0
        anchor.copy(
            confidence = if (seconds < 30.0) PositionConfidence.LOW else PositionConfidence.LOST,
            estimatedAccuracyMeters = anchor.estimatedAccuracyMeters + seconds * 1.5,
            source = if (navigationMode() == NavigationMode.VEHICLE) PositionSource.VEHICLE_DEAD_RECKONING else PositionSource.PEDESTRIAN_DEAD_RECKONING,
            timestamp = timestamp
        )
    }

    private fun clockConsistency(): Boolean? {
        val before = previousRawClock ?: return null
        val current = latestRawClock ?: return null
        return current.hardwareClockDiscontinuityCount == before.hardwareClockDiscontinuityCount && current.timeNanos >= before.timeNanos
    }

    private fun ageNanos(a: MonotonicTimestamp, b: MonotonicTimestamp) = kotlin.math.abs(a.differenceNanos(b))

    private companion object { const val STATUS_MAX_AGE_NANOS = 2_000_000_000L }
}

/** Runs recorded observations through the same trust integration and retains original plus derived records. */
class GnssTrustReplayProcessor(private val pipeline: GnssTrustNavigationPipeline) {
    fun process(path: java.nio.file.Path): List<SensorSessionRecord> {
        val output = mutableListOf<SensorSessionRecord>()
        pipeline.observe { result -> output += result.derivedRecords }
        ReplayEngine(path, ReplayClock(ReplayClockMode.MAX_SPEED)).replay(consumer = { record ->
            output += record
            if (record !is GnssTrustSessionRecord && record !is WeightedGnssSolutionSessionRecord) pipeline.onRecord(record)
        })
        return output.sortedBy { it.timestamp.nanos }
    }
}
