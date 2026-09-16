package cartographer.perf.benchmark;

import java.util.List;
import java.util.Objects;

/** Immutable run output retaining warmup metadata and every measured sample. */
public record BenchmarkRunResult(
        BenchmarkPlan plan,
        List<BenchmarkIterationResult> warmups,
        List<BenchmarkIterationResult> measuredIterations,
        BenchmarkExecutionStatus status
) {
    public BenchmarkRunResult {
        Objects.requireNonNull(plan, "plan is required");
        warmups = List.copyOf(Objects.requireNonNull(warmups, "warmups is required"));
        measuredIterations = List.copyOf(
                Objects.requireNonNull(measuredIterations, "measuredIterations is required")
        );
        Objects.requireNonNull(status, "status is required");
    }

    public boolean hasConsistentFingerprints() {
        String expected = null;
        for (BenchmarkIterationResult iteration : measuredIterations) {
            if (iteration.fingerprint().isEmpty()) {
                continue;
            }
            String fingerprint = iteration.fingerprint().orElseThrow().sha256Hex();
            if (expected == null) {
                expected = fingerprint;
            } else if (!expected.equals(fingerprint)) {
                return false;
            }
        }
        return true;
    }

    public boolean hasMissingMeasuredFingerprints() {
        return measuredIterations.stream().anyMatch(
                iteration -> iteration.successful() && iteration.fingerprint().isEmpty()
        );
    }
}
