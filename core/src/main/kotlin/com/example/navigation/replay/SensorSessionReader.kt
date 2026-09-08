package com.example.navigation.replay

import com.example.navigation.core.MonotonicTimestamp
import java.io.BufferedInputStream
import java.io.Closeable
import java.io.EOFException
import java.io.ObjectInputStream
import java.nio.file.Files
import java.nio.file.Path

/** Sensor categories available for session filtering. */
enum class SensorType {
    GNSS,
    RAW_GNSS,
    GYROSCOPE,
    ACCELEROMETER,
    GAME_ROTATION_VECTOR,
    MAGNETOMETER,
    BAROMETER,
    VEHICLE_SPEED,
    NAVIGATION_MODE,
    MARKER,
    PEDESTRIAN_METADATA,
    PEDESTRIAN_POSE,
    PEDESTRIAN_EVENT,
    GNSS_TRUST,
    WEIGHTED_GNSS_SOLUTION,
    AIR_ALERT,
    ANDROID_LOCATION_EVIDENCE,
    GNSS_COLLECTOR_CAPABILITY,
    EXTERNAL_EVIDENCE_STATUS
}

/**
 * Lazily reads a recorded session. Only the header and the current record are
 * held while iterating; records are never accumulated by this reader.
 */
class SensorSessionReader(path: Path) : Closeable {
    private val input = ObjectInputStream(BufferedInputStream(Files.newInputStream(path)))

    val header: SensorSessionHeader = readHeader()

    /** True after iteration observes a clean footer; false for an interrupted stream. */
    var isComplete: Boolean = false
        private set
    var completedAt: MonotonicTimestamp? = null
        private set

    /**
     * Returns a lazy stream of records in file order.
     * [fromInclusive] and [toExclusive] select the timestamp range.
     * A null sensor set includes every sensor record.
     */
    fun records(
        fromInclusive: MonotonicTimestamp? = null,
        toExclusive: MonotonicTimestamp? = null,
        sensorTypes: Set<SensorType>? = null
    ): Sequence<SensorSessionRecord> = sequence {
        require(fromInclusive == null || toExclusive == null || fromInclusive <= toExclusive) {
            "fromInclusive must not be after toExclusive"
        }
        while (true) {
            val value = try {
                input.readObject()
            } catch (_: EOFException) {
                return@sequence
            }

            when (value) {
                is SensorSessionFooter -> {
                    isComplete = true
                    completedAt = value.completedAt
                    return@sequence
                }
                is SensorSessionRecord -> {
                    val inRange = (fromInclusive == null || value.timestamp >= fromInclusive) &&
                        (toExclusive == null || value.timestamp < toExclusive)
                    if (inRange && (sensorTypes == null || sensorTypeOf(value) in sensorTypes)) {
                        yield(value)
                    }
                }
                else -> throw IllegalStateException("Unexpected object in sensor session: ${value::class.qualifiedName}")
            }
        }
    }

    override fun close() = input.close()

    private fun readHeader(): SensorSessionHeader {
        val value = input.readObject()
        require(value is SensorSessionHeader) { "Sensor session does not start with a header" }
        require(value.schemaVersion <= SensorSessionHeader.CURRENT_SCHEMA_VERSION) {
            "Unsupported sensor-session schema version: ${value.schemaVersion}"
        }
        return value
    }

    private fun sensorTypeOf(record: SensorSessionRecord): SensorType = when (record) {
        is GnssSessionRecord -> SensorType.GNSS
        is RawGnssSessionRecord -> SensorType.RAW_GNSS
        is GnssStatusSessionRecord -> SensorType.GNSS
        is GyroscopeSessionRecord -> SensorType.GYROSCOPE
        is AccelerometerSessionRecord -> SensorType.ACCELEROMETER
        is GameRotationVectorSessionRecord -> SensorType.GAME_ROTATION_VECTOR
        is MagnetometerSessionRecord -> SensorType.MAGNETOMETER
        is BarometerSessionRecord -> SensorType.BAROMETER
        is VehicleSpeedSessionRecord -> SensorType.VEHICLE_SPEED
        is NavigationModeSessionRecord -> SensorType.NAVIGATION_MODE
        is SessionMarker -> SensorType.MARKER
        is PedestrianMetadataSessionRecord -> SensorType.PEDESTRIAN_METADATA
        is PedestrianPoseSessionRecord -> SensorType.PEDESTRIAN_POSE
        is PedestrianEventSessionRecord -> SensorType.PEDESTRIAN_EVENT
        is GnssTrustSessionRecord -> SensorType.GNSS_TRUST
        is WeightedGnssSolutionSessionRecord -> SensorType.WEIGHTED_GNSS_SOLUTION
        is AirAlertSessionRecord -> SensorType.AIR_ALERT
        is AndroidLocationEvidenceSessionRecord -> SensorType.ANDROID_LOCATION_EVIDENCE
        is GnssCollectorCapabilitySessionRecord -> SensorType.GNSS_COLLECTOR_CAPABILITY
        is ExternalEvidenceStatusSessionRecord -> SensorType.EXTERNAL_EVIDENCE_STATUS
    }
}
