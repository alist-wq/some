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
```

Nullable fields must reflect Android API availability.

### Acceptance criteria

GNSS samples can be serialized for replay.

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

A recorded session can replay deterministically independent of actual wall-clock time.

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

Recorded streams produce events in original timestamp order.

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

### Tests

Mock/provider-level tests where possible.

Run Android build.

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

### Model
Sol

### Objective

Implement gradual re-entry of GNSS after rejection.

Must not snap immediately to first candidate fix.

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

---

## TASK 087 — Diagnostics Screen

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

Do not blindly resume dead reckoning from stale state without checking timestamps.

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

# EPIC 25 — First Integrated Milestones

---

## MILESTONE A — Recorder

Required tasks:

```text
001–031
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