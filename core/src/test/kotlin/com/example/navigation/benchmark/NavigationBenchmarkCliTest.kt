package com.example.navigation.benchmark

import com.example.navigation.core.MonotonicTimestamp
import com.example.navigation.navigation.NavigationMode
import com.example.navigation.replay.NavigationModeSessionRecord
import com.example.navigation.replay.SensorSessionHeader
import com.example.navigation.replay.SensorSessionWriter
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NavigationBenchmarkCliTest {
    @Test fun `parses three benchmark inputs`() {
        val command = NavigationBenchmarkCli.parse(listOf("session.navsession", "scenario.yaml", "config.json"))
        assertEquals("session.navsession", command.session.toString())
    }

    @Test fun `requires all benchmark inputs`() {
        assertFailsWith<IllegalArgumentException> { NavigationBenchmarkCli.parse(listOf("session")) }
    }

    @Test fun `renders human and machine readable metrics`() {
        val metrics = NavigationBenchmarkMetrics(95.0, 10.0, 20.0, 0, 3, 0, 4, 5, emptyList())
        assertContains(NavigationBenchmarkCli.renderHumanReadable(metrics), "Correct route: 95.0%")
        assertContains(NavigationBenchmarkCli.renderMachineReadable(metrics), "\"wrongBranchCount\":0")
    }

    @Test fun `executes a complete matching session and renders reports`() {
        val session = Files.createTempFile("benchmark", ".navsession")
        val scenario = Files.createTempFile("benchmark", ".yaml")
        val algorithm = Files.createTempFile("benchmark", ".yaml")
        try {
            SensorSessionWriter(session, SensorSessionHeader(sessionId = "session-1", startedAt = MonotonicTimestamp(1))).use { writer ->
                writer.append(NavigationModeSessionRecord(MonotonicTimestamp(2), NavigationMode.VEHICLE))
                writer.close(MonotonicTimestamp(3))
            }
            Files.writeString(scenario, "session: session-1\nroute: route-1\ncorruption:\n  type: loss\n  start: 0\n  duration: 1\n")
            Files.writeString(algorithm, "algorithm: replay_passthrough\n")
            val result = NavigationBenchmarkCli.execute(NavigationBenchmarkCommand(session, scenario, algorithm))
            assertEquals(1, result.replayedRecordCount)
            assertContains(NavigationBenchmarkCli.renderHumanReadable(result), "Session: session-1")
            assertContains(NavigationBenchmarkCli.renderMachineReadable(result), "\"replayedRecordCount\":1")
        } finally {
            Files.deleteIfExists(session)
            Files.deleteIfExists(scenario)
            Files.deleteIfExists(algorithm)
        }
    }
}
