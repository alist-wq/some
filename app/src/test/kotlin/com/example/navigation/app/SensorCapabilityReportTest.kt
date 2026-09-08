package com.example.navigation.app

import org.junit.Assert.assertEquals
import org.junit.Test

class SensorCapabilityReportTest {
    @Test fun `report preserves every capability and device identity`() {
        val report = SensorCapabilityReport(true, false, true, false, true, true, false, true, 35, "Acme Model")
        assertEquals(true, report.gyroscope)
        assertEquals(false, report.accelerometer)
        assertEquals(true, report.gameRotationVector)
        assertEquals(false, report.magnetometer)
        assertEquals(true, report.barometer)
        assertEquals(true, report.rawGnss)
        assertEquals(false, report.agc)
        assertEquals(true, report.carrierFrequencies)
        assertEquals(35, report.androidVersion)
        assertEquals("Acme Model", report.deviceModel)
    }
}
