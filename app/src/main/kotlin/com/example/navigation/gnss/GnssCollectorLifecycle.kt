package com.example.navigation.gnss

import com.example.navigation.gnss.EvidenceCapability

/** Testable registration state shared by Android GNSS collectors. */
internal class GnssCollectorLifecycle {
    private var active = false
    private var capability = EvidenceCapability.UNAVAILABLE

    fun start(register: () -> Boolean): EvidenceCapability {
        check(!active) { "GNSS collector is already started" }
        capability = try {
            if (register()) {
                active = true
                EvidenceCapability.AVAILABLE
            } else EvidenceCapability.UNSUPPORTED
        } catch (_: SecurityException) {
            EvidenceCapability.PERMISSION_DENIED
        }
        return capability
    }

    fun stop(unregister: () -> Unit) {
        if (!active) return
        unregister()
        active = false
    }

    fun capability(): EvidenceCapability = capability
    fun isActive(): Boolean = active
}
