package com.example.navigation.app

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal object RenavDiagnostics {
    private val lock = Any()
    private var writer: DiagnosticWriter? = null
    private var previousHandler: Thread.UncaughtExceptionHandler? = null

    fun initialize(context: Context) {
        synchronized(lock) {
            if (writer != null) return
            val appContext = context.applicationContext
            writer = AndroidDiagnosticWriter(appContext).also { it.open() }
            previousHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, error ->
                event("uncaught_exception", "thread=${thread.name} ${error.stackTraceToString()}")
                previousHandler?.uncaughtException(thread, error)
            }
        }
    }

    fun event(name: String, detail: String? = null) {
        synchronized(lock) { writer?.append(DiagnosticLine.format(name, detail)) }
    }

    internal fun resetForTests() = synchronized(lock) { writer = null; previousHandler = null }
}

internal data class DiagnosticLine(val monotonicNanos: Long, val wallClockMillis: Long, val name: String, val detail: String?) {
    fun render(): String = buildString {
        append("elapsedNanos=").append(monotonicNanos)
            .append(" wallClock=").append(ISO_FORMAT.format(Date(wallClockMillis)))
            .append(" event=").append(name)
        if (!detail.isNullOrBlank()) append(" detail=").append(detail.replace('\n', ' '))
        append('\n')
    }

    companion object {
        private val ISO_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        fun format(name: String, detail: String? = null): String =
            DiagnosticLine(SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), name, detail).render()
    }
}

private interface DiagnosticWriter {
    fun open()
    fun append(line: String)
}

private class AndroidDiagnosticWriter(private val context: Context) : DiagnosticWriter {
    private var output: OutputStream? = null
    private var fallback: File? = null

    override fun open() {
        val displayName = DiagnosticFileNamer.forWallClock(System.currentTimeMillis())
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Files.FileColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.Files.FileColumns.MIME_TYPE, "text/plain")
                    put(MediaStore.Files.FileColumns.RELATIVE_PATH, DIAGNOSTIC_RELATIVE_DIRECTORY)
                }
                val uri = context.contentResolver.insert(MediaStore.Files.getContentUri("external"), values)
                output = uri?.let { context.contentResolver.openOutputStream(it, "wa") }
            }
        } catch (_: Throwable) {
            output = null
        }
        if (output == null) {
            val directory = File(context.filesDir, "diagnostics").apply { mkdirs() }
            fallback = File(directory, displayName)
        }
        append("diagnostic_log_started appVersion=${context.packageManager.getPackageInfo(context.packageName, 0).versionName} device=${Build.MANUFACTURER} ${Build.MODEL}")
    }

    override fun append(line: String) {
        try {
            (output ?: fallback?.outputStream()?.also { output = it })?.apply {
                write(line.toByteArray(Charsets.UTF_8)); flush()
            }
        } catch (_: Throwable) {
            // Diagnostics must never crash the navigation app.
        }
    }
}

private const val DIAGNOSTIC_RELATIVE_DIRECTORY = "Documents/RENAV/Diagnostics"

internal object DiagnosticFileNamer {
    fun forWallClock(wallClockMillis: Long): String =
        "renav-log-${SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date(wallClockMillis))}.txt"
}
