package cartographer.perf.macro;

import cartographer.perf.metrics.Pf18ResourceEvidence;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/** Factual evidence for one declared measured sample, including failed samples. */
public record Pf18MeasuredIterationEvidence(
        int iterationIndex,
        OptionalLong parentWallClockNanoseconds,
        boolean successful,
        Optional<Boolean> cacheHit,
        Optional<String> sourceWork,
        Optional<String> failure,
        Optional<Pf18ResourceEvidence> resourceEvidence
) {
    public Pf18MeasuredIterationEvidence {
        if (iterationIndex < 0) throw new IllegalArgumentException("iterationIndex must not be negative");
        parentWallClockNanoseconds = Objects.requireNonNull(parentWallClockNanoseconds);
        cacheHit = Objects.requireNonNull(cacheHit);
        sourceWork = Objects.requireNonNull(sourceWork);
        failure = Objects.requireNonNull(failure);
        resourceEvidence = Objects.requireNonNull(resourceEvidence);
        if (successful && (parentWallClockNanoseconds.isEmpty()
                || cacheHit.isEmpty() || sourceWork.isEmpty() || failure.isPresent()
                || resourceEvidence.isEmpty())) {
            throw new IllegalArgumentException("successful sample evidence is incomplete");
        }
        if (!successful && failure.isEmpty()) {
            throw new IllegalArgumentException("failed sample evidence requires failure");
        }
    }
}
