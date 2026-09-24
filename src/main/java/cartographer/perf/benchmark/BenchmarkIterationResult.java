package cartographer.perf.benchmark;

import cartographer.perf.fingerprint.ResultFingerprint;
import java.util.Objects;
import java.util.Optional;

/** Immutable raw result for one warmup or measured iteration. */
public record BenchmarkIterationResult(
        int iterationIndex,
        long wallClockNanoseconds,
        Optional<ResultFingerprint> fingerprint,
        Optional<BenchmarkFailure> failure
) {
    public BenchmarkIterationResult {
        if (iterationIndex < 0) {
            throw new IllegalArgumentException("iterationIndex must not be negative");
        }
        if (wallClockNanoseconds < 0) {
            throw new IllegalArgumentException("wallClockNanoseconds must not be negative");
        }
        Objects.requireNonNull(fingerprint, "fingerprint is required");
        Objects.requireNonNull(failure, "failure is required");
        if (fingerprint.isPresent() == failure.isPresent()) {
            throw new IllegalArgumentException(
                    "exactly one of fingerprint or failure must be present"
            );
        }
    }

    public boolean successful() {
        return failure.isEmpty();
    }
}
