package cartographer.application;

import cartographer.render.MapRenderReport;
import cartographer.render.OverlayRenderReport;
import cartographer.scanner.ActualBlockMap;
import cartographer.scanner.SurfaceScanResult;
import cartographer.save.ReadDiagnostics;

import java.awt.image.BufferedImage;
import java.util.Optional;
import java.util.List;

public record RenderActualOreMapResult(
        BufferedImage image,
        MapRenderReport renderReport,
        SurfaceScanResult surface,
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

    public RenderActualOreMapResult(
            BufferedImage image,
            MapRenderReport renderReport,
            SurfaceScanResult surface,
            OverlayRenderReport environmentOverlay,
            OverlayRenderReport geologyOverlay,
            Optional<ActualBlockMap> actualOreMap,
            ReadDiagnostics mapChunkDiagnostics,
            ReadDiagnostics chunkDiagnostics,
            ReadDiagnostics mapRegionDiagnostics,
            ReadDiagnostics actualOreDiagnostics,
            int userMarkersDrawn
    ) {
        this(
                image,
                renderReport,
                surface,
                environmentOverlay,
                geologyOverlay,
                actualOreMap,
                mapChunkDiagnostics,
                chunkDiagnostics,
                mapRegionDiagnostics,
                actualOreDiagnostics,
                userMarkersDrawn,
                actualOreMap.map(
                        map -> List.of(
                                new ActualOreOverlayResult(
                                        new ActualOreOverlaySpec(
                                                map.match(),
                                                map.match(),
                                                new java.awt.Color(225, 92, 24)
                                        ),
                                        map
                                )
                        )
                ).orElse(List.of())
        );
    }

    public RenderActualOreMapResult {
        actualOreMap = Optional.ofNullable(actualOreMap).orElse(Optional.empty());
        actualOreOverlays = List.copyOf(
                actualOreOverlays == null ? List.of() : actualOreOverlays
        );
    }
}
