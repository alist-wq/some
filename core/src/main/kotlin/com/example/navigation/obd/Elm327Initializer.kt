package com.example.navigation.obd

data class Elm327Capabilities(
    val adapterIdentifier: String?,
    val echoDisabled: Boolean,
    val linefeedsDisabled: Boolean,
    val spacesDisabled: Boolean,
    val headersDisabled: Boolean
)

/** Initializes an ELM327-compatible adapter and reports confirmed capabilities. */
class Elm327Initializer(
    private val transport: ObdTransport,
    private val commandTimeoutMillis: Long = DEFAULT_COMMAND_TIMEOUT_MILLIS,
    private val resetTimeoutMillis: Long = DEFAULT_RESET_TIMEOUT_MILLIS
) {
    init {
        require(commandTimeoutMillis > 0) { "commandTimeoutMillis must be positive" }
        require(resetTimeoutMillis > 0) { "resetTimeoutMillis must be positive" }
    }

    fun initialize(): Elm327Capabilities {
        val reset = responseFor("ATZ", resetTimeoutMillis)
        require(reset.isNotBlank() && !isFailure(reset)) { "ELM327 reset returned an invalid response: $reset" }

        return Elm327Capabilities(
            adapterIdentifier = responseFor("ATI", commandTimeoutMillis).takeUnless(::isFailure),
            echoDisabled = acceptsOk("ATE0"),
            linefeedsDisabled = acceptsOk("ATL0"),
            spacesDisabled = acceptsOk("ATS0"),
            headersDisabled = acceptsOk("ATH0")
        )
    }

    private fun acceptsOk(command: String): Boolean = responseFor(command, commandTimeoutMillis) == "OK"

    private fun responseFor(command: String, timeoutMillis: Long): String {
        val raw = transport.command(command, timeoutMillis)
        return normalize(command, raw)
    }

    private fun normalize(command: String, raw: String): String = raw
        .replace('>', ' ')
        .replace("\r", "\n")
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.equals(command, ignoreCase = true) }
        .joinToString(" ")
        .trim()
        .uppercase()

    private fun isFailure(response: String): Boolean = response.isBlank() || FAILURE_MARKERS.any(response::contains)

    private companion object {
        const val DEFAULT_COMMAND_TIMEOUT_MILLIS = 2_000L
        const val DEFAULT_RESET_TIMEOUT_MILLIS = 8_000L
        val FAILURE_MARKERS = listOf("?", "ERROR", "UNABLE TO CONNECT", "BUS INIT")
    }
}
