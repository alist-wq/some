package com.example.navigation.app

/** Algorithms/evidence that may be unavailable because a device does not expose required hardware. */
enum class NavigationFeature {
    GYRO_HEADING,
    GAME_ROTATION_HEADING,
    MAGNETOMETER_SUPPORT,
    STEP_DETECTION,
    BAROMETER_RELATIVE_ALTITUDE,
    RAW_GNSS_EVIDENCE,
    AGC_TRUST_EVIDENCE,
    CARRIER_FREQUENCY_TRUST_EVIDENCE
}

data class FeatureAvailability(val enabled: Boolean, val unavailableReason: String? = null) {
    init {
        require((enabled && unavailableReason == null) || (!enabled && !unavailableReason.isNullOrBlank()))
    }
}

/** Maps a startup capability report to explicit, non-anomalous feature availability. */
class CapabilityFeatureFlags private constructor(private val availability: Map<NavigationFeature, FeatureAvailability>) {
    fun status(feature: NavigationFeature): FeatureAvailability = availability.getValue(feature)
    fun isEnabled(feature: NavigationFeature): Boolean = status(feature).enabled

    companion object {
        fun from(report: SensorCapabilityReport): CapabilityFeatureFlags = CapabilityFeatureFlags(
            mapOf(
                NavigationFeature.GYRO_HEADING to report.gyroscope.availability("gyroscope unavailable"),
                NavigationFeature.GAME_ROTATION_HEADING to report.gameRotationVector.availability("game rotation vector unavailable"),
                NavigationFeature.MAGNETOMETER_SUPPORT to report.magnetometer.availability("magnetometer unavailable"),
                NavigationFeature.STEP_DETECTION to report.accelerometer.availability("accelerometer unavailable"),
                NavigationFeature.BAROMETER_RELATIVE_ALTITUDE to report.barometer.availability("barometer unavailable"),
                NavigationFeature.RAW_GNSS_EVIDENCE to report.rawGnss.availability("raw GNSS unavailable"),
                NavigationFeature.AGC_TRUST_EVIDENCE to report.agc.availability("AGC trust evidence unavailable"),
                NavigationFeature.CARRIER_FREQUENCY_TRUST_EVIDENCE to report.carrierFrequencies.availability("carrier-frequency trust evidence unavailable")
            )
        )

        private fun Boolean.availability(reason: String) = if (this) FeatureAvailability(true) else FeatureAvailability(false, reason)
    }
}
