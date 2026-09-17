package cartographer.application;

import cartographer.render.MapRenderReport;
import cartographer.render.MapViewportGeometry;
import cartographer.resource.SurfaceRenderAnalysis;
import cartographer.scanner.SurfaceScanResult;
import cartographer.scanner.SurfaceMapScanResult;
import cartographer.save.ReadDiagnostics;

import java.awt.image.BufferedImage;
import java.util.Objects;

public record RenderSurfaceResourceMapResult(
        BufferedImage image,
        MapViewportGeometry geometry,
        SurfaceRenderAnalysis analysis,
        SurfaceScanResult surface,
        SurfaceMapScanResult compactSurface,
        MapRenderReport renderReport,
        ReadDiagnostics mapChunkDiagnostics,
        ReadDiagnostics chunkDiagnostics,
        int userMarkersDrawn
) {

    public RenderSurfaceResourceMapResult(
            java.awt.image.BufferedImage image,
            MapViewportGeometry geometry,
            SurfaceRenderAnalysis analysis,
            SurfaceScanResult surface,
            MapRenderReport renderReport,
            ReadDiagnostics mapChunkDiagnostics,
            ReadDiagnostics chunkDiagnostics,
            int userMarkersDrawn
    ) {
        this(image, geometry, analysis, surface,
                SurfaceMapScanResultCompatibility.fromLegacy(surface), renderReport,
                mapChunkDiagnostics, chunkDiagnostics, userMarkersDrawn);
    }

    public RenderSurfaceResourceMapResult {
        Objects.requireNonNull(image, "image is required");
        Objects.requireNonNull(geometry, "geometry is required");
        Objects.requireNonNull(analysis, "analysis is required");
        Objects.requireNonNull(surface, "surface is required");
        Objects.requireNonNull(compactSurface, "compact surface is required");
        Objects.requireNonNull(renderReport, "renderReport is required");
        Objects.requireNonNull(mapChunkDiagnostics, "mapChunkDiagnostics is required");
        Objects.requireNonNull(chunkDiagnostics, "chunkDiagnostics is required");
        if (userMarkersDrawn < 0) {
            throw new IllegalArgumentException("user markers drawn cannot be negative");
        }
    }
}
