package com.example.navigation.benchmark

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BenchmarkScenarioTest {
    @Test fun `parses documented gradual drift scenario`() {
        val scenario = BenchmarkScenarioParser.parse("""
            session: UA-KH-014
            route: route-014
            corruption:
              type: gradual_drift
              start: 270
              duration: 420
        """.trimIndent())
        assertEquals("UA-KH-014", scenario.session)
        assertEquals("route-014", scenario.route)
        assertEquals(GnssCorruptionType.GRADUAL_DRIFT, scenario.corruption.type)
        assertEquals(270, scenario.corruption.startSeconds)
        assertEquals(420, scenario.corruption.durationSeconds)
    }

    @Test fun `rejects missing fields unknown fields and invalid timing`() {
        assertFailsWith<IllegalArgumentException> { BenchmarkScenarioParser.parse("session: x\nroute: y") }
        assertFailsWith<IllegalArgumentException> { BenchmarkScenarioParser.parse("session: x\nroute: y\ncorruption:\n  type: gradual_drift\n  start: -1\n  duration: 2") }
        assertFailsWith<IllegalArgumentException> { BenchmarkScenarioParser.parse("session: x\nroute: y\nextra: z\ncorruption:\n  type: loss\n  start: 0\n  duration: 1") }
    }
}
