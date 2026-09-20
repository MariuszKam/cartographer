# Auto Update Architecture

## Purpose

VS Cartographer Auto Update is implemented as five application-level stages.
This numbering is independent from the older Windows packaging stages in
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
- follows normal HTTPS redirects;
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
on a dedicated daemon executor afterwards.

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
