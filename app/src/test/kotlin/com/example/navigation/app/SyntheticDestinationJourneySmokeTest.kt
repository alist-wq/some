package com.example.navigation.app

import com.example.navigation.routing.*
import com.example.navigation.navigation.NavigationMode
import org.junit.Assert.*
import org.junit.Test

class SyntheticDestinationJourneySmokeTest {
    @Test fun `synthetic destination journey reaches preview active guidance and arrival`() {
        val fixture = SyntheticDestinationJourneyFixture()
        val searched = DestinationSearchSelectionReducer.query(DestinationSearchSelectionReducer.open(DestinationSearchSelectionState()), fixture.index, "test museum")
        val selected = DestinationSearchSelectionReducer.select(searched, fixture.destinationRecord)
        val proposed = requireNotNull(selected.proposedDestination)
        val planning = RoutePlanningUseCase().plan(
            fixture.origin,
            ConfirmedDestination(proposed.stableId, proposed.label, proposed.coordinate, proposed.sourcePackage),
            NavigationMode.VEHICLE,
            fixture.verifiedGraph
        )
        val preview = RoutePreviewPresentationFactory.create(planning)
        assertTrue(preview.canStartNavigation)
        assertEquals(3, preview.routeGeometry.size)

        val lifecycle = ActiveRouteLifecycle()
        lifecycle.start(ConfirmedRoutePlan("synthetic", requireNotNull(planning.candidate)))
        fixture.observations().forEach(lifecycle::observe)
        val guidance = ActiveRouteGuidancePresentationFactory.create(lifecycle.state)
        assertEquals(ActiveRoutePhase.ARRIVED, lifecycle.state.phase)
        assertEquals("ARRIVED", guidance.label)
        assertTrue(guidance.canChooseNewDestination)
    }
}
