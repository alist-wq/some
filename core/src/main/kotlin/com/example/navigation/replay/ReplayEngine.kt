package com.example.navigation.replay

import com.example.navigation.core.MonotonicTimestamp
import java.nio.file.Path

/** Interface shared by live and replay sensor sources. */
fun interface SensorSessionRecordConsumer {
    fun onRecord(record: SensorSessionRecord)
}

/** Streams recorded observations to a [SensorSessionRecordConsumer] under a [ReplayClock]. */
class ReplayEngine(
    private val sessionPath: Path,
    private val clock: ReplayClock
) {
    /** Replays records in their recorded timestamp order and returns session metadata. */
    fun replay(
        consumer: SensorSessionRecordConsumer,
        transformers: List<ReplayRecordTransformer> = emptyList()
    ): SensorSessionHeader =
        SensorSessionReader(sessionPath).use { reader ->
            var previousTimestamp: MonotonicTimestamp? = null
            reader.records().forEach { record ->
                var transformed = listOf(record)
                transformers.forEach { transformer -> transformed = transformed.flatMap(transformer::transform) }
                transformed.forEach { output ->
                    previousTimestamp?.let {
                        require(output.timestamp >= it) { "Replay output contains out-of-order observations" }
                    }
                    clock.advanceTo(output.timestamp)
                    consumer.onRecord(output)
                    previousTimestamp = output.timestamp
                }
            }
            reader.header
        }
}
