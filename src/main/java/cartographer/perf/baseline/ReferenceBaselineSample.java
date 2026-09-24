package cartographer.perf.baseline;

import cartographer.perf.fingerprint.ResultFingerprint;
import java.util.Objects;

/** One successful measured sample retained as baseline evidence. */
public record ReferenceBaselineSample(
        int iterationIndex,
        long wallClockNanoseconds,
        ResultFingerprint correctnessFingerprint
) {
    public ReferenceBaselineSample {
        if (iterationIndex < 0) {
            throw new IllegalArgumentException("iterationIndex must not be negative");
        }
        if (wallClockNanoseconds < 0) {
            throw new IllegalArgumentException(
                    "wallClockNanoseconds must not be negative"
            );
        }
        Objects.requireNonNull(correctnessFingerprint, "correctnessFingerprint is required");
    }
}
