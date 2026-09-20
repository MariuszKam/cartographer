package cartographer.perf.snapshot;

import cartographer.perf.fingerprint.ResultFingerprint;
import cartographer.perf.metrics.Pf18ResourceEvidence;

import java.util.Objects;

public record Pf3RockWarmRenderSample(
        int radius,
        long warmElapsedNanoseconds,
        long exactParityElapsedNanoseconds,
        Pf18ResourceEvidence warmResources,
        int sourceConnectionsOpened,
        int sourceConnectionsClosed,
        boolean retainedMapAbsent,
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
        if (sourceConnectionsOpened < 0 || sourceConnectionsClosed < 0) {
            throw new IllegalArgumentException(
                    "source connection counts cannot be negative"
            );
        }
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

    public boolean sourceReadEliminated() {
        return sourceConnectionsOpened == 0
                && sourceConnectionsClosed == 0;
    }

    public boolean accepted() {
        return sourceReadEliminated()
                && retainedMapAbsent
                && geometryParity
                && legendParity
                && countParity
                && imageParity();
    }
}
