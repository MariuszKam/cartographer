package cartographer.application;

import cartographer.render.MapRenderReport;
import cartographer.render.OverlayRenderReport;
import cartographer.scanner.ActualBlockMap;
import cartographer.scanner.SurfaceScanResult;
import cartographer.save.ReadDiagnostics;

import java.awt.image.BufferedImage;
import java.util.Optional;

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
        int userMarkersDrawn
) {

    public RenderActualOreMapResult {
        actualOreMap = Optional.ofNullable(actualOreMap).orElse(Optional.empty());
    }
}
