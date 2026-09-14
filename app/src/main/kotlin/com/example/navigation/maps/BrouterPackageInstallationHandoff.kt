package com.example.navigation.maps

import android.content.Context
import com.example.navigation.routing.BrouterPackageInstallResult
import java.util.concurrent.Executor

/**
 * Explicit state handoff from a verified regional-map activation to BRouter installation.
 *
 * MapLibre callbacks only enqueue this capability. The network and filesystem work runs on the
 * supplied background executor, and its independently reported state never changes tile status.
 */
enum class BrouterPackageInstallationPhase { IDLE, AVAILABLE, QUEUED, INSTALLING, READY, UNAVAILABLE }

data class BrouterPackageInstallationState(
    val mapPackageId: String? = null,
    val phase: BrouterPackageInstallationPhase = BrouterPackageInstallationPhase.IDLE,
    val reason: String? = null
)

interface BrouterPackageInstallationStateStore {
    fun save(state: BrouterPackageInstallationState)
    fun load(): BrouterPackageInstallationState?
}

class PreferencesBrouterPackageInstallationStateStore(context: Context) : BrouterPackageInstallationStateStore {
    private val preferences = context.applicationContext.getSharedPreferences("brouter_package_installation_state", Context.MODE_PRIVATE)

    override fun save(state: BrouterPackageInstallationState) {
        preferences.edit().putString("mapPackageId", state.mapPackageId).putString("phase", state.phase.name)
            .putString("reason", state.reason).apply()
    }

    override fun load(): BrouterPackageInstallationState? = preferences.getString("mapPackageId", null)?.let { packageId ->
        runCatching {
            BrouterPackageInstallationState(
                packageId,
                BrouterPackageInstallationPhase.valueOf(requireNotNull(preferences.getString("phase", null))),
                preferences.getString("reason", null)
            )
        }.getOrNull()
    }
}

private object NoopBrouterPackageInstallationStateStore : BrouterPackageInstallationStateStore {
    override fun save(state: BrouterPackageInstallationState) = Unit
    override fun load(): BrouterPackageInstallationState? = null
}

class BrouterPackageInstallationHandoff(
    private val backgroundExecutor: Executor,
    private val activeMapPackage: () -> RegionalMapPackage?,
    private val installForActiveMap: () -> BrouterPackageInstallResult,
    private val onStateChanged: (BrouterPackageInstallationState) -> Unit = {},
    private val store: BrouterPackageInstallationStateStore = NoopBrouterPackageInstallationStateStore
) {
    var state = BrouterPackageInstallationState()
        private set

    /**
     * Records only that a matching package can be requested for the newly activated map.
     * Map activation must not silently begin a network download.
     */
    fun observeActivatedMap(activated: RegionalMapPackage) {
        val active = activeMapPackage()
        if (!matches(active, activated)) {
            update(BrouterPackageInstallationState(activated.id, BrouterPackageInstallationPhase.UNAVAILABLE, "Activated map is no longer active"))
            return
        }
        update(BrouterPackageInstallationState(activated.id, BrouterPackageInstallationPhase.AVAILABLE, "BRouter package is available for explicit installation"))
    }

    /** Enqueue installation only from a separate explicit user request. */
    fun requestInstallationForActiveMap() {
        val activated = activeMapPackage()
        if (activated == null) {
            update(BrouterPackageInstallationState(phase = BrouterPackageInstallationPhase.UNAVAILABLE, reason = "No active map package is available"))
            return
        }
        update(BrouterPackageInstallationState(activated.id, BrouterPackageInstallationPhase.QUEUED))
        backgroundExecutor.execute {
            val current = activeMapPackage()
            if (!matches(current, activated)) {
                update(BrouterPackageInstallationState(activated.id, BrouterPackageInstallationPhase.UNAVAILABLE, "Activated map is no longer active"))
                return@execute
            }
            update(BrouterPackageInstallationState(activated.id, BrouterPackageInstallationPhase.INSTALLING))
            when (val result = installForActiveMap()) {
                is BrouterPackageInstallResult.Activated -> update(BrouterPackageInstallationState(activated.id, BrouterPackageInstallationPhase.READY))
                is BrouterPackageInstallResult.Unavailable -> update(BrouterPackageInstallationState(activated.id, BrouterPackageInstallationPhase.UNAVAILABLE, result.reason))
            }
        }
    }

    /** Interrupted background work is never reported as installed when the process returns. */
    fun recoverInterruptedWork() {
        val saved = store.load() ?: return
        if (saved.phase in setOf(BrouterPackageInstallationPhase.QUEUED, BrouterPackageInstallationPhase.INSTALLING)) {
            update(saved.copy(phase = BrouterPackageInstallationPhase.UNAVAILABLE, reason = "BRouter installation interrupted"))
        } else {
            state = saved
            onStateChanged(saved)
        }
    }

    private fun matches(active: RegionalMapPackage?, activated: RegionalMapPackage) =
        active?.id == activated.id && active.mapVersion == activated.mapVersion

    private fun update(next: BrouterPackageInstallationState) {
        state = next
        store.save(next)
        onStateChanged(next)
    }
}
