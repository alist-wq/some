package com.example.navigation.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorManager
import android.location.LocationManager
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.navigation.replay.RecordingSensorAvailability
import com.example.navigation.replay.RecordingSessionController
import com.example.navigation.replay.PedestrianSessionMetadata
import com.example.navigation.replay.PedestrianPoseLabel
import com.example.navigation.replay.PedestrianEventLabel
import java.nio.file.Path
import com.example.navigation.gnss.GnssTrustNavigationSnapshot
import com.example.navigation.core.MonotonicTimestamp

data class NavigationServiceSnapshot(
    val recording: Boolean,
    val obdStatus: String,
    val message: String,
    val gnssTrust: GnssTrustNavigationSnapshot? = null,
    val externalEvidence: ExternalEvidenceSnapshot = ExternalEvidenceSnapshot(),
    val liveSensorQuality: LiveSensorQualityCapabilityFeed? = null,
    val guidanceAudio: GuidanceOutputCapability? = null
)

/** Owns active live providers independently of any activity instance. */
class NavigationForegroundService : Service() {
    inner class LocalBinder : Binder() { fun service(): NavigationForegroundService = this@NavigationForegroundService }

    private val binder = LocalBinder()
    private lateinit var controller: RecordingSessionController
    private lateinit var bridge: LiveRecordingProviderBridge
    private lateinit var recoveryStore: NavigationProcessRecoveryStore
    private lateinit var externalEvidence: ExternalEvidenceCoordinator
    private lateinit var sensorQuality: LiveSensorQualityCapabilityTracker
    private lateinit var guidanceAudio: GuidanceAudioFocusOwner
    @Volatile private var snapshot = NavigationServiceSnapshot(false, "OBD not selected", "Ready")

    override fun onCreate() {
        super.onCreate()
        val sensors = getSystemService(SENSOR_SERVICE) as SensorManager
        sensorQuality = LiveSensorQualityCapabilityTracker(SensorCapabilityCollector.collect(this))
        guidanceAudio = GuidanceAudioFocusOwner(AndroidGuidanceAudioFocusPlatform(getSystemService(AudioManager::class.java)))
        controller = RecordingSessionController(
            filesDir.toPath().resolve("recordings"),
            RecordingSensorAvailability(
                gnss = true,
                rawGnss = true,
                gyroscope = sensors.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null,
                accelerometer = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null,
                gameRotationVector = sensors.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR) != null,
                obd = getSystemService(BluetoothManager::class.java).adapter != null
            )
        )
        externalEvidence = ExternalEvidenceCoordinator(cacheDir.resolve("external-evidence"), controller, { evidence ->
            snapshot = snapshot.copy(externalEvidence = evidence)
        })
        bridge = LiveRecordingProviderBridge(
            getSystemService(LOCATION_SERVICE) as LocationManager, sensors, Looper.getMainLooper(), controller,
            onObdStatus = { obd -> snapshot = snapshot.copy(obdStatus = obd) },
            onTrustSnapshot = { trust -> snapshot = snapshot.copy(gnssTrust = trust) },
            referenceProvider = externalEvidence.referenceProvider,
            onGnssLocation = { sample -> externalEvidence.updateLocation(sample.timestamp,
                sample.wallClockTimestampMillis ?: System.currentTimeMillis(), sample.latitude, sample.longitude,
                sample.altitudeMeters ?: 0.0) },
            onSensorObservation = { stream, timestamp ->
                sensorQuality.onObservation(stream, timestamp)
                updateSensorQuality(timestamp)
            },
            onSensorAccuracy = { stream, accuracy, timestamp ->
                sensorQuality.onReportedAccuracy(stream, accuracy, timestamp)
                updateSensorQuality(timestamp)
            },
            onSensorProvidersStarted = { result ->
                sensorQuality.onProvidersStarted(result)
                updateSensorQuality(MonotonicTimestamp(SystemClock.elapsedRealtimeNanos()))
            },
            onSensorProvidersStopped = {
                sensorQuality.onProvidersStopped()
                updateSensorQuality(MonotonicTimestamp(SystemClock.elapsedRealtimeNanos()))
            }
        )
        recoveryStore = NavigationProcessRecoveryStore(this)
        snapshot = recoverySnapshot(recoveryStore.consume(ProcessRestartRecoveryPolicy(), System.currentTimeMillis()))
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording(intent.getStringExtra(EXTRA_OBD_ADDRESS), intent.serializableMetadata())
            ACTION_STOP -> stopRecording()
        }
        return START_NOT_STICKY
    }

    /** Re-evaluates callback freshness on read; it does not drive navigation or sensor collection. */
    fun currentSnapshot(): NavigationServiceSnapshot {
        if (::sensorQuality.isInitialized) updateSensorQuality(MonotonicTimestamp(SystemClock.elapsedRealtimeNanos()))
        if (::guidanceAudio.isInitialized) snapshot = snapshot.copy(guidanceAudio = guidanceAudio.snapshot())
        return snapshot
    }
    fun recordingController(): RecordingSessionController = controller
    fun markEvent() { if (snapshot.recording) bridge.markEvent("manual event") }
    fun labelPose(pose: PedestrianPoseLabel) { if (snapshot.recording) bridge.labelPose(pose) }
    fun labelPedestrianEvent(event: PedestrianEventLabel) { if (snapshot.recording) bridge.labelPedestrianEvent(event) }

    private fun updateSensorQuality(now: MonotonicTimestamp) {
        snapshot = snapshot.copy(liveSensorQuality = sensorQuality.snapshot(now))
    }

    private fun startRecording(address: String?, metadata: PedestrianSessionMetadata?) {
        if (snapshot.recording) return
        startAsForeground("Starting navigation recording")
        try {
            val device = address?.let { target ->
                getSystemService(BluetoothManager::class.java).adapter?.bondedDevices?.firstOrNull { it.address == target }
            }
            bridge.selectObdDevice(device)
            val result = bridge.start(metadata)
            externalEvidence.start()
            controller.status().outputPath?.fileName?.toString()?.let { fileName ->
                recoveryStore.save(InterruptedNavigationSession(fileName, SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis()))
            }
            snapshot = NavigationServiceSnapshot(
                recording = true,
                obdStatus = if (device == null) "OBD not selected" else "OBD connecting",
                message = "Recording active: GNSS=${result.gnssLocationCapability}, raw GNSS=${result.rawGnssCapability}, status=${result.gnssStatusCapability}, gyro=${result.gyroscope}, accelerometer=${result.accelerometer}, rotation=${result.gameRotationVector}",
                gnssTrust = snapshot.gnssTrust,
                externalEvidence = snapshot.externalEvidence,
                liveSensorQuality = sensorQuality.snapshot(MonotonicTimestamp(SystemClock.elapsedRealtimeNanos()))
            )
            updateNotification("Navigation recording active")
        } catch (error: Throwable) {
            snapshot = snapshot.copy(message = "Unable to start recording: ${error.message}")
            stopForegroundCompat()
            stopSelf()
        }
    }

    private fun stopRecording() {
        if (snapshot.recording) {
            recoveryStore.clear()
            externalEvidence.close()
            val saved: Path? = bridge.stop()
            snapshot = snapshot.copy(recording = false, message = "Recording saved: ${saved?.fileName}")
        }
        stopForegroundCompat()
        stopSelf()
    }

    override fun onDestroy() {
        if (::bridge.isInitialized) bridge.close()
        if (::externalEvidence.isInitialized) externalEvidence.close()
        if (::guidanceAudio.isInitialized) guidanceAudio.close()
        super.onDestroy()
    }

    private fun startAsForeground(text: String) {
        val notification = notification(text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else startForeground(NOTIFICATION_ID, notification)
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text))
    }

    private fun notification(text: String): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_menu_mylocation)
        .setContentTitle("Offline navigation")
        .setContentText(text)
        .setOngoing(true)
        .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Navigation recording", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun recoverySnapshot(decision: ProcessRestartRecoveryDecision): NavigationServiceSnapshot = when (decision.disposition) {
        ProcessRestartRecoveryDisposition.NONE -> NavigationServiceSnapshot(false, "OBD not selected", "Ready")
        ProcessRestartRecoveryDisposition.RECOVER_INTERRUPTED_SESSION -> NavigationServiceSnapshot(
            false, "OBD not selected",
            "Previous session ${decision.sessionFileName} ended unexpectedly. Recording and dead reckoning remain stopped; start a new session with fresh sensors."
        )
        ProcessRestartRecoveryDisposition.DISCARD_STALE_SESSION -> NavigationServiceSnapshot(
            false, "OBD not selected",
            "Stale interrupted session ${decision.sessionFileName} was not resumed. Start a new session with fresh sensors."
        )
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE) else @Suppress("DEPRECATION") stopForeground(true)
    }

    companion object {
        private const val ACTION_START = "com.example.navigation.app.action.START_NAVIGATION"
        private const val ACTION_STOP = "com.example.navigation.app.action.STOP_NAVIGATION"
        private const val EXTRA_OBD_ADDRESS = "obdAddress"
        private const val EXTRA_PEDESTRIAN_METADATA = "pedestrianMetadata"
        private const val CHANNEL_ID = "navigation_recording"
        private const val NOTIFICATION_ID = 98

        fun start(context: Context, obdAddress: String?, metadata: PedestrianSessionMetadata? = null) {
            ContextCompat.startForegroundService(context, Intent(context, NavigationForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_OBD_ADDRESS, obdAddress)
                putExtra(EXTRA_PEDESTRIAN_METADATA, metadata)
            })
        }
        fun stop(context: Context) = context.startService(Intent(context, NavigationForegroundService::class.java).setAction(ACTION_STOP))

        @Suppress("DEPRECATION")
        private fun Intent.serializableMetadata(): PedestrianSessionMetadata? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) getSerializableExtra(EXTRA_PEDESTRIAN_METADATA, PedestrianSessionMetadata::class.java)
            else getSerializableExtra(EXTRA_PEDESTRIAN_METADATA) as? PedestrianSessionMetadata
    }
}
