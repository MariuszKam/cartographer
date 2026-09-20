package cartographer.perf;

import java.util.Objects;

/**
 * Persisted PF-2 preparation summary for one immutable save revision.
 *
 * <p>This is derived evidence only. It never replaces source authority and is
 * written only after a Prepare World operation returns normally.</p>
 */
public record WorldSnapshotPreparationSummary(
        String revisionHash,
        int observedMapChunks,
        boolean mapChunkCatalogComplete,
        boolean terrainCoverageComplete,
        boolean surfaceCoverageComplete,
        boolean mapRegionCoverageComplete,
        boolean upperRockCoverageComplete,
        boolean resourceIndexCoverageComplete
) {
    public WorldSnapshotPreparationSummary {
        revisionHash = Objects.requireNonNull(
                revisionHash,
                "revisionHash is required"
        ).trim();
        if (revisionHash.isEmpty()) {
            throw new IllegalArgumentException(
                    "revisionHash must not be blank"
            );
        }
        if (observedMapChunks < 0) {
            throw new IllegalArgumentException(
                    "observedMapChunks cannot be negative"
            );
        }
    }

    public boolean complete() {
        return mapChunkCatalogComplete
                && terrainCoverageComplete
                && surfaceCoverageComplete
                && mapRegionCoverageComplete
                && upperRockCoverageComplete
                && resourceIndexCoverageComplete;
    }
}
