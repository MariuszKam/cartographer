# PF-1.8 Final Validation Runbook

This is the reviewer procedure for the final PF-1.8 candidate. Checkpoint I
defines the procedure and closes implementation; it does not execute or
validate any runtime gate. The reviewer must resolve the final Checkpoint I
commit SHA and record it as <FINAL_CHECKPOINT_I_SHA> before running any
campaign. Every generated report, log, cache namespace, JFR summary, and PNG
must contain or be traceable to that exact SHA.

## Candidate and historical identity

The historical cross-SHA ROCK reference is:

~~~text
baseline Git SHA = 3f62bc0a1f76901de92bb585358fe6e02f150b08
~~~

That commit contains the real-save macro baseline runner and the ROCK ladder
ROCK_UPPER_R256, ROCK_UPPER_R512, ROCK_UPPER_R1024, ROCK_UPPER_R2048, and
ROCK_UPPER_R4096. It predates the PF-1.1 merge
fdcd7da7a927fd55efe8a20bce3a1196b9e757b9 and is therefore the historical
cross-SHA ROCK reference candidate.

Do not use 43ccb929d536253c1073e981c90dc13db5c55a47 as the executable macro
baseline; it predates the real-save macro runner.

The comparisons have different meanings:

~~~text
CROSS-SHA ROCK comparison
  baseline SHA = 3f62bc0a1f76901de92bb585358fe6e02f150b08
  candidate SHA = <FINAL_CHECKPOINT_I_SHA>

SAME-SHA MAP configuration comparison
  baseline configuration = <FINAL_CHECKPOINT_I_SHA> + JVM_WARM + cache disabled/source-authoritative
  candidate configuration = <FINAL_CHECKPOINT_I_SHA> + CACHE_WARM
~~~

The MAP comparison measures the PF-1.7 cache configuration effect. It is not
a historical cross-SHA MAP improvement claim.

## Reviewer preconditions

Before each comparable campaign, record that:

- Vintage Story is closed and the source .vcdbs is quiescent/offline;
- a recoverable backup is available;
- the reviewer will not intentionally mutate the source save;
- comparable runs use the same physical save, machine, Java 25 runtime,
  heap/JVM configuration, and comparable OS/environment;
- all cache, report, JFR, and PNG destinations are external to the save
  directory;
- the exact Git SHA is recorded before the campaign;
- the repository/worktree is clean, or the deviation is explained;
- existing evidence directories are not silently reused or overwritten.

If the machine, Java/JVM configuration, OS, save, or other material
environment changes between comparable runs, mark that comparison
INCONCLUSIVE; do not normalize away the difference.

## Phase A — final candidate identity

From the candidate worktree:

~~~powershell
git fetch origin
git branch --show-current
git rev-parse HEAD
git status --short
java --version
~~~

Record the OS, architecture, Java vendor/version, heap configuration, and
the exact output of the identity commands. The branch must be
perf/p1.8-performance-gate-hardening, git status --short must be clean, and
git rev-parse HEAD must equal <FINAL_CHECKPOINT_I_SHA>. Otherwise stop. Do
not substitute a nearby commit.

## Phase B — clean build and full test suite

On the final candidate SHA, retain the complete console/log evidence for:

~~~powershell
.\gradlew.bat clean build
~~~

Compilation failure is FAIL. Any deterministic test failure is FAIL pending
diagnosis and correction. Do not proceed as though the suite passed. A fix
commit changes the candidate SHA and invalidates all earlier candidate
identity and runtime evidence.

## Phase C — real-save source safety

With a real offline save and a new external cache root, run:

~~~powershell
.\gradlew.bat pf18SourceSafety -Psave="<SAVE>" -PcacheRoot="<PF18_SAFETY_CACHE>"
~~~

Record the final candidate SHA alongside this command because the task's
source-safety interface receives the save and cache paths rather than the Git
identity. The PF-1.8 source-safety run is the primary safety gate. The
existing supplementary smoke command may also be run:

~~~powershell
.\gradlew.bat realSaveValidation -Psave="<SAVE>"
~~~

Required PASS evidence includes unchanged source existence and regular-file
status, size, mtime, SHA-256, and no unexpected WAL, SHM, or journal creation,
removal, or modification. It also includes a qualifying PF-1.7 manifest under
the explicit external cache root. Source mutation or prohibited sidecar
creation is an unconditional FAIL. Safety inspection unavailable is
INCONCLUSIVE, never a fabricated pass.

## Phase D — cross-SHA ROCK baseline

Use a detached worktree or equivalent isolated clean checkout for the
historical baseline. Do not modify it:

~~~powershell
git worktree add --detach "<BASELINE_WORKTREE>" 3f62bc0a1f76901de92bb585358fe6e02f150b08
~~~

From that worktree, use its existing perfBaseline task on the same save and
environment:

~~~powershell
.\gradlew.bat perfBaseline -Psave="<SAVE>" -Pworkload="ROCK_UPPER_R256" -PgitSha="3f62bc0a1f76901de92bb585358fe6e02f150b08"
~~~

Repeat for ROCK_UPPER_R512, ROCK_UPPER_R1024, ROCK_UPPER_R2048, and
ROCK_UPPER_R4096, retaining every report or failure. Then run the candidate
worktree's same perfBaseline command for each workload, changing only
-PgitSha to <FINAL_CHECKPOINT_I_SHA> and using a separate external output
location if supported by the reviewer setup.

R128 has no equivalent historical baseline in this runner and is
NOT APPLICABLE TO CROSS-SHA BASELINE, not FAIL. A baseline OOM or failure is
retained as historical scalability evidence; it is not omitted or replaced
with a number.

For each comparable successful pair, report baseline SHA, candidate SHA, save
fingerprint, environment, workload, correctness fingerprint, and baseline and
candidate min/p50/p95/max. A factual delta or ratio may be shown without an
invented threshold. Fingerprint mismatch is FAIL. Different save or
environment is INCONCLUSIVE.

## Phase E — final candidate scaling campaign

Use dedicated, newly created external cache and output roots. Do not reuse
uncontrolled user state. Run pf18Macro with the final candidate SHA for:

~~~text
MAP_R128       MAP_R256       MAP_R512
MAP_R1024      MAP_R2048      MAP_R4096

ROCK_UPPER_R128   ROCK_UPPER_R256   ROCK_UPPER_R512
ROCK_UPPER_R1024  ROCK_UPPER_R2048  ROCK_UPPER_R4096
~~~

Example:

~~~powershell
.\gradlew.bat pf18Macro -Psave="<SAVE>" -PcacheRoot="<PF18_EXTERNAL_CACHE_ROOT>" -PgitSha="<FINAL_CHECKPOINT_I_SHA>" -Pworkload="MAP_R1024" -Pmode="JVM_WARM" -Poutput="<PF18_EXTERNAL_OUTPUT_ROOT>"
~~~

R1024 is the mandatory serious reference point. R2048 is the primary
scalability target. R4096 is a stretch observation. An R4096 failure or OOM
must be retained honestly and not silently omitted. R128/R256 alone are not
enough for final performance conclusions.

Every accepted macro report must itself be FACTUAL. An INVALID report cannot
be promoted by this runbook. Fingerprint mismatch is FAIL; safety failure is
FAIL; missing/incomplete samples are INCONCLUSIVE or FAIL according to the
factual cause.

## Phase F — execution-state evidence

At R1024, run these final-SHA configurations:

~~~text
MAP_R1024:          PROCESS_COLD, JVM_WARM, CACHE_WARM
ROCK_UPPER_R1024:   PROCESS_COLD, JVM_WARM
~~~

Do not request CACHE_WARM for ROCK. Compare MAP CACHE_WARM with MAP JVM_WARM
as a same-SHA configuration comparison:

~~~text
baseline SHA = <FINAL_CHECKPOINT_I_SHA>
candidate SHA = <FINAL_CHECKPOINT_I_SHA>
~~~

This is not a cross-SHA improvement claim. PROCESS_COLD must be fresh JVM
process evidence; JVM_WARM is in-process evidence; CACHE_WARM excludes cache
population from measured timing and proves HIT facts for every measured
iteration.

## Phase G — cache-warm scaling

For PF-1.7 cache evidence, run and retain MAP CACHE_WARM reports for
MAP_R1024 and MAP_R2048, and attempt MAP_R4096 as stretch evidence. Every
measured CACHE_WARM iteration must prove cache HIT and retain source-work
facts. Timing alone is not cache-avoidance evidence.

## Phase H — resource evidence

For accepted macro campaigns, review process CPU where available, aggregate
per-heap-pool peak-used evidence, and GC collection count/time. Whole-process
allocation remains UNAVAILABLE when the low-overhead sampler cannot collect
it reliably. RSS remains UNAVAILABLE when it is not practical to collect.
Unavailable values must not be converted to zero. RSS unavailability alone
does not reject a campaign when the contract says where practical. Use the
separate JFR run for allocation-pressure evidence.

## Phase I — JFR diagnostic evidence

Run pf18Jfr separately for MAP_R1024 and ROCK_UPPER_R1024, with separate
external JFR cache/output roots:

~~~powershell
.\gradlew.bat pf18Jfr -Psave="<SAVE>" -PcacheRoot="<JFR_EXTERNAL_CACHE_ROOT>" -PgitSha="<FINAL_CHECKPOINT_I_SHA>" -Pworkload="MAP_R1024" -Poutput="<JFR_MAP_OUTPUT>"
.\gradlew.bat pf18Jfr -Psave="<SAVE>" -PcacheRoot="<JFR_EXTERNAL_CACHE_ROOT_ROCK>" -PgitSha="<FINAL_CHECKPOINT_I_SHA>" -Pworkload="ROCK_UPPER_R1024" -Poutput="<JFR_ROCK_OUTPUT>"
~~~

MAP must report CACHE_WARM; JVM warm after external cache preparation.
ROCK must report SOURCE_AUTHORITATIVE; JVM_WARM diagnostic profile.

JFR is diagnostic evidence only and never replaces normal macro timing. Review
CPU hot frames, allocation sampling/weight availability, GC/pause evidence,
monitor/thread-park evidence, file-I/O evidence, conservative SQLite
attribution wording, recording size/configuration, and exact SHA/workload/
fingerprint/safety identity. Missing event types remain UNAVAILABLE or NOT
DETERMINED; they are not fabricated zero activity.

## Phase J — manual PNG inspection

Semantic and logical-ARGB fingerprints remain the automated correctness gate,
but AGENTS.md also requires visual inspection for rendering milestones. Using
the existing production map render path, generate source/MISS/population and
compatible HIT renders at R1024 with style topographic, scale 1,
layers terrain,surface, no ore overlay, and the same save/player-derived
center as the macro campaign.

Use an isolated temporary .vs-cartographer home/cache namespace and restore the
reviewer's prior environment afterward. A reviewer may use a temporary
process-scoped JAVA_TOOL_OPTIONS=-Duser.home=<PF18_TEMP_HOME>. Store PNGs
outside the save directory and inspect both images manually. Also inspect CLI
cache diagnostics. Do not use compressed PNG bytes as the semantic correctness
gate. Material visual difference is FAIL; no manual inspection is PENDING
MANUAL VALIDATION.

## Phase K — evidence matrix

Fill every row independently. Use only these literal statuses:
PASS, FAIL, INCONCLUSIVE, NOT RUN, and PENDING MANUAL VALIDATION. Each row
must name the concrete report, log, recording, summary, or PNG that supports
it; a related row passing does not make another row pass.

| Gate | Status | Supporting artifact/log/report | Notes |
|---|---|---|---|
| Exact candidate identity | NOT RUN |  |  |
| Clean build | NOT RUN |  |  |
| Full test suite | NOT RUN |  |  |
| Source/cache semantic parity | NOT RUN |  |  |
| Image/logical-ARGB parity | NOT RUN |  |  |
| MISS | NOT RUN |  |  |
| HIT | NOT RUN |  |  |
| Mixed HIT/MISS | NOT RUN |  |  |
| Corrupt-row recovery | NOT RUN |  |  |
| Deterministic healing | NOT RUN |  |  |
| Revision invalidation | NOT RUN |  |  |
| Malformed-manifest fail-closed | NOT RUN |  |  |
| Cache-disabled fallback | NOT RUN |  |  |
| SaveSession lifecycle | NOT RUN |  |  |
| Terrain source-read avoidance | NOT RUN |  |  |
| Surface decode avoidance | NOT RUN |  |  |
| Source save safety | NOT RUN |  |  |
| Cross-SHA ROCK baseline comparability | NOT RUN |  |  |
| ROCK R1024 comparison | NOT RUN |  |  |
| Candidate R1024 serious macro evidence | NOT RUN |  |  |
| R2048 scaling evidence | NOT RUN |  |  |
| R4096 stretch observation | NOT RUN |  |  |
| PROCESS_COLD distinction | NOT RUN |  |  |
| JVM_WARM distinction | NOT RUN |  |  |
| CACHE_WARM distinction | NOT RUN |  |  |
| Resource evidence | NOT RUN |  |  |
| MAP JFR | NOT RUN |  |  |
| ROCK JFR | NOT RUN |  |  |
| Manual PNG inspection | PENDING MANUAL VALIDATION |  |  |
| Unresolved limitations | NOT RUN |  |  |

## Final verdict rules

- Fingerprint mismatch is an unconditional FAIL.
- Source-save mutation or prohibited sidecar creation is an unconditional FAIL.
- Invalid benchmark methodology is FAIL or INCONCLUSIVE, never PASS.
- Missing required evidence is NOT RUN, PENDING MANUAL VALIDATION, or
  INCONCLUSIVE as factually appropriate.
- A missing or non-comparable historical baseline makes that comparison
  INCONCLUSIVE.
- R4096 stretch failure is recorded and is not converted by an invented
  threshold into an overall verdict.
- No arbitrary runtime, speedup, or memory threshold may be invented here.
- A large improvement never overrides correctness; a slow result never weakens
  a gate.

PF-1.8 can be accepted only after the reviewer has accepted all mandatory
correctness and safety gates and inspected the complete applicable evidence.
Codex does not perform that acceptance. PF-1.6 and PF-1.7 may move from
IMPLEMENTED — VALIDATION PENDING to VALIDATED only after that reviewer
acceptance of the complete PF-1.8 runtime evidence.
