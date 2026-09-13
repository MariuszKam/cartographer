package cartographer.application;

import cartographer.render.MapRenderReport;
import cartographer.render.OverlayRenderReport;
import cartographer.scanner.ActualBlockMap;
import cartographer.scanner.ActualBlockMatchMode;
import cartographer.scanner.SurfaceScanResult;
import cartographer.save.ReadDiagnostics;

import java.awt.image.BufferedImage;
import java.util.Objects;
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
                compatibilityOverlays(actualOreMap)
        );
    }

    public RenderActualOreMapResult {
        actualOreMap = Objects.requireNonNull(
                actualOreMap,
                "actualOreMap is required; use Optional.empty() when absent"
        );
        actualOreOverlays = List.copyOf(
                actualOreOverlays == null ? List.of() : actualOreOverlays
        );
    }

    private static List<ActualOreOverlayResult> compatibilityOverlays(
            Optional<ActualBlockMap> actualOreMap
    ) {
        Objects.requireNonNull(
                actualOreMap,
                "actualOreMap is required; use Optional.empty() when absent"
        );
        return actualOreMap.map(
                map -> List.of(
                        new ActualOreOverlayResult(
                                new ActualOreOverlaySpec(
                                        map.match(),
                                        map.match(),
                                        new java.awt.Color(225, 92, 24),
                                        ActualBlockMatchMode.GENERIC_SUBSTRING
                                ),
                                map
                        )
                )
        ).orElse(List.of());
    }
}
