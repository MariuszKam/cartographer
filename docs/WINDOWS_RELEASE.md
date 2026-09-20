# Windows Release Structure

## Entry points

VS Cartographer has two entry points:

- CLI: `cartographer.Main`
- Desktop: `cartographer.ui.CartographerDesktopLauncher`

The Gradle `application.mainClass` intentionally remains `cartographer.Main` so
the existing CLI application workflow is unchanged. Windows packaging
explicitly selects `cartographer.ui.CartographerDesktopLauncher`.

## Packaging input

Stage 1 provides the Gradle preparation task:

```text
./gradlew prepareJpackageInput
```

It stages the application JAR and all JARs from the runtime classpath in:

```text
build/jpackage/input/
```

This is the non-modular classpath input for the future Windows packaging step.

## Release version and metadata

The canonical release version is stored once in:

```text
gradle.properties
```

using stable semantic versioning:

```properties
version=1.0.0
```

`build.gradle.kts`, Windows package names, release validation, generated runtime
metadata, release tags, and the update manifest all consume that canonical
version. Auto Update v1 intentionally accepts only `MAJOR.MINOR.PATCH`; prerelease
versions such as `1.1.0-beta.1` are outside the first update-channel contract.

The Windows app-image passes the following metadata to `jpackage`:

```text
Name: VS Cartographer
Version: <project version>
Vendor: MariuszKam
Description: Offline Vintage Story save cartographer and world analysis tool
Copyright: Copyright © 2026 MariuszKam
```

The build also generates `cartographer-build.properties` into the runtime
resources. `cartographer.update.ApplicationVersion.current()` reads that
generated metadata, so the running application does not maintain a second
hard-coded version constant.

## Stage 2: portable Windows app image

Stage 2 adds Windows-specific Gradle tasks using the JDK 25 `jpackage` tool.
The app-image uses the desktop launcher and contains its own generated Java
runtime. The application remains a non-modular classpath application; no custom
runtime image is supplied.

Run on Windows with:

```powershell
.\gradlew.bat packageWindowsAppImage
```

The expected output is:

```text
build/jpackage/app-image/VS Cartographer/
├── VS Cartographer.exe
├── app/
└── runtime/
```

The packaging task resolves `jpackage.exe` from the project's Java 25 Gradle
toolchain and fails when invoked on a non-Windows host. The packaged launcher
receives `--enable-native-access=ALL-UNNAMED`.

The complete app-image can be distributed as a portable ZIP:

```powershell
.\gradlew.bat packageWindowsPortable
```

The expected output is:

```text
build/distributions/VS-Cartographer-1.0.0-win-x64.zip
```

The ZIP preserves the top-level `VS Cartographer/` directory and contains the
complete app-image, including `app/` and `runtime/`.

The intended release flow is:

```text
prepareJpackageInput
        ↓
jpackage --type app-image (Stage 2)
        ↓
portable Windows application
        ↓
jpackage Windows installer (Stage 4)
```

Stage 4 provides the Windows EXE installer task. Icon customization remains
optional; Stage 2 does not provide an icon unless the project icon is added.

## Runtime

The project targets Java 25. Packaged launchers retain the equivalent of:

```text
--enable-native-access=ALL-UNNAMED
```

The desktop packaging metadata is intended for the application name
`VS Cartographer`, desktop main class
`cartographer.ui.CartographerDesktopLauncher`, and vendor `MariuszKam`.

## Stage 3: Windows package polish

Stage 3 adds release metadata and optional custom icon support. A project-owned
Windows icon may be placed at:

```text
src/main/packaging/vs-cartographer.ico
```

When that file exists, `jpackage` uses it automatically. When it is absent,
packaging continues with the standard `jpackage` icon. Stage 3 does not commit
a placeholder icon.

The packaged launcher remains GUI-only and does not use `--win-console`. The
CLI remains available separately through the existing Gradle application
workflow.

## Stage 4: Windows EXE installer

Stage 4 adds a Windows EXE installer that packages the previously generated
`build/jpackage/app-image/VS Cartographer/` app-image. It does not reconstruct
the application JAR, classpath, launcher, or bundled runtime.

Installer creation is run later on Windows with:

```powershell
.\gradlew.bat packageWindowsInstaller
```

The packaging machine requires a JDK 25 toolchain and WiX Toolset 3.0 or later.
WiX must be installed and available to `jpackage` by the packaging machine; the
project does not download, install, or bundle WiX.

The expected installer artifact is:

```text
build/distributions/VS-Cartographer-Setup-1.0.0.exe
```

The installer requests per-user installation, a `VS Cartographer` Start Menu
group and shortcut, a Desktop shortcut, and an installation directory chooser.
Its stable Windows upgrade UUID is:

```text
d9458212-ecd8-4882-8d90-ffba8ade0c4f
```

This UUID is part of the product identity and must remain unchanged for
compatible future releases unless VS Cartographer intentionally becomes a
different Windows product.

The installer remains GUI-only and does not use `--win-console`. It is currently
unsigned, so Windows SmartScreen may warn during manual validation. It does not
provide `.vcdbs` file associations or automatic updates.

## Stage 5: release validation and hardening

Stage 5 provides a repeatable structural and save-integrity validation helper.
It does not launch the application, installer, or Java, and it does not open a
save through SQLite.

### Automated structural/integrity checks

Run artifact checks later, after the release artifacts have been built:

```powershell
powershell -ExecutionPolicy Bypass -File tools/validate-windows-release.ps1 -Mode Artifacts
```

Artifacts mode checks the app-image launcher and `app/` and `runtime/`
directories, checks the canonical portable ZIP and installer for non-zero size,
inspects the ZIP entries without extracting or executing them, and writes the
portable ZIP and installer SHA-256 values to:

```text
build/release-validation/SHA256SUMS.txt
```

The helper does not require `runtime/bin/java.exe` and does not require the
unsigned installer to have Authenticode signing. It reports structure only; it
does not claim that the GUI, CLI, installer, shortcuts, or uninstall work.

With Vintage Story closed, record a save baseline before manual regression:

```powershell
powershell -ExecutionPolicy Bypass -File tools/validate-windows-release.ps1 -Mode Before -SavePath "<path-to-save.vcdbs>"
```

The baseline is written only to `build/release-validation/save-baseline.json`
and contains the normalized save path, SHA-256, file length, timestamp, and
initial `-wal`/`-shm` sidecar state. The helper performs only read-only
filesystem inspection of the save and does not delete pre-existing sidecars.

After all manual checks, with applications closed, run:

```powershell
powershell -ExecutionPolicy Bypass -File tools/validate-windows-release.ps1 -Mode After -SavePath "<path-to-save.vcdbs>"
```

After mode is a mandatory release gate: the save SHA-256 and size must be
unchanged, and no new `-wal` or `-shm` sidecar may have appeared. Existing
sidecars are reported as pre-existing and are left untouched.

### Clean regression build

The user + ChatGPT will run the following later; these commands are not part of
the validation helper:

```powershell
.\gradlew.bat clean
.\gradlew.bat test
.\gradlew.bat packageWindowsPortable
.\gradlew.bat packageWindowsInstaller
```

### Manual CLI regression

The user + ChatGPT will run the existing CLI regression pattern against the
real save:

```powershell
.\gradlew.bat run --args='map render <save.vcdbs> --radius 512 --out output\map-final.png --style topographic --layers terrain,surface,environment,geology,markers'
```

The command must complete successfully, create the output PNG, and report zero
failed regions and zero failed chunks/mapchunks when those counters are
reported by the application. The output map requires manual visual inspection.

### Manual portable GUI regression

The user + ChatGPT will extract `VS-Cartographer-1.0.0-win-x64.zip` outside the
repository and launch `VS Cartographer.exe` from the extracted directory. They
will verify that there is no unwanted console window, dark JavaFX styling is
present, and a real `.vcdbs` save opens through the File Chooser. They will
manually check player position, map interaction, Ores, Surface, Rock,
Prospecting, expected layers, map controls, and normal application close.

To check bundled-runtime independence, they will launch the extracted EXE
outside the repository and IntelliJ, optionally set `JAVA_HOME` to an invalid
location only in the current PowerShell process, launch again, and restore the
original process-local value. They will not modify machine-wide `JAVA_HOME` or
permanent `PATH`, and `runtime/bin/java.exe` is not required.

### Manual installer regression

The user + ChatGPT will verify that the unsigned installer starts, presents the
installation directory chooser, installs per-user, creates the Desktop shortcut
and `VS Cartographer` Start Menu entry, and launches the installed GUI without
an unwanted console window. They will open a real save and manually check Ores,
Surface, Rock, Prospecting, and map interaction. A Windows SmartScreen warning
is acceptable for this unsigned installer.

### Persistence test

The user + ChatGPT will create or modify safe Cartographer-owned state, such as
the HOME marker or existing user markers, close the application, relaunch it,
and verify that the state persists as expected. This validates the existing
`.vs-cartographer` behavior; it does not migrate storage to `%APPDATA%`.

### Uninstall test

The user + ChatGPT will verify that uninstall completes, installed program files
are removed as expected, and the Start Menu and Desktop shortcuts are removed.
They will also verify that the Vintage Story save remains untouched and that
user-owned Cartographer configuration is not destructively removed unless the
existing application behavior explicitly provides otherwise. No custom
uninstall cleanup is added by Stage 5.

## Stage 6: GitHub Actions Windows release automation

Stage 6 uses:

```text
.github/workflows/windows-release.yml
```

for both manual package validation and tag-driven stable releases.

A manual `workflow_dispatch` still builds and validates the selected ref, then
uploads the Windows deliverables as a short-lived Actions artifact. It does not
publish a GitHub Release.

A pushed stable tag matching:

```text
v<project version>
```

for example `v1.1.0`, runs the same test/package/validation gates and additionally
publishes the release. The Gradle `verifyReleaseTag` task fails closed when the
tag does not exactly match the canonical version from `gradle.properties`.

The release workflow uses Temurin Java 25 and installs WiX 5.0.2 through the
official .NET global tool:

```text
WixToolset.Util.wixext 5.0.2
WixToolset.UI.wixext 5.0.2
```

The Windows release deliverables are version-derived rather than hard-coded:

```text
VS-Cartographer-<version>-win-x64.zip
VS-Cartographer-Setup-<version>.exe
SHA256SUMS.txt
update.properties
```

For a tag build, `generateUpdateManifest` creates:

```text
build/release-validation/update.properties
```

with the stable update contract:

```properties
schemaVersion=1
channel=stable
version=<version>
installerFile=VS-Cartographer-Setup-<version>.exe
installerUrl=https://github.com/MariuszKam/cartographer/releases/download/v<version>/VS-Cartographer-Setup-<version>.exe
installerSha256=<sha256>
installerSize=<bytes>
releaseUrl=https://github.com/MariuszKam/cartographer/releases/tag/v<version>
```

The workflow creates the GitHub Release as a draft first, uploads all four
release assets, and only then publishes the draft. A failed publication therefore
does not intentionally expose an incomplete release through the stable channel.

Creating a tag is the publication boundary:

```text
master
  -> set canonical version
  -> validation/review
  -> tag vX.Y.Z
  -> Windows Release Build
  -> tests
  -> portable ZIP
  -> installer EXE
  -> structural/checksum validation
  -> update manifest
  -> draft GitHub Release
  -> published GitHub Release
```

CI still does not run against the user's real save, run Stage 5 Before/After
modes, automate GUI interaction, install or uninstall the installer, or prove
the upgrade path. Installer/update runtime validation remains reviewer-controlled
and is required in the later Auto Update validation stage.

## Pull request CI

The lightweight pull request workflow is:

```text
.github/workflows/pr-ci.yml
```

It runs automatically only for pull requests targeting `master`, on a
`windows-latest` runner. It uses Temurin Java 25 with Gradle dependency caching
and its current gate is:

```powershell
.\gradlew.bat test
```

The workflow has `contents: read` permissions. It allows one active run per PR;
when a newer commit is pushed to the same PR, the superseded run is cancelled.

PR CI is deliberately lightweight. It validates application tests before merge
but does not install WiX, run jpackage, build the portable ZIP, build the
installer EXE, run Windows release artifact validation, publish artifacts,
launch the GUI, or access a real Vintage Story save.

The heavier release pipeline remains:

```text
.github/workflows/windows-release.yml
```

Normal pull requests continue to use lightweight test-only CI. Windows packaging
runs only when explicitly dispatched or when a release tag is pushed, so normal
code changes do not pay the WiX/jpackage cost.

```text
Pull request -> PR CI -> Java 25 -> Gradle tests -> merge eligibility

manual dispatch -> tests -> Windows packages -> artifact validation -> Actions artifact

tag vX.Y.Z -> tag/version gate -> tests -> Windows packages -> artifact validation
            -> update manifest -> published GitHub Release
```

This documentation does not claim that PR CI has executed successfully or that
branch protection currently requires its check.

## Packaging non-goals

The Windows packaging stages do not:

- create an MSI;
- change CLI behavior;
- change save handling;
- change application storage;
- change runtime semantics.

Stage 3 does not create an installer, add file associations, or supply a custom
runtime image. The executable, metadata, and optional icon behavior have not
been tested or verified by this documentation.
