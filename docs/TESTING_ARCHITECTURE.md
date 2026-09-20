# Testing Architecture

## Purpose

This document is the engineering contract for tests in VS Cartographer.

The goal is not merely to make the suite faster. The target architecture is a
large test suite that executes in the fastest validated topology while
remaining deterministic, isolated, order-independent, diagnosable, and safe on
developer machines and CI.

Parallelism is an optimization, not a goal. It is enabled only when repeated
evidence shows a real benefit without weakening determinism or isolation.

## Core invariants

Tests must satisfy all of the following unless a documented exception applies:

- one test must not depend on another test having run before it;
- one test must not leave state that changes a later test;
- test correctness must not depend on scheduler timing;
- temporary filesystem state must be test-owned;
- SQLite fixtures must be test-owned unless explicitly immutable;
- mutable global JVM state must not be changed concurrently;
- owned threads, executors, sockets, files, and database connections must be
  cleaned up on success and failure paths;
- production behavior must not be weakened to make a timing-sensitive test pass;
- full quality gates remain mandatory even when the suite is optimized.

## Test categories

The repository distinguishes tests by responsibility, not by how convenient
they are to run.

Test categories are expressed with test-source meta-annotations:

- `@IntegrationTest` -> JUnit tag `integration`;
- `@ConcurrencyTest` -> JUnit tag `concurrency`.

New categories are added only when the repository contains a concrete test that
needs them; do not create speculative category annotations or empty diagnostic
tasks.

Classification is incremental. An unclassified test must not automatically be
assumed to be a pure unit test until the audit has reviewed it. Multiple
categories may apply to the same test when that improves targeted diagnosis.
Category tasks are diagnostic subsets and may overlap; they are not CI shards.

### Unit tests

A unit test should exercise a small unit of behavior with cheap setup. It must
not start a complete production pipeline when a narrower collaborator or
fixture can prove the same contract.

Unit tests should normally avoid SQLite, network listeners, JavaFX startup, and
large filesystem fixtures.

### Integration tests

An integration test verifies collaboration across real infrastructure
boundaries such as SQLite, filesystem-backed stores, or multiple production
components.

Integration tests are allowed to be heavier, but they must still be isolated
and deterministic. Integration is not an exemption from parallel-safety.

### Concurrency and lifecycle tests

Concurrency tests must synchronize on semantic events using explicit
coordination such as latches, barriers, phasers, semaphores, controlled
futures, or callbacks.

A timeout is a failure/deadlock guard. It is not the condition that proves the
behavior.

## Filesystem rules

Use JUnit `@TempDir` for mutable test files and directories whenever possible.

Each test must own the paths it mutates. Do not use fixed writable paths under
the project directory, the operating-system temp root, the user's home
directory, or a shared cache directory.

Fixtures shared across tests may be shared only when they are immutable during
test execution.

Cleanup must work on assertion and exception paths. Prefer lifecycle constructs
that make cleanup automatic rather than relying on the last assertion being
reached.

## SQLite rules

SQLite databases created by tests must be test-owned, normally below a
per-test `@TempDir`.

Do not share one mutable SQLite database between parallel test cases.

Connections, statements, and result sets must be closed deterministically.

A test that validates read-only save behavior must preserve the production
source-safety contract. Test speed is not a reason to open a writable
production-style source connection.

Avoid rebuilding a large database fixture when a minimal schema and minimal row
set prove the same behavior.

If an immutable database fixture is reused, its immutability and ownership must
be obvious from the fixture API.

## Global and static state

Tests must not casually mutate process-wide state such as:

- `System.setProperty` / `System.clearProperty`;
- `user.home`;
- default locale or timezone;
- global logging configuration;
- static mutable registries;
- application-wide singletons;
- common executors or global caches.

When production behavior truly depends on process-wide state, prefer injecting
an explicit dependency so tests can isolate it.

The current architecture treats JVM-global mutation as a HIGH-risk finding
that blocks CI. Prefer refactoring production code toward an injectable
dependency rather than adding an ad-hoc test exception.

Immutable `static final` constants and immutable fixtures are safe. Mutable
static test state is not.

## Concurrency rules

Do not use `Thread.sleep(...)` or `TimeUnit.*.sleep(...)` as synchronization.

Do not infer lifecycle progress from:

- elapsed time;
- `Thread.State`;
- `Thread.isAlive()`;
- executor queue size;
- an assumption that a worker "probably started already".

Synchronize on the event that the assertion is actually about.

Every executor or thread created by a test must have a clear owner. Cleanup must
release blockers, request shutdown/cancellation where appropriate, and verify
bounded termination. A timed join or await that expires must fail with useful
diagnostics rather than silently continuing.

Worker completion and callback/consumer completion are separate events unless a
production contract explicitly makes them the same event.

## Network and ports

Prefer in-process fakes over real network listeners when the network stack is
not part of the behavior under test.

When a real listener is required, do not use a hard-coded shared port. Bind to
an operating-system-assigned free port and keep ownership local to the test.

Close listeners and clients on all paths.

External internet access is not part of the normal unit-test contract.

## JavaFX and other process-wide runtimes

Process-wide runtimes must have a deliberate lifecycle strategy before
class-level or method-level parallel execution is enabled for their tests.

Do not repeatedly initialize a runtime that is designed to be initialized once
per JVM.

Tests must distinguish logic that can run without the toolkit from logic that
actually requires toolkit integration.

## Fixtures

Prefer the smallest fixture that proves the contract.

Good shared fixtures are immutable values, immutable byte arrays, immutable
serialized inputs, or immutable fixture builders.

Avoid a shared mutable fixture merely because construction is expensive.
Expensive setup should first be challenged: determine whether the test is
accidentally exercising too much production code.

Where expensive immutable setup is genuinely necessary, reuse may be introduced
only after ownership and thread-safety are explicit.

## Naming

Test class names should identify the production subject or contract.

Test method names should describe behavior or invariant rather than an
implementation sequence.

A useful pattern is:

`operationConditionExpectedOutcome`

Names should remain readable without requiring knowledge of the current
implementation.

## Performance contract

Test performance is observable engineering data.

The Gradle `test` task emits machine-readable timing reports at:

`build/reports/test-performance/test-class-timings.csv`

`build/reports/test-performance/test-method-timings.csv`

and logs the slowest classes and individual tests at the end of the run. The
parallel probe writes equivalent reports with the
`test-parallel-probe-` prefix so serial and parallel evidence are never
silently mixed.

Performance work follows this order:

1. establish a serial baseline;
2. identify dominant classes and setup costs;
3. remove accidental heavy work;
4. make tests isolated and parallel-safe;
5. introduce controlled Gradle process parallelism;
6. validate repeated deterministic execution;
7. consider JUnit in-process parallelism only where the additional complexity is
   justified;
8. shard CI only if simpler process-level parallelism is insufficient.

The current representative baseline is roughly twenty seconds for the complete
1100+ test suite on the Windows CI runner, with the slowest class around a few
seconds after the SQLite fixture refactor.

`testPerformanceBudget` deliberately uses coarse regression limits rather than
microbenchmark thresholds:

- complete suite: at most 60 seconds;
- individual test class: at most 15 seconds;
- at least 1104 tests must execute;
- zero failed and zero skipped tests.

These limits are intentionally much wider than normal run-to-run noise. Their
purpose is to catch structural regressions such as accidentally restoring
row-by-row autocommit fixture setup, not to fail CI over minor runner variance.

## Parallel execution strategy

The production pull-request test topology uses one Gradle test worker.
Controlled 1/2/3/4-worker experiments showed that whole-suite process-level
parallelism increased elapsed time after the SQLite fixture refactor, so adding
workers would make CI slower while increasing resource contention.

Current Gradle test tasks explicitly set
`junit.jupiter.execution.parallel.enabled=false`. The production `test`
task therefore runs one worker with sequential JUnit execution. This is an
evidence-based final topology, not a temporary fallback.

`testParallelProbe` remains an opt-in developer diagnostic for future
experiments. It measures Gradle worker-process parallelism only. Any future
JUnit in-process experiment must use an explicit dedicated task/configuration;
it must not silently alter the production baseline.

### Current Gradle verification tasks

The default `test` task intentionally uses one Gradle test worker. This is
the selected production topology for the current suite because repeated
whole-suite worker experiments did not outperform the one-worker baseline.

The repository provides these TEST-PERF tasks:

```text
testArchitectureAudit
testArchitectureGuard
testPerformanceBudget
testQualityGate
testParallelProbe
testIntegration
testConcurrency
```

`testArchitectureAudit` writes detailed findings and a per-file summary under
`build/reports/test-performance/`.

The audit summary is triage evidence, not a static proof that a test is safe.
It assigns each file one of three review priorities:

- `HIGH` for patterns that violate the current isolation contract, including
  filesystem or SQLite mutation without `@TempDir`, process-global default or
  system-property mutation, mutable static test state, sleep-based
  synchronization, unbounded `Thread.join()`, or thread/executor/network
  fixtures that have not been explicitly categorized;
- `REVIEW` for explicitly categorized concurrency/resource behavior that
  still deserves human inspection, such as owned thread creation;
- `INFO` for owned fixture behavior such as filesystem/SQLite mutation under
  `@TempDir`, category annotations, and other non-blocking evidence.

`testArchitectureGuard` enforces the generated triage. Any `HIGH` row fails
the build. This keeps the audit and the gate on one source of truth instead of
maintaining a smaller independent regex deny-list.

Large SQLite fixture populations must be inserted inside an explicit
transaction and should use JDBC batching. Repeating one auto-committed insert
per fixture row is both unnecessarily slow and increases timing noise in CI.
The optimization must remain test-only and must not weaken the production
read-only save contract.

`testParallelProbe` is an opt-in validation task that runs the complete
JUnit suite with bounded Gradle worker-process parallelism. The default probe
worker count is CPU-aware and capped conservatively; it may be overridden for a
controlled experiment with:

```powershell
.\gradlew.bat testParallelProbe -PtestParallelForks=2
```

The override must remain between 1 and 16. A higher number is not evidence of a
better configuration; representative timing and repeated deterministic runs
decide the final worker count.

`testIntegration` and `testConcurrency` execute the corresponding explicitly
categorized subsets with one Gradle worker. They exist for focused diagnosis
and deliberate stress campaigns. Because categories may overlap, their
combined test counts must not be treated as the size of the complete suite.

The normal pull-request correctness gate is the complete
`test` task.

Pull-request CI has exactly one test job and one Gradle quality-gate
invocation: `testQualityGate`. The gate runs the architecture audit and guard,
executes the complete suite once with the selected one-worker topology, and
validates the coarse performance/completeness budget. CI then retains
timing/JUnit evidence and performs the existing tooling syntax validation.

Topology matrices and repeated stress campaigns are diagnostic techniques, not
normal PR checks. They may be run deliberately when test architecture changes,
but they must not be added as permanent matrix jobs to the pull-request
workflow. A red stress result must still be investigated; removing the stress
job from PR CI is not permission to ignore previously observed failures.

## CI contract

Pull-request CI must preserve the complete correctness gate and expose one
test job only. Multiple worker-matrix or repeated stress jobs must not be part
of the normal pull-request workflow.


Optimization may change execution topology, but must not:

- silently skip slow tests;
- convert failures to warnings;
- remove assertions;
- ignore leaked workers;
- hide flaky tests behind retries;
- weaken read-only save guarantees.

Timing and test-result evidence should remain available when the test task
fails, because failed and slow runs are especially important for diagnosis.

Future sharding must ensure that the complete suite is represented exactly once
(or intentionally repeated for stress validation) and that shard failures are
visible to the final gate.

## Flakiness

A later green run does not erase an unexplained red run.

Before calling a failure flaky, classify the evidence. Determine whether the
root cause is:

- a test synchronization defect;
- shared mutable state;
- filesystem or SQLite collision;
- leaked resources;
- production race;
- environment/tooling behavior;
- another reproducible implementation defect.

A flaky-test repair must preserve the semantic coverage of the original test.

## Anti-patterns

Do not introduce:

- sleeps as synchronization;
- fixed writable temp paths;
- mutable static fixtures shared by tests;
- ordering dependencies;
- "retry until green" as a correctness strategy;
- giant integration pipelines inside ordinary unit tests;
- one SQLite database shared by unrelated test cases;
- process-global property mutation without restoration and isolation;
- silent catches that turn cleanup or worker leaks into success;
- arbitrary timeout increases as the only flaky-test fix;

## Merge checklist

Before merging a new or changed test, verify:

- the test passes independently;
- the test does not depend on execution order;
- mutable filesystem state is test-owned;
- SQLite state is isolated;
- global/static mutation is absent or explicitly isolated and restored;
- concurrency uses semantic synchronization;
- no sleep is used as synchronization;
- owned resources are cleaned up on failure paths;
- the fixture is no heavier than necessary;
- the test category matches what it actually exercises;
- failure messages provide enough information for CI diagnosis;
- the change does not weaken the full quality gate.

## Rollout rule

Execution-topology changes are introduced incrementally. At each checkpoint the
suite must stay under the same or stronger correctness contract.

The current target state is 1100+ tests executing deterministically in the
fastest validated topology, without flaky behavior and without dependence on
test order. For the present suite that topology is one Gradle worker with JUnit
in-process parallelism disabled. Parallel execution remains an opt-in diagnostic
and may become a production choice only if future measurements show a real,
repeatable benefit without weakening isolation.
