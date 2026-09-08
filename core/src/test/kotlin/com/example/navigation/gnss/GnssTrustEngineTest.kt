package com.example.navigation.gnss

import com.example.navigation.core.MonotonicTimestamp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GnssTrustEngineTest {
    @Test fun `weighted score retains inspectable contributions`() {
        val engine = GnssTrustEngine(GnssTrustEngineConfig(weights = mapOf(
            GnssTrustEvidenceType.POSITION_JUMP to 3.0,
            GnssTrustEvidenceType.SPEED_CONSISTENCY to 1.0
        )))
        val inputs = listOf(
            available(GnssTrustEvidenceType.POSITION_JUMP, 0.2, "large innovation"),
            available(GnssTrustEvidenceType.SPEED_CONSISTENCY, 1.0, "speeds agree")
        )

        val result = engine.evaluate(inputs)

        assertEquals(0.4, result.value, 1e-9)
        assertEquals(inputs, result.evidence)
        assertEquals(0.6, result.contributions[0].weightedScore!!, 1e-9)
        assertEquals(0.75, result.contributions[0].normalizedWeight!!, 1e-9)
        assertEquals(0.15, result.contributions[0].scoreContribution!!, 1e-9)
        assertEquals(result.value, result.contributions.sumOf { it.scoreContribution ?: 0.0 }, 1e-9)
    }

    @Test fun `unavailable evidence remains visible without hidden penalty`() {
        val unavailable = GnssTrustEvidence(
            GnssTrustEvidenceType.AGC,
            GnssTrustEvidenceAvailability.UNAVAILABLE,
            null,
            "AGC is not reported by this device",
            MonotonicTimestamp(10)
        )
        val result = GnssTrustEngine(GnssTrustEngineConfig(noAvailableEvidenceScore = 0.5)).evaluate(listOf(unavailable))

        assertEquals(0.5, result.value)
        assertEquals(unavailable, result.evidence.single())
        assertEquals(null, result.contributions.single().weightedScore)
        assertEquals(null, result.contributions.single().scoreContribution)
    }

    @Test fun `evidence rejects scores outside trust range`() {
        assertFailsWith<IllegalArgumentException> {
            available(GnssTrustEvidenceType.OTHER, 1.1, "invalid")
        }
    }

    private fun available(type: GnssTrustEvidenceType, score: Double, detail: String) = GnssTrustEvidence(
        type, GnssTrustEvidenceAvailability.AVAILABLE, score, detail, MonotonicTimestamp(10)
    )
}
