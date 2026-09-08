package com.example.navigation.obd

import kotlin.test.Test
import kotlin.test.assertEquals

class Elm327InitializerTest {
    @Test
    fun initializerHandlesEchoAndLineEndingDifferences() {
        val transport = FakeTransport(mapOf(
            "ATZ" to "ATZ\r\rELM327 v1.5\r>",
            "ATI" to "ATI\nELM327 v1.5\n>",
            "ATE0" to "OK\r>",
            "ATL0" to "OK\n>",
            "ATS0" to "OK\r\n>",
            "ATH0" to "OK\r>"
        ))

        val capabilities = Elm327Initializer(transport).initialize()

        assertEquals("ELM327 V1.5", capabilities.adapterIdentifier)
        assertEquals(true, capabilities.echoDisabled)
        assertEquals(true, capabilities.linefeedsDisabled)
        assertEquals(true, capabilities.spacesDisabled)
        assertEquals(true, capabilities.headersDisabled)
    }

    private class FakeTransport(private val responses: Map<String, String>) : ObdTransport {
        override val isConnected = true
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun reconnect() = Unit
        override fun command(command: String, timeoutMillis: Long): String = responses.getValue(command)
        override fun close() = Unit
    }
}
