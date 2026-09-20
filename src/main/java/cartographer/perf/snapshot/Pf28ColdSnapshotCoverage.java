package cartographer.perf.snapshot;

import cartographer.application.PrepareWorldSnapshotResult;

import java.util.Objects;

/** Layer-by-layer PF-2.8 evidence for one cold world-snapshot build. */
public record Pf28ColdSnapshotCoverage(
        int observedMapChunks,
        boolean mapChunkCatalogComplete,
        boolean terrainCoverageComplete,
        boolean surfaceCoverageComplete,
        boolean mapRegionCoverageComplete,
        boolean upperRockCoverageComplete,
        boolean resourceIndexCoverageComplete
) {
    public Pf28ColdSnapshotCoverage {
        if (observedMapChunks < 0) {
            throw new IllegalArgumentException(
                    "observedMapChunks cannot be negative"
            );
        }
    }

    public static Pf28ColdSnapshotCoverage from(
            PrepareWorldSnapshotResult result
    ) {
        Objects.requireNonNull(result, "result is required");
        return new Pf28ColdSnapshotCoverage(
                result.observedMapChunks(),
                result.mapChunkCatalogComplete(),
                result.terrainCoverageComplete(),
                result.surfaceCoverageComplete(),
                result.mapRegionCoverageComplete(),
                result.upperRockCoverageComplete(),
                result.resourceIndexCoverageComplete()
        );
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
