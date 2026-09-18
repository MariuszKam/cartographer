# VS Cartographer Performance Foundation

Status: PF-1.0.0 contract; runtime evidence and milestone acceptance are not established by this document.

## 1. Purpose

VS Cartographer already has a working MVP/backend for offline analysis of Vintage Story `.vcdbs` saves. The Performance Foundation establishes a scalable backend architecture for large-save analysis. It defines how performance work is measured, compared, instrumented, and gated while preserving analytical correctness and `.vcdbs` safety.

Performance is part of the architecture, not a cosmetic optimization phase.

This contract is normative for future performance work. A change is valuable only when its measurable behavior improves against a stated workload and its analytical result, diagnostics, coordinate behavior, and save safety remain valid. Missing data and parser failures retain their existing semantics.

## 2. Terminology

- **Workload**: A named, reproducible input and operation definition: save identity, spatial scope, analysis options, render options, expected output shape, and execution conditions.
- **Benchmark iteration**: One complete timed execution of the operation under the workload, from the defined start boundary to the defined end boundary.
- **Warmup**: An unmeasured execution used to reach the intended JVM state before measured iterations. A warmup is not evidence of performance.
- **Process-cold run**: A run started in a newly launched JVM process. It includes JVM startup and class loading when those are inside the measurement boundary. It does not imply cold disk or cold OS caches.
- **JVM-warm run**: A run in an already-running JVM after the specified warmups, with JIT compilation and class loading allowed to settle.
- **Cache-warm run**: A run performed after the specified application/data cache has been populated. The cache identity and preparation procedure must be recorded.
- **Baseline**: The explicitly identified reference implementation and measurement set, including its Git commit and correctness fingerprint.
- **Candidate**: The implementation or configuration being compared with the baseline.
- **Result fingerprint**: A deterministic hash of normalized semantic output used to establish that baseline and candidate mean the same thing.
- **Peak heap**: The maximum JVM heap usage observed during the measured operation, in bytes (reported also in MiB where useful; 1 MiB = 1,048,576 bytes).
- **Allocated bytes**: The total bytes allocated by the JVM during the operation, including objects that are later collected; report bytes, not merely live heap.
- **Throughput**: Completed workload units per second, such as chunks/second or columns/second. The unit and completion definition are mandatory.
- **Wall-clock time**: Elapsed real time between measurement boundaries, in nanoseconds or milliseconds; it includes waiting and I/O.
- **CPU time**: Processor time consumed by the relevant thread(s), in nanoseconds or milliseconds; it excludes time waiting for I/O or locks.
- **p50 / median**: The 50th percentile of measured iterations: the value at or below which 50% of observations fall.
- **p95**: The 95th percentile: the value at or below which 95% of observations fall.
- **Working set**: In-flight decoded data and temporary state required to produce the result, bounded by an explicit policy. For process-level observations, OS working set means resident process memory and must be identified as such.
- **Bounded memory**: Memory whose maximum is constrained by result size plus declared limits for queues, workers, buffers, and other working state, independent of unbounded input length.
- **Hot path**: A frequently executed path whose CPU, allocation, synchronization, or I/O cost materially affects a workload; examples include per-block and per-column loops.
- **Query fusion**: Sharing one read/decode traversal among multiple compatible analyses instead of repeating the traversal for each analysis.
- **Backpressure**: A mechanism that prevents producers from submitting more work or data than bounded downstream capacity can accept.

“Cold” without a qualifier is insufficient. A fresh JVM process is process-cold, not a cold-disk benchmark. OS filesystem cache state is separate and must not be claimed unless it is explicitly controlled and recorded.

## 3. Performance Constitution

The following rules are hard requirements:

1. A performance claim requires measurements from a named workload and a stated methodology.
2. A benchmark result without correctness verification is insufficient evidence.
3. A faster result with a different semantic/result fingerprint is `FAIL`.
4. Decoded server chunks are transient working data unless retaining them is explicitly justified by the design.
5. Heavy operations may retain compact results, but must not accumulate decoded input chunks without a bounded-memory design.
6. Memory usage must be bounded by the output/result representation plus an explicitly bounded working set.
7. Multiple analyses over the same chunk range should be fused when their semantics allow it.
8. Hot loops must avoid unnecessary per-block and per-column object allocation.
9. String normalization and classification belong outside block-level hot paths whenever possible.
10. Parallel work requires explicit bounds and backpressure.
11. The JVM common `ForkJoinPool` must not be used for core backend processing without an explicit architectural decision.
12. Concurrency must not be introduced merely because a workload is slow.
13. Prefer algorithmic and data-layout improvements before GC tuning, exotic JVM flags, off-heap designs, SIMD, JNI, GPU compute, or similar techniques.
14. Do not optimize away error handling, missing-data semantics, diagnostics, coordinate correctness, or safety checks.
15. `.vcdbs` files remain read-only. Analysis must not modify the save.
16. Any performance change touching save access requires save-integrity validation.
17. New `.vcdbs-wal` or `.vcdbs-shm` files during intended read-only analysis are suspicious and require investigation.
18. No performance stage is `DONE` solely because unit tests pass.
19. No performance stage is `DONE` solely because static review passes.
20. No benchmark produced by Codex may be reported as reviewer-validated evidence unless the reviewer actually ran and inspected it.

## 4. Complexity Contracts

For an analysis over `C` decoded chunks and `O` output cells, the preferred memory model is:

```text
M = O(O + boundedWorkingSet)
```

Avoid a model that retains all decoded input:

```text
M = O(C * decodedChunkSize)
```

For `R` compatible resource analyses over the same chunk set `C`, prefer fused work approximately proportional to:

```text
O(C + matches)
```

instead of repeated scans:

```text
O(R * C)
```

when semantics permit fusion. Fusion must not change ordering, error reporting, missing-data behavior, or result meaning.

For radius-based two-dimensional analysis, the approximate number of world columns is:

```text
N(r) = πr²
```

Doubling radius therefore produces approximately four times as many world columns. Scaling must be evaluated against input-size growth, not only absolute elapsed time. Report the workload dimensions and units used for the comparison.

## 5. Benchmark Methodology

Every future benchmark record must include:

- exact Git commit SHA;
- workload ID;
- save identity/fingerprint (without copying or modifying the save);
- Java version and JVM vendor;
- operating system;
- available processors;
- configured heap limit;
- relevant Cartographer configuration;
- number of warmups; and
- number of measured iterations.

For measured iterations, report at least median/p50, p95, minimum, and maximum, with units. Do not use a single iteration as performance evidence except during debugging; label such a measurement `debug-only` and do not use it for a regression verdict. Do not silently discard inconvenient results; exclusions require a recorded reason and the complete applicable sample set remains available.

The report must distinguish process-cold, JVM-warm, and cache-warm conditions. It must not describe a fresh JVM run as a cold-disk benchmark. If OS filesystem cache state is not controlled, state that it is unknown or uncontrolled.

## 6. Correctness Fingerprints

Benchmarkable operations must eventually support stable semantic fingerprints. The fingerprint is a correctness gate, not an optimization metric.

For image output, prefer hashing normalized image pixel content, such as deterministic ARGB data, rather than PNG file bytes, because encoder metadata and compression can differ without changing the image.

For analytical results:

1. normalize deterministic ordering;
2. serialize semantic values deterministically, including coordinate-space meaning where applicable; and
3. hash the normalized representation.

The comparison rule is explicit:

```text
baseline fingerprint != candidate fingerprint => FAIL
```

The exception is a semantic change that is explicitly intended, separately reviewed, and used to establish a new baseline. A performance report must identify that exception rather than treating it as an optimization success.

## 7. Instrumentation Principles

Instrumentation must have bounded, measured overhead. Designs that add highly contended global atomics or heavyweight allocations to per-block hot loops are prohibited without evidence that the instrumentation cost is acceptable and necessary.

Prefer per-stage timing, per-chunk counters, local or thread-owned counters, and aggregation outside the hottest loops. Where appropriate, normal execution should use no-op instrumentation. The profiler/metrics system must not become a new bottleneck or alter the workload's memory behavior materially.

## 8. Macro vs Micro Benchmarks

**Macro benchmark** means a real Cartographer workflow over a representative `.vcdbs`, using the actual reader/parser/scanner/renderer path. Macro benchmarks support product-level performance conclusions and must include correctness and save-safety gates where applicable.

**Micro benchmark** means an isolated operation, intended for JMH. Examples include chunk decoding, protobuf parsing, block classification, and packed-coordinate lookup. Micro benchmarks explain local costs and guide focused changes; they do not establish end-to-end behavior.

JMH must not replace real-save macro benchmarks. JUnit tests must not be used as reliable wall-clock benchmark infrastructure.

## 9. Profiling Contract

Java Flight Recorder (JFR) is the primary runtime profiler for Java 25 performance investigations unless evidence supports another profiler. Future profiling must be capable of examining CPU samples, allocation pressure, garbage collection, thread states, locks/contention, and file I/O where useful.

Custom application events, if added later, must be coarse enough not to distort hot paths. PF-1.0.0 does not implement JFR events.

## 10. Regression Classification

| Outcome | Meaning |
| --- | --- |
| `PASS` | Correctness matches; required safety gates pass; the targeted objective is met; and no unacceptable regression occurs in another critical metric. |
| `FAIL` | Correctness mismatch, save-integrity failure, unbounded memory behavior, missed targeted objective, serious new regression, or invalid benchmark methodology. |
| `INCONCLUSIVE` | Measurements are noisy or insufficient, the environment changed, a baseline is missing, or profiling/measurement failed. |
| `NOT RUN` | The measurement was not executed. |
| `PENDING MANUAL VALIDATION` | Runtime evidence remains the responsibility of the reviewer/user environment. |

## 11. Performance Stage Evidence Matrix

| Evidence | Codex | ChatGPT reviewer | User/local reviewer |
| --- | --- | --- | --- |
| Implementation and narrow scope | Implement; static self-review; report assumptions | Independent diff and architecture review | Review as needed |
| Tests | Must not claim execution unless explicitly authorized and run | Interpret supplied evidence | Run tests and report results |
| Runtime profiling and benchmarks | Must not claim runtime performance success | Interpret evidence and issue technical gate verdict | Run macro benchmarks/profiling in the local environment |
| Real-save behavior and save integrity | Preserve read-only design; report relevant assumptions | Review safety contract and evidence | Run real-save validation; inspect for save changes and suspicious `.vcdbs-wal`/`.vcdbs-shm` files |
| Rendered output | Report implementation scope only | Review evidence when supplied | Perform PNG/manual inspection where required |
| Stage acceptance | Do not declare acceptance or `DONE` | Architecture/diff review and technical gate | Own local validation and product acceptance |

Remote GitHub state can prove remote refs, commits, and recorded workflow results; it does not prove local runtime state, local save integrity, or a local build artifact.

## 12. Initial Performance Foundation Roadmap

The sequence below is the performance roadmap. Each stage has its own
implementation, review, and validation record; the statuses below do not
replace those gates.

### PF-1.0 Performance Laboratory & Contracts

- **PF-1.0.0 Performance Constitution** — this normative contract.
- **PF-1.0.1 Metrics Model** — `PLANNED`.
- **PF-1.0.2 Low-Overhead Instrumentation** — `PLANNED`.
- **PF-1.0.3 Deterministic Workload Catalog** — `PLANNED`.
- **PF-1.0.4 Correctness Fingerprints** — `PLANNED`.
- **PF-1.0.5 Benchmark Runner** — `PLANNED`.
- **PF-1.0.6 JFR Integration** — `PLANNED`.
- **PF-1.0.7 JMH Microbenchmark Laboratory** — `PLANNED`.
- **PF-1.0.8 Reference Baseline** — `PLANNED`.
- **PF-1.0.9 Regression Comparator** — `PLANNED`.
- **PF-1.0.10 Save Safety Gate** — `PLANNED`.
- **PF-1.0.11 Baseline Report** — `PLANNED`.

### Subsequent stages

- **PF-1.1 Decode Memory Model** — [`VALIDATED`](PF_1_1_DECODE_MEMORY_MODEL.md) on the `perf/p1.1-decode-memory-model` branch; implementation and reviewer runtime evidence are recorded separately.
- **PF-1.2 Streaming Processing Engine** — [`IMPLEMENTED — VALIDATION PENDING`](PF_1_2_STREAMING_PROCESSING_ENGINE.md).
- **PF-1.3 Streaming ROCK Engine** — [`IMPLEMENTED — VALIDATION PENDING`](PF_1_3_STREAMING_ROCK_ENGINE.md). Implementation checkpoints are complete through G; runtime validation and performance evidence remain pending.
- **PF-1.4 Surface Tile Engine** — [`IMPLEMENTED — VALIDATION PENDING`](PF_1_4_SURFACE_TILE_ENGINE.md). Checkpoints A-G are implemented; runtime validation and performance evidence remain pending.
- **PF-1.5 Fused Prospecting Engine** — [`IMPLEMENTED — VALIDATION PENDING`](PF_1_5_FUSED_PROSPECTING_ENGINE.md). The production saved prospecting path now uses one shared selective chunk stream for upper-rock and all requested ore resources; runtime parity, save-safety, and JFR gates remain pending.
- **PF-1.6 SaveSession / Snapshot** — [`IMPLEMENTED — VALIDATION PENDING`](PF_1_6_SAVE_SESSION_SNAPSHOT.md). Checkpoints A–J establish the operation-scoped read-only session lifecycle, immutable save snapshot, session-aware readers, Prospecting/ROCK/Surface migration, and save identity checks. Runtime and performance validation remain deferred to PF-1.8.
- **PF-1.7 Render Data Cache** — `PLANNED`.
- **PF-1.8 Performance Gate / Hardening** — `PLANNED`.

## 13. Non-goals for PF-1.0.0

PF-1.0.0 is documentation and contract only. It must not:

- change Java production source files;
- change tests;
- modify Gradle dependencies;
- add JMH;
- add JFR code or events;
- implement metrics;
- implement benchmark commands;
- alter SQLite behavior;
- alter parser behavior;
- alter scanners;
- alter renderers;
- alter JavaFX;
- modify CI workflows;
- implement caching;
- implement parallelism;
- make actual performance claims; or
- record invented benchmark numbers.

Acceptance remains subject to independent review and the required validation gates. This document does not declare PF-1.0.0 accepted or `DONE`.

## Local real-save safety validation

Reviewers can run the opt-in save-integrity gate with:

```powershell
.\gradlew.bat realSaveValidation -Psave="C:\path\world.vcdbs"
```

The task hashes the main save and SQLite sidecars before and after a small production read-only SQLite smoke access. `PASS` requires the existing `SaveSafetyGate` to report `PASS`; creation of a new WAL or SHM sidecar is a failure. This is reviewer-only tooling, is not a benchmark, and provides no performance evidence. It is not wired into `build`, `check`, `test`, or CI. It does not run JMH or JFR.

Direct `.vcdbs` analysis uses immutable SQLite read-only access and assumes an offline, quiescent save. Do not use Cartographer to read a save while Vintage Story or another process is actively modifying it.

## ROCK performance scaling ladder

The global `RadiusProfile` vocabulary is `R128`, `R256`, `R512`, `R1024`, `R2048`, and `R4096`. The standard workload catalog applies all six profiles to each existing workload family. Defining an identifier does not prove that the operation currently completes; a failed or OOM run is still valid scalability evidence.

For ROCK performance work, the profiles have these roles:

- **R256 — sanity workload:** fast development feedback; insufficient by itself for serious ROCK performance acceptance.
- **R512 — medium workload:** intermediate scaling evidence.
- **R1024 — mandatory reference workload (“Trabant”):** the canonical BEFORE/AFTER workload. A serious ROCK performance PASS must not rely solely on R256/R512 evidence.
- **R2048 — primary scalability target (“Ferrari”):** the main target for the PF-1.1–PF-1.3 architecture work.
- **R4096 — stretch scalability workload:** observes headroom and how far the architecture scales; it is available before comfortable completion is expected.

Large-radius failures must be reported honestly and must not be hidden by weakening runtime gates. For example, `R1024 -> SUCCESS`, `R2048 -> OOM`, and `R4096 -> OOM` can be a legitimate BEFORE state; later evidence may show R1024 faster, R2048 successful, and R4096 still failing, followed eventually by R4096 success. R2048/R4096 are targets, not claims of current performance, bounded memory, safety, or runtime validation. R2048/R4096 success is not required to begin PF-1.1.

For changes materially affecting ROCK, R256/R512 are development evidence, R1024 is mandatory canonical evidence, R2048 is the primary target, and R4096 is the stretch target. Correctness fingerprint mismatch is FAIL regardless of speed, and save safety remains a separate mandatory gate when applicable.

## Local macro baseline tooling

Reviewers can run a selected workload from the supported real-save ROCK macro scaling ladder:

```powershell
.\gradlew.bat perfBaseline `
  -Psave="C:\path\world.vcdbs" `
  -Pworkload="ROCK_UPPER_R1024" `
  -PgitSha="<40-char SHA>"
```

The supported macro workloads are `ROCK_UPPER_R256`, `ROCK_UPPER_R512`, `ROCK_UPPER_R1024`, `ROCK_UPPER_R2048`, and `ROCK_UPPER_R4096`. Replace the workload argument in the command above with any of those IDs. The command uses the production ROCK pipeline with two JVM-warm warmups and five measured iterations. The OS filesystem cache state is uncontrolled; this is not cold-disk evidence. The save SHA-256 is calculated only after successful measured iterations, and save safety must be checked separately with `realSaveValidation`. Keep Vintage Story closed and the save quiescent. This is opt-in reviewer tooling, not JMH or JFR, and is not wired into build, test, check, or CI. It does not introduce PF-1.0.12.
