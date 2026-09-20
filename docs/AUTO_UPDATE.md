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
