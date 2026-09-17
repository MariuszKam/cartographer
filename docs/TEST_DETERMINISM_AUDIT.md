# Test Determinism Audit

## Scope

- Starting master SHA: `10d7f7b856963e9a2b83c58bc873ac414c82bae4`
- Branch: `test/deterministic-test-hardening`
- Audit date: `2026-09-17`
- Test root inspected: `src/test/` (all 119 Java test sources)
- Relevant configuration inspected: `build.gradle.kts`, `.github/workflows/pr-ci.yml`
- Relevant documentation inspected: `AGENTS.md`, `docs/CHATGPT_CONTROLLER_WORKFLOW.md`, `docs/PERFORMANCE_FOUNDATION.md`, `docs/PF_1_1_DECODE_MEMORY_MODEL.md`, `docs/PF_1_2_STREAMING_PROCESSING_ENGINE.md`, `docs/WINDOWS_RELEASE.md`, and `docs/WORKSTATION_V1_HANDOFF.md`
- CI context: pull requests run the JUnit platform test task on `windows-latest` with Java 25.

## Methodology

Static inspection only. The complete `src/test/` tree, test fixtures/helpers/resources, JUnit/Gradle configuration, CI workflow, and relevant concurrency/performance documentation were inspected with repository-local file enumeration and text searches. No tests were executed.

## Executive summary

Total test source files: **119**

Files with confirmed risk: **1**

Files with likely risk: **2**

Files with possible risk: **0**

Files reviewed as safe: **116**

The file-level counts classify each unique file once using its highest-risk finding classification; therefore the pipeline test file is counted under confirmed risk even though it also contains likely findings. The finding-level counts below count individual findings.

Finding counts:

- HIGH: 3
- MEDIUM: 2
- LOW: 0

Classification counts:

- CONFIRMED RISK: 3
- LIKELY RISK: 2
- POSSIBLE RISK: 0

The audit did not identify a definite production concurrency defect. The high findings are test synchronization risks. Runtime confirmation remains pending Checkpoint B; this is a static audit, not a flake reproduction exercise.

## Confirmed CI evidence

Recent GitHub Actions runs reported different failures from `cartographer.save.BoundedStreamingDecodePipelineTest` despite local green runs. Reported methods were:

- `closeCancelsRunningAndQueuedWorkWithoutCallbacks()`
- `interruptedCloseRestoresInterruptAfterWorkerQuiescence()`
- `interruptedCompletionWaitRestoresInterruptStatusAndAborts()`
- `completedResultRetainsCapacityUntilConsumerReturns()`
- `fatalWorkerFailureStopsCallbacksAfterFailureBoundary()`

This evidence confirms that the class contains scheduler-sensitive assumptions. It does not, by itself, establish a production race.

## Findings

### TD-001 — Completion callback is observed without a callback handshake

Classification: **CONFIRMED RISK**  
Severity: **HIGH**

File: `src/test/java/cartographer/save/BoundedStreamingDecodePipelineTest.java`  
Lines / methods: 48, 82; `laterCompletionIsConsumedBeforeSlowFirstTask()`, `newSubmissionDoesNotWaitForSlowOldestTask()`

Pattern:

```java
pipeline.submit(() -> 3);
assertTrue(values.contains(2));
assertFalse(values.contains(1));
```

Why this can be flaky: the latches prove that worker tasks started/returned, but they do not prove that the control thread has completed the consumer callback. `submit()` may return before the callback has become visible to this assertion, or a different completion may be consumed first. The test therefore uses incidental scheduler progress to infer completion order. This is directly relevant to the observed `completedResultRetainsCapacityUntilConsumerReturns()` / completion-boundary failures in the same class.

Observed evidence: different CI runs fail in this class while local runs are green; the class is the known source of nondeterminism.

Recommended remediation: add a callback-controlled latch or equivalent explicit handshake for the expected value, and assert the boundary only after that handshake. Keep a bounded timeout only as a deadlock guard.

Production-code change required: **NO**

### TD-002 — Interrupt is injected after a thread-state polling heuristic

Classification: **CONFIRMED RISK**  
Severity: **HIGH**

File: `src/test/java/cartographer/save/BoundedStreamingDecodePipelineTest.java`  
Lines / methods: 1079-1088 helper used by `interruptedCloseRestoresInterruptAfterWorkerQuiescence()` (705-762) and `interruptedCompletionWaitRestoresInterruptStatusAndAborts()` (932-986)

Pattern:

```java
awaitThreadState(controller, Thread.State.WAITING, Thread.State.TIMED_WAITING);
controller.interrupt();
```

Why this can be flaky: `Thread.State` is a coarse, transient observation. `WAITING`/`TIMED_WAITING` does not prove that the controller reached the intended `Future.get`/completion wait rather than another wait or cleanup point. The polling loop also consumes real scheduler time and can miss the intended transition under load. Interrupt delivery can consequently target the wrong lifecycle phase.

Observed evidence: the two interrupt/lifecycle methods are among the CI-reported failures from this class.

Recommended remediation: expose a test-controlled handshake at the exact blocking boundary, or arrange the test so the controller signals readiness immediately before entering the operation under test. Use thread-state polling only as diagnostic failure context, not as the correctness synchronization.

Production-code change required: **NO**

### TD-003 — Timed worker-start gates can fail before a valid concurrent operation starts

Classification: **CONFIRMED RISK**  
Severity: **HIGH**

File: `src/test/java/cartographer/save/BoundedStreamingDecodePipelineTest.java`  
Lines / methods: multiple 1-second awaits, especially 40-41, 77-79, 145, 217-218, 264, 311-312, 377, 405, 454-455, 508-509, 574-576, 627-628, 688-692, 746-748, 800-804, 902, 925, 971-976

Pattern: worker and controller progress is required to occur within `await(1, TimeUnit.SECONDS)`, followed by lifecycle assertions or interruption.

Why this can be flaky: these waits are not all merely cleanup guards. They establish that workers have started, that a result has reached a boundary, or that a controller is in the phase to be interrupted. A loaded Windows CI runner can schedule a correct worker/controller later than one second. Several failure paths also use bounded cleanup joins in `finally`, so an early assertion failure can leave a deliberately blocked controller/worker until later test teardown.

Observed evidence: the class has produced varying CI failures across lifecycle and completion tests, including `closeCancelsRunningAndQueuedWorkWithoutCallbacks()`, `interruptedCloseRestoresInterruptAfterWorkerQuiescence()`, `interruptedCompletionWaitRestoresInterruptStatusAndAborts()`, and `fatalWorkerFailureStopsCallbacksAfterFailureBoundary()`.

Recommended remediation: replace correctness-bearing one-second gates with explicit handshakes tied to the exact state transition under test. Retain a longer, centralized timeout only as a failure/deadlock guard and guarantee release/cleanup in all assertion-failure paths.

Production-code change required: **NO**

### TD-004 — Reader concurrency tests use one-second worker-start gates without failure-safe release

Classification: **LIKELY RISK**  
Severity: **MEDIUM**

Files: `src/test/java/cartographer/save/VcdbsReaderDirectChunkLookupTest.java`, lines 289 and 326; `src/test/java/cartographer/save/VcdbsReaderSelectiveChunkLookupTest.java`, line 220  
Methods: `tableStreamDecodeStillRunsConcurrently()`, `parallelDecodeOverlapsWhileConsumerRemainsCallerThread()`, `selectiveDecodeWorkRunsConcurrently()`

Pattern: `assertTrue(parser.bothStarted.await(1, TimeUnit.SECONDS));` followed by `parser.release.countDown()` and an unbounded `caller.join()`.

Why this can be flaky: the timeout is a correctness-bearing assertion that both executor workers must start within one second. If it expires on a loaded runner, the release signal is skipped and the caller remains blocked in the parser; the subsequent unbounded join can hang the test process or delay suite cleanup. The worker-count assertions are safe after the caller join; the start gate is the risk.

Observed evidence: static inspection only; no runtime reproduction was performed.

Recommended remediation: use an explicit test handshake with a suite-level deadlock guard and put release/join cleanup in `finally`, so an unavailable worker start cannot strand blocked executor work.

Production-code change required: **NO**

### TD-005 — Bounded thread joins are used as cleanup, but not consistently asserted

Classification: **LIKELY RISK**  
Severity: **MEDIUM**

File: `src/test/java/cartographer/save/BoundedStreamingDecodePipelineTest.java`  
Lines / methods: 229, 274, 325, 522, 588, 637, 699, 760, 810, 984 and related `finally` blocks

Pattern: `control.join(1000)` / `controller.join(1000)` in cleanup, sometimes after an earlier unbounded join on the success path.

Why this can be flaky: the bounded joins are not direct correctness assertions, but they can mask a stranded non-daemon test thread when an earlier scheduler-sensitive assertion fails. The suite may then report a later unrelated failure, hang, or be affected by surviving worker/controller activity. This is a cleanup/reproducibility risk rather than proof that the pipeline implementation is incorrect.

Observed evidence: static inspection only; no runtime reproduction was performed.

Recommended remediation: centralize failure-safe cleanup, signal every release latch before joining, and assert termination with a diagnostic timeout after cleanup rather than silently ignoring an alive thread.

Production-code change required: **NO**

## Production concurrency assessment

No possible production concurrency defect was identified by this static audit. The high findings are **TEST SYNCHRONIZATION RISK**: the tests do not always establish the exact lifecycle boundary before observing or interrupting it. The implementation contract in `docs/PF_1_2_STREAMING_PROCESSING_ENGINE.md` does leave callback order unspecified with respect to submission order, so completion-order assertions should be synchronized at the callback boundary. Checkpoint B should separately inspect any runtime failure that remains after deterministic test handshakes; evidence distinguishing a production defect would be a reproducible invariant violation with the test's handshakes established, not merely a timeout or thread-state mismatch.

## Safe patterns observed

- `CountDownLatch` handshakes are used extensively to coordinate worker start, callback entry, release, and interruption observation.
- `awaitUninterruptibly` preserves interrupt status after deterministic release; it is used intentionally for worker-quiescence fixtures.
- `awaitWorkerTermination` uses `System.nanoTime()` only as a bounded deadlock guard and restores interruption; it is not used to assert a performance threshold.
- Completion results are compared as sets where the pipeline contract explicitly makes delivery order unspecified.
- `System.nanoTime()` in reader tests is used only to create unique temporary database names.
- `@TempDir` isolates filesystem fixtures per test. Temporary paths are repository-agnostic and do not rely on listing order, timestamps, open-handle deletion, or default encoding.
- Performance tests inject/manualize time through fake recorder clocks rather than asserting machine elapsed time. `MacroBaselineEnvironmentTest` only checks the invariant that available processors is positive.
- Seeded/static fixture data is used; no unseeded `Random`, `ThreadLocalRandom`, `SecureRandom`, `Math.random()`, `UUID.randomUUID()`, or shuffle-based assertion was found.
- No fixed ports, sockets, network access, default locale/timezone/charset assertions, system-property mutation, environment-variable dependency, or JUnit method-order dependency was found.
- SQLite fixtures close connections/statements before assertions and use primary-key/request-key semantics where result order is not treated as a contract.

## Full test inventory

All 119 files under `src/test/` were enumerated. Files not listed in Findings are marked SAFE after the category searches and source-context review.

| Status | File |
|---|---|
| SAFE | `src/test/java/cartographer/application/AnalyzeProspectingAreaUseCaseTest.java` |
| SAFE | `src/test/java/cartographer/application/DiscoverObservedSurfaceResourcesUseCaseTest.java` |
| SAFE | `src/test/java/cartographer/application/MapChunkPositionPlannerTest.java` |
| SAFE | `src/test/java/cartographer/application/MapChunkRenderWindowPlannerTest.java` |
| SAFE | `src/test/java/cartographer/application/OreChunkPositionPlannerTest.java` |
| SAFE | `src/test/java/cartographer/application/RenderActualOreMapUseCaseTest.java` |
| SAFE | `src/test/java/cartographer/application/RenderCoverageMapUseCaseTest.java` |
| SAFE | `src/test/java/cartographer/application/RenderRockMapRequestTest.java` |
| SAFE | `src/test/java/cartographer/application/RenderSurfaceResourceMapUseCaseTest.java` |
| SAFE | `src/test/java/cartographer/application/SurfaceDiscoveryCacheTest.java` |
| SAFE | `src/test/java/cartographer/application/SurfaceDiscoveryPolicyTest.java` |
| SAFE | `src/test/java/cartographer/application/SurfaceDiscoveryRequestGateTest.java` |
| SAFE | `src/test/java/cartographer/application/SurfaceMaterialMatchTest.java` |
| SAFE | `src/test/java/cartographer/application/SurfaceResourceSelectionTest.java` |
| SAFE | `src/test/java/cartographer/cli/CoverageCommandTest.java` |
| SAFE | `src/test/java/cartographer/cli/GeologyCommandPlayerCenteredSectionTest.java` |
| SAFE | `src/test/java/cartographer/cli/GeologyCommandSectionTest.java` |
| SAFE | `src/test/java/cartographer/cli/MapCommandTest.java` |
| SAFE | `src/test/java/cartographer/cli/MarkerCommandTest.java` |
| SAFE | `src/test/java/cartographer/cli/RockCommandTest.java` |
| SAFE | `src/test/java/cartographer/cli/ScanCommandBlocksMapTest.java` |
| SAFE | `src/test/java/cartographer/coverage/RegionCoverageAnalyzerTest.java` |
| SAFE | `src/test/java/cartographer/coverage/RegionCoverageRendererTest.java` |
| SAFE | `src/test/java/cartographer/geology/RockStrataAnalyzerTest.java` |
| SAFE | `src/test/java/cartographer/geology/crosssection/GeologyCrossSectionAnalyzerTest.java` |
| SAFE | `src/test/java/cartographer/geology/rock/RockAtYScannerTest.java` |
| SAFE | `src/test/java/cartographer/geology/rock/RockCodeResolverTest.java` |
| SAFE | `src/test/java/cartographer/geology/rock/RockColumnScannerTest.java` |
| SAFE | `src/test/java/cartographer/marker/MarkerStoreTest.java` |
| SAFE | `src/test/java/cartographer/model/CoordinateTest.java` |
| SAFE | `src/test/java/cartographer/model/DecodedChunkLayerTest.java` |
| SAFE | `src/test/java/cartographer/model/MapChunkTest.java` |
| SAFE | `src/test/java/cartographer/model/ParsedChunkTest.java` |
| SAFE | `src/test/java/cartographer/model/WorldMetadataTest.java` |
| SAFE | `src/test/java/cartographer/navigation/DirectionCalculatorTest.java` |
| SAFE | `src/test/java/cartographer/navigation/HomeStoreTest.java` |
| SAFE | `src/test/java/cartographer/parser/BlockScannerTest.java` |
| SAFE | `src/test/java/cartographer/parser/ChunkPaletteProbeTest.java` |
| SAFE | `src/test/java/cartographer/parser/ChunkParserTest.java` |
| SAFE | `src/test/java/cartographer/parser/GeologyAnalyzerTest.java` |
| SAFE | `src/test/java/cartographer/parser/IncrementalRenderIndexTest.java` |
| SAFE | `src/test/java/cartographer/parser/MapChunkParserTest.java` |
| SAFE | `src/test/java/cartographer/parser/MarkerStoreTest.java` |
| SAFE | `src/test/java/cartographer/parser/PlayerDataParserTest.java` |
| SAFE | `src/test/java/cartographer/parser/RegistryParserTest.java` |
| SAFE | `src/test/java/cartographer/parser/RenderCacheTest.java` |
| SAFE | `src/test/java/cartographer/parser/SaveGameParserTest.java` |
| SAFE | `src/test/java/cartographer/parser/SurfaceScannerTest.java` |
| SAFE | `src/test/java/cartographer/parser/TilePyramidTest.java` |
| SAFE | `src/test/java/cartographer/perf/baseline/ReferenceBaselineFactoryTest.java` |
| SAFE | `src/test/java/cartographer/perf/benchmark/BenchmarkRunnerTest.java` |
| SAFE | `src/test/java/cartographer/perf/comparison/RegressionComparatorTest.java` |
| SAFE | `src/test/java/cartographer/perf/fingerprint/FingerprintTest.java` |
| SAFE | `src/test/java/cartographer/perf/instrumentation/PerformanceRecorderTest.java` |
| SAFE | `src/test/java/cartographer/perf/jfr/JfrBenchmarkProfilerTest.java` |
| SAFE | `src/test/java/cartographer/perf/macro/MacroBaselineEnvironmentTest.java` |
| SAFE | `src/test/java/cartographer/perf/macro/MacroBaselineRunnerTest.java` |
| SAFE | `src/test/java/cartographer/perf/macro/MacroWorkloadResolverTest.java` |
| SAFE | `src/test/java/cartographer/perf/metrics/PerformanceMetricsTest.java` |
| SAFE | `src/test/java/cartographer/perf/report/BaselineReportRendererTest.java` |
| SAFE | `src/test/java/cartographer/perf/safety/RealSaveValidationRunnerTest.java` |
| SAFE | `src/test/java/cartographer/perf/safety/SaveSafetyGateTest.java` |
| SAFE | `src/test/java/cartographer/perf/workload/WorkloadCatalogTest.java` |
| SAFE | `src/test/java/cartographer/prospecting/ProspectingEvaluatorTest.java` |
| SAFE | `src/test/java/cartographer/render/ActualBlockMapRendererTest.java` |
| SAFE | `src/test/java/cartographer/render/ActualOreOverlayPainterTest.java` |
| SAFE | `src/test/java/cartographer/render/ArgbRasterTest.java` |
| SAFE | `src/test/java/cartographer/render/DenseHeightGridTest.java` |
| SAFE | `src/test/java/cartographer/render/GeologyCrossSectionRendererTest.java` |
| SAFE | `src/test/java/cartographer/render/MapRendererTest.java` |
| SAFE | `src/test/java/cartographer/render/MapTerrainPreparationTest.java` |
| SAFE | `src/test/java/cartographer/render/MapViewportGeometryTest.java` |
| SAFE | `src/test/java/cartographer/render/OreOverlayPaletteTest.java` |
| SAFE | `src/test/java/cartographer/render/RenderLayerTest.java` |
| SAFE | `src/test/java/cartographer/render/RockMapRendererTest.java` |
| SAFE | `src/test/java/cartographer/render/SemanticMapRendererTest.java` |
| SAFE | `src/test/java/cartographer/render/SoilFertilityOverlayRendererTest.java` |
| SAFE | `src/test/java/cartographer/render/SurfaceObjectColorPolicyTest.java` |
| SAFE | `src/test/java/cartographer/render/SurfaceObjectMarkerStylePolicyTest.java` |
| SAFE | `src/test/java/cartographer/render/SurfaceOverlayLegendTest.java` |
| SAFE | `src/test/java/cartographer/resource/ObservedSurfaceResourceCatalogTest.java` |
| SAFE | `src/test/java/cartographer/resource/SurfaceObjectAnalyzerTest.java` |
| SAFE | `src/test/java/cartographer/resource/SurfaceObjectCandidateCatalogTest.java` |
| SAFE | `src/test/java/cartographer/resource/SurfaceObjectCandidateResolverTest.java` |
| SAFE | `src/test/java/cartographer/resource/SurfaceObjectClassifierTest.java` |
| SAFE | `src/test/java/cartographer/resource/SurfaceObjectFilterTest.java` |
| SAFE | `src/test/java/cartographer/resource/SurfaceObjectPresentationTest.java` |
| SAFE | `src/test/java/cartographer/resource/SurfaceObjectSelectionAnalysisTest.java` |
| SAFE | `src/test/java/cartographer/save/ChunkDecodeWorkspacePoolTest.java` |
| SAFE | `src/test/java/cartographer/save/ChunkPosDecoderTest.java` |
| SAFE | `src/test/java/cartographer/save/ChunkPosEncoderTest.java` |
| SAFE | `src/test/java/cartographer/save/ReadDiagnosticsTest.java` |
| SAFE | `src/test/java/cartographer/save/VcdbsReaderDirectMapChunkLookupTest.java` |
| SAFE | `src/test/java/cartographer/scanner/ActualBlockMapScannerTest.java` |
| SAFE | `src/test/java/cartographer/scanner/MultiActualBlockMapScannerTest.java` |
| SAFE | `src/test/java/cartographer/scanner/OreCodeMatcherTest.java` |
| SAFE | `src/test/java/cartographer/scanner/RainHeightSurfacePlannerTest.java` |
| SAFE | `src/test/java/cartographer/scanner/RainHeightSurfaceScannerTest.java` |
| SAFE | `src/test/java/cartographer/scanner/RainHeightSurfaceTargetTest.java` |
| SAFE | `src/test/java/cartographer/scanner/SurfaceClassifierTest.java` |
| SAFE | `src/test/java/cartographer/scanner/SurfaceFallbackChunkPlannerTest.java` |
| SAFE | `src/test/java/cartographer/scanner/SurfaceFallbackMapChunksTest.java` |
| SAFE | `src/test/java/cartographer/scanner/SurfaceFastPathMergerTest.java` |
| SAFE | `src/test/java/cartographer/scanner/SurfaceObjectPlannerTest.java` |
| SAFE | `src/test/java/cartographer/scanner/SurfaceObjectScannerTest.java` |
| SAFE | `src/test/java/cartographer/soil/SoilFertilityClassifierTest.java` |
| SAFE | `src/test/java/cartographer/ui/OrePresetTest.java` |
| SAFE | `src/test/java/cartographer/ui/OreResourceResolverTest.java` |
| SAFE | `src/test/java/cartographer/ui/PlayerPositionServiceTest.java` |
| SAFE | `src/test/java/cartographer/ui/ResourceCatalogServiceTest.java` |
| SAFE | `src/test/java/cartographer/ui/SurfaceMaterialPresetTest.java` |
| SAFE | `src/test/java/cartographer/ui/workstation/MapCursorMappingTest.java` |
| SAFE | `src/test/java/cartographer/ui/workstation/MapViewportNavigationTest.java` |
| SAFE | `src/test/java/cartographer/ui/workstation/ObservedSurfaceResourceSelectionTest.java` |
| SAFE | `src/test/java/cartographer/ui/workstation/SurfaceMaterialOptionsTest.java` |
| SAFE | `src/test/java/cartographer/ui/workstation/SurfaceObjectDiscoveryStateTest.java` |
| FINDINGS TD-004 | `src/test/java/cartographer/save/VcdbsReaderDirectChunkLookupTest.java` |
| FINDINGS TD-004 | `src/test/java/cartographer/save/VcdbsReaderSelectiveChunkLookupTest.java` |
| FINDINGS TD-001–TD-003, TD-005 | `src/test/java/cartographer/save/BoundedStreamingDecodePipelineTest.java` |

## Checkpoint B recommended scope

### HIGH

- TD-001: make completion observation callback-handshake based.
- TD-002: replace `Thread.State` polling as the interrupt readiness mechanism.
- TD-003: replace correctness-bearing one-second pipeline gates with exact handshakes and failure-safe cleanup.

### MEDIUM

- TD-004: harden reader concurrency start gates and release/join cleanup.
- TD-005: make bounded cleanup joins diagnostic and failure-safe.

### LOW

- None identified.

Do not implement these remediations in Checkpoint A.

## Potential guardrails for Checkpoint C

Derived from the findings:

- Require an explicit test-controlled handshake before interrupting a thread or asserting a scheduler-dependent lifecycle phase.
- Permit timed waits only as deadlock/failure guards unless the test documents why the duration is a contract; do not use short timeouts as progress synchronization.
- Require blocked worker/controller test fixtures to release their latches in `finally` before joining.
- Require concurrency tests to assert callback/order boundaries through latches or equivalent happens-before relationships, not immediate collection visibility.
- Require bounded cleanup joins to report/assert surviving threads with diagnostics.
- Keep fake/manual clocks and explicit set/order normalization patterns used by the safe tests.

No workflow, CI, production, or test source changes were made for these potential guardrails.
