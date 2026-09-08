package com.example.navigation.gnss

import org.junit.Assert.*
import org.junit.Test

class GnssCollectorLifecycleAndMappingTest {
    @Test fun `registration success tears down exactly once`() {
        val lifecycle = GnssCollectorLifecycle()
        var unregisters = 0
        assertEquals(EvidenceCapability.AVAILABLE, lifecycle.start { true })
        assertTrue(lifecycle.isActive())
        lifecycle.stop { unregisters++ }
        lifecycle.stop { unregisters++ }
        assertEquals(1, unregisters)
        assertFalse(lifecycle.isActive())
    }

    @Test fun `unsupported and permission denied remain explicit and do not unregister`() {
        val unsupported = GnssCollectorLifecycle()
        assertEquals(EvidenceCapability.UNSUPPORTED, unsupported.start { false })
        unsupported.stop { fail("unsupported callback was never registered") }
        val denied = GnssCollectorLifecycle()
        assertEquals(EvidenceCapability.PERMISSION_DENIED, denied.start { throw SecurityException("denied") })
        assertFalse(denied.isActive())
    }

    @Test fun `GPS and Galileo status preserve all available and unavailable fields`() {
        val gps = GnssStatusFields(GnssConstellation.GPS, 7, true, 120f, 45f, 36f, 1575.42e6f, true, true).toDomain(2)
        val galileo = GnssStatusFields(GnssConstellation.GALILEO, 12, false, 20f, 10f, 18f, null, false, false).toDomain(2)
        assertEquals(7, gps.svid)
        assertTrue(gps.usedInFix)
        assertNotNull(gps.carrierFrequencyHz)
        assertEquals(GnssConstellation.GALILEO, galileo.constellation)
        assertNull(galileo.carrierFrequencyHz)
        assertFalse(galileo.usedInFix)
    }

    @Test fun `raw optional fields remain absent rather than fabricated`() {
        val raw = RawGnssFields(22, GnssConstellation.GLONASS, null, 27f, null, -2.0, 0.2,
            null, null, 5, null, GnssMultipathIndicator.UNKNOWN, 10, null).toDomain()
        assertEquals(22, raw.svid)
        assertNull(raw.carrierFrequencyHz)
        assertNull(raw.automaticGainControlDb)
        assertNull(raw.receivedSvTimeUncertaintyNanos)
    }
}
