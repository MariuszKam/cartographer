package cartographer.application;

import cartographer.render.ActualOreOverlayResult;
import cartographer.render.MapRenderReport;
import cartographer.render.MapViewportGeometry;
import cartographer.render.OverlayRenderReport;
import cartographer.scanner.ActualBlockMap;
import cartographer.scanner.SurfaceDiagnosticsSummary;
import cartographer.save.ReadDiagnostics;

import java.awt.image.BufferedImage;
import java.util.Objects;
import java.util.Optional;
import java.util.List;

public record RenderActualOreMapResult(
        BufferedImage image,
        MapViewportGeometry geometry,
        MapRenderReport renderReport,
        SurfaceDiagnosticsSummary surface,
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
        Optional<PreparedMapData> preparedMapData,
        Optional<MapDecorationState> decorationState,
        Optional<MapRegionOverlayState> mapRegionOverlayState
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
        decorationState = Objects.requireNonNull(
                decorationState,
                "decorationState is required; use Optional.empty() when unavailable"
        );
        mapRegionOverlayState = Objects.requireNonNull(
                mapRegionOverlayState,
                "mapRegionOverlayState is required; use Optional.empty() when unavailable"
        );
    }

}
