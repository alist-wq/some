package com.example.navigation.gnss

import com.example.navigation.core.MonotonicTimestamp

/** Read-only expected geometry. It never replaces receiver observations. */
fun interface SpacecraftReferenceProvider {
    fun referenceAt(timestamp: MonotonicTimestamp, constellation: GnssConstellation, svid: Int): SpacecraftReferenceEvidence?
}

object UnavailableSpacecraftReferenceProvider : SpacecraftReferenceProvider {
    override fun referenceAt(timestamp: MonotonicTimestamp, constellation: GnssConstellation, svid: Int) = null
}

data class AirAlertSourceEvent(
    val transition: AirAlertTransition,
    val provider: String,
    val sourceStatus: ContextSourceStatus
)

/** Converts provider callbacks to privacy-limited monotonic session markers. */
class AirAlertMarkerMapper(private val monotonicNow: () -> MonotonicTimestamp) {
    fun map(event: AirAlertSourceEvent) = AirAlertContextMarker(
        timestamp = monotonicNow(), transition = event.transition, provider = event.provider,
        sourceStatus = event.sourceStatus
    )
}
