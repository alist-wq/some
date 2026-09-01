# CODEX_BACKLOG.md

## Usage

Each task in this backlog is intended to be executed independently by Codex.

Before starting any task, Codex must read:

- `/AGENTS.md`
- the task definition below
- any files explicitly listed as dependencies

Codex must not broaden scope beyond the task.

Each task should end with:

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

---

# Model Guidance

Use the lowest-cost model capable of reliably completing the task.

Recommended tiers:

```text
LUNA
- boilerplate
- data classes
- simple persistence
- straightforward Android APIs
- unit tests
- refactors
- documentation

TERRA
- module implementation
- Bluetooth/OBD
- Android GNSS integrations
- MapLibre / GraphHopper integration
- replay infrastructure
- medium-complexity algorithms

SOL
- sensor-fusion architecture
- GNSS spoof/trust reasoning
- probabilistic map matching
- difficult debugging
- algorithm tuning
- cross-module performance problems
```

Do not use Sol for routine implementation.

---

# Threat-Model Rationale

The navigation engine must assume that GNSS can fail in more than one way.

The receiver may experience:

```text
NO SIGNAL
JAMMED SIGNAL
LARGE POSITION JUMP
REMOTE LOCATION SPOOF
GRADUAL POSITION DRIFT
FALSE SPEED
FALSE BEARING
GNSS TIME ANOMALY
APPARENTLY VALID BUT FALSE FIX
```

A publicly observed failure pattern in Ukraine includes devices reporting a remote position around Lima, Peru during GNSS interference. This backlog treats that as a canonical replay and benchmark fixture only.

Production logic must never contain a Lima-, Peru-, Ukraine-, country-, or coordinate-specific spoof detector.

The engineering question is always:

```text
Is this GNSS observation physically and probabilistically
consistent with independent information about movement?
```

Independent evidence includes:

```text
previous trusted state
monotonic elapsed time
OBD vehicle speed
gyroscope turns
pedestrian steps
pedestrian heading
route progress
map topology
raw GNSS diagnostics
```

Remote continental teleports are relatively easy to detect. The harder long-term threat is a plausible nearby false trajectory or gradual drift. Passing a Lima-style test therefore does not establish robust spoof resistance.

---

# Project Invariants

These requirements apply across all tasks.

## Invariant 1 — GNSS validity is not trust

An Android GNSS fix marked valid by the operating system is still only a measurement.

It must not be treated as ground truth without trust evaluation.

## Invariant 2 — Monotonic time is authoritative for fusion

Sensor fusion and dead reckoning must use monotonic Android timing derived from `elapsedRealtimeNanos` semantics.

GNSS time and wall-clock time may be logged and analyzed but must never become the primary propagation time base.

## Invariant 3 — Preserve a trusted anchor

Rejected GNSS observations must never replace the most recent trusted navigation anchor.

## Invariant 4 — Dead reckoning starts from trusted state

When GNSS becomes untrustworthy, vehicle DR or pedestrian PDR must continue from the last trusted navigation state, not from the most recent GNSS observation.

## Invariant 5 — Remote spoof detection is generic

Production logic must not special-case Lima, Peru, Ukraine, or any other named location.

Geographic names and known coordinates may appear in:

```text
tests
fixtures
benchmarks
documentation
```

but not as production spoof signatures.

## Invariant 6 — Raw GNSS is evidence, not truth

Raw measurements such as C/N0, AGC, pseudorange rate, and constellation status may be affected by interference or spoofing and must remain supporting evidence.

## Invariant 7 — Do not overfit to spectacular failures

A remote continental teleport is an easy detection case.

The system must eventually be tested against:

```text
small offsets
gradual drift
parallel-road displacement
plausible-speed false trajectories
```

---

# EPIC 0 — Repository Foundation

---

## TASK 001 — Verify Repository Structure

### Model
Luna

### Objective

Inspect the existing Android repository and verify whether it conforms to the architecture described in `AGENTS.md`.

### Allowed changes

Documentation only.

May create:

```text
/docs/repository-audit.md
```

Do not modify application code.

### Required output

Document:

- current modules
- missing modules
- unexpected dependencies
- package structure
- existing build system
- Kotlin version
- Android Gradle Plugin version
- minimum SDK
- target SDK
- Compose status
- existing test infrastructure

### Acceptance criteria

`repository-audit.md` contains a mapping between current repository structure and desired architecture.

### Tests

None.

### Dependencies

None.

---

## TASK 002 — Create Core Module Structure

### Model
Luna

### Objective

Create missing modules/packages required for MVP without implementing functionality.

Target logical modules:

```text
core
sensors
gnss
obd
fusion
pedestrian
matching
routing
maps
navigation
replay
data
benchmark
```

Modules may remain packages inside fewer Gradle modules if that better matches the existing repository.

Do not create excessive Gradle-module fragmentation.

### Allowed changes

Project structure and Gradle configuration.

### Acceptance criteria

- project builds
- all packages exist
- no circular dependencies
- UI does not become a dependency of sensor modules

### Tests

Run:

```text
./gradlew assembleDebug
```

---

## TASK 002A — Establish Android App Build Baseline

### Model
Terra

### Objective

Create or validate the minimum scoped Android application/module and Gradle build configuration required before Android-platform implementation tasks such as TASK 019 can compile and run tests.

This task exists to keep platform setup separate from feature tasks. TASK 019 must implement GNSS collection, not silently bootstrap the Android project.

### Scope

Create or validate only the Android build baseline:

```text
Android application module
Kotlin Android plugin
Android Gradle Plugin configuration
Gradle wrapper
compileSdk
minSdk
targetSdk
application namespace/package
AndroidManifest.xml
main source set
unit-test source set
instrumentation-test source set
baseline AndroidX dependencies
```

Configure Compose only if the repository architecture has already selected Compose. Do not introduce Compose solely for this task.

### Allowed changes

Only files required to establish a compilable Android application/module baseline, including where necessary:

```text
settings.gradle / settings.gradle.kts
root build.gradle / build.gradle.kts
gradle.properties
gradle wrapper files
version catalog
app/build.gradle / app/build.gradle.kts
app/src/main/AndroidManifest.xml
minimal source-set placeholders required for compilation
```

Do not redesign the module architecture established by TASK 002.

### Do not implement

Do not implement any feature behavior, including:

```text
GNSS collection
raw GNSS collection
IMU/sensor collection
OBD
maps
routing
navigation
dead reckoning
spoof detection
feature UI
```

A minimal application entry point may exist only if required for a valid Android build; it must contain no navigation feature logic.

### Requirements

- use versions compatible with the repository and current chosen toolchain
- keep dependency additions minimal
- document any version choice that was not already established by TASK 001/002
- preserve module boundaries from `AGENTS.md`
- shared/core models must remain usable from the Android app without circular dependencies
- do not move feature code into the app module merely to make the build pass

### Acceptance criteria

- `./gradlew assembleDebug` succeeds
- `./gradlew test` succeeds, or succeeds with no tests present
- the Android application module resolves correctly in the Gradle project
- the app module can reference the intended shared/core models
- no circular module dependencies are introduced
- subsequent Android tasks have a known module/source set in which to implement platform adapters

### Tests

Run at minimum:

```text
./gradlew assembleDebug
./gradlew test
```

Report the exact commands and results.

### Dependencies

- TASK 001
- TASK 002

---

# EPIC 1 — Shared Data Model

---

## TASK 003 — Define Monotonic Timestamp Type

### Model
Luna

### Objective

Create a strongly documented representation for monotonic sensor timestamps.

Use Android `elapsedRealtimeNanos` semantics.

### Suggested interface

```kotlin
@JvmInline
value class MonotonicTimestamp(
    val nanos: Long
)
```

### Requirements

Provide utility operations for:

- subtraction
- duration conversion
- ordering

Do not mix Unix time with monotonic timestamps.

### Tests

Test:

- ordering
- differences
- nanosecond conversion

---

## TASK 004 — Define GNSS Sample Model

### Model
Luna

### Objective

Create immutable models representing processed GNSS observations.

Suggested fields:

```text
latitude
longitude
altitude
horizontal accuracy
vertical accuracy
speed
speed accuracy
bearing
bearing accuracy
provider
monotonic timestamp
wall clock timestamp
GNSS-derived time where available
```

GNSS-derived time and wall-clock time must remain separate from the monotonic timestamp used for fusion.

Nullable fields must reflect Android API availability.

### Acceptance criteria

GNSS samples can be serialized for replay and GNSS time can be corrupted in replay without altering monotonic timing.

---

## TASK 005 — Define Raw GNSS Models

### Model
Terra

### Objective

Create storage-safe data representations for raw satellite observations.

Capture where available:

```text
SVID
constellation
carrier frequency
C/N0
AGC
pseudorange rate
accumulated delta range
state flags
multipath indicator
received SV time
GNSS clock information
```

Do not store Android framework objects directly.

### Acceptance criteria

Raw GNSS samples can be serialized and replayed independently from Android APIs.

---

## TASK 006 — Define IMU Sample Models

### Model
Luna

### Objective

Create immutable representations for:

```text
gyro
accelerometer
game rotation vector
magnetometer
barometer
```

Every sample must include a monotonic timestamp.

---

## TASK 007 — Define OBD Sample Model

### Model
Luna

### Objective

Define vehicle telemetry structures.

Initial required type:

```kotlin
data class VehicleSpeedSample(
    val metersPerSecond: Double,
    val timestamp: MonotonicTimestamp,
    val source: VehicleSpeedSource
)
```

Future-compatible structure should allow more PIDs without requiring redesign.

---

## TASK 008 — Define Navigation Position Model

### Model
Luna

### Objective

Implement the navigation state models described in `AGENTS.md`.

Required:

```text
NavigationPosition
PositionSource
PositionConfidence
NavigationMode
```

Position must support:

```text
estimated accuracy
confidence
source
timestamp
```

---

# EPIC 2 — Recording System

---

## TASK 009 — Sensor Session Format

### Model
Terra

### Objective

Define the persistent format used to record synchronized navigation data.

Must support:

```text
GNSS
raw GNSS
IMU
OBD
barometer
navigation mode
route identifier
events/markers
GNSS-derived time separately from monotonic time
```

### Requirements

Format must:

- preserve monotonic timestamps
- preserve GNSS-derived time independently when available
- support future schema evolution
- support deterministic replay
- allow GNSS corruption without changing IMU/OBD timing
- avoid loading an entire large session into RAM

### Deliverable

Architecture decision document:

```text
/docs/session-format.md
```

and corresponding Kotlin models.

### Tests

Serialization/deserialization round trip.

---

## TASK 010 — Session Writer

### Model
Terra

### Objective

Implement streaming sensor-session recording.

### Requirements

Writer must:

- append observations efficiently
- preserve ordering
- tolerate sensors arriving at different frequencies
- periodically flush
- properly close incomplete sessions
- never block sensor callbacks with slow disk operations
- preserve independent monotonic and GNSS/wall-time fields

### Tests

Generate simulated multi-sensor session and verify no observations are lost.

---

## TASK 011 — Session Reader

### Model
Luna

### Objective

Read recorded sessions sequentially.

### Requirements

Support:

```text
streamed iteration
time-range selection
sensor-type filtering
```

Do not require loading full session into memory.

---

# EPIC 3 — Replay Platform

---

## TASK 012 — Replay Clock

### Model
Terra

### Objective

Implement a deterministic virtual monotonic clock.

Required modes:

```text
REALTIME
ACCELERATED
STEP
MAX_SPEED
```

### Acceptance criteria

A recorded session can replay deterministically independent of actual wall-clock time and independent of GNSS-derived time.

---

## TASK 013 — Replay Engine

### Model
Terra

### Objective

Replay recorded sensor observations to the same interfaces consumed by production navigation algorithms.

Production algorithms must not need to know whether data is:

```text
LIVE
or
REPLAY
```

### Acceptance criteria

Recorded streams produce events in original monotonic timestamp order.

GNSS fields can later be corrupted independently without altering IMU/OBD event timing.

---

## TASK 014 — Replay Event Bus

### Model
Luna

### Objective

Create a typed event-distribution layer for replay data.

Avoid one giant generic event object.

---

## TASK 015 — Synthetic GNSS Outage

### Model
Luna

### Objective

Implement replay-time GNSS removal.

Configuration example:

```text
start = 270 seconds
duration = 420 seconds
```

During the configured interval:

- GNSS positions are removed
- raw GNSS behavior is configurable

Production code must remain unchanged.

---

## TASK 016 — GNSS Position Jump Simulator

### Model
Luna

### Objective

Inject an artificial GNSS teleport.

Example:

```text
+20 km latitude/longitude displacement
```

Support:

```text
instant jump
jump and remain
jump then recover
```

---

## TASK 016A — Remote Location Spoof Simulator

### Model
Terra

### Objective

Extend the replay corruption framework to simulate an abrupt GNSS transition from the real navigation area to an arbitrary remote false location.

The canonical initial benchmark target is Lima, Peru, but the implementation must support arbitrary coordinates.

### Design rationale

A remote false fix may still look like a valid GNSS observation to Android. The simulator must therefore exercise the trust engine rather than relying on provider validity flags.

The purpose of the Lima fixture is reproducibility. It must not become a production special case.

### Example

```text
REAL STREAM
T+0 local
T+1 local
T+2 local

SPOOF START

T+3 remote target
T+4 remote target
T+5 remote target
```

Independent streams remain unchanged unless explicitly configured:

```text
OBD speed
gyroscope
accelerometer
rotation vector
barometer
step data
```

### Configuration

Support conceptually equivalent configuration:

```yaml
corruption:
  type: remote_location_spoof
  start_seconds: 270
  duration_seconds: 600
  target:
    latitude: -12.0464
    longitude: -77.0428
  behavior: STATIC
```

Initial behaviors:

```text
STATIC
CONTINUE_RELATIVE_MOTION
```

`STATIC` keeps false fixes near the configured target.

`CONTINUE_RELATIVE_MOTION` transfers subsequent GNSS movement into the false geographic area.

### Requirements

Support:

- arbitrary latitude/longitude
- configurable start/duration
- abrupt transition
- configurable recovery
- preservation of original session
- deterministic replay

Corruption must exist only in the replay layer.

### Acceptance criteria

1. Replay remains normal before spoof start.
2. GNSS transitions to configured remote coordinates.
3. IMU remains byte/semantically identical to original replay input.
4. OBD remains identical to original replay input.
5. Replay is deterministic.
6. Source recording remains unchanged.

### Tests

Include:

```text
local → Lima
local → arbitrary target 1000 km away
spoof starts and ends
spoof persists to replay end
STATIC behavior
CONTINUE_RELATIVE_MOTION behavior
```

---

## TASK 017 — Gradual GNSS Drift Simulator

### Model
Terra

### Objective

Inject progressive false GNSS displacement.

Example:

```text
0 m
10 m
25 m
50 m
100 m
...
```

Support configurable:

```text
direction
drift speed
start
end
```

---

## TASK 018 — GNSS Velocity/Bearing Corruption

### Model
Luna

### Objective

Allow independent corruption of:

```text
GNSS velocity
GNSS bearing
```

without altering position.

Useful for trust-engine testing.

---

## TASK 018A — GNSS Time Corruption Simulator

### Model
Terra

### Objective

Allow replay scenarios to corrupt GNSS-derived time independently from position.

### Design rationale

Spoofing may affect position and time differently. The navigation engine must remain stable even when GNSS time is wrong because propagation uses monotonic time.

### Supported scenarios

Implement:

```text
TIME_JUMP_FORWARD
TIME_JUMP_BACKWARD
TIME_OFFSET
TIME_GRADUAL_DRIFT
```

Support combinations:

```text
position correct + GNSS time wrong
position wrong + GNSS time correct
position wrong + GNSS time wrong
```

### Critical requirement

The simulator must never alter:

```text
MonotonicTimestamp
elapsedRealtimeNanos-derived timing
replay ordering
IMU dt
OBD dt
PDR dt
```

unless a separate test explicitly targets those fields.

### Acceptance criteria

GNSS time corruption does not change:

- vehicle distance integration
- gyro integration
- step integration
- replay event ordering
- route progress timing

### Tests

Verify a large GNSS time jump does not change deterministic propagation when GNSS position observations are otherwise identical.

---

## TASK 018B — Combined Remote Spoof Scenario

### Model
Luna

### Objective

Create reusable replay fixtures combining:

```text
remote GNSS position
GNSS time anomaly
GNSS bearing corruption
GNSS velocity corruption
```

Each component must be independently configurable.

### Canonical fixture

Create benchmark fixture:

```text
LIMA-001
```

Behavior:

```text
normal navigation
↓
remote-location spoof begins
↓
GNSS reports Lima-area position
↓
GNSS time may become inconsistent
↓
GNSS speed/bearing may remain plausible or be corrupted
↓
spoof persists
↓
plausible local GNSS returns
```

### Prohibition

No production algorithm may contain logic equivalent to:

```text
if location is Lima:
    reject GNSS
```

---

# EPIC 4 — Android GNSS Collection

---

## TASK 019 — GNSS Location Provider

### Model
Terra

### Objective

Implement live Android GNSS location collection.

### Requirements

Use Android location APIs.

Convert framework objects immediately into internal models.

Do not expose Android `Location` outside the GNSS module.

Do not treat provider validity or reported accuracy as sufficient evidence of trust.

TASK 019 must not create or redesign the Android build/module baseline. If TASK 002A is not complete, stop and report the unmet dependency.

### Tests

Mock/provider-level tests where possible.

Run Android build.

### Dependencies

- TASK 002
- TASK 002A
- TASK 003
- TASK 004

---

## TASK 020 — Raw GNSS Collector

### Model
Terra

### Objective

Implement:

```text
GnssMeasurementsEvent.Callback
```

Collect:

```text
satellite measurements
clock data
C/N0
carrier frequency
AGC where available
```

Handle devices that do not support all measurements.

### Acceptance criteria

Unsupported measurement fields must degrade gracefully.

---

## TASK 021 — GNSS Status Collector

### Model
Luna

### Objective

Collect satellite status:

```text
constellation
satellite count
used-in-fix
azimuth
elevation
C/N0
```

---

# EPIC 5 — IMU Collection

---

## TASK 022 — Gyroscope Provider

### Model
Luna

### Objective

Implement high-frequency gyroscope collection.

Default target:

```text
50 Hz
```

Configuration must permit:

```text
25
50
100 Hz
```

Do not hardcode final production rate.

---

## TASK 023 — Game Rotation Vector Provider

### Model
Terra

### Objective

Collect Android:

```text
TYPE_GAME_ROTATION_VECTOR
```

Expose orientation in a stable internal representation.

Document coordinate frame.

---

## TASK 024 — Accelerometer Provider

### Model
Luna

### Objective

Collect accelerometer samples for:

```text
step detection
motion classification
diagnostics
```

Do not implement velocity integration.

---

## TASK 025 — Barometer Provider

### Model
Luna

### Objective

Collect pressure observations where supported.

Gracefully report unavailable sensor.

---

## TASK 026 — IMU Calibration State

### Model
Terra

### Objective

Create persistent runtime calibration state for:

```text
gyro bias
phone mounting orientation
sensor stability
```

Do not implement complex estimation yet.

---

# EPIC 6 — OBD-II

---

## TASK 027 — Bluetooth OBD Transport

### Model
Terra

### Objective

Implement Bluetooth communication with an ELM327-compatible adapter.

### Requirements

Support:

```text
connect
disconnect
reconnect
command
timeout
transport error
```

Do not implement navigation logic.

---

## TASK 028 — ELM327 Initialization

### Model
Terra

### Objective

Implement initialization and adapter capability detection.

Must handle:

```text
slow clones
invalid responses
echo
line ending differences
timeouts
```

---

## TASK 029 — Vehicle Speed PID

### Model
Luna

### Objective

Implement:

```text
Mode 01 PID 0D
```

Output normalized:

```text
m/s
```

Preserve raw response for diagnostics.

---

## TASK 030 — OBD Polling Scheduler

### Model
Terra

### Objective

Poll vehicle speed without overlapping commands.

Target:

```text
5–20 Hz depending adapter
```

Measure actual:

```text
latency
poll rate
timeout rate
```

---

## TASK 031 — OBD Reconnection Strategy

### Model
Terra

### Objective

Recover from:

```text
Bluetooth drop
ELM reset
vehicle ignition restart
temporary command failures
```

Navigation must be informed explicitly if speed becomes unavailable.

---

# EPIC 7 — Offline Maps

---

## TASK 032 — MapLibre Integration

### Model
Terra

### Objective

Display a basic MapLibre map in Compose.

### Acceptance criteria

- map renders
- user can pan/zoom
- navigation marker overlay works

Do not implement navigation behavior yet.

---

## TASK 033 — Offline Region Manager

### Model
Terra

### Objective

Support downloading and managing offline regions.

Required operations:

```text
download
progress
cancel
list
delete
storage usage
```

---

## TASK 034 — Map Data Version Metadata

### Model
Luna

### Objective

Record:

```text
region
download date
map version
size
routing graph version
```

---

# EPIC 8 — Offline Routing

---

## TASK 035 — Evaluate GraphHopper Android Integration

### Model
Sol

### Objective

Investigate the safest practical architecture for offline GraphHopper routing in this repository.

Do not implement full routing.

### Required output

```text
/docs/graphhopper-architecture.md
```

Cover:

```text
Android compatibility
memory requirements
graph preparation
regional graph sizes
licensing
route calculation APIs
spatial index availability
turn restrictions
map-matching reuse potential
```

### Acceptance criteria

Make a recommendation:

```text
embed directly
adapt core
use custom extracted graph
or alternative
```

No vague recommendation.

---

## TASK 036 — Routing Graph Loader

### Model
Terra

### Objective

Implement the architecture approved in Task 035.

Load one offline regional routing graph.

---

## TASK 037 — Route Calculation

### Model
Terra

### Objective

Calculate:

```text
origin → destination
```

for vehicle mode.

Return internal `Route`.

---

## TASK 038 — Route Segment Model

### Model
Luna

### Objective

Represent route as ordered segments.

Each segment should include where available:

```text
geometry
length
heading
road/path ID
turn information
access type
```

---

## TASK 039 — Pedestrian Routing Profile

### Model
Terra

### Objective

Add pedestrian route calculation.

Ensure vehicle-only restrictions aren't incorrectly applied to pedestrian routes and vice versa.

---

# EPIC 9 — Vehicle Dead Reckoning

---

## TASK 040 — Vehicle Motion State

### Model
Luna

### Objective

Define state:

```text
position
heading
speed
gyro bias
distance
uncertainty
timestamp
```

---

## TASK 041 — Gyro Heading Propagation

### Model
Terra

### Objective

Integrate relative yaw between updates.

Must support deterministic replay.

### Tests

Synthetic:

```text
straight
90° right
90° left
180° turn
constant gyro bias
```

---

## TASK 042 — OBD Distance Integration

### Model
Terra

### Objective

Integrate vehicle speed over monotonic time.

Test variable speed.

Do not assume constant polling rate.

---

## TASK 043 — Vehicle Dead Reckoner

### Model
Sol

### Objective

Combine:

```text
OBD speed
gyro yaw
initial trusted position
initial heading
```

into a propagated vehicle state.

Do not add map matching yet.

### Tests

Synthetic known trajectories.

---

## TASK 044 — Gyro Bias Estimator

### Model
Sol

### Objective

Estimate slowly changing gyro yaw bias during trusted navigation periods.

Requirements:

- conservative updates
- freeze/reduce updates when GNSS trust is low
- expose uncertainty

---

# EPIC 10 — Route-Constrained Vehicle Matching

---

## TASK 045 — Route Progress Estimator

### Model
Terra

### Objective

Estimate:

```text
meters along planned route
```

using vehicle distance.

Initialize from a trusted route position.

---

## TASK 046 — Route Turn Detector

### Model
Terra

### Objective

Compare observed gyro turns with route geometry.

Generate events such as:

```text
expected right turn
observed right turn
turn mismatch
```

---

## TASK 047 — Route Candidate State

### Model
Luna

### Objective

Create representation for multiple plausible route positions.

Fields:

```text
segment
distance along segment
total route distance
heading
probability
uncertainty
```

---

## TASK 048 — Route-Constrained Hypothesis Tracker

### Model
Sol

### Objective

Maintain multiple plausible route-progress candidates.

Use:

```text
distance traveled
turn observations
segment heading
route topology
```

Do not use GNSS yet.

---

## TASK 049 — Vehicle Route Matcher Benchmark

### Model
Terra

### Objective

Replay known route with GNSS removed and generate:

```text
correct segment %
route progress error
wrong turn count
confidence
```

---

# EPIC 11 — GNSS Trust

---

## TASK 050A — Last Trusted Position Anchor

### Model
Terra

### Objective

Create an explicit representation of the most recent navigation state established while GNSS was sufficiently trustworthy.

Suggested model:

```kotlin
data class TrustedPositionAnchor(
    val position: GeoPoint,
    val timestamp: MonotonicTimestamp,
    val headingDegrees: Double?,
    val routeProgressMeters: Double?,
    val navigationMode: NavigationMode,
    val confidence: Double
)
```

Exact fields may adapt to existing project models.

### Design rationale

If a GNSS observation suddenly teleports to a remote location, the navigation system needs a stable local origin from which DR/PDR can continue. The last trusted anchor provides that origin.

### Requirements

- update anchor only when trust policy allows it
- rejected GNSS never replaces anchor
- questionable GNSS does not replace anchor unless explicitly permitted by policy

### Acceptance criteria

Remote GNSS observations cannot move the trusted anchor.

### Tests

```text
trusted → trusted: anchor updates
trusted → questionable: policy followed
trusted → rejected: anchor unchanged
trusted local → Lima spoof: anchor remains local
```

---

## TASK 050B — Geographic Continuity Evidence

### Model
Terra

### Objective

Generate GNSS trust evidence based on whether a new position is physically reachable from the last trusted navigation state.

### Inputs

Use where available:

```text
last trusted position
new GNSS position
monotonic elapsed time
navigation mode
recent speed estimate
position uncertainty
```

### Design rationale

This detector provides a generic physical consistency test. It rejects impossible transitions because of displacement and elapsed time, not because of destination coordinates.

### Requirements

Different motion modes may use configurable conservative envelopes:

```text
PEDESTRIAN
VEHICLE
UNKNOWN
```

Thresholds must be configurable and documented.

Output `GnssTrustEvidence`; do not directly modify navigation position.

### Tests

```text
pedestrian + 5 m movement → plausible
vehicle + 30 m movement → plausible
stationary + small GNSS noise → plausible
local → Lima in 1 s → impossible
100 km jump in 1 s → impossible
moderate movement over sufficient elapsed time → evaluated correctly
```

---

## TASK 050C — Hard Innovation Gate

### Model
Sol

### Objective

Add a conservative safety boundary preventing catastrophically implausible GNSS observations from contaminating estimator state before the full trust state machine reacts.

### Concept

```text
GNSS observation
      ↓
geographic continuity / innovation check
      ↓
catastrophically impossible?
  YES                 NO
   ↓                   ↓
block correction     normal trust evaluation
```

### Design rationale

Trust aggregation may intentionally be gradual and hysteretic. A continental teleport should not get even one estimator update simply because the state machine needs multiple samples to transition.

### Requirements

The GNSS sample must still be:

- recorded
- visible in diagnostics
- passed to trust analysis

Only state correction is blocked.

The gate must account for uncertainty and use deliberately conservative configurable thresholds.

### Tests

Include:

```text
normal GNSS noise
normal vehicle movement
normal pedestrian movement
tunnel exit correction
large urban multipath error
100 km teleport
local → Lima teleport
```

False hard rejection of plausible observations is a test failure.

---

## TASK 050 — GNSS Trust Framework

### Model
Sol

### Objective

Create:

```text
GnssTrustEngine
GnssTrustEvidence
GnssTrustScore
```

Trust output range:

```text
0.0–1.0
```

Evidence must remain inspectable.

Do not hide trust reasoning inside one opaque scalar function.

---

## TASK 051 — Position Jump Evidence

### Model
Terra

### Objective

Detect GNSS displacement incompatible with elapsed time and estimated vehicle/pedestrian movement.

Output evidence score.

Do not reject GNSS directly.

---

## TASK 052 — GNSS vs OBD Speed Evidence

### Model
Terra

### Objective

Compare GNSS speed and OBD speed over a rolling time window.

Handle:

```text
stops
low speeds
OBD dropout
GNSS velocity unavailable
```

---

## TASK 053 — OBD Distance vs GNSS Distance

### Model
Sol

### Objective

Implement rolling displacement consistency analysis.

Compare:

```text
integrated OBD distance
vs
GNSS trajectory distance
```

Use configurable windows.

Avoid false positives around curves.

---

## TASK 053A — Independent Motion vs Remote GNSS Evidence

### Model
Sol

### Objective

Strengthen trust evaluation for cases where GNSS remains internally plausible but disagrees with independent local motion.

Compare rolling-window evidence from:

```text
OBD distance
gyro turns
PDR distance
PDR turns
route progress
```

against the GNSS trajectory.

### Design rationale

A spoofed signal may report believable speed, bearing, accuracy, satellite status, and a smooth trajectory. Internal GNSS consistency therefore does not prove physical consistency.

### Vehicle example

```text
OBD: 172 m travelled
gyro: one right turn
route: local urban road
GNSS: remote false trajectory
```

### Pedestrian example

```text
steps: 143
estimated distance: ~100 m
observed turn: left
GNSS: trajectory inconsistent with local movement
```

### Acceptance criteria

Detection does not depend on GNSS reporting an invalid fix.

---

## TASK 054 — GNSS Heading vs Motion Evidence

### Model
Terra

### Objective

Compare:

```text
GNSS bearing
gyro heading change
road heading
```

Only evaluate when motion is sufficient to make bearing meaningful.

---

## TASK 055 — GNSS vs Route Evidence

### Model
Terra

### Objective

Score whether GNSS location is compatible with:

```text
current route
reachable route progress
current hypotheses
```

---

## TASK 056 — C/N0 Feature Extractor

### Model
Terra

### Objective

Extract constellation-level statistics:

```text
mean
median
variance
sudden synchronized change
satellite count
```

Do not implement spoof conclusions.

---

## TASK 057 — AGC Feature Extractor

### Model
Terra

### Objective

Extract AGC patterns where device hardware exposes AGC.

Gracefully disable feature if unsupported.

---

## TASK 058 — GNSS Trust Aggregation V1

### Model
Sol

### Objective

Combine current trust evidence into an explainable score.

Inputs may include:

```text
position jump
geographic continuity
vehicle speed mismatch
distance mismatch
independent motion mismatch
heading mismatch
route mismatch
C/N0 indicators
AGC indicators
GNSS time anomalies
```

### Requirements

Every score must expose contribution of each signal.

---

## TASK 059 — GNSS Trust State Machine

### Model
Sol

### Objective

Create states:

```text
TRUSTED
QUESTIONABLE
REJECTED
RECOVERING
```

Avoid rapid oscillation.

Implement hysteresis and sustained-observation requirements.

---

## TASK 059A — GNSS Time Anomaly Evidence

### Model
Terra

### Objective

Detect suspicious GNSS-derived time changes without allowing them to affect sensor-fusion timing.

### Evidence examples

```text
large sudden GNSS time jump
GNSS time moving backward
GNSS time inconsistent with recent GNSS clock state
large unexpected GNSS/system-time disagreement
```

### Critical requirement

GNSS time anomalies produce trust evidence only.

They must never replace monotonic timing used by:

```text
fusion
dead reckoning
OBD integration
IMU integration
PDR
replay
```

### Acceptance criteria

TASK 018A corruption changes trust diagnostics but not propagation timing.

---

# EPIC 12 — Hybrid Vehicle Navigation

---

## TASK 060A — Trusted Anchor Propagation Start

### Model
Sol

### Objective

Ensure dead reckoning transitions from the last trusted navigation state rather than from a newly rejected GNSS observation.

### Required behavior

```text
trusted local position
↓
GNSS questionable
↓
GNSS remote teleport
↓
GNSS rejected
↓
DR continues from trusted local state
```

### Forbidden behavior

```text
trusted local
↓
GNSS Lima
↓
navigation state becomes Lima
↓
DR starts in Lima
```

### Acceptance criteria

`LIMA-001` never initializes dead reckoning from the false remote location.

---

## TASK 060 — GNSS Correction Interface

### Model
Terra

### Objective

Allow trusted GNSS to correct dead-reckoned state.

Correction weight depends on:

```text
GNSS trust
GNSS accuracy
current DR uncertainty
```

The hard innovation gate must run before any correction is applied.

---

## TASK 061 — GNSS Rejection Integration

### Model
Sol

### Objective

When trust falls below configured threshold:

```text
stop strong GNSS corrections
continue DR
continue route matching
increase uncertainty
```

GNSS samples must still be recorded.

Rejected samples must not update the trusted anchor.

---

## TASK 062 — GNSS Recovery Integration

### Model
Sol

### Objective

Implement gradual re-entry of GNSS after rejection.

Must not snap immediately to first candidate fix.

Remote spoof recovery must require sustained consistency with the local DR/map-matched state.

---

## TASK 063 — Vehicle Position Confidence

### Model
Sol

### Objective

Calculate position uncertainty based on:

```text
time since trusted GNSS
distance since trusted GNSS
gyro uncertainty
OBD availability
route ambiguity
candidate probability separation
```

---

# EPIC 13 — Pedestrian Foundation

---

## TASK 064 — Step Detector

### Model
Terra

### Objective

Detect walking steps using accelerometer data.

Requirements:

- configurable thresholds
- refractory period
- walking cadence bounds
- replay compatibility

Do not estimate distance.

---

## TASK 065 — Step Detector Benchmark

### Model
Terra

### Objective

Measure:

```text
true steps
detected steps
false steps
missed steps
```

across available recordings.

---

## TASK 066 — Basic Stride Model

### Model
Terra

### Objective

Estimate step length using:

```text
default user profile
cadence
acceleration characteristics
```

Return:

```text
estimated length
uncertainty
```

---

## TASK 067 — GNSS Stride Calibration

### Model
Sol

### Objective

During trusted GNSS walking periods, calibrate:

```text
distance per step
cadence relationship
```

Freeze calibration when GNSS trust becomes questionable.

Remote-spoofed GNSS must never calibrate stride length.

---

# EPIC 14 — Pedestrian Heading

---

## TASK 068 — Pedestrian Heading State

### Model
Luna

### Objective

Create separate heading representation for:

```text
device orientation
pedestrian movement direction
```

Never treat them as equivalent.

---

## TASK 069 — Basic Gyro Turn Detector

### Model
Terra

### Objective

Detect major pedestrian turns.

Initial categories:

```text
STRAIGHT
LEFT
RIGHT
U_TURN
UNKNOWN
```

Focus on relative turns rather than absolute compass heading.

---

## TASK 070 — Phone Pose Classifier V1

### Model
Sol

### Objective

Build basic pose classification.

Initial states:

```text
HAND_FORWARD
HAND_SWINGING
POCKET
UNKNOWN
```

Do not target perfect classification.

Output probability/confidence.

---

## TASK 071 — Pedestrian Heading Estimator V1

### Model
Sol

### Objective

Combine:

```text
gyro
pose state
trusted GNSS course when available
route direction
```

into estimated pedestrian heading.

Magnetometer must remain a low-confidence optional input.

---

# EPIC 15 — Pedestrian Dead Reckoning

---

## TASK 072 — Pedestrian Motion State

### Model
Luna

### Objective

Represent:

```text
step count
distance
heading
stride estimate
stride uncertainty
heading uncertainty
pose confidence
timestamp
```

---

## TASK 073 — Pedestrian Dead Reckoner

### Model
Sol

### Objective

Propagate position from:

```text
steps
stride
pedestrian heading
```

No map matching yet.

PDR must initialize from the last trusted local navigation anchor when GNSS is rejected.

---

## TASK 074 — Pedestrian Route Progress

### Model
Terra

### Objective

Estimate distance along planned walking route.

---

## TASK 075 — Pedestrian Turn/Route Matching

### Model
Sol

### Objective

Use observed walking turns to confirm expected route branches.

Maintain multiple candidates around ambiguous intersections.

---

## TASK 076 — Pedestrian Free-Space State

### Model
Sol

### Objective

Add ability for the matcher to represent:

```text
NOT CURRENTLY MATCHED TO PATH
```

Do not force user position onto an OSM path when measurements disagree strongly.

---

## TASK 077 — Pedestrian Confidence Model

### Model
Sol

### Objective

Confidence factors:

```text
stride uncertainty
heading uncertainty
phone pose
path ambiguity
distance since GNSS
step count
map quality
```

---

# EPIC 16 — Barometric Assistance

---

## TASK 078 — Relative Altitude Estimator

### Model
Terra

### Objective

Convert barometric pressure changes into relative altitude change.

Focus on:

```text
relative change
```

not absolute elevation.

---

## TASK 079 — Stairs/Elevation Event Detector

### Model
Terra

### Objective

Detect sustained:

```text
UP
DOWN
LEVEL
```

movement.

---

# EPIC 17 — Navigation Mode Switching

---

## TASK 080 — Motion Classification Framework

### Model
Terra

### Objective

Represent probabilities for:

```text
VEHICLE
PEDESTRIAN
STATIONARY
UNKNOWN
```

---

## TASK 081 — Vehicle-to-Pedestrian Transition

### Model
Sol

### Objective

Detect likely transition using:

```text
OBD reaches 0
OBD disconnect
step sequence begins
```

Preserve position continuity.

Do not automatically transition based only on Bluetooth disconnect.

---

## TASK 082 — Pedestrian-to-Vehicle Transition

### Model
Sol

### Objective

Detect likely transition from walking to vehicle movement.

Avoid triggering when:

```text
walking stops
user enters public transit
Bluetooth reconnects accidentally
```

Initial MVP may require user confirmation.

---

# EPIC 18 — Navigation UI

---

## TASK 083 — Navigation Map Screen

### Model
Terra

### Objective

Display:

```text
route
estimated position
movement direction
destination
```

---

## TASK 084 — Position Source Indicator

### Model
Luna

### Objective

Show:

```text
GNSS
HYBRID
VEHICLE DR
PEDESTRIAN DR
```

---

## TASK 085 — GNSS Trust Indicator

### Model
Luna

### Objective

Expose simple state:

```text
GNSS NORMAL
GNSS QUESTIONABLE
GNSS UNTRUSTED
GNSS RECOVERING
```

Do not expose raw technical diagnostics in normal navigation view.

---

## TASK 086 — Position Uncertainty UI

### Model
Terra

### Objective

Visualize uncertainty.

Possible implementation:

```text
accuracy circle
soft route-progress range
confidence label
```

Do not show an exact blue dot when confidence is LOW.

A remote spoofed GNSS coordinate must never cause the visible navigation marker to jump before trust evaluation.

---

## TASK 087 — Diagnostics Screen

### Model
Terra

### Objective

Display development diagnostics:

```text
GNSS trust
trust evidence
hard innovation gate status
trusted anchor
OBD rate
vehicle speed
gyro yaw rate
step cadence
route candidate probabilities
position uncertainty
last trusted GNSS
GNSS time anomaly evidence
```

---

# EPIC 19 — Benchmark Platform

---

## TASK 088 — Benchmark Scenario Format

### Model
Luna

### Objective

Define scenario configuration.

Example:

```yaml
session: UA-KH-014
route: route-014

corruption:
  type: gradual_drift
  start: 270
  duration: 420
```

Scenario format must support combined position/time/speed/bearing corruption.

---

## TASK 089 — Benchmark Metrics Engine

### Model
Terra

### Objective

Calculate:

```text
correct route %
mean progress error
max progress error
wrong branches
GNSS rejection delay
GNSS false rejection
recovery time
low confidence duration
maximum spoof-induced navigation displacement
trusted-anchor movement during rejection
```

---

## TASK 090 — Benchmark CLI

### Model
Terra

### Objective

Allow:

```text
./gradlew runNavigationBenchmark
```

or equivalent.

Input:

```text
session
scenario
algorithm config
```

Output machine-readable and human-readable results.

---

## TASK 091 — Benchmark Regression Thresholds

### Model
Terra

### Objective

Allow CI to fail if algorithm performance deteriorates beyond configured tolerance.

Example:

```text
wrong branches > 0 → FAIL
correct route < 95% → FAIL
mean progress error increases >20% → FAIL
```

Thresholds must remain configurable.

---

## TASK 091A — Canonical Remote Spoof Benchmark

### Model
Terra

### Objective

Add `LIMA-001` as a canonical regression benchmark.

### Design rationale

This reproduces a known remote-location failure pattern but validates generic physical-consistency defenses rather than location-specific logic.

### Scenario

```text
trusted local navigation
↓
remote GNSS spoof begins
↓
GNSS reports Lima-area position
↓
independent local motion continues
↓
spoof persists
↓
plausible local GNSS returns
```

Run against vehicle and pedestrian recordings when both datasets become available.

### Required metrics

```text
did navigation position enter spoof area?
time to QUESTIONABLE
time to REJECTED
maximum spoof-induced navigation displacement
route progress error
wrong route branches
dead-reckoning continuity
GNSS recovery time
trusted-anchor movement during spoof
```

### Mandatory acceptance criteria

```text
Navigation enters Lima: NO
Trusted anchor enters Lima: NO
DR initialized from spoof position: NO
Monotonic propagation timing corrupted: NO
GNSS samples remain recorded: YES
```

---

## TASK 091B — Remote Spoof Matrix Benchmark

### Model
Terra

### Objective

Prevent overfitting specifically to the Lima fixture.

Run equivalent remote-location corruptions using multiple displacement classes:

```text
10 km
50 km
500 km
1000+ km
different country
different continent
```

### Acceptance criteria

Results are governed by physical consistency and uncertainty, not target coordinates.

---

## TASK 091C — Near-Spoof Benchmark

### Model
Sol

### Objective

Test the harder case where false GNSS remains geographically near the real position.

### Scenarios

```text
50 m displacement
100 m displacement
250 m displacement
500 m displacement
1 km displacement
```

Include:

```text
instant offset
gradual drift
parallel-road displacement
plausible-speed drift
```

### Design rationale

A remote teleport is easy to detect. Nearby gradual spoofing is more likely to challenge route matching and fusion because the false trajectory may remain physically plausible for significant periods.

TASK 091C is MVP+ and should not block the first interference prototype, but it is required before claiming general spoof robustness.

---

# EPIC 20 — Field Test Tooling

---

## TASK 092 — Start/Stop Recording UI

### Model
Luna

### Objective

Allow a developer to manually record test drives/walks.

Display:

```text
recording status
duration
sensor availability
storage used
```

---

## TASK 093 — Field-Test Marker Button

### Model
Luna

### Objective

Add manual marker:

```text
MARK EVENT
```

Store timestamp.

Useful for marking:

```text
known GPS failure
suspected spoof event
intersection
wrong navigation
route deviation
```

---

## TASK 094 — Session Export

### Model
Luna

### Objective

Export recorded session as a portable file.

No automatic network upload.

---

# EPIC 21 — Performance

---

## TASK 095 — Sensor Rate Profiling

### Model
Terra

### Objective

Measure CPU and battery impact of:

```text
25 Hz
50 Hz
100 Hz
```

IMU operation.

Document results.

---

## TASK 096 — Map Matching Rate Profiling

### Model
Terra

### Objective

Compare:

```text
1 Hz
2 Hz
5 Hz
10 Hz
```

map matching.

Measure:

```text
CPU
latency
accuracy
battery
```

---

## TASK 097 — OBD Polling Performance

### Model
Terra

### Objective

Measure actual usable speed polling rate across adapters.

Track:

```text
mean response time
p95
timeouts
disconnects
```

---

# EPIC 22 — Reliability

---

## TASK 098 — Navigation Foreground Service

### Model
Terra

### Objective

Move active navigation/sensor collection into an Android foreground service where required.

Ensure UI lifecycle does not terminate navigation state.

---

## TASK 099 — App Background/Resume Test

### Model
Luna

### Objective

Verify:

```text
screen off
app background
screen rotation
activity recreation
```

do not corrupt navigation state.

---

## TASK 100 — Process Restart Recovery

### Model
Terra

### Objective

Persist enough session/navigation state to recover gracefully after Android process termination.

Do not blindly resume dead reckoning from stale state without checking monotonic/session timestamps.

Do not restore a previously rejected GNSS coordinate as a trusted anchor.

---

# EPIC 23 — Hardware Compatibility

---

## TASK 101 — Sensor Capability Report

### Model
Luna

### Objective

At application startup collect device capability information:

```text
gyro
accelerometer
game rotation vector
magnetometer
barometer
raw GNSS
AGC
carrier frequencies
Android version
device model
```

---

## TASK 102 — Capability-Based Feature Flags

### Model
Terra

### Objective

Disable unsupported algorithms gracefully.

Example:

```text
AGC unsupported
→ AGC trust evidence unavailable
→ confidence computation adapts
```

Do not treat missing hardware as anomalous behavior.

---

# EPIC 24 — Test Dataset

---

## TASK 103 — Dataset Metadata Format

### Model
Luna

### Objective

Every recorded session should have metadata:

```text
session ID
date
region
vehicle/pedestrian
phone model
OBD adapter
route
conditions
known anomalies
ground truth availability
```

---

## TASK 104 — Dataset Validation Tool

### Model
Terra

### Objective

Detect invalid recordings:

```text
timestamp reversal
large data gaps
missing GNSS
missing IMU
invalid OBD
truncated file
```

---

## TASK 104A — Interference Event Metadata

### Model
Luna

### Objective

Extend dataset metadata to describe suspected, observed, or injected GNSS interference.

Suggested fields:

```text
interferenceObserved
interferenceType
eventStart
eventEnd
reportedFalseLocation
GNSS time anomaly
device behavior
manual notes
classification confidence
```

### Classification values

Support at least:

```text
UNKNOWN
SUSPECTED_JAMMING
SUSPECTED_SPOOFING
KNOWN_TEST_INJECTION
NORMAL
```

### Design rationale

An abnormal navigation trace alone is insufficient evidence to claim spoofing. Dataset metadata must preserve uncertainty about event classification.

---

# EPIC 25 — First Integrated Milestones

---

## MILESTONE A — Recorder

Required tasks:

```text
001–031
including 002A
092–094
101
```

Success criteria:

Android application can record:

```text
GNSS
raw GNSS
gyro
rotation vector
accelerometer
barometer
OBD speed
```

to a replayable session.

The recorded model keeps monotonic timing distinct from GNSS/wall-clock time.

---

## MILESTONE B — Offline Navigation Base

Required tasks:

```text
032–039
083
```

Success criteria:

App can:

```text
download maps
calculate route
display route
operate offline
```

---

## MILESTONE C — Vehicle GPS-Loss MVP

Required tasks:

```text
040–049
060
063
088–090
```

Success criteria:

A replay test can:

```text
begin with GNSS
remove GNSS
continue estimating progress
remain on planned route
```

Target:

```text
5 minutes GNSS loss
>95% correct route branch
typical route progress error <50 m
```

---

## MILESTONE D — GNSS Interference MVP

Required tasks:

```text
015
016
016A
017
018
018A
018B

050A
050B
050C
050–059
059A

060A
060–063

088–091
091A
091B
```

TASK 091C is MVP+ and is not a blocker for the first interference prototype.

### Success criteria

System must handle:

```text
GNSS outage
large local jump
remote geographic teleport
Lima-style remote spoof fixture
gradual GNSS drift
false GNSS velocity
false GNSS bearing
GNSS time anomaly
return of plausible local GNSS
```

A catastrophically implausible GNSS observation must not become navigation state before trust evaluation/rejection completes.

Mandatory remote-spoof behavior:

```text
navigation remains local
trusted anchor remains local
DR/PDR origin remains local
monotonic fusion timing remains stable
spoof samples remain logged for diagnostics
```

---

## MILESTONE E — Pedestrian MVP

Required tasks:

```text
064–077
039
```

Success criteria:

Known walking route can continue during GNSS loss.

Initial target:

```text
500 m minimum
>90% correct route branch
```

Pedestrian navigation must continue from the last trusted local anchor when GNSS becomes rejected.

---

## MILESTONE F — Multimodal MVP

Required tasks:

```text
078–087
080–082
```

Success criteria:

A route may transition:

```text
vehicle
↓
park
↓
pedestrian
```

without losing navigation continuity.

---

# Recommended First Codex Execution Sequence

Do not start with sensor fusion.

The recommended first execution queue is:

```text
001 Repository audit

002 Module structure

002A Android app build baseline

003 Monotonic timestamp

004 GNSS models

005 Raw GNSS models

006 IMU models

007 OBD model

008 Navigation models

009 Session format

010 Session writer

011 Session reader

012 Replay clock

013 Replay engine

019 GNSS provider

020 Raw GNSS collector

022 Gyro provider

023 Game rotation provider

024 Accelerometer provider

027 OBD transport

028 ELM327 initialization

029 Vehicle speed PID

092 Recording UI

094 Session export
```

At this point stop feature development temporarily.

Record real data.

The next major decisions should use actual Android sensor traces rather than assumptions.

After the base replay engine exists, implement spoof simulation before GNSS trust work:

```text
015 GNSS outage
016 GNSS jump
016A Remote location spoof
017 Gradual drift
018 Velocity/bearing corruption
018A GNSS time corruption
018B Combined remote spoof fixture
```

Then proceed to the trust epic.

---

# First Important Gate

Before implementing Task 043 `Vehicle Dead Reckoner`, collect at least:

```text
3 short urban drives
1 highway drive
1 stop-and-go drive
```

Each should record:

```text
GNSS
raw GNSS
OBD speed
gyro
rotation vector
accelerometer
```

Recommended duration:

```text
10–30 minutes each
```

The dataset becomes the input for all subsequent algorithm work.

---

# Pedestrian Gate

Before implementing Task 070 `Phone Pose Classifier`, record walking sessions with:

```text
phone held forward
phone swinging
front trouser pocket
jacket pocket
```

Include:

```text
straight walking
90° turns
180° turns
stops
stairs if available
```

Do not train/tune pose logic exclusively from synthetic sensor data.

---

# Remote-Spoof Gate

Before claiming Milestone D complete, run at minimum:

```text
LIMA-001
remote spoof matrix
large local jump
gradual drift
GNSS time anomaly
```

Passing only `LIMA-001` is insufficient because continental teleports are easier to detect than nearby plausible spoof trajectories.

---

# Canonical LIMA-001 State Transition

Expected behavior:

```text
START

GNSS TRUSTED
navigation local

↓

remote spoof starts

↓

GNSS reports Lima-area position

↓

geographic continuity:
FAIL

↓

hard innovation gate:
BLOCK GNSS CORRECTION

↓

GNSS trust:
QUESTIONABLE
→
REJECTED

↓

trusted anchor:
UNCHANGED

↓

VEHICLE:
OBD + gyro + route DR

or

PEDESTRIAN:
steps + gyro + route PDR

↓

navigation remains local

↓

plausible local GNSS returns

↓

RECOVERING

↓

sustained consistency

↓

HYBRID

↓

TRUSTED
```

Mandatory assertions:

```text
navigation_position_remote == false
trusted_anchor_remote == false
spoof_used_as_dr_origin == false
monotonic_timing_corrupted == false
gnss_samples_recorded == true
```

---

# Completed Task Review Policy

Do not automatically rerun completed tasks after threat-model changes.

For each completed task ask:

```text
Does the new requirement change the task's
public model, interface, persistence format, or timing assumptions?

YES → review affected implementation
NO  → leave completed task untouched
```

Potential review candidates for the remote-spoof update:

```text
003 Monotonic Timestamp
004 GNSS Sample Model
005 Raw GNSS Models
009 Sensor Session Format
010 Session Writer
011 Session Reader
012 Replay Clock
013 Replay Engine
```

Review does not mean reimplementation.

Confirm only that:

```text
GNSS time is separate from monotonic time
raw observations are preserved
replay can modify GNSS independently of IMU/OBD timestamps
session format can represent test GNSS-time corruption
```

If these conditions already hold:

```text
NO CHANGE REQUIRED
```

---

# Credit-Saving Rules

## Rule 1

Never ask Codex to inspect the entire repository unless architecture investigation explicitly requires it.

Provide the module and files relevant to the task.

---

## Rule 2

Use Luna for mechanical work.

Examples:

```text
models
serialization
unit tests
basic UI
simple Android wrappers
```

---

## Rule 3

Use Terra for integration work.

Examples:

```text
Bluetooth
GNSS APIs
MapLibre
GraphHopper
replay framework
```

---

## Rule 4

Reserve Sol for algorithmic uncertainty.

Examples:

```text
GNSS trust
dead reckoning
map matching
particle filtering
pose estimation
difficult regressions
```

---

## Rule 5

Do not ask Codex to "improve" an algorithm without providing a failing test or replay.

Instead:

```text
Scenario UA-KH-014 fails.

Expected:
0 wrong route branches.

Actual:
1 wrong branch at timestamp 437.2 sec.

Investigate the cause.

Do not change GNSS trust logic.

Allowed modules:
matching/
fusion/

Add regression test before fixing.
```

This pattern dramatically reduces unbounded exploratory work.

---

# Standard Codex Task Prompt Template

Use this template when submitting backlog tasks:

```text
Read /AGENTS.md first.

Implement TASK XXX from /CODEX_BACKLOG.md.

Scope:
[task scope]

Allowed modules:
[module list]

Do not modify:
[restricted modules]

Requirements:
[requirements]

Acceptance criteria:
[criteria]

Tests required:
[tests]

Before finishing:
1. Build affected modules.
2. Run relevant tests.
3. Report any failures.
4. Do not silently change shared interfaces.
5. Summarize files changed.

If a required architectural change is outside this task's scope,
do not implement it. Document the required change instead.
```

---

# Bug Investigation Prompt Template

```text
Read /AGENTS.md.

Investigate this failure:

Scenario:
[scenario ID]

Expected:
[expected result]

Actual:
[actual result]

First reproduce the problem.

Do not make changes before identifying the failing subsystem.

Allowed modules:
[...]

Forbidden modules:
[...]

After reproduction:

1. identify likely root cause
2. add a regression test
3. implement the smallest fix
4. rerun affected tests
5. rerun the supplied replay scenario
6. report benchmark before/after
```

---

# Algorithm Experiment Prompt Template

```text
Read /AGENTS.md.

This is an experiment, not a production change.

Hypothesis:
[algorithm hypothesis]

Dataset:
[list of sessions]

Baseline:
[current benchmark]

Implement the experiment behind a feature/config flag.

Do not remove the baseline algorithm.

Run the full supplied benchmark set.

Report:

baseline metrics
experimental metrics
per-session differences
regressions
CPU/runtime difference

Do not make the experiment the default unless every acceptance
criterion is met.
```

---

# Remote-Spoof Impact Audit Prompt

Use this if tasks were completed before the remote-spoof requirements were added:

```text
Read AGENTS.md and CODEX_BACKLOG.md.

Review the Remote GNSS Spoofing requirements.

Do not implement new features yet.

Inspect all tasks that have already been completed and determine
whether the new requirements invalidate or require changes to any
existing implementation.

Specifically verify:

1. sensor fusion uses monotonic timestamps;
2. GNSS-derived time is stored separately;
3. replay can corrupt GNSS independently of IMU and OBD;
4. rejected GNSS cannot overwrite trusted navigation state;
5. no component assumes Android GNSS fixes are always trustworthy.

Produce:

docs/remote-spoof-impact-audit.md

For every completed task classify it:

NO CHANGE
MINOR CHANGE
REQUIRES UPDATE

Explain why.

Do not modify production code during this audit.
```

---

# Final Principle

The project should be developed as:

```text
MEASURE
↓
REPLAY
↓
BENCHMARK
↓
CHANGE
↓
REPLAY AGAIN
```

not:

```text
WRITE ALGORITHM
↓
DRIVE AROUND
↓
IT LOOKS OK
```

For GNSS integrity specifically:

```text
GNSS
↓
Is this observation consistent with independently observed movement?
↓
YES                     NO
↓                       ↓
weighted fusion         distrust
                        ↓
                        DR/PDR from trusted anchor
```

Codex should optimize against deterministic evidence whenever possible.
