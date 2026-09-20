# Testing Architecture

## Purpose

This document is the engineering contract for tests in VS Cartographer.

The goal is not merely to make the suite faster. The target architecture is a
large test suite that can execute with controlled parallelism while remaining
deterministic, isolated, order-independent, diagnosable, and safe on developer
machines and CI.

Parallelism is enabled only after the suite proves that it is safe for the
chosen level of concurrency. A faster flaky suite is a regression.

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
- `@ConcurrencyTest` -> JUnit tag `concurrency`;
- `@GuiTest` -> JUnit tag `gui`;
- `@SerialTest` -> JUnit tag `serial`.

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

### GUI / JavaFX tests

Tests that require JavaFX lifecycle or global toolkit state must make that
requirement explicit. They must not silently assume that each test owns the
process-wide JavaFX runtime.

### Serial-only tests

Serial execution is an exception, not a default escape hatch.

A test may be classified as serial-only only when it exercises unavoidable
process-wide mutable state or another resource that cannot be safely isolated.
The reason must be documented close to the classification and should be
periodically reconsidered.

Serial classification must never be used to hide a race, shared fixture bug,
fixed-path collision, leaked thread, or missing cleanup.

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

If temporary mutation of JVM-global state is unavoidable, the test must restore
the previous value on every path and must be classified so it cannot race with
other tests that observe or mutate the same state.

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

Do not set permanent numeric budgets before measuring representative baselines.
Once budgets are adopted, they must be based on observed CI behavior and must
not encourage weakening correctness.

## Parallel execution strategy

The preferred first level of parallelism is Gradle test-worker process
parallelism because separate JVMs provide a strong isolation boundary.

The worker count must be bounded and related to available CPU and workload
characteristics. More workers are not automatically faster when tests compete
for filesystem or SQLite resources.

JUnit in-process parallel execution is a later optimization. It increases the
importance of thread-safe fixtures and process-wide state audits, so it must not
be enabled repository-wide before those contracts are satisfied.

Serial-only tests must remain a small, explicit set.

### Current Gradle verification tasks

The default `test` task is intentionally kept at one Gradle test worker while
the serial baseline and audit are established.

The repository provides these TEST-PERF tasks:

```text
testArchitectureAudit
testParallelProbe
testSerial
testIntegration
testConcurrency
testGui
```

`testArchitectureAudit` writes detailed findings and a per-file summary under
`build/reports/test-performance/`.

`testParallelProbe` is an opt-in validation task. It excludes tests tagged
`serial` and uses bounded Gradle worker-process parallelism. The default probe
worker count is CPU-aware and capped conservatively; it may be overridden for a
controlled experiment with:

```powershell
.\gradlew.bat testParallelProbe -PtestParallelForks=2
```

The override must remain between 1 and 16. A higher number is not evidence of a
better configuration; representative timing and repeated deterministic runs
decide the final worker count.

`testSerial` executes only tests explicitly tagged `serial` and always uses
one Gradle test worker. A serial tag requires a concrete process-wide isolation
reason; it is not a substitute for fixing test-owned filesystem, SQLite,
threading, or cleanup defects.

`testIntegration`, `testConcurrency`, and `testGui` execute the corresponding
explicitly categorized subsets with one Gradle worker. They exist for focused
diagnosis and later stress campaigns. Because categories may overlap, their
combined test counts must not be treated as the size of the complete suite.

The normal PR correctness gate must not switch from `test` to
`testParallelProbe` until the parallel-safety audit and repeated validation
support that change.

## CI contract

Pull-request CI must preserve the complete correctness gate.

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
- serial-only labels used instead of fixing isolation.

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
- any serial-only requirement has a concrete documented reason;
- failure messages provide enough information for CI diagnosis;
- the change does not weaken the full quality gate.

## Rollout rule

Parallelism is introduced incrementally. At each checkpoint the suite must stay
under the same or stronger correctness contract.

The target state is 1100+ tests executing with controlled parallelism,
deterministically, without flaky behavior and without dependence on test order.
