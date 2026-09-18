# PF-1.8 Performance Gate / Hardening

Status: **IN PROGRESS — CHECKPOINT A CONTRACT DEFINED; VALIDATION NOT RUN**.

PF-1.8 is the validation and hardening campaign for PF-1.6 SaveSession /
SaveSnapshot and PF-1.7 Render Data Cache. This document is normative for the
entire milestone. It defines evidence and acceptance gates; it does not claim
that any gate has passed.

## 1. Purpose and scope

PF-1.8 will establish whether the PF-1.6 and PF-1.7 implementations are
correct, safe on real saves, operationally bounded, and useful under measured
workloads. It covers:

* automated correctness and lifecycle validation;
* semantic and image parity between uncached/source and cached paths;
* source-save immutability and SQLite sidecar safety;
* SaveSession and source-connection lifecycle evidence;
* cache miss, hit, mixed, corruption, healing, invalidation, and fail-closed
  behavior;
* avoidance of source mapchunk reads on terrain hits and server-chunk decode
  work on Surface hits;
* process/JVM/cache warm-state benchmark methodology;
* bounded-memory, allocation, GC, JFR, and practical resident-memory evidence;
* real-save macro evidence separate from JMH microbenchmark evidence.

PF-1.8 does not authorize weakening parser, missing-data, diagnostic,
coordinate, or save-safety semantics. It does not invent a performance target
where the repository has not established one.

PF-1.6 and PF-1.7 remain **IMPLEMENTED — VALIDATION PENDING** until the
required PF-1.8 evidence has been reviewed. PF-1.8 itself may not be declared
accepted by a unit-test run, static review, successful commit, or plausible
benchmark alone.

## 2. Evidence vocabulary

The following terms are distinct and must be used literally in reports:

* **IMPLEMENTED** — the requested code or documentation exists in the named
  commit. This is an implementation statement, not runtime evidence.
* **STATICALLY ACCEPTED** — an independent review found the branch lineage,
  scope, architecture, and static contract compliance acceptable. It does not
  prove execution, performance, save safety, or output parity.
* **VALIDATED** — the applicable runtime evidence was actually executed,
  inspected, and accepted by the responsible reviewer for the named gate.
* **PASS** — the named gate ran and all of its acceptance criteria passed.
* **FAIL** — the named gate ran and a required criterion failed, including a
  correctness mismatch, save modification, invalid methodology, or serious
  safety/performance regression.
* **INCONCLUSIVE** — evidence exists but cannot support a conclusion, for
  example because the environment changed, the input was not immutable, the
  sample is insufficient, or profiling/measurement was invalid.
* **NOT RUN** — the measurement or check was not executed.
* **PENDING MANUAL VALIDATION** — execution and inspection remain assigned to
  the reviewer/user environment; no pass is implied.

Static acceptance and implementation status must never be reported as
runtime `PASS` or `VALIDATED`. A skipped gate remains `NOT RUN` or `PENDING
MANUAL VALIDATION`, as appropriate.

## 3. Non-negotiable correctness and safety rules

### 3.1 Correctness before performance

Correctness is evaluated before any timing or scalability conclusion. A
candidate with a semantic mismatch, image mismatch where image parity is
required, changed error/missing-data behavior, coordinate-space error,
incorrect diagnostic result, lifecycle violation, or save-integrity failure is
an unconditional `FAIL`, regardless of elapsed time, throughput, allocation,
or memory result.

No gate may be weakened after observing a bad result. A slow, failing, noisy,
or inconvenient result must be recorded and diagnosed; thresholds, workloads,
fingerprint rules, sample exclusions, or safety checks must not be changed
retroactively to obtain `PASS`.

### 3.2 Semantic and image fingerprint parity

Every comparable source/baseline and candidate run uses the same immutable
workload and records both inputs and output identity. Semantic fingerprints
must normalize deterministic ordering and serialize semantic values including
coordinate-space meaning, missing/error state, diagnostics, and relevant
options before hashing. A baseline fingerprint different from the candidate
fingerprint is `FAIL`.

For rendered output, compare a normalized deterministic pixel representation
(for example ARGB pixels with dimensions and color semantics), not PNG bytes
alone. PNG output must also be manually inspected when rendering is in scope.
Encoder metadata or compression differences must not create a false mismatch.
An intentional semantic change requires separate review and a newly declared
baseline; it is not an optimization success.

### 3.3 Immutable inputs and source-save safety

The save, workload definition, configuration, and comparison environment must
remain unchanged for the baseline/candidate comparison. The save must be
offline and quiescent: Vintage Story and other writers must not be modifying
the `.vcdbs` during validation. If the input changes, old and new timings are
not comparable and the comparison is `INCONCLUSIVE` until a new baseline and
candidate use the same immutable input.

The source `.vcdbs` is strictly read-only. PF-1.8 validation must:

1. record the source path, normalized identity, byte size, last-modified time,
   and SHA-256 checksum before the operation;
2. record the same values after the operation;
3. verify checksum, size, and modification time are unchanged;
4. inspect the directory for `.vcdbs-wal`, `.vcdbs-shm`, and journal files;
5. fail if analysis creates a new source-save WAL, SHM, or journal sidecar;
6. distinguish pre-existing sidecars from newly created or modified ones;
7. confirm all writable cache artifacts remain beneath the explicitly injected
   cache root and never beside or inside the source-save database; and
8. record any inability to inspect or compare these facts as `INCONCLUSIVE`,
   never as `PASS`.

The source connection must use the PF-1.6 read-only/immutable lifecycle and
must not be replaced by a writable SQLite connection. No analysis, cache
preparation, manifest publication, healing, or benchmark setup may write to
the game save. Decoded chunks are transient; compact bounded result state and
bounded working state are permitted. The memory model must remain bounded by
result state plus explicitly bounded working state. Core backend processing
must not use the common ForkJoinPool.

### 3.4 Source identity and revision validation

The PF-1.7 revision inputs must be recorded and checked: normalized save
identity, source byte size, source last-modified time in milliseconds, render
data schema version, and parser/data compatibility version. A source size or
mtime change must select a different revision and must not reuse the old
artifacts. A checksum is required for safety evidence even though normal
PF-1.7 revision identity is not a content hash.

## 4. PF-1.6 runtime gates

PF-1.8 must validate the runtime SaveSession/source-connection contract, not
merely infer it from source structure:

* one operation-scoped source `SaveSession` is opened for the applicable main
  render or related-read operation;
* metadata and registry are obtained from the immutable `SaveSnapshot`;
* session-aware readers borrow the source connection and do not close it;
* statements and result sets remain locally owned by their readers;
* the session owns and closes its connection exactly at operation end;
* closed-session and wrong-save identity protections remain effective; and
* no connection, session, result set, statement, decoded chunk, or save state
  escapes into persistent cache state or a later operation.

The evidence must distinguish static one-session design from runtime lifecycle
observation. A static assertion that the architecture appears correct is not
runtime connection-count or close-order evidence.

## 5. PF-1.7 cache scenario gates

Each scenario must compare the result and relevant diagnostics against the
authoritative uncached/source path using the fingerprint rules above. The
cache remains optional: cache I/O failure must fall back to source analysis
without changing source-save safety or semantic error behavior.

Required scenarios are:

* **MISS** — absent or unpopulated compatible artifacts use the source path
  and may publish valid bounded artifacts.
* **HIT** — valid compatible artifacts produce parity and avoid the applicable
  source work.
* **mixed HIT/MISS** — terrain and Surface identities, and individual
  coordinates, can independently hit or miss without cross-contamination.
* **corrupt-row recovery** — malformed or unreadable terrain/Surface rows are
  treated as MISS/CORRUPT, source analysis remains authoritative, and the
  valid source result can republish the row without touching the save.
* **deterministic healing** — a repaired row has deterministic bytes/content,
  is accepted on the next compatible lookup, and produces the same semantic
  and image fingerprints.
* **revision invalidation** — source size and mtime changes do not reuse the
  prior revision's artifacts; parser/data or cache-schema changes likewise
  select incompatible revisions.
* **malformed-manifest fail-closed** — missing, malformed, incomplete,
  incompatible, or newer manifest metadata is not trusted; cache artifacts
  under that revision are not used.
* **cache-disabled fallback** — manifest/cache preparation failure or an
  explicitly disabled cache leaves the authoritative source path operational
  and produces parity.

The report must distinguish requested, HIT, MISS, CORRUPT, source-loaded, and
published counts. A cache hit does not mean the source `.vcdbs` is never
opened: dynamic metadata and other requested data may still use the one
operation-scoped session.

### 5.1 Avoidance gates

For terrain, a valid terrain HIT must omit the corresponding coordinate from
the source mapchunk request. Runtime evidence must show that source-mapchunk
loading was avoided for those hit coordinates; a timing improvement without
this evidence is insufficient.

For Surface, a valid Surface HIT must omit the corresponding mapchunk from
Surface source planning and avoid decoding its server chunks. Runtime evidence
must show the avoided server-chunk decode work. A terrain HIT does not imply a
Surface HIT, and a Surface HIT does not imply a terrain HIT.

Boundary-clipped request state must not be persisted as a reusable full tile.
Cached cells must enter the same accumulator and diagnostics semantics as
source cells. Missing data and parser failures must remain explicit and must
not be converted to fabricated values.

## 6. Execution-state definitions

Every macro report labels each run as exactly one of these states:

* **PROCESS_COLD** — a newly launched JVM process for the operation. It may
  include process startup and class loading when those are inside the declared
  timing boundary. It is not cold-disk evidence; OS filesystem cache state is
  recorded as uncontrolled unless explicitly controlled.
* **JVM_WARM** — an already-running JVM after declared, unmeasured warmups and
  with class loading/JIT settling handled by the stated preparation procedure.
  It does not imply a populated render-data cache.
* **CACHE_WARM** — a run after the named compatible cache manifest and
  artifacts have been populated by a separately recorded preparation step.
  The cache identity, revision, population operation, and whether the JVM is
  also warm must be stated.

Preparation work is outside measured iterations unless the workload contract
explicitly defines it inside the boundary. Warmups are not evidence. A fresh
JVM must not be described as a cold-disk run, and cache population must not be
quietly included in a cache-warm timing.

## 7. Benchmark methodology and evidence

Real-save macro benchmarks must use the actual Cartographer reader/parser/
scanner/renderer path over a named representative `.vcdbs`. Each report must
include:

* exact baseline and candidate Git SHAs and their parent/lineage;
* workload ID, operation, radius, bounds, options, render layers, and output
  definition;
* immutable source identity plus pre/post checksum, size, mtime, and sidecar
  result;
* Java version/vendor, OS, available processors, configured heap limit, and
  relevant Cartographer configuration;
* execution state (`PROCESS_COLD`, `JVM_WARM`, or `CACHE_WARM`), preparation
  boundaries, warmup count, measured-iteration count, and cache revision;
* p50/median, p95, minimum, and maximum wall-clock time with units;
* the completed work unit and throughput definition;
* semantic and image fingerprints and correctness verdict; and
* exclusions, failures, OOMs, invalid iterations, and environmental changes,
  with reasons. Inconvenient observations may not be silently discarded.

Where available, report CPU time separately from wall-clock time. Include
peak heap, allocated bytes, GC counts/time or equivalent behavior, and peak
resident memory/RSS when practical. Heap and allocation evidence must state
how and where the measurements were obtained; RSS evidence must identify the
process and platform limitations. These observations supplement correctness;
they do not replace it.

JFR profiling evidence is separate from normal timing evidence. A JFR run
must identify its recording configuration and overhead considerations and
report CPU samples, allocation pressure, GC, thread/lock behavior, and useful
SQLite/file-I/O observations. JFR timings must not be substituted for normal
benchmark timings, and normal benchmark timings must not be presented as JFR
profiling evidence.

JMH is microbenchmark evidence only. It may explain isolated decoder,
parser, classification, or lookup costs, but it does not establish end-to-end
real-save behavior. JUnit wall-clock assertions are not benchmark evidence.
No invented numeric performance claim or arbitrary threshold is permitted in
Checkpoint A or later checkpoints unless a separately reviewed contract
establishes it. Existing results must be reported honestly, including a
failure or OOM. A performance result without correctness and save-safety
evidence is not a gate pass.

## 8. Radius ladder and scaling roles

The standard radius vocabulary is `R128`, `R256`, `R512`, `R1024`, `R2048`,
and `R4096`. A profile identifier does not guarantee completion; failure or
OOM is valid scalability evidence and must be reported.

* **R128** — small smoke/sanity workload for fast correctness and setup
  checks; insufficient for serious performance conclusions.
* **R256** — development and sanity workload; useful for rapid iteration but
  insufficient by itself for serious acceptance.
* **R512** — medium workload for intermediate scaling evidence.
* **R1024** — mandatory canonical serious evidence whenever the workload
  supports it; the required reference workload for comparable before/after
  evidence.
* **R2048** — primary scalability target; evidence should show how the system
  behaves as input grows, without claiming a threshold that has not been
  established.
* **R4096** — stretch workload for headroom and scalability limits. It is not
  silently required when the workload cannot support it, and its failure is
  reported rather than hidden.

The approximate two-dimensional input growth is proportional to `πr²`, so
reports must include workload dimensions and completed units rather than
comparing elapsed times without context. R1024 is mandatory canonical serious
evidence where supported; R2048 is the scalability target and R4096 the
stretch observation, not invented pass/fail timing thresholds.

## 9. Checkpoint sequence A–I

The PF-1.8 milestone uses exactly one shared branch,
`perf/p1.8-performance-gate-hardening`, for all checkpoints. No checkpoint,
fix, temporary, or follow-up branch may be created for this milestone.

The planned sequence is:

* **A — contract**: define this normative gate, evidence vocabulary, safety
  rules, workload roles, and review cadence. Documentation only.
* **B — validation harness**: add only the narrowly required instrumentation
  and deterministic test seams for PF-1.6/PF-1.7 runtime gates, preserving
  bounded state and read-only source access.
* **C — correctness parity**: exercise source/cache MISS, HIT, mixed, image
  and semantic fingerprints, diagnostics, and coordinate/error semantics.
* **D — cache hardening**: validate corrupt-row recovery, deterministic
  healing, revision invalidation, malformed-manifest fail-closed behavior,
  and cache-disabled fallback.
* **E — lifecycle and avoidance**: validate SaveSession/source-connection
  ownership and runtime terrain mapchunk/server-chunk avoidance gates.
* **F — source safety**: run real-save checksum/size/mtime and WAL/SHM/journal
  safety validation on a quiescent save.
* **G — macro performance evidence**: run the declared process/JVM/cache-warm
  methodology across supported radius profiles, with correctness first.
* **H — resource and profiling evidence**: collect heap, allocation, GC,
  practical RSS, and separately recorded JFR evidence; keep JMH microbenchmarks
  distinct from macro results.
* **I — final gate review**: inspect the complete evidence package, unresolved
  limitations, lineage, and scope. The reviewer/user decides acceptance; Codex
  must not self-declare it.

Each checkpoint is committed and pushed to the same branch, then independently
reviewed. Expensive integrated runtime validation normally occurs once the
final integrated stage head exists, unless a documented narrow early-runtime
reason is required. A failed gate receives the smallest reasonable fix as a
new commit; the failing evidence is preserved and the gate is rerun without
weakening it.

Checkpoint B is implemented as an operation-scoped SaveSession lifecycle probe
with deterministic exactly-once close observation and focused structural tests.
It records facts only; it does not issue a PF-1.6 verdict. Tests and runtime
validation are **NOT RUN** by Codex. PF-1.6 and PF-1.7 remain **PENDING MANUAL
VALIDATION**.

Checkpoint C is implemented with the existing logical ARGB fingerprinter and
canonical semantic fingerprint infrastructure. Source, MISS, HIT, and mixed
path parity gates cover stable render geometry and Surface cell semantics;
cache work counters remain operational evidence rather than fingerprint input.
Tests and runtime validation are **NOT RUN** by Codex. PF-1.6 and PF-1.7 remain
**PENDING MANUAL VALIDATION**.

Checkpoint D is implemented with deterministic integrated hardening gates for
terrain and Surface corrupt-row recovery, byte-level terrain healing,
size/mtime revision invalidation, representative malformed/incompatible
manifest fail-closed cases, cache-preparation failure fallback, and source
cache-root containment. Automated tests, real-save validation, and performance
measurement are **NOT RUN** by Codex. PF-1.6 and PF-1.7 remain **PENDING
MANUAL VALIDATION**.

The D static-review gaps were corrected in a follow-up implementation commit:
Surface healing now checks deterministic persisted payload bytes and semantic /
image parity; incompatible manifests are complete documents whose version is
the only incompatibility; cache-preparation fallback checks source parity; and
successful cache scenarios check normalized cache-root containment. Tests remain
**NOT RUN** and PF-1.6/PF-1.7 remain **PENDING MANUAL VALIDATION**.

D-FIX2 strengthens the cache containment evidence by asserting the exact
manifest, terrain SQLite, and Surface SQLite paths produced by PF-1.7 are
regular files under the normalized injected cache root, outside the source
directory, and absent beside the source save. Temporary manifest publication
files are also checked for cleanup. This is implementation evidence only;
tests remain **NOT RUN**.

E-FIX adds a real temporary SQLite fixture test for the actual session-aware
`VcdbsReader` mapchunk path. It performs two reads through one `SaveSession`,
checks the underlying read-only connection remains open between reads, and
checks the owning session closes it exactly at operation end. Tests remain
**NOT RUN** by Codex.

Checkpoint E is implemented with production-render lifecycle tests using the
operation-scoped SaveSession probe, isolated-operation/session-protection
tests, and explicit terrain/Surface HIT and mixed HIT/MISS source-work
avoidance assertions. Runtime tests are **NOT RUN** by Codex. PF-1.6 and
PF-1.7 remain **PENDING MANUAL VALIDATION**.

Checkpoint F is implemented as an opt-in source-safety runner composed with
the existing before/after `SaveSafetySnapshotter` and `SaveSafetyGate`. It
executes a small production main-render workload with an explicit normalized
cache root outside the source save directory and reports factual safety,
operation, artifact, and failure state. Reviewer command syntax is:

```powershell
.\gradlew.bat pf18SourceSafety `
  -Psave="C:\path\world.vcdbs" `
  -PcacheRoot="C:\path\pf18-cache"
```

The existing `realSaveValidation` narrow SQLite smoke command remains
unchanged. Real-save safety validation, tests, builds, and benchmarks are
**NOT RUN** by Codex. PF-1.6, PF-1.7, and PF-1.8 remain **PENDING MANUAL
VALIDATION**.

F-FIX hardens the source-safety PASS contract: protected source state must be
unchanged and a valid PF-1.7 manifest for the executed save revision must be
present under the explicit external cache root. Terrain and Surface cache
presence are reported separately; arbitrary files under the cache root do not
qualify. Missing or escaped qualifying cache evidence is `INCONCLUSIVE`, while
source or sidecar mutation remains a safety `FAIL`. Tests and real-save
validation remain **NOT RUN**.

D-FIX3 isolates the successful Surface cache-containment fixture: the source
directory and injected cache root are sibling paths, and the test asserts those
containment assumptions before checking the exact PF-1.7 manifest, terrain, and
Surface artifacts. Existing deterministic healing, parity, incompatible
manifest, and fallback coverage is retained. Tests remain **NOT RUN**.

Checkpoint G is implemented as an opt-in macro campaign runner on top of the
existing benchmark and fingerprint foundations. It supports `MAP_R128` through
`MAP_R4096` and `ROCK_UPPER_R128` through `ROCK_UPPER_R4096`, performs an
unmeasured correctness preflight, records semantic and logical-ARGB image
fingerprints separately, supports `JVM_WARM`, genuine fresh-process
`PROCESS_COLD`, and cache-prepared `CACHE_WARM`, and captures immutable source
safety around the campaign. Campaign evidence is isolated and is never
overwritten. The reviewer command is:

```powershell
.\gradlew.bat pf18Macro `
  -Psave="C:\path\world.vcdbs" `
  -PcacheRoot="C:\path\pf18-cache" `
  -PgitSha="<40-char-sha>" `
  -Pworkload="MAP_R1024" `
  -Pmode="JVM_WARM" `
  -Poutput="C:\path\pf18-evidence"
```

The command does not invent thresholds or throughput claims. Runtime macro
evidence, tests, and real-save validation are **NOT RUN** by Codex. PF-1.6,
PF-1.7, and PF-1.8 remain **PENDING MANUAL VALIDATION**.

## 10. Final evidence required for PF-1.6/PF-1.7 validation

Before either PF-1.6 or PF-1.7 can be marked `VALIDATED`, the final PF-1.8
evidence package must contain, for the applicable scope:

1. successful compilation and the full automated test-suite result;
2. source/cache semantic fingerprints and required image/pixel parity;
3. MISS, HIT, mixed HIT/MISS, corrupt-row recovery, deterministic healing,
   revision invalidation, malformed-manifest fail-closed, and cache-disabled
   fallback results;
4. runtime one-session/source-connection lifecycle evidence;
5. terrain HIT source-mapchunk avoidance and Surface HIT server-chunk-decode
   avoidance evidence;
6. source checksum, size, mtime, and WAL/SHM/journal safety evidence showing
   the source save was not modified;
7. supported process-cold, JVM-warm, and cache-warm macro reports, including
   R1024 serious evidence where supported and R2048/R4096 observations under
   their stated roles;
8. bounded-memory evidence covering heap, allocations, GC, and RSS when
   practical, plus separately identified JFR profiling evidence; and
9. reviewer inspection of the complete reports, generated outputs, and any
   unresolved `INCONCLUSIVE`, `NOT RUN`, or `PENDING MANUAL VALIDATION` gate.

No missing item may be inferred from a related test or static inspection. A
required item that is not executed remains `NOT RUN` or `PENDING MANUAL
VALIDATION`; an invalid or non-comparable item is `INCONCLUSIVE`. PF-1.8
cannot be declared `PASS` solely from unit tests or static review.

Codex may implement the scoped changes and report factual evidence it actually
observed. Codex may not self-declare PF-1.8, PF-1.7, or PF-1.6 accepted,
validated, or done. The independent controller review and user/product
acceptance remain separate from implementation status.

## 11. Checkpoint A boundaries and review cadence

Checkpoint A changes documentation only. It does not modify production Java,
test Java, Gradle, GitHub Actions, application behavior, cache behavior,
benchmark runners, JFR analysis, or any save-access code. It does not run
real-save validation, performance benchmarks, the application, builds, or
tests.

The PF-1.8 branch is created directly from the verified remote `master` base
and is reused for every checkpoint. Each checkpoint change is narrow,
committed with an explicit message, pushed to the shared branch, and then
reviewed against its exact remote commit and parent. The shared branch is not
rebased, force-pushed, or amended after a push. Checkpoint A stops after its
commit and push; later checkpoints require a new task instruction.

### Checkpoint A evidence state

* Contract document: **IMPLEMENTED** in the Checkpoint A commit.
* PF-1.8 runtime gates: **NOT RUN**.
* PF-1.6/PF-1.7 runtime validation: **PENDING MANUAL VALIDATION**.
* Performance conclusions: **NOT RUN**; no performance claim is made.
