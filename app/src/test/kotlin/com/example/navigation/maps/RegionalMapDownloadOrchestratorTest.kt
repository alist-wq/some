package com.example.navigation.maps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionalMapDownloadOrchestratorTest {
    private val packageInfo = RegionalMapPackage(
        "ua", "Ukraine", RegionalMapBounds(44.0, 22.0, 53.0, 41.0), "asset://style.json",
        1.0, 14.0, 1_000L, "v1", "g1", "a".repeat(64)
    )

    @Test fun `download progresses verifies activates and remains offline ready`() {
        val backend = FakeBackend()
        val store = MemoryStore()
        var activated = false
        val orchestrator = RegionalMapDownloadOrchestrator(backend, verifier("v1", "a".repeat(64)), RegionalMapActivator { _, _ -> activated = true }, store)
        assertEquals(RegionalMapDownloadPhase.PREPARING, orchestrator.prepare(packageInfo).phase)
        assertEquals(RegionalMapDownloadPhase.DOWNLOADING, orchestrator.start().phase)
        backend.progress(RegionalMapBackendProgress(7L, 500L, 1_000L, false))
        assertEquals(500L, orchestrator.state.downloadedBytes)
        backend.progress(RegionalMapBackendProgress(7L, 1_000L, 1_000L, true))
        assertEquals(RegionalMapDownloadPhase.READY, orchestrator.state.phase)
        assertTrue(activated)
        assertFalse(orchestrator.state.retryable)
    }

    @Test fun `pause cancel failure and retry remain explicit`() {
        val backend = FakeBackend()
        val orchestrator = RegionalMapDownloadOrchestrator(backend, verifier("v1", "a".repeat(64)), RegionalMapActivator { _, _ -> }, MemoryStore())
        orchestrator.prepare(packageInfo); orchestrator.start()
        backend.progress(RegionalMapBackendProgress(3L, 10L, null, false))
        assertEquals(RegionalMapDownloadPhase.PAUSED, orchestrator.pause().phase)
        assertEquals(RegionalMapDownloadPhase.DOWNLOADING, orchestrator.retry().phase)
        backend.error("network unavailable")
        assertEquals(RegionalMapDownloadPhase.FAILED, orchestrator.state.phase)
        assertTrue(orchestrator.state.retryable)
        assertEquals("network unavailable", orchestrator.state.failureReason)
        assertEquals(RegionalMapDownloadPhase.DOWNLOADING, orchestrator.retry().phase)
        assertEquals(RegionalMapDownloadPhase.CANCELLED, orchestrator.cancel().phase)
    }

    @Test fun `interrupted download recovers paused and never false ready`() {
        val store = MemoryStore(RegionalMapDownloadState("ua", RegionalMapDownloadPhase.DOWNLOADING, 4L, 20L, 100L))
        val orchestrator = RegionalMapDownloadOrchestrator(FakeBackend(), verifier("v1", "a".repeat(64)), RegionalMapActivator { _, _ -> }, store)
        val recovered = orchestrator.recover(packageInfo)
        assertEquals(RegionalMapDownloadPhase.PAUSED, recovered.phase)
        assertTrue(recovered.retryable)
        assertEquals("Download interrupted", recovered.failureReason)
    }

    @Test fun `checksum and version failures are corrupt or stale and never activate`() {
        var activations = 0
        fun run(version: String, hash: String): RegionalMapDownloadPhase {
            val backend = FakeBackend()
            val orchestrator = RegionalMapDownloadOrchestrator(backend, verifier(version, hash), RegionalMapActivator { _, _ -> activations++ }, MemoryStore())
            orchestrator.prepare(packageInfo); orchestrator.start()
            backend.progress(RegionalMapBackendProgress(9L, 100L, 100L, true))
            return orchestrator.state.phase
        }
        assertEquals(RegionalMapDownloadPhase.CORRUPT, run("v1", "b".repeat(64)))
        assertEquals(RegionalMapDownloadPhase.STALE, run("old", "a".repeat(64)))
        assertEquals(0, activations)
    }

    private fun verifier(version: String, hash: String) = RegionalMapPackageVerifier { _, _, bytes ->
        InstalledRegionalMapEvidence(version, bytes, hash, complete = true)
    }

    private class FakeBackend : RegionalMapDownloadBackend {
        private var onProgress: ((RegionalMapBackendProgress) -> Unit)? = null
        private var onError: ((String) -> Unit)? = null
        override fun start(packageInfo: RegionalMapPackage, onProgress: (RegionalMapBackendProgress) -> Unit, onError: (String) -> Unit) {
            this.onProgress = onProgress; this.onError = onError
        }
        override fun pause(regionId: Long, onComplete: () -> Unit, onError: (String) -> Unit) = onComplete()
        override fun resume(packageInfo: RegionalMapPackage, regionId: Long, onProgress: (RegionalMapBackendProgress) -> Unit, onComplete: () -> Unit, onError: (String) -> Unit) {
            this.onProgress = onProgress; this.onError = onError; onComplete()
        }
        fun progress(value: RegionalMapBackendProgress) = requireNotNull(onProgress)(value)
        fun error(value: String) = requireNotNull(onError)(value)
    }

    private class MemoryStore(initial: RegionalMapDownloadState? = null) : RegionalMapDownloadStateStore {
        private var value = initial
        override fun save(state: RegionalMapDownloadState) { value = state }
        override fun load(): RegionalMapDownloadState? = value
        override fun clear() { value = null }
    }
}
