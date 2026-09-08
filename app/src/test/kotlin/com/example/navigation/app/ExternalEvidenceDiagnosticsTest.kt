package com.example.navigation.app

import com.example.navigation.core.MonotonicTimestamp
import com.example.navigation.gnss.*
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalEvidenceDiagnosticsTest {
    @Test fun `every feed state is explicit and attribution is safety limited`() {
        ExternalEvidenceState.entries.forEach { state ->
            val label = externalEvidenceStatusLabel(ExternalEvidenceStatus(
                MonotonicTimestamp(1), ExternalEvidenceSource.AIR_ALERT, state, "NEPTUN"
            ))
            assertTrue(label.contains(state.name))
            assertTrue(label.contains("NEPTUN"))
        }
        assertTrue(externalEvidenceStatusLabel(null).contains("Unavailable"))
        assertTrue(NEPTUN_ATTRIBUTION.contains("not an official warning system"))
        assertTrue(NEPTUN_ATTRIBUTION.contains("https://neptun.in.ua/"))
    }
}
