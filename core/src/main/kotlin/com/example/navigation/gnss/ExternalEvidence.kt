package com.example.navigation.gnss

import com.example.navigation.core.MonotonicTimestamp
import java.io.Serializable

enum class ExternalEvidenceSource { SPACECRAFT_REFERENCE, AIR_ALERT }
enum class ExternalEvidenceState { AVAILABLE, STALE, DISCONNECTED, UNAVAILABLE, ERROR }

/** Coverage/status evidence. It contains no provider wall-clock event history. */
data class ExternalEvidenceStatus(
    val timestamp: MonotonicTimestamp,
    val source: ExternalEvidenceSource,
    val state: ExternalEvidenceState,
    val provider: String,
    val ageSeconds: Double? = null,
    val detail: String? = null
) : Serializable {
    init {
        require(provider.isNotBlank())
        require(ageSeconds == null || ageSeconds >= 0.0)
    }
}
