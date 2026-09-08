# Repository Audit

## Scope

This audit verifies the repository structure against the architecture described in `AGENTS.md`, as required by TASK 001.

## Current repository contents

The repository root currently contains:

- `AGENTS.md`
- `CODEX_BACKLOG.md`

No application source tree or Android project files are present.

## Architecture mapping

| Desired area | Current state | Assessment |
| --- | --- | --- |
| `app/` | Missing | No Android application module is present. |
| `core/` | Missing | No shared core module is present. |
| `sensors/` | Missing | No sensor providers or sensor module are present. |
| `gnss/` | Missing | No GNSS provider, models, or trust components are present. |
| `obd/` | Missing | No OBD transport or vehicle-speed components are present. |
| `fusion/` | Missing | No estimation or dead-reckoning components are present. |
| `pedestrian/` | Missing | No pedestrian navigation components are present. |
| `matching/` | Missing | No route/map-matching components are present. |
| `routing/` | Missing | No routing components are present. |
| `maps/` | Missing | No offline map components are present. |
| `navigation/` | Missing | No navigation state or position models are present. |
| `replay/` | Missing | No replay or recorded-session components are present. |
| `data/` | Missing | No data module or persistence format is present. |
| `benchmark/` | Missing | No benchmark module or runner is present. |

The desired architecture therefore has no implemented counterpart in the current repository yet.

## Missing modules and files

The repository has no Android modules and no Kotlin source files. The following foundational project files are also missing:

- `settings.gradle` or `settings.gradle.kts`
- Root `build.gradle` or `build.gradle.kts`
- Gradle wrapper files
- Android manifest files
- Android module build files
- `src/main` and `src/test` source trees

## Unexpected dependencies

No dependency declarations or lockfiles were found. Consequently, no unexpected external dependencies can be identified.

## Package structure

No package declarations or Kotlin/Java source files were found. Package structure is not yet established.

## Existing build system

No build system is present. Gradle, the Gradle wrapper, and Android project configuration files were not found.

## Kotlin and Android versions

The Kotlin version, Android Gradle Plugin version, minimum SDK, and target SDK cannot be determined because no Android/Gradle configuration exists.

## Compose status

Jetpack Compose status is not applicable yet. No Compose dependencies, source files, or Android UI module were found.

## Existing test infrastructure

No test source trees, test configuration, test dependencies, or test runner configuration were found. TASK 001 specifies no tests.

## Findings and recommended foundation mapping

The repository is an initial documentation/backlog foundation rather than an Android implementation. To align it with the desired architecture, subsequent repository-foundation work must first establish the Android Gradle project and module boundaries, then add shared models and infrastructure in the order described by the backlog. This audit does not implement those changes.

