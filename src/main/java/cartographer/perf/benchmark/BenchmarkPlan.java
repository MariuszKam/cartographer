package cartographer.perf.benchmark;

import cartographer.perf.metrics.ExecutionMode;
import cartographer.perf.workload.WorkloadSpec;

import java.util.Objects;

public record BenchmarkPlan(
        WorkloadSpec workload,
        ExecutionMode executionMode,
        int warmupCount,
        int measuredIterationCount
) {
    public BenchmarkPlan {
        Objects.requireNonNull(workload, "workload is required");
        Objects.requireNonNull(executionMode, "executionMode is required");
        if (warmupCount < 0) {
            throw new IllegalArgumentException("warmupCount must not be negative");
        }
        if (measuredIterationCount <= 0) {
            throw new IllegalArgumentException("measuredIterationCount must be positive");
        }
    }
}
