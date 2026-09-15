package cartographer.application;

import cartographer.model.WorldPosition;
import cartographer.resource.ObservedSurfaceResourceCatalog;
import cartographer.resource.SurfaceObjectCandidateCatalog;
import cartographer.save.ReadDiagnostics;
import cartographer.save.SelectiveChunkStreamStats;
import cartographer.scanner.SurfaceObjectPlan;
import cartographer.scanner.SurfaceObjectScanResult;

import java.util.Objects;

public record DiscoverObservedSurfaceResourcesResult(
        WorldPosition center,
        SurfaceObjectCandidateCatalog candidateCatalog,
        SurfaceObjectPlan plan,
        SelectiveChunkStreamStats chunkStats,
        SurfaceObjectScanResult scan,
        ObservedSurfaceResourceCatalog observedResources,
        ReadDiagnostics mapChunkDiagnostics,
        ReadDiagnostics chunkDiagnostics
) {
    public DiscoverObservedSurfaceResourcesResult {
        Objects.requireNonNull(center, "center is required");
        Objects.requireNonNull(candidateCatalog, "candidate catalog is required");
        Objects.requireNonNull(plan, "plan is required");
        Objects.requireNonNull(chunkStats, "chunk stats are required");
        Objects.requireNonNull(scan, "scan is required");
        Objects.requireNonNull(observedResources, "observed resources are required");
        Objects.requireNonNull(mapChunkDiagnostics, "mapchunk diagnostics are required");
        Objects.requireNonNull(chunkDiagnostics, "chunk diagnostics are required");
    }
}
