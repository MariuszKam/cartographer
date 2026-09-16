package cartographer.perf.comparison;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable deterministic comparison mechanics, without release thresholds. */
public record RegressionComparison(
        RegressionComparisonStatus status,
        List<RegressionInconclusiveReason> inconclusiveReasons,
        Optional<Boolean> fingerprintMatch,
        Optional<RegressionMetricDelta> min,
        Optional<RegressionMetricDelta> p50,
        Optional<RegressionMetricDelta> p95,
        Optional<RegressionMetricDelta> max
) {
    public RegressionComparison {
        Objects.requireNonNull(status, "status is required");
        inconclusiveReasons = List.copyOf(
                Objects.requireNonNull(inconclusiveReasons, "inconclusiveReasons is required")
        );
        Objects.requireNonNull(fingerprintMatch, "fingerprintMatch is required");
        Objects.requireNonNull(min, "min is required");
        Objects.requireNonNull(p50, "p50 is required");
        Objects.requireNonNull(p95, "p95 is required");
        Objects.requireNonNull(max, "max is required");

        boolean hasAnyDelta = min.isPresent() || p50.isPresent() || p95.isPresent() || max.isPresent();
        switch (status) {
            case PASS -> {
                if (!inconclusiveReasons.isEmpty()
                        || !fingerprintMatch.orElse(false)
                        || !min.isPresent() || !p50.isPresent() || !p95.isPresent() || !max.isPresent()) {
                    throw new IllegalArgumentException("PASS comparison has contradictory evidence");
                }
            }
            case FAIL -> {
                if (!inconclusiveReasons.isEmpty()
                        || !fingerprintMatch.map(match -> !match).orElse(false)
                        || hasAnyDelta) {
                    throw new IllegalArgumentException("FAIL comparison has contradictory evidence");
                }
            }
            case INCONCLUSIVE -> {
                if (inconclusiveReasons.isEmpty() || fingerprintMatch.isPresent() || hasAnyDelta) {
                    throw new IllegalArgumentException(
                            "INCONCLUSIVE comparison has contradictory evidence"
                    );
                }
            }
        }
    }
}
