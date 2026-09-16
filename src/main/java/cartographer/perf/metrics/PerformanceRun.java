package cartographer.perf.metrics;

import java.util.Objects;

/** Immutable identity and measurements for one completed measured run. */
public record PerformanceRun(
        String gitCommitSha,
        String workloadId,
        String saveFingerprint,
        ExecutionMode executionMode,
        int warmupCount,
        int measuredIterationCount,
        PerformanceEnvironment environment,
        PerformanceMetrics metrics
) {
    public PerformanceRun {
        gitCommitSha = required(gitCommitSha, "gitCommitSha");
        workloadId = required(workloadId, "workloadId");
        saveFingerprint = required(saveFingerprint, "saveFingerprint");
        Objects.requireNonNull(executionMode, "executionMode is required");
        if (warmupCount < 0) {
            throw new IllegalArgumentException("warmupCount must not be negative");
        }
        if (measuredIterationCount <= 0) {
            throw new IllegalArgumentException("measuredIterationCount must be positive");
        }
        Objects.requireNonNull(environment, "environment is required");
        Objects.requireNonNull(metrics, "metrics is required");
    }

    private static String required(String value, String name) {
        Objects.requireNonNull(value, name + " is required");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
