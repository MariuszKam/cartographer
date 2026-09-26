# VS Cartographer

[![PR CI](https://github.com/MariuszKam/cartographer/actions/workflows/pr-ci.yml/badge.svg?branch=master)](https://github.com/MariuszKam/cartographer/actions/workflows/pr-ci.yml)

**VS Cartographer** is a desktop application for offline analysis of [Vintage Story](https://www.vintagestory.at/) `.vcdbs` save files.

It turns explored world data into an interactive workstation for mapping terrain, finding resources, inspecting geology and coverage, and combining saved evidence for prospecting — without intentionally modifying the source save.

> **For reliable analysis, close Vintage Story before opening a save in Cartographer.**

## What you can do

| Tool            | Purpose                                                                                                |
| --------------- | ------------------------------------------------------------------------------------------------------ |
| **Map**         | Render explored terrain with optional surface, soil-fertility, environment, geology and marker layers. |
| **Coverage**    | Visualize observed mapregion coverage and holes inside the explored bounds.                            |
| **Ores**        | Scan authoritative saved CHUNK data for one or multiple ore resources, with optional Y filtering.      |
| **Surface**     | Analyze discovered surface objects or surface materials around the selected area.                      |
| **Geology**     | Render the upper rock layer or inspect rock at a selected world Y level.                               |
| **Prospecting** | Combine saved geology and ore evidence into resource assessments.                                      |

The workstation also provides configurable radii, player centering, zoom/navigation, result inspection, technical diagnostics, HOME/user markers, and reusable derived world snapshots for faster repeated analysis.

## Save safety

The Vintage Story save is treated as **authoritative read-only input**.

Cartographer opens the source SQLite database read-only and does not intentionally migrate, repair, rewrite or otherwise modify it. Caches, snapshots, indexes, HOME state, markers, update files and diagnostic logs are Cartographer-owned derived state stored separately under:

```text
~/.vs-cartographer/
```

Derived data is disposable and rebuildable. It never replaces the source save as the authority.

Malformed or unsupported data is handled defensively where possible; Cartographer keeps distinctions such as missing, unknown and corrupt data instead of inventing replacement world data.

## Install on Windows

Official packaged releases are built for **Windows x64**.

1. Open the [latest GitHub Release](https://github.com/MariuszKam/cartographer/releases/latest).
2. Download `VS-Cartographer-Setup-<version>.exe`.
3. Run the installer and launch **VS Cartographer**.
4. Close Vintage Story.
5. Choose **Open save** and select a `.vcdbs` save.
6. Pick a tool, radius and options, then render or analyze.

A portable Windows ZIP is also published with each stable release.

### World snapshot

For larger worlds or repeated analysis, **Prepare world** builds reusable derived snapshot/index data under Cartographer's own cache directory.

Snapshot data may be refreshed or rebuilt at any time. It is an optimization layer, not a replacement for the save.

## Updates

The desktop application includes a stable-channel update flow.

Update metadata and installers are fetched over HTTPS from GitHub. Downloaded installers are checked against the published file size and SHA-256 before installation. The Windows updater waits for the running Cartographer process to exit before handing replacement to the packaged installer.

Stable GitHub Releases are owned by CI. Maintainers publish through:

```text
GitHub
  -> Actions
  -> Windows Release Build
  -> Run workflow
  -> release_mode = publish
```

The same workflow offers `validate` for a full packaging dry run without creating a tag or GitHub Release.

See [Windows release documentation](docs/WINDOWS_RELEASE.md) and [Auto Update architecture](docs/AUTO_UPDATE.md) for the complete release/update contracts.

## Development

The project targets **Java 25** and uses Gradle with JavaFX.

Run the desktop application:

```bash
./gradlew run
```

On Windows:

```powershell
.\gradlew.bat run
```

Run the canonical verification gate:

```bash
./gradlew testQualityGate
```

On Windows:

```powershell
.\gradlew.bat testQualityGate
```

The quality gate includes the test suite, architecture enforcement and the repository's test-performance contract.

Windows packaging additionally uses the JDK `jpackage` tool and WiX. The supported packaging/release procedures are documented in [docs/WINDOWS_RELEASE.md](docs/WINDOWS_RELEASE.md).

## Architecture

The project keeps authoritative source access, binary decoding, domain interpretation, application orchestration, derived persistence and presentation as explicit responsibilities.

Important invariants include:

- production package dependencies remain acyclic;
- presentation does not leak into lower-level capabilities;
- coordinate spaces are explicit domain concepts;
- source data and derived/cache data remain semantically distinct;
- concrete infrastructure is composed at the application edge;
- the source save remains read-only.

The durable rules live in [AGENTS.md](AGENTS.md) and [docs/ARCHITECTURE_PRINCIPLES.md](docs/ARCHITECTURE_PRINCIPLES.md).

## Further documentation

- [Testing architecture](docs/TESTING_ARCHITECTURE.md)
- [Windows packaging and release](docs/WINDOWS_RELEASE.md)
- [Auto Update architecture](docs/AUTO_UPDATE.md)
- [Auto Update Stage 5 validation](docs/AUTO_UPDATE_STAGE5_VALIDATION.md)
