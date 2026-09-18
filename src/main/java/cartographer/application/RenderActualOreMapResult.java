package cartographer.application;

import cartographer.render.MapRenderReport;
import cartographer.render.MapViewportGeometry;
import cartographer.render.OverlayRenderReport;
import cartographer.scanner.ActualBlockMap;
import cartographer.scanner.SurfaceMapScanResult;
import cartographer.save.ReadDiagnostics;

import java.awt.image.BufferedImage;
import java.util.Objects;
import java.util.Optional;
import java.util.List;

public record RenderActualOreMapResult(
        BufferedImage image,
        MapViewportGeometry geometry,
        MapRenderReport renderReport,
        SurfaceMapScanResult surface,
        OverlayRenderReport environmentOverlay,
        OverlayRenderReport geologyOverlay,
        Optional<ActualBlockMap> actualOreMap,
        ReadDiagnostics mapChunkDiagnostics,
        ReadDiagnostics chunkDiagnostics,
        ReadDiagnostics mapRegionDiagnostics,
        ReadDiagnostics actualOreDiagnostics,
        int userMarkersDrawn,
        List<ActualOreOverlayResult> actualOreOverlays,
        RenderDataCacheReport renderDataCacheReport,
        Optional<PreparedMapData> preparedMapData
) {

    public RenderActualOreMapResult {
        Objects.requireNonNull(image, "image is required");
        Objects.requireNonNull(geometry, "geometry is required");
        Objects.requireNonNull(renderReport, "renderReport is required");
        Objects.requireNonNull(surface, "surface is required");
        Objects.requireNonNull(
                actualOreMap,
                "actualOreMap is required; use Optional.empty() when absent"
        );
        actualOreOverlays = List.copyOf(
                actualOreOverlays == null ? List.of() : actualOreOverlays
        );
        Objects.requireNonNull(renderDataCacheReport, "renderDataCacheReport is required");
        preparedMapData = Objects.requireNonNull(
                preparedMapData,
                "preparedMapData is required; use Optional.empty() when unavailable"
        );
    }

    public RenderActualOreMapResult(
            BufferedImage image,
            MapViewportGeometry geometry,
            MapRenderReport renderReport,
            SurfaceMapScanResult surface,
            OverlayRenderReport environmentOverlay,
            OverlayRenderReport geologyOverlay,
            Optional<ActualBlockMap> actualOreMap,
            ReadDiagnostics mapChunkDiagnostics,
            ReadDiagnostics chunkDiagnostics,
            ReadDiagnostics mapRegionDiagnostics,
            ReadDiagnostics actualOreDiagnostics,
            int userMarkersDrawn,
            List<ActualOreOverlayResult> actualOreOverlays
    ) {
        this(image, geometry, renderReport, surface, environmentOverlay, geologyOverlay,
                actualOreMap, mapChunkDiagnostics, chunkDiagnostics, mapRegionDiagnostics,
                actualOreDiagnostics, userMarkersDrawn, actualOreOverlays,
                RenderDataCacheReport.disabled("PF-1.7 render-data cache disabled"),
                Optional.empty());
    }

    public RenderActualOreMapResult(
            BufferedImage image,
            MapViewportGeometry geometry,
            MapRenderReport renderReport,
            SurfaceMapScanResult surface,
            OverlayRenderReport environmentOverlay,
            OverlayRenderReport geologyOverlay,
            Optional<ActualBlockMap> actualOreMap,
            ReadDiagnostics mapChunkDiagnostics,
            ReadDiagnostics chunkDiagnostics,
            ReadDiagnostics mapRegionDiagnostics,
            ReadDiagnostics actualOreDiagnostics,
            int userMarkersDrawn,
            List<ActualOreOverlayResult> actualOreOverlays,
            RenderDataCacheReport renderDataCacheReport
    ) {
        this(
                image, geometry, renderReport, surface, environmentOverlay, geologyOverlay,
                actualOreMap, mapChunkDiagnostics, chunkDiagnostics, mapRegionDiagnostics,
                actualOreDiagnostics, userMarkersDrawn, actualOreOverlays,
                renderDataCacheReport, Optional.empty()
        );
    }

}
