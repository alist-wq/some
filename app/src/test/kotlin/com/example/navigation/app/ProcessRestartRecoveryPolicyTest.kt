package com.example.navigation.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ProcessRestartRecoveryPolicyTest {
    private val policy = ProcessRestartRecoveryPolicy(maximumCheckpointAgeMillis = 1_000L)

    @Test fun `fresh interrupted session is surfaced but never resumes dead reckoning`() {
        val decision = policy.evaluate(session(9_500L), 10_000L)
        assertEquals(ProcessRestartRecoveryDisposition.RECOVER_INTERRUPTED_SESSION, decision.disposition)
        assertFalse(decision.resumeDeadReckoning)
    }

    @Test fun `stale and future checkpoints are discarded and never resume`() {
        val stale = policy.evaluate(session(1_000L), 10_000L)
        val future = policy.evaluate(session(11_000L), 10_000L)
        assertEquals(ProcessRestartRecoveryDisposition.DISCARD_STALE_SESSION, stale.disposition)
        assertEquals(ProcessRestartRecoveryDisposition.DISCARD_STALE_SESSION, future.disposition)
        assertFalse(stale.resumeDeadReckoning)
        assertFalse(future.resumeDeadReckoning)
    }

    private fun session(wallClockMillis: Long) = InterruptedNavigationSession("session.navsession", 5L, wallClockMillis)
}
