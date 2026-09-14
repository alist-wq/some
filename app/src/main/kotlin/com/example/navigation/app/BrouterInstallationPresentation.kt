package com.example.navigation.app

import com.example.navigation.maps.BrouterPackageInstallationPhase
import com.example.navigation.maps.BrouterPackageInstallationState

data class BrouterInstallationPresentation(
    val visible: Boolean,
    val status: String,
    val detail: String,
    val actionLabel: String?,
    val canRequestInstallation: Boolean
)

/** UI wording for the existing explicit-install handoff; it never treats availability as installed. */
object BrouterInstallationPresentationFactory {
    fun create(state: BrouterPackageInstallationState): BrouterInstallationPresentation = when (state.phase) {
        BrouterPackageInstallationPhase.IDLE -> BrouterInstallationPresentation(false, "", "", null, false)
        BrouterPackageInstallationPhase.AVAILABLE -> BrouterInstallationPresentation(true, "PEDESTRIAN ROUTING AVAILABLE",
            state.reason ?: "Download the verified package to enable pedestrian route planning.", "INSTALL PEDESTRIAN ROUTING", true)
        BrouterPackageInstallationPhase.QUEUED -> BrouterInstallationPresentation(true, "PEDESTRIAN ROUTING QUEUED",
            "The verified package installation is queued.", null, false)
        BrouterPackageInstallationPhase.INSTALLING -> BrouterInstallationPresentation(true, "INSTALLING PEDESTRIAN ROUTING",
            "Verifying and installing the signed package.", null, false)
        BrouterPackageInstallationPhase.READY -> BrouterInstallationPresentation(true, "PEDESTRIAN ROUTING READY",
            "The verified package is installed for this active map.", null, false)
        BrouterPackageInstallationPhase.UNAVAILABLE -> BrouterInstallationPresentation(true, "PEDESTRIAN ROUTING UNAVAILABLE",
            state.reason ?: "The verified package is unavailable for this active map.", null, false)
    }
}
