package com.example.navigation.benchmark

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith

class GnssTrustCalibrationDatasetTest {
    @Test fun `incomplete real dataset cannot be presented as calibrated`() {
        val recording = GnssCalibrationRecording("sha256:test", GnssCalibrationScenario.NORMAL, "device", null, 35, true, true, false, false)
        val issues = GnssCalibrationDataset(listOf(recording)).readinessIssues()
        assertContains(issues, "multiple recordings required")
        assertContains(issues, "missing AUTHORIZED_INJECTION recording")
        assertContains(issues, "held-out recording required")
    }

    @Test fun `consent and anonymization are mandatory`() {
        assertFailsWith<IllegalArgumentException> {
            GnssCalibrationRecording("x", GnssCalibrationScenario.NORMAL, "device", null, 35, false, true, false, false)
        }
    }
}
