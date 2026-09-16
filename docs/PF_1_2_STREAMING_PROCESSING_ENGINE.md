# PF-1.2 Streaming Processing Engine

**Status:** `DESIGN CONTRACT — IMPLEMENTATION NOT STARTED`

**Scope:** Checkpoint A — Architecture Contract only

PF-1.2 defines the bounded, completion-driven processing infrastructure for
later streaming analysis stages. This document is normative for the future
implementation and review of PF-1.2. It does not claim implementation,
validation, performance improvement, or milestone completion.

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

## 2. Problem statement

The existing `BoundedOrderedDecodePipeline<T>` bounds in-flight work and uses
fixed platform worker threads, while downstream consumption remains on the
submitting/control thread. Its pending collection nevertheless resolves the
oldest submitted `Future` first. Thus it provides submission-order delivery.
If an earlier decode is slow while later decodes have completed, the control
thread waits at the head of the pending collection: this is submission-order
head-of-line blocking.

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

The future implementation must be completion-driven and conceptually follow
this algorithm on submission:

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

## 11. Legacy `ParallelChunkScanner`

The current `ParallelChunkScanner` uses `parallelStream()` and therefore
relies on common `ForkJoinPool` behavior. PF-1.2 must not blindly rewrite it
and must not modify it in this checkpoint. Before removal or modification in
a later checkpoint, production usages must be audited with repository search.
If it is unused, later cleanup may remove it. If it is used, its semantics
must be reviewed before migration.

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

## 13. Planned implementation checkpoints

The planned sequence is:

```text
A — Architecture Contract
B — Completion Engine
C — Reader Integration
D — Failure & Lifecycle Hardening
E — Legacy Cleanup
F — Implementation / Validation Record
```

This document implements only checkpoint A. Checkpoints B through F are not
pre-marked complete and require their own implementation, review, and
evidence.

## 14. Required future unit/concurrency tests

Checkpoint B/D implementation must provide deterministic tests, using latches,
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

## 15. Future validation gates

PF-1.2 cannot become `VALIDATED` from static review or unit tests alone.
Future reviewer-controlled evidence must include:

- static architecture and diff review;
- `clean test`;
- `jmhClasses`;
- real-save save-safety validation;
- canonical ROCK R1024 comparison;
- ROCK R2048 comparison;
- identical correctness fingerprints for comparable baseline/candidate
  workloads;
- inspection of raw benchmark samples, not only comparator status;
- JFR inspection because this stage changes concurrency architecture; and
- an R4096 stretch observation, with success not required for PF-1.2.

No results are supplied or implied by this contract.

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

## 17. Checkpoint-A boundary

This file is the complete PF-1.2 Checkpoint-A architecture contract. It adds
no production Java, test Java, Gradle, CI, parser, decoder, SQLite, UI, or
runtime behavior. PF-1.2 remains design-only until later checkpoints and
independent reviewer-controlled validation provide evidence.
