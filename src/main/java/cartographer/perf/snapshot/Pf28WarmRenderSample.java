package cartographer.perf.snapshot;

import cartographer.perf.fingerprint.ResultFingerprint;
import cartographer.perf.metrics.Pf18ResourceEvidence;

import java.util.Objects;

public record Pf28WarmRenderSample(
        int radius,
        long warmElapsedNanoseconds,
        long sourceParityElapsedNanoseconds,
        Pf18ResourceEvidence warmResources,
        int sourceConnectionsOpened,
        int sourceConnectionsClosed,
        boolean snapshotBacked,
        boolean geometryParity,
        ResultFingerprint warmFingerprint,
        ResultFingerprint sourceFingerprint
) {
    public Pf28WarmRenderSample {
        if (radius <= 0) {
            throw new IllegalArgumentException("radius must be positive");
        }
        if (warmElapsedNanoseconds < 0L || sourceParityElapsedNanoseconds < 0L) {
            throw new IllegalArgumentException("elapsed time cannot be negative");
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
        sourceFingerprint = Objects.requireNonNull(
                sourceFingerprint,
                "sourceFingerprint is required"
        );
    }

    public boolean sourceReadEliminated() {
        return sourceConnectionsOpened == 0
                && sourceConnectionsClosed == 0;
    }

    public boolean imageParity() {
        return warmFingerprint.equals(sourceFingerprint);
    }

    public boolean accepted() {
        return snapshotBacked
                && sourceReadEliminated()
                && geometryParity
                && imageParity();
    }
}
