# AGENTS.md

## Project

This repository contains an Android offline navigation application designed to maintain an estimated navigation position when GNSS becomes unavailable, jammed, unreliable, or potentially spoofed.

The system supports two primary navigation modes:

- Vehicle dead reckoning
- Pedestrian dead reckoning

The application combines trusted GNSS measurements, Android motion sensors, vehicle telemetry, offline map data, route constraints, and probabilistic map matching.

The software must never represent an estimated position as a guaranteed true position.

---

# Core Objective

The application should:

1. Use GNSS normally while GNSS observations are trustworthy.
2. Detect GNSS observations that are inconsistent with independent motion measurements.
3. Reduce or remove GNSS influence when confidence falls.
4. Continue estimating movement using dead reckoning.
5. Constrain the estimate using the known route and map topology.
6. Maintain an explicit position-confidence estimate.
7. Reintroduce GNSS gradually after observations become trustworthy again.

The first supported use case is route-constrained navigation.

Unrestricted GNSS-independent localization is not an MVP requirement.

---

# Architecture Principles

## 1. Separate measurement from estimation

Sensor providers only collect measurements.

They must not implement navigation decisions.

Examples:

- `GnssProvider` collects GNSS data.
- `ImuProvider` collects IMU data.
- `VehicleSpeedProvider` collects OBD vehicle speed.

Navigation logic belongs in estimation modules.

---

## 2. Preserve raw data

Whenever practical, store raw sensor observations before applying filtering.

Recorded sessions must be replayable.

The same input recording must produce deterministic output unless an algorithm explicitly uses randomness.

Randomized algorithms must support deterministic seeds during testing.

---

## 3. Use monotonic timestamps

Sensor fusion must use Android monotonic clock timestamps.

Preferred time base:

`elapsedRealtimeNanos`

Do not use wall-clock timestamps for sensor fusion.

Wall-clock time may be stored separately for logging.

---

## 4. GNSS is a measurement, not truth

Never assume that an Android `Location` object is valid simply because the OS reports a GNSS fix.

Every GNSS observation must pass through the GNSS trust layer before being used as a strong positioning correction.

---

## 5. Do not double-integrate phone acceleration for velocity

For vehicle navigation, use vehicle speed from OBD when available.

For pedestrian navigation, use step detection and stride estimation.

Direct double integration of consumer smartphone accelerometer measurements must not be used as the primary long-duration velocity estimator.

---

## 6. Prefer relative gyro heading over magnetometer heading

Inside vehicles, the magnetometer is not sufficiently reliable to act as the primary heading source.

Use:

- gyroscope
- game rotation vector
- road geometry
- trusted GNSS course

as primary heading information.

Magnetometer measurements may be used as low-confidence supporting observations.

---

## 7. Maintain uncertainty

Every estimated position must have an associated uncertainty/confidence state.

Do not return only:

`latitude`
`longitude`

Return something similar to:

```kotlin
data class NavigationPosition(
    val latitude: Double,
    val longitude: Double,
    val confidence: PositionConfidence,
    val estimatedAccuracyMeters: Double,
    val source: PositionSource,
    val timestampNanos: Long
)
```

Possible position sources:

```kotlin
enum class PositionSource {
    GNSS,
    HYBRID,
    VEHICLE_DEAD_RECKONING,
    PEDESTRIAN_DEAD_RECKONING,
    MAP_MATCHED_ESTIMATE
}
```

---

# Repository Structure

Preferred structure:

```text
app/
core/
sensors/
gnss/
obd/
fusion/
pedestrian/
matching/
routing/
maps/
navigation/
replay/
data/
benchmark/
```

Possible Kotlin structure:

```text
sensors/
    ImuProvider.kt
    RotationProvider.kt
    AccelerometerProvider.kt
    SensorCalibration.kt

gnss/
    GnssProvider.kt
    RawGnssProvider.kt
    GnssSample.kt
    GnssTrustEngine.kt
    GnssTrustScore.kt
    Cn0Monitor.kt
    AgcMonitor.kt

obd/
    ObdConnection.kt
    Elm327Transport.kt
    VehicleSpeedProvider.kt
    ObdSample.kt

fusion/
    VehicleMotionEstimator.kt
    DeadReckoner.kt
    GyroBiasEstimator.kt
    PositionUncertainty.kt

pedestrian/
    StepDetector.kt
    StepLengthEstimator.kt
    PedestrianHeadingEstimator.kt
    PedestrianDeadReckoner.kt
    PhonePoseEstimator.kt

matching/
    RouteMatcher.kt
    RoadCandidate.kt
    RoadHypothesis.kt
    HypothesisTracker.kt
    ParticleState.kt

routing/
    RoutingEngine.kt
    Route.kt
    RouteSegment.kt
    RouteRepository.kt

maps/
    OfflineMapManager.kt
    MapRenderer.kt

navigation/
    NavigationEngine.kt
    NavigationPosition.kt
    PositionConfidence.kt
    PositionSource.kt

replay/
    ReplayEngine.kt
    ReplayClock.kt
    RecordedSession.kt

benchmark/
    BenchmarkRunner.kt
    BenchmarkResult.kt
```

Exact module boundaries may evolve, but responsibilities should remain separated.

---

# Navigation Modes

Use explicit navigation modes.

```kotlin
enum class NavigationMode {
    VEHICLE,
    PEDESTRIAN,
    UNKNOWN
}
```

Mode switching must not silently reset position history.

The last trustworthy state from the previous mode should initialize the next mode when appropriate.

Example:

```text
vehicle navigation
↓
vehicle stops
↓
OBD disconnects
↓
walking detected
↓
pedestrian dead reckoning
```

---

# Vehicle Dead Reckoning

Primary measurements:

- OBD vehicle speed
- gyroscope yaw
- Android game rotation vector
- trusted GNSS
- route geometry

Primary state should include:

```text
position
heading
speed
gyro bias
distance travelled
route progress
position uncertainty
```

Vehicle speed should preferably come from OBD-II PID 0D.

Do not estimate long-term vehicle speed from accelerometer integration.

---

# Pedestrian Dead Reckoning

Primary measurements:

- detected steps
- estimated stride length
- gyroscope
- rotation vector
- trusted GNSS
- pedestrian route topology
- optional barometric altitude

Primary state should include:

```text
position
walking heading
step count
estimated stride
route progress
pose confidence
position uncertainty
```

Phone orientation and pedestrian direction are not equivalent.

The pedestrian module must not assume the phone is always held facing forward.

---

# Pedestrian Pose Handling

Potential phone states:

```text
HAND_FORWARD
HAND_SWINGING
POCKET
BAG
UNKNOWN
```

If phone pose confidence is low, heading uncertainty must increase.

Do not force a precise heading estimate when the phone orientation is ambiguous.

---

# Route-Constrained Navigation

Route-constrained positioning is the primary MVP mode.

Instead of solving arbitrary 2D localization, prefer estimating:

```text
current route segment
distance along route
current movement direction
probability/confidence
```

Route progress should be considered a first-class state variable.

Example:

```kotlin
data class RouteProgress(
    val segmentId: Long,
    val metersAlongSegment: Double,
    val totalMetersAlongRoute: Double,
    val probability: Double
)
```

---

# Map Matching

Do not snap every estimated point to the nearest road.

Nearest-road matching is insufficient.

Maintain multiple hypotheses when necessary.

Examples:

```text
straight road
left branch
right branch
parallel road
pedestrian path
```

Candidate probability may incorporate:

- distance from estimated position
- observed travel distance
- gyro turn angle
- road heading
- expected route
- legal road connectivity
- pedestrian connectivity
- GNSS trust
- previous candidate probability

Prefer an HMM, Viterbi-style matcher, particle filter, or similar probabilistic approach.

---

# GNSS Trust Engine

The GNSS trust engine outputs a continuous value.

Preferred representation:

```text
0.0 = unusable
1.0 = highly trustworthy
```

Do not rely solely on a binary spoof flag.

Possible inputs:

- impossible position jump
- impossible velocity
- GNSS velocity vs OBD velocity
- GNSS displacement vs OBD distance
- GNSS heading vs gyro/road heading
- deviation from current route
- impossible movement relative to previous state
- C/N0 anomalies
- AGC anomalies
- satellite constellation anomalies
- GNSS time anomalies
- disagreement with map-matched state

Example behavior:

```text
trust >= 0.70
GNSS may strongly correct dead reckoning

0.30 <= trust < 0.70
GNSS correction weight should be reduced

trust < 0.30
GNSS position should normally be rejected
```

Thresholds must be configurable and must be validated against recorded datasets.

Do not hard-code final production values before testing.

---

# GNSS Recovery

After GNSS has been rejected, do not immediately trust the first plausible fix.

Recovery should require sustained consistency.

Suggested process:

```text
candidate GNSS returns
↓
plausibility checks pass
↓
observe multiple samples
↓
compare with dead-reckoned/map-matched estimate
↓
increase trust gradually
↓
hybrid mode
↓
normal GNSS mode
```

---

# Replay System

The replay system is a core product component.

It is not optional debugging infrastructure.

Every major positioning algorithm should be testable using recorded sensor sessions.

Recorded data should include, where available:

```text
GNSS location
GNSS velocity
GNSS bearing
raw GNSS measurements
C/N0
AGC
gyro
accelerometer
rotation vector
barometer
OBD speed
navigation mode
reference route
ground-truth position if available
```

All measurements should carry monotonic timestamps.

---

# GNSS Corruption Simulation

Replay tests should support synthetic GNSS faults.

Required scenarios should eventually include:

```text
GNSS loss
constant false location
large position jump
gradual position drift
false velocity
false heading
delayed GNSS observations
noisy GNSS
intermittent GNSS
```

Corruption must be implemented in the replay layer rather than by changing production algorithms.

---

# Benchmark Metrics

The replay benchmark should report at minimum:

```text
percentage on correct road/path
mean position error
maximum position error
mean route-progress error
maximum route-progress error
number of wrong intersection selections
GNSS rejection delay
GNSS false-rejection count
GNSS recovery time
time spent in low-confidence state
```

Example:

```text
Scenario: UA-KH-014

GNSS corruption:
04:30–11:30

Correct road:
99.1%

Mean route-progress error:
17.8 m

Maximum route-progress error:
46.2 m

Wrong intersection selections:
0

Recovery time:
4.1 sec
```

---

# Confidence Model

The application must distinguish between:

```text
HIGH
MEDIUM
LOW
LOST
```

or another explicit confidence representation.

Confidence should account for:

- time since trusted GNSS
- accumulated gyro uncertainty
- distance-estimation uncertainty
- route ambiguity
- number of competing map hypotheses
- map quality
- pedestrian pose uncertainty

Do not display false precision.

---

# User Interface Requirements

The UI should expose positioning source.

Example:

```text
POSITION SOURCE

GNSS
HYBRID
VEHICLE DR
PEDESTRIAN DR
```

If GNSS is rejected, indicate this clearly.

Example:

```text
GNSS SIGNAL UNTRUSTED

Dead reckoning active
Estimated accuracy: ±35 m
```

When confidence is low, use an uncertainty area or explicit warning rather than a precise normal-location marker.

---

# Offline Requirements

Core navigation should function without network access after required regional data has been downloaded.

Offline data may include:

- vector map tiles
- OpenStreetMap road/path graph
- routing graph
- saved routes
- navigation metadata

Network services must not be required for the dead-reckoning loop.

---

# Data Safety

Sensor logs may contain sensitive location history.

Debug logs must not automatically upload recorded trajectories.

Any future remote telemetry must be explicitly opt-in.

Tests should use synthetic or anonymized datasets when possible.

---

# Android Requirements

Primary language:

`Kotlin`

Preferred UI:

`Jetpack Compose`

Sensor and GNSS code should be isolated from UI code.

Use foreground services where required for continuous navigation.

Avoid keeping high-frequency sensors active when navigation is not running.

---

# Battery Requirements

Do not automatically use maximum sensor frequency.

Suggested initial ranges:

```text
gyro:
50–100 Hz

fusion:
20–50 Hz

OBD:
5–20 Hz depending on adapter

map matching:
5–10 Hz

GNSS trust evaluation:
GNSS update rate
```

Final frequencies should be based on profiling.

---

# External Libraries

Prefer established libraries over custom implementations where appropriate.

Candidates include:

- MapLibre Native for Android map rendering
- OpenStreetMap data
- GraphHopper for routing / road graph functionality
- Android GNSS APIs
- Android sensor APIs

Do not add a dependency without documenting:

```text
purpose
license
module using it
reason custom implementation was rejected
```

---

# Coding Rules for Codex Agents

## Keep tasks narrow

A Codex task should normally modify one module or one tightly related feature.

Avoid requests such as:

`Build the navigation engine.`

Prefer:

`Implement VehicleSpeedProvider using the existing interface and add unit tests.`

---

## Do not redesign unrelated code

If the requested change only concerns the GNSS module, do not refactor the map or UI module unless required.

If architecture changes appear necessary, stop the implementation at the boundary and document the proposed change.

---

## Preserve public interfaces

Do not modify shared interfaces unless the task explicitly allows it.

If an interface must change, identify affected modules before modifying it.

---

## Tests are required

New algorithms must include tests.

Bug fixes should include a regression test whenever practical.

Sensor algorithms should preferably be tested against replayed data rather than only mocked scalar values.

---

## Build before completion

Before marking an implementation task complete:

1. Compile the affected module.
2. Run relevant unit tests.
3. Run relevant replay tests if applicable.
4. Report failures.
5. Do not hide failing tests.

---

## No silent fallback

Do not silently substitute:

```text
GNSS speed
```

when OBD speed is required.

Do not silently substitute:

```text
magnetometer heading
```

when gyro-based heading is required.

Fallback behavior must be explicit and reflected in confidence.

---

# Task Completion Format

When completing a Codex task, report:

```text
Implemented:
- ...

Files changed:
- ...

Tests added:
- ...

Tests run:
- ...

Known limitations:
- ...

Recommended next task:
- ...
```

Do not claim a feature works unless the relevant build/tests were actually executed.

---

# Agent Responsibilities

## Architecture Agent

Responsible for:

- module boundaries
- interfaces
- shared data models
- architecture decisions
- dependency direction

Must avoid implementing large feature modules unless specifically tasked.

---

## Android Platform Agent

Responsible for:

- Android app configuration
- permissions
- services
- lifecycle
- Compose integration
- Android compatibility

---

## GNSS Agent

Responsible for:

- Android location APIs
- raw GNSS measurements
- satellite measurements
- C/N0
- AGC
- GNSS timestamps

Must not decide whether GNSS should be trusted.

That belongs to the trust engine.

---

## GNSS Trust Agent

Responsible for:

- GNSS plausibility scoring
- GNSS/OBD comparison
- GNSS/IMU comparison
- GNSS/map comparison
- trust transitions
- recovery logic

---

## IMU Agent

Responsible for:

- gyroscope
- acceleration
- rotation vector
- timestamping
- calibration
- sensor quality

---

## OBD Agent

Responsible for:

- Bluetooth transport
- ELM327 communication
- vehicle speed
- reconnect behavior
- latency measurements

The first required vehicle PID is:

`Mode 01 PID 0D — Vehicle Speed`

---

## Fusion Agent

Responsible for:

- dead-reckoning state
- gyro propagation
- vehicle distance propagation
- bias estimation
- uncertainty growth
- trusted correction application

---

## Pedestrian Agent

Responsible for:

- step detection
- stride estimation
- pedestrian heading
- phone pose
- pedestrian dead reckoning
- pedestrian uncertainty

---

## Routing Agent

Responsible for:

- route calculation
- offline routing graph
- route geometry
- segment metadata
- turn restrictions

---

## Map Matching Agent

Responsible for:

- candidate generation
- route/path hypotheses
- HMM or particle tracking
- branch selection
- route-progress estimation

---

## Replay Agent

Responsible for:

- recorded sensor playback
- deterministic timing
- GNSS corruption injection
- benchmark execution

Replay behavior must be reproducible.

---

## Navigation UI Agent

Responsible for:

- map rendering
- current route
- estimated position
- confidence visualization
- GNSS trust status
- maneuver guidance

The UI must not hide uncertainty.

---

## QA Agent

Responsible for:

- integration tests
- replay regression suite
- build verification
- device compatibility
- performance regression detection

---

# Initial Development Order

Tasks should normally follow this sequence:

```text
01 Repository structure
02 Shared data models
03 Sensor session format
04 Replay engine
05 GNSS recorder
06 Raw GNSS recorder
07 IMU recorder
08 OBD connection
09 Vehicle speed provider
10 Offline map
11 Offline routing
12 Vehicle dead reckoner
13 Route-progress estimator
14 Turn detector
15 Route-constrained matcher
16 GNSS trust engine
17 GNSS rejection
18 GNSS recovery
19 Position confidence model
20 Vehicle navigation UI
21 Pedestrian step detector
22 Stride estimator
23 Pedestrian heading estimator
24 Pedestrian route-progress estimator
25 Pedestrian map matcher
26 Vehicle/pedestrian mode switching
27 Replay benchmark suite
28 Performance optimization
29 Field-test tooling
```

---

# MVP Success Criteria

## Vehicle MVP

During replay with corrupted or removed GNSS:

- remain on the correct route branch
- avoid impossible GNSS jumps
- correctly identify most route turns
- expose increasing uncertainty over time
- recover smoothly when GNSS becomes trustworthy

Initial target:

```text
5+ minutes without trusted GNSS

correct route branch:
> 95%

target typical route-progress error:
< 50 m
```

These are engineering targets, not guarantees.

---

## Pedestrian MVP

During route-constrained walking:

- detect steps reliably
- estimate distance without GNSS
- identify major turns
- remain on the correct planned route when possible
- expose phone-pose uncertainty

Initial target:

```text
500–1000 m route-constrained PDR

correct route branch:
> 90%

without trusted GNSS
```

Targets should be revised based on recorded test data.

---

# Non-Goals for Initial MVP

Do not expand MVP scope to include:

- full indoor navigation
- arbitrary indoor floor plans
- BLE beacon infrastructure
- UWB positioning
- unrestricted long-duration inertial navigation
- lane-level positioning
- autonomous driving
- safety-critical vehicle control
- military targeting or guidance systems

The project is a civilian navigation aid.

---

# Core Rule

When uncertain, the software must prefer:

`less precise but honest`

over:

`precise but potentially wrong`

Every implementation decision should preserve this principle.