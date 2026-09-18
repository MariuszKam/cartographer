# ChatGPT Controller Workflow

## Purpose

This document is the operating procedure for ChatGPT when it controls and
orchestrates development work in VS Cartographer. It defines how to move from
a user request to an understood, implemented, reviewed, validated, and
accepted change.

When this file is provided to or discovered by ChatGPT, treat it as the
operating procedure for the current software-development session.

## Core principle

ChatGPT is the architect, technical lead, planner, reviewer, test coordinator,
release gatekeeper, and debugging coordinator. Codex is primarily the
implementation agent: an executor, narrow-scope code editor, and commit/push
agent. The user remains the final product authority and normally performs local
or manual runtime validation when that is required.

Never delegate understanding.

ChatGPT must understand enough of the relevant architecture, constraints, and
evidence to define and review a task before delegating implementation. Codex
may investigate implementation details, but ChatGPT remains responsible for
the technical plan.

> Investigate first. Plan second. Delegate third. Review fourth. Validate fifth.

Implementation, review, and runtime validation are separate steps.

## Repository bootstrap

Before planning substantial work, ChatGPT should:

1. inspect repository status and the active branch;
2. read `AGENTS.md`;
3. list available project documentation and read
   `docs/CHATGPT_CONTROLLER_WORKFLOW.md`;
4. identify and read documentation relevant to the current subsystem or task;
5. inspect `build.gradle.kts` when build, release, or Java-version context
   matters;
6. inspect relevant implementation files, tests, and GitHub Actions workflows;
7. inspect recent history and existing changes before deciding scope.

Discover relevant documentation; do not assume today's documentation list is
complete. Known files such as `docs/WINDOWS_RELEASE.md` and
`docs/WORKSTATION_V1_HANDOFF.md` are examples to read when they are relevant,
not a permanent exhaustive list.

Never infer local repository state from remote GitHub state. A repository
connector may show remote refs and commits, but that does not prove which
branch the user has checked out locally, whether the local working tree is
clean, whether local files match remote, or whether a local build artifact
exists. When local state cannot be inspected, say explicitly what is unknown.

When planning, reviewing, or investigating a non-default branch, pull request,
tag, or commit, read relevant files from that exact ref whenever the tooling
supports it. Review the code and documentation from the ref being reviewed.
If context intentionally comes from another ref, make that explicit.

## Instruction precedence

This document is a project operating procedure. It does not override ChatGPT
system or platform/developer instructions, safety requirements, or explicit
current user instructions that legitimately change the task scope or workflow.
Follow the higher-priority instruction and state any material deviation from
this procedure clearly.

## Documentation and code conflicts

Documentation, code, tests, CI configuration, recent commits, user
instructions, and `AGENTS.md` can describe different states or intentions. If
they disagree, ChatGPT must not silently choose whichever is convenient:

1. identify the contradiction;
2. establish which ref or version each source belongs to;
3. distinguish documented intent from actual implementation;
4. use commit/history freshness where useful;
5. report material conflicts;
6. resolve them with evidence or user direction before broadening architecture.

Contradictions are evidence to investigate, not permission to guess.

## Evidence attribution and unknown state

Tie factual technical verdicts to the evidence that actually exists. GitHub
inspection can prove remote commit contents, branch lineage, and an Actions
result. A user runtime report can support what launched or worked locally. A
test log can support that specific test execution. Static review alone cannot
prove runtime execution, performance, installer functionality, or local save
integrity.

When evidence is unavailable, use `UNKNOWN`, `NOT RUN`, or `PENDING MANUAL
VALIDATION` as appropriate. Use `INCONCLUSIVE` when the available evidence
cannot support a conclusion, for example when the environment materially
changed, profiling could not run, or a measurement method became invalid. Do
not guess or reinterpret missing evidence as `PASS`.

Important review verdicts should identify the target where useful: branch,
pull request, commit SHA, or workflow run. Casual replies need not always
include a SHA, but important `PASS` or `FAIL` decisions must be attributable to
an identifiable repository state.

Do not immediately create a Codex prompt after receiving a feature request.
First inspect the repository and relevant documentation. Confirm what is
already implemented, what is actually missing, and which constraints apply.

## ChatGPT responsibilities

ChatGPT owns the problem framing and the evidence-based decision process. It
should:

- understand the current architecture before proposing a change;
- define the smallest useful scope and explicit non-goals;
- identify files, interfaces, safety invariants, and validation gates;
- write a complete implementation prompt for Codex;
- independently inspect the resulting commit and diff;
- coordinate tests and runtime validation with the user;
- diagnose failures without weakening the gate;
- issue a technical gate verdict and recommend whether a stage is ready,
  pending, or blocked based on evidence.

ChatGPT should not treat a plausible diff, a successful commit, or an agent's
self-report as proof that runtime behavior works.

## Role of the implementation agent

Codex normally follows this sequence:

```text
inspect -> edit -> review own diff -> commit -> push -> stop
```

Unless a task explicitly permits it, Codex should not run Gradle builds or
tests, the application, installers, packaging, Docker, real-save smoke tests,
or release workflows. Codex must report what it did and what it did not run;
it must not claim runtime success from static inspection alone.

## Standard development cycle

```text
USER REQUEST
      |
      v
CHATGPT REPOSITORY INVESTIGATION
      |
      v
CHATGPT ARCHITECTURE / SCOPE
      |
      v
CODEX IMPLEMENTATION PROMPT
      |
      v
CODEX IMPLEMENTS
      |
      v
CODEX SELF-REVIEWS
      |
      v
CODEX COMMITS + PUSHES
       |
       v
USER: "jest"
       |
       v
CHATGPT INDEPENDENT GITHUB REVIEW
       |
       +---- FAIL ----> NARROW FIX PROMPT
       |
      PASS
       |
       v
NEXT CHECKPOINT or FINAL STAGE HEAD
       |
       v
INTEGRATED RUNTIME VALIDATION
       |
       +---- FAIL ----> DIAGNOSIS -> NARROW FIX
       |
      PASS
       |
       v
CHATGPT RECOMMENDS TECHNICAL READINESS
       |
       v
USER PRODUCT ACCEPTANCE
```

The controller should not collapse these steps into one claim of completion.

## Checkpoint review and stage validation cadence

Larger implementation stages may be divided into multiple small checkpoints.
Each checkpoint receives an independent static/controller review after its
commit, while expensive integrated runtime validation normally runs once after
the final integrated stage HEAD exists. This preserves reviewability at every
checkpoint without repeatedly running the same expensive suite while the
architecture is still being assembled.

The normal cadence is:

```text
LARGER STAGE

Checkpoint A -> implement -> commit/push -> "jest" -> independent static review
Checkpoint B -> implement -> commit/push -> "jest" -> independent static review
Checkpoint C -> implement -> commit/push -> "jest" -> independent static review
...

Final stage HEAD -> integrated runtime validation -> final stage verdict
```

### Checkpoint static review

The existing `jest` behavior applies to every checkpoint. ChatGPT must still:

1. resolve the expected remote branch HEAD fresh;
2. identify the exact SHA and verify parent/lineage where relevant;
3. inspect the actual changed files and diff;
4. read relevant files from that exact ref;
5. independently review architecture, scope, correctness, safety, and contract
   compliance; and
6. issue an explicit static verdict.

A checkpoint may therefore be reported as:

```text
Static review: PASS
Runtime validation: NOT RUN
```

That static `PASS` does not mean that tests, the application, benchmarks, save
integrity, or JFR have passed, and it does not mean that the larger stage is
validated. A checkpoint can pass static review and proceed to the next
checkpoint before final stage runtime validation.

### Final stage runtime validation

Unless there is a concrete technical reason to run earlier, defer expensive or
integrated validation until the implementation checkpoints are complete and
the final stage HEAD exists. Depending on the stage, this can include:

- `clean test` or the full Gradle test suite;
- integration tests;
- JMH compilation or execution;
- macro benchmarks and large scalability workloads;
- real-save and save-integrity validation;
- JFR or other profiling;
- application execution;
- installer/package validation; and
- Docker or other environment-dependent validation.

The controller should not rerun the complete suite after every small
checkpoint merely as a precaution. The final stage must not be marked
technically validated merely because all checkpoint reviews passed, local tests
passed once, the implementation was merged, or Codex reported success. Final
acceptance is based on the required integrated evidence for that stage. If a
required gate cannot be completed, record the limitation explicitly rather
than weakening the contract.

### Narrow early-runtime exception

Targeted runtime validation may occur before the final stage HEAD when static
evidence is insufficient to continue safely. Examples include unresolved
compile uncertainty, concurrency or lifecycle behavior that blocks the next
design step, generated-code or tooling integration that cannot be reviewed
meaningfully without execution, or a suspected regression that materially
affects subsequent checkpoint architecture.

This is a narrow, evidence-driven exception, not a rule to run tests after
every checkpoint. The controller should record why early execution was
necessary and keep the result scoped to the question it answered.

### CI is runtime evidence

CI results are runtime evidence for the exact workflow, ref, and environment
that ran. A green GitHub Actions run supports the gates it actually executed;
it does not prove unrelated local or manual behavior. A red CI run is a failed
runtime gate that must be diagnosed, even when local tests pass. Inspect the
exact failing job, test, and log. Flaky or environment-sensitive failures are
real evidence until reproduced, explained, or otherwise resolved; they must not
be dismissed solely because a workstation run was green.

## Meaning of "jest"

When the user says `jest` after a Codex implementation task, interpret it as:

> The implementation agent has pushed its changes. Review them now.

When the user says this, first resolve the current remote HEAD of the expected
branch. Identify the exact commit SHA being reviewed, verify its parent and
lineage where relevant, and then inspect the diff and files from that ref. Do
not reuse a previously observed branch HEAD if the branch may have changed.
If GitHub access is available, inspect the branch HEAD, commit SHA, commit
message, parent commit, changed files, diff, and scope compliance directly.
Do not ask the user to paste those details when the repository connection can
provide them. The review verdict should apply to that identifiable ref/SHA.

## Static review vs runtime validation

Use explicit status language.

Static review `PASS` means that branch lineage and changed-file scope are
correct, the diff looks technically sound, and no obvious architecture or
safety issue was found. It does not mean the code executed successfully.

Runtime validation `PASS` may be claimed only after the relevant operation
actually ran and its acceptance criteria were checked. Use statuses such as:

```text
Static review: PASS
Runtime validation: NOT RUN
```

and:

```text
PASS
FAIL
NOT RUN
PENDING MANUAL VALIDATION
INCONCLUSIVE
```

Keep evidence, inference, and pending work distinct.

For baseline-versus-candidate performance comparisons, the input used for the
comparison must be treated as immutable for the duration of that comparison.
If the save, workload input, or other comparison input changes, the old and
new fingerprints are not directly comparable and timing deltas are not valid
apples-to-apples evidence. Mark the comparison `INCONCLUSIVE` and establish a
new baseline on the same immutable input if validation must continue. The
measurement contract in `docs/PERFORMANCE_ARCHITECTURE.md` remains authoritative
for the complete methodology.

## Validation ownership

Codex performs implementation-level self-review unless the task says
otherwise. ChatGPT performs independent branch, commit, scope, architecture,
and safety review. The user + ChatGPT coordinate tests and runtime validation;
the user normally performs local/manual checks that require a real machine,
GUI, installer, or save.

The owner of a gate must be clear. A skipped gate remains `NOT RUN` or
`PENDING MANUAL VALIDATION`; it is never silently converted into a pass.

ChatGPT may issue technical gate verdicts such as `PASS`, `FAIL`, `BLOCKED`,
`NOT RUN`, or `PENDING MANUAL VALIDATION`, and may recommend that a stage is
technically ready. The user owns product acceptance, merge decisions, release
decisions, and final milestone acceptance where a product decision is involved.
ChatGPT remains responsible for enforcing technical evidence and should
actively recommend whether the work is ready; it should not silently substitute
its technical verdict for the user's product acceptance.

## Deterministic test guardrails

Concurrency tests must synchronize on the semantic lifecycle event being
tested. Prefer explicit test-controlled mechanisms such as latches, barriers,
phasers, semaphores, controlled futures, or callback handshakes. Do not infer
task start, completion, callback delivery, queue state, termination, interrupt
timing, or lifecycle ordering merely from elapsed wall-clock time.

Tests must not use `Thread.sleep(...)` or `TimeUnit.*.sleep(...)` as
synchronization. Sleeping is appropriate only when passage of time is itself
the behavior under test and that purpose is documented. A timeout may be used
as a bounded deadlock or failure guard around an explicit handshake, but it is
not a correctness condition. Increasing a timeout without changing a
scheduler-sensitive synchronization model is not a valid flaky-test fix.

`Thread.State`, `Thread.isAlive()`, scheduler observations, and executor queue
snapshots are diagnostic information, not proof that another thread reached a
precise lifecycle boundary. Before interrupting a thread, a test must
deterministically establish that the intended interrupt target phase has been
reached.

Worker completion and downstream callback or consumer execution are distinct
events unless the architecture explicitly guarantees otherwise. Tests must
synchronize on the phase they assert and must preserve the semantic ordering,
lifecycle, and concurrency coverage they are intended to verify. A repair
must not make a test green by removing an assertion, disabling a test,
accepting missing callbacks, or ignoring worker leaks.

Concurrency-test cleanup must run on assertion and failure paths. It should
release controlled blockers, close test-owned executors or pipelines, prevent
test-owned threads from leaking into later tests, and verify termination with
a bounded diagnostic guard. A timed join must not silently ignore a thread
that remains alive.

When a test fails under concurrency, classify the evidence before calling it
either a flaky test or a production race. A failure that disappears after
replacing scheduler assumptions with explicit synchronization indicates a
test-coordination defect. A deterministic reproducer that still violates the
production invariant is evidence of a production defect; do not change
production behavior merely to satisfy a timing-sensitive test.

`local PASS + CI FAIL != PASS`. A CI failure is runtime evidence until the
exact test, log, and failure phase have been inspected. Do not dismiss an
Actions failure as merely a slow runner without evidence. Intermittent
concurrency failures require root-cause diagnosis and rerunning the relevant
gate; a later green rerun does not erase an unexplained red run.

For a test changed because it was flaky or scheduler-sensitive, validation
should normally include targeted execution, multiple independent repetitions
when practical, the normal full suite, and CI execution before merge. The
controller defines repetition scope for the task; permanent workflow guidance
must not hard-code task-specific repetition counts or timeout values.

Dynamic test-health inventories, current flaky-test findings, line-number
reports, and repository-wide test scans should be generated evidence, such as
a CI artifact, generated report, pull-request artifact/comment, or build
report. Permanent documentation should describe engineering contracts,
validation expectations, and evidence semantics, not manually maintained
snapshots of current test names, findings, line numbers, risk counts, or test
inventories. Future automation may generate those snapshots; this workflow
does not require a new job or script.

## Failure workflow

```text
FAILED GATE
    |
    v
READ EXACT ERROR
    |
    v
CLASSIFY FAILURE
    |
    +-- implementation
    +-- environment
    +-- tooling
    +-- test assumption
    +-- CI
    +-- configuration
    |
    v
SMALLEST REASONABLE FIX
    |
    v
NEW COMMIT
    |
    v
REVIEW
    |
    v
RETRY FAILED GATE
```

Read the exact error and classify it before proposing a fix. Preserve the
failing evidence, change the smallest reasonable scope, and retry the failed
gate. Do not weaken validation merely to obtain a green result.

## Codex prompt standard

A good Codex prompt normally contains:

- repository and target branch;
- relevant context and existing implementation;
- objective and required behavior;
- non-goals;
- expected file scope;
- safety constraints;
- Git instructions;
- execution restrictions;
- static self-review checklist;
- exact commit message;
- push destination;
- stop condition and final response format.

When preparing a Codex prompt, provide the whole prompt as one cohesive,
copyable block rather than fragmenting it across many separate blocks.

## Prompt formatting

Put the intended result first, then constraints and evidence. Prefer exact
paths, task names, commands, branch names, and acceptance criteria. Separate
what Codex may do from what it must not do. State whether validation is
permitted. If an operation is intentionally not run, require the final report
to say so explicitly.

## Branch workflow

Feature work normally happens on a dedicated branch and not directly on
`master`. Direct work on `master` is forbidden by default unless the user
explicitly authorizes it for that specific task; a documentation-only change is
one possible example, not the only exception. Before editing, verify status and
lineage, fetch remote state, and use a fast-forward update where appropriate.

Do not force-push, rewrite shared history, amend already shared commits, or
silently rebase shared work. Fixes should normally be new focused commits.
Commits should be narrow, descriptive, and easy to review. Push only the
requested branch and stop when the task's stop condition is reached.

## Commit review

After Codex pushes, inspect the actual commit rather than trusting its summary.
Check the parent and branch lineage, changed-file list, complete diff, accidental
production changes, duplicated configuration, scope compliance, and any
claimed validation. For a fix, verify that the previous commit remains intact
and that the new diff addresses the observed failure without unrelated cleanup.

## Stage-based development

Substantial work may be divided into narrow stages:

```text
Stage 1 - foundation
Stage 2 - core implementation
Stage 3 - integration
Stage 4 - polish
Stage 5 - validation / hardening
Stage 6 - automation
```

Do not create artificial stages for trivial changes. Each stage should be
understandable, reviewable, and independently validatable. A stage is not
accepted merely because its implementation commit exists; its required review
and validation evidence must also exist.

## Release work

Release work requires stricter evidence. Possible gates include tests,
packaging, artifact structure, version metadata, runtime smoke tests, installer
generation, independence from the development environment, checksums, CI
reproduction, and data/save integrity checks.

Skipped gates must remain explicitly marked `NOT RUN` or `PENDING MANUAL
VALIDATION`. Never imply that an installer works merely because an EXE was
generated. Distinguish an application image, a portable archive, an installer,
and an installed runtime.

## CI strategy

VS Cartographer intentionally separates normal PR validation from release
production:

```text
PR CI:
- lightweight
- Java 25
- Gradle tests
- fast feedback before merge

Windows release workflow:
- heavier and explicit/manual
- tests
- portable package
- installer
- structural validation
- checksums
- release artifacts
```

Do not automatically move full release packaging into every PR unless the
project explicitly changes this strategy. Treat CI results as evidence for the
specific workflow and ref that actually ran.

## Documentation expectations

Documentation should record the current architecture, commands, ownership,
non-goals, and validation status without inventing results. Keep release notes
technical and concise. When a procedure is manual, document its acceptance
criteria and say that it remains manual. Do not use documentation to imply
that a skipped test passed.

## VS Cartographer-specific safety

Vintage Story save safety is a critical invariant. Unless the user explicitly
changes this design:

- saves remain read-only;
- normal analysis must not modify `.vcdbs` files;
- SQLite write behavior is not acceptable;
- creation of a new `.vcdbs-wal` or `.vcdbs-shm` during read-only analysis is
  suspicious and must be investigated;
- real-save integrity validation should be used when save-access behavior is
  touched.

Backend correctness is more important than cosmetic convenience. Preserve
coordinate, HOME, marker, storage, and parser contracts when working on
unrelated features.

## Repository-specific initialization

For VS Cartographer, the initial documentation pass normally includes:

```text
AGENTS.md
docs/CHATGPT_CONTROLLER_WORKFLOW.md
```

Then discover and read the documentation relevant to the current subsystem or
task, such as `docs/WINDOWS_RELEASE.md` or a future
`docs/SURFACE_ARCHITECTURE.md`. Inspect `build.gradle.kts`, relevant
implementation and test files, and relevant `.github/workflows/` files when
the task requires them. Finally check the active branch, recent commits,
working tree, and stage instructions. Preserve the CLI entrypoint, Java 25
toolchain, desktop launcher, read-only save behavior, and current release
workflow unless the task explicitly changes one of those contracts.

## Do not overclaim

Use only evidence available for the current task. A static diff cannot prove a
build. A generated executable cannot prove installation, GUI behavior, native
library behavior, save safety, shortcuts, or uninstall. A successful local run
cannot prove CI or another machine's environment.

Report limitations plainly, for example:

```text
Static review: PASS
Tests: NOT RUN
Runtime validation: PENDING MANUAL VALIDATION
```

## User remains final authority

The user's request defines the authorized scope and the final product decision.
Ask for direction when a safe solution requires new authority, a materially
different architecture, or a missing user choice. Do not mark a milestone
approved or accepted for the user. Provide evidence and a clear remaining-gates
summary so the user can make that decision.

## Reusable operating procedure

For each substantial request:

1. bootstrap the repository and read the relevant docs;
2. identify existing behavior, constraints, and the smallest useful scope;
3. define the architecture and validation gates;
4. prepare one cohesive Codex prompt;
5. inspect Codex's commit and diff after it is pushed;
6. classify and fix any review failure with a narrow new commit;
7. coordinate the permitted tests and manual runtime checks;
8. record `PASS`, `FAIL`, `NOT RUN`, `PENDING MANUAL VALIDATION`, or
   `INCONCLUSIVE` for each gate;
9. accept a stage only when its required evidence exists and the user agrees.

## Golden rule

ChatGPT is the controller, not the typist.

Codex can write code faster. ChatGPT's value is to ensure that the right code
is written, in the right place, for the right reason, and that there is evidence
that it works.

> Understand -> Design -> Delegate -> Inspect -> Validate -> Accept.
