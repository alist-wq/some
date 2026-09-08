package com.example.navigation.replay

import com.example.navigation.core.MonotonicTimestamp
import com.example.navigation.gnss.GnssSample
import com.example.navigation.gnss.RawGnssSample
import com.example.navigation.gnss.GnssStatusSnapshot
import com.example.navigation.gnss.AndroidLocationEvidence
import com.example.navigation.gnss.AirAlertContextMarker
import com.example.navigation.gnss.GnssEpochTrustDecision
import com.example.navigation.gnss.WeightedGnssPositionSolution
import com.example.navigation.gnss.GnssCollectorCapability
import com.example.navigation.sensors.BarometerSample
import com.example.navigation.sensors.MagnetometerSample
import com.example.navigation.obd.VehicleSpeedSample
import com.example.navigation.sensors.AccelerometerSample
import com.example.navigation.sensors.GameRotationVectorSample
import com.example.navigation.sensors.GyroscopeSample
import java.nio.file.Path
import java.util.UUID
import com.example.navigation.navigation.NavigationMode

data class RecordingSensorAvailability(
    val gnss: Boolean, val rawGnss: Boolean, val gyroscope: Boolean,
    val accelerometer: Boolean, val gameRotationVector: Boolean, val obd: Boolean
)

data class RecordingStatus(
    val isRecording: Boolean = false,
    val startedAt: MonotonicTimestamp? = null,
    val elapsedNanos: Long = 0L,
    val outputPath: Path? = null
)

/** Coordinates the streaming writer and exposes explicit recording/marker operations. */
class RecordingSessionController(
    private val outputDirectory: Path,
    private val sensorAvailability: RecordingSensorAvailability,
    private val nowNanos: () -> Long = System::nanoTime
) : AutoCloseable {
    private var writer: SensorSessionWriter? = null
    private var currentStatus = RecordingStatus()

    fun status(): RecordingStatus = currentStatus.copy(
        elapsedNanos = currentStatus.startedAt?.let { nowNanos() - it.nanos } ?: 0L
    )
    fun availability(): RecordingSensorAvailability = sensorAvailability

    @Synchronized fun start(metadata: PedestrianSessionMetadata? = null): RecordingStatus {
        check(writer == null) { "A recording is already active" }
        val startedAt = MonotonicTimestamp(nowNanos())
        val path = outputDirectory.resolve("session-${UUID.randomUUID()}.navsession")
        writer = SensorSessionWriter(path, SensorSessionHeader(
            sessionId = path.fileName.toString(), startedAt = startedAt,
            routeIdentifier = metadata?.routeIdentifier,
            initialNavigationMode = metadata?.navigationMode ?: NavigationMode.UNKNOWN
        ))
        currentStatus = RecordingStatus(true, startedAt, 0L, path)
        metadata?.let {
            writerOrFail().append(PedestrianMetadataSessionRecord(startedAt, it))
            writerOrFail().append(NavigationModeSessionRecord(startedAt, NavigationMode.PEDESTRIAN))
            writerOrFail().append(PedestrianPoseSessionRecord(startedAt, it.initialPose))
        }
        return currentStatus
    }

    @Synchronized fun stop(): Path {
        val activeWriter = writer ?: error("No recording is active")
        val completedAt = MonotonicTimestamp(nowNanos())
        activeWriter.close(completedAt)
        val path = currentStatus.outputPath ?: error("Recording has no output path")
        writer = null
        currentStatus = RecordingStatus()
        return path
    }

    fun markEvent(label: String, detail: String? = null) {
        require(label.isNotBlank()) { "Marker label must not be blank" }
        writerOrFail().append(SessionMarker(MonotonicTimestamp(nowNanos()), label, detail))
    }
    fun labelPose(pose: PedestrianPoseLabel) = writerOrFail().append(
        PedestrianPoseSessionRecord(MonotonicTimestamp(nowNanos()), pose)
    )
    fun labelPedestrianEvent(event: PedestrianEventLabel, note: String? = null) = writerOrFail().append(
        PedestrianEventSessionRecord(MonotonicTimestamp(nowNanos()), event, note)
    )
    fun append(sample: GnssSample) = writerOrFail().append(GnssSessionRecord(sample))
    fun append(sample: RawGnssSample) = writerOrFail().append(RawGnssSessionRecord(sample))
    fun append(status: GnssStatusSnapshot) = writerOrFail().append(GnssStatusSessionRecord(status))
    fun append(evidence: AndroidLocationEvidence) = writerOrFail().append(AndroidLocationEvidenceSessionRecord(evidence))
    fun append(marker: AirAlertContextMarker) = writerOrFail().append(AirAlertSessionRecord(marker))
    fun append(decision: GnssEpochTrustDecision) = writerOrFail().append(GnssTrustSessionRecord(decision))
    fun append(solution: WeightedGnssPositionSolution) = writerOrFail().append(WeightedGnssSolutionSessionRecord(solution))
    fun append(record: SensorSessionRecord) = writerOrFail().append(record)
    fun append(capability: GnssCollectorCapability) = writerOrFail().append(GnssCollectorCapabilitySessionRecord(capability))
    fun append(sample: GyroscopeSample) = writerOrFail().append(GyroscopeSessionRecord(sample))
    fun append(sample: AccelerometerSample) = writerOrFail().append(AccelerometerSessionRecord(sample))
    fun append(sample: GameRotationVectorSample) = writerOrFail().append(GameRotationVectorSessionRecord(sample))
    fun append(sample: MagnetometerSample) = writerOrFail().append(MagnetometerSessionRecord(sample))
    fun append(sample: BarometerSample) = writerOrFail().append(BarometerSessionRecord(sample))
    fun append(sample: VehicleSpeedSample) = writerOrFail().append(VehicleSpeedSessionRecord(sample))
    override fun close() { if (writer != null) stop() }
    private fun writerOrFail() = writer ?: error("No recording is active")
}
