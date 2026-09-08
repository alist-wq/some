package com.example.navigation.replay

import com.example.navigation.core.MonotonicTimestamp
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.ObjectOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Streams session records to [path] on a dedicated thread.
 *
 * Calls to [append] only enqueue records; no disk I/O occurs on the calling
 * sensor callback. Records are persisted in append order, which callers must
 * make deterministic when simultaneous observations need a defined order.
 */
class SensorSessionWriter(
    private val path: Path,
    private val header: SensorSessionHeader,
    private val flushIntervalMillis: Long = DEFAULT_FLUSH_INTERVAL_MILLIS
) : Closeable {
    private val queue = LinkedBlockingQueue<QueueItem>()
    private val failure = AtomicReference<Throwable?>(null)
    private val worker: Thread

    @Volatile
    private var closed = false

    init {
        require(flushIntervalMillis > 0) { "flushIntervalMillis must be positive" }
        worker = Thread(::writeRecords, "sensor-session-writer").apply {
            isDaemon = true
            start()
        }
    }

    /** Enqueues one record for asynchronous persistence in append order. */
    fun append(record: SensorSessionRecord) {
        check(!closed) { "Sensor session writer is closed" }
        failure.get()?.let { throw IllegalStateException("Sensor session writer failed", it) }
        queue.add(QueueItem.Record(record))
    }

    /** Drains accepted records, writes a completion footer, and closes the stream. */
    fun close(completedAt: MonotonicTimestamp) {
        if (closed) return
        closed = true
        queue.add(QueueItem.Close(completedAt))
        worker.join()
        failure.get()?.let { throw IllegalStateException("Sensor session writer failed", it) }
    }

    override fun close() = close(header.startedAt)

    private fun writeRecords() {
        try {
            path.parent?.let(Files::createDirectories)
            ObjectOutputStream(BufferedOutputStream(Files.newOutputStream(path))).use { output ->
                output.writeObject(header)
                var lastFlushNanos = System.nanoTime()
                while (true) {
                    when (val item = queue.poll(flushIntervalMillis, TimeUnit.MILLISECONDS)) {
                        is QueueItem.Record -> output.writeObject(item.record)
                        is QueueItem.Close -> {
                            output.writeObject(SensorSessionFooter(item.completedAt))
                            output.flush()
                            return
                        }
                        null -> output.flush()
                    }
                    if (System.nanoTime() - lastFlushNanos >= TimeUnit.MILLISECONDS.toNanos(flushIntervalMillis)) {
                        output.flush()
                        lastFlushNanos = System.nanoTime()
                    }
                }
            }
        } catch (error: Throwable) {
            failure.set(error)
        }
    }

    private sealed interface QueueItem {
        data class Record(val record: SensorSessionRecord) : QueueItem
        data class Close(val completedAt: MonotonicTimestamp) : QueueItem
    }

    private companion object {
        const val DEFAULT_FLUSH_INTERVAL_MILLIS = 1_000L
    }
}
