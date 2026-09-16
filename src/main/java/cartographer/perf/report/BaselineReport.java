package cartographer.perf.report;

import cartographer.perf.metrics.ExecutionMode;
import cartographer.perf.metrics.PerformanceEnvironment;
import cartographer.perf.safety.SaveSafetyStatus;
import cartographer.perf.safety.SaveSafetyViolation;
import cartographer.perf.fingerprint.ResultFingerprint;

import java.util.List;
import java.util.Objects;

/** Immutable presentation model for one validated baseline evidence set. */
public record BaselineReport(
        String gitCommitSha,
        String workloadId,
        String saveFingerprint,
        ExecutionMode executionMode,
        PerformanceEnvironment environment,
        int warmupCount,
        int measuredIterationCount,
        ResultFingerprint correctnessFingerprint,
        long minWallClockNanoseconds,
        long p50WallClockNanoseconds,
        long p95WallClockNanoseconds,
        long maxWallClockNanoseconds,
        List<BaselineReportSample> samples,
        SaveSafetyStatus saveSafetyStatus,
        List<SaveSafetyViolation> saveSafetyViolations
) {
    public BaselineReport {
        Objects.requireNonNull(gitCommitSha, "gitCommitSha is required");
        Objects.requireNonNull(workloadId, "workloadId is required");
        Objects.requireNonNull(saveFingerprint, "saveFingerprint is required");
        Objects.requireNonNull(executionMode, "executionMode is required");
        Objects.requireNonNull(environment, "environment is required");
        if (warmupCount < 0) {
            throw new IllegalArgumentException("warmupCount must not be negative");
        }
        if (measuredIterationCount <= 0) {
            throw new IllegalArgumentException("measuredIterationCount must be positive");
        }
        Objects.requireNonNull(correctnessFingerprint, "correctnessFingerprint is required");
        if (minWallClockNanoseconds < 0 || p50WallClockNanoseconds < 0
                || p95WallClockNanoseconds < 0 || maxWallClockNanoseconds < 0) {
            throw new IllegalArgumentException("wall-clock values must not be negative");
        }
        samples = List.copyOf(Objects.requireNonNull(samples, "samples is required"));
        if (samples.size() != measuredIterationCount) {
            throw new IllegalArgumentException("samples must match measuredIterationCount");
        }
        Objects.requireNonNull(saveSafetyStatus, "saveSafetyStatus is required");
        saveSafetyViolations = List.copyOf(
                Objects.requireNonNull(saveSafetyViolations, "saveSafetyViolations is required")
        );
        if (saveSafetyStatus == SaveSafetyStatus.PASS && !saveSafetyViolations.isEmpty()) {
            throw new IllegalArgumentException("PASS report cannot contain safety violations");
        }
        if (saveSafetyStatus == SaveSafetyStatus.FAIL && saveSafetyViolations.isEmpty()) {
            throw new IllegalArgumentException("FAIL report requires safety violations");
        }
    }
}
