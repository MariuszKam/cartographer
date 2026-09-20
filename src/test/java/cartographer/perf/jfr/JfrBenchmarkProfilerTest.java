package cartographer.perf.jfr;

import cartographer.perf.benchmark.BenchmarkExecutionStatus;
import cartographer.perf.benchmark.BenchmarkOperationResult;
import cartographer.perf.benchmark.BenchmarkPlan;
import cartographer.perf.benchmark.BenchmarkRunner;
import cartographer.perf.fingerprint.ResultFingerprint;
import cartographer.perf.metrics.ExecutionMode;
import cartographer.perf.workload.MapWorkload;
import cartographer.perf.workload.RadiusProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JfrBenchmarkProfilerTest {
    @TempDir
    Path temporaryDirectory;

    private static final BenchmarkPlan BENCHMARK_PLAN = new BenchmarkPlan(
            new MapWorkload(RadiusProfile.R128),
            ExecutionMode.JVM_WARM,
            1,
            1
    );
    private static final JfrRecordingPlan RECORDING_PLAN = new JfrRecordingPlan(
            Path.of("review-recording.jfr"),
            1_000_000L,
            "review recording",
            JfrConfiguration.PROFILE
    );
    private static final ResultFingerprint FINGERPRINT =
            new ResultFingerprint("0".repeat(64));

    @Test
    void planValidatesDestinationAndBound() {
        assertThrows(NullPointerException.class, () -> new JfrRecordingPlan(
                null, 1, "recording", JfrConfiguration.PROFILE));
        assertThrows(IllegalArgumentException.class, () -> new JfrRecordingPlan(
                Path.of("recording.jfr"), 0, "recording", JfrConfiguration.PROFILE));
        assertThrows(IllegalArgumentException.class, () -> new JfrRecordingPlan(
                Path.of("recording.txt"), 1, "recording", JfrConfiguration.PROFILE));
        assertThrows(IllegalArgumentException.class, () -> new JfrRecordingPlan(
                Path.of("recording.jfr"), 1, " ", JfrConfiguration.PROFILE));
        assertThrows(NullPointerException.class, () -> new JfrRecordingPlan(
                Path.of("recording.jfr"), 1, "recording", null));
    }

    @Test
    void lifecycleIsConfiguredStartedBenchmarkedStoppedDumpedAndClosed() {
        List<String> events = new ArrayList<>();
        FakeRecording recording = new FakeRecording(events);
        BenchmarkRunner runner = new BenchmarkRunner(() -> 0L);
        JfrRecordingResult result = new JfrBenchmarkProfiler(
                runner,
                plan -> recording
        ).profile(RECORDING_PLAN, BENCHMARK_PLAN,
                workload -> {
                    events.add("benchmark");
                    return BenchmarkOperationResult.success(FINGERPRINT);
                });

        assertEquals(List.of("configure", "start", "benchmark", "benchmark", "stop", "dump", "close"), events);
        assertEquals(FINGERPRINT,
                result.benchmarkResult().measuredIterations().get(0).fingerprint().orElseThrow());
        assertEquals(RECORDING_PLAN.destination(), result.destination());
        assertEquals(RECORDING_PLAN.maxSizeBytes(), result.maxSizeBytes());
    }

    @Test
    void existingDestinationIsRejectedBeforeRecordingOrBenchmark() throws Exception {
        Path destination = temporaryDirectory.resolve("existing.jfr");
        Files.createFile(destination);
        JfrRecordingPlan plan = new JfrRecordingPlan(
                destination, 1, "recording", JfrConfiguration.PROFILE
        );
        int[] opens = {0};

        assertThrows(JfrProfilingException.class, () -> new JfrBenchmarkProfiler(
                new BenchmarkRunner(() -> 0L),
                ignored -> {
                    opens[0]++;
                    return new FakeRecording(new ArrayList<>());
                }
        ).profile(plan, BENCHMARK_PLAN, workload ->
                BenchmarkOperationResult.success(FINGERPRINT)));
        assertEquals(0, opens[0]);
    }

    @Test
    void startupFailurePreventsBenchmarkAndDumpFailureIsNotSuccess() {
        int[] calls = {0};
        assertThrows(JfrProfilingException.class, () -> new JfrBenchmarkProfiler(
                new BenchmarkRunner(() -> 0L),
                plan -> {
                    throw new JfrProfilingException("startup failed");
                }
        ).profile(RECORDING_PLAN, BENCHMARK_PLAN, workload -> {
            calls[0]++;
            return BenchmarkOperationResult.success(FINGERPRINT);
        }));
        assertEquals(0, calls[0]);

        FakeRecording dumpingFailure = new FakeRecording(new ArrayList<>());
        dumpingFailure.dumpFailure = new JfrProfilingException("dump failed");
        assertThrows(JfrProfilingException.class, () -> new JfrBenchmarkProfiler(
                new BenchmarkRunner(() -> 0L),
                plan -> dumpingFailure
        ).profile(RECORDING_PLAN, BENCHMARK_PLAN, workload ->
                BenchmarkOperationResult.success(FINGERPRINT)));
        assertTrue(dumpingFailure.events.contains("close"));
    }

    @Test
    void benchmarkRuntimeFailureRemainsRunnerResultAndErrorPropagatesWithCleanup() {
        FakeRecording runtimeRecording = new FakeRecording(new ArrayList<>());
        JfrRecordingResult runtimeResult = new JfrBenchmarkProfiler(
                new BenchmarkRunner(() -> 0L),
                plan -> runtimeRecording
        ).profile(RECORDING_PLAN, BENCHMARK_PLAN, workload ->
                { throw new IllegalStateException("benchmark failed"); });
        assertEquals(BenchmarkExecutionStatus.WARMUP_FAILED,
                runtimeResult.benchmarkResult().status());

        FakeRecording errorRecording = new FakeRecording(new ArrayList<>());
        AssertionError error = assertThrows(AssertionError.class, () ->
                new JfrBenchmarkProfiler(
                        new BenchmarkRunner(() -> 0L),
                        plan -> errorRecording
                ).profile(RECORDING_PLAN,
                        new BenchmarkPlan(
                                BENCHMARK_PLAN.workload(),
                                ExecutionMode.JVM_WARM,
                                0,
                                1
                        ),
                        workload -> { throw new AssertionError("catastrophic"); }));
        assertEquals("catastrophic", error.getMessage());
        assertEquals(List.of("configure", "start", "close"), errorRecording.events);
    }

    @Test
    void nonJvmWarmModesAreRejectedAndResultCollectionsAreImmutable() {
        assertThrows(UnsupportedOperationException.class, () -> new JfrBenchmarkProfiler(
                new BenchmarkRunner(() -> 0L),
                plan -> new FakeRecording(new ArrayList<>())
        ).profile(RECORDING_PLAN,
                new BenchmarkPlan(BENCHMARK_PLAN.workload(), ExecutionMode.PROCESS_COLD, 0, 1),
                workload -> BenchmarkOperationResult.success(FINGERPRINT)));

        JfrRecordingResult result = new JfrBenchmarkProfiler(
                new BenchmarkRunner(() -> 0L),
                plan -> new FakeRecording(new ArrayList<>())
        ).profile(RECORDING_PLAN, BENCHMARK_PLAN,
                workload -> BenchmarkOperationResult.success(FINGERPRINT));
        assertThrows(UnsupportedOperationException.class, () ->
                result.benchmarkResult().measuredIterations().clear());
    }

    private static final class FakeRecording implements JfrRecordingController {
        private final List<String> events;
        private JfrProfilingException dumpFailure;

        private FakeRecording(List<String> events) {
            this.events = events;
        }

        @Override
        public void configure() {
            events.add("configure");
        }

        @Override
        public void start() {
            events.add("start");
        }

        @Override
        public void stop() {
            events.add("stop");
        }

        @Override
        public void dump(Path destination) {
            events.add("dump");
            if (dumpFailure != null) {
                throw dumpFailure;
            }
        }

        @Override
        public void close() {
            events.add("close");
        }
    }
}
