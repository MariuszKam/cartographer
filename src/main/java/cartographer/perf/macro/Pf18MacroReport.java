package cartographer.perf.macro;

import cartographer.perf.metrics.ExecutionMode;
import cartographer.perf.metrics.PerformanceEnvironment;
import cartographer.perf.safety.SaveSafetyResult;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable factual report for one PF-1.8 macro campaign. */
public record Pf18MacroReport(
        String gitSha,
        String saveFingerprint,
        String workloadId,
        String workloadFamily,
        int radius,
        ExecutionMode executionMode,
        String preparation,
        PerformanceEnvironment environment,
        int warmupCount,
        int measuredCount,
        Optional<String> semanticFingerprint,
        Optional<String> imageFingerprint,
        List<Long> measuredWallClockNanoseconds,
        long minNanoseconds,
        long p50Nanoseconds,
        long p95Nanoseconds,
        long maxNanoseconds,
        List<String> failures,
        SaveSafetyResult sourceSafety,
        boolean cacheHitVerified,
        List<String> cacheEvidence,
        Path outputPath
) {
    public Pf18MacroReport {
        gitSha = required(gitSha, "gitSha");
        saveFingerprint = required(saveFingerprint, "saveFingerprint");
        workloadId = required(workloadId, "workloadId");
        workloadFamily = required(workloadFamily, "workloadFamily");
        preparation = required(preparation, "preparation");
        Objects.requireNonNull(executionMode, "executionMode is required");
        Objects.requireNonNull(environment, "environment is required");
        if (radius <= 0 || warmupCount < 0 || measuredCount <= 0) {
            throw new IllegalArgumentException("invalid macro methodology values");
        }
        if (measuredWallClockNanoseconds == null) {
            throw new NullPointerException("measured samples are required");
        }
        measuredWallClockNanoseconds = List.copyOf(measuredWallClockNanoseconds);
        semanticFingerprint = Objects.requireNonNull(semanticFingerprint,
                "semantic fingerprint is required");
        imageFingerprint = Objects.requireNonNull(imageFingerprint,
                "image fingerprint is required");
        failures = List.copyOf(Objects.requireNonNull(failures, "failures are required"));
        cacheEvidence = List.copyOf(Objects.requireNonNull(cacheEvidence,
                "cache evidence is required"));
        Objects.requireNonNull(sourceSafety, "source safety is required");
        outputPath = Objects.requireNonNull(outputPath, "output path is required")
                .toAbsolutePath().normalize();
        requireNonNegative(minNanoseconds, "minNanoseconds");
        requireNonNegative(p50Nanoseconds, "p50Nanoseconds");
        requireNonNegative(p95Nanoseconds, "p95Nanoseconds");
        requireNonNegative(maxNanoseconds, "maxNanoseconds");
    }

    public boolean evidenceIsValid() {
        return failures.isEmpty()
                && sourceSafety.status() == cartographer.perf.safety.SaveSafetyStatus.PASS
                && (executionMode != ExecutionMode.CACHE_WARM || cacheHitVerified);
    }

    private static String required(String value, String name) {
        Objects.requireNonNull(value, name + " is required");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private static void requireNonNegative(long value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }
}
