# Auto Update Stage 5 — Final Validation

## Purpose

Stage 5 is the acceptance campaign for the complete Auto Update v1 stack.

It does not add a sixth runtime updater subsystem. Stages 1–4 already provide:

- release publication;
- update detection;
- secure installer download;
- verified install bootstrap;
- restart and outcome handling.

Stage 5 proves that those pieces work together on Windows against real packaged
releases and that the update does not damage Vintage Story saves or
Cartographer-owned user state.

The stage remains reviewer-controlled. GitHub Actions cannot prove interactive
installer behavior, Windows shortcuts, GUI state, uninstall behavior, or real
save integrity.

## Stage 5 gates

```text
S5.1  controlled old-version -> new-version upgrade
S5.2  Cartographer-owned state persistence
S5.3  Vintage Story save integrity
S5.4  installer / shortcuts / uninstall
S5.5  failure scenarios and final evidence
```

The helper is:

```text
tools/validate-auto-update-stage5.ps1
```

Automated evidence is written under:

```text
build/auto-update-stage5/
├── campaign-baseline.json
├── after-upgrade.json
└── after-uninstall.json
```

These files are validation evidence only and are not application state.

## Preconditions

Run Stage 5 on Windows with Vintage Story closed.

Before taking the baseline:

1. install the old stable VS Cartographer version using its normal EXE installer;
2. launch it and confirm the World Bar reports the old runtime version;
3. open the real validation save;
4. create or modify at least one persistent HOME or user marker;
5. close VS Cartographer;
6. ensure the newer stable release is ready to publish or already published as
   the GitHub latest stable release.

The old and target versions must use stable `MAJOR.MINOR.PATCH` numbering and
the target must be newer.

Example campaign:

```text
old     1.0.0
target  1.0.1
```

Do not use a production save that cannot be backed up independently.

## S5.1 — Baseline and old-to-new campaign

Take the baseline before checking for the newer release:

```powershell
powershell -ExecutionPolicy Bypass -File tools\validate-auto-update-stage5.ps1 `
  -Mode BeforeUpgrade `
  -SavePath "<path-to-save.vcdbs>" `
  -OldVersion 1.0.0 `
  -TargetVersion 1.0.1
```

The baseline gate checks:

- exactly one Windows uninstall registration for VS Cartographer;
- installed product version matches the old version;
- Desktop shortcut exists and resolves to `VS Cartographer.exe`;
- Start Menu shortcut exists and resolves to the same launcher;
- the real `.vcdbs` save SHA-256 and length;
- pre-existing WAL/SHM sidecar state;
- protected Cartographer-owned files outside volatile cache/update directories;
- persisted `autoCheck` preference when present.

The protected-state baseline deliberately excludes:

```text
~/.vs-cartographer/cache/**
~/.vs-cartographer/updates/**
~/.vs-cartographer/update.properties
```

`update.properties` is treated specially because
`lastSuccessfulCheck` is expected to change during the campaign. Its
`autoCheck` preference is checked separately.

If there is no persistent protected Cartographer state, the baseline fails.
Create a HOME or user marker first so persistence is actually being tested.

### Perform the real update

With the baseline recorded:

1. publish or expose the target stable release through the normal Stage 1
   GitHub Release pipeline;
2. start the installed old version;
3. confirm it detects the target release;
4. download the update;
5. confirm the UI reaches `Ready <target>`;
6. click `Restart & update`;
7. confirm the old process exits before the installer runs;
8. complete the installer;
9. confirm VS Cartographer relaunches;
10. confirm the World Bar reports the target runtime version;
11. confirm the previous-install result does not falsely report failure.

The runtime version shown by the application is the authoritative application
check. Windows `DisplayVersion` is an additional packaging check.

## S5.2 — Post-upgrade state persistence

After the successful upgrade and relaunch, close VS Cartographer and run:

```powershell
powershell -ExecutionPolicy Bypass -File tools\validate-auto-update-stage5.ps1 `
  -Mode AfterUpgrade `
  -SavePath "<path-to-save.vcdbs>"
```

This checks that:

- the Windows installed version matches the target version from the baseline;
- both shortcuts still resolve to the packaged launcher;
- protected Cartographer-owned files are byte-for-byte unchanged;
- persisted `autoCheck` remains unchanged when it existed at baseline;
- the Vintage Story save remains unchanged;
- no new WAL/SHM sidecar appeared.

The helper writes `after-upgrade.json` whether automated checks pass or fail.

Manual state checks remain required:

- HOME still appears correctly for the validation save;
- user markers still exist and render correctly;
- normal map interaction still works;
- the updater no longer offers the already-installed target as a newer update.

## S5.3 — Save-integrity contract

The save-integrity contract is strict:

```text
SHA-256 before == SHA-256 after
length before  == length after
no new .vcdbs-wal
no new .vcdbs-shm
```

Pre-existing WAL/SHM files are recorded but are not deleted by the helper.

Stage 5 does not replace the existing release-validation helper. The older
`tools/validate-windows-release.ps1` remains useful for package structure and
standalone before/after release checks. The Stage 5 helper adds one campaign
baseline spanning upgrade and uninstall.

## S5.4 — Installer, shortcuts and uninstall

After the upgraded target version has passed normal GUI checks, uninstall VS
Cartographer through the normal Windows uninstall UI.

Then run:

```powershell
powershell -ExecutionPolicy Bypass -File tools\validate-auto-update-stage5.ps1 `
  -Mode AfterUninstall `
  -SavePath "<path-to-save.vcdbs>"
```

The automated uninstall gate requires:

- no VS Cartographer uninstall registration remains;
- Desktop shortcut is removed;
- Start Menu shortcut is removed;
- the installed launcher path recorded during the campaign no longer exists;
- the Vintage Story save is still unchanged;
- protected Cartographer-owned HOME/marker state still exists unchanged;
- persisted `autoCheck` remains unchanged when it existed at baseline.

Stage 5 intentionally does not require deleting `~/.vs-cartographer`.
User-owned Cartographer state is expected to survive uninstall.

## S5.5 — Failure scenarios

### Tampered staged installer

After a valid target installer reaches `Ready <target>`, close VS Cartographer
without installing it and run:

```powershell
powershell -ExecutionPolicy Bypass -File tools\validate-auto-update-stage5.ps1 `
  -Mode TamperInstaller `
  -TargetVersion 1.0.1
```

The helper:

- creates a `.stage5-backup` beside the staged installer;
- flips one byte in the canonical staged EXE;
- preserves file length;
- verifies that SHA-256 changed.

Launch the old application and use `Restart & update`.

Expected behavior:

```text
installer is rejected
application does not claim update success
old version remains usable
download/update can be retried
```

Restore the original staged file afterwards:

```powershell
powershell -ExecutionPolicy Bypass -File tools\validate-auto-update-stage5.ps1 `
  -Mode RestoreInstaller `
  -TargetVersion 1.0.1
```

### Installer cancellation / failure

Repeat the update from the old version and cancel the installer when Windows
presents it.

Expected behavior:

- the next launch does not report a successful update;
- the old version remains usable;
- the failure/cancellation path does not modify the Vintage Story save;
- another update attempt remains possible.

The exact native installer exit code can differ by cancellation path, so Stage 5
does not hard-code one cancellation code as the product contract.

### Network unavailable

With no usable network path to GitHub:

- application startup must remain usable;
- automatic update failure must remain non-disruptive;
- manual update check must expose a retry state;
- opening and rendering a local save must remain available.

Stage 5 does not automate network-interface changes.

## Final acceptance matrix

Record the final campaign with explicit evidence:

| Gate | Required evidence | Status before reviewer run |
| --- | --- | --- |
| S5.1 old -> new | real packaged release upgrade + target runtime version | PENDING MANUAL VALIDATION |
| S5.2 state persistence | AfterUpgrade helper + HOME/marker GUI check | PENDING MANUAL VALIDATION |
| S5.3 save integrity | baseline/AfterUpgrade/AfterUninstall hashes and sidecars | PENDING MANUAL VALIDATION |
| S5.4 installer lifecycle | registry + shortcuts + uninstall helper | PENDING MANUAL VALIDATION |
| S5.5 failures | tamper, cancellation/failure, unavailable network | PENDING MANUAL VALIDATION |
| tests | final branch `.\gradlew.bat test` | NOT RUN until CI/reviewer evidence exists |

Do not mark Auto Update Stage 5 or Auto Update v1 DONE from static review alone.

The stage is complete only when:

1. final Stage 5 branch/PR review passes;
2. the final Gradle suite passes on the exact candidate;
3. the real old-to-new Windows upgrade succeeds;
4. state and save-integrity checks pass;
5. installer shortcuts and uninstall checks pass;
6. required failure scenarios behave as documented;
7. the reviewer accepts the evidence.
