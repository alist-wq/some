package com.example.navigation.maps

import com.example.navigation.routing.BrouterPackageInstallResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executor

class BrouterPackageInstallationHandoffTest {
    private val directExecutor = Executor { command -> command.run() }
    private val map = RegionalMapPackage("ua-kharkiv-pedestrian", "Kharkiv", RegionalMapBounds(49.8, 35.9, 50.2, 36.5), "asset://style", 8.0, 14.0, 1L, "openfreemap-2026-09", "unavailable", "a".repeat(64))

    @Test fun `verified map activation exposes an available package without installing it`() {
        val states = mutableListOf<BrouterPackageInstallationState>()
        var installs = 0
        val handoff = BrouterPackageInstallationHandoff(directExecutor, { map }, {
            installs++
            BrouterPackageInstallResult.Unavailable("fixture downloader disabled")
        }, states::add)

        handoff.observeActivatedMap(map)

        assertEquals(0, installs)
        assertEquals(BrouterPackageInstallationPhase.AVAILABLE, handoff.state.phase)
        assertEquals(listOf(BrouterPackageInstallationPhase.AVAILABLE), states.map { it.phase })
    }

    @Test fun `explicit installation request schedules BRouter installation for the active map`() {
        val states = mutableListOf<BrouterPackageInstallationState>()
        var installs = 0
        val handoff = BrouterPackageInstallationHandoff(directExecutor, { map }, {
            installs++
            BrouterPackageInstallResult.Unavailable("fixture downloader disabled")
        }, states::add)

        handoff.requestInstallationForActiveMap()

        assertEquals(1, installs)
        assertEquals(BrouterPackageInstallationPhase.UNAVAILABLE, handoff.state.phase)
        assertEquals(listOf(BrouterPackageInstallationPhase.QUEUED, BrouterPackageInstallationPhase.INSTALLING, BrouterPackageInstallationPhase.UNAVAILABLE), states.map { it.phase })
    }

    @Test fun `inactive map never schedules BRouter installation`() {
        var installs = 0
        val handoff = BrouterPackageInstallationHandoff(directExecutor, { null }, {
            installs++
            throw AssertionError("installation must not run")
        })

        handoff.requestInstallationForActiveMap()

        assertEquals(0, installs)
        assertEquals(BrouterPackageInstallationPhase.UNAVAILABLE, handoff.state.phase)
        assertTrue(handoff.state.reason!!.contains("No active map"))
    }

    @Test fun `interrupted background installation becomes explicitly unavailable on recovery`() {
        val store = MemoryStore(BrouterPackageInstallationState(map.id, BrouterPackageInstallationPhase.INSTALLING))
        val handoff = BrouterPackageInstallationHandoff(directExecutor, { map }, { throw AssertionError("must not install during recovery") }, store = store)

        handoff.recoverInterruptedWork()

        assertEquals(BrouterPackageInstallationPhase.UNAVAILABLE, handoff.state.phase)
        assertEquals("BRouter installation interrupted", handoff.state.reason)
    }

    private class MemoryStore(private var state: BrouterPackageInstallationState?) : BrouterPackageInstallationStateStore {
        override fun save(state: BrouterPackageInstallationState) { this.state = state }
        override fun load(): BrouterPackageInstallationState? = state
    }
}
