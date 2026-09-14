package com.example.navigation.app

import android.bluetooth.BluetoothDevice
import android.hardware.SensorManager
import android.location.LocationManager
import android.os.Looper
import com.example.navigation.gnss.AndroidGnssLocationProvider
import com.example.navigation.gnss.AndroidGnssStatusCollector
import com.example.navigation.gnss.AndroidRawGnssCollector
import com.example.navigation.obd.AndroidBluetoothObdTransport
import com.example.navigation.obd.Elm327Initializer
import com.example.navigation.obd.VehicleSpeedPollingListener
import com.example.navigation.obd.VehicleSpeedPollingScheduler
import com.example.navigation.obd.VehicleSpeedProvider
import com.example.navigation.replay.RecordingSessionController
import com.example.navigation.replay.PedestrianSessionMetadata
import com.example.navigation.replay.PedestrianPoseLabel
import com.example.navigation.replay.PedestrianEventLabel
import com.example.navigation.sensors.AndroidAccelerometerProvider
import com.example.navigation.sensors.AndroidBarometerProvider
import com.example.navigation.sensors.AndroidGameRotationVectorProvider
import com.example.navigation.sensors.AndroidGyroscopeProvider
import java.util.concurrent.Executors
import com.example.navigation.gnss.AndroidLocationEvidence
import com.example.navigation.gnss.GnssSample
import com.example.navigation.gnss.GnssStatusSnapshot
import com.example.navigation.gnss.RawGnssSample
import com.example.navigation.gnss.GnssTrustNavigationPipeline
import com.example.navigation.gnss.GnssTrustNavigationSnapshot
import com.example.navigation.gnss.SpacecraftReferenceProvider
import com.example.navigation.gnss.UnavailableSpacecraftReferenceProvider
import com.example.navigation.replay.AndroidLocationEvidenceSessionRecord
import com.example.navigation.replay.GnssSessionRecord
import com.example.navigation.replay.GnssStatusSessionRecord
import com.example.navigation.replay.RawGnssSessionRecord
import com.example.navigation.navigation.NavigationMode
import com.example.navigation.gnss.GnssCollectorCapability
import com.example.navigation.gnss.GnssCollectorSource
import com.example.navigation.core.MonotonicTimestamp
import android.os.SystemClock

sealed interface LiveGnssEvidence {
    data class Location(val sample: GnssSample, val androidEvidence: AndroidLocationEvidence) : LiveGnssEvidence
    data class Raw(val sample: RawGnssSample) : LiveGnssEvidence
    data class Status(val snapshot: GnssStatusSnapshot) : LiveGnssEvidence
}

data class LiveProviderStartResult(
    val rawGnss: Boolean,
    val gnssStatus: Boolean,
    val gyroscope: Boolean,
    val accelerometer: Boolean,
    val gameRotationVector: Boolean,
    val barometer: Boolean,
    val obdConfigured: Boolean,
    val gnssLocationCapability: com.example.navigation.gnss.EvidenceCapability = com.example.navigation.gnss.EvidenceCapability.UNAVAILABLE,
    val rawGnssCapability: com.example.navigation.gnss.EvidenceCapability = com.example.navigation.gnss.EvidenceCapability.UNAVAILABLE,
    val gnssStatusCapability: com.example.navigation.gnss.EvidenceCapability = com.example.navigation.gnss.EvidenceCapability.UNAVAILABLE
)

/** Connects live Android and selected OBD providers to the active recording session only. */
class LiveRecordingProviderBridge(
    locationManager: LocationManager,
    sensorManager: SensorManager,
    callbackLooper: Looper,
    private val controller: RecordingSessionController,
    private val onObdStatus: (String) -> Unit = {},
    private val onTrustEvidence: (LiveGnssEvidence) -> Unit = {},
    private val onTrustSnapshot: (GnssTrustNavigationSnapshot) -> Unit = {},
    referenceProvider: SpacecraftReferenceProvider = UnavailableSpacecraftReferenceProvider,
    private val onGnssLocation: (GnssSample) -> Unit = {},
    private val onSensorObservation: (MotionSensorStream, MonotonicTimestamp) -> Unit = { _, _ -> },
    private val onSensorAccuracy: (MotionSensorStream, Int, MonotonicTimestamp) -> Unit = { _, _, _ -> },
    private val onSensorProvidersStarted: (LiveProviderStartResult) -> Unit = {},
    private val onSensorProvidersStopped: () -> Unit = {}
) : AutoCloseable {
    private var activeMode = NavigationMode.UNKNOWN
    private val trustPipeline = GnssTrustNavigationPipeline(navigationMode = { activeMode }, referenceProvider = referenceProvider).also { pipeline ->
        pipeline.observe { output ->
            output.derivedRecords.forEach(controller::append)
            onTrustSnapshot(output.snapshot)
        }
    }
    private var pendingLocation: GnssSample? = null
    private val location = AndroidGnssLocationProvider(
        locationManager = locationManager,
        callbackLooper = callbackLooper,
        listener = { sample -> controller.append(sample); pendingLocation = sample; onGnssLocation(sample) },
        evidenceListener = { evidence ->
            controller.append(evidence)
            trustPipeline.onRecord(AndroidLocationEvidenceSessionRecord(evidence))
            pendingLocation?.takeIf { it.timestamp == evidence.timestamp }?.let {
                onTrustEvidence(LiveGnssEvidence.Location(it, evidence))
                trustPipeline.onRecord(GnssSessionRecord(it))
            }
            pendingLocation = null
        }
    )
    private val rawGnss = AndroidRawGnssCollector(locationManager) {
        controller.append(it); onTrustEvidence(LiveGnssEvidence.Raw(it)); trustPipeline.onRecord(RawGnssSessionRecord(it))
    }
    private val status = AndroidGnssStatusCollector(locationManager, callbackLooper) {
        controller.append(it); onTrustEvidence(LiveGnssEvidence.Status(it)); trustPipeline.onRecord(GnssStatusSessionRecord(it))
    }
    private val gyro = AndroidGyroscopeProvider(
        sensorManager, callbackLooper,
        listener = { sample -> controller.append(sample); onSensorObservation(MotionSensorStream.GYROSCOPE, sample.timestamp) },
        accuracyListener = { accuracy -> onSensorAccuracy(MotionSensorStream.GYROSCOPE, accuracy, monotonicNow()) }
    )
    private val accelerometer = AndroidAccelerometerProvider(
        sensorManager, callbackLooper,
        listener = { sample -> controller.append(sample); onSensorObservation(MotionSensorStream.ACCELEROMETER, sample.timestamp) },
        accuracyListener = { accuracy -> onSensorAccuracy(MotionSensorStream.ACCELEROMETER, accuracy, monotonicNow()) }
    )
    private val rotation = AndroidGameRotationVectorProvider(
        sensorManager, callbackLooper,
        listener = { sample -> controller.append(sample); onSensorObservation(MotionSensorStream.GAME_ROTATION_VECTOR, sample.timestamp) },
        accuracyListener = { accuracy -> onSensorAccuracy(MotionSensorStream.GAME_ROTATION_VECTOR, accuracy, monotonicNow()) }
    )
    private val barometer = AndroidBarometerProvider(sensorManager, callbackLooper) { controller.append(it) }
    private val obdExecutor = Executors.newSingleThreadExecutor()
    private var obdDevice: BluetoothDevice? = null
    private var obdTransport: AndroidBluetoothObdTransport? = null
    private var obdScheduler: VehicleSpeedPollingScheduler? = null
    @Volatile private var started = false

    fun selectObdDevice(device: BluetoothDevice?) {
        check(!started) { "Cannot change OBD adapter while recording" }
        obdDevice = device
    }

    fun start(metadata: PedestrianSessionMetadata? = null): LiveProviderStartResult {
        check(!started) { "Live recording providers are already started" }
        activeMode = metadata?.navigationMode ?: NavigationMode.UNKNOWN
        controller.start(metadata)
        return try {
            val locationCapability = location.start()
            val rawAvailable = rawGnss.start()
            val statusAvailable = status.start()
            val capabilityTime = MonotonicTimestamp(SystemClock.elapsedRealtimeNanos())
            controller.append(GnssCollectorCapability(capabilityTime, GnssCollectorSource.LOCATION, locationCapability))
            controller.append(GnssCollectorCapability(capabilityTime, GnssCollectorSource.RAW_MEASUREMENTS, rawGnss.capability()))
            controller.append(GnssCollectorCapability(capabilityTime, GnssCollectorSource.SATELLITE_STATUS, status.capability()))
            val gyroAvailable = gyro.start()
            val accelerometerAvailable = accelerometer.start()
            val rotationAvailable = rotation.start()
            val barometerAvailable = barometer.start()
            started = true
            startObdAsync()
            LiveProviderStartResult(rawAvailable, statusAvailable, gyroAvailable, accelerometerAvailable,
                rotationAvailable, barometerAvailable, obdDevice != null,
                locationCapability, rawGnss.capability(), status.capability()).also(onSensorProvidersStarted)
        } catch (error: Throwable) {
            stopProviders()
            controller.stop()
            throw error
        }
    }

    private fun startObdAsync() {
        val selected = obdDevice ?: return onObdStatus("OBD not selected")
        onObdStatus("OBD connecting")
        obdExecutor.execute {
            try {
                val transport = AndroidBluetoothObdTransport(selected)
                transport.connect()
                Elm327Initializer(transport).initialize()
                if (!started) { transport.close(); return@execute }
                val scheduler = VehicleSpeedPollingScheduler(
                    VehicleSpeedProvider(transport),
                    listener = object : VehicleSpeedPollingListener {
                        override fun onVehicleSpeed(sample: com.example.navigation.obd.VehicleSpeedSample) {
                            if (started) controller.append(sample)
                        }
                    }
                )
                obdTransport = transport
                obdScheduler = scheduler
                scheduler.start()
                onObdStatus("OBD recording speed")
            } catch (error: Throwable) {
                onObdStatus("OBD unavailable: ${error.message ?: error::class.simpleName}")
                obdTransport?.close()
                obdTransport = null
            }
        }
    }

    fun stop(): java.nio.file.Path? {
        if (!started) return null
        started = false
        obdScheduler?.close(); obdScheduler = null
        obdTransport?.close(); obdTransport = null
        stopProviders()
        onObdStatus(if (obdDevice == null) "OBD not selected" else "OBD stopped")
        return controller.stop()
    }

    fun markEvent(label: String = "manual event") {
        check(started) { "Live recording is not active" }
        controller.markEvent(label)
    }
    fun labelPose(pose: PedestrianPoseLabel) {
        check(started) { "Live recording is not active" }
        controller.labelPose(pose)
    }
    fun labelPedestrianEvent(event: PedestrianEventLabel) {
        check(started) { "Live recording is not active" }
        controller.labelPedestrianEvent(event)
    }

    override fun close() {
        stop()
        obdExecutor.shutdownNow()
    }

    private fun stopProviders() {
        barometer.stop(); rotation.stop(); accelerometer.stop(); gyro.stop()
        status.stop(); rawGnss.stop(); location.stop()
        onSensorProvidersStopped()
    }

    private fun monotonicNow(): MonotonicTimestamp = MonotonicTimestamp(SystemClock.elapsedRealtimeNanos())
}
