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
        int surfaceObjectRegistryVariants,
        int surfaceObjectPositionsInspected,
        int surfaceObjectUnavailablePositions,
        int surfaceObjectObservedTargets,
        int surfaceObjectNotObservedTargets,
        SelectiveChunkStreamStats surfaceObjectChunkStats,
        boolean surfaceObjectScanUsed
) {

    public RenderSurfaceResourceMapResult {
        Objects.requireNonNull(image, "image is required");
        Objects.requireNonNull(analysis, "analysis is required");
        Objects.requireNonNull(surface, "surface is required");
        Objects.requireNonNull(renderReport, "renderReport is required");
        Objects.requireNonNull(mapChunkDiagnostics, "mapChunkDiagnostics is required");
        Objects.requireNonNull(chunkDiagnostics, "chunkDiagnostics is required");
        if (surfaceObjectRegistryVariants < 0
                || surfaceObjectPositionsInspected < 0
                || surfaceObjectUnavailablePositions < 0
                || surfaceObjectObservedTargets < 0
                || surfaceObjectNotObservedTargets < 0) {
            throw new IllegalArgumentException("surface object diagnostics cannot be negative");
        }
        Objects.requireNonNull(surfaceObjectChunkStats, "surfaceObjectChunkStats is required");
    }
}
