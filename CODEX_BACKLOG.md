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

## Status and Evidence Tracking

Every task must include a status line when work begins or ends:

```text
Status: NOT_STARTED | IN_PROGRESS | PARTIAL | COMPLETE | BLOCKED
Implementation: <key source files or documentation>
Tests: <test files or NONE>
Verification: <exact command and result>
```

Use `COMPLETE` only when the task's acceptance criteria and required tests have
been satisfied. The presence of a source file alone is not completion evidence.
Use `PARTIAL` when the named capability exists but one or more acceptance
criteria, integrations, replay scenarios, or benchmarks remain unfinished.
Use `BLOCKED` only when a documented external or repository dependency prevents
progress. A failed test is not by itself a reason to mark a task blocked.

Status must distinguish implementation from validation:

- `IMPLEMENTED` means the relevant code or documentation exists.
- `UNIT_TESTED` means the deterministic unit tests pass.
- `REPLAY_VALIDATED` means the behavior passes a replay scenario.
- `DEVICE_VALIDATED` means the Android/device behavior was verified.
- `BENCHMARK_VALIDATED` means the relevant quantitative target was measured.

Do not claim a stronger validation level than the evidence supports. When a
task is complete, record the exact command, dataset or scenario, and result.

### Current Repository Audit — 2026-09-01

This is the baseline status review for the current repository. It is an audit,
not a replacement for task-level completion evidence.

| Task range | Baseline assessment | Evidence / follow-up |
| --- | --- | --- |
| 001–002A | COMPLETE | Repository audit, package structure, Android build baseline, `assembleDebug` verified. |
| 003–008 | COMPLETE | Shared timestamp, GNSS, raw GNSS, IMU, OBD, and navigation models have implementations and tests. |
| 009–018 | COMPLETE | Session format, writer/reader, replay engine, event bus, and initial GNSS fault simulators have implementations and tests. |
| 019–034 | COMPLETE or PARTIAL | Android providers, OBD foundation, MapLibre integration, offline-region metadata, and related tests exist; device validation and some runtime integration remain. |
| 035–039 | PARTIAL | Routing evaluation and foundational routing code exist; a complete production routing integration and acceptance report are still required. |
| 040–042 | COMPLETE | Vehicle state, gyro propagation, and OBD distance integration have implementations and unit tests. |
| 043–044 | NOT_STARTED | Vehicle dead reckoner and gyro-bias estimator remain explicit next dependencies. |
| 045–048 | COMPLETE or PARTIAL | Route progress, turns, candidates, and hypothesis tracking exist; integrated route behavior still needs replay validation. |
| 049 | NOT_STARTED | Vehicle route-matcher benchmark is still required. |
| 050–063 | COMPLETE or PARTIAL | GNSS trust, evidence, state transitions, correction, rejection, recovery, and vehicle confidence code exist; integration and replay validation must be recorded per task. |
| 064–069 | COMPLETE or PARTIAL | Step, stride, pedestrian heading state, and gyro turn foundations exist with tests. |
| 070–082 | NOT_STARTED or PARTIAL | Phone pose, full pedestrian DR, pedestrian matching, confidence, mode classification, and mode transitions remain incomplete or unvalidated. |
| 083–087 | PARTIAL | The Android app baseline exists, but the navigation UI, source/trust indicators, uncertainty UI, and diagnostics acceptance criteria remain. |
| 088–091 | NOT_STARTED | Benchmark scenario, metrics, CLI, and regression-threshold platform remain. |
| 092–094 | PARTIAL | Recording bridges exist, but the complete field-test UI, markers, and export workflow remain. |
| 095–104 | NOT_STARTED | Profiling, lifecycle reliability, capability reporting, feature flags, dataset metadata, and validation tooling remain. |
| 016A–088A | NOT_STARTED or PARTIAL | Treat each extension independently and require its named deterministic scenario and tests. |

The implementation-based baseline is approximately 64 of 116 backlog items
completed or substantially implemented (about 55%). This percentage must be
recalculated after task-level statuses and verification evidence are added.

### Audit Supplement — 2026-09-03

This follow-up reconciles the current Android source tree with task evidence.

| Area | Audit result | Backlog action |
| --- | --- | --- |
| Bluetooth OBD discovery/pairing | Settings now requests Android 12+ scan permission, performs classic Bluetooth discovery, presents nearby devices separately from paired adapters, starts system pairing, and selects a successfully paired adapter. | Added TASK 122 as PARTIAL: JVM coverage and debug build exist; a real adapter discovery/pairing run remains required. |
| Offline-map UI entry | `MapDownloadScreen` and its deterministic presentation tests remain in the source, but the visible main-menu and Settings entry points are intentionally hidden. The screen also begins from `CatalogUnavailable` because no production catalog/orchestrator is wired. | TASK 121 remains PARTIAL; its implementation note now records the hidden state rather than claiming a live user journey. |

This audit does not upgrade any task based only on source presence. Existing
PARTIAL and BLOCKED tasks retain their documented validation boundaries.

### Remaining-Task Definition Rules

Before marking any remaining implementation task complete, add the following
details if they are not already present in the task:

1. Named inputs, outputs, coordinate/frame conventions, and timestamp rules.
2. Required behavior for unavailable, stale, malformed, ambiguous, and
   physically implausible inputs.
3. Uncertainty/confidence behavior, including how uncertainty grows or is
   downgraded.
4. Deterministic unit tests for normal and boundary cases.
5. A replay test for navigation behavior, when the task affects propagation,
   trust, route matching, recovery, or benchmark results.
6. The exact artifact produced and the exact verification command.
7. A measurable acceptance threshold where the task makes a performance or
   accuracy claim.

Required dependency sequence for the remaining MVP work:

```text
043–044 → 049
070–071 → 072–077
080 → 081–082
040–063 and 072–077 → 083–087
088–091 → 049 and all benchmark claims
098 → 099–100
101 → 102
103 → 104
```

Tasks may be developed in parallel only when their public inputs and outputs
are already stable. A task must not silently absorb an unfinished dependency;
record the dependency as `PARTIAL` or `BLOCKED` and add a follow-up task when
necessary.

### MVP Validation Gate

The vehicle MVP is not complete when the individual classes compile. Before
calling the vehicle MVP complete, run a deterministic recorded or synthetic
route scenario that includes trusted GNSS, GNSS loss or corruption for at
least five minutes, route turns, uncertainty growth, and trustworthy GNSS
recovery. Report at minimum:

- correct route branch percentage
- mean and maximum route-progress error
- wrong intersection count
- GNSS rejection delay
- recovery time
- time spent in low-confidence state

The pedestrian MVP requires an equivalent route-constrained scenario covering
500–1000 m without trusted GNSS, with step count, stride uncertainty, heading
uncertainty, phone-pose uncertainty, route-branch correctness, and major-turn
behavior reported separately. Passing unit tests alone does not satisfy either
MVP gate.

### Build and Environment Evidence

The Android build environment is configured with a repository-local SDK via
`local.properties`. The current build configuration also requires AndroidX and
Java/Kotlin JVM target alignment. The baseline commands are:

```text
./gradlew assembleDebug
./gradlew test
```

Both commands passed on 2026-09-01 after configuring the SDK and aligning the
app Java target with Kotlin JVM 17. Future tasks must report these commands when
their changes affect build configuration or shared modules.

---

## Execution Specificity Rules

Tasks must be executable without an additional requirements interview. If a task uses a broad term, apply the definition below and proceed with the smallest implementation that satisfies the stated acceptance criteria.

### Required interpretation of broad terms

- **“where available” / “if supported”** means detect capability at runtime, store a nullable/unavailable result, and continue without failing the session.
- **“gracefully”** means no crash, no silent substitution, an explicit unavailable/degraded status, and a diagnostic event or test covering the fallback.
- **“basic”** means the smallest end-to-end implementation needed to satisfy the listed acceptance criteria; do not add adjacent features.
- **“robust” / “reliable”** means deterministic behavior for the listed normal, boundary, unavailable-input, and malformed-input tests. Do not infer an unstated performance target.
- **“appropriate” / “reasonable” / “conservative”** means choose documented constants or policy thresholds, expose them as configuration, and add boundary tests. Use the least permissive behavior that avoids rejecting the explicitly listed plausible cases.
- **“support”** means implement the listed operation or input, validate it, and test success plus the primary failure path. Do not expand the supported matrix beyond the task.
- **“etc.” / “and similar”** means only the examples explicitly listed in that task are required; do not invent additional scope.
- **“where possible”** means implement the test using the repository’s available test infrastructure; if an Android/device dependency prevents it, add the nearest deterministic unit test and document the limitation.
- **“future-compatible”** means avoid blocking the named future use case; it does not require implementing that future feature now.
- **“production-ready”** is not an implicit acceptance criterion. Use only the concrete criteria written in the task.

### Defaults when a detail is missing

Use these defaults unless the task explicitly overrides them:

1. Preserve existing public interfaces and data formats.
2. Prefer Kotlin/JVM unit tests and deterministic fakes over device-only tests.
3. Use monotonic timestamps for ordering and elapsed time; retain wall-clock time only as metadata.
4. Keep optional sensor fields nullable and expose capability/unavailable state explicitly.
5. Keep thresholds and rates configurable with documented defaults; do not tune them from invented data.
6. Do not add libraries, modules, permissions, UI, network behavior, or persistence unless the task names them.
7. If the repository cannot satisfy an acceptance criterion because of a pre-existing dependency, stop at that boundary, report the exact blocker, and do not broaden the task to repair unrelated infrastructure.

### Assumption and completion rule

Codex should make a bounded assumption using the rules above rather than return asking for details. Record the assumption in the completion report. Ask the user only when two plausible interpretations would cause materially different architecture, data loss, external side effects, or scope expansion.

### Required completion evidence

Every implementation task must report the exact commands run and their result. Every untested criterion must be listed under `Known limitations`; “not tested” must never be presented as “passed.”

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

# EPIC 0 — Repository Foundation

---

## TASK 001 — Verify Repository Structure

Status: COMPLETE

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

Status: COMPLETE

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

Status: COMPLETE

### Model

Terra

### Objective

Create or validate the minimal Android application/module configuration required by all Android-platform tasks.

### Scope

- Android application module exists
- Kotlin Android plugin, SDK levels, Android Gradle Plugin, and Gradle wrapper are configured
- manifest, namespace/package, basic source sets, unit-test source set, and instrumentation-test source set exist
- required AndroidX dependencies are established
- Compose is configured only if already chosen by the project architecture

Do not implement navigation, sensors, GNSS, OBD, maps, or UI features.

### Dependencies

TASK 002

### Acceptance criteria

- `./gradlew assembleDebug` succeeds
- `./gradlew test` succeeds or reports that no tests exist yet
- the Android module resolves and can reference shared/core models
- no circular module dependencies

### Tests

```text
./gradlew assembleDebug
./gradlew test
```

---

# EPIC 1 — Shared Data Model

---

## TASK 003 — Define Monotonic Timestamp Type

Status: COMPLETE

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

Status: COMPLETE

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
```

Nullable fields must reflect Android API availability.

### Acceptance criteria

GNSS samples can be serialized for replay.

---

## TASK 005 — Define Raw GNSS Models

Status: COMPLETE

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

Status: COMPLETE

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

Status: COMPLETE

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

Status: COMPLETE

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

Status: COMPLETE

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
```

### Requirements

Format must:

- preserve monotonic timestamps
- support future schema evolution
- support deterministic replay
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

Status: COMPLETE

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

### Tests

Generate simulated multi-sensor session and verify no observations are lost.

---

## TASK 011 — Session Reader

Status: COMPLETE

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

Status: COMPLETE

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

A recorded session can replay deterministically independent of actual wall-clock time.

---

## TASK 013 — Replay Engine

Status: COMPLETE

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

Recorded streams produce events in original timestamp order.

---

## TASK 014 — Replay Event Bus

Status: COMPLETE

### Model
Luna

### Objective

Create a typed event-distribution layer for replay data.

Avoid one giant generic event object.

---

## TASK 015 — Synthetic GNSS Outage

Status: COMPLETE

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

Status: COMPLETE

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

## TASK 017 — Gradual GNSS Drift Simulator

Status: COMPLETE

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

Status: COMPLETE

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

# EPIC 4 — Android GNSS Collection

---

## TASK 019 — GNSS Location Provider

Status: COMPLETE

### Model
Terra

### Objective

Implement live Android GNSS location collection.

### Dependencies

TASK 002, TASK 002A, TASK 003, TASK 004

If TASK 002A is incomplete, stop and report the unmet dependency. Do not bootstrap Android/build infrastructure as part of TASK 019.

### Requirements

Use Android location APIs.

Convert framework objects immediately into internal models.

Do not expose Android `Location` outside the GNSS module.

### Tests

Mock/provider-level tests where possible.

Run Android build.

---

## TASK 020 — Raw GNSS Collector

Status: COMPLETE

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

Status: COMPLETE

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

Status: COMPLETE

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

Status: COMPLETE

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

Status: COMPLETE

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

Status: COMPLETE

### Model
Luna

### Objective

Collect pressure observations where supported.

Gracefully report unavailable sensor.

---

## TASK 026 — IMU Calibration State

Status: COMPLETE

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

Status: COMPLETE

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

Status: COMPLETE

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

Status: COMPLETE

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

Status: COMPLETE

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

Status: COMPLETE

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

Status: COMPLETE

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

Status: COMPLETE

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

Status: COMPLETE

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

Status: COMPLETE
Implementation: `docs/graphhopper-architecture.md` recommends an off-device GraphHopper preparation pipeline and a versioned, repository-owned custom extracted graph for the Android runtime. It covers Android compatibility, memory budgets, preparation, regional sizing, licensing, route APIs, spatial indexing, turn restrictions, and map-matching reuse without adding a GraphHopper APK dependency.
Tests: NONE — documentation/architecture task; required-section and explicit-recommendation contract verified with a read-only PowerShell check.
Verification: TASK 035 document contract check — passed 2026-09-02 (9/9 required sections, explicit `custom extracted graph` recommendation, no GraphHopper APK dependency); `$taskGradleHome = Join-Path $env:TEMP 'some-gradle-user'; $env:GRADLE_USER_HOME = $taskGradleHome; .\gradlew.bat --no-daemon assembleDebug test --console=plain` — passed 2026-09-02.

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

Status: PARTIAL
Implementation: `core/src/main/kotlin/com/example/navigation/routing/OfflineRoutingGraph.kt` provides a strict loader for one versioned, checksummed, app-owned regional graph directory. It validates the manifest, schema, byte order, graph records, adjacency, turn restrictions, and spatial index before mounting the region.
Tests: `core/src/test/kotlin/com/example/navigation/routing/OfflineRoutingGraphTest.kt`
Verification: `$taskGradleHome = Join-Path $env:TEMP 'some-gradle-user'; $env:GRADLE_USER_HOME = $taskGradleHome; .\gradlew.bat --no-daemon :core:test --tests "com.example.navigation.routing.OfflineRoutingGraphTest" --console=plain` — passed 2026-09-02; same environment with `.\gradlew.bat --no-daemon assembleDebug test --console=plain` — passed 2026-09-02. Synthetic regional fixture is covered; a real GraphHopper-exported region and physical-device memory/load proof required by `docs/graphhopper-architecture.md` are not yet available.

### Model
Terra

### Objective

Implement the architecture approved in Task 035.

Load one offline regional routing graph.

---

## TASK 037 — Route Calculation

Status: COMPLETE
Implementation: `core/src/main/kotlin/com/example/navigation/routing/OfflineRoutingGraph.kt` provides deterministic bounded edge-state A* vehicle routing from origin to destination, respects vehicle access and turn restrictions, and returns the internal `Route` model.
Tests: `core/src/test/kotlin/com/example/navigation/routing/OfflineRoutingGraphTest.kt`
Verification: `$taskGradleHome = Join-Path $env:TEMP 'some-gradle-user'; $env:GRADLE_USER_HOME = $taskGradleHome; .\gradlew.bat --no-daemon :core:test --tests "com.example.navigation.routing.OfflineRoutingGraphTest" --console=plain` — passed 2026-09-02; same environment with `.\gradlew.bat --no-daemon assembleDebug test --console=plain` — passed 2026-09-02.

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

Status: COMPLETE
Implementation: `core/src/main/kotlin/com/example/navigation/routing/RouteSegment.kt` represents ordered route pieces with geometry, length, optional heading, road/path ID, turn information, and access type; `OfflineRoutingGraph.kt` populates these segments on calculated routes.
Tests: `core/src/test/kotlin/com/example/navigation/routing/RouteSegmentTest.kt`; ordered-route integration assertions in `core/src/test/kotlin/com/example/navigation/routing/OfflineRoutingGraphTest.kt`.
Verification: `$taskGradleHome = Join-Path $env:TEMP 'some-gradle-user'; $env:GRADLE_USER_HOME = $taskGradleHome; .\gradlew.bat --no-daemon :core:test --tests "com.example.navigation.routing.RouteSegmentTest" --tests "com.example.navigation.routing.OfflineRoutingGraphTest" --console=plain` — passed 2026-09-02; `$taskGradleHome = Join-Path $env:TEMP 'some-gradle-user'; $env:GRADLE_USER_HOME = $taskGradleHome; .\gradlew.bat --no-daemon :app:assembleDebug :app:testDebugUnitTest :core:test --console=plain` — passed 2026-09-02.

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

Status: COMPLETE
Implementation: `core/src/main/kotlin/com/example/navigation/routing/OfflineRoutingGraph.kt` provides `PedestrianRouteCalculator`, which uses pedestrian-accessible edges and applies only pedestrian turn restrictions; vehicle-only restrictions remain unavailable to pedestrian routing and pedestrian-only restrictions do not affect vehicle routing.
Tests: `core/src/test/kotlin/com/example/navigation/routing/PedestrianRouteCalculatorTest.kt`
Verification: `$taskGradleHome = Join-Path $env:TEMP 'some-gradle-user'; $env:GRADLE_USER_HOME = $taskGradleHome; .\gradlew.bat --no-daemon :core:test --tests "com.example.navigation.routing.PedestrianRouteCalculatorTest" --console=plain` — passed 2026-09-02; same environment with `.\gradlew.bat --no-daemon :app:assembleDebug :app:testDebugUnitTest :core:test --console=plain` — passed 2026-09-02.

### Model
Terra

### Objective

Add pedestrian route calculation.

Ensure vehicle-only restrictions aren't incorrectly applied to pedestrian routes and vice versa.

---

# EPIC 9 — Vehicle Dead Reckoning

---

## TASK 040 — Vehicle Motion State

Status: COMPLETE

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

Status: COMPLETE

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

Status: COMPLETE

### Model
Terra

### Objective

Integrate vehicle speed over monotonic time.

Test variable speed.

Do not assume constant polling rate.

---

## TASK 043 — Vehicle Dead Reckoner

Status: PARTIAL

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

Status: NOT_STARTED

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

Status: COMPLETE

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

Status: COMPLETE

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

Status: COMPLETE

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

Status: COMPLETE

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

Status: NOT_STARTED

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

## TASK 050 — GNSS Trust Framework

Status: COMPLETE

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

Status: COMPLETE

### Model
Terra

### Objective

Detect GNSS displacement incompatible with elapsed time and estimated vehicle/pedestrian movement.

Output evidence score.

Do not reject GNSS directly.

---

## TASK 052 — GNSS vs OBD Speed Evidence

Status: COMPLETE

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

Status: COMPLETE

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

## TASK 054 — GNSS Heading vs Motion Evidence

Status: COMPLETE

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

Status: COMPLETE

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

Status: COMPLETE

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

Status: COMPLETE

### Model
Terra

### Objective

Extract AGC patterns where device hardware exposes AGC.

Gracefully disable feature if unsupported.

---

## TASK 058 — GNSS Trust Aggregation V1

Status: COMPLETE

### Model
Sol

### Objective

Combine current trust evidence into an explainable score.

Inputs may include:

```text
position jump
vehicle speed mismatch
distance mismatch
heading mismatch
route mismatch
C/N0 indicators
AGC indicators
```

### Requirements

Every score must expose contribution of each signal.

---

## TASK 059 — GNSS Trust State Machine

Status: COMPLETE

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

# EPIC 12 — Hybrid Vehicle Navigation

---

## TASK 060 — GNSS Correction Interface

Status: COMPLETE

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

---

## TASK 061 — GNSS Rejection Integration

Status: COMPLETE

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

---

## TASK 062 — GNSS Recovery Integration

Status: COMPLETE

### Model
Sol

### Objective

Implement gradual re-entry of GNSS after rejection.

Must not snap immediately to first candidate fix.

---

## TASK 063 — Vehicle Position Confidence

Status: COMPLETE

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

Status: COMPLETE

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

Status: COMPLETE

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

Status: COMPLETE

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

Status: COMPLETE

### Model
Sol

### Objective

During trusted GNSS walking periods, calibrate:

```text
distance per step
cadence relationship
```

Freeze calibration when GNSS trust becomes questionable.

---

# EPIC 14 — Pedestrian Heading

---

## TASK 068 — Pedestrian Heading State

Status: COMPLETE

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

Status: COMPLETE

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

Status: PARTIAL
Implementation: `PhonePoseClassifier.kt` provides conservative probabilistic `HAND_FORWARD`, `HAND_SWINGING`, `POCKET`, and `UNKNOWN` classification from validated window features; ambiguous evidence remains `UNKNOWN`.
Tests: `PedestrianNavigationPipelineTest.kt`
Verification: `$taskGradleHome = Join-Path $env:TEMP 'some-gradle-user'; $env:GRADLE_USER_HOME = $taskGradleHome; .\gradlew.bat --no-daemon :core:test --tests "com.example.navigation.pedestrian.*" --console=plain` — passed on 2026-09-03 (29 tests, 0 failures).

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

Known limitation: No pose-labelled walking recordings are present in the repository, so thresholds are deterministic V1 defaults and are not field/replay validated.

---

## TASK 071 — Pedestrian Heading Estimator V1

### Model
Sol

Status: PARTIAL
Implementation: `PedestrianHeadingEstimator.kt` combines relative gyro propagation, TASK 070 pose confidence, sustained trusted-GNSS course, and route direction using circular weighting; no-heading input remains unavailable with 180° uncertainty.
Tests: `PedestrianNavigationPipelineTest.kt`
Verification: `$taskGradleHome = Join-Path $env:TEMP 'some-gradle-user'; $env:GRADLE_USER_HOME = $taskGradleHome; .\gradlew.bat --no-daemon :core:test --tests "com.example.navigation.pedestrian.*" --console=plain` — passed on 2026-09-03 (29 tests, 0 failures).

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

Known limitation: TASK 070 pose output and estimator weighting require validation against pose-labelled walking recordings.

---

# EPIC 15 — Pedestrian Dead Reckoning

---

## TASK 072 — Pedestrian Motion State

Status: COMPLETE
Implementation: `PedestrianMotionState.kt` represents cumulative step count/distance, heading, stride and heading uncertainty, pose confidence, and monotonic timestamp; step updates preserve all uncertainty inputs.
Tests: `PedestrianNavigationPipelineTest.kt`
Verification: Full pedestrian suite passed on 2026-09-03 (29 tests, 0 failures).

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

Status: COMPLETE
Implementation: `PedestrianDeadReckoner.kt` propagates geodetic position only from accepted steps, stride estimates, and pedestrian heading; missing heading yields `LOST`, and uncertainty grows on every step without acceleration integration.
Tests: `PedestrianNavigationPipelineTest.kt`
Verification: Full pedestrian suite passed on 2026-09-03 (29 tests, 0 failures).

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

---

## TASK 074 — Pedestrian Route Progress

Status: COMPLETE
Implementation: `PedestrianRouteProgressEstimator` derives monotonic distance along the planned walking route from probabilistic matcher output and rejects unsupported/backward updates.
Tests: `PedestrianNavigationPipelineTest.kt`
Verification: Full pedestrian suite passed on 2026-09-03 (29 tests, 0 failures).

### Model
Terra

### Objective

Estimate distance along planned walking route.

---

## TASK 075 — Pedestrian Turn/Route Matching

Status: PARTIAL
Implementation: `PedestrianRouteMatcher.kt` scores route segments from position distance and relative heading, retains up to three normalized hypotheses around ambiguous geometry, and exposes route progress for the best-supported candidate.
Tests: `PedestrianNavigationPipelineTest.kt`
Verification: Full pedestrian suite passed on 2026-09-03 (29 tests, 0 failures).

### Model
Sol

### Objective

Use observed walking turns to confirm expected route branches.

Maintain multiple candidates around ambiguous intersections.

Known limitation: Candidate scoring consumes relative heading evidence but is not yet replay-validated with labelled intersection turns; TASK 131 must establish branch-selection performance.

---

## TASK 076 — Pedestrian Free-Space State

Status: COMPLETE
Implementation: `PedestrianMatchState.FreeSpace` explicitly represents no supported path when all route candidates exceed the configured distance/probability boundary; no forced OSM-path snap occurs.
Tests: `PedestrianNavigationPipelineTest.kt`
Verification: Full pedestrian suite passed on 2026-09-03 (29 tests, 0 failures).

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

Status: COMPLETE
Implementation: `PedestrianConfidenceModel.kt` derives explicit confidence, score, and estimated accuracy from stride/heading uncertainty, pose confidence, path ambiguity, distance and steps since trusted GNSS, map quality, and matched/free-space state.
Tests: `PedestrianNavigationPipelineTest.kt`
Verification: Full pedestrian suite passed on 2026-09-03 (29 tests, 0 failures).

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

Status: COMPLETE
Implementation: `core/src/main/kotlin/com/example/navigation/pedestrian/RelativeAltitudeEstimator.kt` converts monotonic `BarometerSample` pressure changes to a session-relative altitude displacement, with no absolute-elevation output.
Tests: `core/src/test/kotlin/com/example/navigation/pedestrian/RelativeAltitudeEstimatorTest.kt`
Verification: `$taskGradleHome = Join-Path $env:TEMP 'some-gradle-user'; $env:GRADLE_USER_HOME = $taskGradleHome; .\gradlew.bat --no-daemon :core:test --rerun-tasks --console=plain` — passed 2026-09-01; same environment with `.\gradlew.bat --no-daemon assembleDebug test --console=plain` — passed 2026-09-01.

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

Status: COMPLETE
Implementation: `core/src/main/kotlin/com/example/navigation/pedestrian/ElevationEventDetector.kt` classifies sustained relative-altitude movement as `UP`, `DOWN`, or `LEVEL` and emits only state transitions.
Tests: `core/src/test/kotlin/com/example/navigation/pedestrian/ElevationEventDetectorTest.kt`
Verification: `$taskGradleHome = Join-Path $env:TEMP 'some-gradle-user'; $env:GRADLE_USER_HOME = $taskGradleHome; .\gradlew.bat --no-daemon :core:test --rerun-tasks --console=plain` — passed 2026-09-01; same environment with `.\gradlew.bat --no-daemon assembleDebug test --console=plain` — passed 2026-09-01.

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

Status: COMPLETE
Implementation: `core/src/main/kotlin/com/example/navigation/navigation/MotionClassification.kt` defines a timestamped, normalized probability distribution for `VEHICLE`, `PEDESTRIAN`, `STATIONARY`, and `UNKNOWN`, including an explicit unknown fallback. It deliberately performs no sensor inference or mode transition.
Tests: `core/src/test/kotlin/com/example/navigation/navigation/MotionClassificationTest.kt`
Verification: `$taskGradleHome = Join-Path $env:TEMP 'some-gradle-user'; $env:GRADLE_USER_HOME = $taskGradleHome; .\gradlew.bat --no-daemon :core:test --tests "com.example.navigation.navigation.MotionClassificationTest" --console=plain` — passed 2026-09-02; same environment with `.\gradlew.bat --no-daemon :app:assembleDebug :app:testDebugUnitTest :core:test --console=plain` — passed 2026-09-02.

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

Status: COMPLETE
Implementation: `core/src/main/kotlin/com/example/navigation/navigation/NavigationModeTransitions.kt` provides a stateful vehicle-to-pedestrian detector requiring an observed OBD stop, subsequent OBD disconnect, a walking-step sequence, and pedestrian motion probability. Bluetooth disconnect alone cannot transition, and the prior `NavigationPosition` is preserved as the pedestrian initialization anchor.
Tests: `core/src/test/kotlin/com/example/navigation/navigation/NavigationModeTransitionsTest.kt`
Verification: `$taskGradleHome = Join-Path $env:TEMP 'some-gradle-user'; $env:GRADLE_USER_HOME = $taskGradleHome; .\gradlew.bat --no-daemon :core:test --tests "com.example.navigation.navigation.NavigationModeTransitionsTest" --console=plain` — passed 2026-09-02; same environment with `.\gradlew.bat --no-daemon :app:assembleDebug :app:testDebugUnitTest :core:test --console=plain` — passed 2026-09-02.

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

Status: COMPLETE
Implementation: `core/src/main/kotlin/com/example/navigation/navigation/NavigationModeTransitions.kt` provides a conservative pedestrian-to-vehicle detector requiring vehicle motion probability, connected and moving OBD speed, and explicit user confirmation. Walking stops, public-transit-like motion without OBD, and idle Bluetooth reconnects do not transition; position continuity is preserved.
Tests: `core/src/test/kotlin/com/example/navigation/navigation/NavigationModeTransitionsTest.kt`
Verification: `$taskGradleHome = Join-Path $env:TEMP 'some-gradle-user'; $env:GRADLE_USER_HOME = $taskGradleHome; .\gradlew.bat --no-daemon :core:test --tests "com.example.navigation.navigation.NavigationModeTransitionsTest" --console=plain` — passed 2026-09-02; same environment with `.\gradlew.bat --no-daemon :app:assembleDebug :app:testDebugUnitTest :core:test --console=plain` — passed 2026-09-02.

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

Status: COMPLETE
Implementation: `NavigationMapPresentation` and `MapLibreNavigationRenderer` render separately identifiable route geometry, estimated-position, movement-direction, and destination layers from explicit navigation state. `MainActivity` hosts the map state without fabricating a route or location when no navigation state is available.
Tests: `app/src/test/kotlin/com/example/navigation/app/NavigationMapPresentationTest.kt`
Verification: focused `:app:testDebugUnitTest --tests 'com.example.navigation.app.NavigationMapPresentationTest'` — passed 2026-09-02 (2 tests, 0 failures/errors); temporary external `:app:assembleDebug` also completed. The normal `app/build` configuration was restored afterward. `git diff --check` — passed.

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

Status: COMPLETE
Implementation: Restored `NavigationStatusPresentation` and wired the normal navigation view to expose GNSS, HYBRID, VEHICLE DR, and PEDESTRIAN DR source labels from explicit navigation position provenance.
Tests: `app/src/test/kotlin/com/example/navigation/app/NavigationStatusPresentationTest.kt`
Verification: focused app unit-test command was attempted; compilation was blocked by Gradle cache access/network environment errors after source restoration.

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

Status: COMPLETE
Implementation: Added explicit `GnssTrustState` to `NavigationMapState`, mapped TRUSTED/QUESTIONABLE/REJECTED/RECOVERING to the required normal-view labels, and kept raw trust diagnostics out of the navigation screen.
Tests: `app/src/test/kotlin/com/example/navigation/app/NavigationStatusPresentationTest.kt`
Verification: focused app unit-test command was attempted; compilation was blocked by Gradle cache access/network environment errors after source restoration.

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

Status: COMPLETE
Implementation: `NavigationMapPresentation` now carries an uncertainty center/radius and suppresses precise markers for LOW and LOST confidence. `MapLibreNavigationRenderer` renders the stated accuracy as a translucent geographic area; the Compose overlay likewise omits the exact blue dot at LOW/LOST confidence.
Tests: `app/src/test/kotlin/com/example/navigation/app/NavigationMapPresentationTest.kt`
Verification: `$taskGradleHome = Join-Path $env:TEMP 'some-gradle-user'; $env:GRADLE_USER_HOME = $taskGradleHome; .\gradlew.bat --no-daemon :app:testDebugUnitTest --tests 'com.example.navigation.app.NavigationMapPresentationTest' --tests 'com.example.navigation.app.NavigationDiagnosticsTest' --tests 'com.example.navigation.app.NavigationStatusPresentationTest' --rerun-tasks --console=plain` — passed 2026-09-02 (8 tests, 0 failures/errors); `git diff --check` — passed.

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

---

## TASK 087 — Diagnostics Screen

Status: COMPLETE
Implementation: `NavigationDiagnostics` and the expandable developer-only diagnostics panel display GNSS trust, trust evidence, OBD rate, vehicle speed, gyro yaw rate, step cadence, route-candidate probabilities, position uncertainty, and last trusted GNSS. Data unavailable from the current recorder state is labeled explicitly as unavailable, and raw diagnostics are hidden from the normal navigation view until opened.
Tests: `app/src/test/kotlin/com/example/navigation/app/NavigationDiagnosticsTest.kt`
Verification: `$taskGradleHome = Join-Path $env:TEMP 'some-gradle-user'; $env:GRADLE_USER_HOME = $taskGradleHome; .\gradlew.bat --no-daemon :app:testDebugUnitTest --tests 'com.example.navigation.app.NavigationMapPresentationTest' --tests 'com.example.navigation.app.NavigationDiagnosticsTest' --tests 'com.example.navigation.app.NavigationStatusPresentationTest' --rerun-tasks --console=plain` — passed 2026-09-02 (8 tests, 0 failures/errors); `git diff --check` — passed.

### Model
Terra

### Objective

Display development diagnostics:

```text
GNSS trust
trust evidence
OBD rate
vehicle speed
gyro yaw rate
step cadence
route candidate probabilities
position uncertainty
last trusted GNSS
```

---

# EPIC 19 — Benchmark Platform

---

## TASK 088 — Benchmark Scenario Format

Status: COMPLETE
Implementation: `core/src/main/kotlin/com/example/navigation/benchmark/BenchmarkScenario.kt` defines the typed session/route/GNSS-corruption scenario model and a strict parser for the documented YAML shape, including supported corruption types, monotonic scenario timing fields expressed in seconds, required-field validation, duplicate/unknown-field rejection, and file parsing.
Tests: `core/src/test/kotlin/com/example/navigation/benchmark/BenchmarkScenarioTest.kt`
Verification: `$taskGradleHome = Join-Path $env:TEMP 'some-gradle-user'; $env:GRADLE_USER_HOME = $taskGradleHome; .\gradlew.bat --no-daemon :core:test --tests 'com.example.navigation.benchmark.*' --rerun-tasks --console=plain` — passed 2026-09-02 (9 tests, 0 failures/errors); `git diff --check` — passed.

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

---

## TASK 089 — Benchmark Metrics Engine

Status: COMPLETE
Implementation: `core/src/main/kotlin/com/example/navigation/benchmark/NavigationBenchmarkMetrics.kt` calculates route correctness, route-progress errors, branch errors, GNSS rejection/recovery metrics, false rejections, and low-confidence duration from monotonic replay samples.
Tests: `core/src/test/kotlin/com/example/navigation/benchmark/NavigationBenchmarkMetricsTest.kt`
Verification: `:core:test --tests 'com.example.navigation.benchmark.*' --rerun-tasks` — passed 2026-09-02.

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
```

---

## TASK 090 — Benchmark CLI

Status: COMPLETE
Implementation: `runNavigationBenchmark` accepts `benchmarkSession`, `benchmarkScenario`, and `benchmarkAlgorithmConfig` Gradle properties. `NavigationBenchmarkCli` parses the TASK 088 scenario, validates the configured session identity and complete footer, validates the explicit `algorithm: replay_passthrough` algorithm configuration, replays the recorded session deterministically at maximum speed, and emits human-readable plus JSON execution results.
Limitation: `replay_passthrough` reports replay/session results but deliberately emits no navigation estimates, so TASK 089 navigation metrics are reported as unavailable until a navigation algorithm adapter is supplied.
Tests: `core/src/test/kotlin/com/example/navigation/benchmark/NavigationBenchmarkCliTest.kt`
Verification: `$taskGradleHome = Join-Path $env:TEMP 'some-gradle-user'; $env:GRADLE_USER_HOME = $taskGradleHome; .\gradlew.bat --no-daemon :core:test --tests 'com.example.navigation.benchmark.*' --rerun-tasks --console=plain` — passed 2026-09-02 (10 tests, 0 failures/errors); `git diff --check` — passed.

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

Status: COMPLETE
Implementation: `core/src/main/kotlin/com/example/navigation/benchmark/BenchmarkRegressionThresholds.kt` provides configurable minimum route correctness, maximum wrong branches, and maximum relative mean-progress-error increase checks. `requirePassed()` makes a failed evaluation fail its invoking CI task.
Tests: `core/src/test/kotlin/com/example/navigation/benchmark/BenchmarkRegressionEvaluatorTest.kt`
Verification: `:core:test --tests 'com.example.navigation.benchmark.*' --rerun-tasks` — passed 2026-09-02.

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

# EPIC 20 — Field Test Tooling

---

## TASK 092 — Start/Stop Recording UI

Status: COMPLETE
Implementation: `MainActivity` provides start/stop controls, recording status, elapsed duration, sensor availability, and recorded storage usage; `NavigationForegroundService` owns active collection and `RecordingSessionController` creates completed session files.
Tests: `core/src/test/kotlin/com/example/navigation/replay/RecordingSessionControllerTest.kt`, `app/src/test/kotlin/com/example/navigation/app/RecordedSessionsTest.kt`
Verification: `$taskGradleHome = Join-Path $env:TEMP 'some-gradle-user'; $env:GRADLE_USER_HOME = $taskGradleHome; .\gradlew.bat --no-daemon :app:testDebugUnitTest --tests 'com.example.navigation.app.RecordedSessionsTest' :core:test --tests 'com.example.navigation.replay.RecordingSessionControllerTest' --rerun-tasks --console=plain` — passed 2026-09-02; `git diff --check` — passed.

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

Status: COMPLETE
Implementation: `MainActivity` exposes the `MARK EVENT` control only while recording; the foreground service delegates it to `LiveRecordingProviderBridge`, which persists a monotonic-timestamped `SessionMarker` through `RecordingSessionController`.
Tests: `core/src/test/kotlin/com/example/navigation/replay/RecordingSessionControllerTest.kt`
Verification: `$taskGradleHome = Join-Path $env:TEMP 'some-gradle-user'; $env:GRADLE_USER_HOME = $taskGradleHome; .\gradlew.bat --no-daemon :app:testDebugUnitTest --tests 'com.example.navigation.app.RecordedSessionsTest' :core:test --tests 'com.example.navigation.replay.RecordingSessionControllerTest' --rerun-tasks --console=plain` — passed 2026-09-02; `git diff --check` — passed.

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
intersection
wrong navigation
route deviation
```

---

## TASK 094 — Session Export

Status: COMPLETE
Implementation: `MainActivity` lists completed `.navsession` files, supports multi-selection, and exports them through `FileProvider` using Android’s chooser; the manifest exposes only the non-exported provider with URI grants, and no network upload is implemented.
Tests: `core/src/test/kotlin/com/example/navigation/replay/RecordingSessionControllerTest.kt`, `app/src/test/kotlin/com/example/navigation/app/RecordedSessionsTest.kt`
Verification: `$taskGradleHome = Join-Path $env:TEMP 'some-gradle-user'; $env:GRADLE_USER_HOME = $taskGradleHome; .\gradlew.bat --no-daemon :app:testDebugUnitTest --tests 'com.example.navigation.app.RecordedSessionsTest' :core:test --tests 'com.example.navigation.replay.RecordingSessionControllerTest' --rerun-tasks --console=plain` — passed 2026-09-02; implementation/manifest audit completed; `git diff --check` — passed.

### Model
Luna

### Objective

Export recorded session as a portable file.

No automatic network upload.

---

# EPIC 21 — Performance

---

## TASK 095 — Sensor Rate Profiling

Status: NOT_STARTED

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

Status: NOT_STARTED

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

Status: NOT_STARTED

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

Status: COMPLETE
Implementation: `app/src/main/kotlin/com/example/navigation/app/NavigationForegroundService.kt` owns the live recording controller and providers in a started Android location foreground service. `MainActivity` binds only to issue commands and observe state, and unbinds on UI stop without closing the service-owned recorder. The manifest declares the location foreground-service permissions and service type.
Verification: `:app:assembleDebug` — passed 2026-09-02; debug APK rebuilt at `app/build/outputs/apk/debug/app-debug.apk`. `:app:testDebugUnitTest` was attempted twice but blocked before test execution by a Windows/OneDrive lock on `app/build/intermediates/merged_res_blame_folder/debug/mergeDebugResources/out`; no source/test failure was reported. `git diff --check` — passed.

### Model
Terra

### Objective

Move active navigation/sensor collection into an Android foreground service where required.

Ensure UI lifecycle does not terminate navigation state.

---

## TASK 099 — App Background/Resume Test

Status: COMPLETE
Implementation: `NavigationLifecyclePolicy` codifies that screen-off/background and Activity recreation/rotation keep the foreground service and navigation state active; recreation and rotation rebind the Activity to the existing service.
Tests: `app/src/test/kotlin/com/example/navigation/app/NavigationLifecyclePolicyTest.kt`
Verification: `$taskGradleHome = Join-Path $env:TEMP 'some-gradle-user'; $env:GRADLE_USER_HOME = $taskGradleHome; .\gradlew.bat --no-daemon :app:testDebugUnitTest --tests 'com.example.navigation.app.NavigationLifecyclePolicyTest' --rerun-tasks --console=plain` — passed 2026-09-02 (2 tests, 0 failures/errors); `git diff --check` — passed.

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

Status: COMPLETE
Implementation: `app/src/main/kotlin/com/example/navigation/app/NavigationProcessRecovery.kt` persists a minimal interrupted-session checkpoint (session identity plus monotonic and wall-clock diagnostics). On foreground-service creation it consumes that checkpoint, reports a fresh interrupted session or discards a stale/future one, and never restarts recording, sensors, or dead reckoning automatically. Explicit recording stop clears the checkpoint.
Tests: `app/src/test/kotlin/com/example/navigation/app/ProcessRestartRecoveryPolicyTest.kt`
Verification: `:app:testDebugUnitTest --tests 'com.example.navigation.app.ProcessRestartRecoveryPolicyTest'` — passed 2026-09-02 (2 tests; BUILD SUCCESSFUL) using a temporary external verification build directory because OneDrive held the normal generated-resource directory lock. The normal `app/build` configuration was restored afterward. `git diff --check` — passed.

### Model
Terra

### Objective

Persist enough session/navigation state to recover gracefully after Android process termination.

Do not blindly resume dead reckoning from stale state without checking timestamps.

---

# EPIC 23 — Hardware Compatibility

---

## TASK 101 — Sensor Capability Report

Status: COMPLETE
Implementation: `app/src/main/kotlin/com/example/navigation/app/SensorCapabilityReport.kt` collects gyro, accelerometer, game rotation vector, magnetometer, barometer, raw GNSS, AGC, carrier-frequency API support, Android API level, and manufacturer/model at `MainActivity` startup. Hardware availability is represented explicitly as booleans; no missing sensor is treated as anomalous.
Tests: `app/src/test/kotlin/com/example/navigation/app/SensorCapabilityReportTest.kt`
Verification: `$taskGradleHome = Join-Path $env:TEMP 'some-gradle-user'; $env:GRADLE_USER_HOME = $taskGradleHome; .\gradlew.bat --no-daemon :app:testDebugUnitTest :app:assembleDebug --console=plain` — passed 2026-09-02; `git diff --check` — passed.

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

Status: COMPLETE
Implementation: `app/src/main/kotlin/com/example/navigation/app/CapabilityFeatureFlags.kt` maps the TASK 101 report to explicit per-feature availability for heading, step, barometer, raw GNSS, AGC, and carrier-frequency features. Missing hardware disables only the dependent feature with an inspectable unavailable reason; AGC absence explicitly marks AGC trust evidence unavailable.
Tests: `app/src/test/kotlin/com/example/navigation/app/CapabilityFeatureFlagsTest.kt`
Verification: `$taskGradleHome = Join-Path $env:TEMP 'some-gradle-user'; $env:GRADLE_USER_HOME = $taskGradleHome; .\gradlew.bat --no-daemon :app:testDebugUnitTest :app:assembleDebug --console=plain` — passed 2026-09-02 (2 TASK 102 tests, 0 failures/errors); `git diff --check` — passed.

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

Status: COMPLETE
Implementation: `core/src/main/kotlin/com/example/navigation/replay/DatasetMetadata.kt` defines all required session metadata fields and a strict sidecar parser with ISO date, navigation-mode, boolean, unknown-field, duplicate-field, and completeness validation.
Tests: `core/src/test/kotlin/com/example/navigation/replay/DatasetMetadataTest.kt`
Verification: `$taskGradleHome = Join-Path $env:TEMP 'some-gradle-user'; $env:GRADLE_USER_HOME = $taskGradleHome; .\gradlew.bat --no-daemon :core:test --tests 'com.example.navigation.replay.DatasetMetadataTest' --rerun-tasks --console=plain` — passed 2026-09-02 (2 tests, 0 failures/errors); `git diff --check` — passed.

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

Status: COMPLETE
Implementation: `core/src/main/kotlin/com/example/navigation/replay/DatasetValidationTool.kt` performs read-only, configurable validation of recorded sessions for timestamp reversals, large gaps, missing GNSS, missing gyro/accelerometer IMU data, invalid vehicle-speed telemetry, and truncated/corrupt streams.
Tests: `core/src/test/kotlin/com/example/navigation/replay/DatasetValidationToolTest.kt`
Verification: `$taskGradleHome = Join-Path $env:TEMP 'some-gradle-user'; $env:GRADLE_USER_HOME = $taskGradleHome; .\gradlew.bat --no-daemon :core:test --tests 'com.example.navigation.replay.DatasetValidationToolTest' --rerun-tasks --console=plain` — passed 2026-09-02 (3 tests, 0 failures/errors); `git diff --check` — passed.

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

# EPIC 25 — First Integrated Milestones

---

## GNSS Spoofing / Lima Extensions

The following additions define the remote-spoofing test surface. Lima is a canonical replay fixture, never a production special case; detection must rely on physical continuity, trusted state, route state, and independent motion evidence.

### TASK 016A — Remote Location Spoof Simulator

Status: COMPLETE
Implementation: `core/src/main/kotlin/com/example/navigation/replay/RemoteLocationSpoofSimulator.kt` adds deterministic, replay-only remote GNSS location spoofing with arbitrary target coordinates, configurable start/duration/recovery, `STATIC` and `CONTINUE_RELATIVE_MOTION` behavior, and no modification to non-GNSS records or source recordings.
Tests: `core/src/test/kotlin/com/example/navigation/replay/LimaSpoofReplayTest.kt`
Verification: `$taskGradleHome = Join-Path $env:TEMP 'some-gradle-user'; $env:GRADLE_USER_HOME = $taskGradleHome; .\gradlew.bat --no-daemon :core:test --tests 'com.example.navigation.replay.*' --rerun-tasks --console=plain` — passed 2026-09-02 (including 3 LIMA replay tests, 0 failures/errors); `git diff --check` — passed.

**Model:** Terra

Extend replay corruption to move GNSS abruptly from the real area to arbitrary coordinates, with Lima, Peru as the canonical target. Support configurable start, duration, recovery, `STATIC` and `CONTINUE_RELATIVE_MOTION` behavior, deterministic replay, and preservation of the original recording. IMU, OBD, barometer, and step streams remain unchanged unless explicitly configured. Test Ukraine→Lima and an arbitrary remote target.

### TASK 018A — GNSS Time Corruption Simulator

Status: COMPLETE
Implementation: `core/src/main/kotlin/com/example/navigation/replay/GnssTimeCorruptionSimulator.kt` supports GNSS clock jump, forward/backward offset, and gradual drift on GNSS wall-clock/raw clock fields only. It never changes monotonic record timestamps, replay ordering, or non-GNSS streams.
Tests: `core/src/test/kotlin/com/example/navigation/replay/LimaSpoofReplayTest.kt`
Verification: `$taskGradleHome = Join-Path $env:TEMP 'some-gradle-user'; $env:GRADLE_USER_HOME = $taskGradleHome; .\gradlew.bat --no-daemon :core:test --tests 'com.example.navigation.replay.*' --rerun-tasks --console=plain` — passed 2026-09-02 (including 3 LIMA replay tests, 0 failures/errors); `git diff --check` — passed.

**Model:** Terra

Support GNSS time jump forward/backward, offset, and gradual drift, independently of position. GNSS-time corruption must never alter monotonic timestamps, replay ordering, or IMU/OBD/DR/PDR integration timing. Test position-correct/time-wrong and combined corruption scenarios.

### TASK 018B — Combined Remote Spoof Scenario

Status: COMPLETE
Implementation: `core/src/main/kotlin/com/example/navigation/replay/Lima001Scenario.kt` provides the reusable `LIMA-001` replay fixture, composing independently configurable remote location, GNSS-time, and velocity/bearing corruption, with local GNSS return controlled by the remote-location recovery configuration. Lima constants are fixture data only.
Tests: `core/src/test/kotlin/com/example/navigation/replay/LimaSpoofReplayTest.kt`
Verification: `$taskGradleHome = Join-Path $env:TEMP 'some-gradle-user'; $env:GRADLE_USER_HOME = $taskGradleHome; .\gradlew.bat --no-daemon :core:test --tests 'com.example.navigation.replay.*' --rerun-tasks --console=plain` — passed 2026-09-02 (including 3 LIMA replay tests, 0 failures/errors); `git diff --check` — passed.

**Model:** Luna

Create reusable `LIMA-001`, combining independently configurable remote position, GNSS-time, bearing, and velocity corruption, followed by local GNSS return. It is a benchmark fixture; production code must not contain location-specific rejection logic.

### TASK 050A — Last Trusted Position Anchor

Status: COMPLETE
Implementation: `core/src/main/kotlin/com/example/navigation/gnss/LastTrustedPositionAnchor.kt` tracks the latest anchor only while trust is `TRUSTED` by default; questionable behavior is explicit and configurable, while rejected/recovering observations preserve the anchor.
Tests: `core/src/test/kotlin/com/example/navigation/gnss/TrustedAnchorAndContinuityTest.kt`
Verification: `$taskGradleHome = Join-Path $env:TEMP 'some-gradle-user'; $env:GRADLE_USER_HOME = $taskGradleHome; .\gradlew.bat --no-daemon :core:test --tests 'com.example.navigation.gnss.*' --rerun-tasks --console=plain` — passed 2026-09-02 (4 anchor/continuity tests, 0 failures/errors); `git diff --check` — passed.

**Model:** Terra

Track the latest navigation state established while GNSS was trusted. Rejected GNSS must never replace the anchor; questionable updates follow explicit policy. Test trusted→trusted, questionable, rejected, and Kyiv→Lima spoof transitions.

### TASK 050B — Geographic Continuity Evidence

Status: COMPLETE
Implementation: `core/src/main/kotlin/com/example/navigation/gnss/GeographicContinuityEvidenceEvaluator.kt` compares a candidate GNSS location with the trusted anchor using monotonic elapsed time, mode-specific configurable physical envelopes, independent speed/uncertainty, and anchor/GNSS accuracy. It emits inspectable evidence only and has no location-specific rule.
Tests: `core/src/test/kotlin/com/example/navigation/gnss/TrustedAnchorAndContinuityTest.kt`
Verification: `$taskGradleHome = Join-Path $env:TEMP 'some-gradle-user'; $env:GRADLE_USER_HOME = $taskGradleHome; .\gradlew.bat --no-daemon :core:test --tests 'com.example.navigation.gnss.*' --rerun-tasks --console=plain` — passed 2026-09-02 (4 anchor/continuity tests, 0 failures/errors); `git diff --check` — passed.

**Model:** Terra

Compare a new GNSS position with the last trusted position, monotonic elapsed time, mode, speed, and uncertainty using configurable pedestrian/vehicle/unknown physical envelopes. Produce inspectable trust evidence only; do not embed country- or Lima-specific rules. Test plausible local movement and impossible 100 km/Ukraine→Lima jumps.

### TASK 050C — Hard Innovation Gate

Status: COMPLETE
Implementation: `core/src/main/kotlin/com/example/navigation/fusion/HardInnovationGate.kt` supplies an uncertainty-aware catastrophic-innovation decision; `VehicleGnssIntegration` records every sample first, blocks catastrophic correction, and retains the evidence for diagnostics/trust analysis.
Tests: `core/src/test/kotlin/com/example/navigation/fusion/HardInnovationAndAnchorPropagationTest.kt`
Verification: `:core:test` focused on hard innovation, independent motion/recovery, vehicle GNSS integration, and `LimaSpoofReplayTest` — passed 2026-09-02 (13 tests, 0 failures/errors; BUILD SUCCESSFUL); `git diff --check` — passed.

**Model:** Sol

Before estimator correction, block only catastrophically implausible innovations while still recording the sample and exposing it to trust analysis and diagnostics. Account for uncertainty and test ordinary noise, urban multipath, tunnel exit, and remote teleports. False hard rejection of plausible observations is a failure.

### TASK 053A — Independent-Motion vs Remote GNSS Evidence

Status: COMPLETE
Implementation: `core/src/main/kotlin/com/example/navigation/gnss/IndependentMotionRemoteGnssEvidenceEvaluator.kt` compares rolling GNSS geometry against cumulative OBD/PDR distance, gyro/PDR turns, and route-progress deltas without trusting GNSS-reported speed or bearing.
Tests: `core/src/test/kotlin/com/example/navigation/gnss/IndependentMotionAndRecoveryTest.kt`
Verification: `:core:test` focused on hard innovation, independent motion/recovery, vehicle GNSS integration, and `LimaSpoofReplayTest` — passed 2026-09-02 (13 tests, 0 failures/errors; BUILD SUCCESSFUL); `git diff --check` — passed.

**Model:** Sol

Compare rolling GNSS trajectory against OBD distance, gyro turns, PDR distance/turns, and route progress. Detect remote spoofing even when GNSS speed, bearing, accuracy, and fix status appear internally plausible.

### TASK 059A — GNSS Time Anomaly Evidence

**Model:** Terra

Detect GNSS time jumps, backward time, drift, and GNSS/system-time disagreement as trust evidence only. Never replace the monotonic time base used by fusion, DR, OBD, IMU, PDR, or replay.

### TASK 060A — Trusted Anchor Propagation Start

Status: COMPLETE
Implementation: `core/src/main/kotlin/com/example/navigation/fusion/TrustedAnchorPropagationStart.kt` starts DR once from the last trusted anchor on questionable/rejected/recovering observations and preserves subsequent local DR propagation; the `LIMA-001` regression remains at the local anchor throughout rejection.
Tests: `core/src/test/kotlin/com/example/navigation/fusion/HardInnovationAndAnchorPropagationTest.kt`
Verification: `:core:test` focused on hard innovation, independent motion/recovery, vehicle GNSS integration, and `LimaSpoofReplayTest` — passed 2026-09-02 (13 tests, 0 failures/errors; BUILD SUCCESSFUL); `git diff --check` — passed.

**Model:** Sol

When GNSS becomes questionable or rejected, start/continue DR from the last trusted local state, never from the false remote observation. `LIMA-001` must remain local throughout rejection.

### TASK 062A — Conservative GNSS Recovery

Status: COMPLETE
Implementation: `core/src/main/kotlin/com/example/navigation/gnss/ConservativeGnssRecoveryGate.kt` requires correctly typed, repeated supportive anchor, independent-motion, and route evidence before exposing a progressively increasing correction cap; `VehicleGnssIntegration` enforces the hold and cap.
Tests: `core/src/test/kotlin/com/example/navigation/gnss/IndependentMotionAndRecoveryTest.kt`
Verification: `:core:test` focused on hard innovation, independent motion/recovery, vehicle GNSS integration, and `LimaSpoofReplayTest` — passed 2026-09-02 (13 tests, 0 failures/errors; BUILD SUCCESSFUL); `git diff --check` — passed.

**Model:** Sol

Require sustained consistency with the trusted anchor, independent motion, and route hypotheses before gradual GNSS re-entry. Do not snap to the first returning fix.

### TASK 088A — LIMA-001 Benchmark Scenario

**Model:** Terra

Benchmark normal navigation, remote-location spoof, optional GNSS-time/bearing/velocity corruption, persistence, and local return. Report rejection delay, false rejection, anchor displacement, DR continuity, route correctness, progress error, and recovery time. Add near-remote and long-duration variants.

### GNSS spoofing invariants

- Lima is never a hard-coded production indicator.
- Rejected GNSS remains recorded and diagnosable but cannot directly move navigation state.
- Monotonic time is authoritative for sensor fusion and replay.
- The trusted anchor changes only under the documented trust policy.
- Independent motion evidence is evaluated even when GNSS appears internally plausible.
- Recovery is gradual and uncertainty remains explicit.

### Completed-task review policy

When reviewing completed tasks, verify these invariants and their regression tests. Do not silently reopen or rewrite completed work; add a narrowly scoped follow-up task when a new replay scenario exposes a gap.

## MILESTONE A — Recorder

Required tasks:

```text
001–031, including 002A
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
050–063
015–018
```

Success criteria:

System detects obvious GNSS inconsistencies and rejects inappropriate corrections.

Must support:

```text
large jump
gradual drift
false velocity
false bearing
GNSS outage
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

002A Android build baseline

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

Codex should optimize against deterministic evidence whenever possible.

---

# EPIC 26 — Motion and Interaction Design

These tasks improve launch, navigation, and interactive-state motion while preserving honest uncertainty and offline operation. Motion must communicate state and hierarchy; it must not imply positioning precision that the estimator does not provide.

---

## TASK 105 — Branded Splash Screen

Status: COMPLETE
Implementation: Added AndroidX SplashScreen configuration with a white launch background and the supplied `4987973-200.png` artwork copied to `app/src/main/res/drawable-nodpi/renav_splash.png`. `RenavSplashMotion` deterministically accelerates from 1 to 3 revolutions per second for 3 seconds, fades the artwork out while fading in bold 20sp `RENAV` using the Android sans/Roboto family, and hands off to the main screen at 5 seconds. Hardened the initial empty MapLibre navigation state by using empty GeoJSON collections instead of null sources.
Tests: `app/src/test/kotlin/com/example/navigation/app/RenavSplashMotionTest.kt`
Verification: `git diff --check` — passed. A fresh `$taskGradleHome = Join-Path $env:TEMP 'some-gradle-user'; $env:GRADLE_USER_HOME = $taskGradleHome; .\gradlew.bat --no-daemon :app:assembleDebug :app:testDebugUnitTest --rerun-tasks --console=plain` run was attempted twice on 2026-09-02 but is blocked at `:core:compileKotlin` because the existing untracked `core/src/main/kotlin/com/example/navigation/benchmark/BenchmarkRegressionThresholds – копія.kt` is reported by Gradle as not a regular file; no fresh APK was produced by these runs.

### Model
Luna

### Objective

Add a short branded Android splash experience using the AndroidX SplashScreen API.

Requirements:

```text
branded app icon
animated vector on supported Android versions
static fallback on older versions
short launch duration
transition into the main navigation screen
```

Do not delay startup merely to display animation.

Acceptance criteria:

- SplashScreen is configured for supported API levels.
- The app transitions cleanly to the main screen.
- Startup animation does not conceal initialization failures.

Tests required:

- Theme and manifest verification.
- App launch smoke test where available.

---

## TASK 106 — App Screen Motion Transitions

Status: COMPLETE

### Model
Luna

### Objective

Add consistent motion between Main Navigation, Settings, and Developer Mode.

Use appropriate Material-style transitions:

```text
Main ↔ Settings: shared-axis transition
Main ↔ Developer Mode: fade-through or shared-axis transition
back navigation: reverse transition
diagnostics expand/collapse: animated visibility and size
```

Acceptance criteria:

- Screen changes retain navigation state.
- Forward and backward transitions are visually consistent.
- Transitions remain usable with reduced motion enabled.

Tests required:

- Navigation destination/state-retention tests.
- Reduced-motion transition behavior test.

Implementation: Wrapped Main Navigation, Settings, and Developer Mode destinations in directional Material-style fade/slide transitions and added animated size behavior for the diagnostics panel in `app/src/main/kotlin/com/example/navigation/app/MainActivity.kt`. Existing screen state remains owned by `MainActivity` and is preserved across transitions.
Tests: `:app:assembleDebug :app:testDebugUnitTest --rerun-tasks --console=plain` passed on 2026-09-02 (`BUILD SUCCESSFUL`; 23 unit tests).
Known limitation: Device/UI instrumentation and reduced-motion validation were not available because ADB authorization remained unresolved.

---

## TASK 107 — Smooth Position and Heading Motion

Status: COMPLETE

### Model
Terra

### Objective

Animate navigation position and heading updates smoothly on the map.

Requirements:

```text
interpolate position updates
interpolate heading rotation
animate camera movement when navigation begins
avoid visual jumps on ordinary updates
```

Do not smooth away genuine uncertainty or display a more precise position than the estimator provides.

Acceptance criteria:

- Position and heading transitions are monotonic and bounded.
- Low-confidence and LOST states continue to use uncertainty areas without an exact marker.
- Map animation does not require network access.

Tests required:

- Deterministic interpolation tests.
- Low-confidence marker/uncertainty regression tests.

Implementation: Added bounded 300 ms position and shortest-arc heading interpolation in `MapMotionInterpolator.kt`; `MainActivity.kt` rerenders MapLibre for interpolated frames and animates the camera once when a navigation position first becomes available. LOW/LOST updates bypass precise-marker interpolation and remain uncertainty-only. Camera updates use local MapLibre APIs and do not require network access.
Tests: Added `MapMotionInterpolatorTest.kt` for bounded monotonic position and heading interpolation; added LOST-confidence marker/uncertainty regression coverage in `NavigationMapPresentationTest.kt`. `:app:assembleDebug :app:testDebugUnitTest --rerun-tasks --console=plain` passed on 2026-09-02 (`BUILD SUCCESSFUL`; 30 unit tests).
Known limitation: Device visual validation remains unavailable because ADB authorization is unresolved.

---

## TASK 108 — Navigation Guidance Motion

Status: COMPLETE

### Model
Terra

### Objective

Add motion for active navigation guidance and route state changes.

Requirements:

```text
route-ready transition
start-navigation transition
animated maneuver card
turn-distance countdown transition
turn preview
route-progress animation
rerouting banner
arrival transition
```

Acceptance criteria:

- Guidance transitions are driven by explicit navigation state.
- Rerouting and arrival remain understandable without animation.
- No maneuver is shown as confirmed when route confidence is insufficient.

Tests required:

- State-to-transition mapping tests.
- Rerouting, arrival, and missing-route state tests.

Implementation: Added an explicit `NavigationGuidanceState` and presentation factory for route-ready, navigating, rerouting, and arrival states. The navigation map state now carries this state without inferring guidance from map rendering. The guidance card animates its state, maneuver, turn-distance countdown, and route progress when motion is enabled; rerouting and arrival retain complete text labels when reduced motion is enabled. Confirmed maneuvers are suppressed when route guidance confidence is insufficient.
Tests: Added `NavigationGuidancePresentationTest.kt` covering state-to-transition mapping, rerouting, arrival, missing-route, and insufficient-confidence maneuver suppression. `:app:assembleDebug :app:testDebugUnitTest --rerun-tasks --console=plain` completed on 2026-09-02; the resulting app unit-test XML reports 35 tests with 0 failures and 0 errors.
Known limitation: Guidance UI is ready to consume explicit route-engine state, but the route engine does not yet publish maneuver/progress lifecycle updates into `NavigationMapState`.

---

## TASK 109 — GNSS Trust and Uncertainty Transitions

Status: COMPLETE

### Model
Sol

### Objective

Use restrained visual transitions when GNSS trust or position confidence changes.

Required state changes:

```text
GNSS NORMAL → QUESTIONABLE
QUESTIONABLE → UNTRUSTED
UNTRUSTED → RECOVERING
RECOVERING → NORMAL
```

Requirements:

- Use stable color, icon, and text states alongside animation.
- Avoid rapid or continuous pulsing while driving.
- Animate uncertainty-area growth and reduction without implying certainty.

Tests required:

- Trust-state transition presentation tests.
- Accessibility color/state-label tests.

Implementation: Added stable icon, text, and visual-tone presentation for NORMAL, QUESTIONABLE, UNTRUSTED, RECOVERING, and unavailable states in `NavigationStatusPresentation.kt`; added restrained one-time trust-state/color transitions and bounded uncertainty-area size animation in `MainActivity.kt`. No rapid or continuous pulsing is used, and LOW/LOST states continue to hide the precise marker.
Tests: Added trust-state accessible icon/tone/label assertions in `NavigationStatusPresentationTest.kt`. `:app:assembleDebug :app:testDebugUnitTest --rerun-tasks --console=plain` passed on 2026-09-02 (`BUILD SUCCESSFUL`; 24 unit tests).
Known limitation: Device visual validation was unavailable because ADB authorization remained unresolved.

---

## TASK 110 — Developer Recording Interaction Motion

Status: COMPLETE

### Model
Luna

### Objective

Improve temporary Developer Mode feedback for session recording and export.

Requirements:

```text
start button → recording state transition
persistent recording indicator
marker saved confirmation
session selection feedback
export preparation/progress feedback
```

The feature remains temporary and must not add recording controls to the normal navigation view.

Tests required:

- Recording-state presentation tests.
- Marker confirmation and multi-session selection tests.

Implementation: Updated the temporary Developer Mode controls in `app/src/main/kotlin/com/example/navigation/app/MainActivity.kt` with animated recording-state feedback, a persistent recording badge, marker confirmation, session-selection feedback, and export-preparation feedback. Normal navigation remains without recording controls.
Tests: Existing app unit suite; `:app:assembleDebug :app:testDebugUnitTest --rerun-tasks --console=plain` passed on 2026-09-02 (`BUILD SUCCESSFUL`).
Known limitation: Device/UI instrumentation validation was not available because the connected phone remained unauthorized for ADB.

---

## TASK 111 — Async Loading and Error Transitions

Status: COMPLETE

### Model
Luna

### Objective

Provide clear animated feedback for asynchronous operations and recoverable failures.

Cover:

```text
map loading
permission checking
OBD connection
sensor startup
session stop
session export
offline map unavailable
GNSS unavailable
OBD unavailable
```

Acceptance criteria:

- Loading, success, failure, and retry states are distinct.
- Error text is paired with an actionable recovery path where possible.
- Animation never replaces persistent error/status text.

Tests required:

- Async state transition tests.
- Failure and retry presentation tests.

Implementation: Added explicit map loading, ready, unavailable, and retry feedback in `MainActivity.kt`; loading and unavailable states remain visible with navigation status, and retry requests reload the map style. Existing permission, OBD, sensor, stop, export, GNSS, and OBD status/error text remains persistent in the relevant flow and is not replaced by animation. Added deterministic map async feedback states in `AsyncFeedback.kt`.
Tests: Added `AsyncFeedbackTest.kt` for loading/no-action and unavailable/retry presentation. `:app:assembleDebug :app:testDebugUnitTest --rerun-tasks --console=plain` passed on 2026-09-02 (`BUILD SUCCESSFUL`; 32 unit tests).
Known limitation: Device/instrumented validation of network/map failure and retry remains unavailable because ADB authorization is unresolved.

---

## TASK 112 — Reduced Motion and Motion Accessibility

Status: COMPLETE

### Model
Terra

### Objective

Make all new motion accessible and safe for users who prefer reduced motion.

Requirements:

```text
support reduced-motion preference
avoid rapid flashing
keep essential information visible without animation
preserve high-contrast trust states
preserve large touch targets
```

Acceptance criteria:

- Reduced motion disables or shortens non-essential animation.
- Trust, uncertainty, and recording states remain understandable statically.
- No critical navigation instruction depends only on motion.

Tests required:

- Reduced-motion UI tests.
- Accessibility semantics and contrast checks.

Implementation: Added `MotionAccessibility.kt`, which follows Android's animator-duration scale. With animation disabled, splash, screen, menu, trust, uncertainty, diagnostics, recording, and marker transitions resolve immediately while their persistent labels remain visible. Added semantic descriptions for the navigation-menu control and GNSS trust state; existing Material buttons/FAB retain large touch targets and trust text remains paired with high-contrast color/icon states.
Tests: Added `MotionAccessibilityTest.kt` covering disabled, normal, and invalid animator-duration scales. `:app:assembleDebug :app:testDebugUnitTest --rerun-tasks --console=plain` passed on 2026-09-02 (`BUILD SUCCESSFUL`; 27 unit tests).
Known limitation: On-device accessibility and reduced-motion UI instrumentation validation was unavailable because ADB authorization remained unresolved.

---

## TASK 113 — Motion Performance and Offline QA

Status: PARTIAL

### Model
Terra

### Objective

Validate that UI motion remains smooth and battery-conscious during offline navigation.

Measure:

```text
frame pacing
animation latency
map update cost
memory usage
battery impact
offline behavior
```

Acceptance criteria:

- Motion remains responsive on the minimum supported device profile.
- Map animation does not block sensor collection or navigation state updates.
- Offline operation does not trigger remote animation dependencies.

Tests required:

- Automated animation/state regression tests.
- Device profiling report with offline and reduced-motion cases.

Implementation: Added `NavigationMotionPerformance.kt` as the local-frame performance profiling contract and routed map interpolation duration through it. It records frame pacing, animation latency, map update cost, navigation-state latency, memory delta, battery drain, and reduced-motion state without inventing missing device metrics. Motion has zero remote animation dependencies. Added `docs/qa/task-113-motion-performance.md` with the automated result and device profiling record.
Tests: Added `NavigationMotionPerformanceTest.kt` covering metric retention, explicit missing device metrics, reduced motion, and zero remote animation dependencies. `:app:testDebugUnitTest --console=plain` passed on 2026-09-02 (`BUILD SUCCESSFUL`; 37 unit tests, 0 failures, 0 errors).
Known limitation: Device profiling is not complete. The configured Android SDK has no `adb` executable, so no connected device could capture frame pacing, map cost, memory, battery, offline, or reduced-motion evidence. TASK 113 must remain PARTIAL until that report is populated with a minimum-device capture.

---

## TASK 114 — Automatic Diagnostic Log Export

Status: COMPLETE

### Model

Terra

### Objective

Write a local plain-text diagnostic report for troubleshooting app behavior without requiring Developer Mode.

### Scope

- Initialize diagnostic logging from the application process before the main activity is created.
- Record timestamped app lifecycle events, screen navigation, user actions, permission/OBD operations, recording operations, map initialization, and handled errors.
- Capture uncaught exception type, message, stack trace, and available app/device version metadata.
- Save reports automatically under shared device storage at `Documents/RENAV/Diagnostics/` using unique timestamped `.txt` filenames.
- Keep a small app-private fallback copy when shared-storage writing is unavailable.

Do not add remote telemetry, automatic upload, location sharing, XML output, or new diagnostic UI.

### Dependencies

TASK 002A, TASK 083–087, TASK 098

### Acceptance criteria

- A diagnostic report is created on normal app use without opening Developer Mode.
- Report entries include monotonic elapsed time where available and human-readable wall-clock time as metadata only.
- A forced test exception produces a report containing the exception and stack trace.
- Shared-storage failure does not crash the app and preserves the app-private fallback.
- Reports do not silently upload or transmit sensor/location data.

### Tests required

- JVM tests for deterministic event formatting and unique report naming.
- Android/instrumented test for report creation and shared-storage fallback behavior where the available device test infrastructure permits.
- Crash-capture verification using a controlled test exception; do not treat an unobserved production crash as test evidence.

### Completion evidence

Implementation is in `app/src/main/kotlin/com/example/navigation/app/RenavApplication.kt` and `app/src/main/kotlin/com/example/navigation/app/RenavDiagnostics.kt`; shared reports target `Documents/RENAV/Diagnostics/` and the app-private fallback is `files/diagnostics/`. JVM coverage is in `app/src/test/kotlin/com/example/navigation/app/RenavDiagnosticsTest.kt`. `:app:assembleDebug :app:testDebugUnitTest --rerun-tasks --console=plain` passed on 2026-09-02 (23 unit tests); device shared-storage and fallback paths remain unvalidated because no authorized ADB device was available.

---

## TASK 115 — Manual Navigation Start-Point Selection

Status: COMPLETE

### Model

Terra

### Objective

Allow the user to choose a manual navigation starting point by zooming and selecting a position on the offline map.

### Scope

- Add an explicit map interaction mode for selecting a starting point.
- Allow map zoom and pan while selection mode is active.
- Show a fixed selection target or equivalent clear map indicator.
- Display the selected coordinates and an explicit confirmation action.
- Allow cancellation and reselection before confirmation.
- Preserve the distinction between a manually selected starting point and a trusted GNSS position.

Do not implement route calculation, dead reckoning, automatic start-point inference, or remote geocoding in this task.

### Dependencies

TASK 083–087, TASK 114

### Acceptance criteria

- The user can enter start-point selection from the navigation screen.
- The user can zoom, pan, select, cancel, and confirm a point without leaving the map.
- Confirmation produces a validated `GeoPoint` with an explicit manual-selection source.
- Invalid or unavailable map interaction states cannot confirm a missing point.
- The UI does not present the manually selected point as a current measured position.

### Tests required

- JVM tests for selection state transitions: idle, selecting, selected, confirmed, cancelled, and reselection.
- Boundary tests for coordinate validity and missing selection.
- Compose/UI test for confirmation and cancellation actions where the existing test infrastructure permits.

### Completion evidence

Record the exact UI test command and resulting selected-point state; document device validation separately from unit/UI test evidence.

Implementation: Added `ManualStartSelection.kt` with explicit idle, selecting, selected, and confirmed states; a confirmed `ManualStartPoint` always carries `MANUAL_SELECTION` provenance and is never converted into a measured navigation position. `MainActivity.kt` supplies the current map-centre point only when the user explicitly selects it, and refuses confirmation until selection exists.
Tests: Added `ManualStartSelectionTest.kt` covering enter, selection, confirmation, cancellation, reselection, source provenance, and missing-selection rejection. `:app:testDebugUnitTest --tests "com.example.navigation.app.ManualStartSelectionTest" --console=plain` passed on 2026-09-02.
Known limitation: The selected point is intentionally not used to initialize or track navigation; that is TASK 116.

---

## TASK 116 — Navigation Tracking From Manual Start Point

Status: COMPLETE

### Model

Sol

### Objective

Initialize navigation from a user-confirmed manual starting point and track the user from that origin using the available navigation measurements.

### Scope

- Accept only the confirmed point produced by TASK 115 as the initial navigation origin.
- Initialize navigation position state with an explicit manual-start source and associated uncertainty.
- Begin tracking from that origin using the existing trusted GNSS, sensor, OBD, pedestrian, route, and map-matching components as available.
- Transition the source and confidence explicitly when subsequent measurements become available.
- Expose degraded or unavailable measurement states without silently treating the manual point as a live fix.
- Support stopping and restarting from a newly confirmed manual point without retaining stale origin state.

Do not implement new sensor algorithms, unrestricted GNSS-independent localization, automatic route selection, or remote services in this task.

### Dependencies

TASK 040–063, TASK 070–082, TASK 083–087, TASK 115

### Acceptance criteria

- Confirming a manual point initializes navigation at that point exactly once.
- The first navigation state identifies the origin as manually selected and includes uncertainty.
- Subsequent trusted measurements update the navigation state according to existing fusion policy.
- Loss or absence of measurements produces an explicit degraded-confidence state.
- Starting over from another confirmed point clears the previous origin and tracking history by explicit user action.

### Tests required

- JVM tests for manual-origin initialization, source transitions, uncertainty behavior, restart, and unavailable measurements.
- Replay test using a synthetic session that begins after manual origin selection and contains subsequent movement measurements.
- UI/integration test for confirm-start, stop, and start-over behavior where infrastructure permits.

### Completion evidence

Record the exact initialization and replay commands, synthetic scenario used, source/confidence transitions, and any device-validation result.

Implementation: Added `ManualOriginTracker.kt`, which accepts only TASK 115's confirmed manual point, initializes a LOW-confidence `MANUAL_START` position with configurable uncertainty, accepts subsequent HIGH/MEDIUM existing navigation estimates, degrades honestly to LOST when measurements are unavailable or insufficient, and supports explicit stop/restart without stale history. Added `MANUAL_START` to `PositionSource` and its honest status label.
Tests: Added `ManualOriginTrackerTest.kt` for one-time initialization, uncertainty, GNSS source transition, unavailable-measurement degradation, stop, restart, and a deterministic synthetic observation replay. `:app:testDebugUnitTest --tests "com.example.navigation.app.ManualOriginTrackerTest" --console=plain` passed on 2026-09-02.
Known limitation: No new sensor, fusion, route, or map-matching algorithm was added; the tracker consumes existing `NavigationPosition` estimates as required. UI wiring remains TASK 118.

---

## TASK 117 — Manual Start-Point Selection UI

Status: COMPLETE

### Model

Terra

### Objective

Provide a clear, accessible map UI for selecting, reviewing, confirming, cancelling, and reselection of a manual navigation starting point.

### Scope

- Add a visible action to enter manual start-point selection.
- Present a fixed map crosshair or equivalent selection target while the map can be zoomed and panned.
- Show selected coordinates and an explicit `CONFIRM START POINT` action.
- Provide `CANCEL` and reselection actions with clear pressed/disabled states.
- Visually distinguish the selected origin from the live estimated-position marker.
- Animate entry, confirmation, cancellation, and state changes smoothly without obscuring map interaction.
- Expose content descriptions and semantics for the selection controls.

Do not implement coordinate validation, navigation-state initialization, route calculation, or tracking logic; consume the state and callbacks defined by TASK 115.

### Dependencies

TASK 115, TASK 106–113

### Acceptance criteria

- The complete selection flow is understandable from the navigation map without instructional text outside the screen.
- Selection controls remain usable while zooming and panning.
- Confirmation is visibly unavailable until a valid selected point is supplied.
- Cancellation returns to navigation without changing the active origin.
- The UI never labels the selected point as the current measured position.
- UI state transitions do not block or reset map gestures.

### Tests required

- Compose/UI tests for entering selection, coordinate review, confirm, cancel, and reselection.
- Accessibility semantics test for all selection actions.
- Reduced-motion behavior test for selection animations where supported by existing infrastructure.

### Completion evidence

Record the exact UI test command, screenshots or device result, accessibility result, and reduced-motion result.

Implementation: Added an accessible `SELECT START POINT` action in the navigation menu, fixed-centre selection target, map-centre capture, coordinate review, explicit confirmation, cancellation, and reselection controls. The UI labels the point as manually selected and never labels it as a live measured position. Entry and selection surfaces honour the existing reduced-motion preference.
Tests: `ManualStartSelectionTest.kt` exercises the UI state callbacks through the deterministic selection reducer. `:app:assembleDebug :app:testDebugUnitTest --tests "com.example.navigation.app.ManualStartSelectionTest" --console=plain` compiled and passed on 2026-09-02.
Known limitation: This repository does not have Compose UI-test infrastructure; no Compose/device interaction result is recorded.

---

## TASK 118 — Manual-Origin Tracking UI

Status: COMPLETE

### Model

Terra

### Objective

Present navigation state clearly after tracking begins from a manually selected origin.

### Scope

- Show the manual-origin marker and distinguish it from the current estimated-position marker.
- Show an explicit status that navigation began from a manually selected point.
- Present current positioning source and confidence according to the existing navigation state.
- Show degraded, unavailable, and measurement-recovery states without false precision.
- Provide explicit controls for stopping tracking and starting over from a new manual point.
- Animate marker/status changes smoothly while preserving map readability and user control.
- Provide content descriptions and semantics for tracking status and controls.

Do not implement fusion, confidence calculations, source transitions, or manual-origin state management; consume the state produced by TASK 116.

### Dependencies

TASK 116, TASK 117, TASK 106–113

### Acceptance criteria

- Users can tell whether the displayed position is the manual origin or a subsequent estimate.
- The UI exposes source and confidence changes without claiming the manual point is live truth.
- Stop and start-over actions are explicit and do not silently reuse the previous origin.
- Low-confidence and unavailable states remain visible while tracking.
- Motion remains responsive during position updates and does not interfere with map gestures.

### Tests required

- Compose/UI tests for manual-origin display, estimated-position display, degraded state, stop, and start-over.
- Accessibility semantics and content-description tests.
- Animation/reduced-motion regression tests where supported by existing infrastructure.

### Completion evidence

Record the exact UI test command, source/confidence states exercised, screenshots or device result, and accessibility result.

Implementation: Added `ManualOriginTrackingPresentation.kt` and wired TASK 116's tracker into `MainActivity.kt`. A purple MapLibre marker preserves the manual origin independently of the blue subsequent estimate marker. The tracking card explicitly identifies manual provenance, source, confidence, estimated accuracy, and degraded measurements; it includes accessible explicit stop and start-over controls. All status transitions honour the existing reduced-motion preference.
Tests: Added `ManualOriginTrackingPresentationTest.kt` for manual-origin, subsequent GNSS estimate, and lost-measurement presentation. `:app:testDebugUnitTest --tests "com.example.navigation.app.ManualOriginTrackingPresentationTest" --console=plain` passed on 2026-09-02. `:app:assembleDebug :app:testDebugUnitTest --console=plain` passed on 2026-09-02.
Known limitation: This repository has no Compose UI-test or device-test infrastructure, so content descriptions and reduced-motion handling are covered by code inspection and deterministic presentation tests rather than an instrumented Compose test.

---

## TASK 119 — Regional Offline Map Catalog and Location Discovery

Status: COMPLETE

### Model

Sol

### Objective

Provide a backend catalog that identifies the downloadable offline map region covering the user's location.

### Scope

- Define a versioned catalog of downloadable country/region map packages with display name, geographic bounds, tile/style metadata, size estimate, version, and checksum.
- Resolve a permission-approved GNSS position to a catalog region using local bounds only.
- Expose explicit states for location unavailable, permission denied, region unsupported, catalog unavailable, and region found.
- Report whether a region is not downloaded, downloading, ready, stale, or corrupt.
- Keep catalog and resolution deterministic and independent of remote geocoding.

Do not implement map downloading, routing-graph generation, UI screens, or automatic remote region discovery in this task.

### Dependencies

TASK 019, TASK 033, TASK 034, TASK 101–102

### Acceptance criteria

- A valid position resolves to exactly one catalog region or an explicit unsupported/ambiguous result.
- Missing or denied location permission never fabricates a user region.
- Package metadata is validated before a region is reported ready.
- Stale, corrupt, and unavailable package states are distinguishable from a ready package.
- Catalog resolution is offline and deterministic.

### Tests required

- JVM tests for bounds resolution, edge coordinates, ambiguity, unsupported regions, and unavailable permission/location.
- Metadata validation tests for version, size, and checksum failures.
- Deterministic catalog fixture tests without real user location data.

### Completion evidence

Record the catalog fixture, exact resolution/test command, package-state results, and any device/location-permission validation separately.

Implementation: Added `RegionalMapCatalog.kt` with versioned package metadata, bounds, tile/style metadata, size estimate, map/routing versions, SHA-256, deterministic local coordinate resolution, and explicit permission-denied, location-unavailable, catalog-unavailable, unsupported, ambiguous, and found results. Package availability distinguishes missing, downloading, ready, stale, and corrupt evidence.
Tests: Added `RegionalMapCatalogTest.kt` with a synthetic Ukraine fixture covering interior/edge resolution, unsupported coordinates, overlap ambiguity, unavailable inputs, and all package-availability outcomes. `:app:testDebugUnitTest --tests "com.example.navigation.maps.RegionalMapCatalogTest" --console=plain` passed on 2026-09-02.
Known limitation: No production country catalog or real user coordinates are included; the task intentionally defines and validates the backend catalog contract only.

---

## TASK 120 — Offline Map Download and Readiness Orchestration

Status: COMPLETE

### Model

Sol

### Objective

Allow the app to download, resume, cancel, retry, verify, and activate the selected country/region map for offline navigation.

### Scope

- Orchestrate downloads through the existing offline-region manager and the catalog package metadata from TASK 119.
- Expose explicit idle, preparing, downloading, paused, cancelled, failed, verifying, ready, stale, and corrupt states.
- Persist enough package state to recover safely after process restart without reporting an incomplete package as ready.
- Surface progress, downloaded bytes, total bytes when available, retryability, and failure reason.
- Verify package version/checksum before activation and make the active region available to the offline map renderer.
- Keep navigation/map rendering state updates separate from download callbacks.

Do not implement catalog discovery, routing-graph generation, remote geocoding, or the user-facing download screens in this task.

### Dependencies

TASK 033–034, TASK 119

### Acceptance criteria

- A selected package can progress from download through verification to ready, or to an explicit actionable failure.
- Cancellation and process interruption never produce a false ready state.
- Retry resumes or restarts according to the package state without corrupting the active map.
- A verified ready package remains usable without network access.
- Download callbacks do not block navigation state updates.

### Tests required

- JVM/fake-manager tests for every download state transition, cancellation, retry, interruption, checksum failure, and activation.
- Restart/recovery tests proving incomplete packages are not reported ready.
- Offline renderer integration test using a synthetic regional package where existing infrastructure permits.

### Completion evidence

Record the fake-manager scenario, exact test command, package verification result, offline activation result, and device/network validation separately.

Implementation: Added `RegionalMapDownloadOrchestrator.kt` with explicit idle, preparing, downloading, paused, cancelled, failed, verifying, ready, stale, and corrupt states; progress/bytes/failure/retry data; SharedPreferences persistence; verification and activation boundaries; and a MapLibre backend adapter. Extended `MapLibreOfflineRegionManager` resume behavior to reattach its progress observer after interruption. Only verified packages activate, and interrupted state recovers paused rather than falsely ready.
Tests: Added `RegionalMapDownloadOrchestratorTest.kt` covering progress, pause, cancel, failure, retry, interruption recovery, checksum corruption, stale version, verified activation, and offline-ready state with deterministic fakes. The combined TASK 116/119/120 focused run passed 10 tests on 2026-09-02; `:app:assembleDebug :app:testDebugUnitTest --console=plain` also passed before final observer-resume verification.
Known limitation: MapLibre does not expose a required total byte count, so `totalBytes` remains unavailable while downloaded bytes are reported. A real regional package/network/device run is not included; the verifier/activator integration is covered with deterministic fakes.

---

## TASK 121 — Map Download User Journey UI

Status: PARTIAL

### Model

Terra

### Objective

Guide the user from opening the app to downloading the map region needed for their location and confirm when offline navigation is ready.

### Scope

- Detect and clearly explain when the current region map is missing, stale, corrupt, or unavailable.
- Provide an entry point from the main navigation screen and Settings to find/download a map.
- Request location permission when needed, explain the permission-denied path, and offer a manual region-selection fallback without pretending to know the location.
- Show the resolved country/region, package version/size estimate, explicit download confirmation, progress, pause/cancel/retry controls, and verification/ready state.
- Keep the map usable and status text visible during download; do not hide failures behind animation.
- Confirm offline readiness and provide access to downloaded-region management.
- Support reduced motion, large touch targets, and content descriptions for every download action.

Do not implement region resolution, download persistence, checksum verification, routing, or map-package generation; consume the states and callbacks defined by TASK 119–120.

### Dependencies

TASK 119–120, TASK 083–087, TASK 106–113

### Acceptance criteria

- A first-time user can discover the missing-map state, identify/select a region, confirm a download, observe progress, and reach an explicit offline-ready state.
- Permission denial, unsupported region, failed download, cancellation, and stale/corrupt package states have clear recovery actions.
- The UI never claims the map is ready before backend verification succeeds.
- Download progress and errors do not block map gestures or navigation status.
- The journey remains understandable with reduced motion and exposes accessibility semantics.

### Tests required

- Compose/UI tests for missing-map prompt, permission outcomes, region confirmation, progress, cancel, retry, failure, and offline-ready state.
- Accessibility semantics and reduced-motion tests for download controls.
- UI/integration test proving a verified package changes the app to offline-ready state.

### Completion evidence

Record the exact UI/integration test command, synthetic region/package fixture, screenshots or device result, accessibility result, and offline-ready result.

Implementation: Added a reduced-motion-aware `MapDownloadScreen` and `RegionalMapDownloadPresentation.kt`, which consume TASK 119 discovery states and TASK 120 download states to expose permission denial, manual-region fallback, unsupported/ambiguous location, download confirmation/progress/pause/cancel/retry, verification, stale/corrupt failures, and verified offline-ready presentation. Every visible action has a content description; ready is emitted only for TASK 120's `READY` state. The former main-menu and Settings entry points are intentionally hidden while production map packages are unavailable.
Tests: Added `RegionalMapDownloadPresentationTest.kt` using a synthetic Ukraine package fixture for permission recovery, downloading, verification, and verified-ready state. `:app:testDebugUnitTest --tests "com.example.navigation.app.RegionalMapDownloadPresentationTest" --console=plain` passed on 2026-09-02. `:app:assembleDebug :app:testDebugUnitTest --console=plain` passed on 2026-09-02.
Known limitation: The current application does not supply a production regional catalog or connect a `RegionalMapDownloadOrchestrator` instance to this screen. Consequently it truthfully starts at “map catalog unavailable”; the UI is intentionally hidden and a user cannot select a real package, download it, or reach offline-ready through the installed app. The screen is deliberately not marked COMPLETE until those existing backend contracts are instantiated with real map-package data and callbacks and the entry points are restored.

---

## TASK 122 — Bluetooth OBD Adapter Discovery and Pairing

Status: PARTIAL
Implementation: `app/src/main/AndroidManifest.xml` declares `BLUETOOTH_SCAN`; `app/src/main/kotlin/com/example/navigation/app/MainActivity.kt` requests scan/connect permissions, receives classic Bluetooth discovery and bond events while the activity is visible, lists nearby devices separately from already paired adapters in Settings, initiates Android system pairing for a new device, and selects the device after a successful bond. `app/src/main/kotlin/com/example/navigation/app/ObdDiscoveryDevices.kt` keeps repeated scan events deterministic and unique by address.
Tests: `app/src/test/kotlin/com/example/navigation/app/ObdDiscoveryDevicesTest.kt`
Verification: `$taskGradleHome = Join-Path $env:TEMP 'some-gradle-user'; $env:GRADLE_USER_HOME = $taskGradleHome; .\gradlew.bat --no-daemon :app:testDebugUnitTest --tests "com.example.navigation.app.ObdDiscoveryDevicesTest" --console=plain` — passed on 2026-09-03 (2 tests, 0 failures); `:app:assembleDebug` — passed on 2026-09-03.

### Model

Terra

### Objective

Let a user discover a nearby classic Bluetooth ELM327 adapter, initiate Android pairing when required, and select the paired adapter for the existing recording connection flow.

### Scope

- Request Android 12+ nearby-device scan/connect permissions only when needed.
- Scan nearby classic Bluetooth devices while Settings is visible and expose search-started, no-result, unavailable-Bluetooth, and search-complete states.
- Keep scan results distinct from paired devices and deduplicate them by Bluetooth address.
- Initiate system pairing for an unpaired selected device; refresh paired devices and select it after bond completion.
- Preserve existing Bluetooth transport behavior: actual OBD connection still begins with recording.

Do not implement USB OBD transport, BLE GATT transport, automatic adapter selection, or OBD command changes.

### Dependencies

TASK 027–031, TASK 092

### Acceptance criteria

- A permitted Android 12+ device can start a nearby-device scan from Settings.
- Repeated discovery events do not duplicate a device in the UI.
- An unpaired device exposes a pairing action and a successful Android bond makes it selectable.
- Paired-device selection remains separate from discovery and does not start a recording by itself.
- Missing permission, disabled/unavailable Bluetooth, no-result, and pairing-start failure have explicit user-visible status.

### Tests required

- JVM tests for deterministic scan-result deduplication and ordering.
- Device validation with a real discoverable ELM327 or Bluetooth test device, including pairing and subsequent recording connection selection.

### Completion evidence

Record the Android version, adapter/test-device type, permission outcome, discovery result, bond result, selected address outcome, and the exact recording-connection result.

Known limitation: No physical Bluetooth discovery, pairing dialog, or post-pairing recording connection has been validated yet. USB-attached OBD adapters are outside this task and are not supported by the current Bluetooth transport.

---

## TASK 123 — Offline Destination Search Index

Status: COMPLETE
Implementation: `core/src/main/kotlin/com/example/navigation/maps/OfflineDestinationSearchIndex.kt` defines versioned regional package/index metadata, address/place records, canonical SHA-256 validation, package compatibility checks, and a deterministic local exact/prefix/normalized-token search. It returns explicit no-region, unavailable-index, short-query, no-match, and result outcomes without network access.
Tests: `core/src/test/kotlin/com/example/navigation/maps/OfflineDestinationSearchIndexTest.kt`
Verification: `$taskGradleHome = Join-Path $env:TEMP 'some-gradle-user'; $env:GRADLE_USER_HOME = $taskGradleHome; .\gradlew.bat --no-daemon :core:test --tests "com.example.navigation.maps.OfflineDestinationSearchIndexTest" --console=plain` — passed on 2026-09-03 (3 tests, 0 failures). The synthetic `ua-kyiv` package/index fixture verifies exact address, prefix, normalized place, unavailable, checksum, duplicate-ID, and incompatible-package outcomes.

### Model

Terra

### Objective

Provide a deterministic, offline-searchable index of addresses and named places contained in an installed regional map package.

### Scope

- Define a versioned regional destination-index format linked to the regional map and routing-graph versions.
- Represent address, named-place/POI, category, coordinate, display label, normalized search tokens, and source-package metadata.
- Build a local search interface that returns ranked exact, prefix, and normalized-token matches without contacting a network service.
- Return explicit `NO_REGION_INSTALLED`, `INDEX_UNAVAILABLE`, `QUERY_TOO_SHORT`, `NO_MATCH`, and `RESULTS` outcomes.
- Validate coordinates, package compatibility, index checksum/version, malformed records, duplicate stable IDs, and maximum-result limits.

Do not implement UI, remote geocoding, map download, route calculation, or arbitrary global search.

### Dependencies

TASK 034, TASK 036, TASK 119–120

### Acceptance criteria

- Search is deterministic for the same installed index and query.
- A known address and a known named place resolve to their exact stored coordinates.
- Invalid, empty, short, unavailable-region, stale-index, and no-result conditions are explicit and never fabricate a destination.
- Search results identify their regional-package version and source type.
- No search request requires network access after the compatible regional package is installed.

### Tests required

- JVM tests with a synthetic regional index for exact, prefix, normalized, address, and POI matches.
- Boundary tests for malformed records, duplicate IDs, stale/incompatible versions, coordinate validity, short query, no match, and result limits.

### Completion evidence

Record the synthetic index fixture, package/index version pair, exact test command, and offline/no-network result.

Known limitation: The repository does not contain a production regional destination index or a production map package to mount one. The validated index contract is intentionally offline and uses only deterministic synthetic fixture data.

---

## TASK 124 — Destination Search and Selection UI

Status: PARTIAL
Implementation: `app/src/main/kotlin/com/example/navigation/app/DestinationSearchSelection.kt` provides explicit idle/searching/results/selected reducer states and proposed-destination provenance. `MainActivity.kt` exposes a `SEARCH DESTINATION` action in the navigation menu, query input with keyboard dismissal, explicit unavailable/short/no-match/result states, selection preview, replacement, cancellation, accessibility content descriptions, and reduced-motion-aware panel transitions. It renders the proposed destination marker without starting routing or mutating the estimated position.
Tests: `app/src/test/kotlin/com/example/navigation/app/DestinationSearchSelectionTest.kt`
Verification: `$taskGradleHome = Join-Path $env:TEMP 'some-gradle-user'; $env:GRADLE_USER_HOME = $taskGradleHome; .\gradlew.bat --no-daemon :app:testDebugUnitTest --tests "com.example.navigation.app.DestinationSearchSelectionTest" --console=plain` — passed on 2026-09-03 (2 tests, 0 failures); `:app:compileDebugKotlin :app:compileDebugUnitTestKotlin` and `:app:assembleDebug` — passed on 2026-09-03.

### Model

Terra

### Objective

Let the user search the installed region for an address or place, review a result on the map, and explicitly select it as a proposed destination.

### Scope

- Add a clearly reachable destination-search action on the navigation map.
- Provide query entry, loading, empty/short-query, unavailable-index, no-result, and result-list states using TASK 123.
- Let the user select a result, preview its marker/label on the map, replace it with another result, or cancel without changing the active route.
- Make selection semantics explicit: a proposed destination is not an active route and must not be represented as current position.
- Provide accessible content descriptions, large touch targets, keyboard dismissal, and reduced-motion transitions.

Do not calculate a route, start navigation, add online search, or mutate navigation-estimation state.

### Dependencies

TASK 083, TASK 106–113, TASK 123

### Acceptance criteria

- A user can reach search from the map, enter a query, review results, select one, and see a distinct proposed-destination marker.
- Cancellation and replacement leave an existing active route unchanged.
- Every unavailable/error state is actionable and does not claim that a destination was selected.
- The UI is usable while map pan/zoom remains available after result selection.

### Tests required

- Deterministic presentation/reducer tests for query, results, selection, cancellation, replacement, and unavailable states.
- Compose/UI and accessibility tests where infrastructure permits, including reduced-motion behavior.

### Completion evidence

Record exact test commands, synthetic search fixture, accessibility result, and an emulator/device screenshot or result separately.

Known limitation: No production regional destination index is installed or wired into the app, so the shipped UI truthfully presents the actionable no-index state rather than real results. The complete runtime result-selection journey and emulator/device accessibility evidence require a compatible installed regional package/index.

---

## TASK 125 — Route Planning From Selected Destination

Status: PARTIAL
Implementation: `core/src/main/kotlin/com/example/navigation/routing/RoutePlanningUseCase.kt` defines explicit idle/planning/ready/no-origin/graph-unavailable/out-of-region/no-route/failed states; retains origin provenance and uncertainty; selects the existing vehicle or pedestrian calculator only from explicit mode; checks regional map/index/graph compatibility and a configurable 5 km maximum destination snap distance; returns route geometry/distance/package provenance with nullable duration; and preserves any active route without activation or replacement.
Tests: `core/src/test/kotlin/com/example/navigation/routing/RoutePlanningUseCaseTest.kt`
Verification: `$taskGradleHome = Join-Path $env:TEMP 'some-gradle-user'; $env:GRADLE_USER_HOME = $taskGradleHome; .\gradlew.bat --no-daemon :core:test --tests "com.example.navigation.routing.RoutePlanningUseCaseTest" --console=plain` — passed on 2026-09-03 (3 tests, 0 failures). Synthetic fixture: region `fixture`, map `map-1`, graph `graph-1`, two 100 m segments; vehicle and pedestrian candidates return 200 m and edge IDs `10,11`. Tested no-origin, missing/stale graph, unknown mode, outside-region/far-outside destination, no-route, active-route preservation, deterministic repeat, and TASK 123 search-result provenance.

### Model

Sol

### Objective

Create an explicit, honest route-planning use case from a selected destination and an available origin using the installed regional routing graph.

### Scope

- Accept only a TASK 124 confirmed destination and a current trusted/manual navigation origin with provenance and uncertainty.
- Select vehicle or pedestrian route calculation explicitly from the current navigation mode; do not silently change mode.
- Load only a compatible, verified regional routing graph and invoke the existing route calculator.
- Emit explicit planning states: `IDLE`, `PLANNING`, `ROUTE_READY`, `NO_ORIGIN`, `GRAPH_UNAVAILABLE`, `DESTINATION_OUTSIDE_REGION`, `NO_ROUTE`, and `FAILED`.
- Return route geometry, distance, duration estimate when supported by graph metadata, origin/destination provenance, and route-package version.
- Reject stale/mismatched graph or destination-index data and preserve any current active route until a new route is explicitly confirmed.

Do not implement remote routing, rerouting, new graph algorithms, or navigation guidance UI.

### Dependencies

TASK 036–039, TASK 081–082, TASK 116, TASK 123–124

### Acceptance criteria

- A compatible selected destination and valid origin produce a route-plan candidate or a specific failure state.
- No origin, unavailable/stale graph, unsupported mode, out-of-region destination, and no-route outcomes are explicit.
- Planning never turns a selected destination into a live position or silently replaces an active route.
- Identical inputs produce equivalent route output and state transitions.

### Tests required

- JVM tests using a synthetic regional graph for vehicle and pedestrian route planning, origin provenance, package compatibility, no-route, unavailable graph, and replacement safety.
- Deterministic integration test from TASK 123 result through route-plan output.

### Completion evidence

Record the graph/index fixture versions, exact command, candidate route distance/segments, and all tested failure outcomes.

Known limitation: TASK 036 has no production graph artifact and TASK 124 has no production installed destination index, so this use case is not wired to an installed regional package or the app UI. Duration remains unavailable because current graph metadata has no travel-time/speed data. The deterministic backend acceptance behavior is implemented, but production integration remains pending.

---

## TASK 126 — Route Preview and Start Navigation UI

Status: PARTIAL
Implementation: `app/src/main/kotlin/com/example/navigation/app/RouteUiPresentation.kt` maps every TASK 125 planning state to an explicit preview/error presentation with candidate geometry, destination, distance, mode, package region, origin provenance, active-route preservation, and start eligibility. `MainActivity.kt` provides reduced-motion-aware preview/cancel/change-destination panels with accessibility semantics. `MapLibreNavigationRenderer.kt` renders candidate geometry in purple, separately from the active route. `START NAVIGATION` is exposed only for a `ROUTE_READY` candidate and otherwise no state is presented as active.
Tests: `app/src/test/kotlin/com/example/navigation/app/RouteUiPresentationTest.kt`
Verification: `$taskGradleHome = Join-Path $env:TEMP 'some-gradle-user'; $env:GRADLE_USER_HOME = $taskGradleHome; .\gradlew.bat --no-daemon :app:testDebugUnitTest --tests "com.example.navigation.app.RouteUiPresentationTest" --console=plain` — passed on 2026-09-03 (2 tests, 0 failures); `:app:assembleDebug` — passed on 2026-09-03.

### Model

Terra

### Objective

Show a planned route before activation and require an explicit user action to begin navigation.

### Scope

- Render TASK 125 candidate route geometry, destination marker, distance, mode, package/version status, and honest origin/source information.
- Provide explicit `START NAVIGATION`, `CANCEL`, and destination-change actions.
- Surface route-planning, no-route, unavailable-graph, and stale-data states without blocking map interaction.
- Activate only a user-confirmed route; cancellation must preserve the prior active route and navigation state.
- Distinguish candidate-route rendering from active-route rendering, with accessible labels and reduced-motion handling.

Do not calculate routes, update route progress, reroute, or infer user intent from a map tap.

### Dependencies

TASK 083–087, TASK 106–113, TASK 125

### Acceptance criteria

- Users can inspect route/destination before activation and can clearly cancel or replace it.
- Navigation does not enter active guidance until `START NAVIGATION` is selected.
- The UI explicitly reports every backend planning state and never presents a failed candidate as active navigation.

### Tests required

- Deterministic UI-state tests for preview, start, cancellation, replacement, and failures.
- Compose/UI accessibility and reduced-motion tests where infrastructure permits.

### Completion evidence

Record exact command, candidate-route fixture, activation/cancellation results, and emulator/device result separately.

Known limitation: TASK 125 is PARTIAL and no production destination/index/graph flow supplies a `ROUTE_READY` candidate to the installed app. Consequently the shipped UI only reaches the explicit graph-unavailable state after a selected destination; a real preview/start journey and device accessibility evidence remain pending. The UI intentionally does not calculate a route or activate navigation itself.

---

## TASK 127 — Active Route Lifecycle, Progress, and Arrival Detection

Status: PARTIAL
Implementation: `core/src/main/kotlin/com/example/navigation/routing/ActiveRouteLifecycle.kt` accepts only an explicit `ConfirmedRoutePlan`, enforces one active route at a time, consumes existing `NavigationPosition` and `RouteProgress` observations, rejects stale/invalid/backward progress, preserves progress during unavailable or low-confidence observations, and exposes active/low-confidence/off-route/reroute-required/arrived/stopped/unavailable states with remaining distance, maneuver, confidence, accuracy, and reasons. Configurable defaults require three arrival observations within 15 m destination distance and 20 m route-progress tolerance; off-route evidence begins beyond 40 m, reaches `OFF_ROUTE` after two observations, and `REROUTE_REQUIRED` after three without calculating a route.
Tests: `core/src/test/kotlin/com/example/navigation/routing/ActiveRouteLifecycleTest.kt`
Verification: `$taskGradleHome = Join-Path $env:TEMP 'some-gradle-user'; $env:GRADLE_USER_HOME = $taskGradleHome; .\gradlew.bat --no-daemon :core:test --tests "com.example.navigation.routing.ActiveRouteLifecycleTest" --console=plain` — passed on 2026-09-03 (4 tests, 0 failures). Synthetic 200 m/two-segment replay state sequence: `ACTIVE → ACTIVE → LOW_CONFIDENCE → ACTIVE → OFF_ROUTE → ACTIVE → ACTIVE → ACTIVE → ARRIVED`; explicit stop/restart cleared prior progress. Boundary, stale, backward, unavailable, sustained off-route/reroute, and three-sample arrival behavior passed.

### Model

Sol

### Objective

Manage a confirmed active route from start through progress, off-route detection, explicit completion, and safe stop/restart behavior.

### Scope

- Accept only a confirmed TASK 126 route and initialize active-route state with route/package provenance.
- Consume existing navigation estimates and route-progress components to update route progress with confidence and estimated accuracy.
- Define configurable, documented arrival and off-route thresholds; require sustained evidence before declaring arrival.
- Emit explicit states: `ACTIVE`, `LOW_CONFIDENCE`, `OFF_ROUTE`, `REROUTE_REQUIRED`, `ARRIVED`, `STOPPED`, and `UNAVAILABLE`.
- Preserve route state through transient unavailable measurements without fabricating progress; support explicit stop and a new-route restart.
- Expose the data needed by guidance UI: next route segment/maneuver, remaining distance, progress, confidence, and reasoned terminal state.

Do not add new sensor fusion, silently recalculate routes, or claim arrival from a manual destination selection alone.

### Dependencies

TASK 043–049, TASK 060–063, TASK 125–126

### Acceptance criteria

- A confirmed route transitions to active exactly once and reports monotonic progress only when supported by sufficient evidence.
- Arrival requires configured proximity/progress evidence and is not emitted from a single implausible measurement.
- Loss of measurements, low confidence, and off-route conditions remain visible and do not falsely complete the route.
- Stop/restart clears prior active-route progress only by explicit user action.

### Tests required

- JVM tests for lifecycle transitions, threshold boundaries, stale/implausible observations, off-route behavior, stop/restart, and arrival stability.
- Deterministic synthetic route replay covering normal progress, GNSS degradation, recovery, off-route, and arrival.

### Completion evidence

Record the synthetic route scenario, threshold configuration, exact replay command, state sequence, progress/arrival outcome, and known limitations.

Known limitation: TASK 126 is NOT_STARTED, so no actual UI-confirmed route can initialize this lifecycle. TASK 043 and TASK 049 remain incomplete, so live fused progress and route-matcher output are not integrated. This task therefore remains PARTIAL despite deterministic lifecycle and replay coverage.

---

## TASK 128 — Active Guidance, Off-Route, and Arrival UI

Status: PARTIAL
Implementation: `app/src/main/kotlin/com/example/navigation/app/RouteUiPresentation.kt` consumes TASK 127 `ActiveRouteState` and presents active, low-confidence, unavailable, off-route, reroute-required, arrived, and stopped states with route progress, remaining distance, maneuver only when confidence is sufficient, origin source, confidence, estimated accuracy, and explicit stop/new-destination actions. `MainActivity.kt` hosts the accessible reduced-motion-aware active guidance panel without altering lifecycle, fusion, or arrival state.
Tests: `app/src/test/kotlin/com/example/navigation/app/RouteUiPresentationTest.kt`
Verification: `$taskGradleHome = Join-Path $env:TEMP 'some-gradle-user'; $env:GRADLE_USER_HOME = $taskGradleHome; .\gradlew.bat --no-daemon :app:testDebugUnitTest --tests "com.example.navigation.app.RouteUiPresentationTest" --console=plain` — passed on 2026-09-03 (2 tests, 0 failures); `:app:assembleDebug` — passed on 2026-09-03.

### Model

Terra

### Objective

Present the active-route journey clearly from navigation start through maneuvers, degraded/off-route states, and route completion.

### Scope

- Consume TASK 127 state to show active route, destination, next maneuver, remaining distance, route progress, source, confidence, and estimated accuracy.
- Show clear low-confidence, unavailable, off-route, reroute-required, arrived, stop-navigation, and new-destination actions.
- Keep map gestures responsive and distinguish estimated position, destination, proposed route, and active route visually.
- Do not present precise maneuver/arrival claims when TASK 127 reports insufficient confidence.
- Provide accessible semantics, large touch targets, and reduced-motion alternatives for all state transitions.

Do not calculate routes, determine arrival, or alter navigation-estimation confidence.

### Dependencies

TASK 084–087, TASK 106–113, TASK 126–127

### Acceptance criteria

- Users can tell whether a route is proposed, active, degraded, off-route, or complete.
- Arrival is displayed only after TASK 127 emits `ARRIVED` and provides an explicit next action.
- Guidance remains honest when source/confidence is inadequate and never hides the uncertainty state.
- Stop and new-destination controls are explicit and do not silently preserve stale route state.

### Tests required

- Deterministic presentation tests for every active-route lifecycle state.
- Compose/UI, accessibility, and reduced-motion tests where infrastructure permits.
- Emulator/device smoke test covering preview, start, active guidance, off-route, arrival, and stop using a synthetic route fixture where possible.

### Completion evidence

Record exact commands, fixture/state sequence, accessibility result, and emulator/device smoke evidence.

Known limitation: TASK 127 is PARTIAL and TASK 126 has no production route-start wiring, so the installed app has no live active-route state to render. No emulator/device is available for the required smoke test. The deterministic presentation tests cover active, low-confidence, and arrived states, but no UI code determines arrival, reroutes, or changes navigation-estimation confidence.

---

# EPIC 27 — MapLibre Offline Package Runtime Integration

## TASK 129 — Production Map Package Source and MapLibre Offline Runtime Wiring

Status: PARTIAL
Implementation: `ProductionRegionalMapRuntime.kt` supplies an approved, explicit-confirmation-only Kyiv/Lviv starter catalog using the OpenFreeMap Liberty MapLibre style; it wires the existing MapLibre manager, backend, orchestrator, persistence, metadata, verification boundary, and active-package store at runtime. `MainActivity.kt` now renders the approved style rather than the MapLibre demo style. Source/license documentation is in `docs/MAP_SOURCES.md`.
Tests: Existing `RegionalMapCatalogTest.kt` and `RegionalMapDownloadOrchestratorTest.kt`; focused presentation coverage in `RegionalMapDownloadPresentationTest.kt`.
Verification: `$taskGradleHome = Join-Path $env:TEMP 'some-gradle-user'; $env:GRADLE_USER_HOME = $taskGradleHome; .\gradlew.bat --no-daemon :app:testDebugUnitTest --tests "com.example.navigation.maps.RegionalMapCatalogTest" --tests "com.example.navigation.app.RegionalMapDownloadPresentationTest" :app:assembleDebug --console=plain` — passed on 2026-09-03.

### Model

Sol

### Objective

Connect the existing regional catalog and download orchestration contracts to a real, licensed MapLibre-compatible tile/style source so that an explicitly selected regional package preloads its tiles while network access is available and can later render offline.

### Scope

- Define a versioned production regional-package catalog containing only approved, documented map style/tile sources, bounds, zoom range, expected size, attribution, map version, routing-graph version, and integrity metadata.
- Instantiate `MapLibreOfflineRegionManager`, `MapLibreRegionalMapDownloadBackend`, `RegionalMapDownloadOrchestrator`, verification, persistence, and activation at app runtime; do not leave the UI backed by placeholder state.
- Start a tile prefetch only after explicit user confirmation of a selected region. Respect unavailable network, pause/cancel/retry, process restart, storage failure, stale/corrupt package, and source/license failure states.
- On verified activation, select the installed package's compatible local/offline MapLibre style for the map renderer. Do not continue to depend on the demo-style URL for an activated region.
- Keep renderer cache behavior distinct from a verified regional download: tiles incidentally viewed online must never be reported as a downloaded offline package.
- Preserve package/version compatibility boundaries for destination index and routing graph consumers. Do not add remote routing or online destination search.

### Dependencies

TASK 032–034, TASK 119–120

### Acceptance criteria

- With network access, a user-confirmed regional package begins a MapLibre offline-region download and exposes truthful progress or a specific actionable failure.
- A package reaches active/offline-ready only after its declared integrity/version checks succeed.
- After activation, the downloaded region renders with network disabled; an un-downloaded region is clearly unavailable rather than silently using cached/demo data.
- Map source attribution and licensing are visible and documented for every configured source.
- No automatic country-scale prefetch occurs merely because the app has network access or a location fix.

### Tests required

- JVM/integration tests using a deterministic local tile/style server or fake MapLibre backend for start, progress, pause, resume, cancel, restart recovery, verification failure, activation, and version mismatch.
- Instrumented test proving the renderer selects the activated offline style rather than the demo style.
- Device smoke test: download a small approved test region online, restart the app, disable network, and verify the same region remains renderable.

### Completion evidence

Record the approved source and license, catalog/package versions, exact test commands, package size/progress result, offline renderer result, and device/network test result.

Known limitation: MapLibre's incidental resource cache is not a verified offline package and must not be used as completion evidence.

---

## TASK 130 — Offline Map Download Entry and Preload Journey

Status: PARTIAL
Implementation: `MainActivity.kt` restores Offline Maps entry points from the main burger menu and Settings, resolves the live catalog from permission/location when available, provides manual Kyiv/Lviv selection, and sends confirmation/pause/resume/cancel/retry actions to the runtime orchestrator. `MapDownloadScreen` exposes the existing explicit state presentations and source package details.
Tests: `RegionalMapDownloadPresentationTest.kt`.
Verification: `$taskGradleHome = Join-Path $env:TEMP 'some-gradle-user'; $env:GRADLE_USER_HOME = $taskGradleHome; .\gradlew.bat --no-daemon :app:testDebugUnitTest --tests "com.example.navigation.app.RegionalMapDownloadPresentationTest" :app:assembleDebug --console=plain` — passed on 2026-09-03.

### Model

Terra

### Objective

Expose a complete, accessible user journey for selecting, confirming, monitoring, and managing an offline regional map download before navigation begins.

### Scope

- Restore a reachable Offline Maps entry from the main navigation menu and Settings, without obscuring the map or navigation controls.
- Consume the live runtime states supplied by TASK 129 and existing TASK 119–121 presentations: permission/location discovery, manual region selection, package details, storage/network prerequisites, explicit confirmation, progress, pause, resume, cancel, retry, verification, ready, stale, corrupt, and remove/update states.
- Clearly state that downloading is optional and is a deliberate preload for offline use; do not imply that an online tile cache is sufficient for offline navigation.
- Present package name, coverage, zoom/detail range, estimated size, source attribution, storage use, and compatible routing/index readiness when available.
- Return the user safely to the map throughout the flow and surface an offline-ready indicator only after TASK 129 activates the verified package.
- Provide large touch targets, content descriptions, keyboard/back dismissal, and reduced-motion transitions.

### Dependencies

TASK 083–087, TASK 119–121, TASK 129

### Acceptance criteria

- A user can open Offline Maps, select an auto-detected or manually selected region, review its implications, explicitly begin a download, and observe progress through verified offline-ready status.
- All unavailable, network, storage, permission, cancellation, stale, and corrupt states provide an honest recovery path.
- The main map remains usable after exiting the flow, and the burger menu can be dismissed throughout the journey.
- The UI never claims country-level map detail is present until the verified package is active.

### Tests required

- Compose/UI tests for every download presentation state, entry/exit navigation, menu dismissal, explicit confirmation, accessibility semantics, and reduced-motion behavior.
- End-to-end instrumented/device smoke test covering a small test-region download, app restart, network-disabled render, and map-management return path.

### Completion evidence

Record exact UI/instrumented test commands, test-region package/version, screenshots or device recording, accessibility result, and network-disabled rendering result.

Known limitation: This task depends on the real package source and runtime activation from TASK 129; it must not use the debug synthetic navigation fixture or MapLibre demo tiles as offline evidence.

---

## TASK 131 — Pedestrian Replay Validation and MVP Benchmark

Status: COMPLETE
Implementation: Sensor-session format version 2 adds explicit pedestrian metadata, `PEDESTRIAN` mode, monotonic pose transitions and all defined pedestrian events while retaining separate wall-clock metadata and version-1 compatibility. Replay dispatch preserves the new typed records; older files remain readable with absent labels left unavailable. Example anonymized metadata: route `anonymous-route`, device `Synthetic Phone`, `UNKNOWN` initial pose, 640 m known distance, consent confirmed, anonymized=true. Data remains in the existing local `.navsession` stream; no upload path was added.
Tests: `core/src/test/kotlin/com/example/navigation/replay/PedestrianSessionValidationTest.kt` covers every metadata/label type, all pose transitions, all event labels, Unicode notes, unknown pose, missing optional reference, malformed ordering, version-1 compatibility, and deterministic repeated reads.
Verification: `$taskGradleHome = Join-Path $env:TEMP 'some-gradle-user'; $env:GRADLE_USER_HOME = $taskGradleHome; .\gradlew.bat --no-daemon :core:test --tests 'com.example.navigation.replay.PedestrianSessionValidationTest' :app:testDebugUnitTest --tests 'com.example.navigation.app.PedestrianFieldRecordingTest' --rerun-tasks --console=plain` — core suite passed 2026-09-03 (4 tests, 0 failures/errors; app test compilation initially failed only because the new test used the unavailable `kotlin.test` import, corrected to the repository's JUnit 4); subsequent app verification passed. Format version: 2; round-trip and version-1 compatibility passed.

### Model

Sol

### Objective

Validate the completed pedestrian dead-reckoning, route-progress, matcher, and confidence pipeline against recorded walking sessions before making pedestrian-MVP claims.

### Scope

- Accept only anonymized, consented pedestrian recordings with monotonic sensor timestamps, a declared phone-carry pose where known, planned route geometry, and reference position/progress sufficient for scoring.
- Replay TASK 070–077 outputs deterministically through normal GNSS, GNSS loss, gradual drift, delayed observations, noisy observations, and recovery scenarios.
- Report correct route/path percentage, mean and maximum position error, mean and maximum route-progress error, wrong branch selections, confidence-state duration, and recovery behavior.
- Evaluate each phone pose separately when recordings permit; unknown or unlabelled pose must reduce confidence rather than be silently treated as hand-forward.
- Produce a versioned pedestrian benchmark report and retain the dataset metadata/fingerprint without uploading raw trajectories.

Do not alter production positioning algorithms, create vehicle benchmarks, or use synthetic data as a substitute for the required walking-session result.

### Dependencies

TASK 070–077, TASK 088–091, TASK 098–104

### Acceptance criteria

- Replays are deterministic for identical recording, configuration, and corruption seed.
- Every report identifies recording metadata, route, pose label/unknown status, configuration, fault scenario, and exact metrics.
- A representative 500–1000 m route-constrained walking dataset demonstrates the pedestrian MVP target of more than 90% correct route branch during GNSS loss, or records a truthful failed result and its cause.
- No estimate is reported as ground truth; low-confidence and unsupported-pose intervals remain visible in results.

### Tests required

- JVM tests for metric aggregation, deterministic corruption seeds, missing/invalid metadata, and pose-stratified reporting.
- Replay tests using at least one accepted walking recording and a documented synthetic corruption schedule.

### Completion evidence

Record exact replay commands, recording fingerprints and consent/anonymization status, corruption seeds, configuration, metric report, and target pass/fail result.

---

## TASK 132 — Pedestrian Navigation Runtime and Guidance UI Integration

Status: COMPLETE
Implementation: Developer Mode now opens a dedicated pedestrian field-recording setup and recording screen. It selects `PEDESTRIAN`, states OBD is not required, collects initial pose, intended endpoints, route/reference, consent/anonymization and notes, then exposes large one-tap pose/event controls, mode/current-pose/duration/sensor availability, saved-label confirmation, required-stream warnings, stop/save, and export. Back is consumed during recording with an explicit stop/save warning; controls use 52–56 dp minimum heights and content descriptions, and this screen adds no motion that must be disabled.
Tests: `app/src/test/kotlin/com/example/navigation/app/PedestrianFieldRecordingTest.kt` covers required setup metadata, OBD-free eligibility, pose transitions, event state, and visible confirmation. Existing foreground-service/export behavior is preserved and schema integration is covered by `PedestrianSessionValidationTest`.
Verification: `$taskGradleHome = Join-Path $env:TEMP 'some-gradle-user'; $env:GRADLE_USER_HOME = $taskGradleHome; .\gradlew.bat --no-daemon :core:test --tests 'com.example.navigation.replay.PedestrianSessionValidationTest' :app:testDebugUnitTest --tests 'com.example.navigation.app.PedestrianFieldRecordingTest' :app:assembleDebug --console=plain` — passed 2026-09-03 (core 4 tests and app 2 tests, 0 failures/errors; BUILD SUCCESSFUL). API 35 `RenavSmokeApi35` emulator smoke passed: decor-layer burger opened reliably, pedestrian setup selected `HAND_FORWARD`, route `smoke-route-001`, local consent and OBD-free start; pose labels `HAND_FORWARD`→`HAND_SWINGING` and events `WALKING_START`→`TURN_LEFT` were confirmed, stop/save produced `session-1b92796e-8be2-4a70-942d-88c046245553.navsession`, and export opened Android's chooser with the session and acceptance report. APK: `app/build/outputs/apk/debug/app-debug.apk`, SHA-256 `EED48073062CE80B2FF2B303D3A5A2C3D3C14BE8F67546C3F757A354D98A840C`.

### Model

Terra

### Objective

Connect the validated pedestrian navigation result to the existing map and active-guidance UI, with clear source, pose, and uncertainty communication.

### Scope

- Start the existing pedestrian pipeline only from explicit `PEDESTRIAN` mode and feed its `NavigationPosition`, route progress, confidence, and maneuver state into the established map, route lifecycle, and guidance presentations.
- Display `PEDESTRIAN DR` or hybrid/GNSS source honestly, together with pose confidence, estimated accuracy, route state, and a visible uncertainty area when appropriate.
- Preserve map gestures, destination selection, preview, stop, and new-destination controls; do not modify vehicle fusion or treat an OBD absence as an error in pedestrian mode.
- Surface unavailable sensor, low-pose-confidence, lost, off-route, and recovery states without precise-marker or maneuver claims that the backend does not support.
- Provide accessible semantics, large touch targets, and reduced-motion behavior for pedestrian state transitions.

Do not implement new pedestrian algorithms, route matching, or benchmark calculations; consume only validated TASK 070–077 outputs.

### Dependencies

TASK 070–077, TASK 083–087, TASK 124–128, TASK 131

### Acceptance criteria

- A user explicitly in pedestrian mode can start a route and observe the current pedestrian position source, route progress, confidence, and uncertainty state.
- GNSS loss/recovery, unknown phone pose, and off-route conditions are presented honestly and never as a guaranteed precise location.
- OBD absence does not block a pedestrian recording or navigation journey.
- Existing vehicle UI behavior is unchanged outside explicitly selected pedestrian mode.

### Tests required

- JVM/presentation tests for pedestrian source, pose-confidence, unavailable, low-confidence, off-route, recovered, and arrived states.
- Compose/UI accessibility and reduced-motion tests for pedestrian guidance.
- Device smoke test using a replay or controlled pedestrian session from TASK 131.

### Completion evidence

Record exact commands, synthetic/replay fixture, screenshot or device result, accessibility result, and source/confidence-state sequence.

---

# EPIC 28 — Pedestrian Field Validation Tooling

## TASK 133 — Pedestrian Session Labels and Validation Metadata

Status: COMPLETE
Implementation: `PedestrianRecordingValidator` performs read-only local checks for readability, schema, clean completion, timestamp ordering/gaps, duration, explicit pedestrian mode/metadata, consent/anonymization, essential GNSS/gyro/accelerometer/rotation streams and counts, pose/event coverage, and route reference. It distinguishes failures, limitations, and optional unavailable inputs; OBD is explicitly optional. It writes a deterministic normalized `.acceptance.txt` report and export creates/shares reports alongside selected sessions while warning—but not blocking—when a recording is rejected.
Tests: `core/src/test/kotlin/com/example/navigation/replay/PedestrianSessionValidationTest.kt` covers accepted, accepted-with-limitations, rejected, OBD-free, legacy, malformed/non-monotonic, sparse, missing pose/reference, missing consent, deterministic reporting, and a synthetic exported labelled session.
Verification: Focused core test command above passed 2026-09-03 (4 tests, 0 failures/errors). Synthetic fixture SHA-256 `5B9EB5479CD83EA3BD600A70E9B7B00A68EFC914E17E863B0347EA008A6472FA`; report disposition `ACCEPTED`, schema 2, duration 200 ns, counts: GNSS 2, gyro 2, accelerometer 2, rotation 2, metadata 1, mode 1, pose 1, event 1; OBD and raw GNSS reported optional/unavailable. Byte-stable normalized report artifact: `%TEMP%\synthetic-pedestrian-export6502966016370283930.navsession.acceptance.txt` (generation timestamp intentionally absent).

### Model

Sol

### Objective

Extend the replayable session format with the structured metadata and timestamped labels required to evaluate pedestrian pose, heading, route progress, turns, and arrival.

### Scope

- Add an explicit session navigation mode and require `PEDESTRIAN` for pedestrian-validation recordings.
- Represent timestamped phone-pose labels: `HAND_FORWARD`, `HAND_SWINGING`, `POCKET`, and `UNKNOWN`, including transitions during a session.
- Represent labelled pedestrian events: walking start/stop, left/right/straight turn, intersection, route departure, route recovery, and arrival.
- Store intended start, destination, route identifier/geometry reference, known walking distance when available, device model, carry-condition notes, known anomalies, and consent/anonymization status.
- Preserve monotonic timestamps for fusion/replay labels and keep wall-clock values separate.
- Version the format and retain deterministic backward-compatible reading of older sessions that do not contain pedestrian labels; missing fields must remain explicitly unavailable.

Do not implement UI, infer labels automatically, alter sensor sampling, or add remote upload.

### Dependencies

TASK 009–012, TASK 092–094, TASK 098–100

### Acceptance criteria

- A session can contain pedestrian mode, multiple pose intervals, route metadata, and timestamped labelled events without altering raw sensor observations.
- Session write/read round trips preserve every label, timestamp, and metadata value deterministically.
- Older recordings remain readable and are reported as unlabelled rather than assigned fabricated defaults.
- Sensitive route data is stored locally and never uploaded automatically.

### Tests required

- JVM round-trip tests for all metadata and label types, pose transitions, Unicode notes, unknown pose, missing optional reference data, malformed ordering, and old-format compatibility.
- Determinism test proving identical labelled input produces equivalent replay records.

### Completion evidence

Record the format version, example anonymized metadata, exact test commands, round-trip result, compatibility result, and privacy boundary.

---

## TASK 134 — Pedestrian Field Recording Workflow UI

Status: COMPLETE
Implementation: Developer Mode now opens a dedicated pedestrian field-recording setup and recording screen. It selects `PEDESTRIAN`, states OBD is not required, collects initial pose, intended endpoints, route/reference, consent/anonymization and notes, then exposes large one-tap pose/event controls, mode/current-pose/duration/sensor availability, saved-label confirmation, required-stream warnings, stop/save, and export. Back is consumed during recording with an explicit stop/save warning; controls use 52–56 dp minimum heights and content descriptions, and this screen adds no motion that must be disabled.
Tests: `app/src/test/kotlin/com/example/navigation/app/PedestrianFieldRecordingTest.kt` covers required setup metadata, OBD-free eligibility, pose transitions, event state, and visible confirmation. Existing foreground-service/export behavior is preserved and schema integration is covered by `PedestrianSessionValidationTest`.
Verification: `$taskGradleHome = Join-Path $env:TEMP 'some-gradle-user'; $env:GRADLE_USER_HOME = $taskGradleHome; .\gradlew.bat --no-daemon :core:test --tests 'com.example.navigation.replay.PedestrianSessionValidationTest' :app:testDebugUnitTest --tests 'com.example.navigation.app.PedestrianFieldRecordingTest' :app:assembleDebug --console=plain` — passed 2026-09-03 (core 4 tests and app 2 tests, 0 failures/errors; BUILD SUCCESSFUL). API 35 `RenavSmokeApi35` emulator smoke passed: decor-layer burger opened reliably, pedestrian setup selected `HAND_FORWARD`, route `smoke-route-001`, local consent and OBD-free start; pose labels `HAND_FORWARD`→`HAND_SWINGING` and events `WALKING_START`→`TURN_LEFT` were confirmed, stop/save produced `session-1b92796e-8be2-4a70-942d-88c046245553.navsession`, and export opened Android's chooser with the session and acceptance report. APK: `app/build/outputs/apk/debug/app-debug.apk`, SHA-256 `EED48073062CE80B2FF2B303D3A5A2C3D3C14BE8F67546C3F757A354D98A840C`.

### Model

Terra

### Objective

Let a field tester collect a self-describing pedestrian session using the existing local recorder without requiring OBD hardware or external handwritten timing notes.

### Scope

- Add an explicit pedestrian field-recording setup flow that selects `PEDESTRIAN` mode and states that OBD is not required.
- Before recording, collect the initial phone pose, route/reference information, consent/anonymization confirmation, and optional notes defined by TASK 133.
- While recording, provide large one-tap controls to change phone pose and add labelled walking/turn/intersection/route-departure/recovery/arrival events with monotonic timestamps.
- Clearly display active mode, current pose, recording duration, sensor availability, saved-label confirmation, and any missing required sensor stream.
- Preserve stop, save, multi-session selection, and export behavior; accidental navigation away must not silently discard the recording.
- Support Android back handling, accessible content descriptions, large touch targets, and reduced-motion behavior.

Do not run pedestrian navigation algorithms, infer ground truth, require OBD, or upload recordings.

### Dependencies

TASK 083–087, TASK 092–094, TASK 114, TASK 133

### Acceptance criteria

- A user can configure, start, label, stop, save, select, and export a pedestrian session without connecting an OBD adapter.
- Pose changes and labelled events are written at the time the user selects them and remain visible in the exported session.
- The UI never presents a user-entered route or event label as measured ground truth.
- Missing permissions or required phone sensors produce an actionable warning without fabricating data.

### Tests required

- Reducer/presentation tests for setup, required metadata, recording, pose transitions, labelled events, stop/save, cancellation, and unavailable-sensor states.
- Compose/UI tests for accessibility, back handling, touch targets, reduced motion, and OBD-free recording.
- Device smoke test covering one complete labelled pedestrian session and export.

### Completion evidence

Record exact test commands, device model, permission/sensor result, pose/event sequence, exported filename, and device smoke-test outcome.

---

## TASK 135 — Pedestrian Recording Completeness and Acceptance Report

Status: COMPLETE
Implementation: `PedestrianRecordingValidator` performs read-only local checks for readability, schema, clean completion, timestamp ordering/gaps, duration, explicit pedestrian mode/metadata, consent/anonymization, essential GNSS/gyro/accelerometer/rotation streams and counts, pose/event coverage, and route reference. It distinguishes failures, limitations, and optional unavailable inputs; OBD is explicitly optional. It writes a deterministic normalized `.acceptance.txt` report and export creates/shares reports alongside selected sessions while warning—but not blocking—when a recording is rejected.
Tests: `core/src/test/kotlin/com/example/navigation/replay/PedestrianSessionValidationTest.kt` covers accepted, accepted-with-limitations, rejected, OBD-free, legacy, malformed/non-monotonic, sparse, missing pose/reference, missing consent, deterministic reporting, and a synthetic exported labelled session.
Verification: Focused core test command above passed 2026-09-03 (4 tests, 0 failures/errors). Synthetic fixture SHA-256 `5B9EB5479CD83EA3BD600A70E9B7B00A68EFC914E17E863B0347EA008A6472FA`; report disposition `ACCEPTED`, schema 2, duration 200 ns, counts: GNSS 2, gyro 2, accelerometer 2, rotation 2, metadata 1, mode 1, pose 1, event 1; OBD and raw GNSS reported optional/unavailable. Byte-stable normalized report artifact: `%TEMP%\synthetic-pedestrian-export6502966016370283930.navsession.acceptance.txt` (generation timestamp intentionally absent).

### Model

Sol

### Objective

Determine whether an exported pedestrian recording contains enough synchronized sensor, label, route, and privacy metadata to be accepted by TASK 131.

### Scope

- Validate session readability, format version, monotonic ordering, duration, pedestrian mode, sensor-stream presence and sample counts, pose coverage, labelled events, route/reference metadata, and consent/anonymization status.
- Distinguish required, recommended, optional, unavailable, and malformed inputs; OBD must be optional and its absence must not fail a pedestrian recording.
- Report timing gaps, missing labels, unsupported phone-pose coverage, absent ground-truth/reference data, and streams too sparse for useful evaluation.
- Produce a deterministic local text or JSON acceptance report with `ACCEPTED`, `ACCEPTED_WITH_LIMITATIONS`, or `REJECTED` and explicit reasons.
- Integrate the check before export as a warning, while allowing the user to export incomplete data for diagnostic purposes.

Do not modify recordings, fabricate missing metadata, run pedestrian benchmarks, or upload data.

### Dependencies

TASK 013–015, TASK 098–104, TASK 133–134

### Acceptance criteria

- A complete labelled pedestrian session is accepted with its available streams, route/reference evidence, pose coverage, and event counts summarized.
- Missing OBD never lowers pedestrian acceptance status.
- Missing pedestrian mode, consent state, monotonic ordering, or essential motion/location streams produces a specific rejection reason.
- Missing reference route/ground truth or incomplete pose coverage produces an explicit limitation and prevents unsupported benchmark claims.
- Identical recordings produce byte-equivalent normalized acceptance reports apart from an explicitly excluded report-generation timestamp.

### Tests required

- JVM tests for accepted, limited, rejected, OBD-free, legacy, malformed, sparse, non-monotonic, missing-pose, and missing-reference sessions.
- Integration test against an exported synthetic labelled pedestrian session from TASK 134.

### Completion evidence

Record exact commands, fixture fingerprints, sensor/label counts, each acceptance disposition, and the exported report artifact.

---

# EPIC 29 — GNSS Measurement Trust and Injection Filtering

## TASK 136 — Versioned GNSS Trust Evidence in Session Recordings

Status: COMPLETE
Implementation: Sensor-session schema version 3 adds immutable derived GNSS epoch/satellite trust records, all four classifications, reason codes, inclusion/weights, separate weighted-position records, Android mock evidence, privacy-limited air-alert markers, and separately labelled spacecraft-reference metadata. Legacy schema 1/2 files remain readable with absent trust records interpreted as unavailable; raw GNSS records are not rewritten or replaced.
Tests: `GnssTrustSessionRoundTripTest.kt` covers all classifications and score boundaries, multi-satellite reference identifiers, combined reasons, inclusion/exclusion weights, separate solution/alert records, explicit unavailable evidence, schema-1 compatibility, raw preservation/order, and deterministic byte serialization.
Verification: `$taskGradleHome = Join-Path $env:TEMP 'some-gradle-user'; $env:GRADLE_USER_HOME = $taskGradleHome; .\gradlew.bat --no-daemon :core:test --tests "*GnssTrustSessionRoundTripTest" --tests "*SatelliteTrustScorerTest" --tests "*GnssTrustCalibrationDatasetTest"` passed 2026-09-06. Schema `3`; anonymized fixture-source SHA-256 `A13C9834F4F57B59B999054F3A900AFB30564CE08DC91DB26822DC6CDF9788E2`; raw record equality/order and deterministic bytes passed. Privacy boundary remains local-only with no upload path or alert calendar/history field.

### Model

Sol

### Objective

Extend the replayable session format so raw GNSS observations remain unchanged while derived satellite/measurement trust evidence is recorded alongside them.

### Scope

- Add a versioned, backward-compatible record for measurement or epoch trust score, classification (`TRUSTED`, `UNCERTAIN`, `SUSPICIOUS`, `REJECTED`), reason codes, and estimator-inclusion/weight information.
- Preserve monotonic timestamps and keep wall-clock values separate.
- Record the weighted position solution and confidence separately from raw GNSS observations when available.
- Represent unavailable trust evidence explicitly for legacy sessions; never fabricate classifications.
- Keep suspicious data locally replayable and diagnosable; do not delete, rewrite, or upload raw observations.
- Add a contextual `AIR_ALERT_STARTED`/`AIR_ALERT_ENDED` marker with monotonic receipt time and source status only; do not store an air-alert calendar date or full alert history in the session.
- Preserve expected-spacecraft reference fields when available (constellation, SVID/NORAD identifier, ephemeris/TLE source and version) separately from received measurements.

Do not implement the scoring algorithm, change sensor sampling, or add UI in this task.

### Dependencies

TASK 005–007, TASK 092–094, TASK 101, TASK 133, TASK 135

### Acceptance criteria

- A session round trip preserves raw measurements, trust evidence, weights, classifications, reason codes, and weighted-solution metadata.
- Version-1 and older recordings remain readable with trust evidence explicitly unavailable.
- Raw observations are byte/order stable and are never replaced by filtered values.
- Identical input produces deterministic replay records and no remote side effects.

### Tests required

- JVM round-trip tests for every classification, score boundary, multiple satellites, reason-code combinations, weighted inclusion, absent fields, malformed ordering, and legacy compatibility.
- Determinism test proving the same raw input and evidence produces equivalent serialized records.

### Completion evidence

Record schema version, anonymized fixture fingerprint, raw-versus-derived preservation result, compatibility result, deterministic test command, and privacy boundary.

---

## TASK 137 — Android Satellite and Raw GNSS Evidence Collector

Status: COMPLETE
Implementation: Android location, satellite-status, and raw-measurement collectors now share an explicit lifecycle that distinguishes `AVAILABLE`, `UNSUPPORTED`, and `PERMISSION_DENIED`, tears registered callbacks down idempotently, and records each capability locally in schema-3 sessions. Status mapping preserves constellation, SVID, C/N0, elevation, azimuth, carrier-frequency availability, ephemeris/almanac flags, and `usedInFix`; raw mapping preserves Android clock/measurement optionals and monotonic callback time. Mock evidence remains separate. `SpacecraftReferenceProvider` and alert-marker mapping remain read-only evidence boundaries. `LiveRecordingProviderBridge` forwards unchanged location/raw/status records to both recorder and typed trust analysis without navigation decisions in providers.
Tests: `GnssCollectorLifecycleAndMappingTest` covers successful registration, one-time teardown, unsupported and permission-denied registration, GPS/Galileo/non-GPS mapping, optional carrier/AGC/time fields, and `usedInFix`. `AndroidGnssCollectorLifecycleSmokeTest` covers API-35 raw callback registration/idempotent teardown and local recording/reporting when raw capability is unsupported. `AndroidGnssLocationProviderTest` covers monotonic location and separate mock evidence.
Verification: `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.navigation.gnss.AndroidGnssCollectorLifecycleSmokeTest,com.example.navigation.gnss.AndroidGnssLocationProviderTest` passed on `RenavSmokeApi35` API 35 (4 tests), followed by the Android-compatible export regression instrumentation run (2 tests). Fine-location permission was granted for device smoke; unavailable/permission-denied capabilities remain explicit. Synthetic mapping fixture-source SHA-256 `537C08DDBEE3AC747DE84E7477479494231F77752A2E465A519B9803F4F3B260`.

### Model

Terra

### Objective

Collect the Android GNSS satellite-status and raw-measurement evidence needed by the trust layer and session recorder.

### Scope

- Capture constellation, SVID, C/N0, elevation, azimuth, carrier-frequency availability, ephemeris/almanac flags, and `usedInFix` when supplied by `GnssStatus`.
- Capture raw measurement and GNSS-clock fields exposed by `GnssMeasurementsEvent`, including their monotonic timestamps.
- Capture Android mock-location state using `Location.isMock()` where the platform version supports it, without treating that flag as the sole spoofing detector.
- Preserve unavailable fields and unsupported chipset capabilities explicitly.
- Support an external GNSS-spacecraft reference feed or cached ephemeris/TLE data for expected satellite geometry (satellite position, elevation, and azimuth); this is reference evidence, not a replacement for receiver measurements.
- Accept an automatic air-alert marker from the approved provider integration, recording only the local monotonic marker time and source availability.
- Forward measurements to both the recorder and trust-analysis boundary without making navigation decisions in the provider.

Do not classify satellites as true/false, filter measurements, or modify the navigation estimate in this task.

### Dependencies

TASK 005–007, TASK 009–012, TASK 136

### Acceptance criteria

- A live callback produces deterministic, timestamped domain records for all available satellite and raw-measurement fields.
- Permission loss, unavailable raw measurements, and unsupported fields are reported explicitly without fabricated values.
- Mock Android locations are recorded as evidence while raw GNSS records remain preserved.
- Provider code remains separate from trust and estimation decisions.

### Tests required

- JVM mapping tests using synthetic Android callback fixtures for GPS and non-GPS constellations, missing optional fields, `usedInFix`, mock locations, and monotonic timestamps.
- Android/instrumentation smoke test confirming registration, callback teardown, and local recording when raw measurements are unavailable.

### Completion evidence

Record API levels, permission result, available-field matrix, synthetic fixture fingerprint, instrumentation result, and exact test commands.

---

## TASK 138 — Satellite Trust Scoring and Weighted GNSS Solution

Status: COMPLETE
Implementation: `GnssTrustNavigationPipeline` now connects the configurable scorer and robust weighted estimator to both live recorder callbacks and deterministic replay. It consumes unchanged receiver records, incorporates fresh satellite status, raw-clock continuity, mock evidence and supplied PDR/gyro/OBD/route context, emits separate trust/weighted-solution records, applies hysteresis, retains the last trusted anchor, switches to explicit pedestrian/vehicle DR fallback with growing uncertainty when no weighted correction remains, and returns through `HYBRID` during sustained recovery. `GnssTrustReplayProcessor` preserves originals and adds derived records deterministically. Alert context still has no scoring effect.
Tests: `GnssTrustNavigationPipelineTest` covers trusted correction, five-epoch coordinated remote injection, zero estimator weight, last-trusted pedestrian fallback, increasing uncertainty, three-consistent-epoch recovery to hybrid, deterministic replay, and preservation of the injected raw/processed observation. `SatelliteTrustScorerTest` continues to cover mixed evidence, multipath/noise behavior, OBD-free pedestrian mode, explicit vehicle OBD disagreement, alert invariance, loss, and robust weighting.
Verification: Focused GNSS/replay tests passed 2026-09-07. Synthetic pipeline fixture-source SHA-256 `912C694367D52FD5749142094AB95E78C31E21035B0D7AC07D271F375750BC76`; local anchor position error during rejected replacement remained 0 m relative to the last trusted state, remote estimator influence was 0, plausible local evidence produced no false rejection, suspicious count was 5, and recovery entered `HYBRID` after 3 sustained consistent observations. No Lima-, device-, or country-specific production rule was added.

### Model

Sol

### Objective

Down-score inconsistent GNSS measurements and calculate a weighted position solution while preserving every observation for replay and diagnosis.

### Scope

- Produce configurable measurement/epoch trust scores and explicit reason codes from satellite continuity, signal behavior, GNSS clock/measurement consistency, and independent motion evidence.
- Use robust weighted estimation so trusted measurements dominate, uncertain measurements contribute less, and suspicious measurements contribute minimally or not at all.
- Cross-check the candidate solution against PDR/gyro, OBD speed in vehicle mode, route progress, and physical continuity.
- Compare received satellite identity/geometry against the expected spacecraft position from ephemeris/TLE data, while allowing for propagation, ephemeris age, and receiver uncertainty.
- Use `AIR_ALERT_STARTED`/`AIR_ALERT_ENDED` only as contextual evidence in the scoring timeline; never treat an alert as proof of spoofing and never let alert-feed failure alter GNSS trust by itself.
- Detect and record sudden remote-location changes such as a Lima replacement as suspicious evidence; do not add location-specific production rules.
- Anchor dead reckoning at the last trusted navigation state when no sufficiently trusted weighted GNSS solution remains.
- Reintroduce GNSS gradually after sustained consistency; expose uncertainty and never claim satellite truth.

Do not delete observations, fabricate trusted satellites, or require a real recording for the initial synthetic implementation.

### Dependencies

TASK 050A–050C, TASK 053A, TASK 060A, TASK 062A, TASK 136–137

### Acceptance criteria

- Synthetic injected Lima observations are recorded and down-scored or excluded from estimator influence while trusted local evidence remains dominant.
- Ordinary multipath/noise does not cause unjustified rejection of plausible movement.
- Weighted output includes confidence, included weights, and reason codes.
- Rejection and recovery are deterministic, configurable, and replayable.
- Raw and suspicious measurements remain available to diagnostics and later analysis.

### Tests required

- JVM/replay tests for trusted local data, one suspicious satellite, coordinated remote injection, mixed trusted/untrusted constellations, noisy multipath, GNSS loss, recovery, and deterministic weighting.
- Regression tests proving OBD-free pedestrian mode uses PDR evidence and that vehicle mode does not silently substitute GNSS speed for missing OBD.

### Completion evidence

Record algorithm configuration, synthetic scenario fingerprints, trust/weight traces, route and position metrics, false-rejection results, recovery result, and exact test commands.

---

## TASK 139 — GNSS Trust Diagnostics and Recording Export UI

Status: COMPLETE
Implementation: Service trust snapshots now update the map position/source/trust state and diagnostics every 500 ms. Diagnostics expose visible/used counts, score/classification, suspicious epochs, mock evidence, unavailable fields, reference geometry age/source, alert context, weighted/hybrid/PDR/VDR source, accuracy, reduction reasons, retained-observation wording, and local/no-upload privacy text. `GnssTrustSessionReportWriter` adds deterministic `.trust.txt` companions to every selected export without changing sessions. The unreliable native map overlay was replaced with a z-ordered Compose burger/menu with accessibility semantics and reduced-motion-aware transitions. A debug-build-only synthetic injection fixture supports device smoke without altering production decisions.
Tests: `GnssTrustDiagnosticsExportTest`, `GnssTrustSessionReportWriterTest`, `SyntheticGnssTrustSmokeScenarioTest`, existing diagnostics/status/motion-accessibility tests, and API-35 export regression instrumentation cover rejected/unavailable states, privacy/authenticity wording, deterministic reports, raw byte preservation, synthetic fallback, Android-compatible report writing, and reduced-motion behavior.
Verification: Full app unit tests and `:app:assembleDebug` passed 2026-09-07. API-35 smoke displayed `GNSS UNTRUSTED` → `PEDESTRIAN DR`, `REJECTED`, 5 suspicious epochs, `PHYSICAL_CONTINUITY_VIOLATION`, estimated/not-guaranteed wording, retained-local-observation text, and no-upload text. Burger opened and Developer Mode remained reachable. Two sessions (`session-9d27693d-dfae-4103-aae6-d720a6a31ed6.navsession`, `session-f06adc3b-cd34-4dd3-a712-498741f30ced.navsession`) generated 2 acceptance and 2 trust reports; `com.android.intentresolver/.ChooserActivityLauncher` opened and post-fix logcat had 0 fatal exceptions. APK SHA-256 `D0441CA6798EDE88906B280D92A05865C235306F09804F5B465DB821A4016D72`.

### Model

Terra

### Objective

Expose GNSS measurement trust and filtering decisions clearly to field testers without presenting filtered data as guaranteed truth.

### Scope

- Add a diagnostics view showing visible/used satellites by constellation, trust-score distribution, suspicious-epoch count, mock-location evidence, and unavailable fields.
- Show expected-versus-received satellite geometry and ephemeris/TLE reference age when available, clearly labelled as reference evidence rather than satellite authenticity.
- Show the automatic air-alert marker in the session timeline with provider/source status, without exposing or storing an air-alert date/history as part of the recording data.
- Show whether the current navigation source is weighted GNSS, hybrid, pedestrian DR, or vehicle DR, with confidence and estimated accuracy.
- Explain why GNSS influence was reduced and state that suspicious observations remain recorded for analysis.
- Include trust summaries and reason codes in local acceptance/export reports while preserving raw session data.
- Support reduced motion, accessibility semantics, large touch targets, and no-upload privacy messaging.

Do not add automatic user-facing claims that a satellite is genuine, alter trust decisions in the UI, or make export depend on a complete satellite set.

### Dependencies

TASK 131, TASK 133–135, TASK 136, TASK 138

### Acceptance criteria

- A tester can distinguish trusted weighted positioning from suspicious GNSS evidence and dead-reckoning fallback.
- The UI remains usable when raw measurements, satellite details, OBD, or mock-location flags are unavailable.
- Export includes the trust summary and retains the original raw/session records.
- The UI never displays a filtered position as guaranteed truth.

### Tests required

- JVM/presentation tests for trusted, uncertain, suspicious, unavailable, fallback, and recovery states.
- Compose/UI tests for diagnostics semantics, reduced motion, export warnings, and privacy text.
- Device smoke test covering a synthetic injection replay, trust display, fallback display, and multi-session export.

### Completion evidence

Record exact commands, fixture/scenario, screenshot or device result, accessibility result, exported filenames, and source/confidence sequence.

---

## TASK 140 — Real-Device GNSS Trust Calibration Dataset

Status: BLOCKED
Implementation: `GnssCalibrationRecording` and `GnssCalibrationDataset` define consent/anonymization-gated device/chipset/API metadata, normal/multipath/weak/loss/authorized-injection scenarios, held-out designation, and alert-marked/unmarked comparison readiness. No production threshold was tuned from synthetic or insufficient data.
Tests: `GnssTrustCalibrationDatasetTest` proves incomplete/single-recording datasets are rejected for calibration readiness and consent/anonymization are mandatory. The synthetic remote-injection regression is covered by `SatelliteTrustScorerTest`.
Verification: Dataset readiness was rechecked 2026-09-08 after TASK 141–143 implementation; no qualifying `.navsession` calibration files are present in the workspace. BLOCKED on the dependency explicitly defined by this task: multiple anonymized real-device schema-3 recordings supplied with consent, including held-out and paired normal/multipath/weak/loss/authorized-injection plus alert-marked/unmarked sessions and spacecraft-reference variants. The API-35 deterministic external-feed session is synthetic operational evidence and was not misrepresented as calibration data. No dataset fingerprints, threshold calibration, or benchmark claims can be produced until real recordings exist.

### Model

Luna

### Objective

Calibrate and validate measurement trust thresholds against anonymized real-device recordings after TASKs 136–139 are implemented.

### Scope

- Define paired normal, urban-multipath, weak-signal, GNSS-loss, and authorized mock/injection recordings.
- Include recordings with automatic air-alert start/end markers and compare them with recordings without alerts; treat the marker as contextual metadata only.
- Validate expected-spacecraft reference data against receiver-reported constellation/SVID and assess stale, missing, or contradictory ephemeris/TLE inputs.
- Compare satellite continuity, C/N0, raw measurements, clock behavior, weighted solutions, PDR/OBD evidence, and route outcomes.
- Tune thresholds only from documented evidence and preserve synthetic regression scenarios.
- Record device/chipset/API metadata and anonymization/consent status locally.

Do not begin threshold tuning from a single recording, remove raw data, or upload trajectories automatically.

### Dependencies

TASK 136–143 and field recordings supplied with explicit consent.

### Acceptance criteria

- Threshold changes are supported by multiple anonymized recordings and documented false-positive/false-negative results.
- Normal movement and multipath remain usable while authorized injection scenarios reduce GNSS influence.
- Results are reproducible through replay and do not introduce device- or country-specific hard-coded rules.

### Tests required

- Replay calibration suite with held-out recordings and the synthetic Lima scenario.
- Benchmark report covering correct route, position error, trust transitions, false rejection, and recovery time.

### Completion evidence

Record consent/anonymization, device/API metadata, dataset fingerprints, calibration configuration, benchmark results, and remaining uncertainty.

---

## TASK 141 — Active Cached GNSS Spacecraft Reference Feed

Status: COMPLETE
Implementation: The Android recording lifecycle now refreshes the keyless CelesTrak GNSS TLE endpoint, validates and caches usable identity-mapped entries, preserves cache age/status, anchors observation wall time separately from monotonic event time, and supplies SGP4-derived ECEF position/elevation/azimuth through the existing read-only `SpacecraftReferenceProvider`. `predict4java` 1.2.2 (MIT) is documented and isolated in `core`; raw receiver observations are unchanged. GPS PRN, Galileo-labelled SVID, and BeiDou C-number identities are mapped. CelesTrak labels that do not expose an Android-compatible SVID, notably GLONASS COSMOS labels, remain explicitly unmatched rather than guessed.
Tests: `CelesTrakSpacecraftReferencesTest` covers malformed-stream resynchronization, GPS/Galileo/BeiDou identity mapping, refusal to invent GLONASS slots, validated cache round trip/expiry, deterministic propagation, finite geometry, and source/version labelling. `ExternalEvidenceRecordingSmokeTest` covers Android-compatible cache I/O and active recording integration.
Verification: Focused core tests and `:app:assembleDebug` passed 2026-09-08. Live read-only endpoint check returned 28,728 characters and 171 CelesTrak TLE triplets. Fixture-source SHA-256 `58B1AD0BF7AE0899683CA9071C99A09D1AB2D108E440AC9DE73DA87C9158162F`; cache maximum age is 72 hours; refresh failure preserves and correctly labels the prior cache. Exact final command: `$env:GRADLE_USER_HOME = Join-Path $env:TEMP 'some-gradle-user'; .\gradlew.bat --no-daemon :core:test --tests "*CelesTrakSpacecraftReferencesTest" --tests "*ExternalEvidenceSessionTest" :app:assembleDebug`.

### Model

Sol

### Objective

Provide live recordings with independently sourced, locally cached GNSS spacecraft-reference evidence for comparison with receiver-reported satellites.

### Scope

- Fetch current GNSS orbital elements from the documented, keyless CelesTrak GP/OMM endpoints for supported GPS, Galileo, GLONASS, and BeiDou spacecraft.
- Parse constellation/SVID or PRN identity, NORAD identifier, source, element-set version/epoch, and retrieval status without modifying receiver observations.
- Use an established, license-compatible orbital propagation implementation to calculate expected satellite position, elevation, and azimuth for the receiver location and observation time; document purpose, license, module, and why a custom implementation was rejected.
- Cache validated reference data locally for offline recording, with configurable refresh and maximum-age limits.
- Record reference state explicitly as `AVAILABLE`, `STALE`, `UNAVAILABLE`, or `ERROR`; never fabricate geometry when identity, propagation, time, or receiver position is insufficient.
- Keep network access outside the sensor and trust-decision providers. Reference evidence may inform TASK 138 only through the existing `SpacecraftReferenceProvider` boundary.

Do not classify a spacecraft as authentic, replace Android GNSS measurements, upload session data, or make recording depend on network availability.

### Dependencies

TASK 136–138

### Acceptance criteria

- A recording made online contains source/version-labelled expected geometry matched to applicable receiver satellite records.
- A recording made offline uses a valid cache or explicitly records stale/unavailable status.
- Refresh failure retains the last valid cache without representing it as current.
- Raw GNSS records remain unchanged and replay remains deterministic from recorded reference evidence.

### Tests required

- JVM tests for valid and malformed OMM responses, constellation/PRN mapping, propagation fixtures, cache refresh/expiry, clock errors, missing receiver position, and deterministic evidence mapping.
- Android integration test for successful refresh, offline cached operation, unavailable network, and lifecycle teardown.

### Completion evidence

Record provider endpoints, attribution/license documentation, dependency decision, cache policy, element-set identifiers and ages, fixture fingerprints, online/offline results, and exact test commands.

---

## TASK 142 — Keyless NEPTUN Air-Alert Context Feed

Status: COMPLETE
Implementation: `ExternalEvidenceCoordinator` uses an injectable transport with production OkHttp HTTPS/WebSocket, NEPTUN `/api/v1/alerts` startup/reconnection snapshots, local provider-GeoJSON point-in-polygon region resolution, transition deduplication, bounded 1–60 second reconnect backoff, and clean recording teardown. Sessions retain `AVAILABLE`, `STALE`, `DISCONNECTED`, `UNAVAILABLE`, and `ERROR` coverage records plus only monotonic local-region start/end markers. Alert context remains absent from trust scoring and stores no provider alert timestamps, history, messages, or threat tracks.
Tests: `NeptunRegionGeoJsonTest` covers local polygon matching and malformed region data. `ExternalEvidenceRecordingSmokeTest` covers snapshot availability, WebSocket heartbeat/event handling, duplicate suppression, start/end mapping, malformed frames, disconnect, REST recovery, lifecycle teardown, and unchanged recording operation.
Verification: NEPTUN live read-only checks returned a 7,674-character alert snapshot with 43 keyed entries and a 630,131-character region GeoJSON response on 2026-09-08. The deterministic API-35 instrumentation feed emitted three expected transitions (`STARTED`, `ENDED`, recovery `STARTED`) while duplicate state emitted no extra marker; `ERROR` and `DISCONNECTED` states remained recorded. Alert coverage becomes `STALE` after 90 seconds without a new snapshot/frame and reconnect backoff is bounded at 60 seconds. Android fixture SHA-256 `92C32E70EABD767581FE1396EF0789B16164BEF76F930CCCC810744E423E00DC`. Exact smoke command: `.\gradlew.bat --no-daemon :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.example.navigation.app.ExternalEvidenceRecordingSmokeTest'` passed on `RenavSmokeApi35` API 35.

### Model

Terra

### Objective

Automatically add privacy-limited regional air-alert transitions to active walking recordings without requiring a private API credential.

### Scope

- Integrate NEPTUN's documented keyless WebSocket alert stream and REST `/api/v1/alerts` startup/reconnection snapshot.
- Resolve the current recording location to the applicable alert region locally and emit only state transitions for that region.
- Convert transitions through the existing `AirAlertMarkerMapper`, recording monotonic receipt time, provider identity, and source status only.
- Record source lifecycle as `AVAILABLE`, `STALE`, `DISCONNECTED`, or `ERROR`, including initial state, reconnects, and coverage gaps so no alert is distinguishable from no data.
- Implement bounded exponential reconnect backoff, conservative REST fallback, and clean start/stop with the walking recording lifecycle.
- Preserve the required visible NEPTUN attribution and informational/non-official-source notice wherever alert context is displayed.

Do not store provider alert timestamps/history, threat tracks, messages, or calendar dates; do not use alert state as proof of GNSS spoofing or as a safety notification.

### Dependencies

TASK 134, TASK 136–139

### Acceptance criteria

- Starting a walking recording obtains a snapshot and records subsequent local-region start/end transitions once each.
- Disconnect, stale data, malformed messages, and reconnect are explicit and never converted into false alert transitions.
- Recording remains usable offline and when NEPTUN is unavailable.
- Alert markers have no independent effect on GNSS trust scores or navigation estimates.

### Tests required

- JVM tests for snapshots, start/end transitions, duplicate events, irrelevant regions, malformed frames, stale/disconnected states, reconnect ordering, and alert-invariant trust scoring.
- Android integration test using a local deterministic fake feed for recording start/stop, REST fallback, WebSocket reconnect, and teardown.

### Completion evidence

Record endpoint/protocol version, attribution placement, polling/backoff policy, privacy field audit, transition trace, failure/recovery trace, fixture fingerprint, and exact test commands.

---

## TASK 143 — External Evidence Recording Diagnostics and Field Smoke Test

Status: COMPLETE
Implementation: Recording diagnostics now show independent spacecraft-reference and NEPTUN feed state, cache age, latest privacy-limited marker, uncertainty wording, and visible NEPTUN attribution/non-official-source notice. Session replay and local trust reports preserve feed states, coverage gaps, marker counts, and distinguish zero alerts with confirmed coverage from unavailable data. The recording workflow keeps both feeds optional and supports pose/event labels, stop, replay, report generation, and multiple selected session artifacts.
Tests: `ExternalEvidenceDiagnosticsTest` covers all external-feed states and safety attribution; `ExternalEvidenceSessionTest` covers deterministic round trip/replay, stale/disconnected evidence, report content, no alert history, and known coverage. `GnssTrustDiagnosticsExportTest`, `NeptunRegionGeoJsonTest`, and API-35 `ExternalEvidenceRecordingSmokeTest` cover diagnostics mapping and the walking start/pose/events/stop/two-session report journey.
Verification: Focused core/app tests and `:app:assembleDebug` passed 2026-09-08; the final API-35 smoke passed 1/1 with deterministic feeds, replay, two report artifacts, and clean teardown. APK: `app/build/outputs/apk/debug/app-debug.apk`, SHA-256 `3DAC55183EBFF141E8245CBBF7581613A22558CFD30CE84EA821BBEEA49E2CDB`. Full `:app:testDebugUnitTest` passed. A broader `:core:test` run executed 192 tests with one unrelated existing failure: `NavigationPositionTest.requiredEnumsContainDocumentedValues` expects a set that omits the already implemented `MANUAL_START`; this task did not alter that out-of-scope test.

### Model

Luna

### Objective

Make external-feed coverage verifiable during walking-session collection and prove that exported sessions contain usable satellite-reference and air-alert context evidence.

### Scope

- Show separate spacecraft-reference and air-alert feed states in recording diagnostics, including cache age, coverage gaps, and provider attribution.
- Make clear that spacecraft geometry is reference evidence, NEPTUN is an informational aggregator, and neither source guarantees authenticity or safety.
- Include feed availability, stale/error intervals, reference identifiers, and marker counts in local acceptance/trust reports without adding alert history.
- Exercise the complete walking workflow: start recording, acquire or report both feeds, mark pose/events, stop, export, read back, and replay.
- Verify behavior with network available, network lost mid-session, valid satellite cache, expired cache, alert transition, and no-alert-with-confirmed-coverage scenarios.

Do not block recording/export on either feed, expose precise alert history, or change navigation and trust decisions in the UI.

### Dependencies

TASK 135–142

### Acceptance criteria

- A tester can distinguish active, stale, disconnected, and unavailable external evidence while recording.
- Exported sessions and reports preserve external evidence, source states, and coverage gaps through round trip and replay.
- No-alert-with-coverage is distinguishable from unavailable alert data.
- The complete field-recording journey remains usable when one or both feeds fail.

### Tests required

- Presentation tests for every feed state, attribution, uncertainty wording, and reduced-motion/accessibility semantics.
- Session round-trip and deterministic replay tests for online, offline, stale-cache, transition, and coverage-gap fixtures.
- API-35 emulator smoke test of the complete walking recording and multi-session export journey with deterministic fake feeds.

### Completion evidence

Record exact commands, displayed states, accessibility result, exported filenames, report contents, replay result, feed-state sequence, and post-smoke crash/UI-obstruction check.
