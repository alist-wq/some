package com.example.navigation.gnss

import com.example.navigation.core.MonotonicTimestamp
import com.example.navigation.navigation.PositionConfidence
import java.io.Serializable

enum class GnssMeasurementClassification { TRUSTED, UNCERTAIN, SUSPICIOUS, REJECTED }

enum class GnssTrustReasonCode {
    SATELLITE_CONTINUITY,
    SIGNAL_ANOMALY,
    CLOCK_INCONSISTENCY,
    MEASUREMENT_INCONSISTENCY,
    INDEPENDENT_MOTION_DISAGREEMENT,
    ROUTE_DISAGREEMENT,
    PHYSICAL_CONTINUITY_VIOLATION,
    EXPECTED_GEOMETRY_DISAGREEMENT,
    MOCK_LOCATION_REPORTED,
    RECOVERY_PENDING,
    EVIDENCE_UNAVAILABLE
}

enum class EvidenceCapability { AVAILABLE, UNAVAILABLE, UNSUPPORTED, PERMISSION_DENIED }

data class SpacecraftReferenceEvidence(
    val constellation: GnssConstellation,
    val svid: Int? = null,
    val noradId: Int? = null,
    val source: String,
    val version: String? = null,
    val satelliteXmeters: Double? = null,
    val satelliteYmeters: Double? = null,
    val satelliteZmeters: Double? = null,
    val expectedElevationDegrees: Double? = null,
    val expectedAzimuthDegrees: Double? = null,
    val ageSeconds: Double? = null,
    val uncertaintyDegrees: Double? = null
) : Serializable {
    init {
        require(source.isNotBlank())
        require(ageSeconds == null || ageSeconds >= 0.0)
        require(uncertaintyDegrees == null || uncertaintyDegrees >= 0.0)
    }
}

data class SatelliteTrustDecision(
    val constellation: GnssConstellation,
    val svid: Int,
    val score: Double,
    val classification: GnssMeasurementClassification,
    val reasons: List<GnssTrustReasonCode>,
    val includedInEstimator: Boolean,
    val estimatorWeight: Double,
    val reference: SpacecraftReferenceEvidence? = null
) : Serializable {
    init {
        require(score.isFinite() && score in 0.0..1.0)
        require(estimatorWeight.isFinite() && estimatorWeight in 0.0..1.0)
        require(includedInEstimator || estimatorWeight == 0.0)
    }
}

data class GnssEpochTrustDecision(
    val timestamp: MonotonicTimestamp,
    val score: Double?,
    val classification: GnssMeasurementClassification?,
    val reasons: List<GnssTrustReasonCode>,
    val satellites: List<SatelliteTrustDecision>,
    val scoringVersion: String,
    val evidenceCapability: EvidenceCapability
) : Serializable {
    init {
        require(scoringVersion.isNotBlank())
        if (evidenceCapability == EvidenceCapability.AVAILABLE) {
            require(score != null && score.isFinite() && score in 0.0..1.0)
            require(classification != null)
        } else {
            require(score == null && classification == null)
        }
    }
}

enum class WeightedPositionSource { WEIGHTED_GNSS, HYBRID, PEDESTRIAN_DEAD_RECKONING, VEHICLE_DEAD_RECKONING }

data class WeightedGnssPositionSolution(
    val timestamp: MonotonicTimestamp,
    val latitude: Double,
    val longitude: Double,
    val estimatedAccuracyMeters: Double,
    val confidence: PositionConfidence,
    val source: WeightedPositionSource,
    val contributingWeight: Double,
    val reasonCodes: List<GnssTrustReasonCode>
) : Serializable {
    init {
        require(latitude in -90.0..90.0 && longitude in -180.0..180.0)
        require(estimatedAccuracyMeters >= 0.0)
        require(contributingWeight.isFinite() && contributingWeight >= 0.0)
    }
}

enum class AirAlertTransition { AIR_ALERT_STARTED, AIR_ALERT_ENDED }
enum class ContextSourceStatus { AVAILABLE, UNAVAILABLE, ERROR }

data class AirAlertContextMarker(
    val timestamp: MonotonicTimestamp,
    val transition: AirAlertTransition,
    val provider: String,
    val sourceStatus: ContextSourceStatus
) : Serializable {
    init { require(provider.isNotBlank()) }
}

data class AndroidLocationEvidence(
    val timestamp: MonotonicTimestamp,
    val mockLocation: Boolean?,
    val mockCapability: EvidenceCapability
) : Serializable

enum class GnssCollectorSource { LOCATION, SATELLITE_STATUS, RAW_MEASUREMENTS }

data class GnssCollectorCapability(
    val timestamp: MonotonicTimestamp,
    val source: GnssCollectorSource,
    val capability: EvidenceCapability
) : Serializable
