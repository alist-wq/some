package com.example.navigation.gnss

import android.content.Context
import android.location.LocationManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.navigation.core.MonotonicTimestamp
import com.example.navigation.replay.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidGnssCollectorLifecycleSmokeTest {
    @Test fun rawRegistrationAndIdempotentTeardownReportCapability() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val collector = AndroidRawGnssCollector(manager) { }
        collector.start()
        assertTrue(collector.capability() in setOf(
            EvidenceCapability.AVAILABLE, EvidenceCapability.UNSUPPORTED, EvidenceCapability.PERMISSION_DENIED
        ))
        collector.stop()
        collector.stop()
    }

    @Test fun unavailableRawCapabilityIsRecordedLocally() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = context.cacheDir.toPath().resolve("gnss-capability-smoke")
        var now = 1L
        val controller = RecordingSessionController(directory, RecordingSensorAvailability(true, false, false, false, false, false)) { now++ }
        val path = controller.start().outputPath!!
        controller.append(GnssCollectorCapability(MonotonicTimestamp(2), GnssCollectorSource.RAW_MEASUREMENTS, EvidenceCapability.UNSUPPORTED))
        controller.stop()
        val records = SensorSessionReader(path).use { it.records().toList() }
        val capability = records.filterIsInstance<GnssCollectorCapabilitySessionRecord>().single().value
        assertEquals(GnssCollectorSource.RAW_MEASUREMENTS, capability.source)
        assertEquals(EvidenceCapability.UNSUPPORTED, capability.capability)
        val report = GnssTrustSessionReportWriter().write(path)
        assertTrue(java.nio.file.Files.isRegularFile(report))
        assertTrue(java.nio.file.Files.newBufferedReader(report).use { it.readText() }.contains("local-only"))
    }
}
