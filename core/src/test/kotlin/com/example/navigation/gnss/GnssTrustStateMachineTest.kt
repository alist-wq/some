package com.example.navigation.gnss

import kotlin.test.Test
import kotlin.test.assertEquals

class GnssTrustStateMachineTest {
    @Test fun `adverse evidence must be sustained before questionable and rejected states`() {
        val machine = GnssTrustStateMachine()

        assertEquals(GnssTrustState.TRUSTED, machine.update(0.2).state)
        assertEquals(GnssTrustState.QUESTIONABLE, machine.update(0.2).state)
        assertEquals(GnssTrustState.QUESTIONABLE, machine.update(0.2).state)
        assertEquals(GnssTrustState.QUESTIONABLE, machine.update(0.2).state)
        assertEquals(GnssTrustState.REJECTED, machine.update(0.2).state)
    }

    @Test fun `rejected state requires sustained recovery before trusted`() {
        val machine = GnssTrustStateMachine(initialState = GnssTrustState.REJECTED)

        repeat(2) { assertEquals(GnssTrustState.REJECTED, machine.update(0.8).state) }
        assertEquals(GnssTrustState.RECOVERING, machine.update(0.8).state)
        repeat(2) { assertEquals(GnssTrustState.RECOVERING, machine.update(0.8).state) }
        assertEquals(GnssTrustState.TRUSTED, machine.update(0.8).state)
    }

    @Test fun `alternating scores do not cause rapid oscillation`() {
        val machine = GnssTrustStateMachine()

        repeat(10) {
            machine.update(0.2)
            machine.update(0.9)
        }

        assertEquals(GnssTrustState.TRUSTED, machine.state)
    }

    @Test fun `recovering state returns to rejected only after sustained adverse scores`() {
        val machine = GnssTrustStateMachine(initialState = GnssTrustState.RECOVERING)

        repeat(2) { assertEquals(GnssTrustState.RECOVERING, machine.update(0.1).state) }
        assertEquals(GnssTrustState.REJECTED, machine.update(0.1).state)
    }
}
