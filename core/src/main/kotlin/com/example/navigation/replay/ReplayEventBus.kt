package com.example.navigation.replay

import com.example.navigation.gnss.GnssSample
import com.example.navigation.gnss.RawGnssSample
import com.example.navigation.gnss.GnssStatusSnapshot
import com.example.navigation.navigation.NavigationMode
import com.example.navigation.obd.VehicleSpeedSample
import com.example.navigation.sensors.*

/** Typed replay events; each event represents one concrete sensor/domain type. */
sealed interface ReplayEvent {
    data class Gnss(val sample: GnssSample) : ReplayEvent
    data class RawGnss(val sample: RawGnssSample) : ReplayEvent
    data class GnssStatus(val status: GnssStatusSnapshot) : ReplayEvent
    data class Gyroscope(val sample: GyroscopeSample) : ReplayEvent
    data class Accelerometer(val sample: AccelerometerSample) : ReplayEvent
    data class GameRotationVector(val sample: GameRotationVectorSample) : ReplayEvent
    data class Magnetometer(val sample: MagnetometerSample) : ReplayEvent
    data class Barometer(val sample: BarometerSample) : ReplayEvent
    data class VehicleSpeed(val sample: VehicleSpeedSample) : ReplayEvent
    data class NavigationMode(val timestamp: com.example.navigation.core.MonotonicTimestamp, val mode: com.example.navigation.navigation.NavigationMode) : ReplayEvent
    data class Marker(val marker: SessionMarker) : ReplayEvent
    data class PedestrianMetadata(val record: PedestrianMetadataSessionRecord) : ReplayEvent
    data class PedestrianPose(val record: PedestrianPoseSessionRecord) : ReplayEvent
    data class PedestrianEvent(val record: PedestrianEventSessionRecord) : ReplayEvent
    data class GnssTrust(val record: GnssTrustSessionRecord) : ReplayEvent
    data class WeightedGnssSolution(val record: WeightedGnssSolutionSessionRecord) : ReplayEvent
    data class AirAlert(val record: AirAlertSessionRecord) : ReplayEvent
    data class AndroidLocationEvidence(val record: AndroidLocationEvidenceSessionRecord) : ReplayEvent
    data class GnssCollectorCapability(val record: GnssCollectorCapabilitySessionRecord) : ReplayEvent
    data class ExternalEvidenceStatus(val record: ExternalEvidenceStatusSessionRecord) : ReplayEvent
}

/** Subscribers receive only typed callbacks for the event kinds they use. */
interface ReplayEventSubscriber {
    fun onGnss(sample: GnssSample) {}
    fun onRawGnss(sample: RawGnssSample) {}
    fun onGnssStatus(status: GnssStatusSnapshot) {}
    fun onGyroscope(sample: GyroscopeSample) {}
    fun onAccelerometer(sample: AccelerometerSample) {}
    fun onGameRotationVector(sample: GameRotationVectorSample) {}
    fun onMagnetometer(sample: MagnetometerSample) {}
    fun onBarometer(sample: BarometerSample) {}
    fun onVehicleSpeed(sample: VehicleSpeedSample) {}
    fun onNavigationMode(timestamp: com.example.navigation.core.MonotonicTimestamp, mode: NavigationMode) {}
    fun onMarker(marker: SessionMarker) {}
    fun onPedestrianMetadata(record: PedestrianMetadataSessionRecord) {}
    fun onPedestrianPose(record: PedestrianPoseSessionRecord) {}
    fun onPedestrianEvent(record: PedestrianEventSessionRecord) {}
    fun onGnssTrust(record: GnssTrustSessionRecord) {}
    fun onWeightedGnssSolution(record: WeightedGnssSolutionSessionRecord) {}
    fun onAirAlert(record: AirAlertSessionRecord) {}
    fun onAndroidLocationEvidence(record: AndroidLocationEvidenceSessionRecord) {}
    fun onGnssCollectorCapability(record: GnssCollectorCapabilitySessionRecord) {}
    fun onExternalEvidenceStatus(record: ExternalEvidenceStatusSessionRecord) {}
}

/** Synchronous typed distribution; publishing does not retain events. */
class ReplayEventBus {
    private val subscribers = LinkedHashSet<ReplayEventSubscriber>()

    fun subscribe(subscriber: ReplayEventSubscriber) { subscribers += subscriber }
    fun unsubscribe(subscriber: ReplayEventSubscriber) { subscribers -= subscriber }

    fun publish(event: ReplayEvent) {
        subscribers.toList().forEach { subscriber ->
            when (event) {
                is ReplayEvent.Gnss -> subscriber.onGnss(event.sample)
                is ReplayEvent.RawGnss -> subscriber.onRawGnss(event.sample)
                is ReplayEvent.GnssStatus -> subscriber.onGnssStatus(event.status)
                is ReplayEvent.Gyroscope -> subscriber.onGyroscope(event.sample)
                is ReplayEvent.Accelerometer -> subscriber.onAccelerometer(event.sample)
                is ReplayEvent.GameRotationVector -> subscriber.onGameRotationVector(event.sample)
                is ReplayEvent.Magnetometer -> subscriber.onMagnetometer(event.sample)
                is ReplayEvent.Barometer -> subscriber.onBarometer(event.sample)
                is ReplayEvent.VehicleSpeed -> subscriber.onVehicleSpeed(event.sample)
                is ReplayEvent.NavigationMode -> subscriber.onNavigationMode(event.timestamp, event.mode)
                is ReplayEvent.Marker -> subscriber.onMarker(event.marker)
                is ReplayEvent.PedestrianMetadata -> subscriber.onPedestrianMetadata(event.record)
                is ReplayEvent.PedestrianPose -> subscriber.onPedestrianPose(event.record)
                is ReplayEvent.PedestrianEvent -> subscriber.onPedestrianEvent(event.record)
                is ReplayEvent.GnssTrust -> subscriber.onGnssTrust(event.record)
                is ReplayEvent.WeightedGnssSolution -> subscriber.onWeightedGnssSolution(event.record)
                is ReplayEvent.AirAlert -> subscriber.onAirAlert(event.record)
                is ReplayEvent.AndroidLocationEvidence -> subscriber.onAndroidLocationEvidence(event.record)
                is ReplayEvent.GnssCollectorCapability -> subscriber.onGnssCollectorCapability(event.record)
                is ReplayEvent.ExternalEvidenceStatus -> subscriber.onExternalEvidenceStatus(event.record)
            }
        }
    }

    fun publish(record: SensorSessionRecord) = publish(record.toReplayEvent())
}

private fun SensorSessionRecord.toReplayEvent(): ReplayEvent = when (this) {
    is GnssSessionRecord -> ReplayEvent.Gnss(sample)
    is RawGnssSessionRecord -> ReplayEvent.RawGnss(sample)
    is GnssStatusSessionRecord -> ReplayEvent.GnssStatus(status)
    is GyroscopeSessionRecord -> ReplayEvent.Gyroscope(sample)
    is AccelerometerSessionRecord -> ReplayEvent.Accelerometer(sample)
    is GameRotationVectorSessionRecord -> ReplayEvent.GameRotationVector(sample)
    is MagnetometerSessionRecord -> ReplayEvent.Magnetometer(sample)
    is BarometerSessionRecord -> ReplayEvent.Barometer(sample)
    is VehicleSpeedSessionRecord -> ReplayEvent.VehicleSpeed(sample)
    is NavigationModeSessionRecord -> ReplayEvent.NavigationMode(timestamp, mode)
    is SessionMarker -> ReplayEvent.Marker(this)
    is PedestrianMetadataSessionRecord -> ReplayEvent.PedestrianMetadata(this)
    is PedestrianPoseSessionRecord -> ReplayEvent.PedestrianPose(this)
    is PedestrianEventSessionRecord -> ReplayEvent.PedestrianEvent(this)
    is GnssTrustSessionRecord -> ReplayEvent.GnssTrust(this)
    is WeightedGnssSolutionSessionRecord -> ReplayEvent.WeightedGnssSolution(this)
    is AirAlertSessionRecord -> ReplayEvent.AirAlert(this)
    is AndroidLocationEvidenceSessionRecord -> ReplayEvent.AndroidLocationEvidence(this)
    is GnssCollectorCapabilitySessionRecord -> ReplayEvent.GnssCollectorCapability(this)
    is ExternalEvidenceStatusSessionRecord -> ReplayEvent.ExternalEvidenceStatus(this)
}
