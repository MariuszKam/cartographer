package cartographer.application;

import cartographer.save.ReadDiagnostics;

import java.util.Objects;

public record PrepareWorldSnapshotResult(
        String revisionHash,
        int observedMapChunks,
        int terrainHits,
        int terrainPublished,
        int surfaceHits,
        int surfacePublished,
        int surfaceSkippedIncomplete,
        int mapRegionHits,
        int mapRegionPublished,
        int upperRockHits,
        int upperRockPublished,
        int resourceBlocksCatalogued,
        int resourceChunkHits,
        int resourceChunksPublished,
        long resourceOccurrenceColumnsPublished,
        boolean mapChunkCatalogComplete,
        boolean terrainCoverageComplete,
        boolean surfaceCoverageComplete,
        boolean mapRegionCoverageComplete,
        boolean upperRockCoverageComplete,
        boolean resourceIndexCoverageComplete,
        ReadDiagnostics mapChunkDiagnostics,
        ReadDiagnostics chunkDiagnostics,
        ReadDiagnostics mapRegionDiagnostics,
        ReadDiagnostics rockDiagnostics,
        ReadDiagnostics resourceDiagnostics
) {
    public PrepareWorldSnapshotResult {
        revisionHash = Objects.requireNonNull(
                revisionHash,
                "revisionHash is required"
        );
        if (observedMapChunks < 0
                || terrainHits < 0
                || terrainPublished < 0
                || surfaceHits < 0
                || surfacePublished < 0
                || surfaceSkippedIncomplete < 0
                || mapRegionHits < 0
                || mapRegionPublished < 0
                || upperRockHits < 0
                || upperRockPublished < 0
                || resourceBlocksCatalogued < 0
                || resourceChunkHits < 0
                || resourceChunksPublished < 0
                || resourceOccurrenceColumnsPublished < 0L) {
            throw new IllegalArgumentException(
                    "world snapshot counters cannot be negative"
            );
        }
        Objects.requireNonNull(
                mapChunkDiagnostics,
                "mapChunkDiagnostics is required"
        );
        Objects.requireNonNull(
                chunkDiagnostics,
                "chunkDiagnostics is required"
        );
        Objects.requireNonNull(
                mapRegionDiagnostics,
                "mapRegionDiagnostics is required"
        );
        Objects.requireNonNull(
                rockDiagnostics,
                "rockDiagnostics is required"
        );
        Objects.requireNonNull(
                resourceDiagnostics,
                "resourceDiagnostics is required"
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
