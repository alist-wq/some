package com.example.navigation.app

import android.content.Context

data class InterruptedNavigationSession(
    val sessionFileName: String,
    /** Android elapsed-realtime value; valid only for diagnosis within the same boot. */
    val checkpointElapsedRealtimeNanos: Long,
    val checkpointWallClockMillis: Long
) {
    init {
        require(sessionFileName.isNotBlank())
        require(checkpointElapsedRealtimeNanos >= 0L)
        require(checkpointWallClockMillis >= 0L)
    }
}

enum class ProcessRestartRecoveryDisposition { NONE, RECOVER_INTERRUPTED_SESSION, DISCARD_STALE_SESSION }

data class ProcessRestartRecoveryDecision(
    val disposition: ProcessRestartRecoveryDisposition,
    val sessionFileName: String? = null,
    /** Always false: dead reckoning requires fresh measurements after process restart. */
    val resumeDeadReckoning: Boolean = false
)

/** Conservative process-restart policy: retain session diagnostics, never reuse an old monotonic state. */
class ProcessRestartRecoveryPolicy(private val maximumCheckpointAgeMillis: Long = 15 * 60 * 1000L) {
    init { require(maximumCheckpointAgeMillis > 0L) }

    fun evaluate(session: InterruptedNavigationSession?, nowWallClockMillis: Long): ProcessRestartRecoveryDecision {
        if (session == null) return ProcessRestartRecoveryDecision(ProcessRestartRecoveryDisposition.NONE)
        val ageMillis = nowWallClockMillis - session.checkpointWallClockMillis
        return if (ageMillis < 0L || ageMillis > maximumCheckpointAgeMillis) {
            ProcessRestartRecoveryDecision(ProcessRestartRecoveryDisposition.DISCARD_STALE_SESSION, session.sessionFileName)
        } else {
            ProcessRestartRecoveryDecision(ProcessRestartRecoveryDisposition.RECOVER_INTERRUPTED_SESSION, session.sessionFileName)
        }
    }
}

/** Small durable checkpoint for graceful UI recovery after the process hosting the foreground service is killed. */
class NavigationProcessRecoveryStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun save(session: InterruptedNavigationSession) {
        preferences.edit()
            .putString(KEY_FILE_NAME, session.sessionFileName)
            .putLong(KEY_ELAPSED_NANOS, session.checkpointElapsedRealtimeNanos)
            .putLong(KEY_WALL_CLOCK_MILLIS, session.checkpointWallClockMillis)
            .apply()
    }

    fun consume(policy: ProcessRestartRecoveryPolicy, nowWallClockMillis: Long): ProcessRestartRecoveryDecision {
        val fileName = preferences.getString(KEY_FILE_NAME, null)
        val elapsedNanos = preferences.getLong(KEY_ELAPSED_NANOS, -1L)
        val wallClockMillis = preferences.getLong(KEY_WALL_CLOCK_MILLIS, -1L)
        val saved = if (fileName.isNullOrBlank() || elapsedNanos < 0L || wallClockMillis < 0L) null
        else InterruptedNavigationSession(fileName, elapsedNanos, wallClockMillis)
        clear()
        return policy.evaluate(saved, nowWallClockMillis)
    }

    fun clear() { preferences.edit().clear().apply() }

    private companion object {
        const val PREFERENCES = "navigation_process_recovery"
        const val KEY_FILE_NAME = "session_file_name"
        const val KEY_ELAPSED_NANOS = "checkpoint_elapsed_nanos"
        const val KEY_WALL_CLOCK_MILLIS = "checkpoint_wall_clock_millis"
    }
}
