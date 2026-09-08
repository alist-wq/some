package com.example.navigation.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.navigation.gnss.GnssEpochTrustDecision
import com.example.navigation.gnss.GnssTrustReasonCode
import com.example.navigation.gnss.WeightedPositionSource
import com.example.navigation.gnss.AirAlertContextMarker
import com.example.navigation.gnss.GnssTrustNavigationSnapshot
import com.example.navigation.replay.GnssTrustSessionReportWriter
import com.example.navigation.gnss.GnssTrustEvidence
import com.example.navigation.gnss.GnssTrustState
import com.example.navigation.navigation.NavigationPosition

/** Development-only snapshot. Null values are deliberately displayed as unavailable. */
data class NavigationDiagnostics(
    val gnssTrustState: GnssTrustState? = null,
    val trustEvidence: List<GnssTrustEvidence> = emptyList(),
    val obdRateHertz: Double? = null,
    val vehicleSpeedMetersPerSecond: Double? = null,
    val gyroYawRateRadiansPerSecond: Double? = null,
    val stepCadenceStepsPerMinute: Double? = null,
    val routeCandidateProbabilities: Map<Long, Double> = emptyMap(),
    val positionUncertaintyMeters: Double? = null,
    val lastTrustedGnss: NavigationPosition? = null,
    val visibleSatellitesByConstellation: Map<String, Int> = emptyMap(),
    val usedSatellitesByConstellation: Map<String, Int> = emptyMap(),
    val latestTrustDecision: GnssEpochTrustDecision? = null,
    val suspiciousEpochCount: Int = 0,
    val mockLocationEvidence: Boolean? = null,
    val weightedSource: WeightedPositionSource? = null,
    val airAlertMarker: AirAlertContextMarker? = null,
    val spacecraftReferenceStatus: com.example.navigation.gnss.ExternalEvidenceStatus? = null,
    val airAlertFeedStatus: com.example.navigation.gnss.ExternalEvidenceStatus? = null,
    val unavailableFields: List<String> = emptyList()
)

@Composable
fun NavigationDiagnosticsPanel(diagnostics: NavigationDiagnostics) {
    Column(Modifier.semantics { contentDescription = "GNSS trust diagnostics; estimates are not guaranteed positions" }.padding(8.dp)) {
        Text("DEVELOPER DIAGNOSTICS")
        Text("GNSS trust: ${diagnostics.gnssTrustState ?: "Unavailable"}")
        Text("Trust evidence: ${if (diagnostics.trustEvidence.isEmpty()) "Unavailable" else diagnostics.trustEvidence.joinToString { evidence ->
            "${evidence.type}=${evidence.trustScore ?: "unavailable"}"
        }}")
        Text("OBD rate: ${diagnostics.obdRateHertz?.let { "${format(it)} Hz" } ?: "Unavailable"}")
        Text("Vehicle speed: ${diagnostics.vehicleSpeedMetersPerSecond?.let { "${format(it)} m/s" } ?: "Unavailable"}")
        Text("Gyro yaw rate: ${diagnostics.gyroYawRateRadiansPerSecond?.let { "${format(it)} rad/s" } ?: "Unavailable"}")
        Text("Step cadence: ${diagnostics.stepCadenceStepsPerMinute?.let { "${format(it)} steps/min" } ?: "Unavailable"}")
        Text("Route candidates: ${if (diagnostics.routeCandidateProbabilities.isEmpty()) "Unavailable" else diagnostics.routeCandidateProbabilities.entries.joinToString {
            "segment ${it.key}=${format(it.value)}"
        }}")
        Text("Position uncertainty: ${diagnostics.positionUncertaintyMeters?.let { "±${format(it)} m" } ?: "Unavailable"}")
        Text("Last trusted GNSS: ${diagnostics.lastTrustedGnss?.let { "${format(it.latitude)}, ${format(it.longitude)}" } ?: "Unavailable"}")
        Text("Visible satellites: ${counts(diagnostics.visibleSatellitesByConstellation)}")
        Text("Used satellites: ${counts(diagnostics.usedSatellitesByConstellation)}")
        Text("Measurement trust: ${diagnostics.latestTrustDecision?.let { "${it.classification} ${format(it.score!!)}" } ?: "Unavailable"}")
        Text("Suspicious epochs: ${diagnostics.suspiciousEpochCount}")
        Text("Mock-location evidence: ${diagnostics.mockLocationEvidence ?: "Unavailable"}")
        Text("Position source: ${diagnostics.weightedSource ?: "Unavailable"}; this is an estimate, not guaranteed truth")
        Text("Reduced-influence reasons: ${diagnostics.latestTrustDecision?.reasons?.joinToString() ?: "Unavailable"}")
        Text("Expected geometry: ${diagnostics.latestTrustDecision?.satellites?.mapNotNull { it.reference }?.takeIf { it.isNotEmpty() }?.joinToString { "${it.constellation}/${it.svid ?: it.noradId}: ${it.source} age=${it.ageSeconds ?: "Unavailable"}s (reference evidence)" } ?: "Unavailable"}")
        Text("Air-alert context: ${diagnostics.airAlertMarker?.let { "${it.transition}; source=${it.provider}/${it.sourceStatus}" } ?: "Unavailable"}")
        Text("Spacecraft reference feed: ${externalEvidenceStatusLabel(diagnostics.spacecraftReferenceStatus, includeAge = true)}")
        Text("Air-alert feed: ${externalEvidenceStatusLabel(diagnostics.airAlertFeedStatus)}")
        Text(NEPTUN_ATTRIBUTION)
        Text("Unavailable fields: ${diagnostics.unavailableFields.ifEmpty { listOf("None reported") }.joinToString()}")
        Text("Suspicious observations remain in the local recording for analysis. Nothing is uploaded automatically.")
    }
}

private fun format(value: Double) = "%.2f".format(java.util.Locale.ROOT, value)
private fun counts(value: Map<String, Int>) = if (value.isEmpty()) "Unavailable" else value.entries.sortedBy { it.key }.joinToString { "${it.key}=${it.value}" }

const val NEPTUN_ATTRIBUTION = "Air-alert data: NEPTUN — informational aggregator, not an official warning system. https://neptun.in.ua/"
fun externalEvidenceStatusLabel(status: com.example.navigation.gnss.ExternalEvidenceStatus?, includeAge: Boolean = false): String =
    status?.let { "${it.state}; ${it.provider}" + if (includeAge) "; cache age=${it.ageSeconds ?: "Unavailable"}s" else "" } ?: "Unavailable"

object GnssTrustExportReport {
    fun render(decisions: List<GnssEpochTrustDecision>): String = GnssTrustSessionReportWriter().render(decisions)
}

fun GnssTrustNavigationSnapshot.toDiagnostics(external: ExternalEvidenceSnapshot = ExternalEvidenceSnapshot()) = NavigationDiagnostics(
    gnssTrustState = trustState,
    visibleSatellitesByConstellation = visibleSatellites.mapKeys { it.key.name },
    usedSatellitesByConstellation = usedSatellites.mapKeys { it.key.name },
    latestTrustDecision = decision,
    suspiciousEpochCount = suspiciousEpochCount,
    mockLocationEvidence = mockLocationEvidence,
    weightedSource = weightedSolution?.source ?: navigationPosition?.source?.let {
        when (it) {
            com.example.navigation.navigation.PositionSource.HYBRID -> WeightedPositionSource.HYBRID
            com.example.navigation.navigation.PositionSource.PEDESTRIAN_DEAD_RECKONING -> WeightedPositionSource.PEDESTRIAN_DEAD_RECKONING
            com.example.navigation.navigation.PositionSource.VEHICLE_DEAD_RECKONING -> WeightedPositionSource.VEHICLE_DEAD_RECKONING
            else -> null
        }
    },
    positionUncertaintyMeters = navigationPosition?.estimatedAccuracyMeters,
    airAlertMarker = external.latestAirAlertMarker,
    spacecraftReferenceStatus = external.spacecraft,
    airAlertFeedStatus = external.airAlert,
    unavailableFields = unavailableFields.sorted()
)
