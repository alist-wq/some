package com.example.navigation.app

import org.junit.Assert.assertEquals
import org.junit.Test

class AppBuildBaselineTest {
    @Test
    fun appModuleCanReferenceCoreModels() {
        assertEquals(0L, appBuildBaselineTimestamp().nanos)
    }
}
