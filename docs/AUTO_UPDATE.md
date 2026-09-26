# Auto Update Architecture

## Purpose

VS Cartographer Auto Update is implemented as five application-level stages.
This numbering is independent of the older Windows packaging stages in
`docs/WINDOWS_RELEASE.md`.

```text
Stage 1  Release Foundation
Stage 2  Update Detection
Stage 3  Secure Download
Stage 4  Install & Restart
Stage 5  Final Validation
```

The stable release channel is the only supported channel in Auto Update v1.

## Stage 1: Release Foundation

Implemented on master before Stage 2.

Provides:

```text
canonical MAJOR.MINOR.PATCH version
runtime ApplicationVersion
tag/version validation
GitHub Release assets
update.properties
installer SHA-256 and size metadata
```

Stable GitHub Releases are published by the Windows Release Build workflow.
The normal release path is the explicit Actions `publish` mode; it creates the
stable tag only after the quality, packaging, and artifact-validation gates
pass, then publishes the complete draft Release. The GitHub Releases creation
UI is not a second release-authoring path for this project.

The latest stable manifest is available through the GitHub latest-release asset
endpoint:

```text
https://github.com/MariuszKam/cartographer/releases/latest/download/update.properties
```

## Stage 2: Update Detection

Stage 2 answers only one product question:

> Is there a newer stable VS Cartographer release available?

It does not download or execute the installer.

### S2.1 Manifest client and parser

The application fetches the Stage 1 `update.properties` manifest through
`java.net.http.HttpClient`.

The production source:

- uses HTTPS;
- starts from the project-owned GitHub latest-release endpoint;
- follows normal HTTPS redirects only while the final response remains on
  trusted GitHub-owned HTTPS hosts (`github.com` or
  `*.githubusercontent.com`);
- uses bounded connect/request timeouts;
- treats non-200 responses as failed checks.

The parser accepts schema version 1 and channel `stable` only. Required
installer/release URLs must use `https://github.com`. The SHA-256 field must
have the expected hexadecimal shape and the installer size must be positive.

Stage 2 validates manifest structure only. Stage 3 will hash the downloaded
installer bytes and enforce the digest before installation.

### S2.2 Version comparison

`UpdateCheckService` compares the manifest version with the generated runtime
`ApplicationVersion`.

```text
remote > current   UPDATE_AVAILABLE
remote = current   UP_TO_DATE
remote < current   UP_TO_DATE (never downgrade)
network/parser     CHECK_FAILED
```

A failed check is not interpreted as "up to date".

### S2.3 Non-blocking startup check

The desktop application shows the JavaFX window first and performs update I/O
on a dedicated daemon executor afterward.

The JavaFX Application Thread is not used for network I/O.

Automatic failures are silent: unavailable internet or GitHub must not prevent
the application from starting or using local saves. Manual checks expose a
compact retry state in the World Bar.

The update executor is shut down from `Application.stop()`.

### S2.4 Preferences and 24-hour TTL

Cartographer-owned update state is stored outside the game save:

```text
~/.vs-cartographer/update.properties
```

Current fields:

```properties
autoCheck=true
lastSuccessfulCheck=<ISO-8601 instant>
```

Defaults:

- automatic checking enabled;
- no previous successful check.

Automatic checks use a 24-hour minimum interval. Manual checks always bypass
that TTL.

Only a successful, parseable update check advances `lastSuccessfulCheck`.
Network failures and malformed manifests do not suppress the next automatic
attempt.

Unreadable or malformed preference data falls back safely to automatic
checking instead of disabling updates.

### S2.5 World Bar UX

The Workstation World Bar displays the running version.

Normal state:

```text
VS CARTOGRAPHER  v1.0.0   ...   Check updates
```

When a newer release is found:

```text
VS CARTOGRAPHER  v1.0.0   ...   Update 1.1.0   Check again
```

The update chip opens the corresponding GitHub Release page. It does not
download the installer in Stage 2.

Manual states include:

```text
Checking...
Up to date
Retry updates
```

Automatic failed checks do not display disruptive dialogs.

## Stage 2 non-goals

Stage 2 intentionally does not implement:

```text
installer download
.part staging
download progress
installer SHA-256 verification
restart
installer execution
rollback
beta/prerelease channels
forced updates
```

Those responsibilities belong to later Auto Update stages.

## Stage 2 validation gates

Before Stage 2 is considered validated:

1. the exact branch/PR diff receives independent controller review;
2. the Gradle test suite is green on the final Stage 2 HEAD;
3. the packaged/desktop GUI is manually checked for layout at supported window
   sizes;
4. manual update checking is exercised with both reachable and unavailable
   network conditions;
5. a controlled newer manifest/release is used to confirm the update chip and
   release link behavior.

Static implementation completion alone is not a runtime PASS.


## Stage 3: Secure Download

Stage 3 turns a detected stable manifest into a locally staged, verified
installer. It still does not execute the installer or restart the application.

### S3.1 Streaming installer download

`HttpUpdateInstallerSource` downloads the installer through
`java.net.http.HttpClient`.

The production source:

- starts from the HTTPS GitHub installer URL already validated by Stage 2;
- follows normal HTTPS redirects used by GitHub release assets;
- uses bounded connection and request timeouts;
- requires HTTP 200;
- rejects redirects that do not remain on HTTPS;
- rejects final redirect targets outside GitHub-owned hosts
  (`github.com` or `*.githubusercontent.com`);
- streams the response instead of buffering the complete EXE in memory;
- rejects responses that exceed the manifest installer size;
- rejects a present `Content-Length` that disagrees with the manifest.

The manifest remains the authoritative size/hash contract.

### S3.2 Staging layout

Downloaded installers are Cartographer-owned state under:

```text
~/.vs-cartographer/updates/<version>/
```

An in-progress download is always written as:

```text
VS-Cartographer-Setup-<version>.exe.part
```

The final installer name does not exist until verification succeeds. Stale
partial files are removed before a retry.

The Stage 2 manifest contract already restricts `installerFile` to a Windows
EXE base name. Stage 3 additionally normalizes and checks resolved target paths
before writing.

### S3.3 Download progress

Download work runs on the dedicated update background executor, never on the
JavaFX Application Thread.

The World Bar keeps the Stage 2 release-notes chip and adds a download action:

```text
Update 1.1.0   Download
```

During transfer:

```text
Update 1.1.0   Downloading 42%
```

Progress is derived from bytes written against the manifest installer size and
UI updates are reduced to percentage changes.

### S3.4 Size and SHA-256 verification

A downloaded `.part` file is promoted only when both are true:

```text
actual size == manifest installerSize
SHA-256     == manifest installerSha256
```

A mismatch fails closed and the partial file is removed. The verified file is
then moved to the canonical `.exe` name, using an atomic move when the
filesystem supports it and a same-directory replacement move otherwise.

If the canonical installer already exists on a later attempt, Stage 3 verifies
its size and SHA-256 first. A valid installer is reused without another network
download; an invalid installer is removed before retrying.

### S3.5 Retry and cleanup

Failed or interrupted downloads leave no intentionally usable installer.
Partial data is cleaned up and the available update remains retryable from the
World Bar.

Interruption restores the Java thread interrupt flag. Application shutdown can
therefore stop update work without treating a partial file as complete.

Before starting a fresh network transfer, Stage 3 also checks that the target
filesystem reports at least the manifest installer size as usable space.

Successful Stage 3 UI state is:

```text
Ready <version>
```

This means only:

> the installer exists locally and passed the manifest size and SHA-256 checks.

It does not mean the installer has been executed.

## Stage 3 non-goals

Stage 3 intentionally does not implement:

```text
installer execution
application shutdown for update
wait-for-PID bootstrap
restart/relaunch
automatic rollback
delta updates
download resume/range requests
beta/prerelease channels
forced updates
```

Those responsibilities remain in Stage 4 or later hardening.

## Stage 3 validation gates

Before Stage 3 is considered validated:

1. the exact branch/PR diff receives independent controller review;
2. the Gradle test suite is green on the final Stage 3 HEAD;
3. a controlled valid installer payload reaches `Ready <version>`;
4. wrong-size and wrong-SHA payloads are rejected and partial files are
   removed;
5. an interrupted/failed transfer is retryable;
6. an already verified installer is reused without a second transfer;
7. the desktop GUI is manually checked at supported minimum/normal window
   sizes while available, downloading, failed, and ready states are visible.

Stage 3 completion does not prove Windows installation or restart behavior.
Those are Stage 4 concerns.


## Stage 4: Install & Restart

Stage 4 turns a Stage 3 `READY` installer into a controlled Windows upgrade.
It deliberately delegates application replacement to the existing jpackage
installer instead of modifying installed JAR/runtime files itself.

### S4.1 Re-verify before installation

`UpdateInstallService` re-validates the staged EXE against the manifest size
and SHA-256 immediately before starting the bootstrap.

The shared `UpdateInstallerVerifier` is used by both Stage 3 download
promotion and Stage 4 install launch. A missing or changed installer fails
closed and returns the UI to a download-retry state.

The external bootstrap independently repeats the size and SHA-256 verification
after the current Cartographer process has exited. This materially narrows the integrity window between the in-process verification
and actual installer execution.

### S4.2 External Windows bootstrap

The updater bootstrap is generated under Cartographer-owned state:

```text
~/.vs-cartographer/updates/bootstrap/install-update.ps1
```

It is outside the installed application directory so the jpackage installer is
not asked to replace files used by the bootstrap itself.

Restart-and-update is available only when the current process resolves to the
packaged launcher:

```text
VS Cartographer.exe
```

Development runs through `java.exe` / Gradle `run` therefore fail safely
instead of attempting to update an arbitrary Java process.

### S4.3 Wait for the application process

The bootstrap receives the exact current process ID and uses the Windows
process lifecycle as its synchronization boundary:

```text
start bootstrap
    -> application requests Platform.exit()
    -> Application.stop() cancels Workstation operations and update work
    -> process exits
    -> bootstrap Wait-Process completes
```

No timer or guessed sleep is used to decide that Cartographer has probably
closed.

### S4.4 Installer execution and result

After the process exit and the second integrity check, PowerShell starts the
verified jpackage EXE with Windows Installer arguments:

```text
/quiet
/norestart
/L*V <installer-log>
```

The Java 25 jpackage EXE wrapper forwards these arguments to its embedded MSI,
so the update runs without the installer wizard while Windows Installer remains
the authority for replacing the installed product. A per-version verbose log
is written under:

```text
~/.vs-cartographer/updates/bootstrap/install-<version>.log
```

The bootstrap records the installer result under:

```text
~/.vs-cartographer/updates/bootstrap/result.properties
```

The result distinguishes:

```text
SUCCESS
RESTART_REQUIRED
INTEGRITY_CHECK_FAILED
INSTALLER_FAILED
BOOTSTRAP_FAILED
```

and includes the target application version and installer exit code. Windows
Installer codes `0` and `1641` are successful outcomes; `3010` is recorded
as `RESTART_REQUIRED` instead of being misreported as an installation failure.
The next Cartographer launch consumes this small result once; malformed/stale
result data never blocks application startup.

### S4.5 Relaunch

After installer completion the bootstrap attempts to start the same packaged
`VS Cartographer.exe` path that launched the update.

A successful recorded installation is accepted by the UI only when the running
application version is at least the recorded target version. If the installer
reported success but the old version is still running, the application reports
the mismatch instead of pretending the upgrade succeeded.

A `3010` outcome is surfaced separately as `Restart required`. The World Bar
does not label that Windows Installer success code as a failed update.

### S4.6 Desktop UX and failure behavior

Stage 3 `Ready <version>` becomes an explicit user action:

```text
Restart & update
```

The application does not install in the background without an explicit user
action. After `Restart & update` is chosen, the native installer UI is
suppressed and the upgrade proceeds unattended.

Before shutdown the UI shows a preparing state. If bootstrap startup fails, the
verified installer remains retryable. If the installer is no longer valid, the
ready state is discarded and the normal Stage 3 download path must verify or
download it again.

A failed bootstrap/installer outcome is shown on the next launch without
preventing normal local application use or update checks.

### Stage 4 safety boundaries

Stage 4 does not:

```text
overwrite installed application files directly
modify Vintage Story .vcdbs saves
delete Cartographer user configuration
force an update
implement automatic rollback
support beta/prerelease channels
support delta patching
```

The existing jpackage Windows upgrade UUID remains the authority for replacing
a compatible installed VS Cartographer version.

## Stage 4 validation gates

Stage 4 implementation is not considered runtime validated until all the
following are checked on the exact final Stage 4 candidate:

1. independent controller review of the exact branch/PR diff;
2. green Gradle test suite;
3. packaged Windows launch detects and securely downloads a controlled newer
   stable release;
4. `Restart & update` closes the old process, runs the installer only after
   process exit with no native installer wizard, and relaunches the application;
5. the relaunched application reports the new runtime version;
6. Windows Installer failure and restart-required outcomes are reported
   accurately and do not falsely claim an ordinary successful completion;
7. tampering with the staged installer between Ready and execution is rejected;
8. Cartographer-owned configuration remains present across the upgrade.

Vintage Story save-integrity, shortcuts/uninstallation, and the complete
old-version-to-new-version release campaign remain part of Stage 5 final
validation. Static implementation alone is not a Stage 4 runtime PASS.


## Stage 5: Final Validation

Stage 5 is the acceptance campaign for Auto Update v1. It does not introduce a
new updater runtime layer. Its job is to prove that Stages 1–4 work together on
real Windows packages without damaging Vintage Story saves or persistent
Cartographer-owned state.

The detailed procedure and commands live in:

```text
docs/AUTO_UPDATE_STAGE5_VALIDATION.md
tools/validate-auto-update-stage5.ps1
```

### S5.1 Controlled old-to-new upgrade

A real installed old stable version must detect, download, verify, install and
relaunch into a newer stable GitHub Release.

The authoritative runtime check is the version shown by the relaunched
application. Windows uninstallation registration and shortcuts are additional
packaging evidence.

### S5.2 Cartographer-owned state persistence

Before the campaign, the reviewer creates meaningful persistent state such as a
HOME or user marker. Stage 5 snapshots protected Cartographer-owned files under
`~/.vs-cartographer`.

Volatile cache/update data is excluded. `update.properties` is handled
separately because `lastSuccessfulCheck` is expected to change; persisted
`autoCheck` must remain stable.

### S5.3 Vintage Story save integrity

The real validation save must remain byte-for-byte unchanged across upgrade and
uninstall:

```text
SHA-256 before == SHA-256 after
length before  == length after
no new .vcdbs-wal
no new .vcdbs-shm
```

Pre-existing SQLite sidecars are recorded and never deleted by validation
tooling.

### S5.4 Installer, shortcuts and uninstall

The campaign verifies the installed product registration, Desktop and Start
Menu shortcuts after upgrade, then verifies that uninstall removes the installed
product registration, shortcuts and launcher while preserving Cartographer user
state and the Vintage Story save.

### S5.5 Failure scenarios

Required reviewer scenarios include:

```text
tampered staged installer
silent installer failure/restart-required outcome
GitHub/network unavailable
```

The Stage 5 helper can prepare and restore a deliberately tampered staged
installer without modifying the Vintage Story save.

### Stage 5 validation status

Static implementation and CI are not sufficient to mark Stage 5 complete.
Final acceptance requires reviewer-controlled Windows runtime evidence for the
exact candidate branch/release pair. Until that evidence exists, Stage 5 remains
`PENDING MANUAL VALIDATION`.
