package com.example.navigation.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObdDiscoveryDevicesTest {
    @Test
    fun `merges repeated scan result and retains current pairing state`() {
        val address = "00:11:22:33:44:55"
        val devices = mergeNearbyObdDevice(
            listOf(NearbyObdDevice("ELM327", address, isPaired = false)),
            NearbyObdDevice("ELM327", address, isPaired = true)
        )

        assertEquals(1, devices.size)
        assertTrue(devices.single().isPaired)
    }

    @Test
    fun `orders distinct scan results by device name`() {
        val devices = mergeNearbyObdDevice(
            listOf(NearbyObdDevice("Zeta", "AA:AA:AA:AA:AA:AA", false)),
            NearbyObdDevice("Alpha", "BB:BB:BB:BB:BB:BB", false)
        )

        assertEquals(listOf("Alpha", "Zeta"), devices.map { it.name })
    }
}
