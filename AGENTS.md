# VS Cartographer

## Purpose

VS Cartographer is a desktop application for offline analysis of Vintage Story save data.

The source save is authoritative input and must never be modified by the application.

Before substantial changes, inspect the current implementation, tests, build configuration, CI, and task-relevant documentation. Treat current repository state as discoverable truth; do not rely on historical snapshots.

## Non-negotiable safety rules

- Open source save data read-only.
- Never intentionally write to, migrate, repair, or mutate the user's source save.
- Treat missing, unknown, unsupported, and corrupt data as distinct from known data.
- Never invent world data to fill gaps.
- Keep binary parsing defensive and bounded.
- Isolate malformed rows, chunks, or records where safe instead of turning one local failure into broader corruption.
- Resolve game identifiers from authoritative save/registry data when available; do not hard-code unstable game IDs as truth.
- Prefer direct evidence from source data over heuristics or inference.
- Never present inferred or derived information as if it were a direct observation.

## Coordinate and domain semantics

- Coordinate spaces are distinct domain concepts.
- Make coordinate conversions explicit at boundaries.
- Never silently mix coordinate spaces.
- Preserve meaningful distinctions such as authoritative versus derived data, observed versus inferred data, and absent versus corrupt versus unknown data.
- Do not erase domain distinctions merely to reduce types or simplify plumbing.

## Architecture

Follow the durable architecture principles in `docs/ARCHITECTURE_PRINCIPLES.md`.

For production changes:

- preserve an acyclic dependency graph;
- preserve semantic ownership of concepts;
- keep dependency direction coherent with architectural responsibility;
- keep presentation concerns outside lower-level domain and infrastructure concerns;
- use explicit boundaries and adapters instead of hidden coupling;
- respect automated architecture constraints;
- do not bypass architecture rules through reflection, service locators, global state, forwarding wrappers, duplicated models, or generic dumping-ground packages.

If a requested change appears to require violating a fundamental architecture principle, surface that conflict explicitly instead of silently weakening the architecture.

## Change discipline

- Prefer the smallest coherent change that fully solves the requested problem.
- Do not bundle unrelated refactors.
- Preserve established behavior unless the task explicitly changes it.
- Place a type according to who owns its meaning, not according to where it happens to be used first.
- Introduce abstractions only for a real seam, substitution need, lifecycle boundary, or architectural responsibility.
- Do not add speculative framework layers for possible future use.
- Keep authoritative source access, parsing/decoding, interpretation, orchestration, persistence, and presentation responsibilities explicit.
- When current documentation conflicts with current executable behavior, investigate the discrepancy rather than choosing whichever version is more convenient.

## Validation and evidence

- Never claim that a build, test, benchmark, runtime workflow, or real-save validation passed unless it actually ran.
- Distinguish static review from runtime evidence.
- Use the repository's current build and CI configuration to discover authoritative validation commands.
- Preserve semantic test coverage; do not delete, disable, weaken, or bypass tests merely to obtain a green result.
- Treat failing architecture or quality guards as design feedback, not obstacles to route around.
- When runtime behavior is affected, validate at the level required by that behavior.
- When rendering or other visual output is affected, include visual inspection where the task requires it.

## Concurrency-test rules

- Synchronize concurrency tests on explicit lifecycle events.
- Do not use scheduler timing or sleep-based waiting as proof that another thread reached a state.
- Timed waits are deadlock guards, not correctness conditions.
- Make cleanup failure-safe and ensure test-owned threads, executors, files, sockets, and other resources terminate or close.
- Do not weaken ordering, callback, lifecycle, or cleanup assertions to make a flaky test pass.

## Repository knowledge

Keep this file as a durable contract, not a repository manual.

Do not add:

- source-tree snapshots;
- package, class, or file inventories;
- current dependency graphs;
- feature-completeness lists;
- roadmap, milestone, release, or next-step status;
- current issue, pull-request, commit, or branch status;
- current test counts, timings, performance numbers, or CI results;
- current parser capability inventories;
- current world/save-specific dimensions or values;
- mutable build or CI commands that can be discovered from repository configuration;
- implementation descriptions that merely restate today's code.

Put changing state in the place that owns it: source code, tests, build configuration, CI, generated reports, task-specific issues or pull requests, or focused documentation.

Only change this file when a fundamental project rule changes, not when ordinary implementation details change.
