package com.example.navigation.pedestrian

import kotlin.test.Test
import kotlin.test.assertTrue

class BasicStrideModelTest {
    @Test fun `higher cadence and acceleration increase initial stride estimate`() {
        val model = BasicStrideModel()
        val baseline = model.estimate(StrideUserProfile(), 100.0, 2.0)
        val active = model.estimate(StrideUserProfile(), 140.0, 4.0)

        assertTrue(active.estimatedLengthMeters > baseline.estimatedLengthMeters)
        assertTrue(active.uncertaintyMeters >= baseline.uncertaintyMeters)
    }

    @Test fun `missing cadence produces explicit additional uncertainty`() {
        val model = BasicStrideModel()
        val withCadence = model.estimate(StrideUserProfile(), 100.0, 2.0)
        val withoutCadence = model.estimate(StrideUserProfile(), null, 2.0)

        assertTrue(withoutCadence.uncertaintyMeters > withCadence.uncertaintyMeters)
    }
}
