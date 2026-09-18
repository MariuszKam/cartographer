package cartographer.perf.macro;

import cartographer.perf.metrics.ExecutionMode;
import cartographer.perf.metrics.PerformanceEnvironment;
import cartographer.perf.metrics.Pf18ResourceEvidence;
import cartographer.perf.safety.SaveSafetyResult;
import cartographer.perf.safety.SaveSafetySnapshot;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/** Immutable factual report for one PF-1.8 macro campaign. */
public record Pf18MacroReport(
        String gitSha,
        String saveFingerprint,
        String workloadId,
        String workloadFamily,
        Path savePath,
        int radius,
        ExecutionMode executionMode,
        String preparation,
        PerformanceEnvironment environment,
        int warmupCount,
        int measuredCount,
        Optional<String> semanticFingerprint,
        Optional<String> imageFingerprint,
        String workloadContract,
        List<Pf18ResourceEvidence> resourceEvidence,
        List<Pf18MeasuredIterationEvidence> measuredEvidence,
        List<Long> measuredWallClockNanoseconds,
        OptionalLong minNanoseconds,
        OptionalLong p50Nanoseconds,
        OptionalLong p95Nanoseconds,
        OptionalLong maxNanoseconds,
        List<String> failures,
        Optional<SaveSafetyResult> sourceSafety,
        Optional<String> sourceSafetyInspectionFailure,
        SaveSafetySnapshot beforeSafety,
        Optional<SaveSafetySnapshot> afterSafety,
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
        savePath = Objects.requireNonNull(savePath, "save path is required")
                .toAbsolutePath().normalize();
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
        workloadContract = required(workloadContract, "workloadContract");
        resourceEvidence = List.copyOf(Objects.requireNonNull(resourceEvidence,
                "resource evidence is required"));
        measuredEvidence = List.copyOf(Objects.requireNonNull(measuredEvidence,
                "measured evidence is required"));
        failures = List.copyOf(Objects.requireNonNull(failures, "failures are required"));
        cacheEvidence = List.copyOf(Objects.requireNonNull(cacheEvidence,
                "cache evidence is required"));
        sourceSafety = Objects.requireNonNull(sourceSafety, "source safety is required");
        sourceSafetyInspectionFailure = Objects.requireNonNull(sourceSafetyInspectionFailure,
                "source safety inspection failure is required");
        Objects.requireNonNull(beforeSafety, "before safety is required");
        afterSafety = Objects.requireNonNull(afterSafety, "after safety is required");
        outputPath = Objects.requireNonNull(outputPath, "output path is required")
                .toAbsolutePath().normalize();
        Objects.requireNonNull(minNanoseconds, "minNanoseconds is required");
        Objects.requireNonNull(p50Nanoseconds, "p50Nanoseconds is required");
        Objects.requireNonNull(p95Nanoseconds, "p95Nanoseconds is required");
        Objects.requireNonNull(maxNanoseconds, "maxNanoseconds is required");
    }

    public boolean evidenceIsValid() {
        return failures.isEmpty()
                && measuredEvidence.size() == measuredCount
                && measuredEvidence.stream().allMatch(Pf18MeasuredIterationEvidence::successful)
                && measuredWallClockNanoseconds.size() == measuredCount
                && resourceEvidence.size() == measuredCount
                && sourceSafety.isPresent()
                && sourceSafety.orElseThrow().status() == cartographer.perf.safety.SaveSafetyStatus.PASS
                && afterSafety.isPresent()
                && (executionMode != ExecutionMode.CACHE_WARM || cacheHitVerified)
                && (executionMode != ExecutionMode.CACHE_WARM
                || measuredEvidence.stream().allMatch(evidence -> evidence.cacheHit().orElse(false)));
    }

    private static String required(String value, String name) {
        Objects.requireNonNull(value, name + " is required");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

}
