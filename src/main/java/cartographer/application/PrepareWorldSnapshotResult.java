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
        boolean mapChunkCatalogComplete,
        boolean terrainCoverageComplete,
        boolean surfaceCoverageComplete,
        ReadDiagnostics mapChunkDiagnostics,
        ReadDiagnostics chunkDiagnostics
) {
    public PrepareWorldSnapshotResult {
        revisionHash = Objects.requireNonNull(revisionHash, "revisionHash is required");
        if (observedMapChunks < 0
                || terrainHits < 0
                || terrainPublished < 0
                || surfaceHits < 0
                || surfacePublished < 0
                || surfaceSkippedIncomplete < 0) {
            throw new IllegalArgumentException("world snapshot counters cannot be negative");
        }
        Objects.requireNonNull(mapChunkDiagnostics, "mapChunkDiagnostics is required");
        Objects.requireNonNull(chunkDiagnostics, "chunkDiagnostics is required");
    }

    public boolean complete() {
        return mapChunkCatalogComplete
                && terrainCoverageComplete
                && surfaceCoverageComplete;
    }
}
