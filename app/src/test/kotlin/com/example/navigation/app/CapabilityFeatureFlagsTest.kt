package com.example.navigation.app

import org.junit.Assert.assertEquals
import org.junit.Test

class CapabilityFeatureFlagsTest {
    @Test fun `missing AGC disables only its trust evidence with an explicit reason`() {
        val flags = CapabilityFeatureFlags.from(report(agc = false, carrierFrequencies = true))
        assertEquals(false, flags.isEnabled(NavigationFeature.AGC_TRUST_EVIDENCE))
        assertEquals("AGC trust evidence unavailable", flags.status(NavigationFeature.AGC_TRUST_EVIDENCE).unavailableReason)
        assertEquals(true, flags.isEnabled(NavigationFeature.CARRIER_FREQUENCY_TRUST_EVIDENCE))
        assertEquals(true, flags.isEnabled(NavigationFeature.RAW_GNSS_EVIDENCE))
    }

    @Test fun `missing hardware is represented as unavailable rather than an error`() {
        val flags = CapabilityFeatureFlags.from(report(gyroscope = false, accelerometer = false, barometer = false))
        assertEquals("gyroscope unavailable", flags.status(NavigationFeature.GYRO_HEADING).unavailableReason)
        assertEquals("accelerometer unavailable", flags.status(NavigationFeature.STEP_DETECTION).unavailableReason)
        assertEquals("barometer unavailable", flags.status(NavigationFeature.BAROMETER_RELATIVE_ALTITUDE).unavailableReason)
    }

    private fun report(
        gyroscope: Boolean = true,
        accelerometer: Boolean = true,
        barometer: Boolean = true,
        agc: Boolean = true,
        carrierFrequencies: Boolean = true
    ) = SensorCapabilityReport(gyroscope, accelerometer, true, true, barometer, true, agc, carrierFrequencies, 35, "test")
}
