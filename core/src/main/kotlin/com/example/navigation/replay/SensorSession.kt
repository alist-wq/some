package com.example.navigation.replay

import com.example.navigation.core.MonotonicTimestamp
import com.example.navigation.gnss.GnssSample
import com.example.navigation.gnss.RawGnssSample
import com.example.navigation.gnss.GnssStatusSnapshot
import com.example.navigation.gnss.GnssEpochTrustDecision
import com.example.navigation.gnss.WeightedGnssPositionSolution
import com.example.navigation.gnss.AirAlertContextMarker
import com.example.navigation.gnss.AndroidLocationEvidence
import com.example.navigation.gnss.GnssCollectorCapability
import com.example.navigation.gnss.ExternalEvidenceStatus
import com.example.navigation.navigation.NavigationMode
import com.example.navigation.obd.VehicleSpeedSample
import com.example.navigation.sensors.AccelerometerSample
import com.example.navigation.sensors.BarometerSample
import com.example.navigation.sensors.GameRotationVectorSample
import com.example.navigation.sensors.GyroscopeSample
import com.example.navigation.sensors.MagnetometerSample
import java.io.Serializable

/** Versioned metadata written once at the beginning of a sensor-session stream. */
data class SensorSessionHeader(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val sessionId: String,
    val startedAt: MonotonicTimestamp,
    val routeIdentifier: String? = null,
    val initialNavigationMode: NavigationMode = NavigationMode.UNKNOWN
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
        const val CURRENT_SCHEMA_VERSION = 3
    }
}

/** A timestamped record in a session stream. */
sealed interface SensorSessionRecord : Serializable {
    val timestamp: MonotonicTimestamp
}

data class GnssSessionRecord(val sample: GnssSample) : SensorSessionRecord {
    override val timestamp: MonotonicTimestamp get() = sample.timestamp
}

data class RawGnssSessionRecord(val sample: RawGnssSample) : SensorSessionRecord {
    override val timestamp: MonotonicTimestamp get() = sample.timestamp
}

data class GnssStatusSessionRecord(val status: GnssStatusSnapshot) : SensorSessionRecord {
    override val timestamp: MonotonicTimestamp get() = status.timestamp
}

data class GnssTrustSessionRecord(val decision: GnssEpochTrustDecision) : SensorSessionRecord {
    override val timestamp: MonotonicTimestamp get() = decision.timestamp
}

data class WeightedGnssSolutionSessionRecord(val solution: WeightedGnssPositionSolution) : SensorSessionRecord {
    override val timestamp: MonotonicTimestamp get() = solution.timestamp
}

data class AirAlertSessionRecord(val marker: AirAlertContextMarker) : SensorSessionRecord {
    override val timestamp: MonotonicTimestamp get() = marker.timestamp
}

data class ExternalEvidenceStatusSessionRecord(val value: ExternalEvidenceStatus) : SensorSessionRecord {
    override val timestamp: MonotonicTimestamp get() = value.timestamp
}

data class AndroidLocationEvidenceSessionRecord(val evidence: AndroidLocationEvidence) : SensorSessionRecord {
    override val timestamp: MonotonicTimestamp get() = evidence.timestamp
}

data class GnssCollectorCapabilitySessionRecord(val value: GnssCollectorCapability) : SensorSessionRecord {
    override val timestamp: MonotonicTimestamp get() = value.timestamp
}

data class GyroscopeSessionRecord(val sample: GyroscopeSample) : SensorSessionRecord {
    override val timestamp: MonotonicTimestamp get() = sample.timestamp
}

data class AccelerometerSessionRecord(val sample: AccelerometerSample) : SensorSessionRecord {
    override val timestamp: MonotonicTimestamp get() = sample.timestamp
}

data class GameRotationVectorSessionRecord(val sample: GameRotationVectorSample) : SensorSessionRecord {
    override val timestamp: MonotonicTimestamp get() = sample.timestamp
}

data class MagnetometerSessionRecord(val sample: MagnetometerSample) : SensorSessionRecord {
    override val timestamp: MonotonicTimestamp get() = sample.timestamp
}

data class BarometerSessionRecord(val sample: BarometerSample) : SensorSessionRecord {
    override val timestamp: MonotonicTimestamp get() = sample.timestamp
}

data class VehicleSpeedSessionRecord(val sample: VehicleSpeedSample) : SensorSessionRecord {
    override val timestamp: MonotonicTimestamp get() = sample.timestamp
}

data class NavigationModeSessionRecord(
    override val timestamp: MonotonicTimestamp,
    val mode: NavigationMode
) : SensorSessionRecord

data class SessionMarker(
    override val timestamp: MonotonicTimestamp,
    val label: String,
    val detail: String? = null
) : SensorSessionRecord

enum class PedestrianPoseLabel { HAND_FORWARD, HAND_SWINGING, POCKET, UNKNOWN }

enum class PedestrianEventLabel {
    WALKING_START, WALKING_STOP, TURN_LEFT, TURN_RIGHT, CONTINUE_STRAIGHT,
    INTERSECTION, ROUTE_DEPARTURE, ROUTE_RECOVERY, ARRIVAL
}

/** User-supplied context. None of these values is inferred or represented as ground truth. */
data class PedestrianSessionMetadata(
    val navigationMode: NavigationMode,
    val initialPose: PedestrianPoseLabel,
    val intendedStart: String? = null,
    val intendedDestination: String? = null,
    val routeIdentifier: String? = null,
    val routeGeometryReference: String? = null,
    val knownWalkingDistanceMeters: Double? = null,
    val deviceModel: String,
    val carryConditionNotes: String? = null,
    val knownAnomalies: List<String> = emptyList(),
    val consentConfirmed: Boolean,
    val anonymized: Boolean,
    val wallClockStartedEpochMillis: Long? = null
) : Serializable {
    init {
        require(navigationMode == NavigationMode.PEDESTRIAN) { "Pedestrian validation sessions require PEDESTRIAN mode" }
        require(deviceModel.isNotBlank()) { "Device model must not be blank" }
        require(knownWalkingDistanceMeters == null || knownWalkingDistanceMeters >= 0.0)
    }
}

data class PedestrianMetadataSessionRecord(
    override val timestamp: MonotonicTimestamp,
    val metadata: PedestrianSessionMetadata
) : SensorSessionRecord

data class PedestrianPoseSessionRecord(
    override val timestamp: MonotonicTimestamp,
    val pose: PedestrianPoseLabel
) : SensorSessionRecord

data class PedestrianEventSessionRecord(
    override val timestamp: MonotonicTimestamp,
    val event: PedestrianEventLabel,
    val note: String? = null
) : SensorSessionRecord

/** Terminal record. Its absence denotes a session interrupted before clean close. */
data class SensorSessionFooter(
    val completedAt: MonotonicTimestamp
) : Serializable {
    private companion object {
        private const val serialVersionUID = 1L
    }
}
