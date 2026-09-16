package cartographer.perf.comparison;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Objects;
import java.util.Optional;

/** Signed candidate-minus-baseline delta; durations and deltas are nanoseconds. */
public record RegressionMetricDelta(
        long baselineNanoseconds,
        long candidateNanoseconds,
        BigInteger signedDeltaNanoseconds,
        Optional<BigDecimal> relativeChangePercent
) {
    public RegressionMetricDelta {
        if (baselineNanoseconds < 0 || candidateNanoseconds < 0) {
            throw new IllegalArgumentException("metric values must not be negative");
        }
        Objects.requireNonNull(signedDeltaNanoseconds, "signedDeltaNanoseconds is required");
        Objects.requireNonNull(relativeChangePercent, "relativeChangePercent is required");
        if (baselineNanoseconds == 0 && relativeChangePercent.isPresent()) {
            throw new IllegalArgumentException(
                    "relative change is undefined for a zero baseline"
            );
        }
        if (baselineNanoseconds != 0 && relativeChangePercent.isEmpty()) {
            throw new IllegalArgumentException(
                    "relative change is required for a nonzero baseline"
            );
        }
    }
}
