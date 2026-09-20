package cartographer.perf.snapshot;

import cartographer.application.PrepareWorldSnapshotResult;

import java.util.Objects;

/** Layer-by-layer PF-2.8 evidence for one cold world-snapshot build. */
public record Pf28ColdSnapshotCoverage(
        int observedMapChunks,
        int terrainHits,
        int terrainPublished,
        int surfaceHits,
        int surfacePublished,
        int mapRegionHits,
        int mapRegionPublished,
        int upperRockHits,
        int upperRockPublished,
        int resourceChunkHits,
        int resourceChunksPublished,
        boolean mapChunkCatalogComplete,
        boolean terrainCoverageComplete,
        boolean surfaceCoverageComplete,
        boolean mapRegionCoverageComplete,
        boolean upperRockCoverageComplete,
        boolean resourceIndexCoverageComplete
) {
    public Pf28ColdSnapshotCoverage {
        if (observedMapChunks < 0
                || terrainHits < 0
                || terrainPublished < 0
                || surfaceHits < 0
                || surfacePublished < 0
                || mapRegionHits < 0
                || mapRegionPublished < 0
                || upperRockHits < 0
                || upperRockPublished < 0
                || resourceChunkHits < 0
                || resourceChunksPublished < 0) {
            throw new IllegalArgumentException(
                    "cold snapshot counters cannot be negative"
            );
        }
    }

    public static Pf28ColdSnapshotCoverage from(
            PrepareWorldSnapshotResult result
    ) {
        Objects.requireNonNull(result, "result is required");
        return new Pf28ColdSnapshotCoverage(
                result.observedMapChunks(),
                result.terrainHits(),
                result.terrainPublished(),
                result.surfaceHits(),
                result.surfacePublished(),
                result.mapRegionHits(),
                result.mapRegionPublished(),
                result.upperRockHits(),
                result.upperRockPublished(),
                result.resourceChunkHits(),
                result.resourceChunksPublished(),
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

    /**
     * PF-2.8 runs against a fresh derived cache root. Any derived HIT means
     * the campaign was not actually a cold snapshot build.
     */
    public boolean coldBuildProven() {
        return terrainHits == 0
                && surfaceHits == 0
                && mapRegionHits == 0
                && upperRockHits == 0
                && resourceChunkHits == 0;
    }
}
