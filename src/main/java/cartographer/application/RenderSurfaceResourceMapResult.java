package cartographer.application;

import cartographer.render.MapRenderReport;
import cartographer.resource.SurfaceResourceAnalysis;
import cartographer.scanner.SurfaceScanResult;
import cartographer.save.ReadDiagnostics;
import cartographer.save.SelectiveChunkStreamStats;

import java.awt.image.BufferedImage;
import java.util.Objects;

public record RenderSurfaceResourceMapResult(
        BufferedImage image,
        SurfaceResourceAnalysis analysis,
        SurfaceScanResult surface,
        MapRenderReport renderReport,
        ReadDiagnostics mapChunkDiagnostics,
        ReadDiagnostics chunkDiagnostics,
        int userMarkersDrawn,
        int exposedObsidianCount,
        int looseObsidianCount,
        int surfaceObjectRegistryVariants,
        int surfaceObjectPositionsInspected,
        int surfaceObjectUnavailablePositions,
        int surfaceObjectObservedTargets,
        int surfaceObjectNotObservedTargets,
        SelectiveChunkStreamStats surfaceObjectChunkStats,
        SurfaceObjectDataSource surfaceObjectDataSource
) {

    public RenderSurfaceResourceMapResult(
            BufferedImage image,
            SurfaceResourceAnalysis analysis,
            SurfaceScanResult surfaceScan,
            MapRenderReport renderReport,
            ReadDiagnostics mapChunkDiagnostics,
            ReadDiagnostics chunkDiagnostics,
            int userMarkersDrawn,
            int exposedObsidianCount,
            int looseObsidianCount,
            int surfaceObjectRegistryVariants,
            int surfaceObjectPositionsInspected,
            int surfaceObjectUnavailablePositions,
            int surfaceObjectObservedTargets,
            int surfaceObjectNotObservedTargets,
            SelectiveChunkStreamStats surfaceObjectChunkStats,
            boolean surfaceObjectScanUsed
    ) {
        this(image, analysis, surfaceScan, renderReport, mapChunkDiagnostics,
                chunkDiagnostics, userMarkersDrawn, exposedObsidianCount,
                looseObsidianCount, surfaceObjectRegistryVariants,
                surfaceObjectPositionsInspected, surfaceObjectUnavailablePositions,
                surfaceObjectObservedTargets, surfaceObjectNotObservedTargets,
                surfaceObjectChunkStats,
                surfaceObjectScanUsed
                        ? SurfaceObjectDataSource.LEGACY_SCAN
                        : SurfaceObjectDataSource.NONE);
    }

    public RenderSurfaceResourceMapResult {
        Objects.requireNonNull(image, "image is required");
        Objects.requireNonNull(analysis, "analysis is required");
        Objects.requireNonNull(surface, "surface is required");
        Objects.requireNonNull(renderReport, "renderReport is required");
        Objects.requireNonNull(mapChunkDiagnostics, "mapChunkDiagnostics is required");
        Objects.requireNonNull(chunkDiagnostics, "chunkDiagnostics is required");
        if (exposedObsidianCount < 0
                || looseObsidianCount < 0
                || surfaceObjectRegistryVariants < 0
                || surfaceObjectPositionsInspected < 0
                || surfaceObjectUnavailablePositions < 0
                || surfaceObjectObservedTargets < 0
                || surfaceObjectNotObservedTargets < 0) {
            throw new IllegalArgumentException("surface object diagnostics cannot be negative");
        }
        Objects.requireNonNull(surfaceObjectChunkStats, "surfaceObjectChunkStats is required");
        Objects.requireNonNull(surfaceObjectDataSource, "surfaceObjectDataSource is required");
    }

    public boolean surfaceObjectScanUsed() {
        return surfaceObjectDataSource == SurfaceObjectDataSource.LEGACY_SCAN;
    }
}
