package cartographer.perf;

import java.util.Objects;

/**
 * Last successfully completed PF-2 world-preparation summary for one revision.
 *
 * <p>This is derived UX metadata only. It is never source authority and does
 * not replace per-store compatibility/corruption checks on warm consumers.</p>
 */
public record WorldPreparationState(
        String revisionHash,
        int observedMapChunks,
        boolean mapChunkCatalogComplete,
        boolean terrainCoverageComplete,
        boolean surfaceCoverageComplete,
        boolean mapRegionCoverageComplete,
        boolean upperRockCoverageComplete,
        boolean resourceIndexCoverageComplete
) {
    public WorldPreparationState {
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
