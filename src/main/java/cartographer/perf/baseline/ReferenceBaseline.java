package cartographer.perf.baseline;

import cartographer.perf.metrics.ExecutionMode;
import cartographer.perf.metrics.PerformanceEnvironment;
import cartographer.perf.fingerprint.ResultFingerprint;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Immutable validated reference measurement set for one workload. */
public record ReferenceBaseline(
        String gitCommitSha,
        String workloadId,
        String saveFingerprint,
        ExecutionMode executionMode,
        int warmupCount,
        int measuredIterationCount,
        PerformanceEnvironment environment,
        ResultFingerprint correctnessFingerprint,
        List<ReferenceBaselineSample> samples,
        ReferenceBaselineSummary summary
) {
    public ReferenceBaseline {
        gitCommitSha = exactGitSha(gitCommitSha);
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
        Objects.requireNonNull(correctnessFingerprint, "correctnessFingerprint is required");
        samples = List.copyOf(Objects.requireNonNull(samples, "samples is required"));
        if (samples.size() != measuredIterationCount) {
            throw new IllegalArgumentException(
                    "samples must match measuredIterationCount"
            );
        }
        Objects.requireNonNull(summary, "summary is required");
    }

    private static String required(String value, String name) {
        Objects.requireNonNull(value, name + " is required");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private static String exactGitSha(String value) {
        String normalized = required(value, "gitCommitSha").toLowerCase(Locale.ROOT);
        if (!normalized.matches("[0-9a-f]{40}")) {
            throw new IllegalArgumentException("gitCommitSha must be a full 40-character SHA");
        }
        return normalized;
    }
}
