package cartographer.perf.comparison;

import cartographer.perf.fingerprint.ResultFingerprint;
import cartographer.perf.instrumentation.PerformanceInstrumentationSnapshot;

import java.util.Objects;
import java.util.Optional;

/** One successful measured sample retained as candidate evidence. */
public record CandidateMeasurementSample(
        int iterationIndex,
        long wallClockNanoseconds,
        ResultFingerprint correctnessFingerprint,
        Optional<PerformanceInstrumentationSnapshot> instrumentation
) {
    public CandidateMeasurementSample {
        if (iterationIndex < 0) {
            throw new IllegalArgumentException("iterationIndex must not be negative");
        }
        if (wallClockNanoseconds < 0) {
            throw new IllegalArgumentException(
                    "wallClockNanoseconds must not be negative"
            );
        }
        Objects.requireNonNull(correctnessFingerprint, "correctnessFingerprint is required");
        Objects.requireNonNull(instrumentation, "instrumentation is required");
    }
}
