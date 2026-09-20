package cartographer.perf.snapshot;

import cartographer.perf.fingerprint.ResultFingerprint;
import cartographer.perf.metrics.Pf18ResourceEvidence;

import java.util.Objects;

public record Pf3RockWarmRenderSample(
        int radius,
        long warmElapsedNanoseconds,
        long exactParityElapsedNanoseconds,
        Pf18ResourceEvidence warmResources,
        boolean geometryParity,
        boolean legendParity,
        boolean countParity,
        ResultFingerprint warmFingerprint,
        ResultFingerprint exactFingerprint
) {
    public Pf3RockWarmRenderSample {
        if (radius <= 0) {
            throw new IllegalArgumentException("radius must be positive");
        }
        if (warmElapsedNanoseconds < 0L
                || exactParityElapsedNanoseconds < 0L) {
            throw new IllegalArgumentException(
                    "elapsed time cannot be negative"
            );
        }
        warmResources = Objects.requireNonNull(
                warmResources,
                "warmResources is required"
        );
        warmFingerprint = Objects.requireNonNull(
                warmFingerprint,
                "warmFingerprint is required"
        );
        exactFingerprint = Objects.requireNonNull(
                exactFingerprint,
                "exactFingerprint is required"
        );
    }

    public boolean imageParity() {
        return warmFingerprint.equals(exactFingerprint);
    }

    public boolean accepted() {
        return geometryParity
                && legendParity
                && countParity
                && imageParity();
    }
}
