package com.example.navigation.replay

/** Replay-only transform; returning an empty list removes a record. */
fun interface ReplayRecordTransformer {
    fun transform(record: SensorSessionRecord): List<SensorSessionRecord>
}
