package cartographer.perf.jfr;

import cartographer.perf.benchmark.BenchmarkExecutionStatus;
import cartographer.perf.benchmark.BenchmarkFailure;
import cartographer.perf.benchmark.BenchmarkIterationResult;
import cartographer.perf.benchmark.BenchmarkOperationResult;
import cartographer.perf.benchmark.BenchmarkPlan;
import cartographer.perf.benchmark.BenchmarkRunResult;
import cartographer.perf.fingerprint.ResultFingerprint;
import cartographer.perf.macro.Pf18IterationEvidence;
import cartographer.perf.macro.Pf18MacroOperationFactory;
import cartographer.perf.metrics.ExecutionMode;
import cartographer.perf.safety.SaveSafetyGate;
import cartographer.perf.safety.SaveSafetySnapshot;
import cartographer.perf.safety.SaveSafetySnapshotter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Pf18JfrRunnerTest {
    private static final String SHA = "0123456789012345678901234567890123456789";

    @TempDir
    Path temp;

    @Test
    void mapCachePreparationCompletesBeforeProfilerStart() throws Exception {
        Path save = save();
        List<String> phases = new ArrayList<>();
        Pf18JfrCampaignIdentity identity = identity("MAP_R128", "MAP", "CACHE_WARM");
        Pf18JfrProfilerInvoker profiler = (plan, benchmark, operation) -> {
            phases.add("profiler");
            return successfulRecording(benchmark, operation);
        };
        Pf18JfrSummary summary = runner(mapFactory(phases, evidence(true, "semantic", "image")),
                profiler, identity, new AtomicInteger()).profile(save,
                temp.resolve("cache"), SHA, "MAP_R128", temp.resolve("output"));

        assertTrue(phases.indexOf("prepare") >= 0);
        assertTrue(phases.indexOf("preflight") > phases.indexOf("prepare"));
        assertTrue(phases.indexOf("recorded") > phases.indexOf("preflight"));
        assertTrue(summary.diagnosticOnly());
    }

    @Test
    void mapCachedPreflightMismatchAndNoHitPreventProfiler() throws Exception {
        Path save = save();
        AtomicInteger profilerCalls = new AtomicInteger();
        Pf18MacroOperationFactory mismatchWithSource = new Pf18MacroOperationFactory() {
            @Override
            public Pf18MacroOperation create(Path ignoredSave, Path cache,
                                              cartographer.perf.workload.WorkloadSpec ignoredWorkload) {
                return () -> cache == null ? evidence(false, "semantic", "image")
                        : evidence(true, "wrong", "image");
            }

            @Override
            public void prepareCache(Path ignoredSave, Path ignoredCache,
                                     cartographer.perf.workload.WorkloadSpec ignoredWorkload) {
            }

            @Override
            public List<String> cacheEvidence(Path ignoredSave, Path ignoredCache,
                                              cartographer.perf.workload.WorkloadSpec ignoredWorkload) {
                return List.of("manifest");
            }
        };
        assertThrows(RuntimeException.class, () -> runner(mismatchWithSource,
                countingProfiler(profilerCalls), identity("MAP_R128", "MAP", "CACHE_WARM"),
                new AtomicInteger()).profile(save, temp.resolve("cache-a"), SHA, "MAP_R128",
                temp.resolve("out-a")));
        assertEquals(0, profilerCalls.get());

        AtomicInteger noHitCalls = new AtomicInteger();
        Pf18MacroOperationFactory noHit = mapFactory(new ArrayList<>(),
                evidence(false, "semantic", "image"));
        assertThrows(RuntimeException.class, () -> runner(noHit, countingProfiler(noHitCalls),
                identity("MAP_R128", "MAP", "CACHE_WARM"), new AtomicInteger()).profile(save,
                temp.resolve("cache-b"), SHA, "MAP_R128", temp.resolve("out-b")));
        assertEquals(0, noHitCalls.get());
    }

    @Test
    void mapRecordedCacheLossInvalidatesProfilerResultAndAnalyzer() throws Exception {
        Path save = save();
        AtomicInteger cacheCalls = new AtomicInteger();
        AtomicInteger analyzerCalls = new AtomicInteger();
        Pf18MacroOperationFactory factory = new Pf18MacroOperationFactory() {
            @Override
            public Pf18MacroOperation create(Path ignoredSave, Path cache,
                                              cartographer.perf.workload.WorkloadSpec ignoredWorkload) {
                return () -> cache == null ? evidence(false, "semantic", "image")
                        : evidence(cacheCalls.getAndIncrement() < 2, "semantic", "image");
            }

            @Override
            public void prepareCache(Path ignoredSave, Path ignoredCache,
                                     cartographer.perf.workload.WorkloadSpec ignoredWorkload) {
            }

            @Override
            public List<String> cacheEvidence(Path ignoredSave, Path ignoredCache,
                                              cartographer.perf.workload.WorkloadSpec ignoredWorkload) {
                return List.of("manifest");
            }
        };
        Pf18JfrRunner runner = runner(factory, normalProfiler(),
                identity("MAP_R128", "MAP", "CACHE_WARM"), analyzerCalls);

        assertThrows(RuntimeException.class, () -> runner.profile(save, temp.resolve("cache"),
                SHA, "MAP_R128", temp.resolve("output")));
        assertEquals(0, analyzerCalls.get());
    }

    @Test
    void rockProfilingIsSourceAuthoritativeWithoutCacheHitRequirement() throws Exception {
        Path save = save();
        AtomicInteger analyzerCalls = new AtomicInteger();
        Pf18JfrSummary summary = runner((ignoredSave, ignoredCache, ignoredWorkload) ->
                        () -> evidence(false, Optional.empty(), Optional.of("image")),
                normalProfiler(), identity("ROCK_UPPER_R128", "ROCK_UPPER",
                        "SOURCE_AUTHORITATIVE; JVM_WARM diagnostic profile"), analyzerCalls)
                .profile(save, temp.resolve("cache"), SHA, "ROCK_UPPER_R128",
                        temp.resolve("output"));

        assertTrue(summary.diagnosticOnly());
        assertEquals(1, analyzerCalls.get());
    }

    @Test
    void allNonSuccessBenchmarkStatusesPreventAnalyzer() throws Exception {
        for (BenchmarkExecutionStatus status : List.of(BenchmarkExecutionStatus.WARMUP_FAILED,
                BenchmarkExecutionStatus.MEASURED_FAILURES,
                BenchmarkExecutionStatus.NONDETERMINISTIC)) {
            Path save = save();
            AtomicInteger analyzerCalls = new AtomicInteger();
            Pf18JfrProfilerInvoker profiler = (plan, ignored, operation) ->
                    new JfrRecordingResult(resultFor(status, plan), Path.of(plan.workload().id() + ".jfr"),
                            1, JfrConfiguration.PROFILE);
            assertThrows(RuntimeException.class, () -> runner(
                    (a, b, c) -> () -> evidence(false, Optional.empty(), Optional.of("image")),
                    profiler, identity("ROCK_UPPER_R128", "ROCK_UPPER",
                            "SOURCE_AUTHORITATIVE; JVM_WARM diagnostic profile"), analyzerCalls)
                    .profile(save, temp.resolve("cache-" + status), SHA, "ROCK_UPPER_R128",
                            temp.resolve("out-" + status)));
            assertEquals(0, analyzerCalls.get());
        }
    }

    @Test
    void wrongMeasuredFingerprintPreventsAnalyzer() throws Exception {
        Path save = save();
        AtomicInteger analyzerCalls = new AtomicInteger();
        Pf18JfrProfilerInvoker profiler = (recordingPlan, plan, operation) -> {
            List<BenchmarkIterationResult> measured = List.of(
                    successfulIteration(0), successfulIteration(1), successfulIteration(2),
                    successfulIteration(3), successfulIteration(4));
            return new JfrRecordingResult(new BenchmarkRunResult(plan,
                    List.of(successfulIteration(0)), measured, BenchmarkExecutionStatus.SUCCESS),
                    recordingPlan.destination(), 1, JfrConfiguration.PROFILE);
        };

        assertThrows(RuntimeException.class, () -> runner(
                (a, b, c) -> () -> evidence(false, Optional.empty(), Optional.of("image")),
                profiler, identity("ROCK_UPPER_R128", "ROCK_UPPER",
                        "SOURCE_AUTHORITATIVE; JVM_WARM diagnostic profile"), analyzerCalls)
                .profile(save, temp.resolve("wrong-cache"), SHA, "ROCK_UPPER_R128",
                        temp.resolve("wrong-output")));
        assertEquals(0, analyzerCalls.get());
    }

    @Test
    void profilerFailureStillAttemptsAfterSafetyCapture() throws Exception {
        Path save = save();
        AtomicInteger snapshots = new AtomicInteger();
        Pf18JfrSafetySnapshotProvider provider = path -> {
            snapshots.incrementAndGet();
            return new SaveSafetySnapshotter().capture(path);
        };
        Pf18JfrProfilerInvoker profiler = (plan, benchmark, operation) -> {
            throw new IllegalStateException("profiler startup failure");
        };
        assertThrows(RuntimeException.class, () -> new Pf18JfrRunner(profiler,
                (a, b, c) -> { throw new AssertionError("analyzer must not run"); },
                (a, b, c) -> () -> evidence(false, Optional.empty(), Optional.of("image")),
                provider, new SaveSafetyGate()).profile(save, temp.resolve("cache"), SHA,
                "ROCK_UPPER_R128", temp.resolve("output")));
        assertEquals(2, snapshots.get());
    }

    @Test
    void safetyMutationPreventsAnalyzerAcceptance() throws Exception {
        Path save = save();
        SaveSafetySnapshotter delegate = new SaveSafetySnapshotter();
        AtomicInteger calls = new AtomicInteger();
        Pf18JfrSafetySnapshotProvider provider = path -> {
            if (calls.incrementAndGet() == 2) {
                try {
                    Files.write(path, new byte[]{9, 8, 7});
                } catch (java.io.IOException exception) {
                    throw new IllegalStateException(exception);
                }
            }
            return delegate.capture(path);
        };
        AtomicInteger analyzerCalls = new AtomicInteger();
        assertThrows(RuntimeException.class, () -> new Pf18JfrRunner(normalProfiler(),
                (a, b, c) -> { analyzerCalls.incrementAndGet(); return emptySummary(a, b, c); },
                (a, b, c) -> () -> evidence(false, Optional.empty(), Optional.of("image")),
                provider, new SaveSafetyGate()).profile(save, temp.resolve("cache"), SHA,
                "ROCK_UPPER_R128", temp.resolve("output")));
        assertEquals(0, analyzerCalls.get());
    }

    @Test
    void outputUnderProtectedSourceDirectoryIsRejected() throws Exception {
        Path save = save();
        Pf18JfrRunner runner = runner((a, b, c) -> () -> evidence(false,
                Optional.empty(), Optional.of("image")), normalProfiler(),
                identity("ROCK_UPPER_R128", "ROCK_UPPER", "SOURCE_AUTHORITATIVE; JVM_WARM diagnostic profile"),
                new AtomicInteger());
        assertThrows(IllegalArgumentException.class, () -> runner.profile(save,
                temp.resolve("cache"), SHA, "ROCK_UPPER_R128", save.getParent().resolve("out")));
    }

    @Test
    void existingRecordingDestinationIsRejectedWithoutOverwrite() throws Exception {
        Path save = save();
        Pf18JfrRunner runner = runner((a, b, c) -> () -> evidence(false,
                Optional.empty(), Optional.of("image")), normalProfiler(),
                identity("MAP_R128", "MAP", "CACHE_WARM"), new AtomicInteger());
        Path output = temp.resolve("existing-output");
        Files.createDirectories(output);
        Files.createFile(output.resolve("MAP_R128.jfr"));
        assertThrows(IllegalArgumentException.class, () -> runner.profile(save,
                temp.resolve("recording-cache"), SHA, "MAP_R128", output));
    }

    @Test
    void existingSummaryDestinationIsRejectedWithoutOverwrite() throws Exception {
        Path save = save();
        Pf18JfrRunner runner = runner((a, b, c) -> () -> evidence(false,
                Optional.empty(), Optional.of("image")), normalProfiler(),
                identity("MAP_R128", "MAP", "CACHE_WARM"), new AtomicInteger());
        Path output = temp.resolve("existing-summary-output");
        Files.createDirectories(output);
        Files.createFile(output.resolve("MAP_R128-jfr-summary.txt"));
        assertThrows(IllegalArgumentException.class, () -> runner.profile(save,
                temp.resolve("summary-cache"), SHA,
                "MAP_R128", output));
    }

    @Test
    void existingMapCampaignCacheDestinationIsRejectedWithoutOverwrite() throws Exception {
        Path save = save();
        Pf18JfrRunner runner = runner((a, b, c) -> () -> evidence(false,
                Optional.empty(), Optional.of("image")), normalProfiler(),
                identity("MAP_R128", "MAP", "CACHE_WARM"), new AtomicInteger());
        Path output = temp.resolve("existing-cache-output");
        Path cache = temp.resolve("existing-cache");
        Files.createDirectories(output);
        Files.createDirectories(cache.resolve("pf18-jfr-" + SHA + "-MAP_R128"));
        assertThrows(IllegalArgumentException.class, () -> runner.profile(save, cache, SHA,
                "MAP_R128", output));
    }

    @Test
    void successfulStatusWithMissingMeasuredFingerprintCannotReachAnalyzer() throws Exception {
        Path save = save();
        AtomicInteger analyzerCalls = new AtomicInteger();
        Pf18JfrProfilerInvoker profiler = (recordingPlan, plan, operation) -> {
            BenchmarkIterationResult missing = new BenchmarkIterationResult(
                    0, 1, Optional.empty(), Optional.empty(),
                    Optional.of(new BenchmarkFailure("MissingFingerprint", "missing", "test")));
            List<BenchmarkIterationResult> measured = List.of(missing,
                    consistentIteration(1), consistentIteration(2),
                    consistentIteration(3), consistentIteration(4));
            return new JfrRecordingResult(new BenchmarkRunResult(plan,
                    List.of(consistentIteration(0)), measured, BenchmarkExecutionStatus.SUCCESS),
                    recordingPlan.destination(), 1, JfrConfiguration.PROFILE);
        };

        assertThrows(RuntimeException.class, () -> runner(
                (a, b, c) -> () -> evidence(false, Optional.empty(), Optional.of("image")),
                profiler, identity("ROCK_UPPER_R128", "ROCK_UPPER",
                        "SOURCE_AUTHORITATIVE; JVM_WARM diagnostic profile"), analyzerCalls)
                .profile(save, temp.resolve("missing-fingerprint-cache"), SHA,
                        "ROCK_UPPER_R128", temp.resolve("missing-fingerprint-output")));
        assertEquals(0, analyzerCalls.get());
    }

    private Pf18JfrRunner runner(Pf18MacroOperationFactory operations,
                                 Pf18JfrProfilerInvoker profiler,
                                 Pf18JfrCampaignIdentity identity,
                                 AtomicInteger analyzerCalls) {
        return new Pf18JfrRunner(profiler, (recording, summary, actual) -> {
            analyzerCalls.incrementAndGet();
            return emptySummary(recording, summary, actual);
        }, operations, new SaveSafetySnapshotter()::capture, new SaveSafetyGate());
    }

    private Pf18MacroOperationFactory mapFactory(List<String> phases,
                                                  Pf18IterationEvidence cached) {
        return new Pf18MacroOperationFactory() {
            @Override
            public Pf18MacroOperation create(Path save, Path cache,
                                              cartographer.perf.workload.WorkloadSpec workload) {
                return () -> {
                    if (cache == null) return evidence(false, "semantic", "image");
                    phases.add(phases.isEmpty() ? "preflight" : "recorded");
                    return cached;
                };
            }

            @Override
            public void prepareCache(Path save, Path cache,
                                     cartographer.perf.workload.WorkloadSpec workload) {
                phases.add("prepare");
            }

            @Override
            public List<String> cacheEvidence(Path save, Path cache,
                                              cartographer.perf.workload.WorkloadSpec workload) {
                return List.of("manifest");
            }
        };
    }

    private Pf18JfrProfilerInvoker normalProfiler() {
        return (recordingPlan, plan, operation) -> successfulRecording(plan, operation);
    }

    private Pf18JfrProfilerInvoker countingProfiler(AtomicInteger calls) {
        return (recordingPlan, plan, operation) -> {
            calls.incrementAndGet();
            return successfulRecording(plan, operation);
        };
    }

    private static JfrRecordingResult successfulRecording(BenchmarkPlan plan,
                                                          cartographer.perf.benchmark.BenchmarkOperation operation) {
        List<BenchmarkIterationResult> warmups = new ArrayList<>();
        for (int index = 0; index < plan.warmupCount(); index++) {
            warmups.add(invoke(plan, operation, index));
        }
        List<BenchmarkIterationResult> measured = new ArrayList<>();
        for (int index = 0; index < plan.measuredIterationCount(); index++) {
            measured.add(invoke(plan, operation, index));
        }
        BenchmarkExecutionStatus status = measured.stream().allMatch(BenchmarkIterationResult::successful)
                ? BenchmarkExecutionStatus.SUCCESS : BenchmarkExecutionStatus.MEASURED_FAILURES;
        return new JfrRecordingResult(new BenchmarkRunResult(plan, warmups, measured, status),
                Path.of("recording.jfr"), 1, JfrConfiguration.PROFILE);
    }

    private static BenchmarkIterationResult invoke(BenchmarkPlan plan,
                                                    cartographer.perf.benchmark.BenchmarkOperation operation,
                                                    int index) {
        try {
            BenchmarkOperationResult result = operation.execute(plan.workload());
            return new BenchmarkIterationResult(index, 1, result.fingerprint(),
                    result.instrumentation(), result.failure());
        } catch (RuntimeException failure) {
            return new BenchmarkIterationResult(index, 1, Optional.empty(), Optional.empty(),
                    Optional.of(BenchmarkFailure.from(failure, "test profiler")));
        }
    }

    private static BenchmarkRunResult resultFor(BenchmarkExecutionStatus status, BenchmarkPlan plan) {
        List<BenchmarkIterationResult> warmups = status == BenchmarkExecutionStatus.WARMUP_FAILED
                ? List.of() : List.of(successfulIteration(0));
        List<BenchmarkIterationResult> measured = status == BenchmarkExecutionStatus.WARMUP_FAILED
                ? List.of() : List.of(status == BenchmarkExecutionStatus.NONDETERMINISTIC
                ? successfulIteration(0) : failedIteration(0));
        if (status == BenchmarkExecutionStatus.NONDETERMINISTIC) {
            measured = List.of(successfulIteration(0), successfulIteration(1),
                    successfulIteration(2), successfulIteration(3), successfulIteration(4));
        }
        return new BenchmarkRunResult(plan, warmups, measured, status);
    }

    private static BenchmarkIterationResult successfulIteration(int index) {
        return new BenchmarkIterationResult(index, 1,
                Optional.of(new ResultFingerprint(Integer.toHexString(index).repeat(64).substring(0, 64))),
                Optional.empty(), Optional.empty());
    }

    private static BenchmarkIterationResult consistentIteration(int index) {
        return new BenchmarkIterationResult(index, 1,
                Optional.of(new ResultFingerprint("d".repeat(64))),
                Optional.empty(), Optional.empty());
    }

    private static BenchmarkIterationResult failedIteration(int index) {
        return new BenchmarkIterationResult(index, 1, Optional.empty(), Optional.empty(),
                Optional.of(new BenchmarkFailure("TestFailure", "failure", "test")));
    }

    private Path save() throws Exception {
        Path source = Files.createTempDirectory(temp, "source-");
        Path save = source.resolve("world.vcdbs");
        Files.write(save, new byte[]{1, 2, 3});
        return save;
    }

    private static Pf18IterationEvidence evidence(boolean cacheHit, String semantic, String image) {
        return new Pf18IterationEvidence(Optional.of(semantic), Optional.of(image), cacheHit,
                "source");
    }

    private static Pf18IterationEvidence evidence(boolean cacheHit, Optional<String> semantic,
                                                  Optional<String> image) {
        return new Pf18IterationEvidence(semantic, image, cacheHit, "source");
    }

    private static Pf18JfrCampaignIdentity identity(String workload, String family, String state) {
        return new Pf18JfrCampaignIdentity(SHA, workload, family, 128, state, "profile", 1,
                Path.of("world.vcdbs"), "PASS", Optional.of("semantic"), Optional.of("image"));
    }

    private static Pf18JfrSummary emptySummary(Path recording, Path summary,
                                               Pf18JfrCampaignIdentity identity) {
        return new Pf18JfrSummary(recording, summary, true, identity, List.of());
    }
}
