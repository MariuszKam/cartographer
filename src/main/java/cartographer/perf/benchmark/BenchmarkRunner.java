package cartographer.perf.benchmark;

import cartographer.perf.fingerprint.ResultFingerprint;
import cartographer.perf.metrics.ExecutionMode;
import cartographer.perf.instrumentation.MonotonicTimeSource;
import cartographer.perf.instrumentation.SystemMonotonicTimeSource;
import cartographer.perf.workload.WorkloadSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * In-process orchestration for JVM-warm runs. It deliberately does not fake
 * process-cold or cache-warm preparation and never retries an iteration.
 */
public final class BenchmarkRunner {
    private final MonotonicTimeSource timeSource;

    public BenchmarkRunner() {
        this(SystemMonotonicTimeSource.INSTANCE);
    }

    public BenchmarkRunner(MonotonicTimeSource timeSource) {
        this.timeSource = Objects.requireNonNull(timeSource, "timeSource is required");
    }

    public BenchmarkRunResult run(BenchmarkPlan plan, BenchmarkOperation operation) {
        Objects.requireNonNull(plan, "plan is required");
        Objects.requireNonNull(operation, "operation is required");
        if (plan.executionMode() != ExecutionMode.JVM_WARM) {
            throw new UnsupportedOperationException(
                    "in-process runner supports only JVM_WARM; PROCESS_COLD and CACHE_WARM "
                            + "require external preparation"
            );
        }

        List<BenchmarkIterationResult> warmups = new ArrayList<>();
        for (int index = 0; index < plan.warmupCount(); index++) {
            BenchmarkIterationResult result = executeIteration(
                    operation,
                    plan.workload(),
                    index
            );
            warmups.add(result);
            if (!result.successful()) {
                return new BenchmarkRunResult(
                        plan,
                        warmups,
                        List.of(),
                        BenchmarkExecutionStatus.WARMUP_FAILED
                );
            }
        }

        List<BenchmarkIterationResult> measured = new ArrayList<>();
        for (int index = 0; index < plan.measuredIterationCount(); index++) {
            measured.add(executeIteration(operation, plan.workload(), index));
        }

        BenchmarkExecutionStatus status = measured.stream().anyMatch(
                result -> !result.successful()
        )
                ? BenchmarkExecutionStatus.MEASURED_FAILURES
                : consistent(measured)
                ? BenchmarkExecutionStatus.SUCCESS
                : BenchmarkExecutionStatus.NONDETERMINISTIC;
        return new BenchmarkRunResult(plan, warmups, measured, status);
    }

    private BenchmarkIterationResult executeIteration(
            BenchmarkOperation operation,
            WorkloadSpec workload,
            int iterationIndex
    ) {
        long startedAt = timeSource.nanoTime();
        BenchmarkOperationResult operationResult;
        try {
            operationResult = Objects.requireNonNull(
                    operation.execute(workload),
                    "benchmark operation returned null"
            );
        } catch (RuntimeException | Error failure) {
            operationResult = BenchmarkOperationResult.failure(
                    BenchmarkFailure.from(failure, "benchmark operation"),
                    java.util.Optional.empty()
            );
        }
        long elapsed = elapsedSince(startedAt);
        return new BenchmarkIterationResult(
                iterationIndex,
                elapsed,
                operationResult.fingerprint(),
                operationResult.instrumentation(),
                operationResult.failure()
        );
    }

    private long elapsedSince(long startedAt) {
        long elapsed;
        try {
            elapsed = Math.subtractExact(timeSource.nanoTime(), startedAt);
        } catch (ArithmeticException overflow) {
            throw new IllegalStateException("iteration timing overflow", overflow);
        }
        if (elapsed < 0) {
            throw new IllegalStateException(
                    "monotonic time source returned a negative iteration duration"
            );
        }
        return elapsed;
    }

    private boolean consistent(List<BenchmarkIterationResult> measured) {
        String expected = null;
        for (BenchmarkIterationResult result : measured) {
            ResultFingerprint fingerprint = result.fingerprint().orElseThrow();
            if (expected == null) {
                expected = fingerprint.sha256Hex();
            } else if (!expected.equals(fingerprint.sha256Hex())) {
                return false;
            }
        }
        return true;
    }
}
