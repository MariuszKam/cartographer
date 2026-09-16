package cartographer.perf.benchmark;

import cartographer.perf.fingerprint.ResultFingerprint;
import cartographer.perf.metrics.ExecutionMode;
import cartographer.perf.workload.MapWorkload;
import cartographer.perf.workload.RadiusProfile;
import cartographer.perf.workload.WorkloadSpec;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BenchmarkRunnerTest {
    private static final WorkloadSpec WORKLOAD = new MapWorkload(RadiusProfile.R128);
    private static final ResultFingerprint FIRST = new ResultFingerprint("0".repeat(64));
    private static final ResultFingerprint SECOND = new ResultFingerprint("1".repeat(64));

    @Test
    void warmupsPrecedeMeasuredIterationsAndAreNotMixed() {
        ManualTime time = new ManualTime(10, 15, 20, 28, 40, 55, 60, 72, 80, 95);
        List<Integer> calls = new ArrayList<>();
        BenchmarkRunner runner = new BenchmarkRunner(time);
        BenchmarkRunResult result = runner.run(
                new BenchmarkPlan(WORKLOAD, ExecutionMode.JVM_WARM, 2, 3),
                workload -> {
                    calls.add(calls.size());
                    return BenchmarkOperationResult.success(FIRST);
                }
        );

        assertEquals(List.of(0, 1, 2, 3, 4), calls);
        assertEquals(2, result.warmups().size());
        assertEquals(3, result.measuredIterations().size());
        assertEquals(List.of(0, 1), result.warmups().stream()
                .map(BenchmarkIterationResult::iterationIndex).toList());
        assertEquals(List.of(0, 1, 2), result.measuredIterations().stream()
                .map(BenchmarkIterationResult::iterationIndex).toList());
        assertEquals(List.of(5L, 8L), result.warmups().stream()
                .map(BenchmarkIterationResult::wallClockNanoseconds).toList());
        assertEquals(List.of(15L, 12L, 15L), result.measuredIterations().stream()
                .map(BenchmarkIterationResult::wallClockNanoseconds).toList());
    }

    @Test
    void measuredFailuresAreRetainedAndLaterIterationsContinueWithoutRetry() {
        ManualTime time = new ManualTime(0, 5, 10, 20, 30, 45, 60, 80);
        List<Integer> calls = new ArrayList<>();
        BenchmarkRunResult result = new BenchmarkRunner(time).run(
                new BenchmarkPlan(WORKLOAD, ExecutionMode.JVM_WARM, 1, 3),
                workload -> {
                    int call = calls.size();
                    calls.add(call);
                    return call == 2
                            ? BenchmarkOperationResult.failure(
                            new BenchmarkFailure("ExampleFailure", "bad", "decode"),
                            Optional.empty()
                    )
                            : BenchmarkOperationResult.success(FIRST);
                }
        );

        assertEquals(List.of(0, 1, 2, 3), calls);
        assertEquals(BenchmarkExecutionStatus.MEASURED_FAILURES, result.status());
        assertEquals(3, result.measuredIterations().size());
        assertTrue(result.measuredIterations().get(1).failure().isPresent());
        assertEquals("decode", result.measuredIterations().get(1).failure().orElseThrow().context());
        assertTrue(result.measuredIterations().get(0).fingerprint().isPresent());
    }

    @Test
    void thrownRuntimeExceptionIsRetainedAndLaterMeasuredIterationsContinue() {
        ManualTime time = new ManualTime(0, 1, 2, 4, 5, 8);
        int[] calls = {0};
        BenchmarkRunResult result = new BenchmarkRunner(time).run(
                new BenchmarkPlan(WORKLOAD, ExecutionMode.JVM_WARM, 0, 3),
                workload -> {
                    int call = calls[0]++;
                    if (call == 1) {
                        throw new IllegalStateException("operation failed");
                    }
                    return BenchmarkOperationResult.success(FIRST);
                }
        );

        assertEquals(3, calls[0]);
        assertEquals(BenchmarkExecutionStatus.MEASURED_FAILURES, result.status());
        assertEquals(IllegalStateException.class.getName(),
                result.measuredIterations().get(1).failure().orElseThrow().exceptionType());
        assertTrue(result.measuredIterations().get(2).successful());
    }

    @Test
    void differingSuccessfulFingerprintsAreDetected() {
        ManualTime time = new ManualTime(0, 1, 2, 3, 4, 5);
        int[] call = {0};
        BenchmarkRunResult result = new BenchmarkRunner(time).run(
                new BenchmarkPlan(WORKLOAD, ExecutionMode.JVM_WARM, 0, 3),
                workload -> BenchmarkOperationResult.success(call[0]++ == 0 ? FIRST : SECOND)
        );

        assertEquals(BenchmarkExecutionStatus.NONDETERMINISTIC, result.status());
        assertFalse(result.hasConsistentFingerprints());
    }

    @Test
    void warmupFailureAbortsBeforeMeasuredIterations() {
        ManualTime time = new ManualTime(0, 4);
        int[] calls = {0};
        BenchmarkRunResult result = new BenchmarkRunner(time).run(
                new BenchmarkPlan(WORKLOAD, ExecutionMode.JVM_WARM, 2, 3),
                workload -> {
                    calls[0]++;
                    return BenchmarkOperationResult.failure(
                            new BenchmarkFailure("WarmupFailure", "failed", "warmup"),
                            Optional.empty()
                    );
                }
        );

        assertEquals(1, calls[0]);
        assertEquals(BenchmarkExecutionStatus.WARMUP_FAILED, result.status());
        assertEquals(1, result.warmups().size());
        assertTrue(result.measuredIterations().isEmpty());
    }

    @Test
    void thrownRuntimeExceptionDuringWarmupAbortsBeforeMeasuredIterations() {
        ManualTime time = new ManualTime(0, 4);
        int[] calls = {0};
        BenchmarkRunResult result = new BenchmarkRunner(time).run(
                new BenchmarkPlan(WORKLOAD, ExecutionMode.JVM_WARM, 2, 3),
                workload -> {
                    calls[0]++;
                    throw new IllegalArgumentException("warmup failed");
                }
        );

        assertEquals(1, calls[0]);
        assertEquals(BenchmarkExecutionStatus.WARMUP_FAILED, result.status());
        assertEquals(1, result.warmups().size());
        assertTrue(result.measuredIterations().isEmpty());
    }

    @Test
    void errorsPropagateImmediatelyWithoutExecutingLaterIterations() {
        int[] calls = {0};
        BenchmarkRunner runner = new BenchmarkRunner(new ManualTime(0));

        AssertionError error = assertThrows(AssertionError.class, () -> runner.run(
                new BenchmarkPlan(WORKLOAD, ExecutionMode.JVM_WARM, 0, 3),
                workload -> {
                    calls[0]++;
                    throw new AssertionError("catastrophic test error");
                }
        ));

        assertEquals("catastrophic test error", error.getMessage());
        assertEquals(1, calls[0]);
    }

    @Test
    void processColdAndCacheWarmAreNotFalselyExecutedInProcess() {
        int[] calls = {0};
        BenchmarkRunner runner = new BenchmarkRunner(() -> 0L);

        assertThrows(UnsupportedOperationException.class, () -> runner.run(
                new BenchmarkPlan(WORKLOAD, ExecutionMode.PROCESS_COLD, 0, 1),
                workload -> {
                    calls[0]++;
                    return BenchmarkOperationResult.success(FIRST);
                }
        ));
        assertThrows(UnsupportedOperationException.class, () -> runner.run(
                new BenchmarkPlan(WORKLOAD, ExecutionMode.CACHE_WARM, 0, 1),
                workload -> {
                    calls[0]++;
                    return BenchmarkOperationResult.success(FIRST);
                }
        ));
        assertEquals(0, calls[0]);
    }

    @Test
    void negativeTimeDeltaIsRejectedAndUnknownInstrumentationStaysAbsent() {
        ManualTime time = new ManualTime(10, 9);
        BenchmarkRunner runner = new BenchmarkRunner(time);

        assertThrows(IllegalStateException.class, () -> runner.run(
                new BenchmarkPlan(WORKLOAD, ExecutionMode.JVM_WARM, 0, 1),
                workload -> BenchmarkOperationResult.success(FIRST)
        ));

        BenchmarkRunResult result = new BenchmarkRunner(new ManualTime(0, 3)).run(
                new BenchmarkPlan(WORKLOAD, ExecutionMode.JVM_WARM, 0, 1),
                workload -> BenchmarkOperationResult.success(FIRST)
        );
        assertTrue(result.measuredIterations().get(0).instrumentation().isEmpty());
    }

    @Test
    void runCollectionsAreImmutable() {
        BenchmarkRunResult result = new BenchmarkRunner(new ManualTime(0, 1)).run(
                new BenchmarkPlan(WORKLOAD, ExecutionMode.JVM_WARM, 0, 1),
                workload -> BenchmarkOperationResult.success(FIRST)
        );

        assertThrows(UnsupportedOperationException.class, () ->
                result.measuredIterations().add(null));
    }

    private static final class ManualTime implements cartographer.perf.instrumentation.MonotonicTimeSource {
        private final long[] values;
        private int index;

        private ManualTime(long... values) {
            this.values = values;
        }

        @Override
        public long nanoTime() {
            return values[index++];
        }
    }
}
