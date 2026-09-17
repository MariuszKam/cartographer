package cartographer.application;

import cartographer.model.WorldPosition;
import cartographer.resource.ObservedSurfaceResourceCatalog;
import cartographer.resource.SurfaceObjectCandidateCatalog;
import cartographer.save.ReadDiagnostics;
import cartographer.save.SelectiveChunkStreamStats;
import cartographer.scanner.SurfaceObjectCompactScanResult;

import java.util.Objects;

public record DiscoverObservedSurfaceResourcesResult(
        WorldPosition center,
        SurfaceObjectCandidateCatalog candidateCatalog,
        int plannedTargetCount,
        int requestedChunkPositionCount,
        SelectiveChunkStreamStats chunkStats,
        SurfaceObjectCompactScanResult scan,
        ObservedSurfaceResourceCatalog observedResources,
        ReadDiagnostics mapChunkDiagnostics,
        ReadDiagnostics chunkDiagnostics
) {
    public DiscoverObservedSurfaceResourcesResult {
        Objects.requireNonNull(center, "center is required");
        Objects.requireNonNull(candidateCatalog, "candidate catalog is required");
        if (plannedTargetCount < 0 || requestedChunkPositionCount < 0) {
            throw new IllegalArgumentException("discovery counts cannot be negative");
        }
        Objects.requireNonNull(chunkStats, "chunk stats are required");
        Objects.requireNonNull(scan, "scan is required");
        Objects.requireNonNull(observedResources, "observed resources are required");
        Objects.requireNonNull(mapChunkDiagnostics, "mapchunk diagnostics are required");
        Objects.requireNonNull(chunkDiagnostics, "chunk diagnostics are required");
    }
}
