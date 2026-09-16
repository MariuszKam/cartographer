package cartographer.perf.comparison;

import cartographer.perf.baseline.ReferenceBaseline;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Compares validated evidence without thresholds, significance tests, or execution. */
public final class RegressionComparator {
    private static final int PERCENT_SCALE = 4;

    public RegressionComparison compare(
            ReferenceBaseline baseline,
            CandidateMeasurement candidate
    ) {
        Objects.requireNonNull(baseline, "baseline is required");
        Objects.requireNonNull(candidate, "candidate is required");

        List<RegressionInconclusiveReason> reasons = new ArrayList<>();
        if (!baseline.workloadId().equals(candidate.workloadId())) {
            reasons.add(RegressionInconclusiveReason.WORKLOAD_MISMATCH);
        }
        if (!baseline.saveFingerprint().equals(candidate.saveFingerprint())) {
            reasons.add(RegressionInconclusiveReason.SAVE_FINGERPRINT_MISMATCH);
        }
        if (baseline.executionMode() != candidate.executionMode()) {
            reasons.add(RegressionInconclusiveReason.EXECUTION_MODE_MISMATCH);
        }
        if (!baseline.environment().equals(candidate.environment())) {
            reasons.add(RegressionInconclusiveReason.ENVIRONMENT_MISMATCH);
        }
        if (baseline.warmupCount() != candidate.warmupCount()) {
            reasons.add(RegressionInconclusiveReason.WARMUP_COUNT_MISMATCH);
        }
        if (baseline.measuredIterationCount() != candidate.measuredIterationCount()) {
            reasons.add(RegressionInconclusiveReason.MEASURED_ITERATION_COUNT_MISMATCH);
        }
        if (!reasons.isEmpty()) {
            return inconclusive(reasons);
        }

        if (!baseline.correctnessFingerprint().equals(candidate.correctnessFingerprint())) {
            return new RegressionComparison(
                    RegressionComparisonStatus.FAIL,
                    List.of(),
                    Optional.of(false),
                    Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()
            );
        }

        return new RegressionComparison(
                RegressionComparisonStatus.PASS,
                List.of(),
                Optional.of(true),
                Optional.of(delta(
                        baseline.summary().minWallClockNanoseconds(),
                        candidate.summary().minWallClockNanoseconds()
                )),
                Optional.of(delta(
                        baseline.summary().p50WallClockNanoseconds(),
                        candidate.summary().p50WallClockNanoseconds()
                )),
                Optional.of(delta(
                        baseline.summary().p95WallClockNanoseconds(),
                        candidate.summary().p95WallClockNanoseconds()
                )),
                Optional.of(delta(
                        baseline.summary().maxWallClockNanoseconds(),
                        candidate.summary().maxWallClockNanoseconds()
                ))
        );
    }

    private static RegressionComparison inconclusive(
            List<RegressionInconclusiveReason> reasons
    ) {
        return new RegressionComparison(
                RegressionComparisonStatus.INCONCLUSIVE,
                reasons,
                Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()
        );
    }

    private static RegressionMetricDelta delta(long baseline, long candidate) {
        BigInteger signedDelta = BigInteger.valueOf(candidate)
                .subtract(BigInteger.valueOf(baseline));
        Optional<BigDecimal> relative = baseline == 0
                ? Optional.empty()
                : Optional.of(BigDecimal.valueOf(candidate)
                        .subtract(BigDecimal.valueOf(baseline))
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(baseline), PERCENT_SCALE, RoundingMode.HALF_UP));
        return new RegressionMetricDelta(baseline, candidate, signedDelta, relative);
    }
}
