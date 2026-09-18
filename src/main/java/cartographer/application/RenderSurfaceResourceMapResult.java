package cartographer.application;

import cartographer.render.MapRenderReport;
import cartographer.render.MapViewportGeometry;
import cartographer.resource.SurfaceRenderAnalysis;
import cartographer.scanner.SurfaceMapScanResult;
import cartographer.save.ReadDiagnostics;

import java.awt.image.BufferedImage;
import java.util.Objects;

public record RenderSurfaceResourceMapResult(
        BufferedImage image,
        MapViewportGeometry geometry,
        SurfaceRenderAnalysis analysis,
        SurfaceMapScanResult surface,
        MapRenderReport renderReport,
        ReadDiagnostics mapChunkDiagnostics,
        ReadDiagnostics chunkDiagnostics,
        int userMarkersDrawn,
        RenderDataCacheReport renderDataCacheReport,
        java.util.Optional<PreparedMapData> preparedMapData,
        java.util.Optional<MapDecorationState> decorationState
) {

    public RenderSurfaceResourceMapResult {
        Objects.requireNonNull(image, "image is required");
        Objects.requireNonNull(geometry, "geometry is required");
        Objects.requireNonNull(analysis, "analysis is required");
        Objects.requireNonNull(surface, "surface is required");
        Objects.requireNonNull(renderReport, "renderReport is required");
        Objects.requireNonNull(mapChunkDiagnostics, "mapChunkDiagnostics is required");
        Objects.requireNonNull(chunkDiagnostics, "chunkDiagnostics is required");
        Objects.requireNonNull(renderDataCacheReport, "renderDataCacheReport is required");
        preparedMapData = Objects.requireNonNull(
                preparedMapData,
                "preparedMapData is required; use Optional.empty() when unavailable"
        );
        decorationState = Objects.requireNonNull(
                decorationState,
                "decorationState is required; use Optional.empty() when unavailable"
        );
        if (userMarkersDrawn < 0) {
            throw new IllegalArgumentException("user markers drawn cannot be negative");
        }
    }

    public RenderSurfaceResourceMapResult(
            BufferedImage image,
            MapViewportGeometry geometry,
            SurfaceRenderAnalysis analysis,
            SurfaceMapScanResult surface,
            MapRenderReport renderReport,
            ReadDiagnostics mapChunkDiagnostics,
            ReadDiagnostics chunkDiagnostics,
            int userMarkersDrawn,
            RenderDataCacheReport renderDataCacheReport
    ) {
        this(
                image,
                geometry,
                analysis,
                surface,
                renderReport,
                mapChunkDiagnostics,
                chunkDiagnostics,
                userMarkersDrawn,
                renderDataCacheReport,
                java.util.Optional.empty(),
                java.util.Optional.empty()
        );
    }
}
