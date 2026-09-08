# Sensor session format

## Decision

Sessions use a streaming Java object stream. The first object is a `SensorSessionHeader`; it is followed by timestamped `SensorSessionRecord` objects in writer append order and, after a clean close, a `SensorSessionFooter`.

## Supported records

The format records processed GNSS, raw GNSS, gyroscope, accelerometer, game rotation vector, magnetometer, barometer, vehicle speed, navigation-mode changes, and user markers. Every observation and marker carries `MonotonicTimestamp` data using Android elapsed-realtime semantics.

## Evolution and recovery

The header carries `schemaVersion`. Readers must reject or explicitly migrate unsupported versions. A footer is written only after all accepted records have been flushed. Its absence means the file is an incomplete session; a reader may replay complete records up to the end of the stream but must report that state.

## Replay and scale

Records are written and read sequentially, so a replay reader can process one record at a time without loading the full session into memory. Ordering is the writer's append order; callers provide deterministic ordering for simultaneous sensor callbacks. No wall-clock timestamp participates in replay ordering.
