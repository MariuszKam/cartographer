# PF-1.2 Streaming Processing Engine

**Status:** `IMPLEMENTED — FINAL VALIDATION PENDING`

**Scope:** Checkpoints A–F — implementation and documentation record; final reviewer-controlled validation remains pending

PF-1.2 defines the bounded, completion-driven processing infrastructure for
later streaming analysis stages. This document remains normative for the
implemented architecture and records the checkpoint history. The implementation
and static checkpoint reviews are complete. Final runtime, regression, save-
safety, profiling, and performance validation have not been run. PF-1.2 is not
`VALIDATED`, and no performance improvement is claimed from implementation
alone.

## 1. Purpose and stage boundaries

The purpose of PF-1.2 is to provide a bounded streaming/concurrency engine
that can process decoded chunk outcomes without retaining an input-sized set
of completed results. PF-1.2 is infrastructure, not a domain analyzer.

The stage boundaries are:

```text
PF-1.2  bounded streaming/concurrency processing infrastructure
PF-1.3  Streaming ROCK Engine
PF-1.4  Surface Tile Engine
PF-1.5  Fused Prospecting Engine
PF-1.6  SaveSession / Snapshot lifecycle
```

PF-1.2 must not absorb ROCK result construction, surface tiling,
prospecting fusion, or save-session ownership. Later stages consume the
engine's completed outcomes and own their domain state and retention choices.

## Implementation record

The following records the actual PF-1.2 checkpoint commits. The checkpoint
implementation and static reviews are complete; the final validation gates
remain reviewer-controlled and pending.

| Checkpoint | Commit | Implemented outcome |
|---|---|---|
| A — Architecture Contract | `ad6a5a359c0c07f9e54f3e86398a218cf544fae8` — `docs: define p1.2 streaming processing contract` | Established the bounded completion-driven processing contract and its stage boundaries. |
| B — Completion Engine | `cc62ff1a5b296821d158d8d39bdb9ab07b1294b3` — `perf: add completion-driven decode pipeline` | Added the completion-driven bounded decode pipeline with fixed platform workers and control-thread consumption. |
| B — Completion Engine hardening | `5914c3d82e7cb7d4cc9aa21bfa19e7e19d1280b8` — `perf: harden completion pipeline failure semantics`; `3a14f4cb20ccfc724a640b13e7fc62cd0112f927` — `perf: enforce safe decode worker quiescence` | Hardened failure propagation, cancellation, shutdown, and worker/resource quiescence. |
| C — Reader Integration | `6b889ababc776deb22fee61b74054c112c3e2e2c` — `perf: stream vcdbs decode completions` | Migrated all five runtime `VcdbsReader` server-chunk decode paths to the streaming completion-driven pipeline. |
| D — Failure & Lifecycle Hardening | `6975b1c5f49d1c2bd3829e28bd4447126abc493a` — `perf: harden decode pipeline lifecycle`; `0c9cd729073d591e7f820502e7d1f29f255b963b` — `test: make decode lifecycle tests deterministic` | Established terminal-operation quiescence, interruption-safe cleanup, primary-failure preservation, interrupt restoration, and deterministic latch-based lifecycle tests. The tests have not yet been executed for PF-1.2 closure. |
| E — Legacy Cleanup | `0dbce56d936cf42a308c31d139e3a1360c99e99f` — `perf: remove legacy decode concurrency` | Deleted the obsolete ordered pipeline, its dedicated tests, and the unused common-pool `ParallelChunkScanner` wrapper. |

## Implemented architecture

The implemented PF-1.2 flow is:

```text
SQLite producer/control thread
    -> bounded submissions
    -> fixed platform decode workers
    -> ExecutorCompletionService completion queue
    -> caller/control-thread outcome consumption
    -> existing downstream semantic consumers
```

Scheduling is completion-driven: a slow earlier submission does not
intentionally impose submission-order head-of-line blocking. Downstream
callbacks remain serialized on the control thread. `maxInFlight` bounds total
outstanding work and results logically, including completed-but-unconsumed
outcomes. Decode workspaces remain exclusively borrowed and bounded, and
pipeline quiescence precedes workspace-pool destruction.

The executor is owned by the operation. No global executor, virtual-thread
strategy, common-pool scheduler, or replacement legacy abstraction was
introduced.

### Reader integration

PF-1.2 migrated these five `VcdbsReader` server-chunk decode categories from
the historical ordered path to `BoundedStreamingDecodePipeline`:

- exact position lookup;
- regular table streaming;
- selective exact lookup;
- selective table streaming; and
- selective coverage reading.

Diagnostics, counters, progress reporting, and downstream consumers remain
owned by the producer/control thread. SQLite result order is not treated as a
semantic ordering guarantee, and semantic consumers were not made parallel.

### Failure and lifecycle record

The following guarantees are implemented and statically reviewed, but remain
subject to final runtime validation:

- `OPEN` transitions to `FINISHED` on successful completion or to an aborted/closed terminal state on failure or close;
- submission is allowed only while open;
- successful `finish` is idempotent and `close` is safe after finish;
- `close` is idempotent after abort;
- fatal worker and consumer failures abort outstanding work;
- the original worker or consumer failure remains primary where applicable;
- cleanup continues across coordination interruption without worker leakage;
- the controlling thread's interrupt status is restored; and
- workspace resources are not destroyed while workers can still use them.

PF-1.1 established decoder ownership, reusable bounded workspace, ZSTD
context lifecycle, and decoder allocation improvements. PF-1.2 adds
completion-driven bounded scheduling, deterministic operation-scoped worker
lifecycle, reader integration, and removal of the ordered/common-pool legacy
paths. PF-1.3 remains responsible for the Streaming ROCK Engine and for
removing whole-operation decoded/derived ROCK retention. PF-1.2 does not solve
the major R4096 `RockMap`/`RockColumnScanner` memory bottleneck.

## 2. Problem statement

The historical `BoundedOrderedDecodePipeline<T>` bounded in-flight work and
used fixed platform worker threads, while downstream consumption remained on
the submitting/control thread. Its pending collection nevertheless resolved
the oldest submitted `Future` first. Thus it provided submission-order
delivery. If an earlier decode was slow while later decodes had completed, the
control thread waited at the head of the pending collection: this was
submission-order head-of-line blocking. Checkpoint E removed that obsolete
implementation and its dedicated tests.

PF-1.2 replaces submission-order result resolution with completion-driven
resolution. The engine consumes a completed result when **any** task
completes. Submission order is not a semantic guarantee.

The conceptual flow is:

```text
SQLite producer
    -> bounded task submission
    -> fixed decode workers
    -> completion queue
    -> caller/control-thread consumption
    -> future streaming analyzers
```

## 3. Thread and role contract

### Producer/control thread

The producer/control thread owns and performs:

- SQLite row iteration;
- task submission and backpressure coordination;
- draining completed outcomes;
- downstream `Consumer` invocation;
- `ReadDiagnostics` updates;
- aggregate counters;
- progress reporting; and
- lifecycle coordination and cleanup.

Decode workers create completed outcomes only. They must not mutate
`ReadDiagnostics`, downstream analyzers, shared domain aggregators, UI state,
or progress reporters. Workers must not invoke downstream semantic consumers
directly. The downstream semantic layer remains serialized unless a later
milestone explicitly changes that contract.

PF-1.2 parallelizes decode-related work, not arbitrary semantic processing.

The engine requires an explicitly bounded fixed worker count and explicitly
owns its executor. CPU-bound decode work uses platform threads. It must not
use `ForkJoinPool.commonPool`, `parallelStream()`, implicit
`CompletableFuture` common-pool execution, or a virtual-thread-per-task
strategy for CPU-bound chunk decoding. Preview APIs such as
`StructuredTaskScope` and new external concurrency dependencies are out of
scope. The default worker-count and max-in-flight policy currently present in
`VcdbsReader` remains unchanged unless later benchmark evidence justifies a
change; this contract does not claim those values are optimal.

## 4. Completion and bounded-memory invariant

For a configured `F = maxInFlight`, define:

- `R`: tasks currently running on decode workers;
- `Q`: tasks queued and waiting for a worker; and
- `C`: completed task outcomes not yet consumed by the control thread.

The hard invariant is:

```text
0 <= R + Q + C <= F
```

This is a required invariant, not guidance. Completed-but-unconsumed
outcomes count toward the bound. The producer must not continuously submit
work merely because tasks have technically completed while potentially large
results remain queued. The bound is on total outstanding work and results,
not only on an executor queue length.

The intended memory shape is approximately:

```text
O(workerCount * reusableWorkspace
  + maxInFlight * boundedOutcome
  + downstreamResultState)
```

`boundedOutcome` means the outcome retained for one outstanding task is
bounded by the contract of the decode operation; it does not authorize
retaining every decoded chunk or every downstream result for the duration of
the input. Input-length-dependent retention merely because work has
completed is prohibited. Exact byte limits are intentionally not specified
until measured evidence exists.

## 5. Submission and completion algorithm

The implemented engine is completion-driven and follows this algorithm on
submission:

1. Non-blockingly drain already completed outcomes where possible.
2. If total in-flight work is at `F`, block until **any** task completes.
3. Consume that completed outcome on the control thread.
4. Only after capacity is released submit another task.

The implementation must not wait for the oldest submitted task. Completion
order may differ from both submission order and SQLite iteration order.

On finish:

1. Stop accepting new work.
2. Drain all outstanding work by completion order.
3. Consume every successful outcome exactly once unless the pipeline aborts.
4. Shut down worker resources deterministically.

The completion queue, executor bookkeeping, or equivalent mechanism must make
the total bound above observable and enforceable by the implementation. A
completed result remains part of `C` until its consumer callback has returned
and capacity has been released.

## 6. Ordering contract

Result delivery order is **UNSPECIFIED with respect to submission order**.
Completion order may vary between executions. A downstream result that
requires deterministic ordering must normalize or sort at the appropriate
semantic boundary.

The shared streaming engine must not globally reintroduce submission-order
buffering to compensate for a downstream component that incorrectly depends
on incidental SQLite or worker ordering. SQLite query result order is not a
semantic ordering guarantee unless an explicit SQL `ORDER BY` contract exists.

## 7. Worker workspace and ownership

PF-1.1 established and validated `ChunkDecodeWorkspace`, reusable decode
scratch, a reusable ZSTD decompression context, exclusive workspace
ownership, a bounded `ChunkDecodeWorkspacePool`, and owned published decoded
layers. PF-1.2 preserves that model and does not redesign decoder memory
ownership.

The following are hard ownership rules:

- no published decoded array may alias reusable workspace scratch;
- one workspace has at most one borrower at a time;
- workspace lifetime outlives every task using it; and
- workspace/resource cleanup happens only after workers have stopped using
  those resources.

The engine may arrange worker borrowing and outcome publication, but it must
not transfer reusable scratch ownership into a published result.

## 8. Failure taxonomy and propagation

### Expected data failures

Malformed chunk payloads, parse failures represented by existing
`ParseResult`, and palette/decode failures already represented by existing
outcome objects are expected per-row/per-chunk data failures. Where possible,
they preserve existing diagnostics semantics and do not abort the whole
operation. They are not infrastructure failures solely because they occur in
a worker.

### Infrastructure/pipeline failures

Unexpected task exceptions, executor rejection, consumer exceptions, pipeline
lifecycle corruption, and interruption during blocking coordination are
pipeline failures. They must not be silently converted into ordinary parse
diagnostics.

The first fatal infrastructure failure terminates normal processing. The
engine must preserve the original/root cause where possible, cancel or
interrupt outstanding queued/running tasks on a best-effort basis, and still
perform lifecycle cleanup. No further semantic consumer callbacks occur after
the fatal-abort boundary, except callbacks already completed before that
boundary. Cancellation cannot promise instantaneous termination of arbitrary
native or Java code.

If coordination is interrupted, the current thread's interrupted status must
be restored. Failure handling must not leave non-daemon worker threads running
after operation shutdown.

## 9. Lifecycle/state contract

The behavior has three conceptual states:

```text
OPEN -> FINISHED
  |       |
  +----> ABORTED/CLOSED
```

Required behavior:

- submission is allowed only while `OPEN`;
- successful `finish` is safe and idempotent;
- `close` is safe after `finish`;
- submission after finish or close fails explicitly;
- a fatal failure transitions out of `OPEN`;
- outstanding work is cancelled on abort/close; and
- executor termination occurs before owned decode-workspace resources are
  destroyed.

This checkpoint defines behavior, not a required public state-machine API.

## 10. Consumer execution contract

Every result consumer callback executes on the producer/control thread, never
on a decode worker thread. Current diagnostics are mutable and are not
designed for concurrent updates; current downstream analyzers generally
assume serialized consumption. Keeping callbacks on the control thread
minimizes the thread-safety surface and lets later streaming milestones
process one completed decoded chunk at a time without making all domain code
concurrent.

## 11. Removed legacy concurrency paths

Checkpoint E audited and removed the unused `ParallelChunkScanner` wrapper,
which had used `parallelStream()` and therefore relied on common
`ForkJoinPool` behavior. The obsolete `BoundedOrderedDecodePipeline` and its
dedicated tests were removed as well. Neither implementation remains a source
alternative to the completion-driven pipeline. Historical references in this
document describe the architecture that was replaced, not active production
components.

## 12. Explicit non-goals

PF-1.2 does not include:

- a `RockColumnScanner` streaming rewrite;
- a `RockMap` redesign;
- an attempt to fix ROCK R4096 whole-result memory retention;
- a Surface Tile Engine;
- prospecting fusion;
- `SaveSession`;
- a global or application-lifetime executor;
- a parsed-data cache;
- render-cache redesign;
- a SQL schema change;
- any SQLite write behavior;
- a coordinate semantic change;
- a parser semantic change;
- decoder ownership redesign;
- JavaFX changes;
- GC tuning or JVM performance flags;
- JNI, GPU, SIMD, or off-heap work;
- a new dependency; or
- a performance claim based on static code inspection.

PF-1.2 must not claim that R4096 will succeed. PF-1.1 evidence associates
R4096 failure with downstream whole-operation ROCK retention and intentionally
leaves that responsibility to PF-1.3.

## 13. Implementation checkpoints

The planned sequence is:

```text
A — Architecture Contract
B — Completion Engine
C — Reader Integration
D — Failure & Lifecycle Hardening
E — Legacy Cleanup
F — Implementation / Validation Record
```

Checkpoints A through E are implemented and statically reviewed. Checkpoint F
records the implementation and validation status. Final runtime evidence is
still required before PF-1.2 can be called `VALIDATED`.

## 14. Implemented unit/concurrency test coverage

Checkpoint B/D provide deterministic tests, using latches,
barriers, or equivalent synchronization primitives rather than fragile
wall-clock micro-timing assertions, for at least:

- a later completed task being consumed before an earlier slow task;
- no submission-order head-of-line blocking from a slow first task;
- consumer execution on the control/submitting thread;
- decode workers being platform threads;
- the in-flight count never exceeding configured maximum;
- completed-but-unconsumed outcomes counting toward that maximum;
- producer blocking when true capacity is exhausted;
- producer resuming after any completion frees capacity;
- each successful result delivered at most/exactly once as applicable;
- no lost results on successful finish;
- worker failure aborting and preserving its cause;
- consumer failure aborting;
- outstanding work being cancelled on close/abort;
- interruption restoring interrupt status;
- finish idempotence;
- submission after terminal state failing; and
- no reliance on elapsed-time performance assertions for correctness.

## 15. Validation status and final validation plan

PF-1.2 is implemented and statically reviewed but is not yet `VALIDATED`.
The following gates are explicitly pending:

| Gate | Status |
|---|---|
| Architecture/static review A | PASS |
| Completion engine static review B | PASS |
| Reader integration static review C | PASS |
| Lifecycle hardening static review D | PASS |
| Legacy cleanup static review E | PASS |
| Final documentation/static review F | PENDING REVIEW |
| `clean test` | NOT RUN |
| `jmhClasses` | NOT RUN |
| real-save safety | NOT RUN |
| ROCK R1024 candidate comparison | NOT RUN for PF-1.2 candidate |
| ROCK R2048 candidate comparison | NOT RUN for PF-1.2 candidate |
| Exact correctness fingerprint comparison | NOT RUN for PF-1.2 candidate |
| Raw timing sample inspection | NOT RUN |
| JFR | NOT RUN |
| R4096 stretch | NOT RUN |

PF-1.1 measurements are historical context and baseline evidence only; they
are not PF-1.2 candidate validation.

The final reviewer-controlled validation plan is:

- static architecture and diff review;
- `clean test`;
- `jmhClasses`;
- real-save save-safety validation;
- canonical ROCK R1024 comparison;
- ROCK R2048 comparison;
- exact correctness fingerprint comparison where baseline comparison is
  applicable;
- inspection of raw timing samples, not only comparator status;
- JFR inspection because this stage changes concurrency architecture; and
- an R4096 stretch observation, with success not required for PF-1.2.

None of these final validation operations has been executed for PF-1.2 in
this documentation checkpoint. No candidate result, performance improvement,
or R4096 success is supplied or implied.

Current comparison tooling primarily validates comparability and correctness
fingerprints and reports timing deltas. A helper/comparator `PASS` alone is
not proof that performance improved or did not regress. Reviewer judgment must
inspect benchmark evidence and methodology. Comparator code is not modified
by this checkpoint.

## 16. Save safety

The `.vcdbs` save remains immutable and is opened read-only. PF-1.2 does not
modify SQLite access mode. Creating WAL/SHM files is not acceptable as normal
behavior. Save SHA/size and sidecar state remain part of reviewer validation
when save-access code changes. Analysis assumes an offline, quiescent save.

## 17. Checkpoint-F boundary

This file is the PF-1.2 architecture contract and implementation/validation
record. Checkpoint F changes documentation/status only; it adds no production
Java, test Java, Gradle, CI, parser, decoder, SQLite, UI, or runtime behavior.
PF-1.2 remains pending final reviewer-controlled validation and must not be
described as `VALIDATED` until that evidence exists.
