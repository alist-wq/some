package com.example.navigation.app

import com.example.navigation.maps.BrouterPackageInstallationPhase
import com.example.navigation.maps.BrouterPackageInstallationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrouterInstallationPresentationTest {
    @Test fun `available package requires an explicit installation tap`() {
        val presentation = BrouterInstallationPresentationFactory.create(
            BrouterPackageInstallationState("ua-kharkiv-pedestrian", BrouterPackageInstallationPhase.AVAILABLE)
        )
        assertTrue(presentation.visible)
        assertTrue(presentation.canRequestInstallation)
        assertEquals("INSTALL PEDESTRIAN ROUTING", presentation.actionLabel)
    }

    @Test fun `ready is not presented as an installation action`() {
        val presentation = BrouterInstallationPresentationFactory.create(
            BrouterPackageInstallationState("ua-kharkiv-pedestrian", BrouterPackageInstallationPhase.READY)
        )
        assertFalse(presentation.canRequestInstallation)
        assertEquals("PEDESTRIAN ROUTING READY", presentation.status)
    }

    @Test fun `unavailable preserves the verification reason`() {
        val presentation = BrouterInstallationPresentationFactory.create(
            BrouterPackageInstallationState("ua-kharkiv-pedestrian", BrouterPackageInstallationPhase.UNAVAILABLE, "Package signer is not trusted")
        )
        assertEquals("Package signer is not trusted", presentation.detail)
    }
}
