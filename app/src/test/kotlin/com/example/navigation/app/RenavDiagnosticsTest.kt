package com.example.navigation.app

import org.junit.Assert.assertTrue
import org.junit.Test

class RenavDiagnosticsTest {
    @Test fun `diagnostic line preserves monotonic and wall clock metadata`() {
        val rendered = DiagnosticLine(123L, 0L, "screen_navigation", "from=MAIN to=SETTINGS").render()
        assertTrue(rendered.contains("elapsedNanos=123"))
        assertTrue(rendered.contains("wallClock=1970-01-01T00:00:00.000Z"))
        assertTrue(rendered.contains("event=screen_navigation detail=from=MAIN to=SETTINGS"))
    }

    @Test fun `diagnostic names are unique by timestamp`() {
        assertTrue(DiagnosticFileNamer.forWallClock(1L).endsWith(".txt"))
        assertTrue(DiagnosticFileNamer.forWallClock(1L) != DiagnosticFileNamer.forWallClock(2L))
    }
}
